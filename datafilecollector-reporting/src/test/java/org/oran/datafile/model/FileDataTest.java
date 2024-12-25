/*-
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2019-2023 Nordix Foundation.
 * ================================================================================
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * ============LICENSE_END=========================================================
 */

package org.oran.datafile.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.oran.datafile.configuration.AppConfig;
import org.oran.datafile.exceptions.DatafileTaskException;

class FileDataTest {
    @Test
    void testSchemeGetSchemeFromString() throws DatafileTaskException {
        assertThrows(DatafileTaskException.class, () -> FileData.Scheme.getSchemeFromString("Scheme String"));
        assertEquals(FileData.Scheme.FTPES, FileData.Scheme.getSchemeFromString("FTPES"));
        assertEquals(FileData.Scheme.SFTP, FileData.Scheme.getSchemeFromString("SFTP"));
        assertEquals(FileData.Scheme.HTTP, FileData.Scheme.getSchemeFromString("HTTP"));
        assertEquals(FileData.Scheme.HTTPS, FileData.Scheme.getSchemeFromString("HTTPS"));
    }

    @Test
    void testSchemeIsFtpScheme() {
        assertTrue(FileData.Scheme.isFtpScheme(FileData.Scheme.FTPES));
        assertTrue(FileData.Scheme.isFtpScheme(FileData.Scheme.SFTP));
        assertFalse(FileData.Scheme.isFtpScheme(FileData.Scheme.HTTP));
        assertFalse(FileData.Scheme.isFtpScheme(FileData.Scheme.HTTPS));
    }

    @Test
    void testSourceName() {
	FileReadyMessage.InternalHeaderFields inHeFi= new FileReadyMessage.InternalHeaderFields("CollectorTimeStamp");
	
        FileReadyMessage.MessageMetaData metaData = new FileReadyMessage.MessageMetaData(
            "sourceID", "evnetID", "Priority", "version", "ReportingEntityName ", 42, "Domain", "eventName", "eventType",
            "stndDefinedNamespace", "nfvendorname", "nfNamingCode", "vesEventListenerVersion", "sourceName", 123456789L, 987654321L, "timezoneoffset", "reportEntityID", inHeFi
        );
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        assertEquals("sourceName", fileData.sourceName());
    }

    @Test
    void testName() {
	FileReadyMessage.InternalHeaderFields inHeFi= new FileReadyMessage.InternalHeaderFields("CollectorTimeStamp");
	
        FileReadyMessage.MessageMetaData metaData = new FileReadyMessage.MessageMetaData(
            "sourceID", "evnetID", "Priority", "version", "ReportingEntityName ", 42, "Domain", "eventName", "eventType",
            "stndDefinedNamespace", "nfvendorname", "nfNamingCode", "vesEventListenerVersion", "sourceName", 123456789L, 987654321L, "timezoneoffset", "reportEntityID", inHeFi
        );
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        FileReadyMessage.FileInfo fileInfo = new FileReadyMessage.FileInfo(
	    1550, "someString", "filereadytime",
	    "fileFormat", "fileDataType", "fileexpirationtime", "gzip"
	);
        fileData.fileInfo = fileInfo;

        assertEquals("sourceName/someString", fileData.name());
    }

    @Test
    void testRemoteFilePath() {
	FileReadyMessage.InternalHeaderFields inHeFi= new FileReadyMessage.InternalHeaderFields("CollectorTimeStamp");
	
        FileReadyMessage.MessageMetaData metaData = new FileReadyMessage.MessageMetaData(
            "sourceID", "evnetID", "Priority", "version", "ReportingEntityName ", 42, "Domain", "eventName", "eventType",
            "stndDefinedNamespace", "nfvendorname", "nfNamingCode", "vesEventListenerVersion", "sourceName", 123456789L, 987654321L, "timezoneoffset", "reportEntityID", inHeFi
        );
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        FileReadyMessage.FileInfo fileInfo = new FileReadyMessage.FileInfo(
	    1550, "http://example.com/remote/file.txt", "filereadytime",
	    "fileFormat", "fileDataType", "fileexpirationtime", "gzip"
	);
        fileData.fileInfo = fileInfo;

        assertEquals("/remote/file.txt", fileData.remoteFilePath());
    }

