/*-
 * ============LICENSE_START======================================================================
 *  Copyright (C) 2020-2023 Nordix Foundation. All rights reserved.
 * Copyright (C) 2020-2021 Nokia. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.oran.datafile.exceptions.DatafileTaskException;
import org.oran.datafile.model.FileServerData;
import org.oran.datafile.oauth2.SecurityContext;

import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClientConfig;

@ExtendWith(MockitoExtension.class)
class DfcHttpClientTest {

    private static final String USERNAME = "bob";
    private static final String PASSWORD = "123";
    private static final String XNF_ADDRESS = "127.0.0.1";
    private static final int PORT = 80;

    @Mock
    private Path pathMock;

    DfcHttpClient dfcHttpClientSpy;

    @BeforeEach
    public void setup() {
        SecurityContext ctx = new SecurityContext("");
        dfcHttpClientSpy = spy(new DfcHttpClient(ctx, createFileServerData()));
    }

    @Test
    void openConnection_successBasicAuthSetup() throws DatafileTaskException {
        dfcHttpClientSpy.open();
        HttpClientConfig config = dfcHttpClientSpy.client.configuration();
        assertEquals(HttpUtils.basicAuthContent(USERNAME, PASSWORD), config.headers().get("Authorization"));
    }

    @Test
    void collectFile_AllOk() throws Exception {
        String REMOTE_FILE = "any";
        Flux<InputStream> fis = Flux.just(new ByteArrayInputStream("ReturnedString".getBytes()));

        dfcHttpClientSpy.open();

        when(dfcHttpClientSpy.getServerResponse(any())).thenReturn(fis);
        doReturn(false).when(dfcHttpClientSpy).isDownloadFailed(any());

        dfcHttpClientSpy.collectFile(REMOTE_FILE, pathMock);
        dfcHttpClientSpy.close();

        verify(dfcHttpClientSpy, times(1)).getServerResponse(REMOTE_FILE);
        verify(dfcHttpClientSpy, times(1)).processDataFromServer(any(), any(), any());
        verify(dfcHttpClientSpy, times(1)).isDownloadFailed(any());
    }

    @Test
    void collectFile_No200ResponseWriteToErrorMessage() throws DatafileTaskException {
        String ERROR_RESPONSE = "This is unexpected message";
        String REMOTE_FILE = "any";
        Flux<Throwable> fis = Flux.error(new Throwable(ERROR_RESPONSE));

        dfcHttpClientSpy.open();

        doReturn(fis).when(dfcHttpClientSpy).getServerResponse(any());

        assertThatThrownBy(() -> dfcHttpClientSpy.collectFile(REMOTE_FILE, pathMock))
            .hasMessageContaining(ERROR_RESPONSE);
        verify(dfcHttpClientSpy, times(1)).getServerResponse(REMOTE_FILE);
        verify(dfcHttpClientSpy, times(1)).processFailedConnectionWithServer(any(), any());
        dfcHttpClientSpy.close();
    }

    @Test
    void isResponseOk_validateResponse() {
        assertTrue(dfcHttpClientSpy.isResponseOk(HttpClientResponseHelper.NETTY_RESPONSE_OK));
        assertFalse(dfcHttpClientSpy.isResponseOk(HttpClientResponseHelper.RESPONSE_ANY_NO_OK));
    }

    private FileServerData createFileServerData() {
        return FileServerData.builder().serverAddress(XNF_ADDRESS).userId(USERNAME).password(PASSWORD).port(PORT)
            .build();
    }
}
