/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ranging.uwb.backend.internal;

import static com.android.ranging.uwb.backend.internal.Utils.CONFIG_MULTICAST_DS_TWR;
import static com.android.ranging.uwb.backend.internal.Utils.CONFIG_UNICAST_DS_TWR;
import static com.android.ranging.uwb.backend.internal.Utils.RANGE_DATA_NTF_ENABLE;
import static com.android.ranging.uwb.backend.internal.Utils.SUPPORTED_BPRF_PREAMBLE_INDEX;

import androidx.annotation.IntRange;

import com.google.common.collect.ImmutableList;

import java.util.List;

/** Describes UWB ranging capabilities for the current device. */
public class RangingCapabilities {
    /** Default minimum ranging interval if the system API doesn't provide it. */
    public static final int FIRA_DEFAULT_RANGING_INTERVAL_MS = 200;
    /** Default supported channel if the system API doesn't provide it. */
    public static final int FIRA_DEFAULT_SUPPORTED_CHANNEL = 9;
    /** Default supported config id if the system API doesn't provide it. */
    public static final ImmutableList<Integer> FIRA_DEFAULT_SUPPORTED_CONFIG_IDS =
            ImmutableList.of(
                    CONFIG_UNICAST_DS_TWR,
                    CONFIG_MULTICAST_DS_TWR);
    /** Ranging interval reconfigure is not supported if the system API doesn't provide. */
    public static final boolean DEFAULT_SUPPORTS_RANGING_INTERVAL_RECONFIGURE = false;
    /** Default supported slot duration if the system API doesn't provide it. */
    public static final ImmutableList<Integer> DEFAULT_SUPPORTED_SLOT_DURATIONS =
            ImmutableList.of(Utils.DURATION_2_MS);
    /** Default supported ranging interval if the system API doesn't provide it. */
    public static final ImmutableList<Integer> DEFAULT_SUPPORTED_RANGING_UPDATE_RATE =
            ImmutableList.of(Utils.NORMAL, Utils.INFREQUENT);


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
    private final List<Integer> mSupportedPreambleIndexes;
    private final boolean mHasBackgroundRangingSupport;
    private final String mCountryCode;

    public RangingCapabilities(
            boolean supportsDistance,
            boolean supportsAzimuthalAngle,
            boolean supportsElevationAngle,
            String countryCode) {
        this(
                supportsDistance,
                supportsAzimuthalAngle,
                supportsElevationAngle,
                DEFAULT_SUPPORTS_RANGING_INTERVAL_RECONFIGURE,
                FIRA_DEFAULT_RANGING_INTERVAL_MS,
                List.of(FIRA_DEFAULT_SUPPORTED_CHANNEL),
                List.of(RANGE_DATA_NTF_ENABLE),
                FIRA_DEFAULT_SUPPORTED_CONFIG_IDS,
                DEFAULT_SUPPORTED_SLOT_DURATIONS,
                DEFAULT_SUPPORTED_RANGING_UPDATE_RATE,
                SUPPORTED_BPRF_PREAMBLE_INDEX,
                false,
                countryCode);
    }

    public RangingCapabilities(
            boolean supportsDistance,
            boolean supportsAzimuthalAngle,
            boolean supportsElevationAngle,
            boolean supportsRangingIntervalReconfigure,
            int minRangingInterval,
            List<Integer> supportedChannels,
            List<Integer> supportedNtfConfigs,
            List<Integer> supportedConfigIds,
            ImmutableList<Integer> supportedSlotDurations,
            ImmutableList<Integer> supportedRangingUpdateRates,
            ImmutableList<Integer> supportedPreambleIndexes,
            boolean hasBackgroundRangingSupport,
            String countryCode) {
        this.mSupportsDistance = supportsDistance;
        this.mSupportsAzimuthalAngle = supportsAzimuthalAngle;
        this.mSupportsElevationAngle = supportsElevationAngle;
        this.mSupportsRangingIntervalReconfigure = supportsRangingIntervalReconfigure;
        this.mMinRangingInterval = minRangingInterval;
        this.mSupportedChannels = supportedChannels;
        this.mSupportedNtfConfigs = supportedNtfConfigs;
        this.mSupportedConfigIds = supportedConfigIds;
        this.mSupportedSlotDurations = supportedSlotDurations;
        this.mSupportedRangingUpdateRates = supportedRangingUpdateRates;
        this.mSupportedPreambleIndexes = supportedPreambleIndexes;
        this.mHasBackgroundRangingSupport = hasBackgroundRangingSupport;
        this.mCountryCode = countryCode;
    }

    /** Whether distance ranging is supported. */
    public boolean supportsDistance() {
        return mSupportsDistance;
    }

    /** Whether azimuthal angle of arrival is supported. */
    public boolean supportsAzimuthalAngle() {
        return mSupportsAzimuthalAngle;
    }

    /** Whether elevation angle of arrival is supported. */
    public boolean supportsElevationAngle() {
        return mSupportsElevationAngle;
    }

    /** Whether ranging interval reconfigure is supported. */
    public boolean supportsRangingIntervalReconfigure() {
        return mSupportsRangingIntervalReconfigure;
    }

    /** Gets the minimum supported ranging interval in milliseconds. */
    @IntRange(from = 0)
    public int getMinRangingInterval() {
        return mMinRangingInterval;
    }

    /** Gets the supported channel number. */
    public List<Integer> getSupportedChannels() {
        return mSupportedChannels;
    }

    /**
     * Gets the supported range data notification configs.
     *
     * @hide
     */
    public List<Integer> getSupportedNtfConfigs() {
        return mSupportedNtfConfigs;
    }

    /** Gets the supported config ids. */
    public List<Integer> getSupportedConfigIds() {
        return mSupportedConfigIds;
    }

    /** Gets the supported slot durations. */
    public List<Integer> getSupportedSlotDurations() {
        return mSupportedSlotDurations;
    }

    /** Gets the supported ranging intervals. */
    public List<Integer> getSupportedRangingUpdateRates() {
        return mSupportedRangingUpdateRates;
    }
    public List<Integer> getSupportedPreambleIndexes() {
        return mSupportedPreambleIndexes;
    }

    /** Whether background ranging is supported. */
    public boolean hasBackgroundRangingSupport() {
        return mHasBackgroundRangingSupport;
    }

    /** 2-letter ISO 3166 country code currently being used */
    public String getCountryCode() {
        return mCountryCode;
    }
}
