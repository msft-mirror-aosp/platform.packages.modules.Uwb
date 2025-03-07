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

package com.android.ranging.rtt.backend;

import static com.android.ranging.rtt.backend.RttRangingSessionCallback.REASON_RTT_NOT_AVAILABLE;
import static com.android.ranging.rtt.backend.RttRangingSessionCallback.REASON_STOP_RANGING_CALLED;
import static com.android.ranging.rtt.backend.RttRangingSessionCallback.REASON_UNKNOWN;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.ranging.rtt.backend.RttRanger.RttRangerListener;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class for interacting with nearby RTT devices to perform ranging.
 */
public class RttRangingDevice {
    private static final String TAG = RttRangingDevice.class.getName();
    private static final int GRAPI_RTT_MESSAGE_ID = 1;
    private static final int MAX_RANGING_RESULT_ERROR_STREAK = 5;
    private final Handler mHandler;
    private final WifiAwareManager mWifiAwareManager;
    private WifiAwareSession mWifiAwareSession;
    private RttRangingSessionCallback mRttListener = null;
    private PublishDiscoverySession mCurrentPublishDiscoverySession = null;
    private SubscribeDiscoverySession mCurrentSubscribeDiscoverySession = null;
    private final Context mContext;
    private final DeviceType mDeviceType;
    private RttRangingParameters mRttRangingParameters = null;
    private PeerHandle mPeerHandle = null;
    private final RttDevice mRttDevice;
    private final RttRanger mRttRanger;
    private String mPeerName = null;
    private boolean mIsRunning;
    private final Object mLock = new Object();
    private final WifiRttManager mWifiRttManager;
    private int mRangingRequestDelay = 500;
    private AtomicInteger mResultErrorStreak;

    public RttRangingParameters getRttRangingParameters() {
        return mRttRangingParameters;
    }

    /** Listener for range results. */
    private RttRangerListener mRttRangingListener = new RttRangerListener() {
        @Override
        public void onRangingFailure(int code) {
            switch (code) {
                case STATUS_CODE_FAIL -> Log.w(TAG, "Failed to range");
                case STATUS_CODE_FAIL_RESULT_EMPTY -> Log.i(TAG, "Range results are empty");
                case STATUS_CODE_FAIL_RTT_NOT_AVAILABLE -> {
                    Log.w(TAG, "RTT Not Available");
                    synchronized (mLock) {
                        if (mRttListener != null) {
                            mRttListener.onRangingSuspended(mRttDevice,
                                    REASON_RTT_NOT_AVAILABLE);
                        }
                        stopRanging();
                    }
                }
                case STATUS_CODE_ERROR_STREAK_TIMEOUT -> {
                    Log.w(TAG, "Ranging result error streak timeout");
                    synchronized (mLock) {
                        if (mRttListener != null) {
                            mRttListener.onRangingSuspended(mRttDevice,
                                    RttRangingSessionCallback.REASON_SYSTEM_POLICY);
                        }
                        stopRanging();
                    }
                }
            }
        }

        @Override
        public void onRangingResults(List<RangingResult> results) {
            if (results == null || results.isEmpty()) {
                onRangingFailure(RttRangerListener.STATUS_CODE_FAIL_RESULT_EMPTY);
                return;
            }
            RangingResult result = results.get(0);
            int status = result.getStatus();
            if (status != RangingResult.STATUS_SUCCESS) {
                if (mResultErrorStreak.incrementAndGet() >= MAX_RANGING_RESULT_ERROR_STREAK) {
                    onRangingFailure(RttRangerListener.STATUS_CODE_ERROR_STREAK_TIMEOUT);
                }
                return;
            }
            if (status == RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC) {
                Log.w(TAG, "Responder does not support 11mc");
                onRangingFailure(RttRangerListener.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE);
                return;
            } else if (status == RangingResult.UNSPECIFIED) {
                Log.w(TAG, "Unspecified failed.");
                onRangingFailure(RttRangerListener.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE);
                return;
            } else if (status == RangingResult.STATUS_FAIL) {
                onRangingFailure(RttRangerListener.STATUS_CODE_FAIL_RESULT_FAIL);
                return;
            }
            if (!mIsRunning) {
                Log.w(TAG, "onRangingResult - ranging has stopped already.");
                stopRanging();
                return;
            }

            mResultErrorStreak.set(0);
            PeerHandle peerHandle = result.getPeerHandle();
            if (mPeerHandle.equals(peerHandle)) {
                synchronized (mLock) {
                    if (mRttListener != null) {
                        mRttListener.onRangingResult(mRttDevice,
                                new RttRangingPosition(result));
                    }
                }
                Log.i(TAG, "callback onRangingResult");
            } else {
                Log.i(TAG, "Received PeerHandle is unknown. lastPeerHandle = " + mPeerHandle
                        + ", gotPeerHandle = " + peerHandle);
            }
        }
    };

