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

package com.android.server.ranging;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingAdapter.TechnologyConfig;
import com.android.server.ranging.cs.CsConfig;
import com.android.server.ranging.cs.CsParameters;
import com.android.server.ranging.fusion.DataFusers;
import com.android.server.ranging.fusion.FusionEngine;
import com.android.server.ranging.uwb.UwbConfig;
import com.android.server.ranging.uwb.UwbParameters;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.uwb.support.base.RequiredParam;

import java.time.Duration;
import java.util.Optional;

/** Parameters for a generic ranging session. */
public class RangingParameters {
    public enum DeviceRole {
        RESPONDER,
        /** The device that initiates the session. */
        INITIATOR;

        public static final ImmutableList<DeviceRole> ROLES =
                ImmutableList.copyOf(DeviceRole.values());
    }

    private final DeviceRole mDeviceRole;
    private final Duration mNoInitialDataTimeout;
    private final Duration mNoUpdatedDataTimeout;
    private final FusionEngine.DataFuser mDataFuser;
    private final ImmutableMap<RangingTechnology, TechnologyConfig> mTechConfigs;

    private RangingParameters(@NonNull Builder builder) {
        mDeviceRole = builder.mDeviceRole;
        mNoInitialDataTimeout = builder.mNoInitialDataTimeout.get();
        mNoUpdatedDataTimeout = builder.mNoUpdatedDataTimeout.get();
        mDataFuser = builder.mDataFuser;

        ImmutableMap.Builder<RangingTechnology, TechnologyConfig> techConfigs =
                new ImmutableMap.Builder<>();
        if (builder.mUwbParameters != null) {
            techConfigs.put(RangingTechnology.UWB,
                    new UwbConfig.Builder()
                            .setDeviceRole(mDeviceRole)
                            .setParameters(builder.mUwbParameters)
                            .build());
        }
        if (builder.mCsParameters != null) {
            techConfigs.put(RangingTechnology.CS, new CsConfig());
        }
        mTechConfigs = techConfigs.build();
    }

    /** @return The device's role within the session. */
    public @NonNull DeviceRole getDeviceRole() {
        return mDeviceRole;
    }

    /**
     * @return timeout after which to stop the session if no ranging data is produced after
     * starting.
     */
    public @NonNull Duration getNoInitialDataTimeout() {
        return mNoInitialDataTimeout;
    }

    /**
     * @return timeout after which to stop the session if no new ranging data is produced.
     */
    public @NonNull Duration getNoUpdatedDataTimeout() {
        return mNoUpdatedDataTimeout;
    }

    /**
     * @return the configured data fuser, or {@code Optional.empty()} if fusion wasn't set.
     */
    public Optional<FusionEngine.DataFuser> getDataFuser() {
        return Optional.ofNullable(mDataFuser);
    }

    /** @return A map between technologies and their corresponding generic parameters object. */
    public @NonNull ImmutableMap<RangingTechnology, TechnologyConfig> getTechnologyConfigs() {
        return mTechConfigs;
    }

    public static class Builder {
        private final DeviceRole mDeviceRole;
        private final RequiredParam<Duration> mNoInitialDataTimeout = new RequiredParam<>();
        private final RequiredParam<Duration> mNoUpdatedDataTimeout = new RequiredParam<>();
        private FusionEngine.DataFuser mDataFuser = null;
        private UwbParameters mUwbParameters = null;
        private CsParameters mCsParameters = null;

        /**
         * @param role of the device within the session.
         */
        public Builder(DeviceRole role) {
            mDeviceRole = role;
        }

        /** Build the {@link RangingParameters object} */
        public RangingParameters build() {
            return new RangingParameters(this);
        }

        /**
         * @param timeout after which the session will be stopped if no ranging data was produced
         *                directly after starting.
         */
        public Builder setNoInitialDataTimeout(@NonNull Duration timeout) {
            mNoInitialDataTimeout.set(timeout);
            return this;
        }

        /**
         * @param timeout after which the session will be stopped if there is no new ranging data
         *                produced.
         */
        public Builder setNoUpdatedDataTimeout(@NonNull Duration timeout) {
            mNoUpdatedDataTimeout.set(timeout);
            return this;
        }

        /**
         * @param fuser to use. See {@link DataFusers}.
         */
        public Builder useSensorFusion(FusionEngine.DataFuser fuser) {
            mDataFuser = fuser;
            return this;
        }

        /**
         * Range with UWB in this session.
         *
         * @param uwbParameters containing a configuration for UWB ranging.
         */
        public Builder useUwb(UwbParameters uwbParameters) {
            mUwbParameters = uwbParameters;
            return this;
        }

        /**
         * Range with Bluetooth Channel Sounding in this session.
         *
         * @param csParameters containing a configuration for CS ranging.
         */
        public Builder useCs(CsParameters csParameters) {
            mCsParameters = csParameters;
            return this;
        }
    }
}
