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

package org.oran.datafile.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.oran.datafile.oauth2.OAuthKafkaAuthenticateLoginCallbackHandler;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = {AppConfig.class})
@ExtendWith(MockitoExtension.class)
class AppConfigTest {

    @InjectMocks
    private AppConfig appConfig;

    @BeforeEach
    void setup() {
        MockitoAnnotations.initMocks(this);
    }
    @Test
    void testGetS3LocksBucket_WhenEmptyLocksBucket_ReturnsS3Bucket() {
        injectFieldValue(appConfig, "s3Bucket", "test-bucket");
        injectFieldValue(appConfig, "s3LocksBucket", "");

        String result = appConfig.getS3LocksBucket();
        assertEquals("test-bucket", result);
    }

    @Test
    void testGetS3LocksBucket_WhenNonEmptyLocksBucket_ReturnsLocksBucket() {
        injectFieldValue(appConfig, "s3Bucket", "test-bucket");
        injectFieldValue(appConfig, "s3LocksBucket", "locks");

        String result = appConfig.getS3LocksBucket();
        assertEquals("locks", result);
    }

    @Test
    void testIsS3Enabled_WhenS3EndpointAndBucketSet_ReturnsTrue() {
        injectFieldValue(appConfig, "s3Bucket", "test-bucket");
        injectFieldValue(appConfig, "s3EndpointOverride", "s3.endpoint");
        boolean result = appConfig.isS3Enabled();
        assertTrue(result);
    }

    @Test
    void testIsS3Enabled_WhenS3EndpointNotSet_ReturnsFalse() {
        injectFieldValue(appConfig, "s3Bucket", "test-bucket");
        injectFieldValue(appConfig, "s3EndpointOverride", "");
        boolean result = appConfig.isS3Enabled();
        assertFalse(result);
    }

    @Test
    void testGetKafkaBootStrapServers() {
        assertNull((new AppConfig()).getKafkaBootStrapServers());
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

    @Test
    void testGetCertificateConfiguration() {
        injectFieldValue(appConfig, "clientTrustStore", "trust-store");
        injectFieldValue(appConfig, "clientTrustStorePassword", "trust-store-password");
        injectFieldValue(appConfig, "clientKeyStore", "key-store");
        injectFieldValue(appConfig, "clientKeyStorePassword", "key-store-password");

        CertificateConfig certificateConfig = appConfig.getCertificateConfiguration();

        assertEquals("trust-store", certificateConfig.trustedCa);
        assertEquals("trust-store-password", certificateConfig.trustedCaPasswordPath);
        assertEquals("key-store", certificateConfig.keyCert);
        assertEquals("key-store-password", certificateConfig.keyPasswordPath);
    }

    @Test
    void testGetSftpConfiguration() {
        injectFieldValue(appConfig, "knownHostsFilePath", "/path/to/known_hosts");
        injectFieldValue(appConfig, "strictHostKeyChecking", true);

        SftpConfig sftpConfig = appConfig.getSftpConfiguration();

        assertEquals("/path/to/known_hosts", sftpConfig.knownHostsFilePath);
        assertTrue(sftpConfig.strictHostKeyChecking);
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
