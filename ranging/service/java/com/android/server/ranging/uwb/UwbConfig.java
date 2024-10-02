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

import com.android.ranging.uwb.backend.internal.UwbAddress;
import com.android.server.ranging.RangingParameters.DeviceRole;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.Conversions;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Configuration for UWB sent as part SetConfigurationMessage for Finder OOB. */
@AutoValue
public abstract class UwbConfig {
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
        return MIN_SIZE_BYTES + getSessionKeyLength();
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

        return builder()
                .setUwbAddress(uwbAddress)
                .setSessionId(sessionId)
                .setSelectedConfigId(configId)
                .setSelectedChannel(channel)
                .setSelectedPreambleIndex(preambleIndex)
                .setSelectedRangingIntervalMs(rangingIntervalMs)
                .setSelectedSlotDurationMs(slotDurationMs)
                .setSessionKey(sessionKey)
                .setCountryCode(countryCode)
                .setDeviceRole(deviceRole)
                .build();
    }

    /** Serializes this {@link UwbConfig} object to bytes. */
    public final byte[] toBytes() {
        int size = MIN_SIZE_BYTES + getSessionKeyLength();
        return ByteBuffer.allocate(size)
                .put(RangingTechnology.UWB.toByte())
                .put(getUwbAddress().toBytes())
                .put(Conversions.intToByteArray(getSessionId(), SESSION_ID_SIZE))
                .put(Conversions.intToByteArray(getSelectedConfigId(), CONFIG_ID_SIZE))
                .put(Conversions.intToByteArray(getSelectedChannel(), CHANNEL_SIZE))
                .put(Conversions.intToByteArray(getSelectedPreambleIndex(), PREAMBLE_INDEX_SIZE))
                .put(Conversions.intToByteArray(getSelectedRangingIntervalMs(),
                        RANGING_INTERVAL_SIZE))
                .put(Conversions.intToByteArray(getSelectedSlotDurationMs(), SLOT_DURATION_SIZE))
                .put(Conversions.intToByteArray(getSessionKeyLength(), SESSION_KEY_LENGTH_SIZE))
                .put(getSessionKey())
                .put(getCountryCode().getBytes(US_ASCII))
                .put(Conversions.intToByteArray(Conversions.toOobDeviceRole(getDeviceRole()),
                        DEVICE_ROLE_SIZE))
                .put(Conversions.intToByteArray(Conversions.toOobDeviceType(getDeviceRole()),
                        DEVICE_TYPE_SIZE))
                .array();
    }

    /** Returns {@link UwbAddress} of the device. */
    public abstract UwbAddress getUwbAddress();

    /** Returns the session Id. */
    public abstract int getSessionId();

    /** Returns the selected config Id. */
    public abstract int getSelectedConfigId();

    /** Returns the selected channel. */
    public abstract int getSelectedChannel();

    /** Returns the selected preamble index. */
    public abstract int getSelectedPreambleIndex();

    /** Returns the selected ranging interval in ms. */
    public abstract int getSelectedRangingIntervalMs();

    /** Returns the selected slot duration in ms. */
    public abstract int getSelectedSlotDurationMs();

    /** Returns the length of the session key. */
    public final int getSessionKeyLength() {
        return getSessionKey().length;
    }

    /**
     * Returns the session key bytes. If S-STS is used then first two bytes are VENDOR ID and
     * following 6 bytes are STATIC STS IV. If P-STS is used then this is either a 16 byte or 32
     * byte
     * session key.
     */
    @SuppressWarnings("mutable")
    public abstract byte[] getSessionKey();

    /** Returns ISO 3166-1 alpha-2 country code, represented by 2 ascii characters */
    public abstract String getCountryCode();

    /** Returns Device Role. */
    public abstract DeviceRole getDeviceRole();

    /** Returns a builder for {@link UwbConfig}. */
    public static Builder builder() {
        return new AutoValue_UwbConfig.Builder().setSessionKey(null);
    }

    /** Builder for {@link UwbConfig}. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setUwbAddress(UwbAddress uwbAddress);

        public abstract Builder setSessionId(int sessionId);

        public abstract Builder setSelectedConfigId(int selectedConfigId);

        public abstract Builder setSelectedChannel(int selectedChannel);

        public abstract Builder setSelectedPreambleIndex(int selectedPreambleIndex);

        public abstract Builder setSelectedRangingIntervalMs(int selectedRangingIntervalMs);

        public abstract Builder setSelectedSlotDurationMs(int selectedSlotDurationMs);

        public abstract Builder setSessionKey(byte[] sessionKey);

        public abstract Builder setCountryCode(String countryCode);

        public abstract Builder setDeviceRole(DeviceRole deviceRole);

        abstract UwbConfig autoBuild();

        public UwbConfig build() {
            UwbConfig uwbConfig = autoBuild();
            Preconditions.checkNotNull(uwbConfig.getUwbAddress(), "UwbAddress cannot be null");
            int sessionKeyLength = uwbConfig.getSessionKeyLength();
            Preconditions.checkArgument(
                    sessionKeyLength == STS_SESSION_KEY_SIZE
                            || sessionKeyLength == PSTS_SHORT_SESSION_KEY_SIZE
                            || sessionKeyLength == PSTS_LONG_SESSION_KEY_SIZE,
                    "Invalid session key length");
            Preconditions.checkArgument(
                    uwbConfig.getCountryCode().length() == COUNTRY_CODE_SIZE,
                    "Invalid country code length");
            return uwbConfig;
        }
    }
}
