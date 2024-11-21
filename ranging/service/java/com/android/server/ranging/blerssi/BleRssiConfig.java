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

import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.blerssi.BleRssiRangingParams;
import android.ranging.params.DataNotificationConfig;

import com.android.server.ranging.RangingPeerConfig;

public class BleRssiConfig implements RangingPeerConfig.TechnologyConfig {
    private static final String TAG = BleRssiConfig.class.getSimpleName();

    private final DataNotificationConfig mDataNotificationConfig;
    private final BleRssiRangingParams mRangingParams;

    private final RangingDevice mPeerDevice;

    @RangingPreference.DeviceRole
    private final int mDeviceRole;

    public BleRssiConfig(int deviceRole,
            BleRssiRangingParams bleRssiRangingParams,
            DataNotificationConfig dataNotificationConfig,
            RangingDevice peerDevice) {
        mDeviceRole = deviceRole;
        mRangingParams = bleRssiRangingParams;
        mDataNotificationConfig = dataNotificationConfig;
        mPeerDevice = peerDevice;
    }

    public DataNotificationConfig getDataNotificationConfig() {
        return mDataNotificationConfig;
    }

    public BleRssiRangingParams getRangingParams() {
        return mRangingParams;
    }

    public int getDeviceRole() {
        return mDeviceRole;
    }

    public RangingDevice getPeerDevice() {
        return mPeerDevice;
    }
}