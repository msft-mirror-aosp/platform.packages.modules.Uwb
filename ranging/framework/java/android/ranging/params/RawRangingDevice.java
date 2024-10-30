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

package android.ranging.params;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.RangingDevice;
import android.ranging.cs.CsRangingParams;
import android.ranging.rtt.RttRangingParams;
import android.ranging.uwb.UwbRangingParams;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class RawRangingDevice implements Parcelable {

    /**
     * Defines the configuration IDs for different ranging scenarios.
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            UPDATE_RATE_NORMAL,
            UPDATE_RATE_INFREQUENT,
            UPDATE_RATE_FAST,
    })
    public @interface RangingUpdateRate {
    }

    /** Ranging interval between 200ms - 240ms for UWB, 2 seconds for BT-CS. */
    public static final int UPDATE_RATE_NORMAL = 1;
    /** Ranging interval between 600ms - 800ms for UWB, 5 seconds for BT-CS. */
    public static final int UPDATE_RATE_INFREQUENT = 2;
    /** Ranging interval between 100ms - 200ms for UWB, 1 second for BT-CS. */
    public static final int UPDATE_RATE_FAST = 3;
    private final RangingDevice mRangingDevice;
    private final UwbRangingParams mUwbRangingParams;
    private final CsRangingParams mCsRangingParams;
    private final RttRangingParams mRttRangingParams;


    private RawRangingDevice(Builder builder) {
        mRangingDevice = builder.mRangingDevice;
        mUwbRangingParams = builder.mUwbRangingParams;
        mCsRangingParams = builder.mCsRangingParams;
        mRttRangingParams = builder.mRttRangingParams;
    }


    protected RawRangingDevice(Parcel in) {
        mRangingDevice = in.readParcelable(RangingDevice.class.getClassLoader(), RangingDevice.class
        );
        mUwbRangingParams = in.readParcelable(UwbRangingParams.class.getClassLoader(),
                UwbRangingParams.class);
        mCsRangingParams = in.readParcelable(CsRangingParams.class.getClassLoader(),
                CsRangingParams.class
        );
        mRttRangingParams = in.readParcelable(RttRangingParams.class.getClassLoader(),
                RttRangingParams.class);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mRangingDevice, flags);
        dest.writeParcelable(mUwbRangingParams, flags);
        dest.writeParcelable(mCsRangingParams, flags);
        dest.writeParcelable(mRttRangingParams, flags);
    }

    public static final Creator<RawRangingDevice> CREATOR = new Creator<RawRangingDevice>() {
        @Override
        public RawRangingDevice createFromParcel(Parcel in) {
            return new RawRangingDevice(in);
        }

        @Override
        public RawRangingDevice[] newArray(int size) {
            return new RawRangingDevice[size];
        }
    };

    public RangingDevice getRangingDevice() {
        return mRangingDevice;
    }

    @Nullable
    public UwbRangingParams getUwbRangingParams() {
        return mUwbRangingParams;
    }

    @Nullable
    public CsRangingParams getCsRangingParams() {
        return mCsRangingParams;
    }

    @Nullable
    public RttRangingParams getRttRangingParams() {
        return mRttRangingParams;
    }

    @Override
    public int describeContents() {
        return 0;
    }


    public static final class Builder {
        private RangingDevice mRangingDevice;
        private UwbRangingParams mUwbRangingParams;
        private CsRangingParams mCsRangingParams;
        private RttRangingParams mRttRangingParams;

        public Builder setRangingDevice(RangingDevice rangingDevice) {
            mRangingDevice = rangingDevice;
            return this;
        }

        public Builder setUwbRangingParams(UwbRangingParams params) {
            mUwbRangingParams = params;
            return this;
        }

        public Builder setRttRangingParams(RttRangingParams params) {
            mRttRangingParams = params;
            return this;
        }

        public Builder setCsRangingParams(CsRangingParams params) {
            mCsRangingParams = params;
            return this;
        }

        public RawRangingDevice build() {
            return new RawRangingDevice(this);
        }
    }
}
