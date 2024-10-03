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

package android.ranging.uwb;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.RangingDevice;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class UwbRangingParameters implements Parcelable {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            CONTROLEE,
            CONTROLLER})
    @interface DeviceRole {}
    public static final int CONTROLEE = 1;
    public static final int CONTROLLER = 2;

    @DeviceRole
    private final int mDeviceRole;

    private final int mSessionId;
    private final int mSubSessionId;

    private final int mUwbConfigId;
    private final byte[] mSessionKeyInfo;
    private final byte[] mSubSessionKeyInfo;

    @NonNull
    private final UwbComplexChannel mUwbComplexChannel;

    @NonNull
    private final Map<RangingDevice, UwbAddress> mPeerAddressMap;

    private final int mRangingUpdateRate;
    @Nullable
    private final UwbRangeDataNtfConfig mUwbRangeDataNtfConfig;

    private final int mSlotDuration;

    private final boolean mIsAoaDisabled;

    private UwbRangingParameters(Builder builder) {
        this.mDeviceRole = builder.mDeviceRole;
        this.mSessionId = builder.mSessionId;
        this.mSubSessionId = builder.mSubSessionId;
        this.mUwbConfigId = builder.mUwbConfigId;
        this.mSessionKeyInfo = builder.mSessionKeyInfo;
        this.mSubSessionKeyInfo = builder.mSubSessionKeyInfo;
        this.mUwbComplexChannel = builder.mUwbComplexChannel;
        this.mPeerAddressMap = builder.mPeerAddressMap;
        this.mRangingUpdateRate = builder.mRangingUpdateRate;
        this.mUwbRangeDataNtfConfig = builder.mUwbRangeDataNtfConfig;
        this.mSlotDuration = builder.mSlotDuration;
        this.mIsAoaDisabled = builder.mIsAoaDisabled;
    }


    // Static Builder class
    public static class Builder {
        private int mDeviceRole = CONTROLEE;
        private int mSessionId;
        private int mSubSessionId;

        private int mUwbConfigId;
        private byte[] mSessionKeyInfo;
        private byte[] mSubSessionKeyInfo;

        @NonNull
        private UwbComplexChannel mUwbComplexChannel;

        @NonNull
        private Map<RangingDevice, UwbAddress> mPeerAddressMap;

        private int mRangingUpdateRate;
        @Nullable
        private android.ranging.uwb.UwbRangeDataNtfConfig mUwbRangeDataNtfConfig;

        private int mSlotDuration;

        private boolean mIsAoaDisabled;

        public Builder setDeviceRole(@DeviceRole int deviceRole) {
            this.mDeviceRole = deviceRole;
            return this;
        }

        public Builder sessionId(int sessionId) {
            this.mSessionId = sessionId;
            return this;
        }

        public Builder subSessionId(int subSessionId) {
            this.mSubSessionId = subSessionId;
            return this;
        }

        public Builder uwbConfigId(int uwbConfigId) {
            this.mUwbConfigId = uwbConfigId;
            return this;
        }

        public Builder sessionKeyInfo(byte[] sessionKeyInfo) {
            this.mSessionKeyInfo = sessionKeyInfo;
            return this;
        }

        public Builder subSessionKeyInfo(byte[] subSessionKeyInfo) {
            this.mSubSessionKeyInfo = subSessionKeyInfo;
            return this;
        }

        public Builder complexChannel(
                @NonNull android.ranging.uwb.UwbComplexChannel complexChannel) {
            this.mUwbComplexChannel = complexChannel;
            return this;
        }

        public Builder peerAddresses(
                @NonNull Map<RangingDevice, android.ranging.uwb.UwbAddress> peerAddresses) {
            this.mPeerAddressMap = peerAddresses;
            return this;
        }

        public Builder rangingUpdateRate(int rangingUpdateRate) {
            this.mRangingUpdateRate = rangingUpdateRate;
            return this;
        }

        public Builder uwbRangeDataNtfConfig(
                @Nullable android.ranging.uwb.UwbRangeDataNtfConfig uwbRangeDataNtfConfig) {
            this.mUwbRangeDataNtfConfig = uwbRangeDataNtfConfig;
            return this;
        }

        public Builder slotDuration(int slotDuration) {
            this.mSlotDuration = slotDuration;
            return this;
        }

        public Builder isAoaDisabled(boolean isAoaDisabled) {
            this.mIsAoaDisabled = isAoaDisabled;
            return this;
        }

        // Build method to create an instance of RangingConfiguration
        public UwbRangingParameters build() {
            return new UwbRangingParameters(this);
        }
    }

    protected UwbRangingParameters(Parcel in) {
        mDeviceRole = in.readInt();
        mSessionId = in.readInt();
        mSubSessionId = in.readInt();
        mUwbConfigId = in.readInt();
        mSessionKeyInfo = in.readBlob();
        mSubSessionKeyInfo = in.readBlob();
        mUwbComplexChannel = Objects.requireNonNull(
                in.readParcelable(
                        UwbComplexChannel.class.getClassLoader(), UwbComplexChannel.class));
        mRangingUpdateRate = in.readInt();
        mUwbRangeDataNtfConfig = in.readParcelable(
                UwbRangeDataNtfConfig.class.getClassLoader(), UwbRangeDataNtfConfig.class);
        mSlotDuration = in.readInt();
        mIsAoaDisabled = in.readByte() != 0;
        // Deserialize peerAddresses (Map<RangingDevice, UwbAddress>)
        int size = in.readInt();  // Get size of the Map
        mPeerAddressMap = new HashMap<>();
        for (int i = 0; i < size; i++) {
            RangingDevice device = Objects.requireNonNull(
                    in.readParcelable(RangingDevice.class.getClassLoader(), RangingDevice.class));
            UwbAddress address = Objects.requireNonNull(
                    in.readParcelable(UwbAddress.class.getClassLoader(), UwbAddress.class));
            mPeerAddressMap.put(device, address);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mDeviceRole);
        dest.writeInt(mSessionId);
        dest.writeInt(mSubSessionId);
        dest.writeInt(mUwbConfigId);
        dest.writeBlob(mSessionKeyInfo);
        dest.writeBlob(mSubSessionKeyInfo);
        dest.writeParcelable(mUwbComplexChannel, flags);
        dest.writeInt(mRangingUpdateRate);
        dest.writeParcelable(mUwbRangeDataNtfConfig, flags);
        dest.writeInt(mSlotDuration);
        dest.writeByte((byte) (mIsAoaDisabled ? 1 : 0));
        // Serialize peerAddresses (Map<RangingDevice, UwbAddress>)
        dest.writeInt(mPeerAddressMap.size());  // Write the size of the Map
        for (Map.Entry<RangingDevice, UwbAddress> entry : mPeerAddressMap.entrySet()) {
            dest.writeParcelable(entry.getKey(), flags);  // Write each RangingDevice
            dest.writeParcelable(entry.getValue(), flags);  // Write each UwbAddress
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<UwbRangingParameters> CREATOR =
            new Creator<UwbRangingParameters>() {
                @Override
                public UwbRangingParameters createFromParcel(Parcel in) {
                    return new UwbRangingParameters(in);
                }

                @Override
                public UwbRangingParameters[] newArray(int size) {
                    return new UwbRangingParameters[size];
                }
            };

    @DeviceRole
    public int getDeviceRole() {
        return  mDeviceRole;
    }
    public int getSessionId() {
        return mSessionId;
    }

    public int getSubSessionId() {
        return mSubSessionId;
    }

    public int getUwbConfigId() {
        return mUwbConfigId;
    }

    public byte[] getSessionKeyInfo() {
        return mSessionKeyInfo;
    }

    public byte[] getSubSessionKeyInfo() {
        return mSubSessionKeyInfo;
    }

    @NonNull
    public UwbComplexChannel getUwbComplexChannel() {
        return mUwbComplexChannel;
    }

    @NonNull
    public Map<RangingDevice, UwbAddress> getPeerAddresses() {
        return mPeerAddressMap;
    }

    public int getRangingUpdateRate() {
        return mRangingUpdateRate;
    }

    @Nullable
    public UwbRangeDataNtfConfig getUwbRangeDataNtfConfig() {
        return mUwbRangeDataNtfConfig;
    }

    public int getSlotDuration() {
        return mSlotDuration;
    }

    public boolean isAoaDisabled() {
        return mIsAoaDisabled;
    }
}
