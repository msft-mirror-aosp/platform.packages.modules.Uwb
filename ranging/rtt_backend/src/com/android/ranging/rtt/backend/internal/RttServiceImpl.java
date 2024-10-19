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

package com.android.ranging.rtt.backend.internal;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 *
 */
public class RttServiceImpl implements RttService {
    private static final String TAG = RttServiceImpl.class.getSimpleName();
    private final Context mContext;
    private WifiManager mWifiManager;

    public RttServiceImpl(@NonNull Context context) {
        this.mContext = context;
        mWifiManager = context.getSystemService(WifiManager.class);
    }

    @Override
    public RttRangingDevice getPublisher(Context context) {
        return new RttRangingDevice(context, RttRangingDevice.DeviceType.PUBLISHER);
    }

    @Override
    public RttRangingDevice getSubscriber(Context context) {
        return new RttRangingDevice(context, RttRangingDevice.DeviceType.SUBSCRIBER);
    }

    @Override
    public boolean isAvailable() {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
            Log.w(TAG, "WiFi Aware is not supported");
            return false;
        }

        if (!mWifiManager.isWifiEnabled()) {
            Log.w(TAG, "Could not start test because Wifi is not enabled");
            return false;
        }

        return true;
    }
}