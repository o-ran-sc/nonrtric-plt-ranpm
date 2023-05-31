/*-
 * ============LICENSE_START======================================================================
 * Copyright (C) 2018-2023 Nordix Foundation. All rights reserved.
 * Copyright (C) 2020-2022 Nokia. All rights reserved.
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

package org.oran.datafile.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.oran.datafile.configuration.AppConfig;
import org.oran.datafile.configuration.CertificateConfig;
import org.oran.datafile.exceptions.DatafileTaskException;
import org.oran.datafile.exceptions.NonRetryableDatafileTaskException;
import org.oran.datafile.ftp.FtpesClient;
import org.oran.datafile.ftp.SftpClient;
import org.oran.datafile.http.DfcHttpClient;
import org.oran.datafile.http.DfcHttpsClient;
import org.oran.datafile.model.Counters;
import org.oran.datafile.model.FileData;
import org.oran.datafile.model.FilePublishInformation;
import org.oran.datafile.model.FileReadyMessage;
import org.oran.datafile.oauth2.SecurityContext;
import reactor.test.StepVerifier;

public class FileCollectorTest {

    final static String DATAFILE_TMPDIR = "/tmp/onap_datafile/";
    private static final String PRODUCT_NAME = "NrRadio";
    private static final String VENDOR_NAME = "Ericsson";
    private static final int LAST_EPOCH_MICROSEC = 87457457;
    private static final String SOURCE_NAME = "oteNB5309";
    private static final int START_EPOCH_MICROSEC = 874575764;
    private static final String TIME_ZONE_OFFSET = "UTC+05:00";
    private static final String FTPES_SCHEME = "ftpes://";
    private static final String SFTP_SCHEME = "sftp://";
    private static final String HTTP_SCHEME = "http://";
    private static final String HTTPS_SCHEME = "https://";
    private static final String SERVER_ADDRESS = "192.168.0.101";
    private static final int PORT_22 = 22;
    private static final String PM_FILE_NAME = "A20161224.1030-1045.bin.gz";
    private static final Path LOCAL_FILE_LOCATION = Paths.get(DATAFILE_TMPDIR, SOURCE_NAME, PM_FILE_NAME);
    private static final String REMOTE_FILE_LOCATION = "/ftp/rop/" + PM_FILE_NAME;
    private static final String USER = "usr";
    private static final String PWD = "pwd";
    private static final String FTPES_LOCATION =
        FTPES_SCHEME + USER + ":" + PWD + "@" + SERVER_ADDRESS + ":" + PORT_22 + REMOTE_FILE_LOCATION;

    private static final String FTPES_LOCATION_NO_PORT =
        FTPES_SCHEME + USER + ":" + PWD + "@" + SERVER_ADDRESS + REMOTE_FILE_LOCATION;
    private static final String SFTP_LOCATION = SFTP_SCHEME + SERVER_ADDRESS + ":" + PORT_22 + REMOTE_FILE_LOCATION;
    private static final String SFTP_LOCATION_NO_PORT = SFTP_SCHEME + SERVER_ADDRESS + REMOTE_FILE_LOCATION;

    private static final String HTTP_LOCATION =
        HTTP_SCHEME + USER + ":" + PWD + "@" + SERVER_ADDRESS + ":" + PORT_22 + REMOTE_FILE_LOCATION;
    private static final String HTTP_LOCATION_NO_PORT =
        HTTP_SCHEME + USER + ":" + PWD + "@" + SERVER_ADDRESS + REMOTE_FILE_LOCATION;
    private static final String HTTPS_LOCATION =
        HTTPS_SCHEME + USER + ":" + PWD + "@" + SERVER_ADDRESS + ":" + PORT_22 + REMOTE_FILE_LOCATION;
    private static final String HTTPS_LOCATION_NO_PORT =
        HTTPS_SCHEME + USER + ":" + PWD + "@" + SERVER_ADDRESS + REMOTE_FILE_LOCATION;

    private static final String GZIP_COMPRESSION = "gzip";
    private static final String MEAS_COLLECT_FILE_FORMAT_TYPE = "org.3GPP.32.435#measCollec";
    private static final String FILE_FORMAT_VERSION = "V10";
    private static final String CERTIFICATE_KEY_PASSWORD_PATH = "certificateKeyPassword";
    private static final String TRUSTED_CA_PATH = "trustedCAPath";
    private static final String TRUSTED_CA_PASSWORD_PATH = "trustedCAPassword";
    private static final String CHANGE_IDENTIFIER = "PM_MEAS_FILES";
    private static final String FILE_FORMAT_TYPE = "org.3GPP.32.435#measCollec";
    private static final String CHANGE_TYPE = "FileReady";

    private static AppConfig appConfigMock = mock(AppConfig.class);
    private static CertificateConfig certificateConfigMock = mock(CertificateConfig.class);

    private FtpesClient ftpesClientMock = mock(FtpesClient.class);

    private SftpClient sftpClientMock = mock(SftpClient.class);

    private DfcHttpClient dfcHttpClientMock = mock(DfcHttpClient.class);
    private DfcHttpsClient dfcHttpsClientMock = mock(DfcHttpsClient.class);

    private Counters counters;

    static final SecurityContext securityContext = new SecurityContext("");

    FileReadyMessage.Event event(String location) {
        FileReadyMessage.MessageMetaData messageMetaData = FileReadyMessage.MessageMetaData.builder() //
            .lastEpochMicrosec(LAST_EPOCH_MICROSEC) //
            .sourceName(SOURCE_NAME) //
            .startEpochMicrosec(START_EPOCH_MICROSEC) //
            .timeZoneOffset(TIME_ZONE_OFFSET) //
            .changeIdentifier(CHANGE_IDENTIFIER) //
            .eventName("Noti_NrRadio-Ericsson_FileReady").build();

        FileReadyMessage.FileInfo fileInfo = FileReadyMessage.FileInfo //
            .builder() //
            .fileFormatType(FILE_FORMAT_TYPE) //
            .location(location) //
            .fileFormatVersion(FILE_FORMAT_VERSION) //
            .compression(GZIP_COMPRESSION) //
            .build();

        FileReadyMessage.ArrayOfNamedHashMap arrayOfNamedHashMap = FileReadyMessage.ArrayOfNamedHashMap //
            .builder().name(PM_FILE_NAME) //
            .hashMap(fileInfo).build();

        List<FileReadyMessage.ArrayOfNamedHashMap> arrayOfNamedHashMapList = new ArrayList<>();
        arrayOfNamedHashMapList.add(arrayOfNamedHashMap);

        FileReadyMessage.NotificationFields notificationFields = FileReadyMessage.NotificationFields //
            .builder().notificationFieldsVersion("notificationFieldsVersion") //
            .changeType(CHANGE_TYPE).changeIdentifier(CHANGE_IDENTIFIER) //
            .arrayOfNamedHashMap(arrayOfNamedHashMapList) //
            .build();

        return FileReadyMessage.Event.builder() //
            .commonEventHeader(messageMetaData) //
            .notificationFields(notificationFields).build();
    }

    private FileReadyMessage fileReadyMessage(String location) {
        FileReadyMessage message = FileReadyMessage.builder() //
            .event(event(location)) //
            .build();
        return message;
    }

    private FileData createFileData(String location) {
        return FileData.createFileData(fileReadyMessage(location)).iterator().next();
    }

    private FilePublishInformation createExpectedFilePublishInformation(String location) {
        return FilePublishInformation.builder() //
            .productName(PRODUCT_NAME) //
            .vendorName(VENDOR_NAME) //
            .lastEpochMicrosec(LAST_EPOCH_MICROSEC) //
            .sourceName(SOURCE_NAME) //
            .startEpochMicrosec(START_EPOCH_MICROSEC) //
            .timeZoneOffset(TIME_ZONE_OFFSET) //
            .name(SOURCE_NAME + "/" + PM_FILE_NAME) //
            .compression(GZIP_COMPRESSION) //
            .fileFormatType(MEAS_COLLECT_FILE_FORMAT_TYPE) //
            .fileFormatVersion(FILE_FORMAT_VERSION) //
            .changeIdentifier(CHANGE_IDENTIFIER) //
            .build();
    }

    @BeforeAll
    static void setUpConfiguration() {
        when(appConfigMock.getCertificateConfiguration()).thenReturn(certificateConfigMock);
        when(appConfigMock.getCollectedFilesPath()).thenReturn(DATAFILE_TMPDIR);
        certificateConfigMock.keyPasswordPath = CERTIFICATE_KEY_PASSWORD_PATH;
        certificateConfigMock.trustedCa = TRUSTED_CA_PATH;
        certificateConfigMock.trustedCaPasswordPath = TRUSTED_CA_PASSWORD_PATH;
    }

    @BeforeEach
    void setUpTest() {
        counters = new Counters();
    }

    @Test
    public void whenFtpesFile_returnCorrectResponse() throws Exception {
        FileCollector collectorUndetTest = spy(new FileCollector(securityContext, appConfigMock, counters));
        doReturn(ftpesClientMock).when(collectorUndetTest).createFtpesClient(any());

        FileData fileData = createFileData(FTPES_LOCATION_NO_PORT);

        FilePublishInformation expectedfilePublishInformation =
            createExpectedFilePublishInformation(FTPES_LOCATION_NO_PORT);

        StepVerifier.create(collectorUndetTest.collectFile(fileData, 3, Duration.ofSeconds(0)))
            .expectNext(expectedfilePublishInformation) //
            .verifyComplete();

        verify(ftpesClientMock, times(1)).open();
        verify(ftpesClientMock, times(1)).collectFile(REMOTE_FILE_LOCATION, LOCAL_FILE_LOCATION);
        verify(ftpesClientMock, times(1)).close();
        verifyNoMoreInteractions(ftpesClientMock);

        assertEquals(1, counters.getNoOfCollectedFiles(), "collectedFiles should have been 1");
        assertEquals(0, counters.getNoOfFailedFtpAttempts(), "failedFtpAttempts should have been 0");
        assertEquals(0, counters.getNoOfFailedHttpAttempts(), "failedHttpAttempts should have been 0");
    }

    @Test
    public void whenSftpFile_returnCorrectResponse() throws Exception {
        FileCollector collectorUndetTest = spy(new FileCollector(securityContext, appConfigMock, counters));
        doReturn(sftpClientMock).when(collectorUndetTest).createSftpClient(any());

        FileData fileData = createFileData(SFTP_LOCATION_NO_PORT);
        FilePublishInformation expectedfilePublishInformation =
            createExpectedFilePublishInformation(SFTP_LOCATION_NO_PORT);

        StepVerifier.create(collectorUndetTest.collectFile(fileData, 3, Duration.ofSeconds(0)))
            .expectNext(expectedfilePublishInformation) //
            .verifyComplete();

        // The same again, but with port
        fileData = createFileData(SFTP_LOCATION);
        expectedfilePublishInformation = createExpectedFilePublishInformation(SFTP_LOCATION);

        StepVerifier.create(collectorUndetTest.collectFile(fileData, 3, Duration.ofSeconds(0)))
            .expectNext(expectedfilePublishInformation) //
            .verifyComplete();

        verify(sftpClientMock, times(2)).open();
        verify(sftpClientMock, times(2)).collectFile(REMOTE_FILE_LOCATION, LOCAL_FILE_LOCATION);
        verify(sftpClientMock, times(2)).close();
        verifyNoMoreInteractions(sftpClientMock);

        assertEquals(2, counters.getNoOfCollectedFiles(), "collectedFiles should have been 2");
    }

    @Test
    public void whenHttpFile_returnCorrectResponse() throws Exception {
        FileCollector collectorUndetTest = spy(new FileCollector(securityContext, appConfigMock, counters));
        doReturn(dfcHttpClientMock).when(collectorUndetTest).createHttpClient(any());

        FileData fileData = createFileData(HTTP_LOCATION_NO_PORT);

        FilePublishInformation expectedfilePublishInformation =
            createExpectedFilePublishInformation(HTTP_LOCATION_NO_PORT);

        StepVerifier.create(collectorUndetTest.collectFile(fileData, 3, Duration.ofSeconds(0)))
            .expectNext(expectedfilePublishInformation) //
            .verifyComplete();

        // The same again, but with port
        fileData = createFileData(HTTP_LOCATION);
        expectedfilePublishInformation = createExpectedFilePublishInformation(HTTP_LOCATION);

        StepVerifier.create(collectorUndetTest.collectFile(fileData, 3, Duration.ofSeconds(0)))
            .expectNext(expectedfilePublishInformation) //
            .verifyComplete();

        verify(dfcHttpClientMock, times(2)).open();
        verify(dfcHttpClientMock, times(2)).collectFile(REMOTE_FILE_LOCATION, LOCAL_FILE_LOCATION);
        verify(dfcHttpClientMock, times(2)).close();
        verifyNoMoreInteractions(dfcHttpClientMock);

        assertEquals(2, counters.getNoOfCollectedFiles(), "collectedFiles should have been 1");
        assertEquals(0, counters.getNoOfFailedFtpAttempts(), "failedFtpAttempts should have been 0");
        assertEquals(0, counters.getNoOfFailedHttpAttempts(), "failedHttpAttempts should have been 0");
    }

    @Test
    public void whenHttpsFile_returnCorrectResponse() throws Exception {
        FileCollector collectorUndetTest = spy(new FileCollector(securityContext, appConfigMock, counters));
        doReturn(dfcHttpsClientMock).when(collectorUndetTest).createHttpsClient(any());

        FileData fileData = createFileData(HTTPS_LOCATION_NO_PORT);

        FilePublishInformation expectedfilePublishInformation =
            createExpectedFilePublishInformation(HTTPS_LOCATION_NO_PORT);

        StepVerifier.create(collectorUndetTest.collectFile(fileData, 3, Duration.ofSeconds(0)))
            .expectNext(expectedfilePublishInformation) //
            .verifyComplete();

        // The same again, but with port
        fileData = createFileData(HTTPS_LOCATION);
        expectedfilePublishInformation = createExpectedFilePublishInformation(HTTPS_LOCATION);

        StepVerifier.create(collectorUndetTest.collectFile(fileData, 3, Duration.ofSeconds(0)))
            .expectNext(expectedfilePublishInformation) //
            .verifyComplete();

        verify(dfcHttpsClientMock, times(2)).open();
        verify(dfcHttpsClientMock, times(2)).collectFile(REMOTE_FILE_LOCATION, LOCAL_FILE_LOCATION);
        verify(dfcHttpsClientMock, times(2)).close();
        verifyNoMoreInteractions(dfcHttpsClientMock);

        assertEquals(2, counters.getNoOfCollectedFiles(), "collectedFiles should have been 1");
        assertEquals(0, counters.getNoOfFailedFtpAttempts(), "failedFtpAttempts should have been 0");
        assertEquals(0, counters.getNoOfFailedHttpAttempts(), "failedHttpAttempts should have been 0");
    }

    @Test
    public void whenFtpesFileAlwaysFail_retryAndFail() throws Exception {
        FileCollector collectorUndetTest = spy(new FileCollector(securityContext, appConfigMock, counters));
        doReturn(ftpesClientMock).when(collectorUndetTest).createFtpesClient(any());

        FileData fileData = createFileData(FTPES_LOCATION);
        doThrow(new DatafileTaskException("Unable to collect file.")).when(ftpesClientMock)
            .collectFile(REMOTE_FILE_LOCATION, LOCAL_FILE_LOCATION);

        StepVerifier.create(collectorUndetTest.collectFile(fileData, 3, Duration.ofSeconds(0)))
            .expectErrorMessage("Retries exhausted: 3/3") //
            .verify();

        verify(ftpesClientMock, times(4)).collectFile(REMOTE_FILE_LOCATION, LOCAL_FILE_LOCATION);

        assertEquals(0, counters.getNoOfCollectedFiles(), "collectedFiles should have been 0");
        assertEquals(4, counters.getNoOfFailedFtpAttempts(), "failedFtpAttempts should have been 4");
        assertEquals(0, counters.getNoOfFailedHttpAttempts(), "failedHttpAttempts should have been 0");
    }

    @Test
    public void whenFtpesFileAlwaysFail_failWithoutRetry() throws Exception {
        FileCollector collectorUndetTest = spy(new FileCollector(securityContext, appConfigMock, counters));
        doReturn(ftpesClientMock).when(collectorUndetTest).createFtpesClient(any());

        FileData fileData = createFileData(FTPES_LOCATION);
        doThrow(new NonRetryableDatafileTaskException("Unable to collect file.")).when(ftpesClientMock)
            .collectFile(REMOTE_FILE_LOCATION, LOCAL_FILE_LOCATION);

        StepVerifier.create(collectorUndetTest.collectFile(fileData, 3, Duration.ofSeconds(0)))
            .expectErrorMessage("Non retryable file transfer failure") //
            .verify();

        verify(ftpesClientMock, times(1)).collectFile(REMOTE_FILE_LOCATION, LOCAL_FILE_LOCATION);

        assertEquals(0, counters.getNoOfCollectedFiles(), "collectedFiles should have been 0");
        assertEquals(1, counters.getNoOfFailedFtpAttempts(), "failedFtpAttempts should have been 1");
        assertEquals(0, counters.getNoOfFailedHttpAttempts(), "failedHttpAttempts should have been 0");
    }

    @Test
    public void whenFtpesFileFailOnce_retryAndReturnCorrectResponse() throws Exception {
        FileCollector collectorUndetTest = spy(new FileCollector(securityContext, appConfigMock, counters));
        doReturn(ftpesClientMock).when(collectorUndetTest).createFtpesClient(any());
        doThrow(new DatafileTaskException("Unable to collect file.")).doNothing().when(ftpesClientMock)
            .collectFile(REMOTE_FILE_LOCATION, LOCAL_FILE_LOCATION);

        FilePublishInformation expectedfilePublishInformation =
            createExpectedFilePublishInformation(FTPES_LOCATION_NO_PORT);

        FileData fileData = createFileData(FTPES_LOCATION_NO_PORT);

        StepVerifier.create(collectorUndetTest.collectFile(fileData, 3, Duration.ofSeconds(0)))
            .expectNext(expectedfilePublishInformation) //
            .verifyComplete();

        verify(ftpesClientMock, times(2)).collectFile(REMOTE_FILE_LOCATION, LOCAL_FILE_LOCATION);

        assertEquals(1, counters.getNoOfCollectedFiles(), "collectedFiles should have been 1");
        assertEquals(1, counters.getNoOfFailedFtpAttempts(), "failedFtpAttempts should have been 1");
        assertEquals(0, counters.getNoOfFailedHttpAttempts(), "failedHttpAttempts should have been 0");
    }
}
