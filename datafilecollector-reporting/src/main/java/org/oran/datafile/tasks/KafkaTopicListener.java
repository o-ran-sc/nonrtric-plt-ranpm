/*-
 * ========================LICENSE_START=================================
 * O-RAN-SC
 * %%
 * Copyright (C) 2021-2023 Nordix Foundation
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

package org.oran.datafile.tasks;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import lombok.ToString;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.oran.datafile.configuration.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

/**
 * The class streams incoming requests from a Kafka topic and sends them further
 * to a multi cast sink, which several other streams can connect to.
 */
@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
public class KafkaTopicListener {

    @ToString
    public static class DataFromTopic {
        public final String key;
        public final String value;

        public DataFromTopic(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(KafkaTopicListener.class);

    private Flux<DataFromTopic> dataFromTopic;
    private final AppConfig appConfig;

    public KafkaTopicListener(AppConfig applConfig) {
        this.appConfig = applConfig;
    }

    public Flux<DataFromTopic> getFlux() {
        if (this.dataFromTopic == null) {
            this.dataFromTopic = startReceiveFromTopic();
        }
        return this.dataFromTopic;
    }

    private Flux<DataFromTopic> startReceiveFromTopic() {
        logger.debug("Listening to kafka topic: {}, client id: {}", appConfig.getInputTopic(),
            appConfig.getKafkaClientId());
        return KafkaReceiver.create(kafkaInputProperties()) //
            .receive() //
            .doOnNext(
                input -> logger.debug("Received from kafka topic: {} :{}", appConfig.getInputTopic(), input.value())) //
            .doOnError(t -> logger.error("KafkaTopicReceiver error: {}", t.getMessage())) //
            .doFinally(sig -> logger.error("KafkaTopicReceiver stopped, reason: {}", sig)) //
            .doFinally(sig -> this.dataFromTopic = null) //
            .filter(t -> !t.value().isEmpty() || !t.key().isEmpty()) //
            .map(input -> new DataFromTopic(input.key(), input.value())) //
            .publish() //
            .autoConnect();
    }

    private ReceiverOptions<String, String> kafkaInputProperties() {
        Map<String, Object> consumerProps = new HashMap<>();
        if (appConfig.getKafkaBootStrapServers().isEmpty()) {
            logger.error("No kafka boostrap server is setup");
        }
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, appConfig.getKafkaBootStrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "osc-dmaap-adapter-" + appConfig.getInputTopic());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        consumerProps.put(ConsumerConfig.CLIENT_ID_CONFIG, appConfig.getKafkaClientId());
        this.appConfig.addKafkaSecurityProps(consumerProps);

        return ReceiverOptions.<String, String>create(consumerProps)
            .subscription(Collections.singleton(appConfig.getInputTopic()));
    }

}
