/*
 * ============LICENSE_START======================================================================
 * Copyright (C) 2018-2023 Nordix Foundation. All rights reserved.
 * Copyright (C) 2020 Nokia. All rights reserved.
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

package org.oran.datafile.ftp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPSClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.oran.datafile.model.FileServerData;
import org.springframework.http.HttpStatus;

public class FtpesClientTest {

    private static final String REMOTE_FILE_PATH = "/dir/sample.txt";
    private static final Path LOCAL_FILE_PATH = Paths.get("target/sample.txt");
    private static final String XNF_ADDRESS = "127.0.0.1";
    private static final int PORT = 8021;
    private static final String FTP_KEY_PATH = "ftpKeyPath";
    private static final String FTP_KEY_PASSWORD_PATH = "ftpKeyPasswordPath";
    private static final Path TRUSTED_CA_PATH = Paths.get("trustedCaPath");
    private static final String TRUSTED_CA_PASSWORD = "trustedCaPassword";

    private static final String USERNAME = "bob";
    private static final String PASSWORD = "123";

    private FTPSClient ftpsClientMock = mock(FTPSClient.class);
    private KeyManager keyManagerMock = mock(KeyManager.class);
    private TrustManager trustManagerMock = mock(TrustManager.class);
    private InputStream inputStreamMock = mock(InputStream.class);
    private OutputStream outputStreamMock = mock(OutputStream.class);

    FtpesClient clientUnderTestSpy;

    private FileServerData createFileServerData() {
        return FileServerData.builder() //
            .serverAddress(XNF_ADDRESS) //
            .userId(USERNAME).password(PASSWORD) //
            .port(PORT) //
            .build();
    }

    @BeforeEach
    protected void setUp() throws Exception {
        clientUnderTestSpy = spy(new FtpesClient(createFileServerData(), Paths.get(FTP_KEY_PATH), FTP_KEY_PASSWORD_PATH,
            TRUSTED_CA_PATH, TRUSTED_CA_PASSWORD));
        clientUnderTestSpy.realFtpsClient = ftpsClientMock;
    }

    private void verifyFtpsClientMock_openOk() throws Exception {
        doReturn(outputStreamMock).when(clientUnderTestSpy).createOutputStream(LOCAL_FILE_PATH);

        when(ftpsClientMock.retrieveFile(ArgumentMatchers.eq(REMOTE_FILE_PATH),
            ArgumentMatchers.any(OutputStream.class))).thenReturn(true);
        verify(ftpsClientMock).setNeedClientAuth(true);
        verify(ftpsClientMock).setKeyManager(keyManagerMock);
        verify(ftpsClientMock).setTrustManager(trustManagerMock);
        verify(ftpsClientMock).connect(XNF_ADDRESS, PORT);
        verify(ftpsClientMock).login(USERNAME, PASSWORD);
        verify(ftpsClientMock).getReplyCode();
        verify(ftpsClientMock, times(1)).enterLocalPassiveMode();
        verify(ftpsClientMock).execPBSZ(0);
        verify(ftpsClientMock).execPROT("P");
        verify(ftpsClientMock).setFileType(FTP.BINARY_FILE_TYPE);
        verify(ftpsClientMock).setBufferSize(1024 * 1024);
    }

    @Test
    public void collectFile_allOk() throws Exception {

        doReturn(keyManagerMock).when(clientUnderTestSpy).getKeyManager(Paths.get(FTP_KEY_PATH), FTP_KEY_PASSWORD_PATH);
        doReturn(trustManagerMock).when(clientUnderTestSpy).getTrustManager(TRUSTED_CA_PATH, TRUSTED_CA_PASSWORD);
        doReturn(outputStreamMock).when(clientUnderTestSpy).createOutputStream(LOCAL_FILE_PATH);
        doReturn(true).when(ftpsClientMock).login(USERNAME, PASSWORD);
        doReturn(HttpStatus.OK.value()).when(ftpsClientMock).getReplyCode();

        clientUnderTestSpy.open();

        doReturn(true).when(ftpsClientMock).retrieveFile(REMOTE_FILE_PATH, outputStreamMock);
        clientUnderTestSpy.collectFile(REMOTE_FILE_PATH, LOCAL_FILE_PATH);

        doReturn(true).when(ftpsClientMock).isConnected();
        clientUnderTestSpy.close();

        verifyFtpsClientMock_openOk();
        verify(ftpsClientMock, times(1)).isConnected();
        verify(ftpsClientMock, times(1)).logout();
        verify(ftpsClientMock, times(1)).disconnect();
        verify(ftpsClientMock, times(1)).retrieveFile(ArgumentMatchers.eq(REMOTE_FILE_PATH), any());
        verifyNoMoreInteractions(ftpsClientMock);
    }

    @Test
    public void collectFileFaultyOwnKey_shouldFail() throws Exception {

        doReturn(outputStreamMock).when(clientUnderTestSpy).createOutputStream(LOCAL_FILE_PATH);
        assertThatThrownBy(() -> clientUnderTestSpy.open()).hasMessageContaining("Could not open connection:");

        verify(ftpsClientMock).setNeedClientAuth(true);

        doReturn(false).when(ftpsClientMock).isConnected();
        clientUnderTestSpy.close();
        verify(ftpsClientMock).isConnected();
        verifyNoMoreInteractions(ftpsClientMock);
    }

    @Test
    public void collectFileFaultTrustedCA_shouldFail_no_trustedCA_file() throws Exception {

        doReturn(keyManagerMock).when(clientUnderTestSpy).getKeyManager(Paths.get(FTP_KEY_PATH), FTP_KEY_PASSWORD_PATH);
        doThrow(new IOException("problem")).when(clientUnderTestSpy).createInputStream(TRUSTED_CA_PATH);

        assertThatThrownBy(() -> clientUnderTestSpy.open()).hasMessageContaining("Could not open connection:");

    }

    @Test
    public void collectFileFaultTrustedCA_shouldFail_empty_trustedCA_file() throws Exception {

        doReturn(keyManagerMock).when(clientUnderTestSpy).getKeyManager(Paths.get(FTP_KEY_PATH), FTP_KEY_PASSWORD_PATH);
        doReturn(inputStreamMock).when(clientUnderTestSpy).createInputStream(TRUSTED_CA_PATH);

        assertThatThrownBy(() -> clientUnderTestSpy.open()).hasMessageContaining("Could not open connection: ");
    }

    @Test
    public void collectFileFaultyLogin_shouldFail() throws Exception {

        doReturn(keyManagerMock).when(clientUnderTestSpy).getKeyManager(Paths.get(FTP_KEY_PATH), FTP_KEY_PASSWORD_PATH);
        doReturn(trustManagerMock).when(clientUnderTestSpy).getTrustManager(TRUSTED_CA_PATH, TRUSTED_CA_PASSWORD);
        doReturn(outputStreamMock).when(clientUnderTestSpy).createOutputStream(LOCAL_FILE_PATH);
        doReturn(false).when(ftpsClientMock).login(USERNAME, PASSWORD);

        assertThatThrownBy(() -> clientUnderTestSpy.open()).hasMessage("Unable to log in to xNF. 127.0.0.1");

        verify(ftpsClientMock).setNeedClientAuth(true);
        verify(ftpsClientMock).setKeyManager(keyManagerMock);
        verify(ftpsClientMock).setTrustManager(trustManagerMock);
        verify(ftpsClientMock).connect(XNF_ADDRESS, PORT);
        verify(ftpsClientMock).login(USERNAME, PASSWORD);
    }

    @Test
    public void collectFileBadRequestResponse_shouldFail() throws Exception {
        doReturn(keyManagerMock).when(clientUnderTestSpy).getKeyManager(Paths.get(FTP_KEY_PATH), FTP_KEY_PASSWORD_PATH);
        doReturn(trustManagerMock).when(clientUnderTestSpy).getTrustManager(TRUSTED_CA_PATH, TRUSTED_CA_PASSWORD);
        doReturn(outputStreamMock).when(clientUnderTestSpy).createOutputStream(LOCAL_FILE_PATH);
        doReturn(true).when(ftpsClientMock).login(USERNAME, PASSWORD);
        doReturn(503).when(ftpsClientMock).getReplyCode();

        assertThatThrownBy(() -> clientUnderTestSpy.open())
            .hasMessage("Unable to connect to xNF. 127.0.0.1 xNF reply code: 503");

        verify(ftpsClientMock).setNeedClientAuth(true);
        verify(ftpsClientMock).setKeyManager(keyManagerMock);
        verify(ftpsClientMock).setTrustManager(trustManagerMock);
        verify(ftpsClientMock).connect(XNF_ADDRESS, PORT);
        verify(ftpsClientMock).login(USERNAME, PASSWORD);
        verify(ftpsClientMock, times(2)).getReplyCode();
        verifyNoMoreInteractions(ftpsClientMock);
    }

    @Test
    public void collectFile_shouldFail() throws Exception {
        doReturn(keyManagerMock).when(clientUnderTestSpy).getKeyManager(Paths.get(FTP_KEY_PATH), FTP_KEY_PASSWORD_PATH);
        doReturn(trustManagerMock).when(clientUnderTestSpy).getTrustManager(TRUSTED_CA_PATH, TRUSTED_CA_PASSWORD);
        doReturn(outputStreamMock).when(clientUnderTestSpy).createOutputStream(LOCAL_FILE_PATH);
        doReturn(true).when(ftpsClientMock).login(USERNAME, PASSWORD);
        doReturn(HttpStatus.OK.value()).when(ftpsClientMock).getReplyCode();
        clientUnderTestSpy.open();

        doReturn(false).when(ftpsClientMock).retrieveFile(REMOTE_FILE_PATH, outputStreamMock);

        assertThatThrownBy(() -> clientUnderTestSpy.collectFile(REMOTE_FILE_PATH, LOCAL_FILE_PATH))
            .hasMessageContaining(REMOTE_FILE_PATH).hasMessageContaining("No retry");

        verifyFtpsClientMock_openOk();
        verify(ftpsClientMock, times(1)).retrieveFile(ArgumentMatchers.eq(REMOTE_FILE_PATH), any());
        verifyNoMoreInteractions(ftpsClientMock);
    }

    @Test
    public void collectFile_shouldFail_ioexception() throws Exception {
        doReturn(keyManagerMock).when(clientUnderTestSpy).getKeyManager(Paths.get(FTP_KEY_PATH), FTP_KEY_PASSWORD_PATH);
        doReturn(trustManagerMock).when(clientUnderTestSpy).getTrustManager(TRUSTED_CA_PATH, TRUSTED_CA_PASSWORD);
        doReturn(outputStreamMock).when(clientUnderTestSpy).createOutputStream(LOCAL_FILE_PATH);
        doReturn(true).when(ftpsClientMock).login(USERNAME, PASSWORD);
        doReturn(HttpStatus.OK.value()).when(ftpsClientMock).getReplyCode();
        clientUnderTestSpy.open();
        when(ftpsClientMock.isConnected()).thenReturn(false);

        doThrow(new IOException("problem")).when(ftpsClientMock).retrieveFile(REMOTE_FILE_PATH, outputStreamMock);

        assertThatThrownBy(() -> clientUnderTestSpy.collectFile(REMOTE_FILE_PATH, LOCAL_FILE_PATH))
            .hasMessage("Could not fetch file: java.io.IOException: problem");

        verifyFtpsClientMock_openOk();
        verify(ftpsClientMock, times(1)).retrieveFile(ArgumentMatchers.eq(REMOTE_FILE_PATH), any());
        verifyNoMoreInteractions(ftpsClientMock);
    }
}
