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

package com.android.server.uwb;

import android.uwb.AngleMeasurement;
import android.uwb.AngleOfArrivalMeasurement;
import android.uwb.DistanceMeasurement;
import android.uwb.RangingMeasurement;
import android.uwb.UwbAddress;

import com.android.server.uwb.UwbSessionManager.UwbSession;
import com.android.server.uwb.correction.UwbFilterEngine;
import com.android.server.uwb.correction.math.SphericalVector;

/**
 * Represents a remote controlee that is involved in a session.
 */
public class UwbControlee implements AutoCloseable {
    private final UwbAddress mUwbAddress;
    private final UwbSession mSession;
    private final UwbFilterEngine mEngine;
    /** Confidence to use when the engine produces a result that wasn't in the original reading. */
    private static final double DEFAULT_CONFIDENCE = 0.0;
    /** Error value to use when the engine produces a result that wasn't in the original reading. */
    private static final double DEFAULT_ERROR_RADIANS = 0.0;
    /** Error value to use when the engine produces a result that wasn't in the original reading. */
    private static final double DEFAULT_ERROR_DISTANCE = 0.0;

    /**
     * Creates a new UwbControlee.
     *
     * @param uwbAddress The address of the controlee.
     */
    public UwbControlee(UwbAddress uwbAddress, UwbSessionManager.UwbSession session) {
        mUwbAddress = uwbAddress;
        mSession = session;
        mEngine = session.createFilterEngine();
    }

    /**
     * Gets the address of the controlee.
     *
     * @return A UwbAddress of the associated controlee.
     */
    public UwbAddress getUwbAddress() {
        return mUwbAddress;
    }

    /**
     * Gets the session that created this Controlee.
     *
     * @return A UwbSession.
     */
    public UwbSession getSession() {
        return mSession;
    }

    /** Shuts down any controlee-specific work. */
    @Override
    public void close() throws Exception {
        mEngine.close();
    }

    /**
     * Updates a RangingMeasurement builder to produce a filtered value. If the filter engine
     *  is not configured, this will not affect the builder.
     * @param rmBuilder The {@link RangingMeasurement.Builder} to reconfigure.
     */
    public void filterMeasurement(RangingMeasurement.Builder rmBuilder) {
        if (mEngine == null) {
            // Engine is disabled. Don't modify the builder.
            return;
        }
        RangingMeasurement rawMeasurement = rmBuilder.build();

        // Gather az/el/dist
        AngleOfArrivalMeasurement aoaMeasurement = rawMeasurement.getAngleOfArrivalMeasurement();
        DistanceMeasurement distMeasurement = rawMeasurement.getDistanceMeasurement();
        boolean hasAzimuth = false;
        boolean hasElevation = false;
        boolean hasDistance = false;
        float azimuth = 0;
        float elevation = 0;
        float distance = 0;
        if (aoaMeasurement != null) {
            if (aoaMeasurement.getAzimuth() != null) {
                hasAzimuth = true;
                azimuth = (float) aoaMeasurement.getAzimuth().getRadians();
            }
            if (aoaMeasurement.getAltitude() != null) {
                hasElevation = true;
                elevation = (float) aoaMeasurement.getAltitude().getRadians();
            }
        }
        if (distMeasurement != null) {
            hasDistance = true;
            distance = (float) distMeasurement.getMeters();
        }
        SphericalVector.Sparse sv = SphericalVector.fromRadians(azimuth, elevation, distance)
                .toSparse(hasAzimuth, hasElevation, hasDistance);

        // Give to the engine.
        mEngine.add(sv);

        SphericalVector engineResult = mEngine.compute();
        if (engineResult == null) {
            // Bail early - the engine didn't compute a result, so just leave the builder alone.
            return;
        }

        // Now re-generate the az/el/dist readings based on engine result.
        updateBuilder(rmBuilder, rawMeasurement, engineResult);
    }

    /**
     * Replaces az/el/dist values in a RangingMeasurement builder.
     * @param rmBuilder The RangingMeasurement builder to update.
     * @param rawMeasurement The original raw measurements. Used for fallback and confidence values.
     * @param replacement The filter engine's result.
     */
    private static void updateBuilder(RangingMeasurement.Builder rmBuilder,
            RangingMeasurement rawMeasurement,
            SphericalVector replacement) {
        // This is fairly verbose because of how nested data is, the risk of nulls, and the
        // fact that that azimuth is required up-front, even in the builder. Refactoring so the
        // RangingMeasurement can be cloned and changed would be nice, but it would change
        // (or at least add to) an external API.

        AngleOfArrivalMeasurement aoaMeasurement = rawMeasurement.getAngleOfArrivalMeasurement();
        DistanceMeasurement distMeasurement = rawMeasurement.getDistanceMeasurement();

        AngleMeasurement azimuthMeasurement = null;
        AngleMeasurement elevationMeasurement = null;
        if (aoaMeasurement != null) {
            if (aoaMeasurement.getAzimuth() != null) {
                azimuthMeasurement = new AngleMeasurement(
                        replacement.azimuth,
                        aoaMeasurement.getAzimuth().getErrorRadians(),
                        aoaMeasurement.getAzimuth().getConfidenceLevel()
                );
            }
            if (aoaMeasurement.getAltitude() != null) {
                elevationMeasurement = new AngleMeasurement(
                        replacement.elevation,
                        aoaMeasurement.getAltitude().getErrorRadians(),
                        aoaMeasurement.getAltitude().getConfidenceLevel()
                );
            }
        }
        if (azimuthMeasurement == null) {
            // There was no azimuth in the original reading. It may have been distance only.
            // Use the azimuth the engine computed.
            azimuthMeasurement = new AngleMeasurement(
                    replacement.azimuth, DEFAULT_ERROR_RADIANS, DEFAULT_CONFIDENCE);
        }
        if (elevationMeasurement == null) {
            // There was no elevation in the original reading.
            // Use the elevation the engine computed.
            elevationMeasurement = new AngleMeasurement(
                    replacement.elevation, DEFAULT_ERROR_RADIANS, DEFAULT_CONFIDENCE);
        }
        AngleOfArrivalMeasurement.Builder aoaBuilder =
                new AngleOfArrivalMeasurement.Builder(azimuthMeasurement);
        aoaBuilder.setAltitude(elevationMeasurement);

        DistanceMeasurement.Builder distanceBuilder = new DistanceMeasurement.Builder();
        if (distMeasurement == null) {
            // No distance value. Might have been a one-way AoA.
            distanceBuilder.setErrorMeters(DEFAULT_ERROR_DISTANCE);
            distanceBuilder.setConfidenceLevel(DEFAULT_CONFIDENCE);
        } else {
            distanceBuilder.setErrorMeters(distMeasurement.getErrorMeters());
            distanceBuilder.setConfidenceLevel(distMeasurement.getConfidenceLevel());
        }
        distanceBuilder.setMeters(replacement.distance);

        rmBuilder.setDistanceMeasurement(distanceBuilder.build());
        rmBuilder.setAngleOfArrivalMeasurement(aoaBuilder.build());
    }
}
