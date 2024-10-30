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

package android.ranging.rtt;

import android.annotation.FlaggedApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.RangingManager;

import androidx.annotation.NonNull;

import com.android.ranging.flags.Flags;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_RTT_ENABLED)
public class RttRangingParams implements Parcelable {

    protected RttRangingParams(Parcel in) {
        mServiceName = in.readString();
        mMatchFilter = in.createByteArray();
    }

    public static final Creator<RttRangingParams> CREATOR = new Creator<RttRangingParams>() {
        @Override
        public RttRangingParams createFromParcel(Parcel in) {
            return new RttRangingParams(in);
        }

        @Override
        public RttRangingParams[] newArray(int size) {
            return new RttRangingParams[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mServiceName);
        dest.writeByteArray(mMatchFilter);
    }

    private final String mServiceName;

    private final byte[] mMatchFilter;

    public String getServiceName() {
        return mServiceName;
    }

    public byte[] getMatchFilter() {
        return mMatchFilter;
    }

    private RttRangingParams(Builder builder) {
        mServiceName = builder.mServiceName;
        mMatchFilter = builder.mMatchFilter;
    }

    public static final class Builder {
        private String mServiceName = "";

        private byte[] mMatchFilter = new byte[0];

        public Builder setServiceName(String serviceName) {
            if (serviceName != null) {
                this.mServiceName = serviceName;
            }
            return this;
        }

        public Builder setMatchFilter(byte[] matchFilter) {
            if (matchFilter != null) {
                this.mMatchFilter = matchFilter.clone();
            }
            return this;
        }

        public RttRangingParams build() {
            return new RttRangingParams(this);
        }
    }

}
