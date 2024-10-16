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
import android.annotation.NonNull;

import com.android.ranging.flags.Flags;

/**
 * Listener interface to receive the availabilities and capabilities of all the ranging technology
 * supported by the device.
 *
 * <p>This interface is used to asynchronously provide information about the
 * supported ranging capabilities of the device. The listener's callback
 * is invoked when the capabilities data is available.</p>
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public interface RangingCapabilitiesListener {

    /**
     * Called when the ranging capabilities are available.
     *
     * @param capabilities the {@link RangingCapabilities} object containing
     *                     detailed information about the supported features
     *                     and limitations of the ranging technology.
     */
    void onRangingCapabilities(@NonNull RangingCapabilities capabilities);
}
