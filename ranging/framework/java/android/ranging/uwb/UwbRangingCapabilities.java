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
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.android.ranging.flags.Flags;

import java.util.ArrayList;
import java.util.List;

/**
 * The type Uwb ranging capabilities.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class UwbRangingCapabilities implements Parcelable {

    private final boolean mSupportsDistance;
    private final boolean mSupportsAzimuthalAngle;
    private final boolean mSupportsElevationAngle;
    private final boolean mSupportsRangingIntervalReconfigure;
    private final int mMinRangingInterval;
    private final List<Integer> mSupportedChannels;
    private final List<Integer> mSupportedNtfConfigs;
    private final List<Integer> mSupportedConfigIds;
    private final List<Integer> mSupportedSlotDurations;
    private final List<Integer> mSupportedRangingUpdateRates;
    private final boolean mHasBackgroundRangingSupport;

    private UwbRangingCapabilities(Builder builder) {
        mSupportsDistance = builder.mSupportsDistance;
        mSupportsAzimuthalAngle = builder.mSupportsAzimuthalAngle;
        mSupportsElevationAngle = builder.mSupportsElevationAngle;
        mSupportsRangingIntervalReconfigure = builder.mSupportsRangingIntervalReconfigure;
        mMinRangingInterval = builder.mMinRangingInterval;
        mSupportedChannels = builder.mSupportedChannels;
        mSupportedNtfConfigs = builder.mSupportedNtfConfigs;
        mSupportedConfigIds = builder.mSupportedConfigIds;
        mSupportedSlotDurations = builder.mSupportedSlotDurations;
        mSupportedRangingUpdateRates = builder.mSupportedRangingUpdateRates;
        mHasBackgroundRangingSupport = builder.mHasBackgroundRangingSupport;
    }

    protected UwbRangingCapabilities(Parcel in) {
        mSupportsDistance = in.readByte() != 0;
        mSupportsAzimuthalAngle = in.readByte() != 0;
        mSupportsElevationAngle = in.readByte() != 0;
        mSupportsRangingIntervalReconfigure = in.readByte() != 0;
        mMinRangingInterval = in.readInt();
        mSupportedChannels = new ArrayList<>();
        in.readList(mSupportedChannels, Integer.class.getClassLoader(), Integer.class);
        mSupportedNtfConfigs = new ArrayList<>();
        in.readList(mSupportedNtfConfigs, Integer.class.getClassLoader(), Integer.class);
        mSupportedConfigIds = new ArrayList<>();
        in.readList(mSupportedConfigIds, Integer.class.getClassLoader(), Integer.class);
        mSupportedSlotDurations = new ArrayList<>();
        in.readList(mSupportedSlotDurations, Integer.class.getClassLoader(), Integer.class);
        mSupportedRangingUpdateRates = new ArrayList<>();
        in.readList(mSupportedRangingUpdateRates, Integer.class.getClassLoader(), Integer.class);
        mHasBackgroundRangingSupport = in.readByte() != 0;
    }

    public static final Creator<UwbRangingCapabilities> CREATOR =
            new Creator<UwbRangingCapabilities>() {
                @Override
                public UwbRangingCapabilities createFromParcel(Parcel in) {
                    return new UwbRangingCapabilities(in);
                }

                @Override
                public UwbRangingCapabilities[] newArray(int size) {
                    return new UwbRangingCapabilities[size];
                }
            };

    /**
     * Is supports distance boolean.
     *
     * @return the boolean
     */
    public boolean isSupportsDistance() {
        return mSupportsDistance;
    }

    /**
     * Is supports azimuthal angle boolean.
     *
     * @return the boolean
     */
    public boolean isSupportsAzimuthalAngle() {
        return mSupportsAzimuthalAngle;
    }

    /**
     * Is supports elevation angle boolean.
     *
     * @return the boolean
     */
    public boolean isSupportsElevationAngle() {
        return mSupportsElevationAngle;
    }

    /**
     * Is supports ranging interval reconfigure boolean.
     *
     * @return the boolean
     */
    public boolean isSupportsRangingIntervalReconfigure() {
        return mSupportsRangingIntervalReconfigure;
    }

    /**
     * Gets min ranging interval.
     *
     * @return the min ranging interval
     */
    public int getMinRangingInterval() {
        return mMinRangingInterval;
    }

    /**
     * Gets supported channels.
     *
     * @return the supported channels
     */
    public List<Integer> getSupportedChannels() {
        return mSupportedChannels;
    }

    /**
     * Gets supported ntf configs.
     *
     * @return the supported ntf configs
     */
    public List<Integer> getSupportedNtfConfigs() {
        return mSupportedNtfConfigs;
    }

    /**
     * Gets supported config ids.
     *
     * @return the supported config ids
     */
    public List<Integer> getSupportedConfigIds() {
        return mSupportedConfigIds;
    }

    /**
     * Gets supported slot durations.
     *
     * @return the supported slot durations
     */
    public List<Integer> getSupportedSlotDurations() {
        return mSupportedSlotDurations;
    }

    /**
     * Gets supported ranging update rates.
     *
     * @return the supported ranging update rates
     */
    public List<Integer> getSupportedRangingUpdateRates() {
        return mSupportedRangingUpdateRates;
    }

    /**
     * Is has background ranging support boolean.
     *
     * @return the boolean
     */
    public boolean isHasBackgroundRangingSupport() {
        return mHasBackgroundRangingSupport;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeByte((byte) (mSupportsDistance ? 1 : 0));
        dest.writeByte((byte) (mSupportsAzimuthalAngle ? 1 : 0));
        dest.writeByte((byte) (mSupportsElevationAngle ? 1 : 0));
        dest.writeByte((byte) (mSupportsRangingIntervalReconfigure ? 1 : 0));
        dest.writeInt(mMinRangingInterval);
        dest.writeList(mSupportedChannels);
        dest.writeList(mSupportedNtfConfigs);
        dest.writeList(mSupportedConfigIds);
        dest.writeList(mSupportedSlotDurations);
        dest.writeList(mSupportedRangingUpdateRates);
        dest.writeByte((byte) (mHasBackgroundRangingSupport ? 1 : 0));
    }

    /**
     * The type Builder.
     */
    public static class Builder {
        private boolean mSupportsDistance;
        private boolean mSupportsAzimuthalAngle;
        private boolean mSupportsElevationAngle;
        private boolean mSupportsRangingIntervalReconfigure;
        private int mMinRangingInterval;
        private List<Integer> mSupportedChannels;
        private List<Integer> mSupportedNtfConfigs;
        private List<Integer> mSupportedConfigIds;
        private List<Integer> mSupportedSlotDurations;
        private List<Integer> mSupportedRangingUpdateRates;
        private boolean mHasBackgroundRangingSupport;

        /**
         * Sets supports distance.
         *
         * @param supportsDistance the supports distance
         * @return the supports distance
         */
        public Builder setSupportsDistance(boolean supportsDistance) {
            this.mSupportsDistance = supportsDistance;
            return this;
        }

        /**
         * Sets supports azimuthal angle.
         *
         * @param supportsAzimuthalAngle the supports azimuthal angle
         * @return the supports azimuthal angle
         */
        public Builder setSupportsAzimuthalAngle(boolean supportsAzimuthalAngle) {
            this.mSupportsAzimuthalAngle = supportsAzimuthalAngle;
            return this;
        }

        /**
         * Sets supports elevation angle.
         *
         * @param supportsElevationAngle the supports elevation angle
         * @return the supports elevation angle
         */
        public Builder setSupportsElevationAngle(boolean supportsElevationAngle) {
            this.mSupportsElevationAngle = supportsElevationAngle;
            return this;
        }

        /**
         * Sets supports ranging interval reconfigure.
         *
         * @param supportsRangingIntervalReconfigure the supports ranging interval reconfigure
         * @return the supports ranging interval reconfigure
         */
        public Builder setSupportsRangingIntervalReconfigure(
                boolean supportsRangingIntervalReconfigure) {
            this.mSupportsRangingIntervalReconfigure = supportsRangingIntervalReconfigure;
            return this;
        }

        /**
         * Sets min ranging interval.
         *
         * @param minRangingInterval the min ranging interval
         * @return the min ranging interval
         */
        public Builder setMinRangingInterval(int minRangingInterval) {
            this.mMinRangingInterval = minRangingInterval;
            return this;
        }

        /**
         * Sets supported channels.
         *
         * @param supportedChannels the supported channels
         * @return the supported channels
         */
        public Builder setSupportedChannels(List<Integer> supportedChannels) {
            this.mSupportedChannels = supportedChannels;
            return this;
        }

        /**
         * Sets supported ntf configs.
         *
         * @param supportedNtfConfigs the supported ntf configs
         * @return the supported ntf configs
         */
        public Builder setSupportedNtfConfigs(List<Integer> supportedNtfConfigs) {
            this.mSupportedNtfConfigs = supportedNtfConfigs;
            return this;
        }

        /**
         * Sets supported config ids.
         *
         * @param supportedConfigIds the supported config ids
         * @return the supported config ids
         */
        public Builder setSupportedConfigIds(List<Integer> supportedConfigIds) {
            this.mSupportedConfigIds = supportedConfigIds;
            return this;
        }

        /**
         * Sets supported slot durations.
         *
         * @param supportedSlotDurations the supported slot durations
         * @return the supported slot durations
         */
        public Builder setSupportedSlotDurations(List<Integer> supportedSlotDurations) {
            this.mSupportedSlotDurations = supportedSlotDurations;
            return this;
        }

        /**
         * Sets supported ranging update rates.
         *
         * @param supportedRangingUpdateRates the supported ranging update rates
         * @return the supported ranging update rates
         */
        public Builder setSupportedRangingUpdateRates(List<Integer> supportedRangingUpdateRates) {
            this.mSupportedRangingUpdateRates = supportedRangingUpdateRates;
            return this;
        }

        /**
         * Sets has background ranging support.
         *
         * @param hasBackgroundRangingSupport the has background ranging support
         * @return the has background ranging support
         */
        public Builder setHasBackgroundRangingSupport(boolean hasBackgroundRangingSupport) {
            this.mHasBackgroundRangingSupport = hasBackgroundRangingSupport;
            return this;
        }

        /**
         * Build uwb ranging capabilities.
         *
         * @return the uwb ranging capabilities
         */
        public UwbRangingCapabilities build() {
            return new UwbRangingCapabilities(this);
        }
    }
}
