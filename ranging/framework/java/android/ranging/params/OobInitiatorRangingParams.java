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
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the parameters for an Out-of-Band (OOB) initiator in a ranging session.
 * This class includes configuration options such as device handles, security level,
 * ranging mode, and interval range for setting up an OOB initiator ranging session.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class OobInitiatorRangingParams extends RangingParams implements Parcelable {

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            SECURITY_LEVEL_BASIC,
            SECURITY_LEVEL_SECURE,
    })
    public @interface SecurityLevel {
    }

    /**
     * Basic security level for the ranging session.
     * <p>Example usage:
     * UWB: Static-STS
     * BLE-CS: Security level one
     */
    public static final int SECURITY_LEVEL_BASIC = 0;

    /**
     * Basic security level for the ranging session.
     * <p>Example usage:
     * UWB: Provisioned-STS
     * BLE-CS: Security level four
     */
    public static final int SECURITY_LEVEL_SECURE = 1;

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            RANGING_MODE_AUTO,
            RANGING_MODE_HIGH_ACCURACY,
            RANGING_MODE_HIGH_ACCURACY_PREFERRED,
            RANGING_MODE_FUSED,
    })
    public @interface RangingMode {
    }

    /**
     * Automatic ranging mode, allows the system to choose the best mode.
     */
    public static final int RANGING_MODE_AUTO = 0;
    /**
     * High accuracy ranging mode. Fallback to ranging technologies if high accuracy ranging is not
     * supported
     */
    public static final int RANGING_MODE_HIGH_ACCURACY = 1;
    /**
     * High accuracy ranging mode. No fallback allowed.
     */
    public static final int RANGING_MODE_HIGH_ACCURACY_PREFERRED = 2;
    /**
     * Starts ranging with all the ranging technologies both devices support.
     */
    public static final int RANGING_MODE_FUSED = 3;

    private final List<DeviceHandle> mDeviceHandles;

    private final RangingIntervalRange mRangingIntervalRange;

    @SecurityLevel
    private final int mSecurityLevel;

    @RangingMode
    private final int mRangingMode;

    private OobInitiatorRangingParams(Builder builder) {
        mRangingSessionType = RangingParams.RANGING_SESSION_OOB;
        mDeviceHandles = new ArrayList<>(builder.mDeviceHandles);
        mSecurityLevel = builder.mSecurityLevel;
        mRangingMode = builder.mRangingMode;
        mRangingIntervalRange = builder.mRangingIntervalRange;
    }

    protected OobInitiatorRangingParams(Parcel in) {
        mRangingSessionType = in.readInt();
        mDeviceHandles = in.createTypedArrayList(DeviceHandle.CREATOR);
        mSecurityLevel = in.readInt();
        mRangingMode = in.readInt();
        mRangingIntervalRange = in.readParcelable(RangingIntervalRange.class.getClassLoader(),
                RangingIntervalRange.class);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRangingSessionType);
        dest.writeTypedList(mDeviceHandles);
        dest.writeInt(mSecurityLevel);
        dest.writeInt(mRangingMode);
        dest.writeParcelable(mRangingIntervalRange, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    public static final Creator<OobInitiatorRangingParams> CREATOR =
            new Creator<OobInitiatorRangingParams>() {
                @Override
                public OobInitiatorRangingParams createFromParcel(Parcel in) {
                    return new OobInitiatorRangingParams(in);
                }

                @Override
                public OobInitiatorRangingParams[] newArray(int size) {
                    return new OobInitiatorRangingParams[size];
                }
            };

    /**
     * Returns the list of DeviceHandles associated with the OOB initiator.
     *
     * @return A list of DeviceHandle objects.
     */
    @NonNull
    public List<DeviceHandle> getDeviceHandles() {
        return mDeviceHandles;
    }

    /**
     * Returns the ranging interval range configuration.
     *
     * @return The RangingIntervalRange associated with the OOB initiator.
     */
    @NonNull
    public RangingIntervalRange getRangingIntervalRange() {
        return mRangingIntervalRange;
    }

    /**
     * Returns the security level set for the ranging session.
     *
     * @return the security level.
     * <p>Possible values:
     * {@link #SECURITY_LEVEL_BASIC}
     * {@link #SECURITY_LEVEL_SECURE}
     */
    @SecurityLevel
    public int getSecurityLevel() {
        return mSecurityLevel;
    }

    /**
     * Returns the ranging mode for the session.
     *
     * @return the ranging mode.
     * <p>Possible values:
     * {@link #RANGING_MODE_AUTO}
     * {@link #RANGING_MODE_HIGH_ACCURACY}
     * {@link #RANGING_MODE_HIGH_ACCURACY_PREFERRED}
     * {@link #RANGING_MODE_FUSED}
     */

    @RangingMode
    public int getRangingMode() {
        return mRangingMode;
    }

    /**
     * Builder class for creating instances of {@link OobInitiatorRangingParams}.
     */
    public static final class Builder {
        private final List<DeviceHandle> mDeviceHandles = new ArrayList<>();
        private RangingIntervalRange mRangingIntervalRange;
        @SecurityLevel
        private int mSecurityLevel = SECURITY_LEVEL_BASIC;
        @RangingMode
        private int mRangingMode = RANGING_MODE_AUTO;

        /**
         * Adds a DeviceHandle to the list of devices for the ranging session.
         *
         * @param deviceHandle The DeviceHandle to add.
         * @return The Builder instance.
         */
        public Builder addDeviceHandle(DeviceHandle deviceHandle) {
            mDeviceHandles.add(deviceHandle);
            return this;
        }

        /**
         * Sets the ranging interval range configuration for the session.
         *
         * @param intervalRange The RangingIntervalRange to set.
         *                      Defaults to range [100ms, 5000ms]
         * @return The Builder instance.
         */
        public Builder setRangingIntervalRange(RangingIntervalRange intervalRange) {
            this.mRangingIntervalRange = intervalRange;
            return this;
        }

        /**
         * Sets the security level for the ranging session.
         *
         * @param securityLevel The security level to set.
         *                      Defaults to {@link #SECURITY_LEVEL_BASIC}
         * @return The Builder instance.
         */
        public Builder setSecurityLevel(@SecurityLevel int securityLevel) {
            this.mSecurityLevel = securityLevel;
            return this;
        }

        /**
         * Sets the ranging mode for the session.
         *
         * @param rangingMode The ranging mode to set.
         *                    Defaults to {@link #RANGING_MODE_AUTO}
         * @return The Builder instance.
         */
        public Builder setRangingMode(@RangingMode int rangingMode) {
            this.mRangingMode = rangingMode;
            return this;
        }

        /**
         * Builds an instance of {@link OobInitiatorRangingParams} with the provided parameters.
         *
         * @return A new OobInitiatorRangingParams instance.
         */
        public OobInitiatorRangingParams build() {
            return new OobInitiatorRangingParams(this);
        }
    }


}
