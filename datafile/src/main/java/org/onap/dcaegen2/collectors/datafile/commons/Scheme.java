/*-
 * ============LICENSE_START=======================================================
 * Copyright (C) 2019 Nordix Foundation. All rights reserved.
 * Copyright (C) 2020-2021 Nokia. All rights reserved.
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
 * ============LICENSE_END=========================================================
 */

package org.onap.dcaegen2.collectors.datafile.commons;

import org.onap.dcaegen2.collectors.datafile.exceptions.DatafileTaskException;

/**
 * Enum specifying the schemes that DFC support for downloading files.
 *
 * @author <a href="mailto:henrik.b.andersson@est.tech">Henrik Andersson</a>
 *
 */
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
     * @throws DatafileTaskException if the value of the string doesn't match any defined scheme.
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

    /**
     * Check if <code>Scheme</code> is FTP type or HTTP type.
     *
     * @param scheme the <code>Scheme</code> which has to be checked.
     * @return true if <code>Scheme</code> is FTP type or false if it is HTTP type
     */
    public static boolean isFtpScheme(Scheme scheme) {
        return scheme == SFTP || scheme == FTPES;
    }
}
