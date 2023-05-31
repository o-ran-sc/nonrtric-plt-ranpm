/*-
 * ============LICENSE_START======================================================================
 * Copyright (C) 2018, 2020 NOKIA Intellectual Property, 2018-2023 Nordix Foundation. All rights reserved.
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;
import org.oran.datafile.configuration.AppConfig;
import org.oran.datafile.configuration.CertificateConfig;
import org.oran.datafile.datastore.DataStore;
import org.oran.datafile.datastore.DataStore.Bucket;
import org.oran.datafile.exceptions.DatafileTaskException;
import org.oran.datafile.http.HttpsClientConnectionManagerUtil;
import org.oran.datafile.model.Counters;
import org.oran.datafile.model.FileData;
import org.oran.datafile.model.FilePublishInformation;
import org.oran.datafile.model.FileReadyMessage;
import org.oran.datafile.oauth2.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import reactor.util.retry.Retry;

/**
 * This implements the main flow of the data file collector. Fetch file ready
 * events from the
 * message router, fetch new files from the PNF publish these in the data
 * router.
 */
@Component
public class CollectAndReportFiles {

    private static Gson gson = new GsonBuilder() //
        .disableHtmlEscaping() //
        .create(); //

    private static final long FILE_TRANSFER_MAX_RETRIES = 2;
    private static final Duration FILE_TRANSFER_INITIAL_RETRY_TIMEOUT = Duration.ofSeconds(2);

    private static final Logger logger = LoggerFactory.getLogger(CollectAndReportFiles.class);

    private final AppConfig appConfig;

    private Counters counters = new Counters();

    private final KafkaSender<String, String> kafkaSender;

    private final DataStore dataStore;

    private final SecurityContext securityContext;

    /**
     * Constructor for task registration in Datafile Workflow.
     *
     * @param applicationConfiguration - application configuration
     */
    public CollectAndReportFiles(SecurityContext securityContext, AppConfig applicationConfiguration) {
        this.appConfig = applicationConfiguration;
        this.kafkaSender = KafkaSender.create(kafkaSenderOptions());
        this.securityContext = securityContext;
        initCerts();

        this.dataStore = DataStore.create(applicationConfiguration);

        start();
    }

    private void initCerts() {
        try {
            CertificateConfig certificateConfig = appConfig.getCertificateConfiguration();
            HttpsClientConnectionManagerUtil.setupOrUpdate(certificateConfig.keyCert, certificateConfig.keyPasswordPath,
                certificateConfig.trustedCa, certificateConfig.trustedCaPasswordPath, true);
        } catch (DatafileTaskException e) {
            logger.error("Could not setup HttpsClient certs, reason: {}", e.getMessage());
        }
    }

    /**
     * Main function for scheduling for the file collection Workflow.
     */
    public void start() {
        start(0);
    }

    private void start(int delayMillis) {
        try {
            logger.trace("Starting");
            if (appConfig.isS3Enabled()) {
                this.dataStore.create(Bucket.FILES).subscribe();
                this.dataStore.create(Bucket.LOCKS).subscribe();
            }
            Thread.sleep(delayMillis);
            createMainTask().subscribe(null, s -> start(2000), null);
        } catch (Exception e) {
            logger.error("Unexpected exception: {}", e.toString(), e);
            Thread.currentThread().interrupt();
        }
    }

    Flux<FilePublishInformation> createMainTask() {
        final int noOfWorkerThreads = appConfig.getNoOfWorkerThreads();
        Scheduler scheduler = Schedulers.newParallel("FileCollectorWorker", noOfWorkerThreads);
        return fetchFromKafka() //
            .doOnNext(fileReadyMessage -> counters.threadPoolQueueSize.incrementAndGet()) //
            .doOnNext(fileReadyMessage -> counters.incNoOfReceivedEvents()) //
            .parallel(noOfWorkerThreads) // Each FileReadyMessage in a separate thread
            .runOn(scheduler) //
            .doOnNext(fileReadyMessage -> counters.threadPoolQueueSize.decrementAndGet()) //
            .flatMap(fileReadyMessage -> Flux.fromIterable(FileData.createFileData(fileReadyMessage)), true, 1) //
            .flatMap(this::filterNotFetched, false, 1, 1) //
            .flatMap(this::fetchFile, false, 1, 1) //
            .flatMap(data -> reportFetchedFile(data, appConfig.getCollectedFileTopic()), false, 1) //
            .sequential() //
            .doOnError(t -> logger.error("Received error: {}", t.toString())); //
    }

    private Mono<FileData> deleteLock(FileData info) {
        return dataStore.deleteLock(lockName(info.name())).map(b -> info); //
    }

    private Mono<FilePublishInformation> moveFileToS3Bucket(FilePublishInformation info) {
        if (this.appConfig.isS3Enabled()) {
            return dataStore.copyFileTo(locaFilePath(info), info.getName())
                .doOnError(t -> logger.warn("Failed to store file '{}' in S3 {}", info.getName(), t.getMessage())) //
                .retryWhen(Retry.backoff(4, Duration.ofMillis(1000))) //
                .map(f -> info) //
                .doOnError(t -> logger.error("Failed to store file '{}' in S3 after retries {}", info.getName(),
                    t.getMessage())) //
                .doOnNext(n -> logger.debug("Stored file in S3: {}", info.getName())) //
                .doOnNext(sig -> deleteLocalFile(info));
        } else {
            return Mono.just(info);
        }
    }

