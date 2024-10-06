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

package com.android.server.ranging.uwb;

import static java.nio.charset.StandardCharsets.US_ASCII;

import android.util.Log;

import androidx.annotation.NonNull;

import com.android.ranging.uwb.backend.internal.RangingParameters;
import com.android.ranging.uwb.backend.internal.Utils;
import com.android.ranging.uwb.backend.internal.UwbAddress;
import com.android.ranging.uwb.backend.internal.UwbComplexChannel;
import com.android.server.ranging.RangingAdapter.TechnologyConfig;
import com.android.server.ranging.RangingParameters.DeviceRole;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.Conversions;

import com.google.common.collect.ImmutableList;
import com.google.uwb.support.base.RequiredParam;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Configuration for UWB sent as part SetConfigurationMessage for Finder OOB. */
public class UwbConfig implements TechnologyConfig {
    private static final String TAG = UwbConfig.class.getSimpleName();

    private static final int MIN_SIZE_BYTES = 20;

    // Size in bytes for properties when serialized.
    private static final int TECHNOLOGY_ID_SIZE = 1;
    private static final int UWB_ADDRESS_SIZE = 2;
    private static final int SESSION_ID_SIZE = 4;
    private static final int CONFIG_ID_SIZE = 1;
    private static final int CHANNEL_SIZE = 1;
    private static final int PREAMBLE_INDEX_SIZE = 1;
    private static final int RANGING_INTERVAL_SIZE = 4;
    private static final int SLOT_DURATION_SIZE = 1;
    private static final int SESSION_KEY_LENGTH_SIZE = 1;
    private static final int STS_SESSION_KEY_SIZE = 8;
    private static final int PSTS_SHORT_SESSION_KEY_SIZE = 16;
    private static final int PSTS_LONG_SESSION_KEY_SIZE = 32;
    private static final int COUNTRY_CODE_SIZE = 2;
    private static final int DEVICE_ROLE_SIZE = 1;
    private static final int DEVICE_TYPE_SIZE = 1;

    /** Returns the size of the object in bytes when serialized. */
    public final int getSize() {
        return MIN_SIZE_BYTES + getSessionKeyInfoLength();
    }

    private final DeviceRole mDeviceRole;
    private final UwbParameters mParameters;

    private UwbConfig(Builder builder) {
        mDeviceRole = builder.mDeviceRole.get();
        mParameters = builder.mParameters.get();
    }

