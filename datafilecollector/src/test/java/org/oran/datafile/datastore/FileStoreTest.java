/*-
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2023 Nordix Foundation.
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

package org.oran.datafile.datastore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.oran.datafile.configuration.AppConfig;
import org.springframework.test.context.ContextConfiguration;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ContextConfiguration(classes = { FileStore.class })
@ExtendWith(MockitoExtension.class)
class FileStoreTest {

    @Mock
    private AppConfig appConfig;

    private FileStore fileStore;

    @Mock
    private Path mockPath;

    @BeforeEach
    void setup() {
        MockitoAnnotations.initMocks(this);
        fileStore = new FileStore(appConfig);

        when(appConfig.getCollectedFilesPath()).thenReturn("/path/to/collected/files");
    }

    @Test
    void testListObjects() {
        when(appConfig.getCollectedFilesPath()).thenReturn("Collected Files Path");
        fileStore.listObjects(DataStore.Bucket.FILES, "Prefix");
        verify(appConfig).getCollectedFilesPath();
    }
    @Test
    void testListObjects3() {
        when(appConfig.getCollectedFilesPath()).thenReturn("Collected Files Path");
        fileStore.listObjects(DataStore.Bucket.LOCKS, "Prefix");
        verify(appConfig).getCollectedFilesPath();
    }

    @Test
    void testListObjects_WithExistingFiles() {
        List<Path> fileList = new ArrayList<>();
        fileList.add(Path.of("/path/to/collected/files/file1.txt"));
        fileList.add(Path.of("/path/to/collected/files/file2.txt"));

        when(appConfig.getCollectedFilesPath()).thenReturn("/path/to/collected/files");

        // Mock Files.walk() to return the prepared stream
        try (MockedStatic<Files> filesMockedStatic = mockStatic(Files.class)) {
            filesMockedStatic.when(() -> Files.walk(any(), anyInt())).
                 thenReturn(fileList.stream());

            StepVerifier.create(fileStore.listObjects(DataStore.Bucket.FILES, "")).
                 expectNext("file1.txt").
                 expectNext("file2.txt").
                 expectComplete();
        }
    }
    @Test
    void testReadObject() {
        when(appConfig.getCollectedFilesPath()).thenReturn("Collected Files Path");
        fileStore.readObject(DataStore.Bucket.FILES, "foo.txt");
        verify(appConfig).getCollectedFilesPath();
    }
    @Test
    void testReadObject2() {
        when(appConfig.getCollectedFilesPath()).thenReturn("Collected Files Path");
        fileStore.readObject(DataStore.Bucket.LOCKS, "foo.txt");
        verify(appConfig).getCollectedFilesPath();
    }

    @Test
    void testReadObject_WithExistingFile() {
        byte[] content = "Hello, world!".getBytes();
        Path filePath = Path.of("/path/to/collected/files/test.txt");

        try (MockedStatic<Files> filesMockedStatic = mockStatic(Files.class)) {
            filesMockedStatic.when(() -> Files.readAllBytes(eq(filePath))).
                 thenReturn(content);

            StepVerifier.create(fileStore.readObject(DataStore.Bucket.FILES, "test.txt")).
                 expectNext(content).
                 verifyComplete();
        }
    }
    @Test
    void testCreateLock() {
        when(appConfig.getCollectedFilesPath()).thenReturn("Collected Files Path");
        fileStore.createLock("Name");
        verify(appConfig, atLeast(1)).getCollectedFilesPath();
    }
    @Test
    void testCreateLock3() {
        when(appConfig.getCollectedFilesPath()).thenReturn("");
        fileStore.createLock("/");
        verify(appConfig, atLeast(1)).getCollectedFilesPath();
    }
    @Test
    void testDeleteLock() {
        when(appConfig.getCollectedFilesPath()).thenReturn("Collected Files Path");
        fileStore.deleteLock("Name");
        verify(appConfig).getCollectedFilesPath();
    }
    @Test
    void testDeleteLock2() {
        when(appConfig.getCollectedFilesPath()).thenReturn("");
        fileStore.deleteLock("//");
        verify(appConfig).getCollectedFilesPath();
    }
    @Test
    void testDeleteObject() {
        when(appConfig.getCollectedFilesPath()).thenReturn("Collected Files Path");
        fileStore.deleteObject(DataStore.Bucket.FILES, "Name");
        verify(appConfig).getCollectedFilesPath();
    }
    @Test
    void testDeleteObject2() {
        when(appConfig.getCollectedFilesPath()).thenReturn("Collected Files Path");
        fileStore.deleteObject(DataStore.Bucket.LOCKS, "Name");
        verify(appConfig).getCollectedFilesPath();
    }

    @Test
    void testPath() {
        when(appConfig.getCollectedFilesPath()).thenReturn("Collected Files Path");
        fileStore.path("Name");
        verify(appConfig).getCollectedFilesPath();
    }
    @Test
    void testFileExists() {
        when(appConfig.getCollectedFilesPath()).thenReturn("Collected Files Path");
        fileStore.fileExists(DataStore.Bucket.FILES, "Key");
        verify(appConfig).getCollectedFilesPath();
    }
    @Test
    void testFileExists2() {
        when(appConfig.getCollectedFilesPath()).thenReturn("Collected Files Path");
        fileStore.fileExists(DataStore.Bucket.LOCKS, "Key");
        verify(appConfig).getCollectedFilesPath();
    }
    @Test
    void testDeleteBucket() {
        when(appConfig.getCollectedFilesPath()).thenReturn("Collected Files Path");
        fileStore.deleteBucket(DataStore.Bucket.FILES);
        verify(appConfig).getCollectedFilesPath();
    }
    @Test
    void testDeleteBucket2() throws IOException {
        try (MockedStatic<Files> mockFiles = mockStatic(Files.class)) {
            mockFiles.when(() -> Files.walkFileTree(Mockito.<Path>any(), Mockito.<FileVisitor<Path>>any())).
                 thenReturn(Paths.get(System.getProperty("java.io.tmpdir"), "test.txt"));
            mockFiles.when(() -> Files.exists(Mockito.<Path>any(), (LinkOption[]) any())).thenReturn(true);
            when(appConfig.getCollectedFilesPath()).thenReturn("");
            fileStore.deleteBucket(DataStore.Bucket.LOCKS);
            mockFiles.verify(() -> Files.exists(Mockito.<Path>any(), (LinkOption[]) any()));
            mockFiles.verify(() -> Files.walkFileTree(Mockito.<Path>any(), Mockito.<FileVisitor<Path>>any()));
            verify(appConfig).getCollectedFilesPath();
        }
    }
    @Test
    void testDeleteBucket3() throws IOException {
        try (MockedStatic<Files> mockFiles = mockStatic(Files.class)) {
            mockFiles.when(() -> Files.walkFileTree(Mockito.<Path>any(), Mockito.<FileVisitor<Path>>any())).
                 thenThrow(new IOException("OK"));
            mockFiles.when(() -> Files.exists(Mockito.<Path>any(), (LinkOption[]) any())).thenReturn(true);
            when(appConfig.getCollectedFilesPath()).thenReturn("");
            fileStore.deleteBucket(DataStore.Bucket.LOCKS);
            mockFiles.verify(() -> Files.exists(Mockito.<Path>any(), (LinkOption[]) any()));
            mockFiles.verify(() -> Files.walkFileTree(Mockito.<Path>any(), Mockito.<FileVisitor<Path>>any()));
            verify(appConfig, atLeast(1)).getCollectedFilesPath();
        }
    }

    @Test
    void testCreateLock_Success() throws IOException {
        Path lockPath = Path.of("/path/to/collected/files/locks/lock.txt");

        when(appConfig.getCollectedFilesPath()).thenReturn("/path/to/collected/files");

        try (MockedStatic<Files> filesMockedStatic = mockStatic(Files.class)) {
            filesMockedStatic.when(() -> Files.createDirectories(lockPath.getParent())).
                 thenReturn(lockPath.getParent());

            try (MockedStatic<Path> pathMockedStatic = mockStatic(Path.class)) {
                filesMockedStatic.when(() -> Files.createFile(any(Path.class))).thenReturn(lockPath);

                String name = "test.txt";
                String[] pathComponents = { "collectedFiles", name };

                when(fileStore.path(Arrays.toString(pathComponents))).thenReturn(mockPath);
                Path path = fileStore.path(Arrays.toString(pathComponents));
                assertEquals(mockPath, path);
            }
        }
    }

    @Test
    void testCopyFileTo_Failure() {
        // Define dummy values for testing
        Path from = Paths.get("non-existent-file.txt");
        String to = "destination-folder";

        // Use StepVerifier to test the method
        Mono<String> resultMono = fileStore.copyFileTo(from, to);

        StepVerifier.create(resultMono).
             expectError(IOException.class).
             verify();
    }
}
