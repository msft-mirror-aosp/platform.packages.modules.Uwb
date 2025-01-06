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

import static android.ranging.uwb.UwbComplexChannel.UWB_CHANNEL_9;
import static android.ranging.uwb.UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_11;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.ranging.RangingConfig;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingPreference;
import android.ranging.SessionConfig;
import android.ranging.ble.cs.BleCsRangingParams;
import android.ranging.ble.rssi.BleRssiRangingParams;
import android.ranging.oob.DeviceHandle;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.oob.OobResponderRangingConfig;
import android.ranging.raw.RawInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.raw.RawResponderRangingConfig;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;
import android.ranging.wifi.rtt.RttRangingParams;

import java.time.Duration;
import java.util.UUID;

/** Utility class to hold ranging params shared across peer devices */
public class RangingParameters {
    public enum Freq {
        HIGH(RawRangingDevice.UPDATE_RATE_FREQUENT),
        MEDIUM(RawRangingDevice.UPDATE_RATE_NORMAL),
        LOW(RawRangingDevice.UPDATE_RATE_INFREQUENT);
        public final int freq;
        Freq(int freq) {
            this.freq = freq;
        }
        int getFreq() {
            return freq;
        }

        Duration getSlowestIntervalDuration() {
            switch (this) {
                case HIGH:
                    return Duration.ofMillis(1000);
                case MEDIUM:
                    return Duration.ofMillis(5000);
                case LOW:
                    return Duration.ofMillis(10000);
                default:
                    return Duration.ofMillis(100000);
            }
        }

        Duration getFastestIntervalDuration() {
            switch (this) {
                case HIGH:
                    return Duration.ofMillis(100);
                case MEDIUM:
                    return Duration.ofMillis(1000);
                case LOW:
                    return Duration.ofMillis(5000);
                default:
                    return Duration.ofMillis(10000);
            }
        }

        @Override
        public String toString() {
            return name();
        }
        public static Freq fromName(String name) {
            try {
                return Freq.valueOf(name);
            } catch (IllegalArgumentException e) {
                return MEDIUM;
            }
        }
    }

    public enum Technology {
        UWB(RangingManager.UWB),
        BLE_RSSI(RangingManager.BLE_RSSI),
        BLE_CS(RangingManager.BLE_CS),
        WIFI_NAN_RTT(RangingManager.WIFI_NAN_RTT),
        OOB(1000);
        public final int technology;
        Technology(int technology) {
            this.technology = technology;
        }
        int getTechnology() {
            return technology;
        }
        @Override
        public String toString() {
            return name();
        }
        public static Technology fromName(String name) {
            try {
                return Technology.valueOf(name);
            } catch (IllegalArgumentException e) {
                return BLE_RSSI;
            }
        }
    }

    public static RangingDevice INITIATOR_DEVICE = new RangingDevice.Builder()
            .setUuid(UUID.nameUUIDFromBytes("initiator".getBytes()))
            .build();
    public static RangingDevice RESPONDER_DEVICE = new RangingDevice.Builder()
            .setUuid(UUID.nameUUIDFromBytes("responder".getBytes()))
            .build();
    private static final byte[] UWB_SESSION_KEY = new byte[] {
            0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8,
            0x8, 0x7, 0x6, 0x5, 0x4, 0x3, 0x2, 0x1
    };
    private static final int UWB_SESSION_ID = 5;
    private static final int UWB_CONFIG_ID = UwbRangingParams.CONFIG_PROVISIONED_MULTICAST_DS_TWR;
    private static final UwbAddress UWB_INITIATOR_ADDRESS =
            UwbAddress.fromBytes(new byte[]{0x5, 0x6});
    private static final UwbAddress UWB_RESPONDER_ADDRESS =
            UwbAddress.fromBytes(new byte[]{0x6, 0x5});
    private static final UwbRangingParams.Builder UWB_INITIATOR_PARAMS_BUILDER =
            new UwbRangingParams.Builder(UWB_SESSION_ID, UWB_CONFIG_ID,
                    UWB_INITIATOR_ADDRESS, UWB_RESPONDER_ADDRESS)
                    .setComplexChannel(new UwbComplexChannel.Builder()
                            .setChannel(UWB_CHANNEL_9)
                            .setPreambleIndex(UWB_PREAMBLE_CODE_INDEX_11)
                            .build())
                    .setSessionKeyInfo(UWB_SESSION_KEY);
    private static final UwbRangingParams.Builder UWB_RESPONDER_PARAMS_BUILDER =
            new UwbRangingParams.Builder(UWB_SESSION_ID, UWB_CONFIG_ID,
                    UWB_RESPONDER_ADDRESS, UWB_INITIATOR_ADDRESS)
                    .setComplexChannel(new UwbComplexChannel.Builder()
                            .setChannel(UWB_CHANNEL_9)
                            .setPreambleIndex(UWB_PREAMBLE_CODE_INDEX_11)
                            .build())
                    .setSessionKeyInfo(UWB_SESSION_KEY);

