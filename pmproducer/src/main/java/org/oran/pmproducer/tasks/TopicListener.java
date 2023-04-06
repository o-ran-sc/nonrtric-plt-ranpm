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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.oran.pmproducer.configuration.ApplicationConfig;
import org.oran.pmproducer.datastore.DataStore;
import org.oran.pmproducer.filter.PmReport;
import org.oran.pmproducer.repository.InfoType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

/**
 * The class streams incoming requests from a Kafka topic and sends them further
 * to a multi cast sink, which several other streams can connect to.
 */
@SuppressWarnings("squid:S2629") // Invoke method(s) only conditionally
public class TopicListener {

    @ToString
    public static class DataFromTopic {
        public final byte[] key;
        public final byte[] value;

        public final String infoTypeId;

        public final Iterable<Header> headers;

        private static byte[] noBytes = new byte[0];

        @Getter
        @Setter
        @ToString.Exclude
        private PmReport cachedPmReport;

        public DataFromTopic(String typeId, Iterable<Header> headers, byte[] key, byte[] value) {
            this.key = key == null ? noBytes : key;
            this.value = value == null ? noBytes : value;
            this.infoTypeId = typeId;
            this.headers = headers;
        }

        public String valueAsString() {
            return new String(this.value);
        }

        public static final String ZIPPED_PROPERTY = "gzip";
        public static final String TYPE_ID_PROPERTY = "type-id";
        public static final String SOURCE_NAME_PROPERTY = "source-name";

        public boolean isZipped() {
            if (headers == null) {
                return false;
            }
            for (Header h : headers) {
                if (h.key().equals(ZIPPED_PROPERTY)) {
                    return true;
                }
            }
            return false;
        }

        public String getTypeIdFromHeaders() {
            return this.getStringProperty(TYPE_ID_PROPERTY);
        }

        public String getSourceNameFromHeaders() {
            return this.getStringProperty(SOURCE_NAME_PROPERTY);
        }

        private String getStringProperty(String propertyName) {
            if (headers == null) {
                return "";
            }
            for (Header h : headers) {
                if (h.key().equals(propertyName)) {
                    return new String(h.value());
                }
            }
            return "";
        }

    }

    private static final Logger logger = LoggerFactory.getLogger(TopicListener.class);
    private final ApplicationConfig applicationConfig;
    private final InfoType type;
    private Flux<DataFromTopic> dataFromTopic;
    private static com.google.gson.Gson gson = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
    private final DataStore dataStore;

    @Setter
    private String kafkaGroupId;

    public TopicListener(ApplicationConfig applConfig, InfoType type) {
        this.applicationConfig = applConfig;
        this.type = type;
        this.dataStore = DataStore.create(applConfig);
        this.kafkaGroupId = this.type.getKafkaGroupId();
    }

    public Flux<DataFromTopic> getFlux() {
        if (this.dataFromTopic == null) {
            this.dataFromTopic = start(this.type.getKafkaClientId(this.applicationConfig));
        }
        return this.dataFromTopic;
    }

    private Flux<DataFromTopic> start(String clientId) {
        logger.debug("Listening to kafka topic: {} type :{}", this.type.getKafkaInputTopic(), type.getId());

        return receiveFromKafka(clientId) //
                .filter(t -> t.value().length > 0 || t.key().length > 0) //
                .map(input -> new DataFromTopic(this.type.getId(), input.headers(), input.key(), input.value())) //
                .flatMap(data -> getDataFromFileIfNewPmFileEvent(data, type, dataStore)) //
                .publish() //
                .autoConnect(1);
    }

    public Flux<ConsumerRecord<byte[], byte[]>> receiveFromKafka(String clientId) {
        return KafkaReceiver.create(kafkaInputProperties(clientId)) //
                .receiveAutoAck() //
                .concatMap(consumerRecord -> consumerRecord) //
                .doOnNext(input -> logger.trace("Received from kafka topic: {}", this.type.getKafkaInputTopic())) //
                .doOnError(t -> logger.error("Received error: {}", t.getMessage())) //
                .onErrorResume(t -> Mono.empty()) //
                .doFinally(sig -> logger.error("TopicListener stopped, type: {}, reason: {}", this.type.getId(), sig));
    }

    private ReceiverOptions<byte[], byte[]> kafkaInputProperties(String clientId) {
        Map<String, Object> props = new HashMap<>();
        if (this.applicationConfig.getKafkaBootStrapServers().isEmpty()) {
            logger.error("No kafka boostrap server is setup");
        }

        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, this.applicationConfig.getKafkaBootStrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId + "_" + kafkaGroupId);

        this.applicationConfig.addKafkaSecurityProps(props);

        return ReceiverOptions.<byte[], byte[]>create(props)
                .subscription(Collections.singleton(this.type.getKafkaInputTopic()));
    }

    public static Mono<DataFromTopic> getDataFromFileIfNewPmFileEvent(DataFromTopic data, InfoType type,
            DataStore fileStore) {
        try {
            if (data.value.length > 200) {
                return Mono.just(data);
            }

            NewFileEvent ev = gson.fromJson(data.valueAsString(), NewFileEvent.class);

            if (ev.getFilename() == null) {
                logger.warn("Ignoring received message: {}", data);
                return Mono.empty();
            }
            logger.trace("Reading PM measurements, type: {}, inputTopic: {}", type.getId(), type.getKafkaInputTopic());
            return fileStore.readObject(DataStore.Bucket.FILES, ev.getFilename()) //
                    .map(bytes -> unzip(bytes, ev.getFilename())) //
                    .map(bytes -> new DataFromTopic(data.infoTypeId, data.headers, data.key, bytes));

        } catch (Exception e) {
            return Mono.just(data);
        }
    }

    public static byte[] unzip(byte[] bytes) throws IOException {
        try (final GZIPInputStream gzipInput = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return gzipInput.readAllBytes();
        }
    }

    private static byte[] unzip(byte[] bytes, String fileName) {
        try {
            return fileName.endsWith(".gz") ? unzip(bytes) : bytes;
        } catch (IOException e) {
            logger.error("Error while decompression, file: {}, reason: {}", fileName, e.getMessage());
            return new byte[0];
        }

    }

}
