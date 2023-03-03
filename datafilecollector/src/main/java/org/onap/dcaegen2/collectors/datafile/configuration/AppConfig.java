/*-
 * ============LICENSE_START======================================================================
 * Copyright (C) 2018, 2020-2022 Nokia. All rights reserved.
 * Copyright (C) 2018-2019 Nordix Foundation. All rights reserved.
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

package org.onap.dcaegen2.collectors.datafile.configuration;

import java.util.Properties;

import lombok.Getter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Holds all configuration for the DFC.
 *
 * @author <a href="mailto:przemyslaw.wasala@nokia.com">Przemysław Wąsala</a> on
 *         3/23/18
 * @author <a href="mailto:henrik.b.andersson@est.tech">Henrik Andersson</a>
 */

@Component
@EnableConfigurationProperties
public class AppConfig {

    @Value("#{systemEnvironment}")
    Properties systemEnvironment;

    @Value("${app.filepath}")
    String filepath;

    @Value("${app.kafka.bootstrap-servers:}")
    private String kafkaBootStrapServers;

    @Value("${app.kafka.collected-file-topic:}")
    public String collectedFileTopic;

    @Value("${app.kafka.file-ready-event-topic:}")
    public String fileReadyEventTopic;

    @Value("${app.kafka.client-id:undefined}")
    public String kafkaClientId;

    @Value("${app.collected-files-path:}")
    public String collectedFilesPath;

    @Value("${app.sftp.strict-host-key-checking:false}")
    public boolean strictHostKeyChecking;

    @Value("${app.sftp.known-hosts-file-path:}")
    public String knownHostsFilePath;

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

}
