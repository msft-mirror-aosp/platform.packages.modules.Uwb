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
package com.android.server.uwb.discovery;

import androidx.annotation.WorkerThread;

/** Abstract class for Discovery Advertise Provider */
@WorkerThread
public abstract class DiscoveryAdvertiseProvider {

    /** Callback for listening to discovery events. */
    @WorkerThread
    public interface DiscoveryAdvertiseCallback {
        /**
         * Called when discovery failed.
         * @param errorCode discovery failure error code.
         */
        void onDiscoveryFailed(int errorCode);
    }

    // Indicates whether discovery advertising has started.
    protected boolean mStarted = false;

    /**
     * Check if advertising has started.
     * @return indicates if advertising has started.
     */
    public boolean isStarted() {
        return mStarted;
    }

    /**
     * Start advertising
     * @return indicates if succeefully started.
     */
    public abstract boolean startAdvertise();

    /**
     * Stop advertising
     * @return indicates if succeefully stopped.
     */
    public abstract boolean stopAdvertise();
}
