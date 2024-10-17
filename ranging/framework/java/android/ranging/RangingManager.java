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

import android.annotation.CallbackExecutor;
import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemService;
import android.content.AttributionSource;
import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.concurrent.Executor;


/**
 * This class provides a way to perform ranging operations such as querying the
 * device's capabilities and determining the distance and angle between the local device and a
 * remote device.
 *
 * <p>To get a {@link RangingManager}, call the
 * <code>Context.getSystemService(RangingManager.class)</code>.
 *
 * @hide
 */

@SystemService(Context.RANGING_SERVICE)
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class RangingManager {
    private static final String TAG = "RangingManager";

    private final Context mContext;
    private final IRangingAdapter mRangingAdapter;

    private final RangingSessionManager mRangingSessionManager;

    /**
     * The interface Ranging technology.
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            RangingTechnology.UWB,
            RangingTechnology.BT_CS,
            RangingTechnology.WIFI_RTT,
            RangingTechnology.BLE_RSSI,
    })
    public @interface RangingTechnology {
        /**
         * Ultra-Wideband (UWB) technology.
         */
        int UWB = 0;

        /**
         * Bluetooth Channel Sounding (BT-CS) technology.
         */
        int BT_CS = 1;

        /**
         * WiFi Round Trip Time (WiFi-RTT) technology.
         */
        int WIFI_RTT = 2;

        /**
         * Bluetooth Low Energy (BLE) RSSI-based ranging technology.
         */
        int BLE_RSSI = 3;
    }

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            /* Ranging technology is not supported on this device. */
            RangingTechnologyAvailability.NOT_SUPPORTED,
            /* Ranging technology is disabled. */
            RangingTechnologyAvailability.DISABLED_USER,
            /* Ranging technology disabled due to regulation. */
            RangingTechnologyAvailability.DISABLED_REGULATORY,
            /* Ranging technology is enabled. */
            RangingTechnologyAvailability.ENABLED,
    })
    public @interface RangingTechnologyAvailability {
        /**
         * Indicates that the ranging technology is not supported on the current device.
         */
        int NOT_SUPPORTED = 0;

        /**
         * Indicates that the ranging technology is disabled by the user.
         */
        int DISABLED_USER = 1;

        /**
         * Indicates that the ranging technology is disabled due to regulatory restrictions.
         */
        int DISABLED_REGULATORY = 2;

        /**
         * Indicates that the ranging technology is enabled and available for use.
         */
        int ENABLED = 3;
    }


    /**
     * @hide
     */
    public RangingManager(@NonNull Context context, @NonNull IRangingAdapter adapter) {
        mContext = context;
        mRangingAdapter = adapter;
        mRangingSessionManager = new RangingSessionManager(adapter);
    }

    /**
     * Gets all the available ranging  technologies and ranging capabilities supported by the
     * device. This method is asynchronous and will invoke the provided
     * {@link RangingCapabilitiesListener#onRangingCapabilities} with {@link RangingCapabilities}.
     *
     * <p>Ranging capabilities may include support for technologies such as
     * UWB (Ultra Wideband), WiFi RTT (Round Trip Time), and channel sounding.
     *
     * <p>This method uses a callback mechanism to deliver the results on the
     * provided {@link Executor}.
     *
     * @param executor the {@link Executor} on which the listener will be invoked.
     * @param listener the {@link RangingCapabilitiesListener} to be notified
     *                 when the ranging availability and capabilities.
     *
     * @throws NullPointerException if {@code executor} or {@code listener} is null.
     */
    @NonNull
    public void getRangingCapabilities(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull RangingCapabilitiesListener listener) {
        Objects.requireNonNull(executor, "Executor cannot be null");
        Objects.requireNonNull(listener, "Capabilities listener cannot be null");
        IRangingCapabilitiesCallback.Stub rangingCapabilitiesCallback =
                new IRangingCapabilitiesCallback.Stub() {

                    @Override
                    public void onRangingCapabilities(
                            RangingCapabilities rangingCapabilities)
                            throws RemoteException {
                        executor.execute(() -> listener.onRangingCapabilities(
                                rangingCapabilities));
                    }
                };
        try {
            mRangingAdapter.getRangingCapabilities(rangingCapabilitiesCallback);
        } catch (RemoteException e) {
            Log.e(TAG, "Get capabilities failed" + e.toString());
        }
    }

    /**
     * Creates a new ranging session. A ranging session enables the application
     * to perform ranging operations using available technologies such as
     * UWB (Ultra-Wideband) or WiFi RTT (Round Trip Time).
     *
     * <p>This method returns a {@link RangingSession} instance, which can be
     * used to initiate, manage, and stop ranging operations. The provided
     * {@link RangingSession.Callback} will be used to receive session-related
     * events, such as session start, stop, and ranging updates.
     *
     * <p>It is recommended to provide an appropriate {@link Executor} to ensure
     * that callback events are handled on a suitable thread.
     *
     * @param callback the {@link RangingSession.Callback} to handle session-related events.
     *                 Must not be {@code null}.
     * @param executor the {@link Executor} on which the callback will be invoked.
     *                 Must not be {@code null}.
     * @return the {@link RangingSession} instance if the session was successfully created,
     *         or {@code null} if the session could not be created.
     * @throws NullPointerException if {@code callback} or {@code executor} is null.
     * @throws SecurityException if the calling app does not have the necessary permissions
     *                           to create a ranging session.
     */
    @Nullable
    public RangingSession createRangingSession(@NonNull Executor executor,
            @NonNull RangingSession.Callback callback) {
        Objects.requireNonNull(executor, "Executor cannot be null");
        Objects.requireNonNull(callback, "Callback cannot be null");
        return createRangingSessionInternal(mContext.getAttributionSource(), callback, executor);
    }

    private RangingSession createRangingSessionInternal(AttributionSource attributionSource,
            RangingSession.Callback callback, Executor executor) {
        return mRangingSessionManager.createRangingSessionInstance(attributionSource, callback,
                executor);
    }
}
