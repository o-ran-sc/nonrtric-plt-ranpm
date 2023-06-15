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

package org.oran.pmproducer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import lombok.Builder;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.oran.pmproducer.clients.AsyncRestClient;
import org.oran.pmproducer.clients.AsyncRestClientFactory;
import org.oran.pmproducer.configuration.ApplicationConfig;
import org.oran.pmproducer.configuration.WebClientConfig;
import org.oran.pmproducer.configuration.WebClientConfig.HttpProxyConfig;
import org.oran.pmproducer.controllers.ProducerCallbacksController;
import org.oran.pmproducer.controllers.ProducerCallbacksController.StatisticsCollection;
import org.oran.pmproducer.datastore.DataStore;
import org.oran.pmproducer.filter.PmReportFilter;
import org.oran.pmproducer.oauth2.SecurityContext;
import org.oran.pmproducer.r1.ConsumerJobInfo;
import org.oran.pmproducer.repository.InfoType;
import org.oran.pmproducer.repository.InfoTypes;
import org.oran.pmproducer.repository.Job;
import org.oran.pmproducer.repository.Job.Statistics;
import org.oran.pmproducer.repository.Jobs;
import org.oran.pmproducer.tasks.NewFileEvent;
import org.oran.pmproducer.tasks.TopicListener;
import org.oran.pmproducer.tasks.TopicListeners;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

import reactor.core.publisher.Flux;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

@SuppressWarnings("java:S3577") // Rename class
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = { //
        "server.ssl.key-store=./config/keystore.jks", //
        "app.webclient.trust-store=./config/truststore.jks", //
        "app.configuration-filepath=./src/test/resources/test_application_configuration.json", //
        "app.pm-files-path=./src/test/resources/", //
        "app.s3.locksBucket=ropfilelocks", //
        "app.pm-files-path=/tmp/pmproducer", //
        "app.s3.bucket=pmproducertest", //
        "app.auth-token-file=src/test/resources/jwtToken.b64", //
        "app.kafka.use-oath-token=false"}) //
/**
 * Tests interwork with Kafka and Minio
 * Requires that Kafka and ICS is started.
 */
class IntegrationWithKafka {

    final static String PM_TYPE_ID = "PmDataOverKafka";

    @Autowired
    private ApplicationConfig applicationConfig;

    @Autowired
    private Jobs jobs;

    @Autowired
    private InfoTypes types;

    @Autowired
    private IcsSimulatorController icsSimulatorController;

    @Autowired
    private TopicListeners topicListeners;

    @Autowired
    private SecurityContext securityContext;

    private static com.google.gson.Gson gson = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    private final Logger logger = LoggerFactory.getLogger(IntegrationWithKafka.class);

    @LocalServerPort
    int localServerHttpPort;

    static class TestApplicationConfig extends ApplicationConfig {
        @Override
        public String getIcsBaseUrl() {
            return thisProcessUrl();
        }

        @Override
        public String getSelfUrl() {
            return thisProcessUrl();
        }

        private String thisProcessUrl() {
            final String url = "https://localhost:" + getLocalServerHttpPort();
            return url;
        }
    }

    /**
     * Overrides the BeanFactory.
     */
    @TestConfiguration
    static class TestBeanFactory extends BeanFactory {

        @Override
        @Bean
        public ServletWebServerFactory servletContainer() {
            return new TomcatServletWebServerFactory();
        }

        @Override
        @Bean
        public ApplicationConfig getApplicationConfig() {
            TestApplicationConfig cfg = new TestApplicationConfig();
            return cfg;
        }
    }

    private static class KafkaReceiver {
        public final String OUTPUT_TOPIC;
        private TopicListener.DataFromTopic receivedKafkaOutput;
        private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
        private final ApplicationConfig applicationConfig;

        int count = 0;