    @Test
    void testScheme() {
	FileReadyMessage.InternalHeaderFields inHeFi= new FileReadyMessage.InternalHeaderFields("CollectorTimeStamp");
	
        FileReadyMessage.MessageMetaData metaData = new FileReadyMessage.MessageMetaData(
            "sourceID", "evnetID", "Priority", "version", "ReportingEntityName ", 42, "Domain", "eventName", "eventType",
            "stndDefinedNamespace", "nfvendorname", "nfNamingCode", "vesEventListenerVersion", "sourceName", 123456789L, 987654321L, "timezoneoffset", "reportEntityID", inHeFi
        );
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        FileReadyMessage.FileInfo fileInfo = new FileReadyMessage.FileInfo(
	    1550, "http://username@example.com:8080/path?query1=value1&query2=value2#rawFragment", "filereadytime",
	    "fileFormat", "fileDataType", "fileexpirationtime", "gzip"
	);
        fileData.fileInfo = fileInfo;

        assertEquals(FileData.Scheme.HTTP, fileData.scheme());
    }

    @Test
    void testGetLocalFilePath() {
        AppConfig config = new AppConfig();
        config.setCollectedFilesPath("/local/path");
	FileReadyMessage.InternalHeaderFields inHeFi= new FileReadyMessage.InternalHeaderFields("CollectorTimeStamp");
	
        FileReadyMessage.MessageMetaData metaData = new FileReadyMessage.MessageMetaData(
            "sourceID", "evnetID", "Priority", "version", "ReportingEntityName ", 42, "Domain", "eventName", "eventType",
            "stndDefinedNamespace", "nfvendorname", "nfNamingCode", "vesEventListenerVersion", "sourceName", 123456789L, 987654321L, "timezoneoffset", "reportEntityID", inHeFi
        );
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        FileReadyMessage.FileInfo fileInfo = new FileReadyMessage.FileInfo(
	    1550, "http://username@example.com:8080/path/someString", "filereadytime",
	    "fileFormat", "fileDataType", "fileexpirationtime", "gzip"
	);
        fileData.fileInfo = fileInfo;

        Path expectedPath = Paths.get("/local/path/sourceName/someString");
        Path actualPath = fileData.getLocalFilePath(config);
        assertEquals(expectedPath, actualPath);
    }

    @Test
    void testFileServerDataWithUserInfo() throws Exception {
        // Arrange
        AppConfig config = new AppConfig();
        config.setCollectedFilesPath("/local/path");
	FileReadyMessage.InternalHeaderFields inHeFi= new FileReadyMessage.InternalHeaderFields("CollectorTimeStamp");
	
        FileReadyMessage.MessageMetaData metaData = new FileReadyMessage.MessageMetaData(
            "sourceID", "evnetID", "Priority", "version", "ReportingEntityName ", 42, "Domain", "eventName", "eventType",
            "stndDefinedNamespace", "nfvendorname", "nfNamingCode", "vesEventListenerVersion", "sourceName", 123456789L, 987654321L, "timezoneoffset", "reportEntityID", inHeFi
        );
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        FileReadyMessage.FileInfo fileInfo = new FileReadyMessage.FileInfo(
	    1550, "http://username:password@example.com:8080/path?query1=value1&query2=value2#rawFragment", "filereadytime",
	    "fileFormat", "fileDataType", "fileexpirationtime", "gzip"
	);
        fileData.fileInfo = fileInfo;

        // Act
        FileServerData result = fileData.fileServerData();

        // Assert
        assertEquals("username", result.userId);
        assertEquals("password", result.password);
    }

    @Test
    void testFileServerDataWithFragment() throws Exception {
        // Arrange
        AppConfig config = new AppConfig();
        config.setCollectedFilesPath("/local/path");
	FileReadyMessage.InternalHeaderFields inHeFi= new FileReadyMessage.InternalHeaderFields("CollectorTimeStamp");
	
        FileReadyMessage.MessageMetaData metaData = new FileReadyMessage.MessageMetaData(
            "sourceID", "evnetID", "Priority", "version", "ReportingEntityName ", 42, "Domain", "eventName", "eventType",
            "stndDefinedNamespace", "nfvendorname", "nfNamingCode", "vesEventListenerVersion", "sourceName", 123456789L, 987654321L, "timezoneoffset", "reportEntityID", inHeFi
        );
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        FileReadyMessage.FileInfo fileInfo = new FileReadyMessage.FileInfo(
	    1550, "http://username@example.com:8080/path?query1=value1&query2=value2#rawFragment", "filereadytime",
	    "fileFormat", "fileDataType", "fileexpirationtime", "gzip"
	);
        //FileReadyMessage.ArrayOfNamedHashMap arrayOfNamedHashMap = new FileReadyMessage.ArrayOfNamedHashMap("someString", fileInfo);
        fileData.fileInfo = fileInfo;

        // Act
        FileServerData result = fileData.fileServerData();

        // Assert
        assertEquals("rawFragment", result.uriRawFragment);
    }

