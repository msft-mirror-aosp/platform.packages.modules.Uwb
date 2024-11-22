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

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.os.Binder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.ranging.IRangingCapabilitiesCallback;
import android.ranging.RangingCapabilities;
import android.ranging.RangingCapabilities.RangingTechnologyAvailability;
import android.ranging.RangingCapabilities.TechnologyCapabilities;
import android.ranging.RangingManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.blerssi.BleRssiCapabilitiesAdapter;
import com.android.server.ranging.cs.CsCapabilitiesAdapter;
import com.android.server.ranging.rtt.RttCapabilitiesAdapter;
import com.android.server.ranging.uwb.UwbCapabilitiesAdapter;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class CapabilitiesProvider {

    /**
     * An adapter to the capability and availability APIs exposed by an underlying technology's
     * stack.
     */
    public abstract static class CapabilitiesAdapter {
        private AvailabilityCallback mAvailabilityCallback = null;

        /**
         * Register a callback to notify when availability changes. This callback will get called
         * once on registration with the initial availability.
         */
        public void registerAvailabilityCallback(@Nullable AvailabilityCallback callback) {
            mAvailabilityCallback = callback;
            if (mAvailabilityCallback != null) {
                mAvailabilityCallback.onAvailabilityChange(
                        getAvailability(),
                        AvailabilityCallback.AvailabilityChangedReason.UNKNOWN);
            }
        }

        public abstract @RangingTechnologyAvailability int getAvailability();

        public abstract @Nullable TechnologyCapabilities getCapabilities();

        protected @Nullable AvailabilityCallback getAvailabilityCallback() {
            return mAvailabilityCallback;
        }
    }

    public interface AvailabilityCallback {
        @IntDef({
                AvailabilityChangedReason.UNKNOWN,
                AvailabilityChangedReason.SYSTEM_POLICY,
        })
        @interface AvailabilityChangedReason {
            int UNKNOWN = 0;
            int SYSTEM_POLICY = 1;
        }

        /** Indicates that the availability of the underlying technology has changed. */
        void onAvailabilityChange(
                @RangingTechnologyAvailability int availability,
                @AvailabilityChangedReason int reason);
    }

    private static final String TAG = CapabilitiesProvider.class.getSimpleName();
    private final RangingInjector mRangingInjector;
    private final Map<Integer, CapabilitiesAdapter> mCapabilityAdapters;

    /** Callbacks provided from the framework */
    private final RemoteCallbackList<IRangingCapabilitiesCallback> mCallbacks =
            new RemoteCallbackList<>();

    public CapabilitiesProvider(RangingInjector rangingInjector) {
        mRangingInjector = rangingInjector;
        mCapabilityAdapters = new HashMap<>();
    }

    /**
     * Registers an availability listener for each technology supported by the ranging API. This
     * needs to be called after the system services for these technologies have been started, but
     * before any capabilities callbacks are registered through the ranging api.
     */
    public synchronized void registerTechnologyAvailabilityListeners() {
        Log.i(TAG, "Registering availability listeners for each technology");
        mCapabilityAdapters.put(
                RangingManager.UWB,
                new UwbCapabilitiesAdapter(mRangingInjector.getContext()));
        mCapabilityAdapters.put(
                RangingManager.BLE_CS,
                new CsCapabilitiesAdapter(mRangingInjector.getContext()));
        mCapabilityAdapters.put(
                RangingManager.WIFI_NAN_RTT,
                new RttCapabilitiesAdapter(mRangingInjector.getContext()));
        mCapabilityAdapters.put(RangingManager.BLE_RSSI,
                new BleRssiCapabilitiesAdapter(mRangingInjector.getContext()));

        for (@RangingManager.RangingTechnology int technology : mCapabilityAdapters.keySet()) {
            mCapabilityAdapters
                    .get(technology)
                    .registerAvailabilityCallback(new AvailabilityListener(technology));
        }
    }

    public synchronized void registerCapabilitiesCallback(
            @NonNull IRangingCapabilitiesCallback callback
    ) {
        mCallbacks.register(callback);
        try {
            callback.onRangingCapabilities(getCapabilities().build());
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to call provided capabilities callback", e);
        }
    }

    public synchronized void unregisterCapabilitiesCallback(
            @NonNull IRangingCapabilitiesCallback callback
    ) {
        mCallbacks.unregister(callback);
    }

    private RangingCapabilities.Builder getCapabilities() {
        RangingCapabilities.Builder builder = new RangingCapabilities.Builder();
        for (@RangingManager.RangingTechnology int technology : mCapabilityAdapters.keySet()) {
            CapabilitiesAdapter adapter = mCapabilityAdapters.get(technology);
            // Any calls to the corresponding technology stacks must be
            // done with a clear calling identity.
            long token = Binder.clearCallingIdentity();
            TechnologyCapabilities capabilities = adapter.getCapabilities();
            builder.addAvailability(technology, adapter.getAvailability());
            if (capabilities != null) {
                builder.addCapabilities(capabilities);
            }
            Binder.restoreCallingIdentity(token);
        }
        return builder;
    }


    private class AvailabilityListener implements AvailabilityCallback {
        private final @RangingManager.RangingTechnology int mTechnology;

        AvailabilityListener(@RangingManager.RangingTechnology int technology) {
            mTechnology = technology;
        }

        @Override
        public void onAvailabilityChange(
                @RangingTechnologyAvailability int availability,
                @AvailabilityChangedReason int unused
        ) {
            RangingCapabilities capabilities = getCapabilities()
                    .addAvailability(mTechnology, availability)
                    .build();
            synchronized (mCallbacks) {
                int i = mCallbacks.beginBroadcast();
                while (i > 0) {
                    i--;
                    try {
                        mCallbacks.getBroadcastItem(i).onRangingCapabilities(capabilities);
                    } catch (RemoteException e) {
                        Log.w(TAG, "Failed to notify callback " + i + " of availability change");
                    }
                }
                mCallbacks.finishBroadcast();
            }
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("---- Dump of CapabilitiesProvider ----");
        for (Entry<Integer, CapabilitiesAdapter> adapter : mCapabilityAdapters.entrySet()) {
            pw.println("-- Dump of CapabilitiesAdapter for technology " + adapter.getKey() + " --");
            pw.println("Availability: " + adapter.getValue().getAvailability());
            pw.println("Capabilities: " + adapter.getValue().getCapabilities());
            pw.println("-- Dump of CapabilitiesAdapter for technology " + adapter.getKey() + " --");
        }
        pw.println("---- Dump of CapabilitiesProvider ----");
    }
}
