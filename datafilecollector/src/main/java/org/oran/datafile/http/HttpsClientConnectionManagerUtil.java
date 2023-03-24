/*-
 * ============LICENSE_START======================================================================
 * Copyright (C) 2021 Nokia. All rights reserved.
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
package org.oran.datafile.http;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.oran.datafile.exceptions.DatafileTaskException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;

/**
 * Utility class supplying connection manager for HTTPS protocol.
 *
 */
public class HttpsClientConnectionManagerUtil {

    private HttpsClientConnectionManagerUtil() {
    }

    private static final Logger logger = LoggerFactory.getLogger(HttpsClientConnectionManagerUtil.class);
    // Be aware to be less than ScheduledTasks.NUMBER_OF_WORKER_THREADS
    private static final int MAX_NUMBER_OF_CONNECTIONS = 200;
    private static PoolingHttpClientConnectionManager connectionManager;

    public static PoolingHttpClientConnectionManager instance() throws DatafileTaskException {
        if (connectionManager == null) {
            throw new DatafileTaskException("ConnectionManager has to be set or update first");
        }
        return connectionManager;
    }

    public static void setupOrUpdate(String keyCertPath, String keyCertPasswordPath, String trustedCaPath,
        String trustedCaPasswordPath, boolean useHostnameVerifier) throws DatafileTaskException {
        synchronized (HttpsClientConnectionManagerUtil.class) {
            if (connectionManager != null) {
                connectionManager.close();
                connectionManager = null;
            }
            setup(keyCertPath, keyCertPasswordPath, trustedCaPath, trustedCaPasswordPath, useHostnameVerifier);
        }
        logger.trace("HttpsConnectionManager setup or updated");
    }

    private static void setup(String keyCertPath, String keyCertPasswordPath, String trustedCaPath,
        String trustedCaPasswordPath, boolean useHostnameVerifier) throws DatafileTaskException {
        try {
            SSLContextBuilder sslBuilder = SSLContexts.custom();
            sslBuilder = supplyKeyInfo(keyCertPath, keyCertPasswordPath, sslBuilder);
            if (!trustedCaPath.isEmpty()) {
                sslBuilder = supplyTrustInfo(trustedCaPath, trustedCaPasswordPath, sslBuilder);
            }

            SSLContext sslContext = sslBuilder.build();

            HostnameVerifier hostnameVerifier =
                useHostnameVerifier ? new DefaultHostnameVerifier() : NoopHostnameVerifier.INSTANCE;

            SSLConnectionSocketFactory sslConnectionSocketFactory =
                new SSLConnectionSocketFactory(sslContext, new String[] {"TLSv1.2"}, null, hostnameVerifier);

            Registry<ConnectionSocketFactory> socketFactoryRegistry =
                RegistryBuilder.<ConnectionSocketFactory>create().register("https", sslConnectionSocketFactory).build();

            connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            connectionManager.setMaxTotal(MAX_NUMBER_OF_CONNECTIONS);

        } catch (Exception e) {
            throw new DatafileTaskException("Unable to prepare HttpsConnectionManager  : ", e);
        }
    }

    private static SSLContextBuilder supplyKeyInfo(String keyCertPath, String keyCertPasswordPath,
        SSLContextBuilder sslBuilder) throws IOException, KeyStoreException, NoSuchAlgorithmException,
        CertificateException, UnrecoverableKeyException {
        String keyPass = Files.readString(Paths.get((keyCertPasswordPath)));
        KeyStore keyFile = createKeyStore(keyCertPath, keyPass);
        return sslBuilder.loadKeyMaterial(keyFile, keyPass.toCharArray());
    }

    private static KeyStore createKeyStore(String path, String storePassword)
        throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
        logger.trace("Creating manager from file: {}", path);
        try (InputStream fis = createInputStream(path)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(fis, storePassword.toCharArray());
            return keyStore;
        }
    }

    private static InputStream createInputStream(String localFileName) throws IOException {
        FileSystemResource realResource = new FileSystemResource(Paths.get(localFileName));
        return realResource.getInputStream();
    }

    private static SSLContextBuilder supplyTrustInfo(String trustedCaPath, String trustedCaPasswordPath,
        SSLContextBuilder sslBuilder)
        throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        String trustPass = Files.readString(Paths.get((trustedCaPasswordPath)));
        File trustStoreFile = new File(trustedCaPath);
        return sslBuilder.loadTrustMaterial(trustStoreFile, trustPass.toCharArray());
    }
}
