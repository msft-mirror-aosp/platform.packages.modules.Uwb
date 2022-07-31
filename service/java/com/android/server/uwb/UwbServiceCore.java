/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.uwb;

import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD;
import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE;

import android.content.AttributionSource;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;
import android.uwb.IUwbAdapterStateCallbacks;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.IUwbVendorUciCallback;
import android.uwb.RangingChangeReason;
import android.uwb.SessionHandle;
import android.uwb.StateChangeReason;
import android.uwb.UwbManager.AdapterStateCallback;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.data.UwbVendorUciResponse;
import com.android.server.uwb.jni.INativeUwbManager;
import com.android.server.uwb.jni.NativeUwbManager;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccRangingReconfiguredParams;
import com.google.uwb.support.ccc.CccStartRangingParams;
import com.google.uwb.support.fira.FiraControleeParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;
import com.google.uwb.support.generic.GenericParams;
import com.google.uwb.support.generic.GenericSpecificationParams;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Core UWB stack.
 */
public class UwbServiceCore implements INativeUwbManager.DeviceNotification,
        INativeUwbManager.VendorNotification, UwbCountryCode.CountryCodeChangedListener {
    private static final String TAG = "UwbServiceCore";

    @VisibleForTesting
    public static final int TASK_ENABLE = 1;
    @VisibleForTesting
    public static final int TASK_DISABLE = 2;
    @VisibleForTesting
    public static final int TASK_RESTART = 3;

    private static final int WATCHDOG_MS = 10000;
    private static final int SEND_VENDOR_CMD_TIMEOUT_MS = 10000;

    private final PowerManager.WakeLock mUwbWakeLock;
    private final Context mContext;
    // TODO: Use RemoteCallbackList instead.
    private final ConcurrentHashMap<Integer, AdapterInfo> mAdapterMap = new ConcurrentHashMap<>();
    private final EnableDisableTask mEnableDisableTask;

    private final UwbSessionManager mSessionManager;
    private final UwbConfigurationManager mConfigurationManager;
    private final NativeUwbManager mNativeUwbManager;
    private final UwbMetrics mUwbMetrics;
    private final UwbCountryCode mUwbCountryCode;
    private final UwbInjector mUwbInjector;
    private /* @UwbManager.AdapterStateCallback.State */ int mState;
    private @StateChangeReason int mLastStateChangedReason;
    private  IUwbVendorUciCallback mCallBack = null;
    private final Handler mHandler;
    private GenericSpecificationParams mCachedSpecificationParams;

    public UwbServiceCore(Context uwbApplicationContext, NativeUwbManager nativeUwbManager,
            UwbMetrics uwbMetrics, UwbCountryCode uwbCountryCode,
            UwbSessionManager uwbSessionManager, UwbConfigurationManager uwbConfigurationManager,
            UwbInjector uwbInjector, Looper serviceLooper) {
        mContext = uwbApplicationContext;

        Log.d(TAG, "Starting Uwb");

        mUwbWakeLock = mContext.getSystemService(PowerManager.class).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "UwbServiceCore:mUwbWakeLock");

        mNativeUwbManager = nativeUwbManager;

        mNativeUwbManager.setDeviceListener(this);
        mNativeUwbManager.setVendorListener(this);
        mUwbMetrics = uwbMetrics;
        mUwbCountryCode = uwbCountryCode;
        mUwbCountryCode.addListener(this);
        mSessionManager = uwbSessionManager;
        mConfigurationManager = uwbConfigurationManager;
        mUwbInjector = uwbInjector;

        updateState(AdapterStateCallback.STATE_DISABLED, StateChangeReason.SYSTEM_BOOT);

        mEnableDisableTask = new EnableDisableTask(serviceLooper);
        mHandler = new Handler(serviceLooper);
    }

    public Handler getHandler() {
        return mHandler;
    }

    private void updateState(int state, int reason) {
        synchronized (UwbServiceCore.this) {
            mState = state;
            mLastStateChangedReason = reason;
        }
    }

    private boolean isUwbEnabled() {
        synchronized (UwbServiceCore.this) {
            return (mState == AdapterStateCallback.STATE_ENABLED_ACTIVE
                    || mState == AdapterStateCallback.STATE_ENABLED_INACTIVE);
        }
    }

    String getDeviceStateString(int state) {
        String ret = "";
        switch (state) {
            case UwbUciConstants.DEVICE_STATE_OFF:
                ret = "OFF";
                break;
            case UwbUciConstants.DEVICE_STATE_READY:
                ret = "READY";
                break;
            case UwbUciConstants.DEVICE_STATE_ACTIVE:
                ret = "ACTIVE";
                break;
            case UwbUciConstants.DEVICE_STATE_ERROR:
                ret = "ERROR";
                break;
        }
        return ret;
    }

    @Override
    public void onVendorUciNotificationReceived(int gid, int oid, byte[] payload) {
        Log.i(TAG, "onVendorUciNotificationReceived");
        if (mCallBack != null) {
            try {
                mCallBack.onVendorNotificationReceived(gid, oid, payload);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send vendor notification", e);
            }
        }
    }

    @Override
    public void onDeviceStatusNotificationReceived(int deviceState) {
        // If error status is received, toggle UWB off to reset stack state.
        // TODO(b/227488208): Should we try to restart (like wifi) instead?
        if ((byte) deviceState == UwbUciConstants.DEVICE_STATE_ERROR) {
            Log.e(TAG, "Error device status received. Restarting...");
            mUwbMetrics.incrementDeviceStatusErrorCount();
            takBugReportAfterDeviceError("UWB Bugreport: restarting UWB due to device error");
            mEnableDisableTask.execute(TASK_RESTART);
            return;
        }
        handleDeviceStatusNotification(deviceState);
    }

    void handleDeviceStatusNotification(int deviceState) {
        Log.i(TAG, "handleDeviceStatusNotification = " + getDeviceStateString(deviceState));
        int state = AdapterStateCallback.STATE_DISABLED;
        int reason = StateChangeReason.UNKNOWN;

        if (deviceState == UwbUciConstants.DEVICE_STATE_OFF) {
            state = AdapterStateCallback.STATE_DISABLED;
            reason = StateChangeReason.SYSTEM_POLICY;
        } else if (deviceState == UwbUciConstants.DEVICE_STATE_READY) {
            state = AdapterStateCallback.STATE_ENABLED_INACTIVE;
            reason = StateChangeReason.SYSTEM_POLICY;
        } else if (deviceState == UwbUciConstants.DEVICE_STATE_ACTIVE) {
            state = AdapterStateCallback.STATE_ENABLED_ACTIVE;
            reason = StateChangeReason.SESSION_STARTED;
        }

        updateState(state, reason);

        for (AdapterInfo adapter : mAdapterMap.values()) {
            try {
                adapter.getAdapterStateCallbacks().onAdapterStateChanged(state, reason);
            } catch (RemoteException e) {
                Log.e(TAG, "onAdapterStateChanged is failed");
            }
        }
    }

    @Override
    public void onCoreGenericErrorNotificationReceived(int status) {
        Log.e(TAG, "onCoreGenericErrorNotificationReceived status = " + status);
        mUwbMetrics.incrementUciGenericErrorCount();
    }

    @Override
    public void onCountryCodeChanged(@Nullable String countryCode) { }

    public void registerAdapterStateCallbacks(IUwbAdapterStateCallbacks adapterStateCallbacks)
            throws RemoteException {
        AdapterInfo adapter = new AdapterInfo(Binder.getCallingPid(), adapterStateCallbacks);
        mAdapterMap.put(Binder.getCallingPid(), adapter);
        adapter.getBinder().linkToDeath(adapter, 0);
        adapterStateCallbacks.onAdapterStateChanged(mState, mLastStateChangedReason);
    }

    public void unregisterAdapterStateCallbacks(IUwbAdapterStateCallbacks callbacks) {
        int pid = Binder.getCallingPid();
        AdapterInfo adapter = mAdapterMap.get(pid);
        adapter.getBinder().unlinkToDeath(adapter, 0);
        mAdapterMap.remove(pid);
    }

    public void registerVendorExtensionCallback(IUwbVendorUciCallback callbacks) {
        Log.e(TAG, "Register the callback");
        mCallBack = callbacks;
    }

    public void unregisterVendorExtensionCallback(IUwbVendorUciCallback callbacks) {
        Log.e(TAG, "Unregister the callback");
        mCallBack = null;
    }

    /**
     * Get cached specification params
     */
    public GenericSpecificationParams getCachedSpecificationParams(String chipId) {
        if (mCachedSpecificationParams != null) return mCachedSpecificationParams;
        // If nothing in cache, populate it.
        getSpecificationInfo(chipId);
        return mCachedSpecificationParams;
    }

    /**
     * Get specification info
     */
    public PersistableBundle getSpecificationInfo(String chipId) {
        // TODO(b/211445008): Consolidate to a single uwb thread.
        Pair<Integer, GenericSpecificationParams> specificationParams =
                mConfigurationManager.getCapsInfo(
                        GenericParams.PROTOCOL_NAME, GenericSpecificationParams.class, chipId);
        if (specificationParams.first != UwbUciConstants.STATUS_CODE_OK
                || specificationParams.second == null)  {
            Log.e(TAG, "Failed to retrieve specification params");
            return new PersistableBundle();
        }
        mCachedSpecificationParams = specificationParams.second;
        return specificationParams.second.toBundle();
    }

    public long getTimestampResolutionNanos() {
        return mNativeUwbManager.getTimestampResolutionNanos();
    }

    public void openRanging(
            AttributionSource attributionSource,
            SessionHandle sessionHandle,
            IUwbRangingCallbacks rangingCallbacks,
            PersistableBundle params,
            String chipId) throws RemoteException {
        if (!isUwbEnabled()) {
            throw new IllegalStateException("Uwb is not enabled");
        }
        int sessionId = 0;
        if (FiraParams.isCorrectProtocol(params)) {
            FiraOpenSessionParams.Builder builder =
                    new FiraOpenSessionParams.Builder(FiraOpenSessionParams.fromBundle(params));
            if (getCachedSpecificationParams(chipId)
                    .getFiraSpecificationParams().hasRssiReportingSupport()) {
                builder.setIsRssiReportingEnabled(true);
            }
            FiraOpenSessionParams firaOpenSessionParams = builder.build();
            sessionId = firaOpenSessionParams.getSessionId();
            mSessionManager.initSession(attributionSource, sessionHandle, sessionId,
                    firaOpenSessionParams.getProtocolName(),
                    firaOpenSessionParams, rangingCallbacks, chipId);
        } else if (CccParams.isCorrectProtocol(params)) {
            CccOpenRangingParams cccOpenRangingParams = CccOpenRangingParams.fromBundle(params);
            sessionId = cccOpenRangingParams.getSessionId();
            mSessionManager.initSession(attributionSource, sessionHandle, sessionId,
                    cccOpenRangingParams.getProtocolName(),
                    cccOpenRangingParams, rangingCallbacks, chipId);
        } else {
            Log.e(TAG, "openRanging - Wrong parameters");
            try {
                rangingCallbacks.onRangingOpenFailed(sessionHandle,
                        RangingChangeReason.BAD_PARAMETERS, new PersistableBundle());
            } catch (RemoteException e) { }
        }
    }

    public void startRanging(SessionHandle sessionHandle, PersistableBundle params)
            throws IllegalStateException {
        if (!isUwbEnabled()) {
            throw new IllegalStateException("Uwb is not enabled");
        }
        Params  startRangingParams = null;
        if (CccParams.isCorrectProtocol(params)) {
            startRangingParams = CccStartRangingParams.fromBundle(params);
        }
        mSessionManager.startRanging(sessionHandle, startRangingParams);
        return;
    }

    public void reconfigureRanging(SessionHandle sessionHandle, PersistableBundle params) {
        if (!isUwbEnabled()) {
            throw new IllegalStateException("Uwb is not enabled");
        }
        Params  reconfigureRangingParams = null;
        if (FiraParams.isCorrectProtocol(params)) {
            reconfigureRangingParams = FiraRangingReconfigureParams.fromBundle(params);
        } else if (CccParams.isCorrectProtocol(params)) {
            reconfigureRangingParams = CccRangingReconfiguredParams.fromBundle(params);
        }
        mSessionManager.reconfigure(sessionHandle, reconfigureRangingParams);
    }

    public void stopRanging(SessionHandle sessionHandle) {
        if (!isUwbEnabled()) {
            throw new IllegalStateException("Uwb is not enabled");
        }
        mSessionManager.stopRanging(sessionHandle);
    }

    public void closeRanging(SessionHandle sessionHandle) {
        if (!isUwbEnabled()) {
            throw new IllegalStateException("Uwb is not enabled");
        }
        mSessionManager.deInitSession(sessionHandle);
    }

    public void addControlee(SessionHandle sessionHandle, PersistableBundle params) {
        if (!isUwbEnabled()) {
            throw new IllegalStateException("Uwb is not enabled");
        }
        Params  reconfigureRangingParams = null;
        if (FiraParams.isCorrectProtocol(params)) {
            FiraControleeParams controleeParams = FiraControleeParams.fromBundle(params);
            reconfigureRangingParams = new FiraRangingReconfigureParams.Builder()
                    .setAction(MULTICAST_LIST_UPDATE_ACTION_ADD)
                    .setAddressList(controleeParams.getAddressList())
                    .setSubSessionIdList(controleeParams.getSubSessionIdList())
                    .build();
        }
        mSessionManager.reconfigure(sessionHandle, reconfigureRangingParams);
    }

    public void removeControlee(SessionHandle sessionHandle, PersistableBundle params) {
        if (!isUwbEnabled()) {
            throw new IllegalStateException("Uwb is not enabled");
        }
        Params  reconfigureRangingParams = null;
        if (FiraParams.isCorrectProtocol(params)) {
            FiraControleeParams controleeParams = FiraControleeParams.fromBundle(params);
            reconfigureRangingParams = new FiraRangingReconfigureParams.Builder()
                    .setAction(MULTICAST_LIST_UPDATE_ACTION_DELETE)
                    .setAddressList(controleeParams.getAddressList())
                    .setSubSessionIdList(controleeParams.getSubSessionIdList())
                    .build();
        }
        mSessionManager.reconfigure(sessionHandle, reconfigureRangingParams);
    }

    public /* @UwbManager.AdapterStateCallback.State */ int getAdapterState() {
        synchronized (UwbServiceCore.this) {
            return mState;
        }
    }

    public synchronized void setEnabled(boolean enabled) {
        int task = enabled ? TASK_ENABLE : TASK_DISABLE;

        if (enabled && isUwbEnabled()) {
            Log.w(TAG, "Uwb is already enabled");
        } else if (!enabled && !isUwbEnabled()) {
            Log.w(TAG, "Uwb is already disabled");
        }

        mEnableDisableTask.execute(task);
    }

    private void sendVendorUciResponse(int gid, int oid, byte[] payload) {
        Log.i(TAG, "onVendorUciResponseReceived");
        if (mCallBack != null) {
            try {
                mCallBack.onVendorResponseReceived(gid, oid, payload);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send vendor response", e);
            }
        }
    }

    /**
     * Send vendor UCI message
     *
     * @param chipId : Identifier of UWB chip for multi-HAL devices
     */
    public synchronized int sendVendorUciMessage(int gid, int oid, byte[] payload, String chipId) {
        if ((!isUwbEnabled())) {
            Log.e(TAG, "sendRawVendor : Uwb is not enabled");
            return UwbUciConstants.STATUS_CODE_FAILED;
        }
        // TODO(b/211445008): Consolidate to a single uwb thread.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        FutureTask<Byte> sendVendorCmdTask = new FutureTask<>(
                () -> {
                    UwbVendorUciResponse response =
                            mNativeUwbManager.sendRawVendorCmd(gid, oid, payload, chipId);
                    if (response.status == UwbUciConstants.STATUS_CODE_OK) {
                        sendVendorUciResponse(response.gid, response.oid, response.payload);
                    }
                    return response.status;
                });
        executor.submit(sendVendorCmdTask);
        int status = UwbUciConstants.STATUS_CODE_FAILED;
        try {
            status = sendVendorCmdTask.get(
                    SEND_VENDOR_CMD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            executor.shutdownNow();
            Log.i(TAG, "Failed to send vendor command - status : TIMEOUT");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return status;
    }

    private class EnableDisableTask extends Handler {

        EnableDisableTask(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            int type = msg.what;
            switch (type) {
                case TASK_ENABLE:
                    enableInternal();
                    break;

                case TASK_DISABLE:
                    mSessionManager.deinitAllSession();
                    disableInternal();
                    break;

                case TASK_RESTART:
                    mSessionManager.deinitAllSession();
                    disableInternal();
                    enableInternal();
                    break;

                default:
                    Log.d(TAG, "EnableDisableTask : Undefined Task");
                    break;
            }
        }

        public void execute(int task) {
            Message msg = mEnableDisableTask.obtainMessage();
            msg.what = task;
            this.sendMessage(msg);
        }

        private void enableInternal() {
            if (isUwbEnabled()) {
                Log.i(TAG, "UWB service is already enabled");
                return;
            }
            try {
                WatchDogThread watchDog = new WatchDogThread("enableInternal", WATCHDOG_MS);
                watchDog.start();

                Log.i(TAG, "Initialization start ...");
                mUwbWakeLock.acquire();
                try {
                    if (!mNativeUwbManager.doInitialize()) {
                        Log.e(TAG, "Error enabling UWB");
                        mUwbMetrics.incrementDeviceInitFailureCount();
                        takBugReportAfterDeviceError("UWB Bugreport: error enabling UWB");
                        updateState(AdapterStateCallback.STATE_DISABLED,
                                StateChangeReason.SYSTEM_POLICY);
                    } else {
                        Log.i(TAG, "Initialization success");
                        /* TODO : keep it until MW, FW fix b/196943897 */
                        mUwbMetrics.incrementDeviceInitSuccessCount();
                        handleDeviceStatusNotification(UwbUciConstants.DEVICE_STATE_READY);
                        // Set country code on every enable.
                        mUwbCountryCode.setCountryCode(true);
                    }
                } finally {
                    mUwbWakeLock.release();
                    watchDog.cancel();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void disableInternal() {
            if (!isUwbEnabled()) {
                Log.i(TAG, "UWB service is already disabled");
                return;
            }

            WatchDogThread watchDog = new WatchDogThread("disableInternal", WATCHDOG_MS);
            watchDog.start();

            try {
                updateState(AdapterStateCallback.STATE_DISABLED, StateChangeReason.SYSTEM_POLICY);
                Log.i(TAG, "Deinitialization start ...");
                mUwbWakeLock.acquire();

                if (!mNativeUwbManager.doDeinitialize()) {
                    Log.w(TAG, "Error disabling UWB");
                } else {
                    Log.i(TAG, "Deinitialization success");
                    /* UWBS_STATUS_OFF is not the valid state. so handle device state directly */
                    handleDeviceStatusNotification(UwbUciConstants.DEVICE_STATE_OFF);
                }
            } finally {
                mUwbWakeLock.release();
                watchDog.cancel();
            }
        }

        public class WatchDogThread extends Thread {
            final Object mCancelWaiter = new Object();
            final int mTimeout;
            boolean mCanceled = false;

            WatchDogThread(String threadName, int timeout) {
                super(threadName);

                mTimeout = timeout;
            }

            @Override
            public void run() {
                try {
                    synchronized (mCancelWaiter) {
                        mCancelWaiter.wait(mTimeout);
                        if (mCanceled) {
                            return;
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    interrupt();
                }

                if (mUwbWakeLock.isHeld()) {
                    Log.e(TAG, "Release mUwbWakeLock before aborting.");
                    mUwbWakeLock.release();
                }
            }

            public synchronized void cancel() {
                synchronized (mCancelWaiter) {
                    mCanceled = true;
                    mCancelWaiter.notify();
                }
            }
        }
    }

    class AdapterInfo implements IBinder.DeathRecipient {
        private final IBinder mIBinder;
        private IUwbAdapterStateCallbacks mAdapterStateCallbacks;
        private int mPid;

        AdapterInfo(int pid, IUwbAdapterStateCallbacks adapterStateCallbacks) {
            mIBinder = adapterStateCallbacks.asBinder();
            mAdapterStateCallbacks = adapterStateCallbacks;
            mPid = pid;
        }

        public IUwbAdapterStateCallbacks getAdapterStateCallbacks() {
            return mAdapterStateCallbacks;
        }

        public IBinder getBinder() {
            return mIBinder;
        }

        @Override
        public void binderDied() {
            mIBinder.unlinkToDeath(this, 0);
            mAdapterMap.remove(mPid);
        }
    }

    private void takBugReportAfterDeviceError(String bugTitle) {
        if (mUwbInjector.getDeviceConfigFacade().isDeviceErrorBugreportEnabled()) {
            mUwbInjector.getUwbDiagnostics().takeBugReport(bugTitle);
        }
    }

    /**
     * Dump the UWB service status
     */
    public synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("---- Dump of UwbServiceCore ----");
        pw.println("device state = " + getDeviceStateString(mState));
        pw.println("mLastStateChangedReason = " + mLastStateChangedReason);
    }
}
