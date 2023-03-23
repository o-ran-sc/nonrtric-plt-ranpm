/*-
 * ============LICENSE_START======================================================================
 * Copyright (C) 2018-2023 Nordix Foundation. All rights reserved.
 * Copyright (C) 2021 Nokia. All rights reserved.
 * ===============================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 * ============LICENSE_END========================================================================
 */
package org.oran.datafile.http;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.oran.datafile.exceptions.DatafileTaskException;

@ExtendWith(MockitoExtension.class)
public class HttpsClientConnectionManagerUtilTest {

    private static final String KEY_PATH = "src/test/resources/keystore.p12";
    private static final String KEY_PASSWORD = "src/test/resources/keystore.pass";
    private static final String KEY_IMPROPER_PASSWORD = "src/test/resources/dfc.jks.pass";
    private static final String TRUSTED_CA_PATH = "src/test/resources/trust.jks";
    private static final String TRUSTED_CA_PASSWORD = "src/test/resources/trust.pass";

    @Test
    public void emptyManager_shouldThrowException() {
        assertThrows(DatafileTaskException.class, () -> HttpsClientConnectionManagerUtil.instance());
    }

    @Test
    public void creatingManager_successfulCase() throws Exception {
        HttpsClientConnectionManagerUtil.setupOrUpdate(KEY_PATH, KEY_PASSWORD, TRUSTED_CA_PATH, TRUSTED_CA_PASSWORD, //
            true);
        assertNotNull(HttpsClientConnectionManagerUtil.instance());
    }

    @Test
    public void creatingManager_improperSecretShouldThrowException() {
        assertThrows(DatafileTaskException.class, () -> HttpsClientConnectionManagerUtil.setupOrUpdate(KEY_PATH, //
            KEY_IMPROPER_PASSWORD, TRUSTED_CA_PATH, TRUSTED_CA_PASSWORD, true));
        assertThrows(DatafileTaskException.class, () -> HttpsClientConnectionManagerUtil.instance());
    }

}
