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
package org.onap.dcaegen2.collectors.datafile.commons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class containing functions used for certificates configuration
 *
 * @author <a href="mailto:krzysztof.gajewski@nokia.com">Krzysztof Gajewski</a>
 */
public final class SecurityUtil {
    private SecurityUtil() {
    }

    private static final Logger logger = LoggerFactory.getLogger(SecurityUtil.class);

    public static String getKeystorePasswordFromFile(String passwordPath) {
        return getPasswordFromFile(passwordPath, "Keystore");
    }

    public static String getTruststorePasswordFromFile(String passwordPath) {
        return getPasswordFromFile(passwordPath, "Truststore");
    }

    public static String getPasswordFromFile(String passwordPath, String element) {
        try {
            return new String(Files.readAllBytes(Paths.get(passwordPath)));
        } catch (IOException e) {
            logger.error("{} password file at path: {} cannot be opened ", element, passwordPath);
        }
        return "";
    }
}
