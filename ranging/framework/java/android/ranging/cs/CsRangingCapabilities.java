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

package android.ranging.cs;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.RangingCapabilities.TechnologyCapabilities;
import android.ranging.RangingManager;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents the capabilities of the Bluetooth-based Channel Sounding (CS) ranging.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_CS_ENABLED)
public final class CsRangingCapabilities implements Parcelable, TechnologyCapabilities {
    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            CS_SECURITY_LEVEL_ONE,
            CS_SECURITY_LEVEL_FOUR,
    })
    public @interface SecurityLevel {
    }

    /**
     * Security Level 1:
     * Either CS tone or CS RTT..
     */
    public static final int CS_SECURITY_LEVEL_ONE = 1;

    /**
     * Security Level 4:
     * 10 ns CS RTT accuracy and CS tones with the addition of CS RTT sounding sequence or random
     * sequence payloads, and support of the Normalized Attack Detector Metric requirements.
     */
    public static final int CS_SECURITY_LEVEL_FOUR = 4;

    private final List<Integer> mSupportedSecurityLevels;

    /**
     * Returns a list of the supported security levels.
     *
     * @return a {@link List} of integers representing the security levels,
     *         where each level is one of {@link SecurityLevel}.
     */
    @NonNull
    @SecurityLevel
    public Set<Integer> getSupportedSecurityLevels() {
        return new HashSet<>(mSupportedSecurityLevels);
    }

    private CsRangingCapabilities(Builder builder) {
        mSupportedSecurityLevels = builder.mSupportedSecurityLevels;
    }

    private CsRangingCapabilities(Parcel in) {
        mSupportedSecurityLevels = new ArrayList<>();
        in.readList(mSupportedSecurityLevels, Integer.class.getClassLoader(), Integer.class);
    }

    @NonNull
    public static final Creator<CsRangingCapabilities> CREATOR =
            new Creator<CsRangingCapabilities>() {
                @Override
                public CsRangingCapabilities createFromParcel(Parcel in) {
                    return new CsRangingCapabilities(in);
                }

                @Override
                public CsRangingCapabilities[] newArray(int size) {
                    return new CsRangingCapabilities[size];
                }
            };

    /**
     * @hide
     */
    @Override
    public int getTechnology() {
        return RangingManager.BLE_CS;
    }

    /**
     * @hide
     */
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeList(mSupportedSecurityLevels);
    }

    /**
     * Builder class for {@link CsRangingCapabilities}.
     * This class provides a fluent API for constructing instances of {@link CsRangingCapabilities}.
     *
     * @hide
     */
    public static final class Builder {
        private List<Integer> mSupportedSecurityLevels;

        /**
         * Set supported security levels to the capabilities.
         *
         * @param supportedSecurityLevels the supported security levels {@link SecurityLevel}.
         * @return this {@link Builder} instance for chaining calls.
         */
        @NonNull
        public Builder setSupportedSecurityLevels(List<Integer> supportedSecurityLevels) {
            this.mSupportedSecurityLevels = supportedSecurityLevels;
            return this;
        }

        /**
         * Builds and returns a {@link CsRangingCapabilities} instance.
         *
         * @return a new {@link CsRangingCapabilities} object.
         */
        @NonNull
        public CsRangingCapabilities build() {
            return new CsRangingCapabilities(this);
        }
    }
}