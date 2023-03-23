/*-
 * ============LICENSE_START======================================================================
 * Copyright (C) 2018, 2020-2022 Nokia. All rights reserved.
 * Copyright (C) 2018-2023 Nordix Foundation. All rights reserved.
 * ===============================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 * ============LICENSE_END========================================================================
 */

package org.oran.datafile.configuration;

import java.util.Map;

import lombok.Getter;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.oran.datafile.oauth2.OAuthKafkaAuthenticateLoginCallbackHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Holds all configuration for the DFC.
 */

@Component
@EnableConfigurationProperties
public class AppConfig {

    @Value("${app.kafka.bootstrap-servers:}")
    private String kafkaBootStrapServers;

    @Value("${app.kafka.collected-file-topic:}")
    @Getter
    private String collectedFileTopic;

    @Value("${app.kafka.file-ready-event-topic:}")
    @Getter
    private String inputTopic;

    @Value("${app.kafka.client-id:undefined}")
    @Getter
    private String kafkaClientId;

    @Value("${app.collected-files-path}")
    @Getter
    private String collectedFilesPath;

    @Value("${app.sftp.strict-host-key-checking:false}")
    private boolean strictHostKeyChecking;

    @Value("${app.sftp.known-hosts-file-path:}")
    @Getter
    private String knownHostsFilePath;

    @Value("${app.ssl.key-store-password-file}")
    private String clientKeyStorePassword = "";

    @Value("${app.ssl.key-store:}")
    private String clientKeyStore = "";

    @Value("${app.ssl.trust-store:}")
    private String clientTrustStore = "";

    @Value("${app.ssl.trust-store-password-file:}")
    private String clientTrustStorePassword;

    @Getter
    @Value("${app.s3.endpointOverride:}")
    private String s3EndpointOverride;

    @Getter
    @Value("${app.s3.accessKeyId:}")
    private String s3AccessKeyId;

    @Getter
    @Value("${app.s3.secretAccessKey:}")
    private String s3SecretAccessKey;

    @Getter
    @Value("${app.s3.bucket:}")
    private String s3Bucket;

    @Value("${app.s3.locksBucket:}")
    private String s3LocksBucket;

    @Value("${app.number-of-worker-treads:200}")
    @Getter
    private int noOfWorkerThreads;

    @Value("${app.kafka.ssl.key-store-location}")
    private String kafkaKeyStoreLocation;

    @Value("${app.kafka.ssl.key-store-type}")
    private String kafkaKeyStoreType;

    @Value("${app.kafka.ssl.key-store-password}")
    private String kafkaKeyStorePassword;

    @Value("${app.kafka.ssl.trust-store-type}")
    private String kafkaTrustStoreType;

    @Value("${app.kafka.ssl.trust-store-location}")
    private String kafkTrustStoreLocation;

    @Value("${app.kafka.use-oath-token}")
    private boolean useOathToken;

    public String getS3LocksBucket() {
        return s3LocksBucket.isEmpty() ? s3Bucket : s3LocksBucket;
    }

    public boolean isS3Enabled() {
        return !s3EndpointOverride.isEmpty() && !s3Bucket.isEmpty();
    }

    public String getKafkaBootStrapServers() {
        return kafkaBootStrapServers;
    }

    public synchronized CertificateConfig getCertificateConfiguration() {
        return CertificateConfig.builder() //
            .trustedCa(this.clientTrustStore) //
            .trustedCaPasswordPath(this.clientTrustStorePassword) //
            .keyCert(this.clientKeyStore) //
            .keyPasswordPath(this.clientKeyStorePassword) //
            .build();
    }

    public synchronized SftpConfig getSftpConfiguration() {
        return SftpConfig.builder() //
            .knownHostsFilePath(this.knownHostsFilePath) //
            .strictHostKeyChecking(this.strictHostKeyChecking) //
            .build();
    }

    public void addKafkaSecurityProps(Map<String, Object> props) {

        if (useOathToken) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SASL_PLAINTEXT.name);
            props.put(SaslConfigs.SASL_MECHANISM, "OAUTHBEARER");
            props.put(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS,
                OAuthKafkaAuthenticateLoginCallbackHandler.class.getName());
            props.put(SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required unsecuredLoginStringClaim_sub=\"alice\"; ");
        }
        if (!kafkaKeyStoreLocation.isEmpty()) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SASL_SSL.name);
            // SSL
            props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, kafkaKeyStoreType);
            props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, kafkaKeyStoreLocation);
            if (!kafkaKeyStorePassword.isEmpty()) {
                props.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, kafkaKeyStorePassword);
            }
            if (!kafkTrustStoreLocation.isEmpty()) {
                props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, kafkaTrustStoreType);
                props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, kafkTrustStoreLocation);
            }
        }
    }

}
