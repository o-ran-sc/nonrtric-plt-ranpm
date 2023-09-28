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

package org.oran.pmlog.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.oran.pmlog.oauth2.OAuthKafkaAuthenticateLoginCallbackHandler;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = {ApplicationConfig.class})
@ExtendWith(MockitoExtension.class)
class ApplicationConfigTest {

    @InjectMocks
    private ApplicationConfig appConfig;

    @BeforeEach
    void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testGetS3LocksBucket_WhenEmptyLocksBucket_ReturnsS3Bucket() {
        injectFieldValue(appConfig, "influxBucket", "test-bucket");

        String result = appConfig.getInfluxBucket();
        assertEquals("test-bucket", result);
    }

    @Test
    void testAddKafkaSecurityProps_UseOAuthToken() {
        Map<String, Object> props = new HashMap<>();
        injectFieldValue(appConfig, "useOathToken", true);
        injectFieldValue(appConfig, "kafkaKeyStoreLocation", "key-store-location");
        injectFieldValue(appConfig, "kafkTrustStoreLocation", "trust-store-location");
        injectFieldValue(appConfig, "kafkaKeyStorePassword", "key-store-password");

        appConfig.addKafkaSecurityProps(props);

        assertEquals(SecurityProtocol.SASL_SSL.name, props.get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
        assertEquals("OAUTHBEARER", props.get(SaslConfigs.SASL_MECHANISM));
        assertEquals(OAuthKafkaAuthenticateLoginCallbackHandler.class.getName(),
            props.get(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS));
        assertEquals(
            "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required unsecuredLoginStringClaim_sub=\"alice\"; ",
            props.get(SaslConfigs.SASL_JAAS_CONFIG));
    }

    @Test
    void testAddKafkaSecurityProps_SslConfig() {
        Map<String, Object> props = new HashMap<>();
        injectFieldValue(appConfig, "useOathToken", false);
        injectFieldValue(appConfig, "kafkaKeyStoreLocation", "key-store-location");
        injectFieldValue(appConfig, "kafkaKeyStoreType", "JKS");
        injectFieldValue(appConfig, "kafkaKeyStorePassword", "key-store-password");
        injectFieldValue(appConfig, "kafkTrustStoreLocation", "trust-store-location");
        injectFieldValue(appConfig, "kafkaTrustStoreType", "JKS");

        appConfig.addKafkaSecurityProps(props);

        assertEquals(SecurityProtocol.SASL_SSL.name, props.get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
        assertEquals("JKS", props.get(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG));
        assertEquals("key-store-location", props.get(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG));
        assertEquals("key-store-password", props.get(SslConfigs.SSL_KEY_PASSWORD_CONFIG));
        assertEquals("JKS", props.get(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG));
        assertEquals("trust-store-location", props.get(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
    }

    private void injectFieldValue(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}

