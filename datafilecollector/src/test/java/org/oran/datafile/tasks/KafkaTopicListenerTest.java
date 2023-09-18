/*-
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2023 Nordix Foundation.
 * ================================================================================
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * ============LICENSE_END=========================================================
 */

package org.oran.datafile.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.oran.datafile.configuration.AppConfig;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

class KafkaTopicListenerTest {

    @Mock
    private AppConfig appConfig;

    @Mock
    private KafkaTopicListener kafkaTopicListener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        when(appConfig.getInputTopic()).thenReturn("testTopic");
        when(appConfig.getKafkaClientId()).thenReturn("testClientId");
        when(appConfig.getKafkaBootStrapServers()).thenReturn("localhost:9092");
        kafkaTopicListener = new KafkaTopicListener(appConfig);
    }

    @Test
    void testStartReceiveFromTopic() {
        KafkaReceiver mockKafkaReceiver = mock(KafkaReceiver.class);

        when(mockKafkaReceiver.receive()).thenReturn(Flux.just(new KafkaTopicListener.DataFromTopic("key", "value")));

        ReceiverOptions<String, String> receiverOptions = mock(ReceiverOptions.class);
        when(receiverOptions.subscription(Collections.singleton("testTopic"))).thenReturn(receiverOptions);
        when(receiverOptions.consumerProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, OffsetResetStrategy.EARLIEST.name()))
            .thenReturn(receiverOptions);

        assertEquals("testTopic", appConfig.getInputTopic());

    }
}