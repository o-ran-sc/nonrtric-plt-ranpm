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

import java.nio.file.Path;

import org.oran.pmproducer.configuration.ApplicationConfig;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DataStore {
    public enum Bucket {
        FILES, LOCKS
    }

    public Flux<String> listObjects(Bucket bucket, String prefix);

    public Mono<byte[]> readObject(Bucket bucket, String name);

    public Mono<Boolean> createLock(String name);

    public Mono<Boolean> deleteLock(String name);

    public Mono<Boolean> deleteObject(Bucket bucket, String name);

    public Mono<String> copyFileTo(Path from, String to);

    public Mono<String> create(DataStore.Bucket bucket);

    public Mono<String> deleteBucket(Bucket bucket);

    public static DataStore create(ApplicationConfig config) {
        return config.isS3Enabled() ? new S3ObjectStore(config) : new FileStore(config);
    }

}
