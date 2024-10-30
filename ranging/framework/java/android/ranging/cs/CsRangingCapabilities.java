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

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_CS_ENABLED)
public final class CsRangingCapabilities {

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

    public static final int CS_SECURITY_LEVEL_ONE = 1;
    public static final int CS_SECURITY_LEVEL_FOUR = 4;

    private final List<Integer> mSupportedSecurityLevels;

    public List<Integer> getSupportedSecurityLevels() {
        return mSupportedSecurityLevels;
    }

    private CsRangingCapabilities(Builder builder) {
        mSupportedSecurityLevels = builder.mSupportedSecurityLevels;
    }

    public static final class Builder {
        private List<Integer> mSupportedSecurityLevels = new ArrayList<>();

        public Builder addSupportedSecurityLevel(@SecurityLevel int securityLevel) {
            mSupportedSecurityLevels.add(securityLevel);
            return this;
        }

        public CsRangingCapabilities build() {
            return new CsRangingCapabilities(this);
        }
    }


}
