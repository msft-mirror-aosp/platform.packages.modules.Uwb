/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.ranging.tests.session;

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;
import static android.ranging.uwb.UwbRangingParams.CONFIG_MULTICAST_DS_TWR;
import static android.ranging.uwb.UwbRangingParams.DURATION_2_MS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.content.AttributionSource;
import android.content.Context;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.SessionHandle;
import android.ranging.raw.RawRangingDevice;
import android.ranging.raw.RawResponderRangingConfig;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager;
import com.android.server.ranging.session.BaseRangingSession;
import com.android.server.ranging.session.RangingSessionConfig;
import com.android.server.ranging.uwb.UwbAdapter;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

@RunWith(JUnit4.class)
@SmallTest
public class BaseRangingSessionTest {

    @Mock
    private AttributionSource mMockAttributionSource;
    @Mock
    private RangingInjector mMockRangingInjector;
    @Mock
    private Context mMockContext;
    @Mock
    private AlarmManager mMockAlarmManager;
    @Mock
    private SessionHandle mMockSessionHandle;
    @Mock
    private RangingServiceManager.SessionListener mMockListener;
    @Mock
    private RawRangingDevice mRangingDevice;

    @Mock
    private UwbAdapter mMockAdapter;

    private RangingSessionConfig mSessionConfig;
    private BaseRangingSession mBaseRangingSession;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockRangingInjector.getAnyNonPrivilegedAppInAttributionSource(any())).thenReturn(
                mMockAttributionSource);
        when(mMockRangingInjector.createAdapter(any(), any(), anyInt(), any())).thenReturn(
                mMockAdapter);
        when(mMockRangingInjector.getContext()).thenReturn(mMockContext);
        when(mMockContext.getSystemService(AlarmManager.class)).thenReturn(mMockAlarmManager);
        when(mMockAdapter.isDynamicUpdatePeersSupported()).thenReturn(true);

        mSessionConfig = new RangingSessionConfig.Builder()
                .setDeviceRole(DEVICE_ROLE_INITIATOR)
                .setSessionConfig(new SessionConfig.Builder().build())
                .build();
        mRangingDevice = new RawRangingDevice.Builder()
                .setRangingDevice(new RangingDevice.Builder().build())
                .setUwbRangingParams(
                        new UwbRangingParams.Builder(10,
                                CONFIG_MULTICAST_DS_TWR,
                                UwbAddress.createRandomShortAddress(),
                                UwbAddress.fromBytes(UwbAddress
                                        .createRandomShortAddress().getAddressBytes()))
                                .setComplexChannel(
                                        new UwbComplexChannel.Builder().setChannel(
                                                9).setPreambleIndex(11).build())
                                .setSessionKeyInfo(
                                        new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3,
                                                2, 1})
                                .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                                .setSlotDuration(DURATION_2_MS)
                                .build()
                ).build();

        mBaseRangingSession = new BaseRangingSession(
                mMockAttributionSource,
                mMockSessionHandle,
                mMockRangingInjector,
                mSessionConfig,
                mMockListener,
                MoreExecutors.newDirectExecutorService()
        );
    }

    @Test
    public void testStartStopBaseRangingSession() {
        mBaseRangingSession.start(mSessionConfig.getTechnologyConfigs(Set.of(mRangingDevice)));
        verify(mMockAdapter, times(1)).start(any(), any(), any());

        mBaseRangingSession.stop();
        verify(mMockAdapter, times(1)).stop();
    }

    @Test
    public void testDynamicAddRemovePeers() {
        RawResponderRangingConfig responderRangingConfig = mock(RawResponderRangingConfig.class);
        when(responderRangingConfig.getRawRangingDevice()).thenReturn(mRangingDevice);
        mBaseRangingSession.start(mSessionConfig.getTechnologyConfigs(Set.of(mRangingDevice)));
        verify(mMockAdapter, times(1)).start(any(), any(), any());

        mBaseRangingSession.addPeer(responderRangingConfig);
        verify(mMockAdapter, times(1)).addPeer(any());

        RangingDevice rangingDevice = new RangingDevice.Builder().build();
        mBaseRangingSession.removePeer(rangingDevice);
        verify(mMockAdapter, times(1)).removePeer(rangingDevice);

        mBaseRangingSession.stop();
        verify(mMockAdapter, times(1)).stop();
    }

    @Test
    public void testAppInFgBg() {
        mBaseRangingSession.start(mSessionConfig.getTechnologyConfigs(Set.of(mRangingDevice)));
        verify(mMockAdapter, times(1)).start(any(), any(), any());

        mBaseRangingSession.appForegroundStateUpdated(true);
        verify(mMockAdapter, times(1)).appMovedToForeground();

        mBaseRangingSession.appForegroundStateUpdated(false);
        verify(mMockAdapter, times(1)).appMovedToBackground();

        mBaseRangingSession.appInBackgroundTimeout();
        verify(mMockAdapter, times(1)).appInBackgroundTimeout();

        mBaseRangingSession.stop();
        verify(mMockAdapter, times(1)).stop();
    }

}
