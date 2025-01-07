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

import android.content.Context;
import android.content.SharedPreferences;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbRangingParams;

/** Utility class to hold ranging params shared across peer devices */
public class ConfigurationParameters {
    private static String PREF_CONFIG = "PrefConfig";

    public Uwb uwb;
    public BleCs bleCs;
    public BleRssi bleRssi;
    public WifiNanRtt wifiNanRtt;
    public Oob oob;

    private ConfigurationParameters(Uwb uwb, BleCs bleCs, BleRssi bleRssi,
            WifiNanRtt wifiNanRtt, Oob oob) {
        this.uwb = uwb;
        this.bleCs = bleCs;
        this.bleRssi = bleRssi;
        this.wifiNanRtt = wifiNanRtt;
        this.oob = oob;
    }

    private ConfigurationParameters(boolean isResponder) {
        this.uwb = new Uwb(isResponder);
        bleCs = new BleCs();
        bleRssi = new BleRssi();
        wifiNanRtt = new WifiNanRtt();
        oob = new Oob();
    }

    public void saveInstance(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_CONFIG, Context.MODE_PRIVATE);
        SharedPreferences.Editor prefEditor = pref.edit();
        uwb.toPref(prefEditor);
        bleCs.toPref(prefEditor);
        bleRssi.toPref(prefEditor);
        wifiNanRtt.toPref(prefEditor);
        oob.toPref(prefEditor);
        prefEditor.apply();
    }

    public static ConfigurationParameters resetInstance(Context context, boolean isResponder) {
        SharedPreferences pref = context.getSharedPreferences(PREF_CONFIG, Context.MODE_PRIVATE);
        SharedPreferences.Editor prefEditor = pref.edit();
        prefEditor.clear();
        prefEditor.apply();
        return new ConfigurationParameters(isResponder);
    }

    public static ConfigurationParameters restoreInstance(Context context, boolean isResponder) {
        SharedPreferences pref = context.getSharedPreferences(PREF_CONFIG, Context.MODE_PRIVATE);
        return new ConfigurationParameters(
                Uwb.fromPref(pref, isResponder),
                BleCs.fromPref(pref, isResponder),
                BleRssi.fromPref(pref, isResponder),
                WifiNanRtt.fromPref(pref, isResponder),
                Oob.fromPref(pref, isResponder));
    }

    public static class BaseTechConfig {
        public RangingParameters.Technology technology;

        public BaseTechConfig(RangingParameters.Technology technology) {
            this.technology = technology;
        }
    }

    public static class Uwb extends BaseTechConfig {
        private static final UwbAddress[] UWB_ADDRESSES = new UwbAddress[] {
                UwbAddress.fromBytes(new byte[]{0x5, 0x6}),
                UwbAddress.fromBytes(new byte[]{0x6, 0x5})
        };
        public boolean isResponder;
        public int channel = UWB_CHANNEL_9;
        public int preamble = UWB_PREAMBLE_CODE_INDEX_11;
        public int configId = UwbRangingParams.CONFIG_PROVISIONED_MULTICAST_DS_TWR;
        public int sessionId = 5;
        public UwbAddress deviceAddress;
        public UwbAddress peerDeviceAddress;
        public byte[] sessionKey = new byte[] {
                0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8,
                0x8, 0x7, 0x6, 0x5, 0x4, 0x3, 0x2, 0x1
        };

        public Uwb(boolean isResponder, UwbAddress deviceAddress, UwbAddress peerDeviceAddress) {
            super(RangingParameters.Technology.UWB);
            this.isResponder = isResponder;
            this.deviceAddress = deviceAddress;
            this.peerDeviceAddress = peerDeviceAddress;
        }

        public Uwb(boolean isResponder) {
            this(
                    isResponder,
                    isResponder ? UWB_ADDRESSES[0] : UWB_ADDRESSES[1],
                    isResponder ? UWB_ADDRESSES[1] : UWB_ADDRESSES[0]
            );
        }

        public void toPref(SharedPreferences.Editor prefEditor) {
            prefEditor.putBoolean("isResponder", isResponder);
            prefEditor.putInt("channel", channel);
            prefEditor.putInt("preamble", preamble);
            prefEditor.putInt("configId", configId);
            prefEditor.putInt("sessionId", sessionId);
            prefEditor.putString("deviceAddress", new String(deviceAddress.getAddressBytes()));
            prefEditor.putString("peerDeviceAddress", new String(peerDeviceAddress.getAddressBytes()));
        }

        public static Uwb fromPref(SharedPreferences pref, boolean isResponder) {
            Boolean isPrefResponder = null;
            if (pref.contains("isResponder")) {
                isPrefResponder = pref.getBoolean("isResponder", false);
            }
            Uwb uwb = null;
            if (isPrefResponder == null || isPrefResponder != isResponder) {
                uwb = new Uwb(isResponder);
            } else {
                uwb = new Uwb(
                        isResponder,
                        UwbAddress.fromBytes(pref.getString(
                                "devicAddress",
                                new String(UWB_ADDRESSES[0].getAddressBytes())).getBytes()),
                        UwbAddress.fromBytes(pref.getString(
                                "devicAddress",
                                new String(UWB_ADDRESSES[1].getAddressBytes())).getBytes()));

            }
            uwb.channel = pref.getInt("channel", UWB_CHANNEL_9);
            uwb.preamble = pref.getInt("preamble", UWB_PREAMBLE_CODE_INDEX_11);
            uwb.configId =
                    pref.getInt("configId", UwbRangingParams.CONFIG_PROVISIONED_UNICAST_DS_TWR);
            uwb.sessionId = pref.getInt("sessionId", 5);
            return uwb;
        }
    }

    public static class BleCs extends BaseTechConfig {
        public BleCs() {
            super(RangingParameters.Technology.BLE_CS);
        }

        public void toPref(SharedPreferences.Editor prefEditor) {
        }

        public static BleCs fromPref(SharedPreferences pref, boolean isResponder) {
            return new BleCs();
        }
    }

    public static class BleRssi extends BaseTechConfig {
        public BleRssi() {
            super(RangingParameters.Technology.BLE_CS);
        }

        public void toPref(SharedPreferences.Editor prefEditor) {
        }

        public static BleRssi fromPref(SharedPreferences pref, boolean isResponder) {
            return new BleRssi();
        }
    }

    public static class WifiNanRtt extends BaseTechConfig {
        public String serviceName = "ranging_service";
        public WifiNanRtt() {
            super(RangingParameters.Technology.WIFI_NAN_RTT);
        }

        public void toPref(SharedPreferences.Editor prefEditor) {
            WifiNanRtt wifiNanRtt = new WifiNanRtt();
            prefEditor.putString("serviceName", wifiNanRtt.serviceName);
        }

        public static WifiNanRtt fromPref(SharedPreferences pref, boolean isResponder) {
            WifiNanRtt wifiNanRtt = new WifiNanRtt();
            wifiNanRtt.serviceName = pref.getString("serviceName", "ranging_service");
            return wifiNanRtt;
        }
    }

    public static class Oob extends BaseTechConfig {
        public Oob() {
            super(RangingParameters.Technology.OOB);
        }

        public void toPref(SharedPreferences.Editor prefEditor) {
        }

        public static Oob fromPref(SharedPreferences pref, boolean isResponder) {
            return new Oob();
        }
    }
}
