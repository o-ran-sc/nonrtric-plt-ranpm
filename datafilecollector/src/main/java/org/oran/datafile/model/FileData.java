/*-
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2019-2023 Nordix Foundation.
 *  Copyright (C) 2021 Nokia. All rights reserved.
 *  Copyright (C) 2023-2025 OpenInfra Foundation Europe. All rights reserved.
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

package org.oran.datafile.model;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import lombok.Builder;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.oran.datafile.configuration.AppConfig;
import org.oran.datafile.exceptions.DatafileTaskException;
import org.oran.datafile.model.FileServerData.FileServerDataBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains data, from the fileReady event, about the file to collect from the
 * xNF.
 *
 */
@Builder
public class FileData {

    public enum Scheme {
        FTPES, SFTP, HTTP, HTTPS;

        public static final String DFC_DOES_NOT_SUPPORT_PROTOCOL_ERROR_MSG = "DFC does not support protocol ";
        public static final String SUPPORTED_PROTOCOLS_ERROR_MESSAGE =
            ". Supported protocols are FTPeS, sFTP, HTTP and HTTPS";

        /**
         * Get a <code>Scheme</code> from a string.
         *
         * @param schemeString the string to convert to <code>Scheme</code>.
         * @return The corresponding <code>Scheme</code>
         * @throws DatafileTaskException if the value of the string doesn't match any
         *         defined scheme.
         */
        public static Scheme getSchemeFromString(String schemeString) throws DatafileTaskException {
            Scheme result;
            if ("FTPES".equalsIgnoreCase(schemeString)) {
                result = Scheme.FTPES;
            } else if ("SFTP".equalsIgnoreCase(schemeString)) {
                result = Scheme.SFTP;
            } else if ("HTTP".equalsIgnoreCase(schemeString)) {
                result = Scheme.HTTP;
            } else if ("HTTPS".equalsIgnoreCase(schemeString)) {
                result = Scheme.HTTPS;
            } else {
                throw new DatafileTaskException(
                    DFC_DOES_NOT_SUPPORT_PROTOCOL_ERROR_MSG + schemeString + SUPPORTED_PROTOCOLS_ERROR_MESSAGE);
            }
            return result;
        }

