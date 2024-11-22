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

package com.android.server.ranging.cs;

import static android.ranging.params.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.params.RawRangingDevice.UPDATE_RATE_NORMAL;
import static android.ranging.params.RawRangingDevice.UPDATE_RATE_INFREQUENT;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.DistanceMeasurementManager;
import android.bluetooth.le.DistanceMeasurementMethod;
import android.bluetooth.le.DistanceMeasurementParams;
import android.bluetooth.le.DistanceMeasurementResult;
import android.bluetooth.le.DistanceMeasurementSession;
import android.content.Context;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingMeasurement;
import android.ranging.cs.CsRangingParams;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingPeerConfig;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.StateMachine;

import com.google.common.annotations.VisibleForTesting;

import java.util.concurrent.Executors;

/**
 * Channel Sounding adapter for ranging.
 * TODO(b/380125808): Need to coalesce requests from multiple apps for the same remote device.
 */
public class CsAdapter implements RangingAdapter {
    private static final String TAG = CsAdapter.class.getSimpleName();

    private final BluetoothAdapter mBluetoothAdapter;
    private final StateMachine<State> mStateMachine;

    /** Invariant: non-null while a ranging session is active */
    private Callback mCallbacks;

    /** Invariant: non-null while a ranging session is active */
    private BluetoothDevice mDeviceFromPeerBluetoothAddress;

    /** Invariant: non-null while a ranging session is active */
    private RangingDevice mRangingDevice;

    /** Invariant: non-null while a ranging session is active */
    private DistanceMeasurementSession mSession;

    /** Injectable constructor for testing. */
    @VisibleForTesting
    public CsAdapter(@NonNull Context context) {
        if (!RangingTechnology.CS.isSupported(context)) {
            throw new IllegalArgumentException("BT_CS system feature not found.");
        }
        mBluetoothAdapter = context.getSystemService(BluetoothManager.class).getAdapter();
        mStateMachine = new StateMachine<>(State.STOPPED);
        mCallbacks = null;
        mSession = null;
    }

    @Override
    public RangingTechnology getType() {
        return RangingTechnology.CS;
    }

    @Override
    public void start(RangingPeerConfig.TechnologyConfig config, Callback callback) {
        Log.i(TAG, "Start called.");
        if (!mStateMachine.transition(State.STOPPED, State.STARTED)) {
            Log.v(TAG, "Attempted to start adapter when it was already started");
            return;
        }

        if (!(config instanceof CsConfig csConfig)) {
            Log.w(TAG, "Tried to start adapter with invalid ranging parameters");
            return;
        }

        CsRangingParams csRangingParams = csConfig.getRangingParams();
        if ((csConfig.getPeerDevice() == null)
                || (csRangingParams.getPeerBluetoothAddress() == null)) {
            Log.e(TAG, "Peer device is null");
            return;
        }

        mCallbacks = callback;
        mRangingDevice = csConfig.getPeerDevice();
        mDeviceFromPeerBluetoothAddress =
                mBluetoothAdapter.getRemoteDevice(csRangingParams.getPeerBluetoothAddress());
        DistanceMeasurementManager distanceMeasurementManager =
                mBluetoothAdapter.getDistanceMeasurementManager();
        int duration = DistanceMeasurementParams.getMaxDurationSeconds();
        int frequency = getFrequency(csRangingParams.getRangingUpdateRate());
        int methodId = DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_CHANNEL_SOUNDING;

        DistanceMeasurementParams params =
                new DistanceMeasurementParams.Builder(mDeviceFromPeerBluetoothAddress)
                        .setDurationSeconds(duration)
                        .setFrequency(frequency)
                        .setMethodId(methodId)
                        .build();

        distanceMeasurementManager.startMeasurementSession(params,
                Executors.newSingleThreadExecutor(), mDistanceMeasurementCallback);
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stop called.");
        if (!mStateMachine.transition(State.STARTED, State.STOPPED)) {
            Log.v(TAG, "Attempted to stop adapter when it was already stopped");
            return;
        }

        if (mSession == null) {
            Log.v(TAG, "Attempted to stop adapter when ranging session was already stopped");
            return;
        }
        mSession.stopSession();
    }

    private int getFrequency(int updateRate) {
        if (updateRate == UPDATE_RATE_INFREQUENT) {
            return DistanceMeasurementParams.REPORT_FREQUENCY_LOW;
        } else if (updateRate == UPDATE_RATE_NORMAL) {
            return DistanceMeasurementParams.REPORT_FREQUENCY_MEDIUM;
        } else if (updateRate == UPDATE_RATE_FREQUENT) {
            return DistanceMeasurementParams.REPORT_FREQUENCY_HIGH;
        }
        return DistanceMeasurementParams.REPORT_FREQUENCY_LOW;
    }

    private void clear() {
        mSession = null;
        mCallbacks = null;
    }

    public enum State {
        STARTED,
        STOPPED,
    }

    private DistanceMeasurementSession.Callback mDistanceMeasurementCallback =
            new DistanceMeasurementSession.Callback() {
                public void onStarted(DistanceMeasurementSession session) {
                    Log.i(TAG, "DistanceMeasurement onStarted !");
                    mSession = session;
                    mCallbacks.onStarted();
                }

                public void onStartFail(int reason) {
                    Log.i(TAG, "DistanceMeasurement onStartFail ! reason " + reason);
                    mCallbacks.onStopped(RangingAdapter.Callback.StoppedReason.FAILED_TO_START);
                    clear();
                }

                public void onStopped(DistanceMeasurementSession session, int reason) {
                    Log.i(TAG, "DistanceMeasurement onStopped ! reason " + reason);
                    mCallbacks.onStopped(RangingAdapter.Callback.StoppedReason.REQUESTED);
                    clear();
                }

                public void onResult(BluetoothDevice device, DistanceMeasurementResult result) {
                    Log.i(TAG, "DistanceMeasurement onResult ! "
                                    + result.getResultMeters()
                                    + ", "
                                    + result.getErrorMeters());
                    RangingData.Builder dataBuilder = new RangingData.Builder()
                            .setRangingTechnology((int) RangingTechnology.CS.getValue())
                            .setDistance(new RangingMeasurement.Builder()
                                    .setMeasurement(result.getResultMeters())
                                    .build())
                            .setTimestampMillis(result.getMeasurementTimestampNanos() * 1000)
                            .setAzimuth(new RangingMeasurement.Builder()
                                    .setMeasurement(result.getAzimuthAngle())
                                    .build())
                            .setElevation(new RangingMeasurement.Builder()
                                    .setMeasurement(result.getAltitudeAngle())
                                    .build());

                    synchronized (mStateMachine) {
                        if (mStateMachine.getState() == State.STARTED) {
                            mCallbacks.onRangingData(mRangingDevice, dataBuilder.build());
                        }
                    }
                }
            };
}