    /**
     * Parses the given byte array and returns {@link UwbConfig} object. Throws {@link
     * IllegalArgumentException} on invalid input.
     */
    public static UwbConfig parseBytes(byte[] uwbConfigBytes) {
        if (uwbConfigBytes.length < MIN_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Failed to parse UwbConfig, invalid size. Bytes: " + Arrays.toString(
                            uwbConfigBytes));
        }

        // Parse Ranging Technology Id
        int parseCursor = 0;
        ImmutableList<RangingTechnology> technology =
                RangingTechnology.parseByte(uwbConfigBytes[parseCursor]);
        if (technology.size() != 1 || technology.get(0) != RangingTechnology.UWB) {
            throw new IllegalArgumentException("Couldn't parse UwbConfig, invalid technology id");
        }
        parseCursor += TECHNOLOGY_ID_SIZE;

        // Parse Uwb Address
        UwbAddress uwbAddress = UwbAddress.fromBytes(
                Arrays.copyOfRange(uwbConfigBytes, parseCursor, parseCursor + UWB_ADDRESS_SIZE));
        parseCursor += UWB_ADDRESS_SIZE;

        // Parse Session Id
        int sessionId =
                Conversions.byteArrayToInt(
                        Arrays.copyOfRange(uwbConfigBytes, parseCursor,
                                parseCursor + SESSION_ID_SIZE));
        parseCursor += SESSION_ID_SIZE;

        // Parse Config Id
        int configId = uwbConfigBytes[parseCursor];
        parseCursor += CONFIG_ID_SIZE;

        // Parse Channel
        int channel = uwbConfigBytes[parseCursor];
        parseCursor += CHANNEL_SIZE;

        // Parse Preamble Index
        int preambleIndex = uwbConfigBytes[parseCursor];
        parseCursor += PREAMBLE_INDEX_SIZE;

        // Parse Ranging Interval Ms
        int rangingIntervalMs =
                Conversions.byteArrayToInt(
                        Arrays.copyOfRange(uwbConfigBytes, parseCursor,
                                parseCursor + RANGING_INTERVAL_SIZE));
        parseCursor += RANGING_INTERVAL_SIZE;

        // Parse Slot Duration
        int slotDurationMs = uwbConfigBytes[parseCursor];
        parseCursor += SLOT_DURATION_SIZE;

        // Parse Session Key
        int sessionKeyLength = uwbConfigBytes[parseCursor];
        parseCursor += SESSION_KEY_LENGTH_SIZE;

        if (uwbConfigBytes.length < MIN_SIZE_BYTES + sessionKeyLength) {
            throw new IllegalArgumentException(
                    "Failed to parse UwbConfig, invalid size. Bytes: " + Arrays.toString(
                            uwbConfigBytes));
        }
        byte[] sessionKey = Arrays.copyOfRange(uwbConfigBytes, parseCursor, sessionKeyLength);
        parseCursor += sessionKeyLength;

        // Parse Country Code
        String countryCode =
                new String(
                        Arrays.copyOfRange(uwbConfigBytes, parseCursor,
                                parseCursor + COUNTRY_CODE_SIZE));
        parseCursor += COUNTRY_CODE_SIZE;

        // Parse Device Role
        DeviceRole deviceRole = Conversions.fromOobDeviceRole(uwbConfigBytes[parseCursor]);
        parseCursor += DEVICE_ROLE_SIZE;

        // Parse Device Type
        int deviceType = uwbConfigBytes[parseCursor];
        parseCursor += DEVICE_TYPE_SIZE;

        if (deviceRole != Conversions.fromOobDeviceType(deviceType)) {
            Log.e(TAG, "Parsed UWB device type (" + deviceType + ")" + " that contradicts role ("
                    + deviceRole.toString() + "). Ignoring type.");
        }

        UwbParameters.Builder paramsBuilder = new UwbParameters.Builder()
                .setLocalAddress(uwbAddress)
                .setSessionId(sessionId)
                .setConfigType(configId)
                .setComplexChannel(new UwbComplexChannel(channel, preambleIndex))
                .setSlotDurationMs(slotDurationMs)
                .setSessionKeyInfo(sessionKey)
                .setCountryCode(countryCode);


        for (@Utils.RangingUpdateRate int rate = Utils.NORMAL; rate <= Utils.FAST; rate++) {
            if (Utils.getRangingTimingParams(configId).getRangingInterval(rate)
                    == rangingIntervalMs) {
                paramsBuilder.setUpdateRateType(rate);
            }
        }

        return new Builder().setDeviceRole(deviceRole).setParameters(paramsBuilder.build()).build();
    }

    /** Serializes this {@link UwbConfig} object to bytes. */
    public final byte[] toBytes() {
        int size = MIN_SIZE_BYTES + getSessionKeyInfoLength();
        return ByteBuffer.allocate(size)
                .put(RangingTechnology.UWB.toByte())
                .put(mParameters.getLocalAddress().toBytes())
                .put(Conversions.intToByteArray(mParameters.getSessionId(), SESSION_ID_SIZE))
                .put(Conversions.intToByteArray(mParameters.getConfigType(), CONFIG_ID_SIZE))
                .put(Conversions.intToByteArray(
                        mParameters.getComplexChannel().getChannel(), CHANNEL_SIZE))
                .put(Conversions.intToByteArray(
                        mParameters.getComplexChannel().getPreambleIndex(), PREAMBLE_INDEX_SIZE))
                .put(Conversions.intToByteArray(
                        Utils.getRangingTimingParams(mParameters.getConfigType())
                                .getRangingInterval(mParameters.getUpdateRateType()),
                        RANGING_INTERVAL_SIZE))
                .put(Conversions.intToByteArray(
                        mParameters.getSlotDurationMs(), SLOT_DURATION_SIZE))
                .put(Conversions.intToByteArray(getSessionKeyInfoLength(), SESSION_KEY_LENGTH_SIZE))
                .put(mParameters.getSessionKeyInfo())
                .put(mParameters.getCountryCode().getBytes(US_ASCII))
                .put(Conversions.intToByteArray(
                        Conversions.toOobDeviceRole(getDeviceRole()), DEVICE_ROLE_SIZE))
                .put(Conversions.intToByteArray(
                        Conversions.toOobDeviceType(getDeviceRole()), DEVICE_TYPE_SIZE))
                .array();
    }

    /** Returns the length of the session key. */
    public final int getSessionKeyInfoLength() {
        if (mParameters.getSessionKeyInfo() == null) {
            return 0;
        } else {
            return mParameters.getSessionKeyInfo().length;
        }
    }

    /** Returns the device's role within the session. */
    public DeviceRole getDeviceRole() {
        return mDeviceRole;
    }

    /** Returns the UWB-specific ranging parameters. */
    public UwbParameters getParameters() {
        return mParameters;
    }

    /**
     * @return the configuration converted to a
     * {@link androidx.core.uwb.backend.impl.internal.RangingParameters} accepted by the UWB
     * backend.
     */
    public RangingParameters asBackendParameters() {
        return new RangingParameters(
                mParameters.getConfigType(), mParameters.getSessionId(),
                mParameters.getSubSessionId(), mParameters.getSessionKeyInfo(),
                mParameters.getSubSessionKeyInfo(), mParameters.getComplexChannel(),
                mParameters.getPeerAddresses().asList(), mParameters.getUpdateRateType(),
                mParameters.getRangeDataNtfConfig(), mParameters.getSlotDurationMs(),
                mParameters.isAoaDisabled()
        );
    }

    /** Builder for {@link UwbConfig}. */
    public static class Builder {
        private final RequiredParam<DeviceRole> mDeviceRole = new RequiredParam<>();
        private final RequiredParam<UwbParameters> mParameters = new RequiredParam<>();

        public @NonNull UwbConfig build() {
            return new UwbConfig(this);
        }

        public Builder setDeviceRole(@NonNull DeviceRole role) {
            mDeviceRole.set(role);
            return this;
        }

        public Builder setParameters(@NonNull UwbParameters parameters) {
            mParameters.set(parameters);
            return this;
        }
    }
}