    private static final String WIFI_NAN_RTT_SERVICE_NAME = "ranging_service";

    private static RawInitiatorRangingConfig createRawInitiatorConfig(
            String rangingTechnologyName, String freqName, BluetoothDevice targetBtDevice) {
        RawRangingDevice.Builder rawRangingDeviceBuilder = new RawRangingDevice.Builder()
                .setRangingDevice(RangingParameters.RESPONDER_DEVICE);
        if (Technology.fromName(rangingTechnologyName).equals(Technology.UWB)) {
            rawRangingDeviceBuilder.setUwbRangingParams(
                    RangingParameters.UWB_INITIATOR_PARAMS_BUILDER
                            .setRangingUpdateRate(Freq.fromName(freqName).freq)
                            .build());
        } else if (Technology.fromName(rangingTechnologyName).equals(Technology.BLE_CS)) {
            rawRangingDeviceBuilder.setCsRangingParams(
                    new BleCsRangingParams.Builder(targetBtDevice.getAddress())
                            .setRangingUpdateRate(Freq.fromName(freqName).freq)
                            .build());
        } else if (Technology.fromName(rangingTechnologyName).equals(Technology.BLE_RSSI)) {
            rawRangingDeviceBuilder.setBleRssiRangingParams(
                    new BleRssiRangingParams.Builder(targetBtDevice.getAddress())
                            .setRangingUpdateRate(Freq.fromName(freqName).freq)
                            .build());
        } else if (Technology.fromName(rangingTechnologyName).equals(Technology.WIFI_NAN_RTT)) {
            rawRangingDeviceBuilder.setRttRangingParams(
                    new RttRangingParams.Builder(WIFI_NAN_RTT_SERVICE_NAME)
                            .setRangingUpdateRate(Freq.fromName(freqName).freq)
                            .build());
        }
        return new RawInitiatorRangingConfig.Builder()
            .addRawRangingDevice(rawRangingDeviceBuilder.build())
            .build();
    }

    private static OobInitiatorRangingConfig createOobInitiatorConfig(
            Context context, BleConnectionCentralViewModel bleConnectionCentralViewModel,
            LoggingListener loggingListener, String freqName, BluetoothDevice targetBtDevice) {
        OobBleClient oobBleClient =
                new OobBleClient(context, bleConnectionCentralViewModel, targetBtDevice,
                        loggingListener);
        if (!oobBleClient.waitForSocketCreation()) {
            oobBleClient.close();
            return null;
        }
        return new OobInitiatorRangingConfig.Builder()
            .addDeviceHandle(
                new DeviceHandle.Builder(RESPONDER_DEVICE, oobBleClient)
                    .build())
            .setSecurityLevel(OobInitiatorRangingConfig.SECURITY_LEVEL_SECURE)
            .setSlowestRangingInterval(Freq.fromName(freqName).getSlowestIntervalDuration())
            .setFastestRangingInterval(Freq.fromName(freqName).getFastestIntervalDuration())
            .build();
    }

