/*-
 * ========================LICENSE_START=================================
 * O-RAN-SC
 * %%
 * Copyright (C) 2023 Nordix Foundation
 * %%
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
 * ========================LICENSE_END===================================
 */

package org.oran.pmproducer.datastore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.oran.pmproducer.configuration.ApplicationConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;


@ExtendWith(MockitoExtension.class)
class S3ObjectStoreTest {

    @Mock
    private ApplicationConfig appConfig;

    @Mock
    private S3AsyncClient s3AsynchClient;

    private S3ObjectStore s3ObjectStore;

    @BeforeEach
    void setup() {
        Mockito.lenient().when(appConfig.getS3EndpointOverride()).thenReturn("https://dummy-s3-bucket.s3.amazonaws.com");
        Mockito.lenient().when(appConfig.getS3AccessKeyId()).thenReturn("test-access-key-id");
        Mockito.lenient().when(appConfig.getS3SecretAccessKey()).thenReturn("test-access-key-secret");

        Mockito.lenient().when(appConfig.getS3Bucket()).thenReturn("test-bucket");
        Mockito.lenient().when(appConfig.getS3LocksBucket()).thenReturn("test-lock-bucket");
        Mockito.lenient().when(appConfig.isS3Enabled()).thenReturn(true);

        s3ObjectStore = new S3ObjectStore(appConfig, s3AsynchClient, true);
    }

