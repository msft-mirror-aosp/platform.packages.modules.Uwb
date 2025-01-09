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

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.android.ranging.rangingtestapp.Constants.GattState;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** The ViewModel for the BLE GATT connection. */
@SuppressLint("MissingPermission") // permissions are checked upfront
public class BleConnectionCentralViewModel extends AndroidViewModel implements BleConnection {
    private static final int GATT_MTU_SIZE = 512;
    private final BluetoothAdapter mBluetoothAdapter;
    private final BluetoothManager mBluetoothManager;
    private final LoggingListener mLoggingListener;
    @Nullable private Set<BluetoothDevice> mConnectedDevices = new ArraySet<>();
    @Nullable private Set<BluetoothGatt> mConnectedGatts = new ArraySet<>();
    private MutableLiveData<BluetoothDevice> mTargetDevice = new MutableLiveData<>();
    // scanner
    private final MutableLiveData<List<String>> mConnectedDeviceAddresses = new MutableLiveData<>();
    private final MutableLiveData<GattState> mGattState = new MutableLiveData<>(GattState.DISCONNECTED);
    private String mTargetBtAddress = "";
    private CountDownLatch mPsmCountDownLatch = new CountDownLatch(1);
    private int mPsm = -1;

    public static class Factory implements ViewModelProvider.Factory {
        private Application mApplication;
        private LoggingListener mLoggingListener;

        public Factory(Application application,
                       LoggingListener loggingListener) {
            mApplication = application;
            mLoggingListener = loggingListener;
        }


        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            return (T) new BleConnectionCentralViewModel(
                    mApplication, mLoggingListener);
        }
    }

    /** Constructor */
    public BleConnectionCentralViewModel(@NonNull Application application,
                                         LoggingListener loggingListener) {
        super(application);
        mBluetoothManager = application.getSystemService(BluetoothManager.class);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mLoggingListener = loggingListener;
    }

    LiveData<GattState> getGattState() {
        return mGattState;
    }

    LiveData<List<String>> getConnectedDeviceAddresses() {
        return mConnectedDeviceAddresses;
    }

    LiveData<BluetoothDevice> getTargetDevice() {
        return mTargetDevice;
    }

    private void updateConnectedDevices() {
        List<String> connectedDevices =
                // Append name to the address for better readability
                mConnectedDevices.stream()
                        .map(d -> d.getAddress() + "(" + d.getName() + ")")
                        .collect(Collectors.toList());
        mConnectedDeviceAddresses.postValue(connectedDevices);
    }

    void setTargetDevice(String deviceAddressAndName) {
        printLog("set target address: " + deviceAddressAndName);
        if (!TextUtils.isEmpty(deviceAddressAndName)) {
            mTargetBtAddress = deviceAddressAndName.substring(0, 17); // Remove the name appended
            mTargetDevice.postValue(mBluetoothAdapter.getRemoteDevice(mTargetBtAddress));
        } else {
            mTargetBtAddress = "";
            mTargetDevice.postValue(null);
        }
    }

    @Override
    public int waitForPsm() {
        boolean success = false;
        try {
            success = mPsmCountDownLatch.await(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            printLog("Failed to wait for PSM" + e);
        }
        if (!success) printLog("Timed out waiting for PSM");
        return mPsm;
    }

    void toggleScanConnect() {
        if (mGattState.getValue() == GattState.DISCONNECTED) {
            connectGattByScanning();
        } else if (mGattState.getValue() == GattState.SCANNING) {
            stopScanning();
        } else if (mGattState.getValue() == GattState.CONNECTED) {
            disconnectGatt();
        }
    }

    private BluetoothGattCallback mGattCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    printLog("onConnectionStateChange status:" + status + ", newState:" + newState);
                    BluetoothDevice device = gatt.getDevice();
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        printLog(device.getName() + " is connected");
                        gatt.requestMtu(GATT_MTU_SIZE);
                        gatt.discoverServices();
                        if (mTargetBtAddress.equals(device.getAddress())) {
                            mTargetDevice.postValue(device);
                        }
                        mConnectedDevices.add(device);
                        mConnectedGatts.add(gatt);
                        mGattState.postValue(GattState.CONNECTED);
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        printLog("disconnected from " + device.getName());
                        gatt.close();
                        if (mTargetBtAddress.equals(device.getAddress())) {
                            mTargetDevice.postValue(null);
                        }
                        mConnectedDevices.remove(device);
                        mConnectedGatts.remove(gatt);
                        if (mConnectedDevices.isEmpty()) {
                            mGattState.postValue(GattState.DISCONNECTED);
                        }
                    }
                    updateConnectedDevices();
                }

                @Override
                public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        printLog("MTU changed to: " + mtu);
                    } else {
                        printLog("MTU change failed: " + status);
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        for (BluetoothGattService service : gatt.getServices()) {
                            if (service.getUuid().equals(Constants.OOB_SERVICE)) {
                                printLog("Listening for PSM characteristics change");
                                BluetoothGattCharacteristic characteristic =
                                        service.getCharacteristic(
                                                Constants.OOB_PSM_CHARACTERISTICS);
                                if (characteristic == null) {
                                    printLog("Failed to get PSM characteristic");
                                    return;
                                }
                                gatt.setCharacteristicNotification(characteristic, true);
                            }
                        }
                    } else {
                        printLog("Service discovery failed: " + status);
                    }
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt,
                        BluetoothGattCharacteristic characteristic, byte[] data) {
                    printLog("GATT characteristics changed: " + characteristic + ", data: "
                                 + Arrays.toString(data));
                    if (characteristic.getUuid().equals(Constants.OOB_PSM_CHARACTERISTICS)) {
                        mPsm = ByteBuffer.wrap(data).getInt();
                        printLog("Received PSM value " + mPsm);
                        mPsmCountDownLatch.countDown();
                    }
                }
            };

    private ScanCallback mScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    List<ParcelUuid> serviceUuids = result.getScanRecord().getServiceUuids();
                    if (serviceUuids != null) {
                        for (ParcelUuid parcelUuid : serviceUuids) {
                            BluetoothDevice btDevice = result.getDevice();
                            printLog("found device - " + btDevice.getName());
                            if (parcelUuid.getUuid().equals(Constants.RANGING_TEST_SERVICE_UUID)) {
                                stopScanning();
                                printLog("connect GATT to: " + btDevice.getName());
                                // Connect to the GATT server
                                btDevice.connectGatt(
                                        getApplication().getApplicationContext(),
                                        false,
                                        mGattCallback,
                                        BluetoothDevice.TRANSPORT_LE);
                            }
                        }
                    }
                }
            };

    private void connectGattByScanning() {
        BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter filter =
                new ScanFilter.Builder()
                        .setServiceUuid(
                                new ParcelUuid(Constants.RANGING_TEST_SERVICE_UUID))
                        .build();
        filters.add(filter);

        ScanSettings settings =
                new ScanSettings.Builder()
                        .setLegacy(false)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setReportDelay(0)
                        .build();

        printLog("start scanning...");

        // Start scanning
        bluetoothLeScanner.startScan(filters, settings, mScanCallback);
        mGattState.setValue(GattState.SCANNING);
    }

    private void stopScanning() {
        BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(mScanCallback);
            mGattState.setValue(GattState.DISCONNECTED);
        }
    }

    private void disconnectGatt() {
        for (BluetoothGatt gatt: mConnectedGatts) {
            printLog("disconnect from " + gatt.getDevice().getName());
            gatt.disconnect();
        }
    }

    private void printLog(@NonNull String logMsg) {
        mLoggingListener.log(logMsg);
    }
}