    public RttRangingDevice(@NonNull Context context, @NonNull DeviceType deviceType) {
        mContext = context;
        mDeviceType = deviceType;

        mHandler = new Handler(Looper.getMainLooper());
        mWifiAwareManager = context.getSystemService(WifiAwareManager.class);
        mWifiRttManager = context.getSystemService(WifiRttManager.class);
        mRttRanger = new RttRanger(mWifiRttManager, mHandler::post, context);
        mRttDevice = new RttDevice(this);
        mIsRunning = false;
        mResultErrorStreak = new AtomicInteger(0);
    }

    public void setRangingParameters(@NonNull RttRangingParameters rttRangingParameters) {
        this.mRttRangingParameters = rttRangingParameters;
    }

    public void setRangingRequestDelay(int rangingRequestDelay) {
        mRangingRequestDelay = rangingRequestDelay;
    }

    public void startRanging(@NonNull RttRangingSessionCallback rttListener,
            ExecutorService executorService) {
        Log.i(TAG, "Start ranging");

        if (mRttRangingParameters == null) {
            Log.w(TAG, "Tried to start ranging but no ranging parameters have been provided");
            return;
        }
        if (!mWifiAwareManager.isAvailable()) {
            Log.w(TAG, "Wifi Aware Manager is not available");
            return;
        }

        synchronized (mLock) {
            if (mIsRunning) {
                Log.w(TAG, "This client is already running.");
                return;
            }
            mIsRunning = true;
            mRttListener = rttListener;
            executorService.execute(() -> mWifiAwareManager.attach(
                    new AwareAttachCallback(mDeviceType, mRttRangingParameters), mHandler));
        }
    }

    public void reconfigureRangingInterval(int intervalSkipCount) {
        if (!mRttRangingParameters.isPeriodicRangingHwFeatureEnabled()) {
            mRttRanger.reconfigureInterval(intervalSkipCount);
        } else {
            Log.e(TAG, "Reconfiguration of ranging interval unsupported for HW periodic ranging");
        }
    }

    public void stopRanging() {
        synchronized (mLock) {
            if (!mIsRunning) {
                Log.w(TAG, "This client has stopped ranging already");
                return;
            }
            Log.i(TAG, "Closing WiFi aware session");
            mIsRunning = false;
            mRttRanger.stopRanging();

            if (mWifiAwareSession != null) {
                mWifiAwareSession.close();
                mWifiAwareSession = null;
            } else {
                Log.e(TAG, "Wifi aware session is null");
                mRttListener.onRangingSuspended(mRttDevice, REASON_STOP_RANGING_CALLED);
            }
            mCurrentPublishDiscoverySession = null;
            mCurrentSubscribeDiscoverySession = null;
        }
    }

    private void notifyPeer(PeerHandle peerHandle, byte[] message) {
        if (mCurrentPublishDiscoverySession != null) {
            mCurrentPublishDiscoverySession.sendMessage(peerHandle, GRAPI_RTT_MESSAGE_ID, message);
        } else if (mCurrentSubscribeDiscoverySession != null) {
            mCurrentSubscribeDiscoverySession.sendMessage(peerHandle, GRAPI_RTT_MESSAGE_ID,
                    message);
        }
    }

