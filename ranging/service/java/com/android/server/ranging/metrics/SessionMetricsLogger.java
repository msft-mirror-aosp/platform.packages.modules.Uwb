/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.ranging.metrics;

import static android.ranging.RangingConfig.RANGING_SESSION_RAW;

import android.ranging.RangingConfig;
import android.ranging.RangingPreference;
import android.ranging.RangingSession;
import android.ranging.SessionHandle;

import com.android.server.ranging.RangingUtils.StateMachine;

public class SessionMetricsLogger {
    private final SessionHandle mSessionHandle;
    private final @RangingPreference.DeviceRole int mDeviceRole;
    private final @RangingConfig.RangingSessionType int mSessionType;
    private final StateMachine<State> mStateMachine;

    private long mLastStateChangeTimestampMs;

    public SessionMetricsLogger(
            SessionHandle sessionHandle,
            @RangingPreference.DeviceRole int deviceRole,
            @RangingConfig.RangingSessionType int sessionType
    ) {
        mSessionHandle = sessionHandle;
        mDeviceRole = deviceRole;
        mSessionType = sessionType;
        mStateMachine = new StateMachine<>(
                sessionType == RANGING_SESSION_RAW
                        ? State.STARTING
                        : State.OOB);
        mLastStateChangeTimestampMs = System.currentTimeMillis();
    }

    public synchronized void logSessionConfigured(int numPeers) {
        RangingStatsLog.write(
                RangingStatsLog.RANGING_SESSION_CONFIGURED,
                mSessionHandle.hashCode(),
                mStateMachine.getState() == State.OOB
                        ? System.currentTimeMillis() - mLastStateChangeTimestampMs
                        : 0,
                coerceUnknownEnumValueToZero(mSessionType, 2),
                coerceUnknownEnumValueToZero(mDeviceRole, 2),
                numPeers);
        mLastStateChangeTimestampMs = System.currentTimeMillis();
        mStateMachine.setState(State.STARTING);
    }

    public synchronized void logSessionStarted() {
        RangingStatsLog.write(
                RangingStatsLog.RANGING_SESSION_STARTED,
                mSessionHandle.hashCode(),
                mSessionHandle.getUid(),
                System.currentTimeMillis() - mLastStateChangeTimestampMs);
        mLastStateChangeTimestampMs = System.currentTimeMillis();
        mStateMachine.setState(State.RANGING);
    }

    public synchronized void logSessionClosed(@RangingSession.Callback.Reason int reason) {
        RangingStatsLog.write(
                RangingStatsLog.RANGING_SESSION_CLOSED,
                mSessionHandle.hashCode(),
                coerceUnknownEnumValueToZero(
                        mStateMachine.getState().toInt(), State.values().length),
                System.currentTimeMillis() - mLastStateChangeTimestampMs,
                reason);
        mLastStateChangeTimestampMs = System.currentTimeMillis();
    }

    private int coerceUnknownEnumValueToZero(int enumValue, int numEnumValues) {
        if (enumValue >= numEnumValues) {
            return 0;
        } else {
            return enumValue + 1;
        }
    }

    private enum State {
        OOB(0),
        STARTING(1),
        RANGING(2);

        private final int mValue;

        State(int value) {
            mValue = value;
        }

        public final int toInt() {
            return mValue;
        }
    }
}
