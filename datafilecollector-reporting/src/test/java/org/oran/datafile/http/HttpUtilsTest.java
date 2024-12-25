/*
 * ============LICENSE_START======================================================================
 * Copyright (C) 2018-2023 Nordix Foundation. All rights reserved.
 * Modifications Copyright (C) 2021 Nokia. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;

import org.apache.hc.core5.net.URIBuilder;
import org.junit.jupiter.api.Test;
import org.oran.datafile.model.FileServerData;

class HttpUtilsTest {

    private static final String XNF_ADDRESS = "127.0.0.1";
    private static final int PORT = 443;
    private static final String FRAGMENT = "thisIsTheFragment";
    private static final String USERNAME = "bob";
    private static final String PASSWORD = "123";

    @Test
    void shouldReturnSuccessfulResponse() {
        assertTrue(HttpUtils.isSuccessfulResponseCodeWithDataRouter(200));
    }

    @Test
    void shouldReturnBadResponse() {
        assertFalse(HttpUtils.isSuccessfulResponseCodeWithDataRouter(404));
    }

    @Test
    void prepareUri_UriWithoutPort() {
        FileServerData serverData =
            FileServerData.builder().serverAddress(XNF_ADDRESS).userId(USERNAME).password(PASSWORD).build();
        String REMOTE_FILE = "any";

        String retrievedUri = HttpUtils.prepareUri("http", serverData, REMOTE_FILE, 80);
        assertTrue(retrievedUri.startsWith("http://" + XNF_ADDRESS + ":80"));
    }

    @Test
    void prepareUri_verifyUriWithoutTokenAndWithoutFragment() throws URISyntaxException {
        String file = "/file";
        String expected = "http://" + XNF_ADDRESS + ":" + PORT + file;
        assertEquals(expected, HttpUtils.prepareUri("http", fileServerDataNoTokenNoFragment(), file, 443));
    }

    private FileServerData fileServerDataNoTokenNoFragment() throws URISyntaxException {
        return FileServerData.builder().serverAddress(XNF_ADDRESS).userId("").password("").port(PORT)
            .queryParameters(new URIBuilder("").getQueryParams()).uriRawFragment("").build();
    }
}
