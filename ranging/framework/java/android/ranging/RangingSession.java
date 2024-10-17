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
import android.annotation.NonNull;
import android.content.AttributionSource;
import android.os.CancellationSignal;
import android.os.RemoteException;
import android.util.Log;

import com.android.ranging.flags.Flags;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;


/**
 * @hide
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

    public CancellationSignal start(RangingPreference rangingPreference) {
        try {
            mRangingAdapter.startRanging(mAttributionSource, mSessionHandle, rangingPreference,
                    mRangingSessionManager);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        CancellationSignal cancellationSignal = new CancellationSignal();
        cancellationSignal.setOnCancelListener(() -> this.close());

        // TODO: if SmartRangingParams then setupTransportHandles();
        return cancellationSignal;
    }

    private void setupTransportHandles() {
        // TODO: Add TransportHandle initialization
        //      foreach deviceHandle:
        //          mTransportHandles.add(rangingDevice, transportHandle)
        //          transportHandle.registerReceiveCallback(new TransportHandleReceiveCallback
        //          (device))
    }

    public void stop() {
        try {
            mRangingAdapter.stopRanging(mSessionHandle);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void onRangingStarted(int technology) {
        mExecutor.execute(() -> mCallback.onStarted(technology));
    }

    public void onRangingClosed(int reason) {
        mExecutor.execute(() -> mCallback.onClosed(reason));
    }

    public void onData(RangingDevice device, RangingData data) {
        mExecutor.execute(() -> mCallback.onResults(device, data));
    }

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

    public interface Callback {
        void onStarted(@RangingManager.RangingTechnology int technology);

        void onStartFailed(int reason);

        void onClosed(int reasonCode);

        /*public void onRangingStarted(@NonNull RangingStartedParameters
                params);*/
        void onRangingStopped(@NonNull RangingDevice device);

        void onResults(@NonNull RangingDevice device, @NonNull RangingData data);
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
