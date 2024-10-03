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

package android.server.ranging;

import android.annotation.NonNull;
import android.content.Context;

import com.android.server.ranging.RangingServiceManager;

public class RangingInjector {

    private static final String TAG = "RangingInjector";

    private final Context mContext;
    private final RangingServiceManager mRangingServiceManager;

    private final RangingCapabilitiesProvider mRangingCapabilitiesProvider;

    public RangingInjector(@NonNull Context context) {
        mContext = context;
        mRangingCapabilitiesProvider = new RangingCapabilitiesProvider(this);
        mRangingServiceManager = new RangingServiceManager(this);
    }

    public Context getContext() {
        return mContext;
    }

    public RangingCapabilitiesProvider getRangingCapabilitiesProvider() {
        return mRangingCapabilitiesProvider;
    }

    public RangingServiceManager getRangingServiceManager() {
        return mRangingServiceManager;
    }
}