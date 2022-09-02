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

package com.android.server.uwb.secure.csml;

import static com.google.common.truth.Truth.assertThat;

import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.secure.iso7816.StatusWord;
import com.android.server.uwb.util.DataTypeConversionUtil;

import org.junit.Test;

public class GetDoResponseTest {
    @Test
    public void successResponse() {
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(
                DataTypeConversionUtil.hexStringToByteArray("0A0B"),
                StatusWord.SW_NO_ERROR.toInt());
        GetDoResponse getDoResponse =
                GetDoResponse.fromResponseApdu(responseApdu);

        assertThat(getDoResponse.isSuccess()).isTrue();
        assertThat(getDoResponse.data.get()).isEqualTo(
                DataTypeConversionUtil.hexStringToByteArray("0A0B"));
    }

    @Test
    public void errorResponse() {
        ResponseApdu responseApdu =
                ResponseApdu.fromStatusWord(StatusWord.SW_WARNING_STATE_UNCHANGED);
        GetDoResponse getDoResponse =
                GetDoResponse.fromResponseApdu(responseApdu);

        assertThat(getDoResponse.isSuccess()).isFalse();
    }
}
