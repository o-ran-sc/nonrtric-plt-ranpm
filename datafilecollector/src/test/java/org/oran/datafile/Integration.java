/*-
 * ========================LICENSE_START=================================
 * O-RAN-SC
 * %%
 * Copyright (C) 2020-2023 NordixFoundation
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

package org.oran.datafile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.oran.datafile.configuration.AppConfig;
import org.oran.datafile.datastore.DataStore;
import org.oran.datafile.datastore.DataStore.Bucket;
import org.oran.datafile.model.Counters;
import org.oran.datafile.model.FileData;
import org.oran.datafile.model.FilePublishInformation;
import org.oran.datafile.model.FileReadyMessage;
import org.oran.datafile.model.FileReadyMessage.MessageMetaData;
import org.oran.datafile.oauth2.SecurityContext;
import org.oran.datafile.tasks.CollectAndReportFiles;
import org.oran.datafile.tasks.FileCollector;
import org.oran.datafile.tasks.KafkaTopicListener;
import org.oran.datafile.tasks.KafkaTopicListener.DataFromTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.FileSystemUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@TestPropertySource(
    properties = { //
        "app.ssl.key-store-password-file=./config/ftps_keystore.pass", //
        "app.ssl.key-store=./config/ftps_keystore.p12", //
        "app.ssl.trust-store-password-file=./config/truststore.pass", //
        "app.ssl.trust-store=", // No trust validation
        "app.collected-files-path=/tmp/osc_datafile/", //
        "logging.file.name=/tmp/datafile.log", //
        "spring.main.allow-bean-definition-overriding=true", //
        "app.s3.endpointOverride=http://localhost:9000", //
        "app.s3.accessKeyId=minio", //
        "app.s3.secretAccessKey=miniostorage", //
        "app.s3.bucket=ropfiles", //
        "app.s3.locksBucket=locks", //
        "app.auth-token-file=src/test/resources/jwtToken.b64", //
        "app.kafka.use-oath-token=false"})
@SuppressWarnings("squid:S3577") // Not containing any tests since it is a mock.
class Integration {

    private static final int LAST_EPOCH_MICROSEC = 151983;
    private static final String SOURCE_NAME = "5GRAN_DU";
    private static final int START_EPOCH_MICROSEC = 15198378;
    private static final String TIME_ZONE_OFFSET = "UTC+05:00";
    private static final String PM_FILE_NAME = "PM_FILE_NAME";

    // This can be any downloadable file on the net
    private static final String FTPES_LOCATION =
        "ftpes:// onap:pano@ftp-ftpes-6:2021/A20000626.2315+0200-2330+0200_GNODEB-15-4.xml.gz";
    private static final String LOCATION =
        "https://launchpad.net/ubuntu/+source/perf-tools-unstable/1.0+git7ffb3fd-1ubuntu1/+build/13630748/+files/perf-tools-unstable_1.0+git7ffb3fd-1ubuntu1_all.deb";
    private static final String GZIP_COMPRESSION = "gzip";
    private static final String FILE_FORMAT_TYPE = "org.3GPP.32.435#measCollec";
    private static final String FILE_FORMAT_VERSION = "V10";
    private static final String CHANGE_IDENTIFIER = "PM_MEAS_FILES";
    private static final String CHANGE_TYPE = "FileReady";

    private static final Logger logger = LoggerFactory.getLogger(Integration.class);
    private static Gson gson = new GsonBuilder() //
        .disableHtmlEscaping() //
        .create(); //

    @LocalServerPort
    private int port;

    @Autowired
    AppConfig appConfig;

    @Autowired
    CollectAndReportFiles scheduledTask;

    static final SecurityContext securityContext = new SecurityContext("");

    private static KafkaReceiver kafkaReceiver;

    private static class KafkaReceiver {
        public final String topic;
        private DataFromTopic receivedKafkaOutput;
        private final Logger logger = LoggerFactory.getLogger(Integration.class);

        int count = 0;

        public KafkaReceiver(AppConfig applicationConfig, String outputTopic) {
            this.topic = outputTopic;

            // Create a listener to the output topic. The KafkaTopicListener happens to be
            // suitable for that,
            AppConfig config = spy(applicationConfig);
            when(config.getKafkaClientId()).thenReturn("MockDatafile");
            when(config.getInputTopic()).thenReturn(outputTopic);
            KafkaTopicListener topicListener = new KafkaTopicListener(config);

            topicListener.getFlux() //
                .doOnNext(this::set) //
                .doFinally(sig -> logger.info("Finally " + sig)) //
                .subscribe();
        }

        private void set(DataFromTopic receivedKafkaOutput) {
            this.receivedKafkaOutput = receivedKafkaOutput;
            this.count++;
            logger.debug("*** received {}, {}", topic, receivedKafkaOutput);
        }

        public synchronized String lastKey() {
            return this.receivedKafkaOutput.key;
        }

        public synchronized String lastValue() {
            return this.receivedKafkaOutput.value;
        }

        public void reset() {
            count = 0;
            this.receivedKafkaOutput = new DataFromTopic("", "");
        }
    }

    static class FileCollectorMock extends FileCollector {
        final AppConfig appConfig;

        public FileCollectorMock(AppConfig appConfig) {
            super(securityContext, appConfig, new Counters());
            this.appConfig = appConfig;
        }

        @Override // (override fetchFile to disable the actual file fetching)
        public Mono<FilePublishInformation> collectFile(FileData fileData, long numRetries, Duration firstBackoff) {
            FileCollector fc = new FileCollector(securityContext, this.appConfig, new Counters());
            FilePublishInformation i = fc.createFilePublishInformation(fileData);

            try {
                Path from = Path.of("config/application.yaml");
                Path to = Path.of(appConfig.getCollectedFilesPath(), fileData.name());
                Files.createDirectories(to.getParent());
                Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                logger.error("Could not copy file {}", e.getMessage());
            }
            return Mono.just(i);
        }
    }

    static class CollectAndReportFilesMock extends CollectAndReportFiles {
        final AppConfig appConfig;

        public CollectAndReportFilesMock(AppConfig appConfig) {
            super(securityContext, appConfig);
            this.appConfig = appConfig;
        }

        @Override // (override fetchFile to disable the actual file fetching)
        protected FileCollector createFileCollector() {
            return new FileCollectorMock(appConfig);
        }
    }

    @TestConfiguration
    static class TestBeanFactory {

        @Bean
        CollectAndReportFiles collectAndReportFiles(@Autowired AppConfig appConfig) {
            return new CollectAndReportFilesMock(appConfig);
        }
    }

    @BeforeEach
    void init() {
        if (kafkaReceiver == null) {
            kafkaReceiver = new KafkaReceiver(appConfig, appConfig.getCollectedFileTopic());
        }
        kafkaReceiver.reset();
        deleteAllFiles();
    }

    @AfterEach
    void afterEach() {
        DataStore store = DataStore.create(this.appConfig);
        store.deleteBucket(Bucket.FILES).block();
        store.deleteBucket(Bucket.LOCKS).block();
        deleteAllFiles();

    }

    private void deleteAllFiles() {
        try {
            FileSystemUtils.deleteRecursively(Path.of(appConfig.getCollectedFilesPath()));
        } catch (IOException e) {
        }
    }

    @Test
    void clear() {

    }

    @Test
    void testKafka() throws InterruptedException {
        waitForKafkaListener();

        this.scheduledTask.sendDataToStream(appConfig.getInputTopic(), "key", "junk").blockLast();

        String fileReadyMessage = gson.toJson(fileReadyMessage());
        this.scheduledTask.sendDataToStream(appConfig.getInputTopic(), "key", fileReadyMessage).blockLast();

        await().untilAsserted(() -> assertThat(kafkaReceiver.count).isEqualTo(1));
        String rec = kafkaReceiver.lastValue();

        assertThat(rec).contains("Ericsson");

        FilePublishInformation recObj = gson.fromJson(rec, FilePublishInformation.class);

        assertThat(recObj.getName()).isEqualTo(SOURCE_NAME + "/" + PM_FILE_NAME);
    }

    @Test
    void testS3Concurrency() throws Exception {
        waitForKafkaListener();

        final int NO_OF_OBJECTS = 10;

        Instant startTime = Instant.now();

        Flux.range(1, NO_OF_OBJECTS) //
            .map(i -> gson.toJson(fileReadyMessage("testS3Concurrency_" + i))) //
            .flatMap(
                fileReadyMessage -> scheduledTask.sendDataToStream(appConfig.getInputTopic(), "key", fileReadyMessage)) //
            .blockLast(); //

        while (kafkaReceiver.count < NO_OF_OBJECTS) {
            logger.info("sleeping {}", kafkaReceiver.count);
            Thread.sleep(1000 * 1);
        }

        String rec = kafkaReceiver.lastValue();
        assertThat(rec).contains("Ericsson");

        final long durationSeconds = Instant.now().getEpochSecond() - startTime.getEpochSecond();
        logger.info("*** Duration :" + durationSeconds + ", objects/second: " + NO_OF_OBJECTS / durationSeconds);
    }

    @SuppressWarnings("squid:S2925") // "Thread.sleep" should not be used in tests.
    private static void waitForKafkaListener() throws InterruptedException {
        Thread.sleep(4000);
    }

    @Test
    @SuppressWarnings("squid:S2699")
    void runMock() throws Exception {
        logger.warn("**************** Keeping server alive! " + this.port);
        synchronized (this) {
            this.wait();
        }
    }

    FileReadyMessage.Event event(String fileName) {
        MessageMetaData messageMetaData = MessageMetaData.builder() //
            .lastEpochMicrosec(LAST_EPOCH_MICROSEC) //
            .sourceName(SOURCE_NAME) //
            .startEpochMicrosec(START_EPOCH_MICROSEC) //
            .timeZoneOffset(TIME_ZONE_OFFSET) //
            .changeIdentifier(CHANGE_IDENTIFIER) //
            .eventName("Noti_RnNode-Ericsson_FileReady").build();

        FileReadyMessage.FileInfo fileInfo = FileReadyMessage.FileInfo //
            .builder() //
            .fileFormatType(FILE_FORMAT_TYPE) //
            .location(LOCATION) //
            .fileFormatVersion(FILE_FORMAT_VERSION) //
            .compression(GZIP_COMPRESSION) //
            .build();

        FileReadyMessage.ArrayOfNamedHashMap arrayOfNamedHashMap = FileReadyMessage.ArrayOfNamedHashMap //
            .builder() //
            .name(fileName) //
            .hashMap(fileInfo) //
            .build();

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

    private FileReadyMessage fileReadyMessage(String fileName) {
        FileReadyMessage message = FileReadyMessage.builder() //
            .event(event(fileName)) //
            .build();
        return message;
    }

    private FileReadyMessage fileReadyMessage() {
        return fileReadyMessage(PM_FILE_NAME);
    }

}