    @Test
    void createS3Bucket() {
        CreateBucketRequest request = CreateBucketRequest.builder()
            .bucket("test-bucket")
            .build();

        when(s3AsynchClient.createBucket(any(CreateBucketRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(CreateBucketResponse.builder().build()));

        Mono<String> result = s3ObjectStore.create(DataStore.Bucket.FILES);

        verify(s3AsynchClient, atLeast(1)).createBucket(any(CreateBucketRequest.class));

        StepVerifier.create(result).expectNext("test-bucket").verifyComplete();

        assertThat(result.block()).isEqualTo("test-bucket");
    }

    @Test
    void listObjects() {
        String prefix = "prefix/";

        ListObjectsResponse response1 = ListObjectsResponse.builder()
            .contents(createS3Object("object1"))
            .isTruncated(true)
            .nextMarker("marker1")
            .build();

        ListObjectsResponse response2 = ListObjectsResponse.builder()
            .contents(createS3Object("object2"))
            .isTruncated(false)
            .build();

        when(s3AsynchClient.listObjects(any(ListObjectsRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(response1),
                CompletableFuture.completedFuture(response2));

        Flux<String> result = s3ObjectStore.listObjects(DataStore.Bucket.FILES, prefix);

        verify(s3AsynchClient, atLeast(1)).listObjects(any(ListObjectsRequest.class));

        StepVerifier.create(result)
            .expectNext("object1")
            .expectNext("object2")
            .verifyComplete();

        // Collect the results into a list
        List<String> resultList = result.collectList().block();

        assertEquals(Arrays.asList("object1", "object2"), resultList);
    }

    @Test
    void testCreateLockWithExistingHead() {
        HeadObjectResponse headObjectResponse = HeadObjectResponse.builder().build();

        when(s3AsynchClient.headObject(any(HeadObjectRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(headObjectResponse));

        Mono<Boolean> result = s3ObjectStore.createLock("lockName");

        StepVerifier.create(result)
            .expectNext(false)
            .verifyComplete();

        assertThat(result.block()).isFalse();
    }

    @Test
    void testCreateLockWithoutExistingHead() {
        HeadObjectResponse headObjectResponse = null;
        Mockito.doReturn(CompletableFuture.completedFuture(headObjectResponse))
            .when(s3AsynchClient)
            .headObject(any(HeadObjectRequest.class));

        Mono<Boolean> result = s3ObjectStore.createLock("lockName");

        StepVerifier.create(result)
            .expectComplete()
            .verify();

        Boolean resultVal = result.block();

        assertThat(resultVal).isNull();
    }


    @Test
    void deleteLock() {
        when(s3AsynchClient.deleteObject(any(DeleteObjectRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(DeleteObjectResponse.builder().build()));

        Mono<Boolean> result = s3ObjectStore.deleteLock("lock-name");

        StepVerifier.create(result)
            .expectNext(true)
            .verifyComplete();

        assertThat(result.block()).isTrue();
    }

    @Test
    void testDeleteObject() {
        when(s3AsynchClient.deleteObject(any(DeleteObjectRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(DeleteObjectResponse.builder().build()));

        Mono<Boolean> result = s3ObjectStore.deleteObject(DataStore.Bucket.LOCKS, "objectName");

        StepVerifier.create(result)
            .expectNext(true)
            .verifyComplete();

        assertThat(result.block()).isTrue();
    }

    @Test
    void testDeleteBucket_Success() {
        DeleteBucketRequest request = DeleteBucketRequest.builder() //
            .bucket("test-bucket")
            .build();

        Mockito.lenient().when(s3AsynchClient.deleteBucket(any(DeleteBucketRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(DeleteBucketResponse.builder().build()));

        DeleteObjectsRequest objectRequest = DeleteObjectsRequest.builder() //
            .bucket("test-bucket")
            .build();

        Mockito.lenient().when(s3AsynchClient.deleteObjects(any(DeleteObjectsRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(DeleteObjectsResponse.builder().build()));

        String prefix = "prefix/";

        ListObjectsResponse response1 = ListObjectsResponse.builder()
            .contents(createS3Object("object1"))
            .isTruncated(true)
            .nextMarker("marker1")
            .build();

        ListObjectsResponse response2 = ListObjectsResponse.builder()
            .contents(createS3Object("object2"))
            .isTruncated(false)
            .build();

        when(s3AsynchClient.listObjects(any(ListObjectsRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(response1),
                CompletableFuture.completedFuture(response2));

        Mono<String> result = s3ObjectStore.deleteBucket(DataStore.Bucket.FILES);

        StepVerifier.create(result)
            .expectNext("NOK")
            .verifyComplete();
    }

    @Test
    void testCopyFileTo_Success() throws URISyntaxException {
        PutObjectRequest request = PutObjectRequest.builder() //
            .bucket("test-bucket") //
            .key("test-access-key-id") //
            .build();

        when(s3AsynchClient.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
            .thenAnswer(invocation -> {
                CompletableFuture<PutObjectResponse> future = CompletableFuture.completedFuture(
                    PutObjectResponse.builder().build()
                );
                return future;
            });

        Path testFile = Paths.get(getClass().getResource("/org/oran/pmproducer/datastore/file.txt").toURI());

        Mono<String> result = s3ObjectStore.copyFileTo(testFile, "test-key");

        StepVerifier.create(result)
            .expectNext("test-key")
            .verifyComplete();
    }

    @Test
    void testReadObject() {
        // Mock the getObject method to return a CompletableFuture with ResponseBytes
        when(s3AsynchClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
            .thenAnswer(invocation -> {
                ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
                    GetObjectResponse.builder().build(),
                    "Hello, World!".getBytes(StandardCharsets.UTF_8)
                );
                CompletableFuture<ResponseBytes<GetObjectResponse>> future = CompletableFuture.completedFuture(
                    responseBytes
                );
                return future;
            });

        // Call the method under test
        Mono<byte[]> result = s3ObjectStore.readObject(DataStore.Bucket.FILES, "test-key");

        byte[] expectedBytes = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        StepVerifier.create(result)
            .consumeNextWith(actualBytes -> Assertions.assertArrayEquals(expectedBytes, actualBytes))
            .verifyComplete();
    }

    @Test
    void testPutObject() {
        // Mock the putObject method to return a CompletableFuture with PutObjectResponse
        when(s3AsynchClient.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
            .thenAnswer(invocation -> {
                CompletableFuture<PutObjectResponse> future = CompletableFuture.completedFuture(
                    PutObjectResponse.builder().build()
                );
                return future;
            });

        // Call the method under test
        Mono<String> result = s3ObjectStore.putObject(DataStore.Bucket.FILES, "test-key", "Hello, World!");

        // Verify the Mono's behavior using StepVerifier
        StepVerifier.create(result)
            .expectNext("test-key")
            .verifyComplete();
    }

    private S3Object createS3Object(String key) {
        return S3Object.builder().key(key).build();
    }
}
