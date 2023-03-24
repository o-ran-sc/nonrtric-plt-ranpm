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
import java.util.List;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.junit.jupiter.api.Test;
import org.oran.datafile.model.FileServerData;

class HttpUtilsTest {

    private static final String XNF_ADDRESS = "127.0.0.1";
    private static final int PORT = 443;
    private static final String JWT_PASSWORD = "thisIsThePassword";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String ANOTHER_TOKEN = "another_token";
    private static final String ANOTHER_DATA = "another_data";
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
    void isSingleQueryWithJWT_validToken() throws URISyntaxException {
        assertTrue(HttpUtils.isQueryWithSingleJWT(validTokenSingleQueryData()));
        assertTrue(HttpUtils.isQueryWithSingleJWT(validTokenDoubleQueryData()));
    }

    @Test
    void isSingleQueryWithJWT_invalidToken() throws URISyntaxException {
        assertFalse(HttpUtils.isQueryWithSingleJWT(validQueryNoToken()));
        assertFalse(HttpUtils.isQueryWithSingleJWT(queryDataDoubleToken()));
        assertFalse(HttpUtils.isQueryWithSingleJWT(null));
    }

    @Test
    void getJWTToken_jWTTokenPresent() throws URISyntaxException {
        assertEquals(JWT_PASSWORD, HttpUtils.getJWTToken(fileServerDataWithJWTToken()));
        assertEquals(JWT_PASSWORD, HttpUtils.getJWTToken(fileServerDataWithJWTTokenLongQueryAndFragment()));
    }

    @Test
    void getJWTToken_JWTTokenNotPresent() throws URISyntaxException {
        assertEquals("", HttpUtils.getJWTToken(fileServerDataQueryWithoutToken()));
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
    void prepareUri_verifyUriWithTokenAndFragment() throws URISyntaxException {
        String file = "/file";
        String expected = "http://" + XNF_ADDRESS + ":" + PORT + file + "?" + ANOTHER_TOKEN + "=" + ANOTHER_DATA + "&"
            + ANOTHER_TOKEN + "=" + ANOTHER_DATA + "&" + ANOTHER_TOKEN + "=" + ANOTHER_DATA + "#" + FRAGMENT;
        assertEquals(expected,
            HttpUtils.prepareUri("http", fileServerDataWithJWTTokenLongQueryAndFragment(), file, 443));
    }

    @Test
    void prepareUri_verifyUriWithoutTokenAndWithoutFragment() throws URISyntaxException {
        String file = "/file";
        String expected = "http://" + XNF_ADDRESS + ":" + PORT + file;
        assertEquals(expected, HttpUtils.prepareUri("http", fileServerDataNoTokenNoFragment(), file, 443));
    }

    private List<NameValuePair> validTokenSingleQueryData() throws URISyntaxException {
        String query = "?" + ACCESS_TOKEN + "=" + JWT_PASSWORD;
        return new URIBuilder(query).getQueryParams();
    }

    private List<NameValuePair> validTokenDoubleQueryData() throws URISyntaxException {
        StringBuilder doubleQuery = new StringBuilder();
        doubleQuery.append("?" + ANOTHER_TOKEN + "=" + ANOTHER_DATA + "&");
        doubleQuery.append(ACCESS_TOKEN + "=" + JWT_PASSWORD);
        return new URIBuilder(doubleQuery.toString()).getQueryParams();
    }

    private List<NameValuePair> validQueryNoToken() throws URISyntaxException {
        String query = "?" + ANOTHER_TOKEN + "=" + JWT_PASSWORD;
        return new URIBuilder(query).getQueryParams();
    }

    private List<NameValuePair> queryDataDoubleToken() throws URISyntaxException {
        StringBuilder doubleToken = new StringBuilder();
        doubleToken.append("?" + ACCESS_TOKEN + "=" + JWT_PASSWORD + "&");
        doubleToken.append(ACCESS_TOKEN + "=" + JWT_PASSWORD + "&");
        doubleToken.append(ANOTHER_TOKEN + "=" + ANOTHER_DATA);
        return new URIBuilder(doubleToken.toString()).getQueryParams();
    }

    private FileServerData fileServerDataWithJWTToken() throws URISyntaxException {
        String query = "?" + ACCESS_TOKEN + "=" + JWT_PASSWORD;

        return FileServerData.builder().serverAddress(XNF_ADDRESS).userId("").password("").port(PORT)
            .queryParameters(new URIBuilder(query).getQueryParams()).build();
    }

    private FileServerData fileServerDataWithJWTTokenLongQueryAndFragment() throws URISyntaxException {
        StringBuilder query = new StringBuilder();
        query.append("?" + ANOTHER_TOKEN + "=" + ANOTHER_DATA + "&");
        query.append(ANOTHER_TOKEN + "=" + ANOTHER_DATA + "&");
        query.append(ACCESS_TOKEN + "=" + JWT_PASSWORD + "&");
        query.append(ANOTHER_TOKEN + "=" + ANOTHER_DATA);

        return FileServerData.builder().serverAddress(XNF_ADDRESS).userId("").password("").port(PORT)
            .queryParameters(new URIBuilder(query.toString()).getQueryParams()).uriRawFragment(FRAGMENT).build();
    }

    private FileServerData fileServerDataQueryWithoutToken() throws URISyntaxException {
        StringBuilder query = new StringBuilder();
        query.append("?" + ANOTHER_TOKEN + "=" + ANOTHER_DATA);

        return FileServerData.builder().serverAddress(XNF_ADDRESS).userId("").password("").port(PORT)
            .queryParameters(new URIBuilder(query.toString()).getQueryParams()).build();
    }

    private FileServerData fileServerDataNoTokenNoFragment() throws URISyntaxException {
        return FileServerData.builder().serverAddress(XNF_ADDRESS).userId("").password("").port(PORT)
            .queryParameters(new URIBuilder("").getQueryParams()).uriRawFragment("").build();
    }
}
