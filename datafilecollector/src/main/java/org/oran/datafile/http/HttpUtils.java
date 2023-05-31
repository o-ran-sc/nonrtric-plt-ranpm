/*-
 * ============LICENSE_START======================================================================
 * Copyright (C) 2018-2023 Nordix Foundation. All rights reserved.
 * Modifications Copyright (C) 2020-2021 Nokia. All rights reserved
 * ===============================================================================================
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
 * ============LICENSE_END========================================================================
 */

package org.oran.datafile.http;

import java.util.Base64;
import java.util.List;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.http.HttpStatus;
import org.oran.datafile.model.FileServerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HttpUtils implements HttpStatus {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);
    public static final int HTTP_DEFAULT_PORT = 80;
    public static final int HTTPS_DEFAULT_PORT = 443;

    private HttpUtils() {
    }

    public static String nonRetryableResponse(int responseCode) {
        return "Unexpected response code - " + responseCode;
    }

    public static String retryableResponse(int responseCode) {
        return "Unexpected response code - " + responseCode + ". No retry attempts will be done.";
    }

    public static boolean isSuccessfulResponseCodeWithDataRouter(Integer statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    public static String basicAuthContent(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    /**
     * Prepare uri to retrieve file from xNF using HTTP connection. If JWT token was
     * included
     * in the queryParameters, it is removed. Other entries are rewritten.
     *
     * @param fileServerData fileServerData including - server address, port,
     *        queryParameters and uriRawFragment
     * @param remoteFile file which has to be downloaded
     * @return uri String representing the xNF HTTP location
     */
    public static String prepareHttpUri(FileServerData fileServerData, String remoteFile) {
        return prepareUri("http", fileServerData, remoteFile, HTTP_DEFAULT_PORT);
    }

    /**
     * Prepare uri to retrieve file from xNF using HTTPS connection. If JWT token
     * was included
     * in the queryParameters, it is removed. Other entries are rewritten.
     *
     * @param fileServerData fileServerData including - server address, port,
     *        queryParameters and uriRawFragment
     * @param remoteFile file which has to be downloaded
     * @return uri String representing the xNF HTTPS location
     */
    public static String prepareHttpsUri(FileServerData fileServerData, String remoteFile) {
        return prepareUri("https", fileServerData, remoteFile, HTTPS_DEFAULT_PORT);
    }

    /**
     * Prepare uri to retrieve file from xNF. If JWT token was included
     * in the queryParameters, it is removed. Other entries are rewritten.
     *
     * @param scheme scheme which is used during the connection
     * @param fileServerData fileServerData including - server address, port, query
     *        and fragment
     * @param remoteFile file which has to be downloaded
     * @param defaultPort default port which exchange empty entry for given
     *        connection type
     * @return uri String representing the xNF location
     */
    public static String prepareUri(String scheme, FileServerData fileServerData, String remoteFile, int defaultPort) {
        int port = fileServerData.port != null ? fileServerData.port : defaultPort;
        String query = queryParametersAsString(fileServerData.queryParameters);
        String fragment = fileServerData.uriRawFragment;
        if (!query.isEmpty()) {
            query = "?" + query;
        }
        if (!fragment.isEmpty()) {
            fragment = "#" + fragment;
        }
        return scheme + "://" + fileServerData.serverAddress + ":" + port + remoteFile + query + fragment;
    }

    /**
     *
     *
     * @param query list of NameValuePair of elements sent in the queryParameters
     * @return String representation of queryParameters elements which were provided
     *         in the input
     *         Empty string is possible when queryParameters is empty.
     */
    private static String queryParametersAsString(List<NameValuePair> query) {
        if (query.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (NameValuePair nvp : query) {
            sb.append(nvp.getName());
            if (nvp.getValue() != null) {
                sb.append("=");
                sb.append(nvp.getValue());
            }
            sb.append("&");
        }
        if ((sb.length() > 0) && (sb.charAt(sb.length() - 1) == '&')) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}
