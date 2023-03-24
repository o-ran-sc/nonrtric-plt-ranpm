/*-
 * ============LICENSE_START======================================================================
 * Copyright (C) 2018-2023 Nordix Foundation, 2020 Nokia. All rights reserved.
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

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.nio.file.Path;

import org.oran.datafile.commons.FileCollectClient;
import org.oran.datafile.exceptions.DatafileTaskException;
import org.oran.datafile.exceptions.NonRetryableDatafileTaskException;
import org.oran.datafile.model.FileServerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gets file from xNF with SFTP protocol.
 *
 */
public class SftpClient implements FileCollectClient {

    private static final Logger logger = LoggerFactory.getLogger(SftpClient.class);

    private static final int SFTP_DEFAULT_PORT = 22;
    private static final String STRICT_HOST_KEY_CHECKING = "StrictHostKeyChecking";

    private final FileServerData fileServerData;
    protected Session session = null;
    protected ChannelSftp sftpChannel = null;
    private final SftpClientSettings settings;

    public SftpClient(FileServerData fileServerData, SftpClientSettings sftpConfig) {
        this.fileServerData = fileServerData;
        this.settings = sftpConfig;
    }

    @Override
    public void collectFile(String remoteFile, Path localFile) throws DatafileTaskException {
        logger.trace("collectFile {}", localFile);

        try {
            sftpChannel.get(remoteFile, localFile.toString());
            logger.trace("File {} Download successful from xNF", localFile.getFileName());
        } catch (SftpException e) {
            boolean retry = e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE && e.id != ChannelSftp.SSH_FX_PERMISSION_DENIED
                && e.id != ChannelSftp.SSH_FX_OP_UNSUPPORTED;
            if (retry) {
                throw new DatafileTaskException("Unable to get file from xNF. Data: " + fileServerData, e);
            } else {
                throw new NonRetryableDatafileTaskException(
                    "Unable to get file from xNF. No retry attempts will be done. Data: " + fileServerData, e);
            }
        }

        logger.trace("collectFile OK");
    }

    @Override
    public void close() {
        logger.trace("closing sftp session");
        if (sftpChannel != null) {
            sftpChannel.exit();
            sftpChannel = null;
        }
        if (session != null) {
            session.disconnect();
            session = null;
        }
    }

    @Override
    public void open() throws DatafileTaskException {
        try {
            if (session == null) {
                session = setUpSession(fileServerData);
                sftpChannel = getChannel(session);
            }
        } catch (JSchException e) {
            boolean retry = !e.getMessage().contains("Auth fail");
            if (retry) {
                throw new DatafileTaskException("Could not open Sftp client. " + e);
            } else {
                throw new NonRetryableDatafileTaskException(
                    "Could not open Sftp client, no retry attempts will be done. " + e);
            }
        }
    }

    JSch createJsch() {
        return new JSch();
    }

    private int getPort(Integer port) {
        return port != null ? port : SFTP_DEFAULT_PORT;
    }

    private Session setUpSession(FileServerData fileServerData) throws JSchException {
        boolean useStrictHostChecking = this.settings.shouldUseStrictHostChecking();
        JSch jsch = createJschClient(useStrictHostChecking);
        return createJshSession(jsch, fileServerData, useStrictHostChecking);
    }

    private JSch createJschClient(boolean useStrictHostChecking) throws JSchException {
        JSch jsch = createJsch();
        if (useStrictHostChecking) {
            jsch.setKnownHosts(this.settings.getKnownHostsFilePath());
        }
        return jsch;
    }

    private Session createJshSession(JSch jsch, FileServerData fileServerData, boolean useStrictHostKeyChecking)
        throws JSchException {
        Session newSession =
            jsch.getSession(fileServerData.userId, fileServerData.serverAddress, getPort(fileServerData.port));
        newSession.setConfig(STRICT_HOST_KEY_CHECKING, toYesNo(useStrictHostKeyChecking));
        newSession.setPassword(fileServerData.password);
        newSession.connect();
        return newSession;
    }

    private String toYesNo(boolean useStrictHostKeyChecking) {
        return useStrictHostKeyChecking ? "yes" : "no";
    }

    private ChannelSftp getChannel(Session session) throws JSchException {
        Channel channel = session.openChannel("sftp");
        channel.connect();
        return (ChannelSftp) channel;
    }

}
