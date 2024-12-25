/*-
 * ============LICENSE_START======================================================================
 * Copyright (C) 2018-2023 Nordix Foundation. All rights reserved.
 * Copyright (C) 2020-2021 Nokia. All rights reserved.
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

package org.oran.datafile.ftp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.oran.datafile.commons.FileCollectClient;
import org.oran.datafile.exceptions.DatafileTaskException;
import org.oran.datafile.exceptions.NonRetryableDatafileTaskException;
import org.oran.datafile.model.FileServerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;

/**
 * Gets file from PNF with FTPS protocol.
 */
public class FtpesClient implements FileCollectClient {
    private static final Logger logger = LoggerFactory.getLogger(FtpesClient.class);

    private static final int DEFAULT_PORT = 21;

    FTPSClient realFtpsClient = new FTPSClient();
    private final FileServerData fileServerData;
    private static TrustManager theTrustManager = null;
    private static KeyManager theKeyManager = null;

    private final Path keyCertPath;
    private final String keyCertPasswordPath;
    private final Path trustedCaPath;
    private final String trustedCaPasswordPath;

    /**
     * Constructor.
     *
     * @param fileServerData info needed to connect to the PNF.
     * @param keyCertPath path to DFC's key cert.
     * @param keyCertPasswordPath path of file containing password for DFC's key
     *        cert.
     * @param trustedCaPath path to the PNF's trusted keystore.
     * @param trustedCaPasswordPath path of file containing password for the PNF's
     *        trusted keystore.
     */
    public FtpesClient(FileServerData fileServerData, Path keyCertPath, String keyCertPasswordPath, Path trustedCaPath,
        String trustedCaPasswordPath) {
        this.fileServerData = fileServerData;
        this.keyCertPath = keyCertPath;
        this.keyCertPasswordPath = keyCertPasswordPath;
        this.trustedCaPath = trustedCaPath;
        this.trustedCaPasswordPath = trustedCaPasswordPath;
    }

    @Override
    public void open() throws DatafileTaskException {
        try {
            realFtpsClient.setNeedClientAuth(trustedCaPath != null);
            realFtpsClient.setKeyManager(getKeyManager(keyCertPath, keyCertPasswordPath));
            realFtpsClient.setTrustManager(getTrustManager(trustedCaPath, trustedCaPasswordPath));
            setUpConnection();
        } catch (DatafileTaskException e) {
            throw e;
        } catch (Exception e) {
            throw new DatafileTaskException("Could not open connection: " + e, e);
        }
    }

    @Override
    public void close() {
        logger.trace("starting to closeDownConnection");
        if (realFtpsClient.isConnected()) {
            try {
                boolean logOut = realFtpsClient.logout();
                logger.trace("logOut: {}", logOut);
            } catch (Exception e) {
                logger.trace("Unable to logout connection.", e);
            }
            try {
                realFtpsClient.disconnect();
                logger.trace("disconnected!");
            } catch (Exception e) {
                logger.trace("Unable to disconnect connection.", e);
            }
        }
    }

    @Override
    public void collectFile(String remoteFileName, Path localFileName) throws DatafileTaskException {
        logger.trace("collectFile called");

        try (OutputStream output = createOutputStream(localFileName)) {
            logger.trace("begin to retrieve from xNF.");
            if (!realFtpsClient.retrieveFile(remoteFileName, output)) {
                throw new NonRetryableDatafileTaskException(
                    "Could not retrieve file. No retry attempts will be done, file :" + remoteFileName);
            }
        } catch (IOException e) {
            throw new DatafileTaskException("Could not fetch file: " + e, e);
        }
        logger.trace("collectFile fetched: {}", localFileName);
    }

    private static int getPort(Integer port) {
        return port != null ? port : DEFAULT_PORT;
    }

    private void setUpConnection() throws DatafileTaskException, IOException {

        realFtpsClient.connect(fileServerData.serverAddress, getPort(fileServerData.port));
        logger.trace("after ftp connect");

        if (!realFtpsClient.login(fileServerData.userId, fileServerData.password)) {
            throw new DatafileTaskException("Unable to log in to xNF. " + fileServerData.serverAddress);
        }

        if (FTPReply.isPositiveCompletion(realFtpsClient.getReplyCode())) {
            realFtpsClient.enterLocalPassiveMode();
            realFtpsClient.setFileType(FTP.BINARY_FILE_TYPE);
            // Set protection buffer size
            realFtpsClient.execPBSZ(0);
            // Set data channel protection to private
            realFtpsClient.execPROT("P");
            realFtpsClient.setBufferSize(1024 * 1024);
        } else {
            throw new DatafileTaskException("Unable to connect to xNF. " + fileServerData.serverAddress
                + " xNF reply code: " + realFtpsClient.getReplyCode());
        }

        logger.trace("setUpConnection successfully!");
    }

    private TrustManager createTrustManager(Path trustedCaPath, String trustedCaPassword)
        throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
        logger.trace("Creating trust manager from file: {}", trustedCaPath);
        try (InputStream fis = createInputStream(trustedCaPath)) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(fis, trustedCaPassword.toCharArray());
            TrustManagerFactory factory = TrustManagerFactory.getInstance("SunX509");
            factory.init(keyStore);
            return factory.getTrustManagers()[0];
        }
    }

    protected InputStream createInputStream(Path localFileName) throws IOException {
        FileSystemResource realResource = new FileSystemResource(localFileName);
        return realResource.getInputStream();
    }

    protected OutputStream createOutputStream(Path localFileName) throws IOException, DatafileTaskException {
        File localFile = localFileName.toFile();
        if (!localFile.createNewFile()) {
            logger.debug("Local file {} already created", localFileName);
            throw new NonRetryableDatafileTaskException("Local file already created: " + localFileName);
        }
        OutputStream output = new FileOutputStream(localFile);
        logger.trace("File {} opened xNF", localFileName);
        return output;
    }

    protected TrustManager getTrustManager(Path trustedCaPath, String trustedCaPasswordPath)
        throws KeyStoreException, NoSuchAlgorithmException, IOException, CertificateException {
        synchronized (FtpesClient.class) {
            if (theTrustManager == null && trustedCaPath != null) {
                String trustedCaPassword = Files.readString(Paths.get((trustedCaPasswordPath)));
                theTrustManager = createTrustManager(trustedCaPath, trustedCaPassword);
            }
            return theTrustManager;
        }
    }

    protected KeyManager getKeyManager(Path keyCertPath, String keyCertPasswordPath)
        throws IOException, GeneralSecurityException {

        synchronized (FtpesClient.class) {
            if (theKeyManager == null) {
                String keyCertPassword = Files.readString(Paths.get((keyCertPasswordPath)));
                theKeyManager = createKeyManager(keyCertPath, keyCertPassword);
            }
            return theKeyManager;
        }
    }

    private KeyManager createKeyManager(Path keyCertPath, String keyCertPassword) throws IOException, KeyStoreException,
        NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {
        logger.trace("Creating key manager from file: {}", keyCertPath);
        try (InputStream fis = createInputStream(keyCertPath)) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(fis, keyCertPassword.toCharArray());
            KeyManagerFactory factory = KeyManagerFactory.getInstance("SunX509");
            factory.init(keyStore, keyCertPassword.toCharArray());
            return factory.getKeyManagers()[0];
        }
    }
}
