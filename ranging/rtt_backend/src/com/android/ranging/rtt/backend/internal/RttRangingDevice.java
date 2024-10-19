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

package com.android.ranging.rtt.backend.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

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

import com.android.ranging.rtt.backend.internal.RttRanger.RttRangerListener;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Class for interacting with nearby RTT devices to perform ranging.
 */
public class RttRangingDevice {
    private static final String TAG = RttRangingDevice.class.getName();
    private static final int GRAPI_RTT_MESSAGE_ID = 1;
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

    /** Listener for range results. */
    private RttRangerListener mRttRangingListener = new RttRangerListener() {
        @Override
        public void onRangingFailure(int code) {
            switch (code) {
                case STATUS_CODE_FAIL:
                    Log.w(TAG, "Failed to range");
                    break;

                case STATUS_CODE_FAIL_RESULT_EMPTY:
                    Log.i(TAG, "Range results are empty");
                    synchronized (mLock) {
//                        if (mRttListener != null) {
//                            mRttListener.onRangingSuspended(mRttDevice,
//                                    RttRangingSessionCallback.REASON_RTT_NOT_AVAILABLE);
//                        }
                        stopRanging();
                    }
                    break;
                case STATUS_CODE_FAIL_RTT_NOT_AVAILABLE:
                    Log.w(TAG, "RTT Not Available");
                    synchronized (mLock) {
//                        if (mRttListener != null) {
//                            mRttListener.onRangingSuspended(mRttDevice,
//                                    RttRangingSessionCallback.REASON_RTT_NOT_AVAILABLE);
//                        }
                        stopRanging();
                    }
                    break;
            }
        }

        @Override
        public void onRangingResult(RangingResult result) {
            if (!mIsRunning) {
                Log.w(TAG, "onRangingResult - ranging has stopped already.");
                stopRanging();
                return;
            }

            PeerHandle peerHandle = result.getPeerHandle();
            if (mPeerHandle.equals(peerHandle)) {
                synchronized (mLock) {
                    if (mRttListener != null) {
                        mRttListener.onRangingResult(mRttDevice, new RttRangingPosition(result));
                    }
                }
                Log.i(TAG, "callback onRangingResult");
            } else {
                Log.i(TAG, "Received PeerHandle is unknown. lastPeerHandle = " + mPeerHandle
                        + ", gotPeerHandle = " + peerHandle);
            }
        }
    };

    private Runnable mRunnablePingPublisher = new Runnable() {
        @Override
        public void run() {
            synchronized (mLock) {
                if (!mIsRunning) {
                    Log.w(TAG, "RttRangingDevice is not running");
                    return;
                }
                if (mRttListener != null) {
                    mRttListener.onRangingResult(mRttDevice, new RttRangingPosition());
                }
                pingPublisher();
            }
        }
    };

    public RttRangingDevice(@NonNull Context context, @NonNull DeviceType deviceType) {
        mContext = context;
        mDeviceType = deviceType;

        mHandler = new Handler(Looper.getMainLooper());
        mWifiAwareManager = context.getSystemService(WifiAwareManager.class);
        mWifiRttManager = context.getSystemService(WifiRttManager.class);
        mRttRanger = new RttRanger(mWifiRttManager, mHandler::post);
        mRttDevice = new RttDevice(this);
        mIsRunning = false;
    }

    public void setRangingParameters(@NonNull RttRangingParameters rttRangingParameters) {
        this.mRttRangingParameters = rttRangingParameters;
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
            mWifiAwareManager.attach(new AwareAttachCallback(mDeviceType, mRttRangingParameters),
                    mHandler);
        }
    }

    public void stopRanging() {
        Log.i(TAG, "Closing WiFi aware session");

        synchronized (mLock) {
            if (!mIsRunning) {
                Log.w(TAG, "This client is already stop.");
                return;
            }
            mIsRunning = false;
            mRttRanger.stopRanging();
            mHandler.removeCallbacks(mRunnablePingPublisher);

            if (mWifiAwareSession != null) {
                mWifiAwareSession.close();
                mWifiAwareSession = null;
            }
            mCurrentPublishDiscoverySession = null;
            mCurrentSubscribeDiscoverySession = null;
            mRttListener = null;
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

    private void pingPublisher() {
//        Log.i(TAG, "Publisher ping");
//        mHandler.postDelayed(mRunnablePingPublisher,
//                mRttRangingParameters.getPublisherPingDuration().toMillis());
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

                if (mRttRangingParameters.getEnablePublisherRanging()) {
                    mRttListener.onRangingInitialized(mRttDevice);
                    mRttRanger.startRanging(peerHandle, mRttRangingListener);
                } else {
                    pingPublisher();
                }
            }

            @Override
            public void onSessionTerminated() {
                Log.i(TAG, "onSession Terminated. ");
            }
        };
    }

    private DiscoverySessionCallback createSubscribeDiscoverySessionCallback() {
        return new DiscoverySessionCallback() {

            @Override
            public void onSubscribeStarted(SubscribeDiscoverySession session) {
                Log.i(TAG, "onSubscribeStarted, SubscribeDiscoverySession= " + session);
                mCurrentSubscribeDiscoverySession = session;
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
                        "onServiceDiscovered, peerHandle= " + peerHandle + ", initial distanceMm= "
                                + distanceMm);

                mPeerHandle = peerHandle;
                notifyPeer(peerHandle, Build.MODEL.getBytes(UTF_8));

                if (mRttListener != null) {
                    mRttListener.onRangingInitialized(mRttDevice);
                    mRttRanger.startRanging(peerHandle, mRttRangingListener);
                } else {
                    Log.e(TAG, "Rtt Listener is null");
                }
            }

            @Override
            public void onSessionTerminated() {
                Log.i(TAG, "onSession Terminated. ");
            }
        };
    }

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
                        .setRangingEnabled(true)
                        .setTerminateNotificationEnabled(true)
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