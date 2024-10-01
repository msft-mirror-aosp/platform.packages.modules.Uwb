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

import android.annotation.Nullable;
import android.os.RemoteException;

import com.android.server.ranging.RangingParameters;
import com.android.server.ranging.cs.CsAdapter;
import com.android.server.ranging.uwb.UwbAdapter;

import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executors;

public class RangingCapabilitiesProvider {

    private final RangingInjector mRangingInjector;
    @Nullable
    private com.android.server.ranging.uwb.UwbAdapter mUwbAdapter;
    @Nullable
    private com.android.server.ranging.cs.CsAdapter mCsAdapter;

    public RangingCapabilitiesProvider(RangingInjector rangingInjector) {
        mRangingInjector = rangingInjector;
        if (UwbAdapter.isSupported(mRangingInjector.getContext())) {
            mUwbAdapter = new UwbAdapter(mRangingInjector.getContext(),
                    MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
                    RangingParameters.DeviceRole.CONTROLLER);
        }
        if (CsAdapter.isSupported(mRangingInjector.getContext())) {
            mCsAdapter = new CsAdapter();
        }
    }

    public android.ranging.RangingCapabilities getCapabilities() throws RemoteException {
        //TODO: Implement this.
        return null;
    }
}
