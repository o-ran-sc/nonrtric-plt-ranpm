/*-
 * ============LICENSE_START======================================================================
 *  Copyright (C) 2023 Nordix Foundation. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.apache.hc.core5.net.URIBuilder;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.oran.datafile.exceptions.DatafileTaskException;
import org.oran.datafile.exceptions.NonRetryableDatafileTaskException;
import org.oran.datafile.model.FileServerData;
import org.oran.datafile.oauth2.SecurityContext;

@ExtendWith(MockitoExtension.class)
class DfcHttpsClientTest {

    private static final String USERNAME = "bob";
    private static final String PASSWORD = "123";
    private static final String XNF_ADDRESS = "127.0.0.1";
    private static final int PORT = 443;
    private static final String JWT_PASSWORD = "thisIsThePassword";
    private static String ACCESS_TOKEN = "access_token";
    private static String remoteFile = "remoteFile";

    @Mock
    private PoolingHttpClientConnectionManager connectionManager;
    @Mock
    private Path localFile;

    DfcHttpsClient dfcHttpsClientSpy;

    @BeforeEach
    public void setup() {
        SecurityContext ctx = new SecurityContext("");
        dfcHttpsClientSpy = spy(new DfcHttpsClient(ctx, createFileServerData(), connectionManager));
    }

    @Test
    void dfcHttpsClient_flow_successfulCallAndResponseProcessing() throws Exception {
        doReturn(HttpClientResponseHelper.APACHE_RESPONSE_OK).when(dfcHttpsClientSpy)
            .executeHttpClient(any(HttpGet.class));
        doReturn((long) 3).when(dfcHttpsClientSpy).writeFile(eq(localFile), any(InputStream.class));

        dfcHttpsClientSpy.open();
        dfcHttpsClientSpy.collectFile(remoteFile, localFile);
        dfcHttpsClientSpy.close();

        verify(dfcHttpsClientSpy, times(1)).makeCall(any(HttpGet.class));
        verify(dfcHttpsClientSpy, times(1)).executeHttpClient(any(HttpGet.class));
        verify(dfcHttpsClientSpy, times(1)).processResponse(HttpClientResponseHelper.APACHE_RESPONSE_OK, localFile);
        verify(dfcHttpsClientSpy, times(1)).writeFile(eq(localFile), any(InputStream.class));
    }

    @Test
    void dfcHttpsClient_flow_successfulCallWithJWTAndResponseProcessing() throws Exception {
        FileServerData serverData = jWTTokenInFileServerData();
        SecurityContext ctx = new SecurityContext("");
        dfcHttpsClientSpy = spy(new DfcHttpsClient(ctx, serverData, connectionManager));

        doReturn(HttpClientResponseHelper.APACHE_RESPONSE_OK).when(dfcHttpsClientSpy)
            .executeHttpClient(any(HttpGet.class));
        doReturn((long) 3).when(dfcHttpsClientSpy).writeFile(eq(localFile), any(InputStream.class));

        dfcHttpsClientSpy.open();
        dfcHttpsClientSpy.collectFile(remoteFile, localFile);
        dfcHttpsClientSpy.close();

        verify(dfcHttpsClientSpy, times(1)).makeCall(any(HttpGet.class));
        verify(dfcHttpsClientSpy, times(1)).executeHttpClient(any(HttpGet.class));
        verify(dfcHttpsClientSpy, times(1)).processResponse(HttpClientResponseHelper.APACHE_RESPONSE_OK, localFile);
        verify(dfcHttpsClientSpy, times(1)).writeFile(eq(localFile), any(InputStream.class));
        String str = serverData.toString();
        assertFalse(str.contains(JWT_PASSWORD));
    }

    @Test
    void dfcHttpsClient_flow_failedCallUnexpectedResponseCode() throws Exception {
        doReturn(HttpClientResponseHelper.APACHE_RESPONSE_OK).when(dfcHttpsClientSpy)
            .executeHttpClient(any(HttpGet.class));
        doReturn(false).when(dfcHttpsClientSpy).isResponseOk(any(HttpResponse.class));

        dfcHttpsClientSpy.open();

        assertThrows(DatafileTaskException.class, () -> dfcHttpsClientSpy.collectFile(remoteFile, localFile));
    }

    @Test
    void dfcHttpsClient_flow_failedCallConnectionTimeout() throws Exception {
        doThrow(ConnectTimeoutException.class).when(dfcHttpsClientSpy).executeHttpClient(any(HttpGet.class));

        dfcHttpsClientSpy.open();

        assertThrows(NonRetryableDatafileTaskException.class,
            () -> dfcHttpsClientSpy.collectFile(remoteFile, localFile));
    }

    @Test
    void dfcHttpsClient_flow_failedCallIOExceptionForExecuteHttpClient() throws Exception {
        doThrow(IOException.class).when(dfcHttpsClientSpy).executeHttpClient(any(HttpGet.class));

        dfcHttpsClientSpy.open();

        assertThrows(DatafileTaskException.class, () -> dfcHttpsClientSpy.collectFile(remoteFile, localFile));
    }

    private FileServerData createFileServerData() {
        return FileServerData.builder().serverAddress(XNF_ADDRESS).userId(USERNAME).password(PASSWORD).port(PORT)
            .build();
    }

    private FileServerData emptyUserInFileServerData() {
        return FileServerData.builder().serverAddress(XNF_ADDRESS).userId("").password("").port(PORT).build();
    }

    private FileServerData invalidUserInFileServerData() {
        return FileServerData.builder().serverAddress(XNF_ADDRESS).userId(USERNAME).password("").port(PORT).build();
    }

    private FileServerData jWTTokenInFileServerData() throws URISyntaxException {
        String query = "?" + ACCESS_TOKEN + "=" + JWT_PASSWORD;

        return FileServerData.builder().serverAddress(XNF_ADDRESS).userId("").password("").port(PORT)
            .queryParameters(new URIBuilder(query).getQueryParams()).build();
    }
}