    public static RangingPreference createInitiatorRangingPreference(
            Context context, BleConnectionCentralViewModel bleConnectionCentralViewModel,
            LoggingListener loggingListener, String rangingTechnologyName, String freqName,
            int duration, BluetoothDevice targetBtDevice) {
        RangingConfig initiatorRangingConfig = null;
        if (Technology.fromName(rangingTechnologyName).equals(Technology.OOB)) {
            initiatorRangingConfig =
                    createOobInitiatorConfig(context, bleConnectionCentralViewModel,
                            loggingListener, freqName, targetBtDevice);
        } else {
            initiatorRangingConfig =
                createRawInitiatorConfig(rangingTechnologyName, freqName, targetBtDevice);
        }
        if (initiatorRangingConfig == null) return null;
        SessionConfig sessionConfig = new SessionConfig.Builder()
                .setRangingMeasurementsLimit(duration)
                .build();
        RangingPreference rangingPreference = new RangingPreference.Builder(
                RangingPreference.DEVICE_ROLE_INITIATOR, initiatorRangingConfig)
                .setSessionConfig(sessionConfig)
                .build();
        return rangingPreference;
    }

    private static RawResponderRangingConfig createRawResponderConfig(
            String rangingTechnologyName, String freqName, BluetoothDevice targetBtDevice) {
        RawRangingDevice.Builder rawRangingDeviceBuilder = new RawRangingDevice.Builder()
                .setRangingDevice(RangingParameters.INITIATOR_DEVICE);
        if (Technology.fromName(rangingTechnologyName).equals(Technology.UWB)) {
            rawRangingDeviceBuilder.setUwbRangingParams(
                    RangingParameters.UWB_RESPONDER_PARAMS_BUILDER
                            .setRangingUpdateRate(Freq.fromName(freqName).freq)
                            .build());
        } else if (Technology.fromName(rangingTechnologyName).equals(Technology.BLE_CS)) {
            rawRangingDeviceBuilder.setCsRangingParams(
                    new BleCsRangingParams.Builder(targetBtDevice.getAddress())
                            .setRangingUpdateRate(Freq.fromName(freqName).freq)
                            .build());
        } else if (Technology.fromName(rangingTechnologyName).equals(Technology.BLE_RSSI)) {
            rawRangingDeviceBuilder.setBleRssiRangingParams(
                    new BleRssiRangingParams.Builder(targetBtDevice.getAddress())
                            .setRangingUpdateRate(Freq.fromName(freqName).freq)
                            .build());
        } else if (Technology.fromName(rangingTechnologyName).equals(Technology.WIFI_NAN_RTT)) {
            rawRangingDeviceBuilder.setRttRangingParams(
                    new RttRangingParams.Builder(WIFI_NAN_RTT_SERVICE_NAME)
                            .setRangingUpdateRate(Freq.fromName(freqName).freq)
                            .build());
        }
        return new RawResponderRangingConfig.Builder()
            .setRawRangingDevice(rawRangingDeviceBuilder.build())
            .build();
    }

    private static OobResponderRangingConfig createOobResponderConfig(
            Context context, BleConnectionPeripheralViewModel bleConnectionPeripheralViewModel,
            LoggingListener loggingListener, String freqName, BluetoothDevice targetBtDevice) {
        OobBleServer oobBleServer =
                new OobBleServer(context, bleConnectionPeripheralViewModel, targetBtDevice,
                        loggingListener);
        if (!oobBleServer.waitForSocketCreation()) {
            oobBleServer.close();
            return null;
        }
        return new OobResponderRangingConfig.Builder(
                new DeviceHandle.Builder(INITIATOR_DEVICE, oobBleServer).build())
            .build();
    }

    public static RangingPreference createResponderRangingPreference(
            Context context, BleConnectionPeripheralViewModel bleConnectionPeripheralViewModel,
            LoggingListener loggingListener, String rangingTechnologyName, String freqName,
            int duration, BluetoothDevice targetBtDevice) {
        RangingConfig responderRangingConfig = null;
        if (Technology.fromName(rangingTechnologyName).equals(Technology.OOB)) {
            responderRangingConfig =
                    createOobResponderConfig(context, bleConnectionPeripheralViewModel,
                            loggingListener, freqName, targetBtDevice);
        } else {
            responderRangingConfig =
                createRawResponderConfig(rangingTechnologyName, freqName, targetBtDevice);
        }
        if (responderRangingConfig == null) return null;
        SessionConfig sessionConfig = new SessionConfig.Builder()
                .setRangingMeasurementsLimit(duration)
                .build();
        RangingPreference rangingPreference = new RangingPreference.Builder(
                RangingPreference.DEVICE_ROLE_RESPONDER, responderRangingConfig)
                .setSessionConfig(sessionConfig)
                .build();
        return rangingPreference;
    }
}
