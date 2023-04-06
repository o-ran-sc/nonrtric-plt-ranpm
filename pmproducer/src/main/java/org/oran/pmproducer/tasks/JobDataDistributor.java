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

package org.oran.pmproducer.tasks;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import lombok.Getter;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.oran.pmproducer.configuration.ApplicationConfig;
import org.oran.pmproducer.datastore.DataStore;
import org.oran.pmproducer.filter.FilteredData;
import org.oran.pmproducer.filter.PmReportFilter;
import org.oran.pmproducer.repository.Job.Parameters.KafkaDeliveryInfo;
import org.oran.pmproducer.repository.Jobs.JobGroup;
import org.oran.pmproducer.tasks.TopicListener.DataFromTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

/**
 * The class streams data from a multi cast sink and sends the data to the Job
 * owner via REST calls.
 */
@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
public class JobDataDistributor {
    private static final Logger logger = LoggerFactory.getLogger(JobDataDistributor.class);

    @Getter
    private final JobGroup jobGroup;
    private Disposable subscription;
    private final ErrorStats errorStats = new ErrorStats();

    private final DataStore dataStore;
    private static com.google.gson.Gson gson = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
    private final ApplicationConfig applConfig;

    private KafkaSender<byte[], byte[]> sender;

    private class ErrorStats {
        @Getter
        private int consumerFaultCounter = 0;

        public void handleOkFromConsumer() {
            this.consumerFaultCounter = 0;
        }

        public void handleException(Throwable t) {
            ++this.consumerFaultCounter;
        }
    }

    public JobDataDistributor(JobGroup jobGroup, ApplicationConfig applConfig) {
        this.applConfig = applConfig;
        this.jobGroup = jobGroup;
        this.dataStore = DataStore.create(applConfig);
        this.dataStore.create(DataStore.Bucket.FILES).subscribe();
        this.dataStore.create(DataStore.Bucket.LOCKS).subscribe();

        SenderOptions<byte[], byte[]> senderOptions = senderOptions(applConfig, jobGroup.getDeliveryInfo());
        this.sender = KafkaSender.create(senderOptions);

    }

    public void start(Flux<TopicListener.DataFromTopic> input) {
        logger.debug("Starting distribution, to topic: {}", jobGroup.getId());
        PmReportFilter filter = jobGroup.getFilter();
        if (filter == null || filter.getFilterData().getPmRopEndTime() == null) {
            this.subscription = filter(input, this.jobGroup) //
                    .flatMap(this::sendToClient) //
                    .onErrorResume(this::handleError) //
                    .subscribe(this::handleSentOk, //
                            this::handleExceptionInStream, //
                            () -> logger.warn("JobDataDistributor stopped jobId: {}", jobGroup.getId()));
        }

        if (filter != null && filter.getFilterData().getPmRopStartTime() != null) {
            this.dataStore.createLock(collectHistoricalDataLockName()) //
                    .doOnNext(isLockGranted -> {
                        if (isLockGranted.booleanValue()) {
                            logger.debug("Checking historical PM ROP files, jobId: {}", this.jobGroup.getId());
                        } else {
                            logger.debug("Skipping check of historical PM ROP files, already done. jobId: {}",
                                    this.jobGroup.getId());
                        }
                    }) //
                    .filter(isLockGranted -> isLockGranted) //
                    .flatMapMany(b -> Flux.fromIterable(filter.getFilterData().getSourceNames())) //
                    .doOnNext(sourceName -> logger.debug("Checking source name: {}, jobId: {}", sourceName,
                            this.jobGroup.getId())) //
                    .flatMap(sourceName -> dataStore.listObjects(DataStore.Bucket.FILES, sourceName), 1) //
                    .filter(this::isRopFile) //
                    .filter(fileName -> filterStartTime(filter.getFilterData(), fileName)) //
                    .filter(fileName -> filterEndTime(filter.getFilterData(), fileName)) //
                    .map(this::createFakeEvent) //
                    .flatMap(data -> TopicListener.getDataFromFileIfNewPmFileEvent(data, this.jobGroup.getType(),
                            dataStore), 100)
                    .map(jobGroup::filter) //
                    .map(this::gzip) //
                    .flatMap(this::sendToClient, 1) //
                    .onErrorResume(this::handleCollectHistoricalDataError) //
                    .doFinally(sig -> sendLastStoredRecord()) //
                    .subscribe();
        }
    }

