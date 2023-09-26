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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.oran.datafile.configuration.AppConfig;

@ExtendWith(MockitoExtension.class)
class DataStoreTest {

    @Mock
    private AppConfig mockAppConfig;

    @Test
    void testCreateWithS3Enabled() {
        when(mockAppConfig.isS3Enabled()).thenReturn(true);
        when(mockAppConfig.getS3EndpointOverride()).thenReturn("https://dummy-s3-bucket.s3.amazonaws.com");
        when(mockAppConfig.getS3AccessKeyId()).thenReturn("test-access-key-id");
        when(mockAppConfig.getS3SecretAccessKey()).thenReturn("test-access-key-secret");
        DataStore dataStore = DataStore.create(mockAppConfig);
        assertTrue(dataStore instanceof S3ObjectStore);
    }

    @Test
    void testCreateWithS3Disabled() {
        when(mockAppConfig.isS3Enabled()).thenReturn(false);
        DataStore dataStore = DataStore.create(mockAppConfig);
        assertTrue(dataStore instanceof FileStore);
    }
}
