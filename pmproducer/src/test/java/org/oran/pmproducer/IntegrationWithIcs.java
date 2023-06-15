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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.oran.pmproducer.clients.AsyncRestClient;
import org.oran.pmproducer.clients.AsyncRestClientFactory;
import org.oran.pmproducer.configuration.ApplicationConfig;
import org.oran.pmproducer.configuration.WebClientConfig;
import org.oran.pmproducer.configuration.WebClientConfig.HttpProxyConfig;
import org.oran.pmproducer.filter.PmReportFilter;
import org.oran.pmproducer.oauth2.SecurityContext;
import org.oran.pmproducer.r1.ConsumerJobInfo;
import org.oran.pmproducer.repository.Jobs;
import org.oran.pmproducer.tasks.ProducerRegstrationTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

@SuppressWarnings("java:S3577") // Rename class
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = { //
        "server.ssl.key-store=./config/keystore.jks", //
        "app.webclient.trust-store=./config/truststore.jks", //
        "app.configuration-filepath=./src/test/resources/test_application_configuration.json", //
        "app.ics-base-url=https://localhost:8434" //
})
/**
 * Tests that the interwork with ICS works.
 * ICS must be running.
 */
class IntegrationWithIcs {

    private static final String JOB_ID = "JOB_ID";
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    @Autowired
    private ApplicationConfig applicationConfig;

    @Autowired
    private ProducerRegstrationTask producerRegstrationTask;

    @Autowired
    private Jobs jobs;

    @Autowired
    private SecurityContext securityContext;

    private static Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    static class TestApplicationConfig extends ApplicationConfig {

        @Override
        public String getIcsBaseUrl() {
            return "https://localhost:8434";
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

    @AfterEach
    void reset() {
        assertThat(this.jobs.size()).isZero();
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
        return restClientFactory.createRestClientNoHttpProxy(selfBaseUrl());
    }

    private AsyncRestClient restClient() {
        return restClient(false);
    }

    private String selfBaseUrl() {
        return "https://localhost:" + this.applicationConfig.getLocalServerHttpPort();
    }

    private String icsBaseUrl() {
        return applicationConfig.getIcsBaseUrl();
    }

    private String jobUrl(String jobId) {
        return icsBaseUrl() + "/data-consumer/v1/info-jobs/" + jobId + "?typeCheck=true";
    }

    private void deleteInformationJobInIcs(String jobId) {
        try {
            restClient().delete(jobUrl(jobId)).block();
        } catch (Exception e) {
            logger.warn("Couldnot delete job: {}  reason: {}", jobId, e.getMessage());
        }
    }

    private void createInformationJobInIcs(String jobId, ConsumerJobInfo jobInfo) {
        String body = gson.toJson(jobInfo);
        restClient().putForEntity(jobUrl(jobId), body).block();
        logger.info("Created job {}, {}", jobId, body);
    }

    @Test
    void testCreateJob() throws Exception {
        await().untilAsserted(() -> assertThat(producerRegstrationTask.isRegisteredInIcs()).isTrue());
        final String TYPE_ID = "PmDataOverKafka";

        PmReportFilter.FilterData filterData = new PmReportFilter.FilterData();

        ConsumerJobInfo jobInfo = IntegrationWithKafka
                .consumerJobInfoKafka(this.applicationConfig.getKafkaBootStrapServers(), TYPE_ID, filterData);

        createInformationJobInIcs(JOB_ID, jobInfo);
        await().untilAsserted(() -> assertThat(this.jobs.size()).isEqualTo(1));

        deleteInformationJobInIcs(JOB_ID);
        await().untilAsserted(() -> assertThat(this.jobs.size()).isZero());
    }
}
