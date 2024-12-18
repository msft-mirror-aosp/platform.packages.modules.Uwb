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

package com.android.server.ranging.oob;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.uwb.UwbOobConfig;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.annotation.Nullable;

/** The Set Configuration Message Additional Data for Finder OOB. */
@AutoValue
public abstract class SetConfigurationMessage {

    // Size in bytes of properties when serialized.
    private static final int MIN_SIZE_BYTES = 4;
    private static final int RANGING_TECHNOLOGIES_SET_SIZE = 2;
    private static final int START_RANGING_LIST_SIZE = 2;

    /**
     * Parses the given byte array and returns {@link SetConfigurationMessage} object. Throws {@link
     * IllegalArgumentException} on invalid input.
     */
    public static SetConfigurationMessage parseBytes(byte[] payload) {
        OobHeader header = OobHeader.parseBytes(payload);

        if (header.getMessageType() != MessageType.SET_CONFIGURATION) {
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid message type: %s, expected %s",
                            header.getMessageType(), MessageType.SET_CONFIGURATION));
        }

        if (payload.length < header.getSize() + MIN_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    String.format(
                            "CapabilityResponseMessage payload size is %d bytes", payload.length));
        }

        int parseCursor = header.getSize();

        // Parse Ranging Technologies Set
        var rangingTechnologiesSet =
                RangingTechnology.fromBitmap(Arrays.copyOfRange(
                        payload, parseCursor, parseCursor + RANGING_TECHNOLOGIES_SET_SIZE));
        parseCursor += RANGING_TECHNOLOGIES_SET_SIZE;

        // Parse Start Ranging List
        var startRangingList =
                RangingTechnology.fromBitmap(Arrays.copyOfRange(
                        payload, parseCursor, parseCursor + START_RANGING_LIST_SIZE));
        parseCursor += START_RANGING_LIST_SIZE;

        // Parse Configs for ranging technologies that are set
        UwbOobConfig uwbConfig = null;
        int countTechsParsed = 0;
        while (parseCursor < payload.length && countTechsParsed++ < rangingTechnologiesSet.size()) {
            byte[] remainingBytes = Arrays.copyOfRange(payload, parseCursor, payload.length);
            TechnologyHeader techHeader = TechnologyHeader.parseBytes(remainingBytes);
            switch (techHeader.getRangingTechnology()) {
                case UWB:
                    if (uwbConfig != null) {
                        throw new IllegalArgumentException(
                                "Failed to parse SetConfigurationMessage, UwbConfig already set. "
                                        + "Bytes: " + Arrays.toString(payload));
                    }
                    uwbConfig = UwbOobConfig.parseBytes(remainingBytes);
                    parseCursor += uwbConfig.getSize();
                    break;
                default:
                    parseCursor += techHeader.getSize();
            }
        }

        return builder()
                .setHeader(header)
                .setRangingTechnologiesSet(rangingTechnologiesSet)
                .setStartRangingList(startRangingList)
                .setUwbConfig(uwbConfig)
                .build();
    }

    /** Serializes this {@link SetConfigurationMessage} object to bytes. */
    public final byte[] toBytes() {
        int size = MIN_SIZE_BYTES + getHeader().getSize();
        UwbOobConfig uwbConfig = getUwbConfig();
        if (uwbConfig != null) {
            size += uwbConfig.getSize();
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(size);
        byteBuffer
                .put(getHeader().toBytes())
                .put(RangingTechnology.toBitmap(getRangingTechnologiesSet()))
                .put(RangingTechnology.toBitmap(getStartRangingList()));
        if (uwbConfig != null) {
            byteBuffer.put(uwbConfig.toBytes());
        }
        return byteBuffer.array();
    }

    /** Returns the OOB header. */
    public abstract OobHeader getHeader();

    /** Returns a list of ranging technologies that are set as part of this message. */
    public abstract ImmutableList<RangingTechnology> getRangingTechnologiesSet();

    /**
     * Returns a list of ranging technologies that should start ranging as soon as this message is
     * received.
     */
    public abstract ImmutableList<RangingTechnology> getStartRangingList();

    /** Returns @Nullable UwbConfig data that should be used to configure UWB ranging session. */
    @Nullable
    public abstract UwbOobConfig getUwbConfig();

    /** Returns a builder for {@link SetConfigurationMessage}. */
    public static Builder builder() {
        return new AutoValue_SetConfigurationMessage.Builder()
                .setRangingTechnologiesSet(ImmutableList.of())
                .setStartRangingList(ImmutableList.of());
    }

    /** Builder for {@link SetConfigurationMessage}. */
    @AutoValue.Builder
    public abstract static class Builder {

        public abstract Builder setHeader(OobHeader header);

        public abstract Builder setRangingTechnologiesSet(
                ImmutableList<RangingTechnology> rangingTechnologiesSet);

        public abstract Builder setStartRangingList(
                ImmutableList<RangingTechnology> startRangingList
        );

        public abstract Builder setUwbConfig(@Nullable UwbOobConfig uwbConfig);

        abstract SetConfigurationMessage autoBuild();

        public SetConfigurationMessage build() {
            SetConfigurationMessage setConfigurationMessage = autoBuild();
            Preconditions.checkArgument(
                    setConfigurationMessage
                            .getRangingTechnologiesSet()
                            .containsAll(setConfigurationMessage.getStartRangingList()),
                    "startRangingList contains items that are not in rangingTechnologiesSet list.");
            Preconditions.checkArgument(
                    setConfigurationMessage
                            .getRangingTechnologiesSet()
                            .contains(RangingTechnology.UWB)
                            == (setConfigurationMessage.getUwbConfig() != null),
                    "UwbConfig or rangingTechnologiesSet for UWB not set properly.");
            return setConfigurationMessage;
        }
    }
}
