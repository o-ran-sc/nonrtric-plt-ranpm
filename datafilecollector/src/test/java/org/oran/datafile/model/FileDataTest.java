/*-
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2019-2023 Nordix Foundation.
 *  Copyright (C) 2023-2025 OpenInfra Foundation Europe. All rights reserved.
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
        DefaultFileReadyMessage.MessageMetaData metaData = new DefaultFileReadyMessage.MessageMetaData("sourceName",
                "otherField1", "otherField2", "otherField3", 42, "field5", "field6", "field7", "field8", 123456789L,
                987654321L, "field11", "field12");
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        assertEquals("field8", fileData.sourceName());
    }

    @Test
    void testName() {
        DefaultFileReadyMessage.MessageMetaData metaData = new DefaultFileReadyMessage.MessageMetaData("sourceName",
                "otherField1", "otherField2", "otherField3", 42, "field5", "field6", "field7", "field8", 123456789L,
                987654321L, "field11", "field12");
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        DefaultFileReadyMessage.FileInfo fileInfo = new DefaultFileReadyMessage.FileInfo("name", "location", "hashMapField",
                "");
        DefaultFileReadyMessage.ArrayOfNamedHashMap arrayOfNamedHashMap = new DefaultFileReadyMessage.ArrayOfNamedHashMap(
                "someString", fileInfo);
        fileData.defaultFileInfo = arrayOfNamedHashMap;

        assertEquals("field8/someString", fileData.name());
    }

    @Test
    void testRemoteFilePath() {
        DefaultFileReadyMessage.MessageMetaData metaData = new DefaultFileReadyMessage.MessageMetaData("sourceName",
                "otherField1", "otherField2", "otherField3", 42, "field5", "field6", "field7", "field8", 123456789L,
                987654321L, "field11", "field12");
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        DefaultFileReadyMessage.FileInfo fileInfo = new DefaultFileReadyMessage.FileInfo("name",
                "ftp://example.com/remote/file.txt", "hashMapField", "");
        DefaultFileReadyMessage.ArrayOfNamedHashMap arrayOfNamedHashMap = new DefaultFileReadyMessage.ArrayOfNamedHashMap(
                "someString", fileInfo);
        fileData.defaultFileInfo = arrayOfNamedHashMap;

        assertEquals("/remote/file.txt", fileData.remoteFilePath());
    }

    @Test
    void testScheme() {
        DefaultFileReadyMessage.MessageMetaData metaData = new DefaultFileReadyMessage.MessageMetaData("sourceName",
                "otherField1", "otherField2", "otherField3", 42, "field5", "field6", "field7", "field8", 123456789L,
                987654321L, "field11", "field12");
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        DefaultFileReadyMessage.FileInfo fileInfo = new DefaultFileReadyMessage.FileInfo("name",
                "http://example.com/file.txt", "hashMapField", "");
        DefaultFileReadyMessage.ArrayOfNamedHashMap arrayOfNamedHashMap = new DefaultFileReadyMessage.ArrayOfNamedHashMap(
                "someString", fileInfo);
        fileData.defaultFileInfo = arrayOfNamedHashMap;

        assertEquals(FileData.Scheme.HTTP, fileData.scheme());
    }

    @Test
    void testGetLocalFilePath() {
        AppConfig config = new AppConfig();
        config.setCollectedFilesPath("/local/path");
        DefaultFileReadyMessage.MessageMetaData metaData = new DefaultFileReadyMessage.MessageMetaData("sourceName",
                "otherField1", "otherField2", "otherField3", 42, "field5", "field6", "field7", "field8", 123456789L,
                987654321L, "field11", "field12");
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        DefaultFileReadyMessage.FileInfo fileInfo = new DefaultFileReadyMessage.FileInfo("name",
                "http://example.com/file.txt", "hashMapField", "");
        DefaultFileReadyMessage.ArrayOfNamedHashMap arrayOfNamedHashMap = new DefaultFileReadyMessage.ArrayOfNamedHashMap(
                "someString", fileInfo);
        fileData.defaultFileInfo = arrayOfNamedHashMap;

        Path expectedPath = Paths.get("/local/path/field8/someString");
        Path actualPath = fileData.getLocalFilePath(config);
        assertEquals(expectedPath, actualPath);
    }

    @Test
    void testFileServerDataWithUserInfo() throws Exception {
        // Arrange
        AppConfig config = new AppConfig();
        config.setCollectedFilesPath("/local/path");
        DefaultFileReadyMessage.MessageMetaData metaData = new DefaultFileReadyMessage.MessageMetaData("sourceName",
                "otherField1", "otherField2", "otherField3", 42, "field5", "field6", "field7", "field8", 123456789L,
                987654321L, "field11", "field12");
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        DefaultFileReadyMessage.FileInfo fileInfo = new DefaultFileReadyMessage.FileInfo("name",
                "http://username:password@example.com:8080/path?query1=value1&query2=value2", "hashMapField", "");
        DefaultFileReadyMessage.ArrayOfNamedHashMap arrayOfNamedHashMap = new DefaultFileReadyMessage.ArrayOfNamedHashMap(
                "someString", fileInfo);
        fileData.defaultFileInfo = arrayOfNamedHashMap;

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
        DefaultFileReadyMessage.MessageMetaData metaData = new DefaultFileReadyMessage.MessageMetaData("sourceName",
                "otherField1", "otherField2", "otherField3", 42, "field5", "field6", "field7", "field8", 123456789L,
                987654321L, "field11", "field12");
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        DefaultFileReadyMessage.FileInfo fileInfo = new DefaultFileReadyMessage.FileInfo("name",
                "http://username@example.com:8080/path?query1=value1&query2=value2#rawFragment", "hashMapField", "");
        DefaultFileReadyMessage.ArrayOfNamedHashMap arrayOfNamedHashMap = new DefaultFileReadyMessage.ArrayOfNamedHashMap(
                "someString", fileInfo);
        fileData.defaultFileInfo = arrayOfNamedHashMap;

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
        DefaultFileReadyMessage.MessageMetaData metaData = new DefaultFileReadyMessage.MessageMetaData("sourceName",
                "otherField1", "otherField2", "otherField3", 42, "field5", "field6", "field7", "field8", 123456789L,
                987654321L, "field11", "field12");
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        DefaultFileReadyMessage.FileInfo fileInfo = new DefaultFileReadyMessage.FileInfo("name",
                "http://example.com:8080/path?query1=value1&query2=value2", "hashMapField", "");
        DefaultFileReadyMessage.ArrayOfNamedHashMap arrayOfNamedHashMap = new DefaultFileReadyMessage.ArrayOfNamedHashMap(
                "someString", fileInfo);
        fileData.defaultFileInfo = arrayOfNamedHashMap;

        FileServerData result = fileData.fileServerData();
        assertEquals("example.com", result.getServerAddress());
    }

    @Test
    void testInvalidScheme() throws Exception {
        // Arrange
        AppConfig config = new AppConfig();
        config.setCollectedFilesPath("/local/path");
        DefaultFileReadyMessage.MessageMetaData metaData = new DefaultFileReadyMessage.MessageMetaData("sourceName",
                "otherField1", "otherField2", "otherField3", 42, "field5", "field6", "field7", "field8", 123456789L,
                987654321L, "field11", "field12");
        FileData fileData = FileData.builder().messageMetaData(metaData).build();

        DefaultFileReadyMessage.FileInfo fileInfo = new DefaultFileReadyMessage.FileInfo("name",
                "abcxyz://example.com:8080/path?query1=value1&query2=value2", "hashMapField", "");
        DefaultFileReadyMessage.ArrayOfNamedHashMap arrayOfNamedHashMap = new DefaultFileReadyMessage.ArrayOfNamedHashMap(
                "someString", fileInfo);
        fileData.defaultFileInfo = arrayOfNamedHashMap;

        // Act
        FileData.Scheme result = fileData.scheme();
        assertEquals("FTPES", result.name());
    }

    @Test
    void testCreateFileData() {

        DefaultFileReadyMessage.MessageMetaData metaData = new DefaultFileReadyMessage.MessageMetaData("sourceName",
                "otherField1", "otherField2", "otherField3", 42, "field5", "field6", "field7", "field8", 123456789L,
                987654321L, "field11", "field12");

        DefaultFileReadyMessage defaultFileReadyMessage = DefaultFileReadyMessage.builder()
                .event(DefaultFileReadyMessage.Event.builder().commonEventHeader(metaData).notificationFields(
                        DefaultFileReadyMessage.NotificationFields.builder().notificationFieldsVersion("1.0")
                                .changeType("Add").changeIdentifier("Change123").arrayOfNamedHashMap(
                                        Collections.singletonList(
                                                DefaultFileReadyMessage.ArrayOfNamedHashMap.builder().name("File1").hashMap(
                                                        DefaultFileReadyMessage.FileInfo.builder().fileFormatType("Text")
                                                                .location("ftp://example.com/files/file.txt")
                                                                .fileFormatVersion("1.0").compression("None").build()).build()))
                                .build()).build()).build();

        Iterable<FileData> fileDataIterable = FileData.createFileData(defaultFileReadyMessage, null);
        DefaultFileReadyMessage.MessageMetaData messageMetaData = fileDataIterable.iterator().next().messageMetaData;

        assertEquals("field8", messageMetaData.sourceName);
    }
}

