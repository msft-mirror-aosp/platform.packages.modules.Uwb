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
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.android.ranging.flags.Flags;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class RawResponderRangingParams extends RangingParams implements Parcelable {

    private final RawRangingDevice mRawRangingDevice;

    private RawResponderRangingParams(Builder builder) {
        mRangingSessionType = RangingParams.RANGING_SESSION_RAW;
        mRawRangingDevice = builder.mRawRangingDevice;
    }

    protected RawResponderRangingParams(Parcel in) {
        mRangingSessionType = in.readInt();
        mRawRangingDevice = in.readParcelable(RawRangingDevice.class.getClassLoader());
    }

    public static final Creator<RawResponderRangingParams> CREATOR =
            new Creator<RawResponderRangingParams>() {
                @Override
                public RawResponderRangingParams createFromParcel(Parcel in) {
                    return new RawResponderRangingParams(in);
                }

                @Override
                public RawResponderRangingParams[] newArray(int size) {
                    return new RawResponderRangingParams[size];
                }
            };

    public RawRangingDevice getRawRangingDevice() {
        return mRawRangingDevice;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mRangingSessionType);
        dest.writeParcelable(mRawRangingDevice, flags);
    }

    public static final class Builder {
        private RawRangingDevice mRawRangingDevice;

        public Builder setRawRangingDevice(RawRangingDevice rangingDevice) {
            mRawRangingDevice = rangingDevice;
            return this;
        }

        public RawResponderRangingParams build() {
            return new RawResponderRangingParams(this);
        }
    }
}




