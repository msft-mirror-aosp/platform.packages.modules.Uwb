/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.ranging;

import static android.ranging.RangingSession.Callback.REASON_LOCAL_REQUEST;
import static android.ranging.RangingSession.Callback.REASON_NO_PEERS_FOUND;
import static android.ranging.RangingSession.Callback.REASON_SYSTEM_POLICY;
import static android.ranging.RangingSession.Callback.REASON_UNKNOWN;
import static android.ranging.RangingSession.Callback.REASON_UNSUPPORTED;

import android.content.AttributionSource;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.ranging.IRangingCallbacks;
import android.ranging.IRangingCapabilitiesCallback;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingParams;
import android.ranging.RangingPreference;
import android.ranging.RangingSession.Callback;
import android.ranging.SessionHandle;
import android.ranging.oob.IOobSendDataListener;
import android.ranging.oob.OobHandle;
import android.ranging.oob.OobInitiatorRangingParams;
import android.ranging.oob.OobResponderRangingParams;
import android.ranging.raw.RawInitiatorRangingParams;
import android.ranging.raw.RawResponderRangingParams;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.oob.OobHandler;
import com.android.server.ranging.session.OobInitiatorRangingSession;
import com.android.server.ranging.session.OobResponderRangingSession;
import com.android.server.ranging.session.RangingSession;
import com.android.server.ranging.session.RangingSessionConfig;
import com.android.server.ranging.session.RawInitiatorRangingSession;
import com.android.server.ranging.session.RawResponderRangingSession;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RangingServiceManager {
    private static final String TAG = RangingServiceManager.class.getSimpleName();

    public enum RangingTask {
        TASK_START_RANGING(1),
        TASK_STOP_RANGING(2),
        TASK_ADD_DEVICE(3),
        TASK_REMOVE_DEVICE(4),
        TASK_RECONFIGURE_INTERVAL(5);

        private final int mVal;

        RangingTask(int val) {
            this.mVal = val;
        }

        public int getValue() {
            return mVal;
        }

        public static RangingTask fromValue(int code) {
            for (RangingTask task : RangingTask.values()) {
                if (task.getValue() == code) {
                    return task;
                }
            }
            throw new IllegalArgumentException("Unknown task code: " + code);
        }
    }

    private final RangingInjector mRangingInjector;
    private final ListeningExecutorService mAdapterExecutor;
    private final RangingTaskManager mRangingTaskManager;
    private final Map<SessionHandle, RangingSession<?>> mSessions = new ConcurrentHashMap<>();

    private IOobSendDataListener mOobDataSender;

    public RangingServiceManager(RangingInjector rangingInjector, Looper looper) {
        mRangingInjector = rangingInjector;
        mAdapterExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        mRangingTaskManager = new RangingTaskManager(looper);
    }

    public void registerCapabilitiesCallback(IRangingCapabilitiesCallback capabilitiesCallback) {
        Log.w(TAG, "Registering ranging capabilities callback");
        mRangingInjector
                .getCapabilitiesProvider()
                .registerCapabilitiesCallback(capabilitiesCallback);
    }

    public void unregisterCapabilitiesCallback(IRangingCapabilitiesCallback capabilitiesCallback) {
        mRangingInjector
                .getCapabilitiesProvider()
                .unregisterCapabilitiesCallback(capabilitiesCallback);
    }

    public void startRanging(
            AttributionSource attributionSource, SessionHandle handle, RangingPreference preference,
            IRangingCallbacks callbacks
    ) {
        // TODO android.permission.RANGING permission check here
//        Context context = mRangingInjector.getContext()
//                .createContext(new ContextParams
//                        .Builder()
//                        .setNextAttributionSource(attributionSource)
//                        .build());

        RangingTaskManager.StartRangingArgs args = new RangingTaskManager.StartRangingArgs(
                attributionSource, handle, preference, callbacks);
        mRangingTaskManager.enqueueTask(RangingTask.TASK_START_RANGING, args);
    }

    public void addRawPeer(SessionHandle handle, RawResponderRangingParams params) {
        if (!mSessions.containsKey(handle)) {
            Log.e(TAG, "Failed to add peer. Ranging session not found");
        }
        DynamicPeer peer = new DynamicPeer(params,
                mSessions.get(handle), null /* Ranging device is in params*/);
        mRangingTaskManager.enqueueTask(RangingTask.TASK_ADD_DEVICE, peer);
    }

    public void removePeer(SessionHandle handle, RangingDevice device) {
        if (!mSessions.containsKey(handle)) {
            Log.e(TAG, "Failed to remove peer. Ranging session not found");
        }
        DynamicPeer peer = new DynamicPeer(null /* params not needed*/, mSessions.get(handle),
                device);
        mRangingTaskManager.enqueueTask(RangingTask.TASK_REMOVE_DEVICE, peer);
    }

    public void reconfigureInterval(SessionHandle handle, int intervalSkipCount) {
        if (!mSessions.containsKey(handle)) {
            Log.e(TAG, "Failed to reconfigure ranging interval. Ranging session not found");
        }
        mRangingTaskManager.enqueueTask(RangingTask.TASK_RECONFIGURE_INTERVAL,
                mSessions.get(handle), intervalSkipCount);
    }

    public void stopRanging(SessionHandle handle) {
        RangingSession<?> session = mSessions.get(handle);
        if (session == null) {
            Log.e(TAG, "stopRanging for nonexistent session");
            return;
        }
        mRangingTaskManager.enqueueTask(RangingTask.TASK_STOP_RANGING, session);
    }

    /**
     * Received data from the peer device.
     *
     * @param oobHandle unique session/device pair identifier.
     * @param data      payload
     */
    public void oobDataReceived(OobHandle oobHandle, byte[] data) {
        if (mSessions.get(oobHandle.getSessionHandle()) instanceof OobHandler session) {
            session.handleOobMessage(oobHandle, data);
        } else {
            Log.e(TAG, "oobDataReceived for non-oob session " + oobHandle.getSessionHandle());
        }
    }

    /**
     * Device disconnected from the OOB channel.
     *
     * @param oobHandle unique session/device pair identifier.
     */
    public void deviceOobDisconnected(OobHandle oobHandle) {
        if (mSessions.get(oobHandle.getSessionHandle()) instanceof OobHandler session) {
            session.handleOobDeviceDisconnected(oobHandle);
        } else {
            Log.e(TAG, "deviceOobDisconnected for non-oob session " + oobHandle.getSessionHandle());
        }
    }

    /**
     * Device reconnected to the OOB channel
     *
     * @param oobHandle unique session/device pair identifier.
     */
    public void deviceOobReconnected(OobHandle oobHandle) {
        if (mSessions.get(oobHandle.getSessionHandle()) instanceof OobHandler session) {
            session.handleOobDeviceReconnected(oobHandle);
        } else {
            Log.e(TAG, "deviceOobReconnected for non-oob session " + oobHandle.getSessionHandle());
        }
    }

    /**
     * Device closed the OOB channel.
     *
     * @param oobHandle unique session/device pair identifier.
     */
    public void deviceOobClosed(OobHandle oobHandle) {
        if (mSessions.get(oobHandle.getSessionHandle()) instanceof OobHandler session) {
            session.handleOobClosed(oobHandle);
        } else {
            Log.e(TAG, "deviceOobClosed for non-oob session " + oobHandle.getSessionHandle());
        }
    }

    /**
     * Register send data listener.
     *
     * @param oobSendDataListener listener for sending the data via OOB.
     */
    public void registerOobSendDataListener(IOobSendDataListener oobSendDataListener) {
        mOobDataSender = oobSendDataListener;
    }

    /**
     * Listens for peer-specific events within a session and translates them to
     * {@link IRangingCallbacks} calls.
     */
    public class SessionListener implements IBinder.DeathRecipient {
        private final SessionHandle mSessionHandle;
        private final IRangingCallbacks mRangingCallbacks;
        private final AtomicBoolean mIsSessionStarted;

        SessionListener(SessionHandle sessionHandle, IRangingCallbacks callbacks) {
            mSessionHandle = sessionHandle;
            mRangingCallbacks = callbacks;
            mIsSessionStarted = new AtomicBoolean(false);
            try {
                mRangingCallbacks.asBinder().linkToDeath(this, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to link to death: " + sessionHandle, e);
                stopRanging(mSessionHandle);
            }
        }

        @Override
        public void binderDied() {
            Log.i(TAG, "binderDied : Stopping session: " + mSessionHandle);
            stopRanging(mSessionHandle);
        }

        public void onTechnologyStarted(
                @NonNull RangingDevice peer, @NonNull RangingTechnology technology
        ) {
            if (!mIsSessionStarted.getAndSet(true)) {
                try {
                    mRangingCallbacks.onOpened(mSessionHandle);
                } catch (RemoteException e) {
                    Log.e(TAG, "onOpened callback failed: " + e);
                }
            }
            try {
                mRangingCallbacks.onStarted(mSessionHandle, peer, technology.getValue());
            } catch (RemoteException e) {
                Log.e(TAG, "onTechnologyStarted callback failed: " + e);
            }
        }

        public void onTechnologyStopped(
                @NonNull RangingDevice peer, @NonNull RangingTechnology technology
        ) {
            try {
                mRangingCallbacks.onStopped(mSessionHandle, peer, technology.getValue());
            } catch (RemoteException e) {
                Log.e(TAG, "onTechnologyStopped callback failed: " + e);
            }
        }

        public void onResults(
                @NonNull RangingDevice peer, @NonNull RangingData data
        ) {
            try {
                mRangingCallbacks.onResults(mSessionHandle, peer, data);
            } catch (RemoteException e) {
                Log.e(TAG, "onData callback failed: " + e);
            }
        }

        /**
         * Signals that ranging in the session has stopped. Called by a {@link RangingSession} once
         * all of its constituent technology-specific sessions have stopped.
         */
        public void onSessionStopped(@RangingAdapter.Callback.ClosedReason int reason) {
            mSessions.remove(mSessionHandle);
            if (mIsSessionStarted.get()) {
                try {
                    mRangingCallbacks.onClosed(mSessionHandle, convertReason(reason));
                } catch (RemoteException e) {
                    Log.e(TAG, "onClosed callback failed: " + e);
                }
            } else {
                try {
                    mRangingCallbacks.onOpenFailed(mSessionHandle, convertReason(reason));
                } catch (RemoteException e) {
                    Log.e(TAG, "onOpenFailed callback failed: " + e);
                }
            }
        }

        private @Callback.Reason int convertReason(
                @RangingAdapter.Callback.ClosedReason int reason
        ) {
            switch (reason) {
                case RangingAdapter.Callback.ClosedReason.REQUESTED:
                    return REASON_LOCAL_REQUEST;
                case RangingAdapter.Callback.ClosedReason.FAILED_TO_START:
                    return REASON_UNSUPPORTED;
                case RangingAdapter.Callback.ClosedReason.LOST_CONNECTION:
                    return REASON_NO_PEERS_FOUND;
                case RangingAdapter.Callback.ClosedReason.SYSTEM_POLICY:
                    return REASON_SYSTEM_POLICY;
                default:
                    return REASON_UNKNOWN;
            }
        }
    }

    private class RangingTaskManager extends Handler {
        RangingTaskManager(Looper looper) {
            super(looper);
        }

        public void enqueueTask(RangingTask task, Object obj) {
            Message msg = mRangingTaskManager.obtainMessage();
            msg.what = task.getValue();
            msg.obj = obj;
            this.sendMessage(msg);
        }

        public void enqueueTask(RangingTask task, Object obj, int arg1) {
            Message msg = mRangingTaskManager.obtainMessage();
            msg.what = task.getValue();
            msg.obj = obj;
            msg.arg1 = arg1;
            this.sendMessage(msg);
        }

        @Override
        public void handleMessage(Message msg) {
            RangingTask task = RangingTask.fromValue(msg.what);
            switch (task) {
                case TASK_START_RANGING -> handleStartRanging((StartRangingArgs) msg.obj);
                case TASK_STOP_RANGING -> {
                    RangingSession rangingSession = (RangingSession) msg.obj;
                    rangingSession.stop();
                }
                case TASK_ADD_DEVICE -> {
                    DynamicPeer peer = (DynamicPeer) msg.obj;
                    peer.mSession.addPeer(peer.mParams);
                }
                case TASK_REMOVE_DEVICE -> {
                    DynamicPeer peer = (DynamicPeer) msg.obj;
                    peer.mSession.removePeer(peer.mRangingDevice);
                }
                case TASK_RECONFIGURE_INTERVAL -> {
                    RangingSession session = (RangingSession) msg.obj;
                    session.reconfigureInterval(msg.arg1);
                }
            }
        }

        public record StartRangingArgs(
                AttributionSource attributionSource,
                SessionHandle handle,
                RangingPreference preference,
                IRangingCallbacks callbacks
        ) {
        }

        public void handleStartRanging(StartRangingArgs args) {
            RangingSessionConfig config = new RangingSessionConfig.Builder()
                    .setDeviceRole(
                            args.preference.getDeviceRole())
                    .setSessionConfig(args.preference().getSessionConfiguration())
                    .build();

            RangingParams baseParams = args.preference.getRangingParameters();
            if (baseParams instanceof RawInitiatorRangingParams params) {
                RawInitiatorRangingSession session = new RawInitiatorRangingSession(
                        args.attributionSource, args.handle, mRangingInjector, config,
                        new SessionListener(args.handle, args.callbacks), mAdapterExecutor
                );
                session.start(params);
                mSessions.put(args.handle, session);
            } else if (baseParams instanceof RawResponderRangingParams params) {
                RawResponderRangingSession session = new RawResponderRangingSession(
                        args.attributionSource, args.handle, mRangingInjector, config,
                        new SessionListener(args.handle, args.callbacks), mAdapterExecutor
                );
                session.start(params);
                mSessions.put(args.handle, session);
            } else if (baseParams instanceof OobInitiatorRangingParams params) {
                OobInitiatorRangingSession session = new OobInitiatorRangingSession(
                        args.attributionSource, args.handle, mRangingInjector, config,
                        new SessionListener(args.handle, args.callbacks), mOobDataSender,
                        mAdapterExecutor
                );
                session.start(params);
                mSessions.put(args.handle, session);
            } else if (baseParams instanceof OobResponderRangingParams params) {
                OobResponderRangingSession session = new OobResponderRangingSession(
                        args.attributionSource, args.handle, mRangingInjector, config,
                        new SessionListener(args.handle, args.callbacks), mOobDataSender,
                        mAdapterExecutor
                );
                session.start(params);
                mSessions.put(args.handle, session);
            }
        }
    }

    public static final class DynamicPeer {
        public final RangingDevice mRangingDevice;
        public final RawResponderRangingParams mParams;
        public final RangingSession<?> mSession;

        public DynamicPeer(RawResponderRangingParams params, RangingSession<?> session,
                RangingDevice device) {
            mParams = params;
            mSession = session;
            mRangingDevice = device;
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("---- Dump of RangingServiceManager ----");
        for (RangingSession<?> session : mSessions.values()) {
            session.dump(fd, pw, args);
        }
        pw.println("---- Dump of RangingServiceManager ----");
        mRangingInjector.getCapabilitiesProvider().dump(fd, pw, args);
    }
}
