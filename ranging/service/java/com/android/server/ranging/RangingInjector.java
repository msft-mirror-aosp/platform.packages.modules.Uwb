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

package com.android.server.ranging;

import static android.Manifest.permission.RANGING;
import static android.permission.PermissionManager.PERMISSION_GRANTED;

import android.annotation.NonNull;
import android.content.AttributionSource;
import android.content.Context;
import android.os.Binder;
import android.permission.PermissionManager;

public class RangingInjector {

    private static final String TAG = "RangingInjector";

    private final Context mContext;
    private final RangingServiceManager mRangingServiceManager;
    private final PermissionManager mPermissionManager;
    private final CapabilitiesProvider mCapabilitiesProvider;

    public RangingInjector(@NonNull Context context) {
        mContext = context;
        mCapabilitiesProvider = new CapabilitiesProvider(this);
        mRangingServiceManager = new RangingServiceManager(this);
        mPermissionManager = context.getSystemService(PermissionManager.class);
    }

    public Context getContext() {
        return mContext;
    }

    public CapabilitiesProvider getCapabilitiesProvider() {
        return mCapabilitiesProvider;
    }

    public RangingServiceManager getRangingServiceManager() {
        return mRangingServiceManager;
    }

    /**
     * Throws security exception if the RANGING permission is not granted for the calling app.
     *
     * <p>Should be used in situations where the app op should not be noted.
     */
    public void enforceRangingPermissionForPreflight(
            @NonNull AttributionSource attributionSource) {
        if (!attributionSource.checkCallingUid()) {
            throw new SecurityException("Invalid attribution source " + attributionSource
                    + ", callingUid: " + Binder.getCallingUid());
        }
        int permissionCheckResult = mPermissionManager.checkPermissionForPreflight(
                RANGING, attributionSource);
        if (permissionCheckResult != PERMISSION_GRANTED) {
            throw new SecurityException("Caller does not hold RANGING permission");
        }
    }

    /**
     * Returns true if the RANGING permission is granted for the calling app.
     *
     * <p>Used for checking permission before first data delivery for the session.
     */
    public boolean checkUwbRangingPermissionForStartDataDelivery(
            @NonNull AttributionSource attributionSource, @NonNull String message) {
        int permissionCheckResult = mPermissionManager.checkPermissionForStartDataDelivery(
                RANGING, attributionSource, message);
        return permissionCheckResult == PERMISSION_GRANTED;
    }
}