    private static SenderOptions<byte[], byte[]> senderOptions(ApplicationConfig config,
            KafkaDeliveryInfo deliveryInfo) {

        String bootstrapServers = deliveryInfo.getBootStrapServers();
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            bootstrapServers = config.getKafkaBootStrapServers();
        }

        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        config.addKafkaSecurityProps(props);

        return SenderOptions.create(props);
    }

    private void sendLastStoredRecord() {
        String data = "{}";
        FilteredData output = new FilteredData("", this.jobGroup.getType().getId(), null, data.getBytes());

        sendToClient(output).subscribe();
    }

    private FilteredData gzip(FilteredData data) {
        if (this.applConfig.isZipOutput()) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                GZIPOutputStream gzip = new GZIPOutputStream(out);
                gzip.write(data.value);
                gzip.flush();
                gzip.close();
                byte[] zipped = out.toByteArray();
                return new FilteredData(data.getSourceName(), data.infoTypeId, data.key, zipped, true);
            } catch (IOException e) {
                logger.error("Unexpected exception when zipping: {}", e.getMessage());
                return data;
            }
        } else {
            return data;
        }
    }

    private Mono<String> handleCollectHistoricalDataError(Throwable t) {
        logger.error("Exception: {} job: {}", t.getMessage(), jobGroup.getId());
        return tryDeleteLockFile() //
                .map(bool -> "OK");
    }

    private String collectHistoricalDataLockName() {
        return "collectHistoricalDataLock" + this.jobGroup.getId();
    }

    private DataFromTopic createFakeEvent(String fileName) {
        NewFileEvent ev = new NewFileEvent(fileName);
        return new DataFromTopic(this.jobGroup.getType().getId(), null, null, gson.toJson(ev).getBytes());
    }

    private static String fileTimePartFromRopFileName(String fileName) {
        // "O-DU-1122/A20000626.2315+0200-2330+0200_HTTPS-6-73.json"
        return fileName.substring(fileName.lastIndexOf("/") + 2);
    }

    private static boolean filterStartTime(PmReportFilter.FilterData filter, String fileName) {
        try {
            OffsetDateTime fileStartTime = getStartTimeFromFileName(fileName);
            OffsetDateTime startTime = OffsetDateTime.parse(filter.getPmRopStartTime());
            boolean isMatch = fileStartTime.isAfter(startTime);
            logger.debug("Checking file: {}, fileStartTime: {}, filterStartTime: {}, isAfter: {}", fileName,
                    fileStartTime, startTime, isMatch);
            return isMatch;
        } catch (Exception e) {
            logger.warn("Time parsing exception: {}", e.getMessage());
            return false;
        }
    }

    private boolean isRopFile(String fileName) {
        return fileName.endsWith(".json") || fileName.endsWith(".json.gz");
    }

    private static boolean filterEndTime(PmReportFilter.FilterData filter, String fileName) {
        if (filter.getPmRopEndTime() == null) {
            return true;
        }
        try {
            OffsetDateTime fileEndTime = getEndTimeFromFileName(fileName);
            OffsetDateTime endTime = OffsetDateTime.parse(filter.getPmRopEndTime());
            boolean isMatch = fileEndTime.isBefore(endTime);
            logger.debug("Checking file: {}, fileEndTime: {}, endTime: {}, isBefore: {}", fileName, fileEndTime,
                    endTime, isMatch);
            return isMatch;

        } catch (Exception e) {
            logger.warn("Time parsing exception: {}", e.getMessage());
            return false;
        }
    }

    private static OffsetDateTime getStartTimeFromFileName(String fileName) {
        String fileTimePart = fileTimePartFromRopFileName(fileName);
        // A20000626.2315+0200-2330+0200_HTTPS-6-73.json
        fileTimePart = fileTimePart.substring(0, 18);
        return parseFileDate(fileTimePart);
    }

    private static OffsetDateTime getEndTimeFromFileName(String fileName) {
        String fileTimePart = fileTimePartFromRopFileName(fileName);
        // A20000626.2315+0200-2330+0200_HTTPS-6-73.json
        fileTimePart = fileTimePart.substring(0, 9) + fileTimePart.substring(19, 28);
        return parseFileDate(fileTimePart);
    }

    private static OffsetDateTime parseFileDate(String timeStr) {
        DateTimeFormatter startTimeFormatter =
                new DateTimeFormatterBuilder().appendPattern("yyyyMMdd.HHmmZ").toFormatter();
        return OffsetDateTime.parse(timeStr, startTimeFormatter);
    }

    private void handleExceptionInStream(Throwable t) {
        logger.warn("JobDataDistributor exception: {}, jobId: {}", t.getMessage(), jobGroup.getId());
    }

    public Mono<String> sendToClient(FilteredData data) {

        SenderRecord<byte[], byte[], Integer> senderRecord = senderRecord(data, this.getJobGroup().getDeliveryInfo());

        logger.trace("Sending data '{}' to Kafka topic: {}", StringUtils.truncate(data.getValueAString(), 10),
                this.getJobGroup().getDeliveryInfo());

        return this.sender.send(Mono.just(senderRecord)) //
                .doOnNext(n -> logger.debug("Sent data to Kafka topic: {}", this.getJobGroup().getDeliveryInfo())) //
                .doOnError(t -> logger.warn("Failed to send to Kafka, job: {}, reason: {}", this.getJobGroup().getId(),
                        t.getMessage())) //
                .onErrorResume(t -> Mono.empty()) //
                .collectList() //
                .map(x -> "ok");

    }

    public synchronized void stop() {
        if (this.subscription != null) {
            logger.debug("Stopped, job: {}", jobGroup.getId());
            this.subscription.dispose();
            this.subscription = null;
        }
        if (sender != null) {
            sender.close();
            sender = null;
        }

        tryDeleteLockFile().subscribe();
    }

    private Mono<Boolean> tryDeleteLockFile() {
        return dataStore.deleteLock(collectHistoricalDataLockName()) //
                .doOnNext(res -> logger.debug("Removed lockfile {} {}", collectHistoricalDataLockName(), res))
                .onErrorResume(t -> Mono.just(false));
    }

    public synchronized boolean isRunning() {
        return this.subscription != null;
    }

    private SenderRecord<byte[], byte[], Integer> senderRecord(FilteredData output, KafkaDeliveryInfo deliveryInfo) {
        int correlationMetadata = 2;
        var producerRecord =
                new ProducerRecord<>(deliveryInfo.getTopic(), null, null, output.key, output.value, output.headers());
        return SenderRecord.create(producerRecord, correlationMetadata);
    }

    private Flux<FilteredData> filter(Flux<DataFromTopic> inputFlux, JobGroup jobGroup) {
        return inputFlux.doOnNext(data -> logger.trace("Received data, job {}", jobGroup.getId())) //
                .doOnNext(data -> jobGroup.getJobs().forEach(job -> job.getStatistics().received(data.value))) //
                .map(jobGroup::filter) //
                .filter(f -> !f.isEmpty()) //
                .map(this::gzip) //
                .doOnNext(f -> jobGroup.getJobs().forEach(job -> job.getStatistics().filtered(f.value))) //
                .doOnNext(data -> logger.trace("Filtered data, job {}", jobGroup.getId())) //
        ; //
    }

    private Mono<String> handleError(Throwable t) {
        logger.warn("exception: {} job: {}", t.getMessage(), jobGroup.getId());
        this.errorStats.handleException(t);
        return Mono.empty(); // Ignore
    }

    private void handleSentOk(String data) {
        this.errorStats.handleOkFromConsumer();
    }

}
