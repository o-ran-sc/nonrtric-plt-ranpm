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

import com.google.common.io.CharStreams;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import lombok.Getter;

import org.oran.pmproducer.clients.AsyncRestClient;
import org.oran.pmproducer.clients.AsyncRestClientFactory;
import org.oran.pmproducer.configuration.ApplicationConfig;
import org.oran.pmproducer.controllers.ProducerCallbacksController;
import org.oran.pmproducer.exceptions.ServiceException;
import org.oran.pmproducer.oauth2.SecurityContext;
import org.oran.pmproducer.r1.ConsumerJobInfo;
import org.oran.pmproducer.r1.ProducerInfoTypeInfo;
import org.oran.pmproducer.r1.ProducerRegistrationInfo;
import org.oran.pmproducer.repository.InfoType;
import org.oran.pmproducer.repository.InfoTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Registers the types and this producer in Innformation Coordinator Service.
 * This is done when needed.
 */
@Component
@EnableScheduling
@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
public class ProducerRegstrationTask {

    private static final Logger logger = LoggerFactory.getLogger(ProducerRegstrationTask.class);
    private final AsyncRestClient restClient;
    private final ApplicationConfig applicationConfig;
    private final InfoTypes types;
    private static com.google.gson.Gson gson = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    @Getter
    private boolean isRegisteredInIcs = false;
    private static final int REGISTRATION_SUPERVISION_INTERVAL_MS = 1000 * 10;

    public ProducerRegstrationTask(@Autowired ApplicationConfig applicationConfig, @Autowired InfoTypes types,
            @Autowired SecurityContext securityContext) {
        AsyncRestClientFactory restClientFactory =
                new AsyncRestClientFactory(applicationConfig.getWebClientConfig(), securityContext);
        this.restClient = restClientFactory.createRestClientNoHttpProxy("");
        this.applicationConfig = applicationConfig;
        this.types = types;
    }

    @Scheduled(fixedRate = REGISTRATION_SUPERVISION_INTERVAL_MS)
    public void runSupervisionTask() {
        if (this.isRegisteredInIcs) {
            return;
        }
        registerTypesAndProducer().subscribe( //
                null, //
                this::handleRegistrationFailure//
        );
    }

    private void handleRegistrationCompleted() {
        isRegisteredInIcs = true;
    }

    private void handleRegistrationFailure(Throwable t) {
        logger.warn("Registration of producer failed {}", t.getMessage());
    }

    private String producerRegistrationUrl() {
        final String producerId = this.applicationConfig.getSelfUrl().replace("/", "_");
        return applicationConfig.getIcsBaseUrl() + "/data-producer/v1/info-producers/" + producerId;
    }

    private String registerTypeUrl(InfoType type) {
        return applicationConfig.getIcsBaseUrl() + "/data-producer/v1/info-types/" + type.getId();
    }

    public Mono<String> registerTypesAndProducer() {
        final int CONCURRENCY = 1;

        return Flux.fromIterable(this.types.getAll()) //
                .doOnNext(type -> logger.info("Registering type {}", type.getId())) //
                .flatMap(this::createInputDataJob, CONCURRENCY)
                .flatMap(type -> restClient.put(registerTypeUrl(type), gson.toJson(typeRegistrationInfo(type))),
                        CONCURRENCY) //
                .collectList() //
                .doOnNext(type -> logger.info("Registering producer")) //
                .flatMap(resp -> restClient.put(producerRegistrationUrl(), gson.toJson(producerRegistrationInfo())))
                .doOnNext(n -> handleRegistrationCompleted());
    }

    private Mono<InfoType> createInputDataJob(InfoType type) {
        if (type.getInputJobType() == null) {
            return Mono.just(type);
        }

        ConsumerJobInfo info =
                new ConsumerJobInfo(type.getInputJobType(), type.getInputJobDefinition(), "pmproducer", "");

        final String JOB_ID = type.getId() + "_5b3f4db6-3d9e-11ed-b878-0242ac120002";
        String body = gson.toJson(info);

        return restClient.put(consumerJobUrl(JOB_ID), body)
                .doOnError(t -> logger.error("Could not create job of type {}, reason: {}", type.getInputJobType(),
                        t.getMessage()))
                .doOnNext(n -> logger.info("Created input job: {}, type: {}", JOB_ID, type.getInputJobType())) //
                .map(x -> type);
    }

    private String consumerJobUrl(String jobId) {
        return applicationConfig.getIcsBaseUrl() + "/data-consumer/v1/info-jobs/" + jobId;
    }

    private Object typeSpecifcInfoObject() {
        return jsonObject("{}");
    }

    private ProducerInfoTypeInfo typeRegistrationInfo(InfoType type) {
        try {
            return new ProducerInfoTypeInfo(jsonSchemaObject(type), typeSpecifcInfoObject());
        } catch (Exception e) {
            logger.error("Fatal error {}", e.getMessage());
            return null;
        }
    }

    private Object jsonSchemaObject(InfoType type) throws IOException, ServiceException {
        final String schemaFile = "/typeSchemaPmData.json";
        return jsonObject(readSchemaFile(schemaFile));
    }

    private String readSchemaFile(String filePath) throws IOException, ServiceException {
        InputStream in = getClass().getResourceAsStream(filePath);
        logger.debug("Reading application schema file from: {} with: {}", filePath, in);
        if (in == null) {
            throw new ServiceException("Could not readfile: " + filePath, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return CharStreams.toString(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    @SuppressWarnings("java:S2139") // Log exception
    private Object jsonObject(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            logger.error("Bug, error in JSON: {} {}", json, e.getMessage());
            throw new NullPointerException(e.getMessage());
        }
    }

    private boolean isEqual(ProducerRegistrationInfo a, ProducerRegistrationInfo b) {
        return a.jobCallbackUrl.equals(b.jobCallbackUrl) //
                && a.producerSupervisionCallbackUrl.equals(b.producerSupervisionCallbackUrl) //
                && a.supportedTypeIds.size() == b.supportedTypeIds.size();
    }

    private ProducerRegistrationInfo producerRegistrationInfo() {
        return ProducerRegistrationInfo.builder() //
                .jobCallbackUrl(baseUrl() + ProducerCallbacksController.JOB_URL) //
                .producerSupervisionCallbackUrl(baseUrl() + ProducerCallbacksController.SUPERVISION_URL) //
                .supportedTypeIds(this.types.typeIds()) //
                .build();
    }

    private String baseUrl() {
        return this.applicationConfig.getSelfUrl();
    }
}
