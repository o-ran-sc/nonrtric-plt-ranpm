/*-
 * ============LICENSE_START======================================================================
 * Copyright (C) 2020 Nokia. All rights reserved.
 * ===============================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * ============LICENSE_END========================================================================
 */

package org.oran.datafile.ftp;

import java.io.File;

import org.oran.datafile.configuration.SftpConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SftpClientSettings {

    private static final Logger logger = LoggerFactory.getLogger(SftpClientSettings.class);

    private final SftpConfig sftpConfig;

    public SftpClientSettings(SftpConfig sftpConfig) {
        this.sftpConfig = sftpConfig;
    }

    public boolean shouldUseStrictHostChecking() {
        boolean strictHostKeyChecking = false;
        if (this.sftpConfig.strictHostKeyChecking) {
            File file = new File(getKnownHostsFilePath());
            strictHostKeyChecking = file.isFile();
            logUsageOfStrictHostCheckingFlag(strictHostKeyChecking, file.getAbsolutePath());
        } else {
            logger.info("StrictHostKeyChecking will be disabled.");
        }
        return strictHostKeyChecking;
    }

    public String getKnownHostsFilePath() {
        return this.sftpConfig.knownHostsFilePath;
    }

    private void logUsageOfStrictHostCheckingFlag(boolean strictHostKeyChecking, String filePath) {
        if (strictHostKeyChecking) {
            logger.info("StrictHostKeyChecking will be enabled with KNOWN_HOSTS_FILE_PATH [{}].", filePath);
        } else {
            logger.warn(
                "StrictHostKeyChecking is enabled but environment variable KNOWN_HOSTS_FILE_PATH is not set or points to not existing file [{}]  -->  falling back to StrictHostKeyChecking='no'.",
                filePath);
        }
    }
}