        public static boolean isFtpScheme(Scheme scheme) {
            return scheme == SFTP || scheme == FTPES;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(FileData.class);

    @SuppressWarnings("java:S1104")
    public DefaultFileReadyMessage.ArrayOfNamedHashMap defaultFileInfo;

    @SuppressWarnings("java:S1104")
    public TS28532FileReadyMessage.FileInfo ts28532FileInfo;

    @SuppressWarnings("java:S1104")
    public DefaultFileReadyMessage.MessageMetaData messageMetaData;

    @SuppressWarnings("java:S1104")
    public TS28532FileReadyMessage.MessageMetaData ts28532MessageMetaData;

    private String fileReadyMessageClass;

    public static Iterable<FileData> createFileData(FileReadyMessage msg, String fileReadyMessageClass) {
        Collection<FileData> res = new ArrayList<>();

        if(fileReadyMessageClass == null || fileReadyMessageClass.isEmpty()) {
            DefaultFileReadyMessage defaultMsg = (DefaultFileReadyMessage) msg;
            for (DefaultFileReadyMessage.ArrayOfNamedHashMap arr : defaultMsg.event.notificationFields.arrayOfNamedHashMap) {
                FileData data = FileData.builder().defaultFileInfo(arr).fileReadyMessageClass(fileReadyMessageClass).messageMetaData(defaultMsg.event.commonEventHeader).build();
                res.add(data);
            }
        } else {
            TS28532FileReadyMessage ts28532DefaultMsg = (TS28532FileReadyMessage) msg;
            for (TS28532FileReadyMessage.FileInfo arr : ts28532DefaultMsg.event.stndDefinedFields.data.fileInfoList) {
                FileData data = FileData.builder().ts28532FileInfo(arr).fileReadyMessageClass(fileReadyMessageClass).ts28532MessageMetaData(ts28532DefaultMsg.event.commonEventHeader).build();
                res.add(data);
            }
        }
        return res;
    }

    /**
     * Get the name of the PNF, must be unique in the network.
     *
     * @return the name of the PNF, must be unique in the network
     */
    public String sourceName() {
        if(fileReadyMessageClass == null || fileReadyMessageClass.isEmpty()) {
            return this.messageMetaData.sourceName;
        } else {
            return this.ts28532MessageMetaData.sourceName;
        }
    }

    public String name() {
        if(fileReadyMessageClass == null || fileReadyMessageClass.isEmpty()) {
            return this.messageMetaData.sourceName + "/" + defaultFileInfo.name;
        } else {
            return this.ts28532MessageMetaData.sourceName + "/" + ts28532FileInfo.fileName();
        }
    }

    /**
     * Get the path to file to get from the PNF.
     *
     * @return the path to the file on the PNF.
     */
    public String remoteFilePath() {
        return getLocationURI().getPath();
    }

    public Scheme scheme() {
        URI uri = getLocationURI();
        try {
            return Scheme.getSchemeFromString(uri.getScheme());
        } catch (Exception e) {
            logger.warn("Could noit get scheme :{}", e.getMessage());
            return Scheme.FTPES;
        }
    }

    private URI getLocationURI() {
        if(fileReadyMessageClass == null || fileReadyMessageClass.isEmpty()) {
            return URI.create(defaultFileInfo.hashMap.location);
        } else {
            return URI.create(ts28532FileInfo.fileLocation);
        }
    }

    /**
     * Get the path to the locally stored file.
     *
     * @return the path to the locally stored file.
     */
    public Path getLocalFilePath(AppConfig config) {
        if(fileReadyMessageClass == null || fileReadyMessageClass.isEmpty()) {
            return Paths.get(config.getCollectedFilesPath(), this.messageMetaData.sourceName, defaultFileInfo.name);
        } else {
            return Paths.get(config.getCollectedFilesPath(), this.ts28532MessageMetaData.sourceName, ts28532FileInfo.fileName());
        }
    }

    /**
     * Get the data about the file server where the file should be collected from.
     * Query data included as it can contain JWT token
     *
     * @return the data about the file server where the file should be collected
     *         from.
     */
    public FileServerData fileServerData() {
        URI uri = getLocationURI();
        Optional<String[]> userInfo = getUserNameAndPasswordIfGiven(uri.getUserInfo());

        FileServerDataBuilder builder = FileServerData.builder() //
            .serverAddress(uri.getHost()) //
            .userId(userInfo.isPresent() ? userInfo.get()[0] : "") //
            .password(userInfo.isPresent() ? userInfo.get()[1] : "");
        if (uri.getPort() > 0) {
            builder.port(uri.getPort());
        }
        URIBuilder uriBuilder = new URIBuilder(uri);
        List<NameValuePair> query = uriBuilder.getQueryParams();
        if (query != null && !query.isEmpty()) {
            builder.queryParameters(query);
        }
        String fragment = uri.getRawFragment();
        if (fragment != null && fragment.length() > 0) {
            builder.uriRawFragment(fragment);
        }
        return builder.build();
    }

    /**
     * Extracts user name and password from the user info, if it they are given in
     * the URI.
     *
     * @param userInfoString the user info string from the URI.
     *
     * @return An <code>Optional</code> containing a String array with the user name
     *         and password if given, or an empty
     *         <code>Optional</code> if not given.
     */
    private static Optional<String[]> getUserNameAndPasswordIfGiven(String userInfoString) {
        if (userInfoString != null) {
            String[] userAndPassword = userInfoString.split(":");
            if (userAndPassword.length == 2) {
                return Optional.of(userAndPassword);
            } else if (userAndPassword.length == 1)// if just user
            {
                String[] tab = new String[2];
                tab[0] = userAndPassword[0];
                tab[1] = "";// add empty password
                return Optional.of(tab);
            }
        }
        return Optional.empty();
    }
}