    private DiscoverySessionCallback createPublishDiscoverySessionCallback() {
        return new DiscoverySessionCallback() {
            @Override
            public void onPublishStarted(PublishDiscoverySession session) {
                Log.i(TAG, "onPublishStarted, PublishDiscoverySession= " + session);
                mCurrentPublishDiscoverySession = session;
                if (mRttListener != null) {
                    mRttListener.onRangingInitialized(mRttDevice);
                }
            }

            @Override
            public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
                Log.i(TAG, "onMessageReceived from subscriber");
                // Received message from subscriber and send device name
                mPeerName = new String(message, UTF_8);
                notifyPeer(peerHandle, Build.MODEL.getBytes(UTF_8));

                if (mPeerHandle == null) {
                    mPeerHandle = peerHandle; // Initialize mPeerHandle at publisher side.
                }

                int updateRateMs = RttRangingParameters.getIntervalMs(mRttRangingParameters);
                mRttListener.onRangingInitialized(mRttDevice);
                if (!mRttRangingParameters.isPeriodicRangingHwFeatureEnabled()
                        && !mRttRangingParameters.isRangeDataNtfDisabled()) {
                    mRttRanger.startRanging(peerHandle, mRttRangingListener, updateRateMs,
                            mRangingRequestDelay);
                }
            }

            @Override
            public void onSessionTerminated() {
                Log.i(TAG, "onSession Terminated. ");
                // TODO: Check whether we can get the reason code.
                mRttListener.onRangingSuspended(mRttDevice, REASON_STOP_RANGING_CALLED);
                mRttListener = null;
            }

            @Override
            public void onServiceLost(PeerHandle peerHandle, int reason) {
                Log.v(TAG, "onServiceLost peerHandle " + peerHandle + " reason " + reason);
                mRttListener.onRangingSuspended(mRttDevice, REASON_UNKNOWN);
            }
        };
    }

    private DiscoverySessionCallback createSubscribeDiscoverySessionCallback() {
        return new DiscoverySessionCallback() {

            @Override
            public void onSubscribeStarted(SubscribeDiscoverySession session) {
                Log.i(TAG, "onSubscribeStarted, SubscribeDiscoverySession= " + session);
                mCurrentSubscribeDiscoverySession = session;
                if (mRttListener != null) {
                    mRttListener.onRangingInitialized(mRttDevice);
                }
            }

            @Override
            public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
                Log.i(TAG, "onMessageReceived from publisher");
                mPeerName = new String(message, UTF_8);
            }

            @Override
            public void onServiceDiscoveredWithinRange(
                    PeerHandle peerHandle,
                    byte[] serviceSpecificInfo,
                    List<byte[]> matchFilter,
                    int distanceMm) {
                Log.i(TAG,
                        "onServiceDiscoveredWithinRange, peerHandle= " + peerHandle
                                + ", initial distanceMm= " + distanceMm);
                onPeerHandleReceived(peerHandle);
            }

            @Override
            public void onServiceDiscovered(PeerHandle peerHandle,
                    byte[] serviceSpecificInfo, List<byte[]> matchFilter) {
                Log.i(TAG,
                        "onServiceDiscovered, peerHandle= " + peerHandle);
                onPeerHandleReceived(peerHandle);
            }

            private void onPeerHandleReceived(PeerHandle peerHandle) {
                mPeerHandle = peerHandle;
                notifyPeer(peerHandle, Build.MODEL.getBytes(UTF_8));

                if (mRttListener != null) {
                    int updateRateMs = RttRangingParameters.getIntervalMs(mRttRangingParameters);
                    mRttListener.onRangingInitialized(mRttDevice);
                    // Rtt Ranger is only used for legacy RTT sessions.
                    if (!mRttRangingParameters.isPeriodicRangingHwFeatureEnabled()) {
                        mRttRanger.startRanging(peerHandle, mRttRangingListener, updateRateMs,
                                mRangingRequestDelay);
                    }
                } else {
                    Log.e(TAG, "Rtt Listener is null");
                }
            }

            // Uncommented in goog/main.
            // @Override
            public void onRangingResultsReceived(List<RangingResult> results) {
                Log.i(TAG, "RTT ranging results: " + results);
                mRttRangingListener.onRangingResults(results);
            }

            @Override
            public void onSessionTerminated() {
                Log.i(TAG, "onSession Terminated. ");
                // TODO: Check whether we can get the reason code.
                mRttListener.onRangingSuspended(mRttDevice, REASON_STOP_RANGING_CALLED);
                mRttListener = null;
            }

            @Override
            public void onServiceLost(PeerHandle peerHandle, int reason) {
                Log.v(TAG, "onServiceLost peerHandle " + peerHandle + " reason " + reason);
                mRttListener.onRangingSuspended(mRttDevice, REASON_UNKNOWN);
            }
        };
    }

    @SuppressLint("NewApi") // FLAG_RANGING_RTT_ENABLED will be enabled from Android 16.
    private class AwareAttachCallback extends AttachCallback {
        private final PublishConfig mPublishConfig;
        private final SubscribeConfig mSubscribeConfig;

        private final DeviceType mDeviceType;

        AwareAttachCallback(DeviceType deviceType,
                RttRangingParameters rttRangingParameters) {
            mDeviceType = deviceType;

            if (deviceType == DeviceType.PUBLISHER) {
                mPublishConfig = new PublishConfig.Builder()
                        .setMatchFilter(
                                Collections.singletonList(rttRangingParameters.getMatchFilter()))
                        .setServiceName(rttRangingParameters.getServiceName())
                        .setRangingEnabled(!rttRangingParameters.isRangeDataNtfDisabled())
                        .setTerminateNotificationEnabled(true)
                        // Uncommented in goog/main.
                        // .setPeriodicRangingResultsEnabled(
                        //        rttRangingParameters.isPeriodicRangingHwFeatureEnabled())
                        .build();
                mSubscribeConfig = null;
            } else if (deviceType == DeviceType.SUBSCRIBER) {
                mSubscribeConfig = new SubscribeConfig.Builder()
                        .setMatchFilter(
                                Collections.singletonList(rttRangingParameters.getMatchFilter()))
                        .setServiceName(rttRangingParameters.getServiceName())
                        .setMaxDistanceMm(rttRangingParameters.getMaxDistanceMm())
                        .setMinDistanceMm(rttRangingParameters.getMinDistanceMm())
                        .setTerminateNotificationEnabled(true)
                        // Uncommented in goog/main.
                        // .setPeriodicRangingInterval(
                        //        RttRangingParameters.getIntervalMs(rttRangingParameters))
                        // .setPeriodicRangingEnabled(
                        //        rttRangingParameters.isPeriodicRangingHwFeatureEnabled())
                        .build();
                mPublishConfig = null;
            } else {
                Log.w(TAG, "Unknown deviceType");
                mPublishConfig = null;
                mSubscribeConfig = null;
            }
        }

        @Override
        public void onAttached(WifiAwareSession session) {
            Log.i(TAG, "onAttached, session = " + session);
            mWifiAwareSession = session;
            if (mDeviceType == DeviceType.PUBLISHER) {
                session.publish(mPublishConfig, createPublishDiscoverySessionCallback(), mHandler);
            } else if (mDeviceType == DeviceType.SUBSCRIBER) {
                session.subscribe(mSubscribeConfig, createSubscribeDiscoverySessionCallback(),
                        mHandler);
            }
        }

        @Override
        public void onAttachFailed() {
            Log.w(TAG, "Wifi Aware attach failed");
            if (mRttListener != null) {
                mRttListener.onRangingSuspended(mRttDevice,
                        RttRangingSessionCallback.REASON_FAILED_TO_START);
            }
            stopRanging();
        }
    }

    public enum DeviceType {
        PUBLISHER,
        SUBSCRIBER,
    }
}
