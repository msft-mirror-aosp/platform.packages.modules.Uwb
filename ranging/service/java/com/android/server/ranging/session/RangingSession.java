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

package com.android.server.ranging.session;

import android.ranging.RangingConfig;
import android.ranging.RangingDevice;
import android.ranging.raw.RawResponderRangingConfig;

import androidx.annotation.NonNull;

import java.io.FileDescriptor;
import java.io.PrintWriter;

public interface RangingSession<P extends RangingConfig> extends AutoCloseable {
    void start(@NonNull P params);

    void stop();

    void addPeer(RawResponderRangingConfig params);

    void removePeer(RangingDevice params);

    void reconfigureInterval(int intervalSkipCount);

    void appForegroundStateUpdated(boolean appInForeground);

    @Override
    void close();

    void dump(FileDescriptor fd, PrintWriter pw, String[] args);
}
