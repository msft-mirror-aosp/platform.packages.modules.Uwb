/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.ranging.rangingtestapp;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;

/** ViewModel for the Initiator. */
public class InitiatorViewModel extends AndroidViewModel {

    private final MutableLiveData<Constants.RangeSessionState> mSessionState =
            new MutableLiveData<>(Constants.RangeSessionState.STOPPED);

    private final MutableLiveData<Double> mDistanceResult = new MutableLiveData<>();

    private final DistanceMeasurementManager
            mDistanceMeasurementManager; // mDistanceMeasurementManager;

    public static class Factory implements ViewModelProvider.Factory {
        private Application mApplication;
        private BleConnection mBleConnection;
        private LoggingListener mLoggingListener;

        public Factory(Application application,
                       BleConnection bleConnection,
                       LoggingListener loggingListener) {
            mApplication = application;
            mBleConnection = bleConnection;
            mLoggingListener = loggingListener;
        }


        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            return (T) new InitiatorViewModel(
                    mApplication, mBleConnection, mLoggingListener);
        }
    }

    public InitiatorViewModel(@NonNull Application application,
                              BleConnection bleConnection,
                              LoggingListener loggingListener) {
        super(application);

        mDistanceMeasurementManager =
                new DistanceMeasurementManager(
                        application,
                        bleConnection,
                        mCallback,
                        loggingListener,
                        false);
    }

    void setTargetDevice(BluetoothDevice targetDevice) {
        mDistanceMeasurementManager.setTargetDevice(targetDevice);
    }

    LiveData<Constants.RangeSessionState> getSessionState() {
        return mSessionState;
    }

    LiveData<Double> getDistanceResult() {
        return mDistanceResult;
    }

    List<String> getSupportedTechnologies() {
        return mDistanceMeasurementManager.getSupportedTechnologies();
    }

    List<String> getMeasurementFreqs() {
        return mDistanceMeasurementManager.getMeasurementFreqs();
    }

    List<String> getMeasurementDurations() {
        return mDistanceMeasurementManager.getMeasureDurationsInIntervalRounds();
    }

    void toggleStartStop(String technology, String freq, int duration) {
        if (mSessionState.getValue() == Constants.RangeSessionState.STOPPED) {
            boolean success =
                    mDistanceMeasurementManager.startDistanceMeasurementManager(
                            technology, freq, duration);
            if (success) {
                mSessionState.postValue(Constants.RangeSessionState.STARTING);
            } else {
                mSessionState.postValue(Constants.RangeSessionState.STOPPED);
            }
        } else {
            mDistanceMeasurementManager.stopDistanceMeasurementManager();
            mSessionState.postValue(Constants.RangeSessionState.STOPPING);
        }
    }

    private DistanceMeasurementManager.Callback mCallback =
            new DistanceMeasurementManager.Callback() {
                @Override
                public void onStartSuccess() {
                    mSessionState.postValue(Constants.RangeSessionState.STARTED);
                }

                @Override
                public void onStartFail() {
                    mSessionState.postValue(Constants.RangeSessionState.STOPPED);
                }

                @Override
                public void onStop() {
                    mSessionState.postValue(Constants.RangeSessionState.STOPPED);
                }


                @Override
                public void onDistanceResult(double distanceMeters) {
                    mDistanceResult.postValue(distanceMeters);
                }
            };
}