        public KafkaReceiver(ApplicationConfig applicationConfig, String outputTopic, SecurityContext securityContext,
                String groupId) {
            this.applicationConfig = applicationConfig;
            this.OUTPUT_TOPIC = outputTopic;

            // Create a listener to the output topic. The TopicListener happens to be
            // suitable for that,
            InfoType type = InfoType.builder() //
                    .id("TestReceiver_" + outputTopic) //
                    .kafkaInputTopic(OUTPUT_TOPIC) //
                    .build();

            TopicListener topicListener = new TopicListener(applicationConfig, type);
            if (groupId != null) {
                topicListener.setKafkaGroupId(groupId);
            }

            topicListener.getFlux() //
                    .map(this::unzip) //
                    .doOnNext(this::set) //
                    .doFinally(sig -> logger.info("Finally " + sig)) //
                    .subscribe();
        }

        private TopicListener.DataFromTopic unzip(TopicListener.DataFromTopic receivedKafkaOutput) {
            if (this.applicationConfig.isZipOutput() != receivedKafkaOutput.isZipped()) {
                logger.error("********* ERROR received zipped: {}, exp zipped: {}", receivedKafkaOutput.isZipped(),
                        this.applicationConfig.isZipOutput());
            }

            if (!receivedKafkaOutput.isZipped()) {
                return receivedKafkaOutput;
            }
            try {
                byte[] unzipped = TopicListener.unzip(receivedKafkaOutput.value);
                return new TopicListener.DataFromTopic("typeId", null, unzipped, receivedKafkaOutput.key);
            } catch (IOException e) {
                logger.error("********* ERROR ", e.getMessage());
                return null;
            }
        }

        private void set(TopicListener.DataFromTopic receivedKafkaOutput) {
            this.receivedKafkaOutput = receivedKafkaOutput;
            this.count++;
            if (logger.isDebugEnabled()) {
                logger.debug("*** received data on topic: {}", OUTPUT_TOPIC);
                logger.debug("*** received typeId: {}", receivedKafkaOutput.getTypeIdFromHeaders());
                logger.debug("*** received sourceName: {}", receivedKafkaOutput.getSourceNameFromHeaders());
            }
        }

        void reset() {
            this.receivedKafkaOutput = new TopicListener.DataFromTopic("", null, null, null);
            this.count = 0;
        }
    }

    private static KafkaReceiver kafkaReceiver;
    private static KafkaReceiver kafkaReceiver2;

    @BeforeEach
    void init() {
        this.applicationConfig.setZipOutput(false);

        if (kafkaReceiver == null) {
            kafkaReceiver = new KafkaReceiver(this.applicationConfig, "ouputTopic", this.securityContext, null);
            kafkaReceiver2 = new KafkaReceiver(this.applicationConfig, "ouputTopic2", this.securityContext, null);
        }
        kafkaReceiver.reset();
        kafkaReceiver2.reset();

        DataStore fileStore = this.dataStore();
        fileStore.create(DataStore.Bucket.FILES).block();
        fileStore.create(DataStore.Bucket.LOCKS).block();

    }

    @AfterEach
    void reset() {
        for (Job job : this.jobs.getAll()) {
            this.icsSimulatorController.deleteJob(job.getId(), restClient());
        }
        await().untilAsserted(() -> assertThat(this.jobs.size()).isZero());
        await().untilAsserted(() -> assertThat(this.topicListeners.getDataDistributors().keySet()).isEmpty());

        this.icsSimulatorController.testResults.reset();

        DataStore fileStore = dataStore();
        fileStore.deleteBucket(DataStore.Bucket.FILES).block();
        fileStore.deleteBucket(DataStore.Bucket.LOCKS).block();
    }

    private AsyncRestClient restClient(boolean useTrustValidation) {
        WebClientConfig config = this.applicationConfig.getWebClientConfig();
        HttpProxyConfig httpProxyConfig = HttpProxyConfig.builder() //
                .httpProxyHost("") //
                .httpProxyPort(0) //
                .build();
        config = WebClientConfig.builder() //
                .keyStoreType(config.getKeyStoreType()) //
                .keyStorePassword(config.getKeyStorePassword()) //
                .keyStore(config.getKeyStore()) //
                .keyPassword(config.getKeyPassword()) //
                .isTrustStoreUsed(useTrustValidation) //
                .trustStore(config.getTrustStore()) //
                .trustStorePassword(config.getTrustStorePassword()) //
                .httpProxyConfig(httpProxyConfig).build();

        AsyncRestClientFactory restClientFactory = new AsyncRestClientFactory(config, securityContext);
        return restClientFactory.createRestClientNoHttpProxy(baseUrl());
    }

