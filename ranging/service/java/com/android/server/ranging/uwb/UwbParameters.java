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

package com.android.server.ranging.uwb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.ranging.uwb.backend.internal.Utils;
import com.android.ranging.uwb.backend.internal.UwbAddress;
import com.android.ranging.uwb.backend.internal.UwbComplexChannel;
import com.android.ranging.uwb.backend.internal.UwbRangeDataNtfConfig;
import com.android.server.ranging.RangingParameters.TechnologyParameters;

import com.google.common.collect.ImmutableSet;
import com.google.uwb.support.base.RequiredParam;

/** Configuration for UWB sent as part SetConfigurationMessage for Finder OOB. */
public class UwbParameters implements TechnologyParameters {
    private final String mCountryCode;
    private final UwbAddress mLocalAddress;
    private final ImmutableSet<UwbAddress> mPeerAddresses;
    private final @Utils.UwbConfigId int mConfigType;
    private final int mSessionId;
    private final int mSubSessionId;
    private final byte[] mSessionKeyInfo;
    private final byte[] mSubSessionKeyInfo;
    private final UwbComplexChannel mComplexChannel;
    private final @Utils.RangingUpdateRate int mUpdateRateType;
    private final UwbRangeDataNtfConfig mRangeDataNtfConfig;
    private final @Utils.SlotDuration int mSlotDurationMs;
    private final boolean mIsAoaDisabled;

    private UwbParameters(Builder builder) {
        mCountryCode = builder.mCountryCode;
        mLocalAddress = builder.mLocalAddress;
        mPeerAddresses = builder.mPeerAddresses.get();
        mConfigType = builder.mConfigType.get();
        mSessionId = builder.mSessionId.get();
        mSubSessionId = builder.mSubSessionId.get();
        mSessionKeyInfo = builder.mSessionKeyInfo;
        mSubSessionKeyInfo = builder.mSubSessionKeyInfo;
        mComplexChannel = builder.mComplexChannel;
        mUpdateRateType = builder.mUpdateRateType.get();
        mSlotDurationMs = builder.mSlotDurationMs;
        mIsAoaDisabled = builder.mIsAoaDisabled;
        mRangeDataNtfConfig = builder.mRangeDataNtfConfig;
    }

    public String getCountryCode() {
        return mCountryCode;
    }

    public UwbAddress getLocalAddress() {
        return mLocalAddress;
    }

    public ImmutableSet<UwbAddress> getPeerAddresses() {
        return mPeerAddresses;
    }

    public @Utils.UwbConfigId int getConfigType() {
        return mConfigType;
    }

    public int getSessionId() {
        return mSessionId;
    }

    public int getSubSessionId() {
        return mSubSessionId;
    }

    public @Nullable byte[] getSessionKeyInfo() {
        return mSessionKeyInfo;
    }

    public @Nullable byte[] getSubSessionKeyInfo() {
        return mSubSessionKeyInfo;
    }

    public @Nullable UwbComplexChannel getComplexChannel() {
        return mComplexChannel;
    }

    public @Utils.RangingUpdateRate int getUpdateRateType() {
        return mUpdateRateType;
    }

    public @NonNull UwbRangeDataNtfConfig getRangeDataNtfConfig() {
        return mRangeDataNtfConfig;
    }

    public @Utils.SlotDuration int getSlotDurationMs() {
        return mSlotDurationMs;
    }

    public boolean isAoaDisabled() {
        return mIsAoaDisabled;
    }

    public static class Builder {
        private String mCountryCode;
        private UwbAddress mLocalAddress = null;
        private final RequiredParam<ImmutableSet<UwbAddress>> mPeerAddresses =
                new RequiredParam<>();
        private final RequiredParam<Integer> mConfigType = new RequiredParam<>();
        private final RequiredParam<Integer> mSessionId = new RequiredParam<>();
        private final RequiredParam<Integer> mSubSessionId = new RequiredParam<>();
        private byte[] mSessionKeyInfo = null;
        private byte[] mSubSessionKeyInfo = null;
        private UwbComplexChannel mComplexChannel = null;
        private final RequiredParam<Integer> mUpdateRateType = new RequiredParam<>();
        private UwbRangeDataNtfConfig mRangeDataNtfConfig =
                new UwbRangeDataNtfConfig.Builder().build();
        private @Utils.SlotDuration int mSlotDurationMs;
        private boolean mIsAoaDisabled = false;

        public @NonNull UwbParameters build() {
            return new UwbParameters(this);
        }

        // TODO(370077264): This is not marked as required, but it does not have a fallback value.
        //  It should be set automatically in the UwbAdapter.
        public Builder setCountryCode(@NonNull String countryCode) {
            mCountryCode = countryCode;
            return this;
        }

        // TODO(370077264): This is not marked as required, but it does not have a fallback value.
        //  It should be set automatically in the UwbAdapter.
        public Builder setLocalAddress(UwbAddress address) {
            mLocalAddress = address;
            return this;
        }

        public Builder setPeerAddresses(ImmutableSet<UwbAddress> addresses) {
            mPeerAddresses.set(addresses);
            return this;
        }

        public Builder setConfigType(@Utils.UwbConfigId int config) {
            mConfigType.set(config);
            return this;
        }

        public Builder setSessionId(int sessionId) {
            mSessionId.set(sessionId);
            return this;
        }

        public Builder setSubSessionId(int subSessionId) {
            mSubSessionId.set(subSessionId);
            return this;
        }

        public Builder setSessionKeyInfo(byte[] sessionKeyInfo) {
            mSessionKeyInfo = sessionKeyInfo;
            return this;
        }

        public Builder setSubSessionKeyInfo(byte[] subSessionKeyInfo) {
            mSubSessionKeyInfo = subSessionKeyInfo;
            return this;
        }

        public Builder setComplexChannel(UwbComplexChannel complexChannel) {
            mComplexChannel = complexChannel;
            return this;
        }

        public Builder setUpdateRateType(@Utils.RangingUpdateRate int type) {
            mUpdateRateType.set(type);
            return this;
        }

        public Builder setRangeDataNtfConfig(UwbRangeDataNtfConfig config) {
            mRangeDataNtfConfig = config;
            return this;
        }

        public Builder setSlotDurationMs(@Utils.SlotDuration int duration) {
            mSlotDurationMs = duration;
            return this;
        }

        public Builder setAoaDisabled(boolean isAoaDisabled) {
            mIsAoaDisabled = isAoaDisabled;
            return this;
        }
    }
}
