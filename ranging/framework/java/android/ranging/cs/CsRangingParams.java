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

package android.ranging.cs;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.params.RawRangingDevice;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Parameters for Bluetooth Channel Sounding ranging.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_CS_ENABLED)
public final class CsRangingParams implements Parcelable {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    SIGHT_TYPE_UNKNOWN,
                    SIGHT_TYPE_LINE_OF_SIGHT,
                    SIGHT_TYPE_NON_LINE_OF_SIGHT,
            })
    @interface SightType {
    }

    public static final int SIGHT_TYPE_UNKNOWN = 0;
    public static final int SIGHT_TYPE_LINE_OF_SIGHT = 1;
    public static final int SIGHT_TYPE_NON_LINE_OF_SIGHT = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    LOCATION_TYPE_UNKNOWN,
                    LOCATION_TYPE_INDOOR,
                    LOCATION_TYPE_OUTDOOR,
            })
    @interface LocationType {
    }

    public static final int LOCATION_TYPE_UNKNOWN = 0;
    public static final int LOCATION_TYPE_INDOOR = 1;
    public static final int LOCATION_TYPE_OUTDOOR = 2;
    private final byte[] mPeerBluetoothAddress;
    @RawRangingDevice.RangingUpdateRate
    private final int mRangingUpdateRate;
    @SightType
    private final int mSightType;
    @LocationType
    private final int mLocationType;
    @CsRangingCapabilities.SecurityLevel
    private final int mSecurityLevel;

    private CsRangingParams(Builder builder) {
        mPeerBluetoothAddress = builder.mPeerBluetoothAddress;
        mRangingUpdateRate = builder.mRangingUpdateRate;
        mSightType = builder.mSightType;
        mLocationType = builder.mLocationType;
        mSecurityLevel = builder.mSecurityLevel;
    }

    private CsRangingParams(Parcel in) {
        mPeerBluetoothAddress = in.readBlob();
        mRangingUpdateRate = in.readInt();
        mSightType = in.readInt();
        mLocationType = in.readInt();
        mSecurityLevel = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(mPeerBluetoothAddress);
        dest.writeInt(mRangingUpdateRate);
        dest.writeInt(mSightType);
        dest.writeInt(mLocationType);
        dest.writeInt(mSecurityLevel);
    }

    public static final Creator<CsRangingParams> CREATOR = new Creator<CsRangingParams>() {
        @Override
        public CsRangingParams createFromParcel(Parcel in) {
            return new CsRangingParams(in);
        }

        @Override
        public CsRangingParams[] newArray(int size) {
            return new CsRangingParams[size];
        }
    };


    /**
     * Returns the bluetooth peer device address.
     */
    public byte[] getPeerBluetoothAddress() {
        return mPeerBluetoothAddress;
    }

    public int getRangingUpdateRate() {
        return mRangingUpdateRate;
    }

    public int getSightType() {
        return mSightType;
    }

    public int getLocationType() {
        return mLocationType;
    }

    public int getSecurityLevel() {
        return mSecurityLevel;
    }


    public static final class Builder {
        private byte[] mPeerBluetoothAddress;
        @RawRangingDevice.RangingUpdateRate
        private int mRangingUpdateRate;
        @SightType
        private int mSightType;
        @LocationType
        private int mLocationType;
        @CsRangingCapabilities.SecurityLevel
        private int mSecurityLevel;

        public Builder setPeerBluetoothAddress(@NonNull byte[] peerBluetoothAddress) {
            mPeerBluetoothAddress = peerBluetoothAddress;
            return this;
        }

        public Builder setRangingUpdateRate(@RawRangingDevice.RangingUpdateRate int updateRate) {
            mRangingUpdateRate = updateRate;
            return this;
        }

        public Builder setSightType(@SightType int sightType) {
            mSightType = sightType;
            return this;
        }

        public Builder setLocationType(@LocationType int locationType) {
            mLocationType = locationType;
            return this;
        }

        public Builder setSecurityLevel(@CsRangingCapabilities.SecurityLevel int securityLevel) {
            mSecurityLevel = securityLevel;
            return this;
        }

        public CsRangingParams build() {
            return new CsRangingParams(this);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

}

