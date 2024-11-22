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

package android.ranging;

import android.annotation.FlaggedApi;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.params.DataNotificationConfig;
import android.ranging.params.SensorFusionParams;

import com.android.ranging.flags.Flags;

/**
 * Defines the ranging session configurations.
 *
 * <p>This class allows apps to set various parameters related to a ranging session.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class SessionConfiguration implements Parcelable {

    private final SensorFusionParams mFusionParameters;
    private final DataNotificationConfig mDataNotificationConfig;
    private final boolean mIsAngleOfArrivalNeeded;
    private final int mRangingMeasurementsLimit;

    private SessionConfiguration(Builder builder) {
        mFusionParameters = builder.mFusionParameters;
        mDataNotificationConfig = builder.mDataNotificationConfig;
        mIsAngleOfArrivalNeeded = builder.mIsAngleOfArrivalNeeded;
        mRangingMeasurementsLimit = builder.mRangingMeasurementsLimit;
    }

    private SessionConfiguration(Parcel in) {
        mFusionParameters = in.readParcelable(SensorFusionParams.class.getClassLoader(),
                SensorFusionParams.class);
        mDataNotificationConfig = in.readParcelable(DataNotificationConfig.class.getClassLoader(),
                DataNotificationConfig.class);
        mIsAngleOfArrivalNeeded = in.readBoolean();
        mRangingMeasurementsLimit = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mFusionParameters, flags);
        dest.writeParcelable(mDataNotificationConfig, flags);
        dest.writeBoolean(mIsAngleOfArrivalNeeded);
        dest.writeInt(mRangingMeasurementsLimit);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    public static final Creator<SessionConfiguration> CREATOR =
            new Creator<SessionConfiguration>() {
                @Override
                public SessionConfiguration createFromParcel(Parcel in) {
                    return new SessionConfiguration(in);
                }

                @Override
                public SessionConfiguration[] newArray(int size) {
                    return new SessionConfiguration[size];
                }
            };

    /**
     * Returns the sensor fusion parameters used for this preference.
     *
     * @return a non-null {@link SensorFusionParams} instance.
     */
    @NonNull
    public SensorFusionParams getFusionParameters() {
        return mFusionParameters;
    }

    /**
     * Returns the data notification configuration for this preference.
     *
     * @return a non-null {@link DataNotificationConfig} instance.
     */
    @NonNull
    public DataNotificationConfig getDataNotificationConfig() {
        return mDataNotificationConfig;
    }

    /**
     * Returns whether Angle-of-arrival was requested by the app.
     */
    public boolean isAngleOfArrivalNeeded() {
        return mIsAngleOfArrivalNeeded;
    }

    /**
     * Returns the configured ranging measurements limit for the ranging session.
     */
    @IntRange(from = 0, to = 65535)
    public int getRangingMeasurementsLimit() {
        return mRangingMeasurementsLimit;
    }

    /**
     * Builder for creating instances of {@link SessionConfiguration}.
     */
    public static final class Builder {
        private DataNotificationConfig mDataNotificationConfig =
                new DataNotificationConfig.Builder().build();
        private SensorFusionParams mFusionParameters = new SensorFusionParams.Builder().build();
        private boolean mIsAngleOfArrivalNeeded = false;
        private int mRangingMeasurementsLimit = 0;

        /**
         * Sets the sensor fusion parameters for this preference.
         *
         * @param parameters the {@link SensorFusionParams} to use.
         * @return the builder instance.
         * @throws IllegalArgumentException if the parameters is null.
         */
        @NonNull
        public Builder setSensorFusionParameters(
                @NonNull SensorFusionParams parameters) {
            mFusionParameters = parameters;
            return this;
        }

        /**
         * Sets the data notification configuration for this preference.
         *
         * @param config the {@link DataNotificationConfig} to use.
         * @return the builder instance for chaining.
         * @throws IllegalArgumentException if the config is null.
         */
        @NonNull
        public Builder setDataNotificationConfig(
                @NonNull DataNotificationConfig config) {
            mDataNotificationConfig = config;
            return this;
        }

        /**
         * Sets whether Angle of Arrival (AoA) is required for the ranging operation.
         *
         * <p> Defaults to false
         *
         * @param isAngleOfArrivalNeeded {@code true} if AoA data is required; {@code false}
         *                               otherwise.
         * @return The {@link Builder} instance.
         */
        @NonNull
        public Builder setAngleOfArrivalNeeded(boolean isAngleOfArrivalNeeded) {
            mIsAngleOfArrivalNeeded = isAngleOfArrivalNeeded;
            return this;
        }

        /**
         * Sets the maximum number of ranging rounds for this session. This includes all ranging
         * rounds, irrespective of whether they were successful or not. For 1:many sessions, a round
         * includes ranging to all peers within that round.
         *
         * <p> By default, when the value is set to {@code 0}, the ranging session will run
         * indefinitely.
         *
         * @param rangingMeasurementsLimit the maximum number of ranging rounds (0 to 65535).
         * @return this {@link Builder} instance.
         * @throws IllegalArgumentException if the value is outside the allowed range (0 to 65535).
         */
        @NonNull
        public Builder setRangingMeasurementsLimit(
                @IntRange(from = 0, to = 65535) int rangingMeasurementsLimit) {
            if (rangingMeasurementsLimit < 0
                    || rangingMeasurementsLimit > 65535) {
                throw new IllegalArgumentException(
                        "Ranging measurements limit must be between 0 and 65535");
            }
            mRangingMeasurementsLimit = rangingMeasurementsLimit;
            return this;
        }

        /**
         * Builds a new {@link SessionConfiguration} instance.
         *
         * @return the new {@link SessionConfiguration} instance.
         */
        @NonNull
        public SessionConfiguration build() {
            return new SessionConfiguration(this);
        }
    }

    @Override
    public String toString() {
        return "SessionConfiguration{"
                + "mFusionParameters="
                + mFusionParameters
                + ", mDataNotificationConfig="
                + mDataNotificationConfig
                + ", mIsAngleOfArrivalNeeded="
                + mIsAngleOfArrivalNeeded
                + ", mRangingMeasurementsLimit="
                + mRangingMeasurementsLimit
                + "}";
    }
}
