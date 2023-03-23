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

package org.oran.pmproducer.configuration;

import java.lang.invoke.MethodHandles;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.oran.pmproducer.configuration.WebClientConfig.HttpProxyConfig;
import org.oran.pmproducer.oauth2.OAuthKafkaAuthenticateLoginCallbackHandler;
import org.oran.pmproducer.repository.InfoType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties
@ToString
public class ApplicationConfig {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Getter
    @Value("${app.configuration-filepath}")
    private String localConfigurationFilePath;

    @Value("${server.ssl.key-store-type}")
    private String sslKeyStoreType = "";

    @Value("${server.ssl.key-store-password}")
    private String sslKeyStorePassword = "";

    @Value("${server.ssl.key-store}")
    private String sslKeyStore = "";

    @Value("${server.ssl.key-password}")
    private String sslKeyPassword = "";

    @Value("${app.webclient.trust-store-used}")
    private boolean sslTrustStoreUsed = false;

    @Value("${app.webclient.trust-store-password}")
    private String sslTrustStorePassword = "";

    @Value("${app.webclient.trust-store}")
    private String sslTrustStore = "";

    @Value("${app.webclient.http.proxy-host}")
    private String httpProxyHost = "";

    @Value("${app.webclient.http.proxy-port}")
    private int httpProxyPort = 0;

    @Getter
    @Setter
    @Value("${server.port}")
    private int localServerHttpPort;

    @Getter
    @Value("${app.ics-base-url}")
    private String icsBaseUrl;

    @Getter
    @Value("${app.pm-producer-base-url}")
    private String selfUrl;

    @Getter
    @Value("${app.kafka.bootstrap-servers:}")
    private String kafkaBootStrapServers;

    @Getter
    @Value("${app.kafka.max-poll-records:300}")
    private int kafkaMaxPollRecords;

    @Getter
    @Value("${app.pm-files-path}")
    private String pmFilesPath;

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
    @Value("${app.s3.locksBucket:}")
    private String s3LocksBucket;

    @Getter
    @Value("${app.s3.bucket:}")
    private String s3Bucket;

    @Getter
    @Setter
    @Value("${app.zip-output}")
    private boolean zipOutput;

    @Value("${app.kafka.ssl.key-store-type}")
    private String kafkaKeyStoreType;

    @Value("${app.kafka.ssl.key-store-location}")
    private String kafkaKeyStoreLocation;

    @Value("${app.kafka.ssl.key-store-password}")
    private String kafkaKeyStorePassword;

    @Value("${app.kafka.ssl.trust-store-type}")
    private String kafkaTrustStoreType;

    @Value("${app.kafka.ssl.trust-store-location}")
    private String kafkTrustStoreLocation;

    @Value("${app.kafka.use-oath-token}")
    private boolean useOathToken;

    private WebClientConfig webClientConfig = null;

    public WebClientConfig getWebClientConfig() {
        if (this.webClientConfig == null) {
            HttpProxyConfig httpProxyConfig = HttpProxyConfig.builder() //
                    .httpProxyHost(this.httpProxyHost) //
                    .httpProxyPort(this.httpProxyPort) //
                    .build();

            this.webClientConfig = WebClientConfig.builder() //
                    .keyStoreType(this.sslKeyStoreType) //
                    .keyStorePassword(this.sslKeyStorePassword) //
                    .keyStore(this.sslKeyStore) //
                    .keyPassword(this.sslKeyPassword) //
                    .isTrustStoreUsed(this.sslTrustStoreUsed) //
                    .trustStore(this.sslTrustStore) //
                    .trustStorePassword(this.sslTrustStorePassword) //
                    .httpProxyConfig(httpProxyConfig) //
                    .build();
        }
        return this.webClientConfig;
    }

    public boolean isS3Enabled() {
        return !(s3EndpointOverride.isBlank() || s3Bucket.isBlank());
    }

    // Adapter to parse the json format of the configuration file.
    static class ConfigFile {
        Collection<InfoType> types;
    }

    public Collection<InfoType> getTypes() {
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
        try {
            String configJson = Files.readString(Path.of(getLocalConfigurationFilePath()), Charset.defaultCharset());
            ConfigFile configData = gson.fromJson(configJson, ConfigFile.class);
            return configData.types;
        } catch (Exception e) {
            logger.error("Could not load configuration file {}", getLocalConfigurationFilePath());
            return Collections.emptyList();
        }
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
