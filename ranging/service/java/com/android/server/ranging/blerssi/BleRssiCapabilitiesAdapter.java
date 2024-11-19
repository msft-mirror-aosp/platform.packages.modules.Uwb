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

package com.android.server.ranging.blerssi;

import static android.ranging.RangingCapabilities.DISABLED_USER;
import static android.ranging.RangingCapabilities.ENABLED;
import static android.ranging.RangingCapabilities.NOT_SUPPORTED;

import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.ranging.RangingCapabilities;

import com.android.server.ranging.CapabilitiesProvider;

public class BleRssiCapabilitiesAdapter extends CapabilitiesProvider.CapabilitiesAdapter {

    private final Context mContext;
    private final BluetoothManager mBluetoothManager;

    public BleRssiCapabilitiesAdapter(Context context) {
        mContext = context;
        if (isSupported(mContext)) {
            mBluetoothManager = mContext.getSystemService(BluetoothManager.class);
        } else {
            mBluetoothManager = null;
        }
    }

    public static boolean isSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    @Override
    public @RangingCapabilities.RangingTechnologyAvailability int getAvailability() {
        if (mBluetoothManager == null) {
            return NOT_SUPPORTED;
        } else if (isAvailable()) {
            return ENABLED;
        } else {
            return DISABLED_USER;
        }
    }

    public boolean isAvailable() {
        return mBluetoothManager.getAdapter().getState() == BluetoothAdapter.STATE_ON;
    }

    @Nullable
    @Override
    public RangingCapabilities.TechnologyCapabilities getCapabilities() {
        // No special capabilities.
        return null;
    }
}
