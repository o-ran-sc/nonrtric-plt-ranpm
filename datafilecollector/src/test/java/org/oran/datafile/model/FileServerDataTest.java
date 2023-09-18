/*-
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2019-2023 Nordix Foundation.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class FileServerDataTest {
    @Test
    void testConstructor() {
        FileServerData actualFileServerData = new FileServerData("42 Main St", "42", "password", new ArrayList<>(),
            "Uri Raw Fragment", 8080);
        assertEquals("FileServerData(serverAddress=42 Main St, userId=42, uriRawFragment=Uri Raw Fragment, port=8080)",
            actualFileServerData.toString());
        assertEquals(8080, actualFileServerData.port.intValue());
        assertTrue(actualFileServerData.queryParameters.isEmpty());
    }
}