    @Test
    void testFileServerDataWithoutUserInfo() throws Exception {
        // Arrange
        AppConfig config = new AppConfig();
        config.setCollectedFilesPath("/local/path");
	FileReadyMessage.InternalHeaderFields inHeFi= new FileReadyMessage.InternalHeaderFields("CollectorTimeStamp");
	
        FileReadyMessage.MessageMetaData metaData = new FileReadyMessage.MessageMetaData(
            "sourceID", "evnetID", "Priority", "version", "ReportingEntityName ", 42, "Domain", "eventName", "eventType",
            "stndDefinedNamespace", "nfvendorname", "nfNamingCode", "vesEventListenerVersion", "sourceName", 123456789L, 987654321L, "timezoneoffset", "reportEntityID", inHeFi
        );
        FileData fileData = FileData.builder().messageMetaData(metaData).build();
        
	FileReadyMessage.FileInfo fileInfo = new FileReadyMessage.FileInfo(
	    1550, "http://username@example.com:8080/path?query1=value1&query2=value2#rawFragment", "filereadytime",
	    "fileFormat", "fileDataType", "fileexpirationtime", "gzip"
	);

        //FileReadyMessage.ArrayOfNamedHashMap arrayOfNamedHashMap = new FileReadyMessage.ArrayOfNamedHashMap("someString", fileInfo);
        fileData.fileInfo = fileInfo;

        FileServerData result = fileData.fileServerData();
        assertEquals("example.com", result.getServerAddress());
    }

    @Test
    void testInvalidScheme() throws Exception {
        // Arrange
        AppConfig config = new AppConfig();
        config.setCollectedFilesPath("/local/path");
	FileReadyMessage.InternalHeaderFields inHeFi= new FileReadyMessage.InternalHeaderFields("CollectorTimeStamp");
	
        FileReadyMessage.MessageMetaData metaData = new FileReadyMessage.MessageMetaData(
            "sourceID", "evnetID", "Priority", "version", "ReportingEntityName ", 42, "Domain", "eventName", "eventType",
            "stndDefinedNamespace", "nfvendorname", "nfNamingCode", "vesEventListenerVersion", "sourceName", 123456789L, 987654321L, "timezoneoffset", "reportEntityID", inHeFi
        );
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        FileReadyMessage.FileInfo fileInfo = new FileReadyMessage.FileInfo(
	    1550, "abcxyz://username@example.com:8080/path?query1=value1&query2=value2#rawFragment", "filereadytime",
	    "fileFormat", "fileDataType", "fileexpirationtime", "gzip"
	);
        fileData.fileInfo = fileInfo;

        // Act
        FileData.Scheme result = fileData.scheme();
        assertEquals("FTPES", result.name());
    }

    @Test
    void testCreateFileData(){
	FileReadyMessage.InternalHeaderFields inHeFi= new FileReadyMessage.InternalHeaderFields("CollectorTimeStamp");
	
        FileReadyMessage.MessageMetaData metaData = new FileReadyMessage.MessageMetaData(
            "sourceID", "evnetID", "Priority", "version", "ReportingEntityName ", 42, "Domain", "eventName", "eventType",
            "stndDefinedNamespace", "nfvendorname", "nfNamingCode", "vesEventListenerVersion", "sourceName", 123456789L, 987654321L, "timezoneoffset", "reportEntityID", inHeFi
        );

        FileReadyMessage fileReadyMessage = FileReadyMessage.builder()
            .event(
                FileReadyMessage.Event.builder()
                    .commonEventHeader(metaData)
                    .stndDefinedFields(
                        FileReadyMessage.StandardDefinedFields.builder()
                            .stndDefinedFieldsVersion("1.0")
                            .schemaReference("long-url")
                            .data(
				FileReadyMessage.DataFields.builder()
				.systemDN("DN")
				.additionalText("Hey Jude!")
				.eventTime("Time")
				.notificationId(1444)
				.href("Hyper-ref")
				.notificationType("NTYPE")
                                .fileInfoList(
				    Collections.singletonList(
				        FileReadyMessage.FileInfo.builder()
                                         .fileSize(1550)
                                         .fileLocation("ftp://example.com/files/file.txt")
					 .fileReadyTime("Now")
					 .fileFormat("Text")
                                         .fileDataType("Performance")
					 .fileExpirationTime("Then")
                                         .fileCompression("None")
				         .build()
			            )
                                )
				.build()
                        )
                        .build()
                  )
                  .build()
	    )
            .build();

        Iterable<FileData> fileDataIterable = FileData.createFileData(fileReadyMessage);
        FileReadyMessage.MessageMetaData messageMetaData = fileDataIterable.iterator().next().messageMetaData;

        assertEquals("sourceName", messageMetaData.sourceName);
    }
}

