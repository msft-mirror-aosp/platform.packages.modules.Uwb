/*
 * Copyright 2024 The Android Open Source Project
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

package android.ranging;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.AttributionSource;
import android.os.CancellationSignal;
import android.os.RemoteException;
import android.ranging.params.DeviceHandle;
import android.ranging.params.OobInitiatorRangingParams;
import android.ranging.params.OobResponderRangingParams;
import android.ranging.params.RangingParams;
import android.util.Log;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;


/**
 * Represents a session for performing ranging operations. A {@link RangingSession} manages
 * the lifecycle of a ranging operation, including start, stop, and event callbacks.
 *
 * <p>All methods are asynchronous and rely on the provided {@link Executor} to invoke
 * callbacks on appropriate threads.
 *
 * <p>This class implements {@link AutoCloseable}, ensuring that resources can be
 * automatically released when the session is closed.
 *
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class RangingSession implements AutoCloseable {
    private static final String TAG = "RangingSession";
    private final AttributionSource mAttributionSource;
    private final SessionHandle mSessionHandle;
    private final IRangingAdapter mRangingAdapter;
    private final RangingSessionManager mRangingSessionManager;
    private final Callback mCallback;
    private final Executor mExecutor;
    private final Map<RangingDevice, ITransportHandle> mTransportHandles =
            new ConcurrentHashMap<>();


    /**
     * @hide
     */
    public RangingSession(RangingSessionManager rangingSessionManager,
            AttributionSource attributionSource,
            SessionHandle sessionHandle, IRangingAdapter rangingAdapter,
            Callback callback, Executor executor) {
        mRangingSessionManager = rangingSessionManager;
        mAttributionSource = attributionSource;
        mSessionHandle = sessionHandle;
        mRangingAdapter = rangingAdapter;
        mCallback = callback;
        mExecutor = executor;
    }

    /**
     * Starts the ranging session with the provided ranging preferences.
     * <p>The {@link Callback#onOpened()} will be called when the session finishes starting.
     *
     * <p>The provided {@link RangingPreference} determines the configuration for the session.
     * A {@link CancellationSignal} is returned to allow the caller to cancel the session
     * if needed. If the session is canceled, the {@link #close()} method will be invoked
     * automatically to release resources.
     *
     * @param rangingPreference {@link RangingPreference} the preferences for configuring the
     *                          ranging session.
     * @return a {@link CancellationSignal} to close the session.
     */
    @NonNull
    public CancellationSignal start(@NonNull RangingPreference rangingPreference) {
        //TODO : check whether this needs to be called after start, or handle when a session is
        // created in ranging service.
        if (rangingPreference.getRangingParameters().getRangingSessionType()
                == RangingParams.RANGING_SESSION_OOB) {
            mRangingSessionManager.registerOobSendDataListener();
            setupTransportHandles(rangingPreference);
        }
        try {
            mRangingAdapter.startRanging(mAttributionSource, mSessionHandle, rangingPreference,
                    mRangingSessionManager);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        CancellationSignal cancellationSignal = new CancellationSignal();
        cancellationSignal.setOnCancelListener(this::close);

        return cancellationSignal;
    }

    private void setupTransportHandles(RangingPreference rangingPreference) {
        List<DeviceHandle> deviceHandleList = new ArrayList<>();
        if (rangingPreference.getRangingParameters() instanceof OobInitiatorRangingParams) {
            deviceHandleList.addAll(((OobInitiatorRangingParams)
                    rangingPreference.getRangingParameters()).getDeviceHandles());
        } else if (rangingPreference.getRangingParameters() instanceof OobResponderRangingParams) {
            deviceHandleList.add(((OobResponderRangingParams)
                    rangingPreference.getRangingParameters()).getDeviceHandle());
        }
        for (DeviceHandle deviceHandle : deviceHandleList) {
            TransportHandleReceiveCallback receiveCallback =
                    new TransportHandleReceiveCallback(deviceHandle.getRangingDevice());
            deviceHandle.getTransportHandle().registerReceiveCallback(receiveCallback);
            mTransportHandles.put(deviceHandle.getRangingDevice(),
                    deviceHandle.getTransportHandle());
        }
    }

    /**
     * Stops the ranging session.
     *
     * <p>This method releases any ongoing ranging operations. If the operation fails,
     * it will propagate a {@link RemoteException} from the system server.
     */
    public void stop() {
        try {
            mRangingAdapter.stopRanging(mSessionHandle);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     */
    public void onOpened() {
        mExecutor.execute(mCallback::onOpened);
    }

    /**
     * @hide
     */
    public void onOpenFailed(@Callback.Reason int reason) {
        mExecutor.execute(() -> mCallback.onOpenFailed(reason));
    }

    /**
     * @hide
     */
    public void onStarted(RangingDevice peer, @RangingManager.RangingTechnology int technology) {
        mExecutor.execute(() -> mCallback.onStarted(peer, technology));
    }

    /**
     * @hide
     */
    public void onResults(RangingDevice peer, RangingData data) {
        mExecutor.execute(() -> mCallback.onResults(peer, data));
    }

    /**
     * @hide
     */
    public void onStopped(RangingDevice peer, @RangingManager.RangingTechnology int technology) {
        mExecutor.execute(() -> mCallback.onStopped(peer, technology));
    }

    /**
     * @hide
     */
    public void onClosed(@Callback.Reason int reason) {
        mExecutor.execute(() -> mCallback.onClosed(reason));
    }

    /**
     * @hide
     */
    void sendOobData(RangingDevice toDevice, byte[] data) {
        if (!mTransportHandles.containsKey(toDevice)) {
            Log.e(TAG, "TransportHandle not found for session: " + mSessionHandle + ", device: "
                    + toDevice);
        }
        mTransportHandles.get(toDevice).sendData(data);
    }

    /**
     * @hide
     */
    @Override
    public void close() {
        stop();
    }

    /**
     * Callback interface for receiving ranging session events.
     */
    public interface Callback {
        /**
         * @hide
         */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(value = {
                REASON_UNKNOWN,
                REASON_LOCAL_REQUEST,
                REASON_UNSUPPORTED,
                REASON_SYSTEM_POLICY,
                REASON_NO_PEERS_FOUND,
        })
        @interface Reason {
        }

        /**
         * Indicates that the session was closed due to an unknown reason.
         */
        int REASON_UNKNOWN = 0;

        /**
         * Indicates that the session was closed because {@link AutoCloseable#close()} or
         * {@link RangingSession#stop()} was called.
         */
        int REASON_LOCAL_REQUEST = 1;

        /**
         * Indicates that the session was closed at the request of a remote peer.
         * @hide
         */
        // TODO(shreshtabm): Add to @{link Reason} defined above once new callbacks are approved
        int REASON_REMOTE_REQUEST = 2;

        /**
         * Indicates that the session closed because the provided session parameters were not
         * supported.
         */
        int REASON_UNSUPPORTED = 3;

        // TODO(shreshtabm): Remove once new callbacks are approved
        int REASON_SYSTEM_POLICY = 2;

        /**
         * Indicates that the local system policy forced the session to close, such
         * as privacy policy, power management policy, permissions, and more.
         * @hide
         */
        // TODO(shreshtabm): Rename to REASON_SYSTEM_POLICY once new callbacks are approved
        int _REASON_SYSTEM_POLICY = 4;

        /**
         * Indicates that the session was closed because none of the specified peers were found.
         * @hide
         */
        int REASON_NO_PEERS_FOUND = 5;

        /**
         * Called when the ranging session opens successfully.
         * @hide
         */
        void onOpened();

        /**
         * Called when the ranging session failed to open.
         *
         * @param reason the reason for the failure, limited to values defined by
         *               {@link Reason}.
         * @hide
         */
        void onOpenFailed(@Reason int reason);

        /**
         * Called when ranging has started with a particular peer using a particular technology
         * during an ongoing session.
         *
         * @param peer       {@link RangingDevice} the peer with which ranging has started.
         * @param technology {@link android.ranging.RangingManager.RangingTechnology}
         *                   the ranging technology that started.
         * @hide
         */
        void onStarted(
                @NonNull RangingDevice peer, @RangingManager.RangingTechnology int technology);

        /**
         * Called when ranging data has been received from a peer.
         *
         * @param peer {@link RangingDevice} the peer from which ranging data was received.
         * @param data {@link RangingData} the received.
         */
        void onResults(@NonNull RangingDevice peer, @NonNull RangingData data);

        /**
         * Called when ranging has stopped with a particular peer using a particular technology
         * during an ongoing session.
         *
         * @param peer       {@link RangingDevice} the peer with which ranging has stopped.
         * @param technology {@link android.ranging.RangingManager.RangingTechnology}
         *                   the ranging technology that stopped.
         * @hide
         */
        void onStopped(
                @NonNull RangingDevice peer, @RangingManager.RangingTechnology int technology
        );

        /**
         * Called when the ranging session has closed.
         *
         * @param reason the reason why the session was closed, limited to values
         *               defined by {@link Reason}.
         */
        void onClosed(@Reason int reason);


        // TODO(shreshtabm): Remove once new callbacks are approved
        void onStartFailed(int reason, @NonNull android.ranging.RangingDevice peer);

        // TODO(shreshtabm): Remove once new callbacks are approved
        void onStarted(int technology);

        // TODO(shreshtabm): Remove once new callbacks are approved
        void onStopped(@NonNull android.ranging.RangingDevice peer);
    }

    class TransportHandleReceiveCallback implements ITransportHandle.ReceiveCallback {

        private final OobHandle mOobHandle;

        TransportHandleReceiveCallback(RangingDevice device) {
            mOobHandle = new OobHandle(mSessionHandle, device);
        }

        @Override
        public void onReceiveData(byte[] data) {
            mRangingSessionManager.oobDataReceived(mOobHandle, data);
        }

        @Override
        public void onDisconnect() {
            mRangingSessionManager.deviceOobDisconnected(mOobHandle);
        }

        @Override
        public void onReconnect() {
            mRangingSessionManager.deviceOobReconnected(mOobHandle);
        }

        @Override
        public void onClose() {
            mRangingSessionManager.deviceOobClosed(mOobHandle);
        }
    }
}
