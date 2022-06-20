/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.uwb.discovery.ble;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.AttributionSource;
import android.content.Context;
import android.uwb.UwbTestUtils;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.discovery.TransportServerProvider.TransportServerCallback;
import com.android.server.uwb.discovery.info.FiraConnectorCapabilities;
import com.android.server.uwb.discovery.info.FiraConnectorDataPacket;
import com.android.server.uwb.discovery.info.FiraConnectorMessage;
import com.android.server.uwb.discovery.info.FiraConnectorMessage.InstructionCode;
import com.android.server.uwb.discovery.info.FiraConnectorMessage.MessageType;
import com.android.server.uwb.discovery.info.SecureComponentInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.concurrent.Executor;

/** Unit test for {@link GattTransportServerProvider} */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class GattTransportServerProviderTest {

    private static final Executor EXECUTOR = UwbTestUtils.getExecutor();
    private static final int SECID = 10;
    private static final byte[] MESSAGE_PAYLOAD1 = new byte[] {(byte) 0xF4, 0x00, 0x40};
    private static final FiraConnectorMessage MESSAGE =
            new FiraConnectorMessage(
                    MessageType.EVENT, InstructionCode.DATA_EXCHANGE, MESSAGE_PAYLOAD1);
    private static final FiraConnectorDataPacket DATA_PACKET =
            new FiraConnectorDataPacket(/*lastChainingPacket=*/ true, SECID, MESSAGE.toBytes());
    private static final int OPTIMIZED_DATA_PACKET_SIZE = 21;
    private static final FiraConnectorCapabilities CAPABILITIES =
            new FiraConnectorCapabilities.Builder()
                    .setOptimizedDataPacketSize(OPTIMIZED_DATA_PACKET_SIZE)
                    .setMaxMessageBufferSize(265)
                    .addSecureComponentInfo(
                            new SecureComponentInfo(
                                    /*static_indication=*/ true,
                                    SECID,
                                    SecureComponentInfo.SecureComponentType.ESE_NONREMOVABLE,
                                    SecureComponentInfo.SecureComponentProtocolType
                                            .FIRA_OOB_ADMINISTRATIVE_PROTOCOL))
                    .build();
    private static final BluetoothGattCharacteristic IN_CHARACTERSTIC =
            new BluetoothGattCharacteristic(
                    UuidConstants.CP_IN_CONTROL_POINT_UUID.getUuid(),
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE);
    private static final BluetoothGattCharacteristic OUT_CHARACTERSTIC =
            new BluetoothGattCharacteristic(
                    UuidConstants.CP_OUT_CONTROL_POINT_UUID.getUuid(),
                    BluetoothGattCharacteristic.PROPERTY_READ
                            | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ);
    private static final BluetoothGattCharacteristic CAPABILITIES_CHARACTERSTIC =
            new BluetoothGattCharacteristic(
                    UuidConstants.CP_FIRA_CONNECTOR_CAPABILITIES_UUID.getUuid(),
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE);
    private static final BluetoothGattDescriptor CCCD_DESCRIPTOR =
            new BluetoothGattDescriptor(
                    UuidConstants.CCCD_UUID.getUuid(), BluetoothGattDescriptor.PERMISSION_READ);

    @Mock AttributionSource mMockAttributionSource;
    @Mock Context mMockContext;
    @Mock BluetoothManager mMockBluetoothManager;
    @Mock BluetoothAdapter mMockBluetoothAdapter;
    @Mock BluetoothGattServer mMockBluetoothGattServer;
    @Mock TransportServerCallback mMockTransportServerCallback;
    @Mock BluetoothDevice mMockBluetoothDevice;

    private GattTransportServerProvider mGattTransportServerProvider;
    private BluetoothGattServerCallback mBluetoothGattServerCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.createContext(any())).thenReturn(mMockContext);
        when(mMockContext.getSystemService(BluetoothManager.class))
                .thenReturn(mMockBluetoothManager);
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothManager.openGattServer(eq(mMockContext), any()))
                .thenReturn(mMockBluetoothGattServer);
        when(mMockBluetoothGattServer.addService(any())).thenReturn(true);
        when(mMockBluetoothGattServer.removeService(any())).thenReturn(true);

        mGattTransportServerProvider =
                new GattTransportServerProvider(
                        mMockAttributionSource, mMockContext, mMockTransportServerCallback);

        ArgumentCaptor<BluetoothGattServerCallback> captor =
                ArgumentCaptor.forClass(BluetoothGattServerCallback.class);
        verify(mMockBluetoothManager, times(1)).openGattServer(eq(mMockContext), captor.capture());
        mBluetoothGattServerCallback = captor.getValue();
        assertThat(mBluetoothGattServerCallback).isNotNull();
    }

    @Test
    public void testStartAndStop() {
        assertThat(mGattTransportServerProvider.start()).isTrue();
        verify(mMockBluetoothGattServer, times(1)).addService(any());
        assertThat(mGattTransportServerProvider.isStarted()).isTrue();
        assertThat(mGattTransportServerProvider.stop()).isTrue();
        verify(mMockBluetoothGattServer, times(1)).removeService(any());
        assertThat(mGattTransportServerProvider.isStarted()).isFalse();
    }

    @Test
    public void testStartProcessing_succeed() {
        mBluetoothGattServerCallback.onConnectionStateChange(
                mMockBluetoothDevice, /*status=*/ 1, BluetoothProfile.STATE_CONNECTED);
        mBluetoothGattServerCallback.onDescriptorWriteRequest(
                mMockBluetoothDevice,
                /*requestId=*/ 1,
                CCCD_DESCRIPTOR,
                /*preparedWrite=*/ false,
                /*responseNeeded=*/ true,
                /*offset=*/ 0,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGattServerCallback.onCharacteristicWriteRequest(
                mMockBluetoothDevice,
                /*requestId=*/ 2,
                CAPABILITIES_CHARACTERSTIC,
                /*preparedWrite=*/ false,
                /*responseNeeded=*/ true,
                /*offset=*/ 0,
                CAPABILITIES.toBytes());

        verify(mMockBluetoothGattServer, times(1))
                .sendResponse(
                        mMockBluetoothDevice,
                        /*requestId=*/ 1,
                        BluetoothGatt.GATT_SUCCESS,
                        /*offset=*/ 0,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        verify(mMockBluetoothGattServer, times(1))
                .sendResponse(
                        mMockBluetoothDevice,
                        /*requestId=*/ 2,
                        BluetoothGatt.GATT_SUCCESS,
                        /*offset=*/ 0,
                        CAPABILITIES.toBytes());
        ArgumentCaptor<FiraConnectorCapabilities> captor =
                ArgumentCaptor.forClass(FiraConnectorCapabilities.class);
        verify(mMockTransportServerCallback, times(1)).onCapabilitesUpdated(captor.capture());
        assertThat(captor.getValue().toString()).isEqualTo(CAPABILITIES.toString());
        verify(mMockTransportServerCallback, times(1)).onProcessingStarted();
        verify(mMockTransportServerCallback, never()).onProcessingStopped();
    }

    private void startProcessing() {
        mBluetoothGattServerCallback.onConnectionStateChange(
                mMockBluetoothDevice, /*status=*/ 1, BluetoothProfile.STATE_CONNECTED);
        mBluetoothGattServerCallback.onDescriptorWriteRequest(
                mMockBluetoothDevice,
                /*requestId=*/ 1,
                CCCD_DESCRIPTOR,
                /*preparedWrite=*/ false,
                /*responseNeeded=*/ false,
                /*offset=*/ 0,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGattServerCallback.onCharacteristicWriteRequest(
                mMockBluetoothDevice,
                /*requestId=*/ 2,
                CAPABILITIES_CHARACTERSTIC,
                /*preparedWrite=*/ false,
                /*responseNeeded=*/ false,
                /*offset=*/ 0,
                CAPABILITIES.toBytes());
    }

    @Test
    public void testSendMessage_succeed() {
        when(mMockBluetoothGattServer.notifyCharacteristicChanged(
                        eq(mMockBluetoothDevice), any(), eq(false)))
                .thenReturn(true);

        startProcessing();

        assertThat(mGattTransportServerProvider.sendMessage(SECID, MESSAGE)).isTrue();
    }

    @Test
    public void testSendMessage_failedProcessingNotStarted() {
        when(mMockBluetoothGattServer.notifyCharacteristicChanged(
                        eq(mMockBluetoothDevice), any(), eq(false)))
                .thenReturn(true);

        assertThat(mGattTransportServerProvider.sendMessage(SECID, MESSAGE)).isFalse();
    }

    @Test
    public void testSendMessage_failedMessageLengthGreaterThanCapabilitites() {
        when(mMockBluetoothGattServer.notifyCharacteristicChanged(
                        eq(mMockBluetoothDevice), any(), eq(false)))
                .thenReturn(true);
        byte[] bytes = new byte[270];
        Arrays.fill(bytes, (byte) 1);
        FiraConnectorMessage message =
                new FiraConnectorMessage(MessageType.EVENT, InstructionCode.DATA_EXCHANGE, bytes);

        startProcessing();

        // Capabilities set the max message length to 265, so a message of length 270 exceeded the
        // limit.
        assertThat(mGattTransportServerProvider.sendMessage(SECID, message)).isFalse();
    }

    @Test
    public void testSendMessage_failedNotifyCharacteristicChangedFailed() {
        when(mMockBluetoothGattServer.notifyCharacteristicChanged(
                        eq(mMockBluetoothDevice), any(), eq(false)))
                .thenReturn(false);

        startProcessing();

        assertThat(mGattTransportServerProvider.sendMessage(SECID, MESSAGE)).isFalse();
    }

    private void setupOutCharactersticRead() {
        Answer notifyOutCharacteristicChangedResponse =
                new Answer() {
                    public Object answer(InvocationOnMock invocation) {
                        EXECUTOR.execute(
                                () ->
                                        mBluetoothGattServerCallback.onCharacteristicReadRequest(
                                                mMockBluetoothDevice,
                                                /*requestId=*/ 3,
                                                /*offset=*/ 0,
                                                OUT_CHARACTERSTIC));
                        return true;
                    }
                };
        doAnswer(notifyOutCharacteristicChangedResponse)
                .when(mMockBluetoothGattServer)
                .notifyCharacteristicChanged(eq(mMockBluetoothDevice), any(), eq(false));
    }

    @Test
    public void testSendMessageAndOutCharactersticRead_succeed() {
        setupOutCharactersticRead();
        startProcessing();

        assertThat(mGattTransportServerProvider.sendMessage(SECID, MESSAGE)).isTrue();
        verify(mMockBluetoothGattServer, times(1))
                .sendResponse(
                        mMockBluetoothDevice,
                        /*requestId=*/ 3,
                        BluetoothGatt.GATT_SUCCESS,
                        /*offset=*/ 0,
                        DATA_PACKET.toBytes());
    }

    @Test
    public void testSendMessageAndOutCharactersticRead_threeReadSucceed() {
        byte[] messagePayload = new byte[45];
        Arrays.fill(messagePayload, (byte) 2);
        FiraConnectorMessage message =
                new FiraConnectorMessage(
                        MessageType.EVENT, InstructionCode.DATA_EXCHANGE, messagePayload);
        byte[] messageBytes = message.toBytes();
        int payloadSize = OPTIMIZED_DATA_PACKET_SIZE - 1;
        FiraConnectorDataPacket dataPacket1 =
                new FiraConnectorDataPacket(
                        /*lastChainingPacket=*/ false,
                        SECID,
                        Arrays.copyOf(messageBytes, payloadSize));
        FiraConnectorDataPacket dataPacket2 =
                new FiraConnectorDataPacket(
                        /*lastChainingPacket=*/ false,
                        SECID,
                        Arrays.copyOfRange(messageBytes, payloadSize, 2 * payloadSize));
        FiraConnectorDataPacket dataPacket3 =
                new FiraConnectorDataPacket(
                        /*lastChainingPacket=*/ true,
                        SECID,
                        Arrays.copyOfRange(messageBytes, 2 * payloadSize, messageBytes.length));

        setupOutCharactersticRead();
        startProcessing();

        assertThat(mGattTransportServerProvider.sendMessage(SECID, message)).isTrue();
        verify(mMockBluetoothGattServer, times(3))
                .sendResponse(
                        eq(mMockBluetoothDevice),
                        eq(/*requestId=*/ 3),
                        eq(BluetoothGatt.GATT_SUCCESS),
                        eq(/*offset=*/ 0),
                        any());
        verify(mMockBluetoothGattServer, times(1))
                .sendResponse(
                        mMockBluetoothDevice,
                        /*requestId=*/ 3,
                        BluetoothGatt.GATT_SUCCESS,
                        /*offset=*/ 0,
                        dataPacket1.toBytes());
        verify(mMockBluetoothGattServer, times(1))
                .sendResponse(
                        mMockBluetoothDevice,
                        /*requestId=*/ 3,
                        BluetoothGatt.GATT_SUCCESS,
                        /*offset=*/ 0,
                        dataPacket2.toBytes());
        verify(mMockBluetoothGattServer, times(1))
                .sendResponse(
                        mMockBluetoothDevice,
                        /*requestId=*/ 3,
                        BluetoothGatt.GATT_SUCCESS,
                        /*offset=*/ 0,
                        dataPacket3.toBytes());
    }

    @Test
    public void testInCharactersticWrite_failedProcessingNotStarted() {
        mBluetoothGattServerCallback.onCharacteristicWriteRequest(
                mMockBluetoothDevice,
                /*requestId=*/ 4,
                IN_CHARACTERSTIC,
                /*preparedWrite=*/ false,
                /*responseNeeded=*/ true,
                /*offset=*/ 0,
                DATA_PACKET.toBytes());

        verify(mMockBluetoothGattServer, times(1))
                .sendResponse(
                        mMockBluetoothDevice,
                        /*requestId=*/ 4,
                        BluetoothGatt.GATT_FAILURE,
                        /*offset=*/ 0,
                        /*value=*/ null);
        verifyZeroInteractions(mMockTransportServerCallback);
    }

    @Test
    public void testInCharactersticWrite_failedEmptyDataPacket() {
        startProcessing();
        mBluetoothGattServerCallback.onCharacteristicWriteRequest(
                mMockBluetoothDevice,
                /*requestId=*/ 4,
                IN_CHARACTERSTIC,
                /*preparedWrite=*/ false,
                /*responseNeeded=*/ true,
                /*offset=*/ 0,
                new byte[] {});

        verify(mMockBluetoothGattServer, times(1))
                .sendResponse(
                        mMockBluetoothDevice,
                        /*requestId=*/ 4,
                        BluetoothGatt.GATT_FAILURE,
                        /*offset=*/ 0,
                        /*value=*/ null);
        verify(mMockTransportServerCallback, never()).onMessage(anyInt(), any());
    }

    @Test
    public void testInCharactersticWrite_noResponse() {
        startProcessing();
        mBluetoothGattServerCallback.onCharacteristicWriteRequest(
                mMockBluetoothDevice,
                /*requestId=*/ 4,
                IN_CHARACTERSTIC,
                /*preparedWrite=*/ false,
                /*responseNeeded=*/ false,
                /*offset=*/ 0,
                new byte[] {});

        verify(mMockBluetoothGattServer, never())
                .sendResponse(any(BluetoothDevice.class), anyInt(), anyInt(), anyInt(), any());
        verify(mMockTransportServerCallback, never()).onMessage(anyInt(), any());

        mBluetoothGattServerCallback.onCharacteristicWriteRequest(
                mMockBluetoothDevice,
                /*requestId=*/ 4,
                IN_CHARACTERSTIC,
                /*preparedWrite=*/ false,
                /*responseNeeded=*/ false,
                /*offset=*/ 0,
                DATA_PACKET.toBytes());

        verify(mMockBluetoothGattServer, never())
                .sendResponse(any(BluetoothDevice.class), anyInt(), anyInt(), anyInt(), any());
        ArgumentCaptor<FiraConnectorMessage> captor =
                ArgumentCaptor.forClass(FiraConnectorMessage.class);
        verify(mMockTransportServerCallback, times(1)).onMessage(eq(SECID), captor.capture());
        assertThat(captor.getValue().toString()).isEqualTo(MESSAGE.toString());
    }

    @Test
    public void testInCharactersticWrite_succeed() {
        byte[] messagePayload = new byte[51];
        Arrays.fill(messagePayload, (byte) 3);
        FiraConnectorMessage message =
                new FiraConnectorMessage(
                        MessageType.EVENT, InstructionCode.DATA_EXCHANGE, messagePayload);
        byte[] messageBytes = message.toBytes();
        int payloadSize = OPTIMIZED_DATA_PACKET_SIZE - 1;
        FiraConnectorDataPacket dataPacket1 =
                new FiraConnectorDataPacket(
                        /*lastChainingPacket=*/ false,
                        SECID,
                        Arrays.copyOf(messageBytes, payloadSize));
        FiraConnectorDataPacket dataPacket2 =
                new FiraConnectorDataPacket(
                        /*lastChainingPacket=*/ false,
                        SECID,
                        Arrays.copyOfRange(messageBytes, payloadSize, 2 * payloadSize));
        FiraConnectorDataPacket dataPacket3 =
                new FiraConnectorDataPacket(
                        /*lastChainingPacket=*/ true,
                        SECID,
                        Arrays.copyOfRange(messageBytes, 2 * payloadSize, messageBytes.length));

        startProcessing();
        mBluetoothGattServerCallback.onCharacteristicWriteRequest(
                mMockBluetoothDevice,
                /*requestId=*/ 4,
                IN_CHARACTERSTIC,
                /*preparedWrite=*/ false,
                /*responseNeeded=*/ true,
                /*offset=*/ 0,
                dataPacket1.toBytes());
        mBluetoothGattServerCallback.onCharacteristicWriteRequest(
                mMockBluetoothDevice,
                /*requestId=*/ 4,
                IN_CHARACTERSTIC,
                /*preparedWrite=*/ false,
                /*responseNeeded=*/ true,
                /*offset=*/ 0,
                dataPacket2.toBytes());
        mBluetoothGattServerCallback.onCharacteristicWriteRequest(
                mMockBluetoothDevice,
                /*requestId=*/ 4,
                IN_CHARACTERSTIC,
                /*preparedWrite=*/ false,
                /*responseNeeded=*/ true,
                /*offset=*/ 0,
                dataPacket3.toBytes());

        verify(mMockBluetoothGattServer, times(3))
                .sendResponse(
                        eq(mMockBluetoothDevice),
                        eq(/*requestId=*/ 4),
                        eq(BluetoothGatt.GATT_SUCCESS),
                        eq(/*offset=*/ 0),
                        any());
        verify(mMockBluetoothGattServer, times(1))
                .sendResponse(
                        mMockBluetoothDevice,
                        /*requestId=*/ 4,
                        BluetoothGatt.GATT_SUCCESS,
                        /*offset=*/ 0,
                        dataPacket1.toBytes());
        verify(mMockBluetoothGattServer, times(1))
                .sendResponse(
                        mMockBluetoothDevice,
                        /*requestId=*/ 4,
                        BluetoothGatt.GATT_SUCCESS,
                        /*offset=*/ 0,
                        dataPacket2.toBytes());
        verify(mMockBluetoothGattServer, times(1))
                .sendResponse(
                        mMockBluetoothDevice,
                        /*requestId=*/ 4,
                        BluetoothGatt.GATT_SUCCESS,
                        /*offset=*/ 0,
                        dataPacket3.toBytes());
        ArgumentCaptor<FiraConnectorMessage> captor =
                ArgumentCaptor.forClass(FiraConnectorMessage.class);
        verify(mMockTransportServerCallback, times(1)).onMessage(eq(SECID), captor.capture());
        assertThat(captor.getValue().toString()).isEqualTo(message.toString());
    }
}
