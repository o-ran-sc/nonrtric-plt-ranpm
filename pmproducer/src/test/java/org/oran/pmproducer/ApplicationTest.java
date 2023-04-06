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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;
import org.oran.pmproducer.clients.AsyncRestClient;
import org.oran.pmproducer.clients.AsyncRestClientFactory;
import org.oran.pmproducer.configuration.ApplicationConfig;
import org.oran.pmproducer.configuration.WebClientConfig;
import org.oran.pmproducer.configuration.WebClientConfig.HttpProxyConfig;
import org.oran.pmproducer.controllers.ProducerCallbacksController;
import org.oran.pmproducer.datastore.DataStore;
import org.oran.pmproducer.datastore.DataStore.Bucket;
import org.oran.pmproducer.filter.FilteredData;
import org.oran.pmproducer.filter.PmReport;
import org.oran.pmproducer.filter.PmReportFilter;
import org.oran.pmproducer.filter.PmReportFilter.FilterData;
import org.oran.pmproducer.oauth2.SecurityContext;
import org.oran.pmproducer.r1.ConsumerJobInfo;
import org.oran.pmproducer.r1.ProducerJobInfo;
import org.oran.pmproducer.repository.InfoType;
import org.oran.pmproducer.repository.InfoTypes;
import org.oran.pmproducer.repository.Job;
import org.oran.pmproducer.repository.Job.Parameters;
import org.oran.pmproducer.repository.Job.Parameters.KafkaDeliveryInfo;
import org.oran.pmproducer.repository.Jobs;
import org.oran.pmproducer.repository.Jobs.JobGroup;
import org.oran.pmproducer.tasks.JobDataDistributor;
import org.oran.pmproducer.tasks.NewFileEvent;
import org.oran.pmproducer.tasks.ProducerRegstrationTask;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { //
        "server.ssl.key-store=./config/keystore.jks", //
        "app.webclient.trust-store=./config/truststore.jks", //
        "app.webclient.trust-store-used=true", //
        "app.configuration-filepath=./src/test/resources/test_application_configuration.json", //
        "app.pm-files-path=/tmp/pmproducer", //
        "app.s3.endpointOverride=" //
})
class ApplicationTest {

    @Autowired
    private ApplicationConfig applicationConfig;

    @Autowired
    private Jobs jobs;

    @Autowired
    private InfoTypes types;

    @Autowired
    private IcsSimulatorController icsSimulatorController;

    @Autowired
    TopicListeners topicListeners;

    @Autowired
    ProducerRegstrationTask producerRegistrationTask;

    @Autowired
    private SecurityContext securityContext;

    private com.google.gson.Gson gson = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    @LocalServerPort
    int localServerHttpPort;

    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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

