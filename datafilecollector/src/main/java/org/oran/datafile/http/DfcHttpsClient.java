/*-
 * ============LICENSE_START======================================================================
 * Copyright (C) 2021 Nokia. All rights reserved.
 * Copyright (C) 2023 Nordix Foundation.
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

import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.oran.datafile.commons.FileCollectClient;
import org.oran.datafile.exceptions.DatafileTaskException;
import org.oran.datafile.exceptions.NonRetryableDatafileTaskException;
import org.oran.datafile.model.FileServerData;
import org.oran.datafile.oauth2.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gets file from PNF with HTTPS protocol.
 *
 */
public class DfcHttpsClient implements FileCollectClient {

    protected CloseableHttpClient httpsClient;

    private static final Logger logger = LoggerFactory.getLogger(DfcHttpsClient.class);
    private static final int FIFTEEN_SECONDS = 15 * 1000;

    private final FileServerData fileServerData;
    private final PoolingHttpClientConnectionManager connectionManager;
    private final SecurityContext securityContext;

    public DfcHttpsClient(SecurityContext securityContext, FileServerData fileServerData,
        PoolingHttpClientConnectionManager connectionManager) {
        this.fileServerData = fileServerData;
        this.connectionManager = connectionManager;
        this.securityContext = securityContext;
    }

    @Override
    public void open() {
        logger.trace("Setting httpsClient for file download.");
        SocketConfig socketConfig = SocketConfig.custom().setSoKeepAlive(true).build();

        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(FIFTEEN_SECONDS).build();

        httpsClient = HttpClients.custom().setConnectionManager(connectionManager).setDefaultSocketConfig(socketConfig)
            .setDefaultRequestConfig(requestConfig).build();

        logger.trace("httpsClient prepared for connection.");
    }

    @Override
    public void collectFile(String remoteFile, Path localFile) throws DatafileTaskException {
        logger.trace("Prepare to collectFile {}", localFile);
        HttpGet httpGet = new HttpGet(HttpUtils.prepareHttpsUri(fileServerData, remoteFile));

        String authorizationContent = this.securityContext.getBearerAuthToken();
        if (!authorizationContent.isEmpty()) {
            httpGet.addHeader("Authorization", "Bearer " + authorizationContent);
        } else if (!this.fileServerData.password.isEmpty()) {
            authorizationContent = HttpUtils.basicAuthContent(this.fileServerData.userId, this.fileServerData.password);
            httpGet.addHeader("Authorization", authorizationContent);
        }
        try {
            HttpResponse httpResponse = makeCall(httpGet);
            processResponse(httpResponse, localFile);
        } catch (IOException e) {
            logger.error("marker", e);
            throw new DatafileTaskException("Error downloading file from server. ", e);
        }
        logger.trace("HTTPS collectFile OK");
    }

    HttpResponse makeCall(HttpGet httpGet) throws IOException, DatafileTaskException {
        try {
            HttpResponse httpResponse = executeHttpClient(httpGet);
            if (isResponseOk(httpResponse)) {
                return httpResponse;
            }

            EntityUtils.consume(httpResponse.getEntity());
            if (isErrorInConnection(httpResponse)) {
                logger.warn("Failed to download file, reason: {}, code: {}",
                    httpResponse.getStatusLine().getReasonPhrase(), httpResponse.getStatusLine());
                throw new NonRetryableDatafileTaskException(HttpUtils.retryableResponse(getResponseCode(httpResponse)));
            }
            throw new DatafileTaskException(HttpUtils.nonRetryableResponse(getResponseCode(httpResponse)));
        } catch (ConnectTimeoutException | UnknownHostException | HttpHostConnectException | SSLHandshakeException
            | SSLPeerUnverifiedException e) {
            logger.warn("Unable to get file from xNF: {}", e.getMessage());
            throw new NonRetryableDatafileTaskException("Unable to get file from xNF. No retry attempts will be done.",
                e);
        }
    }

    CloseableHttpResponse executeHttpClient(HttpGet httpGet) throws IOException {
        return httpsClient.execute(httpGet);
    }

    boolean isResponseOk(HttpResponse httpResponse) {
        return getResponseCode(httpResponse) == 200;
    }

    private int getResponseCode(HttpResponse httpResponse) {
        return httpResponse.getStatusLine().getStatusCode();
    }

    boolean isErrorInConnection(HttpResponse httpResponse) {
        return getResponseCode(httpResponse) >= 400;
    }

    void processResponse(HttpResponse response, Path localFile) throws IOException {
        logger.trace("Starting to process response.");
        HttpEntity entity = response.getEntity();
        InputStream stream = entity.getContent();
        long numBytes = writeFile(localFile, stream);
        stream.close();
        EntityUtils.consume(entity);
        logger.trace("Transmission was successful - {} bytes downloaded.", numBytes);
    }

    long writeFile(Path localFile, InputStream stream) throws IOException {
        return Files.copy(stream, localFile, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void close() {
        logger.trace("Https client has ended downloading process.");
    }
}