    private AsyncRestClient restClient() {
        return restClient(false);
    }

    private String baseUrl() {
        return "https://localhost:" + this.applicationConfig.getLocalServerHttpPort();
    }

    private static Object jsonObject(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new NullPointerException(e.toString());
        }
    }

    public static ConsumerJobInfo consumerJobInfoKafka(String kafkaBootstrapServers, String topic,
            PmReportFilter.FilterData filterData) {
        try {
            Job.Parameters.KafkaDeliveryInfo deliveryInfo = Job.Parameters.KafkaDeliveryInfo.builder() //
                    .topic(topic) //
                    .bootStrapServers(kafkaBootstrapServers) //
                    .build();
            Job.Parameters param = Job.Parameters.builder() //
                    .filter(filterData) //
                    .deliveryInfo(deliveryInfo) //
                    .build();

            String str = gson.toJson(param);
            Object parametersObj = jsonObject(str);

            return new ConsumerJobInfo(PM_TYPE_ID, parametersObj, "owner", "");
        } catch (Exception e) {
            return null;
        }
    }

    private SenderOptions<byte[], byte[]> kafkaSenderOptions() {
        String bootstrapServers = this.applicationConfig.getKafkaBootStrapServers();

        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // props.put(ProducerConfig.CLIENT_ID_CONFIG, "sample-producerx");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        // Security
        this.applicationConfig.addKafkaSecurityProps(props);

        return SenderOptions.create(props);
    }

    private SenderRecord<byte[], byte[], Integer> kafkaSenderRecord(String data, String key, String typeId) {
        final InfoType infoType = this.types.get(typeId);
        int correlationMetadata = 2;
        return SenderRecord.create(new ProducerRecord<>(infoType.getKafkaInputTopic(), key.getBytes(), data.getBytes()),
                correlationMetadata);
    }

    private void sendDataToKafka(Flux<SenderRecord<byte[], byte[], Integer>> dataToSend) {
        final KafkaSender<byte[], byte[]> sender = KafkaSender.create(kafkaSenderOptions());

        sender.send(dataToSend) //
                .doOnError(e -> logger.error("Send failed", e)) //
                .blockLast();

        sender.close();
    }

    @SuppressWarnings("squid:S2925") // "Thread.sleep" should not be used in tests.
    private static void waitForKafkaListener() throws InterruptedException {
        Thread.sleep(4000);
    }

    private StatisticsCollection getStatistics() {
        String targetUri = baseUrl() + ProducerCallbacksController.STATISTICS_URL;
        String statsResp = restClient().get(targetUri).block();
        StatisticsCollection stats = gson.fromJson(statsResp, StatisticsCollection.class);
        return stats;
    }

    @Builder
    static class CharacteristicsResult {
        long noOfFilesPerSecond;
        long noOfSentBytes;
        long noOfSentGigaBytes;
        long noOfSentObjects;
        long inputFileSize;
        long noOfReceivedFiles;
        long noOfReceivedBytes;
        long noOfSubscribers;
        long sizeOfSentObj;
        boolean zipOutput;
    }

    private CharacteristicsResult getCharacteristicsResult(Instant startTime) {
        final long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
        StatisticsCollection stats = getStatistics();
        long noOfSentBytes = 0;
        long noOfSentObjs = 0;
        for (Statistics s : stats.jobStatistics) {
            noOfSentBytes += s.getNoOfSentBytes();
            noOfSentObjs += s.getNoOfSentObjects();
        }

        Statistics oneJobsStats = stats.jobStatistics.iterator().next();

        return CharacteristicsResult.builder() //
                .noOfSentBytes(noOfSentBytes) //
                .noOfSentObjects(noOfSentObjs) //
                .noOfSentGigaBytes(noOfSentBytes / (1024 * 1024)) //
                .noOfSubscribers(stats.jobStatistics.size()) //
                .zipOutput(this.applicationConfig.isZipOutput()) //
                .noOfFilesPerSecond((oneJobsStats.getNoOfReceivedObjects() * 1000) / durationMs) //
                .noOfReceivedBytes(oneJobsStats.getNoOfReceivedBytes()) //
                .inputFileSize(oneJobsStats.getNoOfReceivedBytes() / oneJobsStats.getNoOfReceivedObjects()) //
                .noOfReceivedFiles(oneJobsStats.getNoOfReceivedObjects()) //
                .sizeOfSentObj(oneJobsStats.getNoOfSentBytes() / oneJobsStats.getNoOfSentObjects()) //
                .build();
    }

    private void printCharacteristicsResult(String str, Instant startTime, int noOfIterations) {
        final long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
        logger.info("*** {} Duration ({} ms),  objects/second: {}", str, durationMs,
                (noOfIterations * 1000) / durationMs);

        System.out.println("--------------");
        System.out.println(gson.toJson(getCharacteristicsResult(startTime)));
        System.out.println("--------------");

    }

    @SuppressWarnings("squid:S2925") // "Thread.sleep" should not be used in tests.
    @Test
    void kafkaCharacteristics_pmFilter_s3() throws Exception {
        // Filter PM reports and sent to two jobs over Kafka

        final String JOB_ID = "kafkaCharacteristics";
        final String JOB_ID2 = "kafkaCharacteristics2";

        // Register producer, Register types
        await().untilAsserted(() -> assertThat(icsSimulatorController.testResults.registrationInfo).isNotNull());
        assertThat(icsSimulatorController.testResults.registrationInfo.supportedTypeIds).hasSize(this.types.size());

        PmReportFilter.FilterData filterData = new PmReportFilter.FilterData();

        filterData.addMeasTypes("NRCellCU", "pmCounterNumber0");

        this.icsSimulatorController.addJob(consumerJobInfoKafka(this.applicationConfig.getKafkaBootStrapServers(),
                kafkaReceiver.OUTPUT_TOPIC, filterData), JOB_ID, restClient());
        this.icsSimulatorController.addJob(consumerJobInfoKafka(this.applicationConfig.getKafkaBootStrapServers(),
                kafkaReceiver2.OUTPUT_TOPIC, filterData), JOB_ID2, restClient());

        await().untilAsserted(() -> assertThat(this.jobs.size()).isEqualTo(2));
        waitForKafkaListener();

        final int NO_OF_OBJECTS = 10;

        Instant startTime = Instant.now();

        final String FILE_NAME = "A20000626.2315+0200-2330+0200_HTTPS-6-73.json";

        DataStore fileStore = dataStore();

        fileStore.create(DataStore.Bucket.FILES).block();
        fileStore.copyFileTo(Path.of("./src/test/resources/" + FILE_NAME), FILE_NAME).block();

        String eventAsString = newFileEvent(FILE_NAME);
        var dataToSend = Flux.range(1, NO_OF_OBJECTS).map(i -> kafkaSenderRecord(eventAsString, "key", PM_TYPE_ID));
        sendDataToKafka(dataToSend);

        while (kafkaReceiver.count < NO_OF_OBJECTS) {
            logger.info("sleeping {}", kafkaReceiver.count);
            Thread.sleep(1000 * 1);
        }

        String msgString = kafkaReceiver.receivedKafkaOutput.valueAsString();
        assertThat(msgString).contains("pmCounterNumber0");
        assertThat(msgString).doesNotContain("pmCounterNumber1");
        assertThat(kafkaReceiver.receivedKafkaOutput.getTypeIdFromHeaders()).isEqualTo(PM_TYPE_ID);
        assertThat(kafkaReceiver.receivedKafkaOutput.getSourceNameFromHeaders()).isEqualTo("HTTPST2-0"); // This is from
                                                                                                         // the file

        printCharacteristicsResult("kafkaCharacteristics_pmFilter_s3", startTime, NO_OF_OBJECTS);
        logger.info("***  kafkaReceiver2 :" + kafkaReceiver.count);

        assertThat(kafkaReceiver.count).isEqualTo(NO_OF_OBJECTS);
    }

    @SuppressWarnings("squid:S2925") // "Thread.sleep" should not be used in tests.
    @Test
    void kafkaCharacteristics_manyPmJobs() throws Exception {
        // Filter PM reports and sent to many jobs over Kafka

        this.applicationConfig.setZipOutput(false);

        // Register producer, Register types
        await().untilAsserted(() -> assertThat(icsSimulatorController.testResults.registrationInfo).isNotNull());
        assertThat(icsSimulatorController.testResults.registrationInfo.supportedTypeIds).hasSize(this.types.size());

        PmReportFilter.FilterData filterData = new PmReportFilter.FilterData();
        final int NO_OF_COUNTERS = 2;
        for (int i = 0; i < NO_OF_COUNTERS; ++i) {
            filterData.addMeasTypes("NRCellCU", "pmCounterNumber" + i);
        }

        final int NO_OF_JOBS = 150;

        ArrayList<KafkaReceiver> receivers = new ArrayList<>();
        for (int i = 0; i < NO_OF_JOBS; ++i) {
            final String outputTopic = "manyJobs_" + i;
            this.icsSimulatorController.addJob(
                    consumerJobInfoKafka(this.applicationConfig.getKafkaBootStrapServers(), outputTopic, filterData),
                    outputTopic, restClient());
            KafkaReceiver receiver = new KafkaReceiver(this.applicationConfig, outputTopic, this.securityContext, null);
            receivers.add(receiver);
        }

        await().untilAsserted(() -> assertThat(this.jobs.size()).isEqualTo(NO_OF_JOBS));
        waitForKafkaListener();

        final int NO_OF_OBJECTS = 1000;

        Instant startTime = Instant.now();

        final String FILE_NAME = "A20000626.2315+0200-2330+0200_HTTPS-6-73.json.gz";

        DataStore fileStore = dataStore();

        fileStore.deleteBucket(DataStore.Bucket.FILES).block();
        fileStore.create(DataStore.Bucket.FILES).block();
        fileStore.copyFileTo(Path.of("./src/test/resources/" + FILE_NAME), FILE_NAME).block();

        String eventAsString = newFileEvent(FILE_NAME);
        var dataToSend = Flux.range(1, NO_OF_OBJECTS).map(i -> kafkaSenderRecord(eventAsString, "key", PM_TYPE_ID));
        sendDataToKafka(dataToSend);

        logger.info("sleeping {}", kafkaReceiver.count);
        while (receivers.get(0).count < NO_OF_OBJECTS) {
            if (kafkaReceiver.count > 0) {
                logger.info("sleeping {}", receivers.get(0).count);
            }

            Thread.sleep(1000 * 1);
        }

        printCharacteristicsResult("kafkaCharacteristics_manyPmJobs", startTime, NO_OF_OBJECTS);

        for (KafkaReceiver receiver : receivers) {
            if (receiver.count != NO_OF_OBJECTS) {
                System.out.println("** Unexpected no of jobs: " + receiver.OUTPUT_TOPIC + " " + receiver.count);
            }
        }
    }

    @SuppressWarnings("squid:S2925") // "Thread.sleep" should not be used in tests.
    @Test
    void kafkaCharacteristics_manyPmJobs_sharedTopic() throws Exception {
        // Filter PM reports and sent to many jobs over Kafka

        this.applicationConfig.setZipOutput(false);

        // Register producer, Register types
        await().untilAsserted(() -> assertThat(icsSimulatorController.testResults.registrationInfo).isNotNull());
        assertThat(icsSimulatorController.testResults.registrationInfo.supportedTypeIds).hasSize(this.types.size());

        final int NO_OF_JOBS = 150;
        ArrayList<KafkaReceiver> receivers = new ArrayList<>();
        for (int i = 0; i < NO_OF_JOBS; ++i) {
            final String outputTopic = "kafkaCharacteristics_onePmJobs_manyReceivers";
            final String jobId = "manyJobs_" + i;
            PmReportFilter.FilterData filterData = new PmReportFilter.FilterData();
            filterData.addMeasTypes("NRCellCU", "pmCounterNumber" + i); // all counters will be added

            this.icsSimulatorController.addJob(
                    consumerJobInfoKafka(this.applicationConfig.getKafkaBootStrapServers(), outputTopic, filterData),
                    jobId, restClient());

            KafkaReceiver receiver =
                    new KafkaReceiver(this.applicationConfig, outputTopic, this.securityContext, "group_" + i);
            receivers.add(receiver);
        }

        await().untilAsserted(() -> assertThat(this.jobs.size()).isEqualTo(NO_OF_JOBS));
        waitForKafkaListener();

        final int NO_OF_OBJECTS = 1000;

        Instant startTime = Instant.now();

        final String FILE_NAME = "A20000626.2315+0200-2330+0200_HTTPS-6-73.json.gz";

        DataStore fileStore = dataStore();

        fileStore.deleteBucket(DataStore.Bucket.FILES).block();
        fileStore.create(DataStore.Bucket.FILES).block();
        fileStore.copyFileTo(Path.of("./src/test/resources/" + FILE_NAME), FILE_NAME).block();

        String eventAsString = newFileEvent(FILE_NAME);
        var dataToSend = Flux.range(1, NO_OF_OBJECTS).map(i -> kafkaSenderRecord(eventAsString, "key", PM_TYPE_ID));
        sendDataToKafka(dataToSend);

        logger.info("sleeping {}", kafkaReceiver.count);
        for (KafkaReceiver receiver : receivers) {
            while (receiver.count < NO_OF_OBJECTS) {
                if (kafkaReceiver.count > 0) {
                    logger.info("sleeping {}", receiver.count);
                }

                Thread.sleep(1000 * 1);
            }
        }

        printCharacteristicsResult("kafkaCharacteristics_manyPmJobs", startTime, NO_OF_OBJECTS);

        for (KafkaReceiver receiver : receivers) {
            if (receiver.count != NO_OF_OBJECTS) {
                System.out.println("** Unexpected no of objects: " + receiver.OUTPUT_TOPIC + " " + receiver.count);
            }
        }

        Thread.sleep(1000 * 5);
    }

    private String newFileEvent(String fileName) {
        NewFileEvent event = NewFileEvent.builder() //
                .filename(fileName) //
                .build();
        return gson.toJson(event);
    }

    private DataStore dataStore() {
        return DataStore.create(this.applicationConfig);
    }

    @Test
    void testHistoricalData() throws Exception {
        // test that it works to get already fetched data
        final String JOB_ID = "testHistoricalData";

        DataStore fileStore = dataStore();

        fileStore.deleteBucket(DataStore.Bucket.FILES).block();
        fileStore.create(DataStore.Bucket.FILES).block();
        fileStore.create(DataStore.Bucket.LOCKS).block();

        fileStore.copyFileTo(Path.of("./src/test/resources/pm_report.json"),
                "O-DU-1122/A20000626.2315+0200-2330+0200_HTTPS-6-73.json").block();

        fileStore.copyFileTo(Path.of("./src/test/resources/pm_report.json"), "OTHER_SOURCENAME/test.json").block();

        await().untilAsserted(() -> assertThat(icsSimulatorController.testResults.registrationInfo).isNotNull());

        PmReportFilter.FilterData filterData = new PmReportFilter.FilterData();
        filterData.getSourceNames().add("O-DU-1122");
        filterData.setPmRopStartTime("1999-12-27T10:50:44.000-08:00");

        filterData.setPmRopEndTime(OffsetDateTime.now().toString());

        this.icsSimulatorController.addJob(consumerJobInfoKafka(this.applicationConfig.getKafkaBootStrapServers(),
                kafkaReceiver.OUTPUT_TOPIC, filterData), JOB_ID, restClient());
        await().untilAsserted(() -> assertThat(this.jobs.size()).isEqualTo(1));

        await().untilAsserted(() -> assertThat(kafkaReceiver.count).isPositive());
    }

}
