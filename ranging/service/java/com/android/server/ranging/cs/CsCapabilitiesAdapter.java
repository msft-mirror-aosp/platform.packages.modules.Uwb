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

package com.android.server.ranging.cs;

import static android.ranging.RangingCapabilities.DISABLED_USER;
import static android.ranging.RangingCapabilities.ENABLED;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.ranging.RangingCapabilities.RangingTechnologyAvailability;
import android.ranging.cs.CsRangingCapabilities;

import androidx.annotation.Nullable;

import com.android.server.ranging.CapabilitiesProvider.AvailabilityCallback;
import com.android.server.ranging.CapabilitiesProvider.CapabilitiesAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CsCapabilitiesAdapter extends CapabilitiesAdapter {

    private final Context mContext;

    private Set<Integer> mSupportedSecurityLevels;

    /** @return true if CS is supported in the provided context, false otherwise */
    public static boolean isSupported(Context context) {
        return context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE_CHANNEL_SOUNDING);

    }

    @Override
    public @RangingTechnologyAvailability int getAvailability() {
        BluetoothAdapter bluetoothAdapter =
                mContext.getSystemService(BluetoothManager.class).getAdapter();
        if (bluetoothAdapter == null) {
            return DISABLED_USER;
        }
        if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
            return ENABLED;
        }
        return DISABLED_USER;
    }

    @Override
    public @Nullable CsRangingCapabilities getCapabilities() {
        if (getAvailability() == ENABLED) {
            List<Integer> securityLevels = new ArrayList<Integer>(mSupportedSecurityLevels);
            return new CsRangingCapabilities.Builder()
                .setSupportedSecurityLevels(securityLevels)
                .build();
        } else {
            return null;
        }
    }

    public CsCapabilitiesAdapter(Context context) {
        mContext = context;

        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {

            BluetoothStateChangeReceiver receiver = new BluetoothStateChangeReceiver();
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            mContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);

            mSupportedSecurityLevels = mContext.getSystemService(BluetoothManager.class)
                    .getAdapter().getDistanceMeasurementManager()
                    .getChannelSoundingSupportedSecurityLevels();
        }

    }

    private class BluetoothStateChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            AvailabilityCallback callback = getAvailabilityCallback();
            if (callback != null) {
                callback.onAvailabilityChange(
                        getAvailability(),
                        AvailabilityCallback.AvailabilityChangedReason.SYSTEM_POLICY);
            }
        }
    }
}