        // @Override
        @Bean
        public ApplicationConfig getApplicationConfig() {
            TestApplicationConfig cfg = new TestApplicationConfig();
            return cfg;
        }
    }

    @BeforeEach
    public void init() {
        this.applicationConfig.setLocalServerHttpPort(this.localServerHttpPort);
        assertThat(this.jobs.size()).isZero();

        DataStore fileStore = this.dataStore();
        fileStore.create(DataStore.Bucket.FILES).block();
        fileStore.create(DataStore.Bucket.LOCKS).block();
    }

    private DataStore dataStore() {
        return DataStore.create(this.applicationConfig);
    }

    @AfterEach
    void reset() {

        for (Job job : this.jobs.getAll()) {
            this.icsSimulatorController.deleteJob(job.getId(), restClient());
        }
        await().untilAsserted(() -> assertThat(this.jobs.size()).isZero());

        this.icsSimulatorController.testResults.reset();

        DataStore fileStore = DataStore.create(applicationConfig);
        fileStore.deleteBucket(Bucket.FILES);
        fileStore.deleteBucket(Bucket.LOCKS);

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

    private Object toJson(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new NullPointerException(e.toString());
        }
    }

    private ConsumerJobInfo consumerJobInfo(String typeId, String infoJobId, Object filter) {
        try {
            return new ConsumerJobInfo(typeId, filter, "owner", "");
        } catch (Exception e) {
            return null;
        }
    }

    private void waitForRegistration() {
        producerRegistrationTask.registerTypesAndProducer().block();
        // Register producer, Register types
        await().untilAsserted(() -> assertThat(icsSimulatorController.testResults.registrationInfo).isNotNull());
        producerRegistrationTask.registerTypesAndProducer().block();

        assertThat(icsSimulatorController.testResults.registrationInfo.supportedTypeIds).hasSize(this.types.size());
        assertThat(producerRegistrationTask.isRegisteredInIcs()).isTrue();
        assertThat(icsSimulatorController.testResults.types).hasSize(this.types.size());
    }

    @Test
    void generateApiDoc() throws IOException {
        String url = "https://localhost:" + applicationConfig.getLocalServerHttpPort() + "/v3/api-docs";
        ResponseEntity<String> resp = restClient().getForEntity(url).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JSONObject jsonObj = new JSONObject(resp.getBody());
        assertThat(jsonObj.remove("servers")).isNotNull();

        String indented = (jsonObj).toString(4);
        String docDir = "api/";
        Files.createDirectories(Paths.get(docDir));
        try (PrintStream out = new PrintStream(new FileOutputStream(docDir + "api.json"))) {
            out.print(indented);
        }
    }

    @Test
    void testTrustValidation() throws IOException {
        String url = "https://localhost:" + applicationConfig.getLocalServerHttpPort() + "/v3/api-docs";
        ResponseEntity<String> resp = restClient(true).getForEntity(url).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testResponseCodes() throws Exception {
        String supervisionUrl = baseUrl() + ProducerCallbacksController.SUPERVISION_URL;
        ResponseEntity<String> resp = restClient().getForEntity(supervisionUrl).block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        String jobUrl = baseUrl() + ProducerCallbacksController.JOB_URL;
        resp = restClient().deleteForEntity(jobUrl + "/junk").block();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ProducerJobInfo info = new ProducerJobInfo(null, "id", "typeId", "owner", "lastUpdated");
        String body = gson.toJson(info);
        testErrorCode(restClient().post(jobUrl, body, MediaType.APPLICATION_JSON), HttpStatus.NOT_FOUND,
                "Could not find type");
    }

    @Test
    void testFiltering() {
        String path = "./src/test/resources/pm_report.json.gz";
        DataStore fs = DataStore.create(this.applicationConfig);
        fs.copyFileTo(Path.of(path), "pm_report.json.gz");

        InfoType infoType = this.types.getAll().iterator().next();
        TopicListener listener = spy(new TopicListener(this.applicationConfig, infoType));
        NewFileEvent event = NewFileEvent.builder().filename("pm_report.json.gz").build();
        ConsumerRecord<byte[], byte[]> cr = new ConsumerRecord<>("", 0, 0, new byte[0], gson.toJson(event).getBytes());
        when(listener.receiveFromKafka(any())).thenReturn(Flux.just(cr));

        KafkaDeliveryInfo deliveryInfo = KafkaDeliveryInfo.builder().topic("topic").bootStrapServers("").build();
        JobGroup jobGroup = new JobGroup(infoType, deliveryInfo);
        jobGroup.add(new Job("id", infoType, "owner", "lastUpdated",
                Parameters.builder().filter(new FilterData()).build(), this.applicationConfig));
        JobDataDistributor distributor = spy(new JobDataDistributor(jobGroup, this.applicationConfig));

        doReturn(Mono.just("")).when(distributor).sendToClient(any());

        distributor.start(listener.getFlux());

        {
            ArgumentCaptor<FilteredData> captor = ArgumentCaptor.forClass(FilteredData.class);
            verify(distributor).sendToClient(captor.capture());
            FilteredData data = captor.getValue();
            PmReport report = PmReportFilter.parse(new String(data.value));
            assertThat(report.event.getCommonEventHeader().getSourceName()).isEqualTo("O-DU-1122");
        }
    }

    @Test
    void testFilteringHistoricalData() {
        DataStore fileStore = DataStore.create(this.applicationConfig);
        fileStore.copyFileTo(Path.of("./src/test/resources/pm_report.json"),
                "O-DU-1122/A20000626.2315+0200-2330+0200_HTTPS-6-73.json").block();

        InfoType infoType = this.types.getAll().iterator().next();
        TopicListener listener = spy(new TopicListener(this.applicationConfig, infoType));
        Flux<ConsumerRecord<byte[], byte[]>> flux = Flux.just("") //
                .delayElements(Duration.ofSeconds(10)).flatMap(s -> Flux.empty());
        when(listener.receiveFromKafka(any())).thenReturn(flux);

        PmReportFilter.FilterData filterData = new PmReportFilter.FilterData();
        filterData.getSourceNames().add("O-DU-1122");
        filterData.setPmRopStartTime("1999-12-27T10:50:44.000-08:00");
        filterData.setPmRopEndTime(OffsetDateTime.now().toString());
        KafkaDeliveryInfo deliveryInfo = KafkaDeliveryInfo.builder().topic("topic").bootStrapServers("").build();
        JobGroup jobGroup = new JobGroup(infoType, deliveryInfo);
        Parameters params = Parameters.builder().filter(filterData).build();
        Job job = new Job("id", infoType, "owner", "lastUpdated", params, this.applicationConfig);
        jobGroup.add(job);
        JobDataDistributor distributor = spy(new JobDataDistributor(jobGroup, this.applicationConfig));

        doReturn(Mono.just("")).when(distributor).sendToClient(any());

        distributor.start(listener.getFlux());

        {
            ArgumentCaptor<FilteredData> captor = ArgumentCaptor.forClass(FilteredData.class);
            verify(distributor, times(2)).sendToClient(captor.capture());
            List<FilteredData> data = captor.getAllValues();
            assertThat(data).hasSize(2);
            PmReport report = PmReportFilter.parse(new String(data.get(0).value));
            assertThat(report.event.getCommonEventHeader().getSourceName()).isEqualTo("O-DU-1122");
            assertThat(new String(data.get(1).value)).isEqualTo("{}");
        }
    }

    @Test
    void testCreateJob() throws Exception {
        // Create a job
        final String JOB_ID = "ID";

        // Register producer, Register types
        waitForRegistration();

        assertThat(this.topicListeners.getTopicListeners()).hasSize(1);

        // Create a job with a PM filter
        PmReportFilter.FilterData filterData = new PmReportFilter.FilterData();

        filterData.addMeasTypes("UtranCell", "succImmediateAssignProcs");
        filterData.getMeasObjInstIds().add("UtranCell=Gbg-997");
        filterData.getSourceNames().add("O-DU-1122");
        filterData.getMeasuredEntityDns().add("ManagedElement=RNC-Gbg-1");
        Job.Parameters param = Job.Parameters.builder() //
                .filter(filterData).deliveryInfo(KafkaDeliveryInfo.builder().bootStrapServers("").topic("").build()) //
                .build();

        String paramJson = gson.toJson(param);
        System.out.println(paramJson);
        ConsumerJobInfo jobInfo = consumerJobInfo("PmDataOverKafka", "EI_PM_JOB_ID", toJson(paramJson));

        this.icsSimulatorController.addJob(jobInfo, JOB_ID, restClient());
        await().untilAsserted(() -> assertThat(this.jobs.size()).isEqualTo(1));

        assertThat(this.topicListeners.getDataDistributors().keySet()).hasSize(1);
    }

    @Test
    void testPmFilteringKafka() throws Exception {
        // Test that the schema for kafka and pm filtering is OK.

        // Create a job
        final String JOB_ID = "ID";

        // Register producer, Register types
        waitForRegistration();

        // Create a job with a PM filter
        PmReportFilter.FilterData filterData = new PmReportFilter.FilterData();
        filterData.addMeasTypes("ManagedElement", "succImmediateAssignProcs");
        Job.Parameters param = Job.Parameters.builder() //
                .filter(filterData).deliveryInfo(KafkaDeliveryInfo.builder().bootStrapServers("").topic("").build()) //
                .build();

        String paramJson = gson.toJson(param);

        ConsumerJobInfo jobInfo = consumerJobInfo("PmDataOverKafka", "EI_PM_JOB_ID", toJson(paramJson));
        this.icsSimulatorController.addJob(jobInfo, JOB_ID, restClient());
        await().untilAsserted(() -> assertThat(this.jobs.size()).isEqualTo(1));
    }

    @Test
    @SuppressWarnings("squid:S2925") // "Thread.sleep" should not be used in tests.
    void testZZActuator() throws Exception {
        // The test must be run last, hence the "ZZ" in the name. All succeeding tests
        // will fail.
        AsyncRestClient client = restClient();
        client.post("/actuator/loggers/org.oran.pmproducer", "{\"configuredLevel\":\"trace\"}").block();
        String resp = client.get("/actuator/loggers/org.oran.pmproducer").block();
        assertThat(resp).contains("TRACE");
        client.post("/actuator/loggers/org.springframework.boot.actuate", "{\"configuredLevel\":\"trace\"}").block();
        // This will stop the web server and all coming tests will fail.
        client.post("/actuator/shutdown", "").block();
        Thread.sleep(1000);

        String url = "https://localhost:" + applicationConfig.getLocalServerHttpPort() + "/v3/api-docs";
        StepVerifier.create(restClient().get(url)) // Any call
                .expectSubscription() //
                .expectErrorMatches(t -> t instanceof WebClientRequestException) //
                .verify();
    }

    public static void testErrorCode(Mono<?> request, HttpStatus expStatus, String responseContains) {
        testErrorCode(request, expStatus, responseContains, true);
    }

    public static void testErrorCode(Mono<?> request, HttpStatus expStatus, String responseContains,
            boolean expectApplicationProblemJsonMediaType) {
        StepVerifier.create(request) //
                .expectSubscription() //
                .expectErrorMatches(
                        t -> checkWebClientError(t, expStatus, responseContains, expectApplicationProblemJsonMediaType)) //
                .verify();
    }

    private static boolean checkWebClientError(Throwable throwable, HttpStatus expStatus, String responseContains,
            boolean expectApplicationProblemJsonMediaType) {
        assertTrue(throwable instanceof WebClientResponseException);
        WebClientResponseException responseException = (WebClientResponseException) throwable;
        assertThat(responseException.getStatusCode()).isEqualTo(expStatus);
        assertThat(responseException.getResponseBodyAsString()).contains(responseContains);
        if (expectApplicationProblemJsonMediaType) {
            assertThat(responseException.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        }
        return true;
    }
}
