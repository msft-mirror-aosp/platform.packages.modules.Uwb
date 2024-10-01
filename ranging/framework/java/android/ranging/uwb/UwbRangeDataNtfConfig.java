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


import static com.google.common.base.Preconditions.checkArgument;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class UwbRangeDataNtfConfig implements Parcelable {

    private final int mRangeDataNtfConfigType;
    private final int mNtfProximityNear;
    private final int mNtfProximityFar;

    protected UwbRangeDataNtfConfig(Parcel in) {
        mRangeDataNtfConfigType = in.readInt();
        mNtfProximityNear = in.readInt();
        mNtfProximityFar = in.readInt();
    }

    public static final Creator<UwbRangeDataNtfConfig> CREATOR =
            new Creator<UwbRangeDataNtfConfig>() {
                @Override
                public UwbRangeDataNtfConfig createFromParcel(Parcel in) {
                    return new UwbRangeDataNtfConfig(in);
                }

                @Override
                public UwbRangeDataNtfConfig[] newArray(int size) {
                    return new UwbRangeDataNtfConfig[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mRangeDataNtfConfigType);
        dest.writeInt(mNtfProximityNear);
        dest.writeInt(mNtfProximityFar);
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            RangeDataNtfConfig.RANGE_DATA_NTF_DISABLE,
            RangeDataNtfConfig.RANGE_DATA_NTF_ENABLE,
            RangeDataNtfConfig.RANGE_DATA_NTF_ENABLE_PROXIMITY_LEVEL_TRIG,
            RangeDataNtfConfig.RANGE_DATA_NTF_ENABLE_PROXIMITY_EDGE_TRIG,
    })
    @interface RangeDataNtfConfig {
        int RANGE_DATA_NTF_DISABLE = 0;
        int RANGE_DATA_NTF_ENABLE = 1;
        int RANGE_DATA_NTF_ENABLE_PROXIMITY_LEVEL_TRIG = 2;
        int RANGE_DATA_NTF_ENABLE_PROXIMITY_EDGE_TRIG = 3;
    }


    private UwbRangeDataNtfConfig(
            @RangeDataNtfConfig int rangeDataNtfConfigType,
            int ntfProximityNear,
            int ntfProximityFar) {
//        checkArgument(SUPPORTED_NTF_CONFIG.contains(rangeDataNtfConfigType),
//                "Invalid/Unsupported Range Data Ntf config");
        checkArgument(ntfProximityNear <= ntfProximityFar,
                "Ntf proximity near cannot be greater than Ntf proximity far");
        mRangeDataNtfConfigType = rangeDataNtfConfigType;
        mNtfProximityNear = ntfProximityNear;
        mNtfProximityFar = ntfProximityFar;
    }

    public int getRangeDataNtfConfigType() {
        return mRangeDataNtfConfigType;
    }

    public int getNtfProximityNear() {
        return mNtfProximityNear;
    }

    public int getNtfProximityFar() {
        return mNtfProximityFar;
    }

    /** Builder for UwbRangeDataNtfConfig */
    public static class Builder {
        private int mRangeDataConfigType = RangeDataNtfConfig.RANGE_DATA_NTF_ENABLE;
        private int mNtfProximityNear = 0;
        private int mNtfProximityFar = 20_000;

        public Builder setRangeDataConfigType(int rangeDataConfig) {
            mRangeDataConfigType = rangeDataConfig;
            return this;
        }

        public Builder setNtfProximityNear(int ntfProximityNear) {
            mNtfProximityNear = ntfProximityNear;
            return this;
        }

        public Builder setNtfProximityFar(int ntfProximityFar) {
            mNtfProximityFar = ntfProximityFar;
            return this;
        }

        public UwbRangeDataNtfConfig build() {
            return new UwbRangeDataNtfConfig(mRangeDataConfigType, mNtfProximityNear,
                    mNtfProximityFar);
        }
    }
}