    private Mono<FileData> filterNotFetched(FileData fileData) {
        Path localPath = fileData.getLocalFilePath(this.appConfig);

        return dataStore.fileExists(Bucket.FILES, fileData.name()) //
            .filter(exists -> !exists) //
            .filter(exists -> !localPath.toFile().exists()) //
            .map(f -> fileData); //

    }

    private String lockName(String fileName) {
        return fileName + ".lck";
    }

    private Path locaFilePath(FilePublishInformation info) {
        return Paths.get(appConfig.getCollectedFilesPath(), info.getName());
    }

    private void deleteLocalFile(FilePublishInformation info) {
        Path path = locaFilePath(info);
        try {
            Files.delete(path);
        } catch (Exception e) {
            logger.warn("Could not delete local file: {}, reason:{}", path, e.getMessage());
        }
    }

    private Flux<FilePublishInformation> reportFetchedFile(FilePublishInformation fileData, String topic) {
        String json = gson.toJson(fileData);
        return sendDataToStream(topic, fileData.getSourceName(), json) //
            .map(result -> fileData);
    }

    public Flux<SenderResult<Integer>> sendDataToStream(String topic, String sourceName, String value) {
        return sendDataToKafkaStream(Flux.just(senderRecord(topic, sourceName, value)));
    }

    private SenderRecord<String, String, Integer> senderRecord(String topic, String sourceName, String value) {
        int correlationMetadata = 2;
        String key = null;
        var producerRecord = new ProducerRecord<>(topic, null, null, key, value, kafkaHeaders(sourceName));
        return SenderRecord.create(producerRecord, correlationMetadata);
    }

    private Iterable<Header> kafkaHeaders(String sourceName) {
        ArrayList<Header> result = new ArrayList<>();
        Header h = new RecordHeader("SourceName", sourceName.getBytes());
        result.add(h);
        return result;
    }

    private Flux<SenderResult<Integer>> sendDataToKafkaStream(Flux<SenderRecord<String, String, Integer>> dataToSend) {
        return kafkaSender.send(dataToSend) //
            .doOnError(e -> logger.error("Send to kafka failed", e));
    }

    private SenderOptions<String, String> kafkaSenderOptions() {
        String bootstrapServers = this.appConfig.getKafkaBootStrapServers();

        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        this.appConfig.addKafkaSecurityProps(props);
        return SenderOptions.create(props);
    }

    public Counters getCounters() {
        return this.counters;
    }

    protected FileCollector createFileCollector() {
        return new FileCollector(securityContext, appConfig, counters);
    }

    private Mono<FilePublishInformation> fetchFile(FileData fileData) {
        return this.dataStore.createLock(lockName(fileData.name())).filter(granted -> granted) //
            .map(granted -> createFileCollector()) //
            .flatMap(collector -> collector.collectFile(fileData, FILE_TRANSFER_MAX_RETRIES,
                FILE_TRANSFER_INITIAL_RETRY_TIMEOUT)) //
            .flatMap(this::moveFileToS3Bucket) //
            .doOnNext(b -> deleteLock(fileData).subscribe()) //
            .doOnError(b -> deleteLock(fileData).subscribe()) //
            .onErrorResume(exception -> handleFetchFileFailure(fileData, exception)); //
    }

    private Mono<FilePublishInformation> handleFetchFileFailure(FileData fileData, Throwable t) {
        Path localFilePath = fileData.getLocalFilePath(this.appConfig);
        logger.error("File fetching failed, path {}, reason: {}", fileData.remoteFilePath(), t.getMessage());
        deleteFile(localFilePath);
        if (FileData.Scheme.isFtpScheme(fileData.scheme())) {
            counters.incNoOfFailedFtp();
        } else {
            counters.incNoOfFailedHttp();
        }
        return Mono.empty();
    }

    /**
     * Fetch more messages from the message router. This is done in a
     * polling/blocking fashion.
     */
    private Flux<FileReadyMessage> fetchFromKafka() {
        KafkaTopicListener listener = new KafkaTopicListener(this.appConfig);
        return listener.getFlux() //
            .flatMap(this::parseReceivedFileReadyMessage, 1);

    }

    Mono<FileReadyMessage> parseReceivedFileReadyMessage(KafkaTopicListener.DataFromTopic data) {
        try {
            FileReadyMessage msg = gson.fromJson(data.value, FileReadyMessage.class);
            logger.debug("Received: {}", msg);
            return Mono.just(msg);
        } catch (Exception e) {
            logger.warn("Could not parse received: {}, reason: {}", data.value, e.getMessage());
            return Mono.empty();
        }
    }

    private static void deleteFile(Path localFile) {
        logger.trace("Deleting file: {}", localFile);
        try {
            Files.delete(localFile);
        } catch (Exception e) {
            logger.trace("Could not delete file: {}, reason: {}", localFile, e.getMessage());
        }
    }
}
