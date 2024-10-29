/*
 * Copyright (C) 2024 The Android Open Source Project
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

package multidevices.snippet.ranging;

import android.app.UiAutomation;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.RemoteException;
import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.params.DataNotificationConfig;
import android.ranging.params.SensorFusionParams;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;
import android.util.Log;
import android.uwb.UwbManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.ranging.uwb.backend.internal.Utils;
import com.android.server.ranging.RangingConfig;
import com.android.server.ranging.RangingData;
import com.android.server.ranging.RangingSession;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.uwb.UwbAdapter;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import dagger.Lazy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class GenericRangingSnippet implements Snippet {
    private static final String TAG = "GenericRangingSnippet";

    private final Context mContext;
    private final ConnectivityManager mConnectivityManager;
    private final UwbManager mUwbManager;
    private final ListeningExecutorService mExecutor = MoreExecutors.listeningDecorator(
            Executors.newSingleThreadExecutor());
    private final EventCache mEventCache = EventCache.getInstance();
    private static final HashMap<String, RangingSession> sRangingHashMap =
            new HashMap<>();
    private static final HashMap<String, GenericRangingCallback> sRangingCallbackHashMap =
            new HashMap<>();

    public GenericRangingSnippet() throws Throwable {
        adoptShellPermission();
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mUwbManager = mContext.getSystemService(UwbManager.class);
    }

    private static class UwbManagerSnippetException extends Exception {

        UwbManagerSnippetException(String msg) {
            super(msg);
        }

        UwbManagerSnippetException(String msg, Throwable err) {
            super(msg, err);
        }
    }

    private void adoptShellPermission() throws Throwable {
        UiAutomation uia = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uia.adoptShellPermissionIdentity();
        try {
            Class<?> cls = Class.forName("android.app.UiAutomation");
            Method destroyMethod = cls.getDeclaredMethod("destroy");
            destroyMethod.invoke(uia);
        } catch (ReflectiveOperationException e) {
            throw new UwbManagerSnippetException("Failed to cleaup Ui Automation", e);
        }
    }

    private enum Event {
        Invalid(0),
        Started(1 << 0),
        Stopped(1 << 1),
        ReportReceived(1 << 2),
        EventAll(
                1 << 0
                        | 1 << 1
                        | 1 << 2
        );

        private final int mType;

        Event(int type) {
            mType = type;
        }

        private int getType() {
            return mType;
        }
    }

    class GenericRangingCallback implements RangingSession.Callback {
        private String mId;
        private RangingData mLastDataReceived = null;

        GenericRangingCallback(String id, int events) {
            mId = id;
        }

        public Optional<RangingData> getLastDataReceived() {
            return Optional.ofNullable(mLastDataReceived);
        }

        private void handleEvent(Event e) {
            Log.d(TAG, "GenericRangingCallback#handleEvent() for " + e.toString());
            SnippetEvent event = new SnippetEvent(mId, "GenericRangingCallback");
            event.getData().putString("genericRangingSessionEvent", e.toString());
            mEventCache.postEvent(event);
        }

        @Override
        public void onStarted(@Nullable RangingTechnology technology) {
            Log.d(TAG, "GenericRangingCallback#onStarted() called");
            handleEvent(Event.Started);
        }

        @Override
        public void onStopped(@Nullable RangingTechnology technology, @StoppedReason int reason) {
            Log.d(TAG, "GenericRangingCallback#onStopped() called");
            handleEvent(Event.Stopped);
        }

        @Override
        public void onData(@NonNull RangingData data) {
            Log.d(TAG, "GenericRangingCallback#onData() called");
            mLastDataReceived = data;
            handleEvent(Event.ReportReceived);
        }
    }

//    private RangingParamsOld generateRangingParameters(JSONObject j) throws JSONException {
//        if (j == null) {
//            return null;
//        }
//        ImmutableMap.Builder<RangingDevice, UwbAddress> peerAddressesBuilder =
//                new ImmutableMap.Builder<>();
//        if (j.has("peerAddresses")) {
//            JSONArray jArray = j.getJSONArray("peerAddresses");
//            for (int i = 0; i < jArray.length(); i++) {
//                peerAddressesBuilder.put(new RangingDevice.Builder().build(),
//                        UwbAddress.fromBytes(convertJSONArrayToByteArray(jArray.getJSONArray(i))));
//            }
//        }
//
//        UwbRangingParams.Builder uwbParamsBuilder = new UwbRangingParams.Builder()
//                .setDeviceRole(j.getInt("deviceRole"))
//                .setPeerAddresses(peerAddressesBuilder.build())
//                .setConfigId(j.getInt("configType"))
//                .setDeviceAddress(UwbAddress.fromBytes(
//                        convertJSONArrayToByteArray(j.getJSONArray("deviceAddress"))))
//                .setSessionId(j.getInt("sessionId"))
//                .setSubSessionId(j.getInt("subSessionId"))
//                .setSessionKeyInfo(
//                        convertJSONArrayToByteArray(j.getJSONArray("sessionKeyInfo")))
//                .setComplexChannel(new UwbComplexChannel.Builder().setChannel(
//                                Utils.channelForTesting)
//                        .setPreambleIndex(Utils.preambleIndexForTesting).build())
//                .setRangingUpdateRate(j.getInt("updateRateType"))
//                .setSlotDurationMillis(j.getInt("slotDurationMillis"))
//                .setAoaDisabled(j.getBoolean("isAoaDisabled"));
//
//        if (j.has("subSessionKeyInfo")) {
//            uwbParamsBuilder.setSubSessionKeyInfo(
//                    convertJSONArrayToByteArray(j.getJSONArray("subSessionKeyInfo")));
//        }
//
//        return new RangingParamsOld.Builder()
//                .setUwbParameters(uwbParamsBuilder.build())
//                .build();
//    }

    private byte[] convertJSONArrayToByteArray(JSONArray jArray) throws JSONException {
        if (jArray == null) {
            return null;
        }
        byte[] bArray = new byte[jArray.length()];
        for (int i = 0; i < jArray.length(); i++) {
            bArray[i] = (byte) jArray.getInt(i);
        }
        return bArray;
    }

    private static String getUwbSessionKeyFromId(int sessionId) {
        return "uwb_session_" + sessionId;
    }

    @AsyncRpc(description = "Start UWB ranging session")
    public void startUwbRanging(String callbackId, JSONObject config)
            throws JSONException, RemoteException {
        ListeningExecutorService adapterExecutor = MoreExecutors.listeningDecorator(
                Executors.newSingleThreadExecutor());
        ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();

        RangingSession session = new RangingSession(mContext, adapterExecutor, timeoutExecutor);

        DataNotificationConfig dataNotificationConfig = new DataNotificationConfig.Builder()
                .setNotificationConfigType(config.getInt("rangeDataConfigType"))
                .build();

        RangingConfig rangingConfig = new RangingConfig.Builder(
                new RangingPreference.Builder()
                        //.setRangingParameters(generateRangingParameters(config))
                        .setSensorFusionParameters(new SensorFusionParams.Builder().build())
                        .setDataNotificationConfig(dataNotificationConfig)
                        .build()
        )
                .setNoInitialDataTimeout(Duration.ofSeconds(3))
                .setNoUpdatedDataTimeout(Duration.ofSeconds(2))
                .build();

        ImmutableMap<UUID, RangingConfig> parameters =
                new ImmutableMap.Builder<UUID, RangingConfig>()
                        .put(UUID.randomUUID(), rangingConfig)
                        .build();
        GenericRangingCallback callback =
                new GenericRangingCallback(callbackId, Event.EventAll.getType());
        String uwbSessionKey = getUwbSessionKeyFromId(config.getInt("sessionId"));

        sRangingHashMap.put(uwbSessionKey, session);
        session.start(parameters, callback);
        sRangingCallbackHashMap.put(uwbSessionKey, callback);
    }

    @Rpc(description = "Stop UWB ranging session")
    public void stopUwbRanging(int sessionId) throws JSONException {
        String uwbSessionKey = getUwbSessionKeyFromId(sessionId);
        if (sRangingHashMap.containsKey(uwbSessionKey)) {
            sRangingHashMap.get(uwbSessionKey).stop();
        }
    }

    @Rpc(description = "Check whether the last report included UWB data from the specified address")
    public boolean verifyUwbPeerFound(JSONArray peerAddress, int sessionId)
            throws JSONException {
        GenericRangingCallback callback =
                sRangingCallbackHashMap.get(getUwbSessionKeyFromId(sessionId));
        if (callback == null) {
            throw new IllegalArgumentException("Could not find session with id " + sessionId);
        }

        Optional<RangingData> data = callback.getLastDataReceived();
        if (data.isEmpty()) {
            Log.i(TAG, "No data has been received yet, or the last data received was empty");
            return false;
        }

        byte[] address = convertJSONArrayToByteArray(peerAddress);
        if (Arrays.equals(data.get().getPeerAddress(), address)) {
            return true;
        } else {
            Log.i(TAG, "Last ranging report did not include any data from peer "
                    + Arrays.toString(address));
            return false;
        }
    }

    @Rpc(description = "Check whether uwb is enabled")
    public boolean isUwbEnabled() {
        return mUwbManager.isUwbEnabled();
    }

    @Rpc(description = "Set airplane mode")
    public void setAirplaneMode(boolean enabled) {
        mConnectivityManager.setAirplaneMode(enabled);
    }

    @Rpc(description = "Log info level message to device logcat")
    public void logInfo(String message) throws JSONException {
        com.google.android.mobly.snippet.util.Log.i(TAG + message);
    }

    private static class CustomUwbAdapterProvider implements Lazy<UwbAdapter> {
        private final UwbAdapter mUwbAdapter;

        CustomUwbAdapterProvider(UwbAdapter uwbAdapter) {
            this.mUwbAdapter = uwbAdapter;
        }

        @Override
        public UwbAdapter get() {
            return mUwbAdapter;
        }
    }
}

