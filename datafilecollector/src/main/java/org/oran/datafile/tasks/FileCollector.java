/*-
 * ============LICENSE_START======================================================================
 * Copyright (C) 2018-2023 Nordix Foundation. All rights reserved.
 * Copyright (C) 2020-2022 Nokia. All rights reserved.
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

package org.oran.datafile.tasks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;

import org.oran.datafile.commons.FileCollectClient;
import org.oran.datafile.configuration.AppConfig;
import org.oran.datafile.configuration.CertificateConfig;
import org.oran.datafile.exceptions.DatafileTaskException;
import org.oran.datafile.exceptions.NonRetryableDatafileTaskException;
import org.oran.datafile.ftp.FtpesClient;
import org.oran.datafile.ftp.SftpClient;
import org.oran.datafile.ftp.SftpClientSettings;
import org.oran.datafile.http.DfcHttpClient;
import org.oran.datafile.http.DfcHttpsClient;
import org.oran.datafile.http.HttpsClientConnectionManagerUtil;
import org.oran.datafile.model.Counters;
import org.oran.datafile.model.FileData;
import org.oran.datafile.model.FilePublishInformation;
import org.oran.datafile.model.FileReadyMessage;
import org.oran.datafile.oauth2.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Collects a file from a PNF.
 */
public class FileCollector {

    private static final Logger logger = LoggerFactory.getLogger(FileCollector.class);
    private final AppConfig appConfig;
    private final Counters counters;
    private final SecurityContext securityContext;

    /**
     * Constructor.
     *
     * @param appConfig application configuration
     */
    public FileCollector(SecurityContext securityContext, AppConfig appConfig, Counters counters) {
        this.appConfig = appConfig;
        this.counters = counters;
        this.securityContext = securityContext;
    }

    /**
     * Collects a file from the PNF and stores it in the local file system.
     *
     * @param fileData data about the file to collect.
     * @param numRetries the number of retries if the publishing fails
     * @param firstBackoff the time to delay the first retry
     * @param contextMap context for logging.
     * @return the data needed to publish the file.
     */
    public Mono<FilePublishInformation> collectFile(FileData fileData, long numRetries, Duration firstBackoff) {

        logger.trace("Entering collectFile with {}", fileData);

        return Mono.just(fileData) //
            .cache() //
            .flatMap(fd -> tryCollectFile(fileData)) //
            .retryWhen(Retry.backoff(numRetries, firstBackoff)) //
            .flatMap(FileCollector::checkCollectedFile);
    }

    private static Mono<FilePublishInformation> checkCollectedFile(Optional<FilePublishInformation> info) {
        if (info.isPresent()) {
            return Mono.just(info.get());
        } else {
            // If there is no info, the file is not retrievable
            return Mono.error(new DatafileTaskException("Non retryable file transfer failure"));
        }
    }

    private Mono<Optional<FilePublishInformation>> tryCollectFile(FileData fileData) {
        logger.trace("starting to collectFile {}", fileData.fileInfo.name);

        final String remoteFile = fileData.remoteFilePath();
        final Path localFile = fileData.getLocalFilePath(this.appConfig);

        try (FileCollectClient currentClient = createClient(fileData)) {
            currentClient.open();
            Files.createDirectories(localFile.getParent());
            currentClient.collectFile(remoteFile, localFile);
            counters.incNoOfCollectedFiles();
            return Mono.just(Optional.of(createFilePublishInformation(fileData)));
        } catch (NonRetryableDatafileTaskException nre) {
            logger.warn("Failed to download file, not retryable: {} {}, reason: {}", fileData.sourceName(),
                fileData.fileInfo.name, nre.getMessage());
            incFailedAttemptsCounter(fileData);
            return Mono.just(Optional.empty()); // Give up
        } catch (DatafileTaskException e) {
            logger.warn("Failed to download file: {} {}, reason: {}", fileData.sourceName(), fileData.fileInfo.name,
                e.getMessage());
            incFailedAttemptsCounter(fileData);
            return Mono.error(e);
        } catch (Exception throwable) {
            logger.warn("Failed to close client: {} {}, reason: {}", fileData.sourceName(), fileData.fileInfo.name,
                throwable.getMessage(), throwable);
            return Mono.just(Optional.of(createFilePublishInformation(fileData)));
        }
    }

    private void incFailedAttemptsCounter(FileData fileData) {
        if (FileData.Scheme.isFtpScheme(fileData.scheme())) {
            counters.incNoOfFailedFtpAttempts();
        } else {
            counters.incNoOfFailedHttpAttempts();
        }
    }

    private FileCollectClient createClient(FileData fileData) throws DatafileTaskException {
        switch (fileData.scheme()) {
            case SFTP:
                return createSftpClient(fileData);
            case FTPES:
                return createFtpesClient(fileData);
            case HTTP:
                return createHttpClient(fileData);
            case HTTPS:
                return createHttpsClient(fileData);
            default:
                throw new DatafileTaskException("Unhandled protocol: " + fileData.scheme());
        }
    }

    public FilePublishInformation createFilePublishInformation(FileData fileData) {
        FileReadyMessage.MessageMetaData metaData = fileData.messageMetaData;
        return FilePublishInformation.builder() //
            .productName(metaData.productName()) //
            .vendorName(metaData.vendorName()) //
            .lastEpochMicrosec(metaData.lastEpochMicrosec) //
            .sourceName(metaData.sourceName) //
            .startEpochMicrosec(metaData.startEpochMicrosec) //
            .timeZoneOffset(metaData.timeZoneOffset) //
            .name(metaData.sourceName + "/" + fileData.fileInfo.name) //
            .compression(fileData.fileInfo.hashMap.compression) //
            .fileFormatType(fileData.fileInfo.hashMap.fileFormatType) //
            .fileFormatVersion(fileData.fileInfo.hashMap.fileFormatVersion) //
            .changeIdentifier(fileData.messageMetaData.changeIdentifier) //
            .objectStoreBucket(this.appConfig.isS3Enabled() ? this.appConfig.getS3Bucket() : null) //
            .build();
    }

    protected SftpClient createSftpClient(FileData fileData) {
        return new SftpClient(fileData.fileServerData(), new SftpClientSettings(appConfig.getSftpConfiguration()));
    }

    protected FtpesClient createFtpesClient(FileData fileData) throws DatafileTaskException {
        CertificateConfig config = appConfig.getCertificateConfiguration();
        Path trustedCa = config.trustedCa.isEmpty() ? null : Paths.get(config.trustedCa);

        return new FtpesClient(fileData.fileServerData(), Paths.get(config.keyCert), config.keyPasswordPath, trustedCa,
            config.trustedCaPasswordPath);
    }

    protected FileCollectClient createHttpClient(FileData fileData) {
        return new DfcHttpClient(securityContext, fileData.fileServerData());
    }

    protected FileCollectClient createHttpsClient(FileData fileData) throws DatafileTaskException {
        return new DfcHttpsClient(securityContext, fileData.fileServerData(),
            HttpsClientConnectionManagerUtil.instance());
    }
}
