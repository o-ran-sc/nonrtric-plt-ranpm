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

package org.oran.pmlog;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = { "server.http-port=8080" }) // Set the http-port property
class BeanFactoryTest {

    @Autowired
    private BeanFactory beanFactory;

    @Test
    void testApplicationConfigBean() {
        // Ensure that the ApplicationConfig bean is created
        assertNotNull(beanFactory.getApplicationConfig());
    }

    @Test
    void testServletContainerBean() {
        // Ensure that the ServletWebServerFactory bean is created
        assertNotNull(beanFactory.servletContainer());
    }

    @Test
    void testKafkaTopicListenerBean() {
        // Ensure that the KafkaTopicListener bean is created with ApplicationConfig dependency
        assertNotNull(beanFactory.getKafkaTopicListener(beanFactory.getApplicationConfig()));
    }

    @Test
    void testInfluxStoreBean() {
        // Ensure that the InfluxStore bean is created with ApplicationConfig and KafkaTopicListener dependencies
        assertNotNull(beanFactory.getInfluxStore(beanFactory.getApplicationConfig(), beanFactory.getKafkaTopicListener(beanFactory.getApplicationConfig())));
    }
}
