/*
 * Copyright 2020 The Android Open Source Project
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

package android.uwb;

import android.os.PersistableBundle;
import android.uwb.RangingChangeReason;
import android.uwb.RangingReport;
import android.uwb.SessionHandle;

/**
 * @hide
 */
oneway interface IUwbRangingCallbacks {
  /**
   * Called when the ranging session has been opened
   *
   * @param sessionHandle the session the callback is being invoked for
   */
  void onRangingOpened(in SessionHandle sessionHandle);

  /**
   * Called when a ranging session fails to start
   *
   * @param sessionHandle the session the callback is being invoked for
   * @param reason the reason the session failed to start
   * @param parameters protocol specific parameters
   */
  void onRangingOpenFailed(in SessionHandle sessionHandle,
                           RangingChangeReason reason,
                           in PersistableBundle parameters);

  /**
   * Called when ranging has started
   *
   * May output parameters generated by the lower layers that must be sent to the
   * remote device(s). The PersistableBundle must be constructed using the UWB
   * support library.
   *
   * @param sessionHandle the session the callback is being invoked for
   * @param rangingOutputParameters parameters generated by the lower layer that
   *                                should be sent to the remote device.
   */
  void onRangingStarted(in SessionHandle sessionHandle,
                        in PersistableBundle parameters);

  /**
   * Called when a ranging session fails to start
   *
   * @param sessionHandle the session the callback is being invoked for
   * @param reason the reason the session failed to start
   * @param parameters protocol specific parameters
   */
  void onRangingStartFailed(in SessionHandle sessionHandle,
                            RangingChangeReason reason,
                            in PersistableBundle parameters);

   /**
   * Called when ranging has been reconfigured
   *
   * @param sessionHandle the session the callback is being invoked for
   * @param parameters the updated ranging configuration
   */
  void onRangingReconfigured(in SessionHandle sessionHandle,
                             in PersistableBundle parameters);

  /**
   * Called when a ranging session fails to be reconfigured
   *
   * @param sessionHandle the session the callback is being invoked for
   * @param reason the reason the session failed to reconfigure
   * @param parameters protocol specific parameters
   */
  void onRangingReconfigureFailed(in SessionHandle sessionHandle,
                                  RangingChangeReason reason,
                                  in PersistableBundle parameters);

  /**
   * Called when the ranging session has been stopped
   *
   * @param sessionHandle the session the callback is being invoked for
   * @param reason the reason the session was stopped
   * @param parameters protocol specific parameters
   */

  void onRangingStopped(in SessionHandle sessionHandle,
                        RangingChangeReason reason,
                        in PersistableBundle parameters);

  /**
   * Called when a ranging session fails to stop
   *
   * @param sessionHandle the session the callback is being invoked for
   * @param reason the reason the session failed to stop
   * @param parameters protocol specific parameters
   */
  void onRangingStopFailed(in SessionHandle sessionHandle,
                           RangingChangeReason reason,
                           in PersistableBundle parameters);

  /**
   * Called when a ranging session is closed
   *
   * @param sessionHandle the session the callback is being invoked for
   * @param reason the reason the session was closed
   * @param parameters protocol specific parameters
   */
  void onRangingClosed(in SessionHandle sessionHandle,
                       RangingChangeReason reason,
                       in PersistableBundle parameters);

  /**
   * Provides a new RangingResult to the framework
   *
   * The reported timestamp for a ranging measurement must be calculated as the
   * time which the ranging round that generated this measurement concluded.
   *
   * @param sessionHandle an identifier to associate the ranging results with a
   *                      session that is active
   * @param result the ranging report
   */
  void onRangingResult(in SessionHandle sessionHandle, in RangingReport result);
}
