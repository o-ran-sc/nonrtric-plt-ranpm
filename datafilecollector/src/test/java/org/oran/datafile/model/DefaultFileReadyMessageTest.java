/*-
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2019-2023 Nordix Foundation.
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DefaultFileReadyMessageTest {
    @Test
    void testMessageMetaDataProductName() {
        assertEquals("Event Name",
                (new DefaultFileReadyMessage.MessageMetaData("42", "Priority", "1.0.2", "Reporting Entity Name", 1,
                        "Domain", "Event Name", "1.0.2", "Source Name", 1L, 1L, "UTC", "42")).productName());
        assertEquals("|",
                (new DefaultFileReadyMessage.MessageMetaData("42", "Priority", "1.0.2", "Reporting Entity Name", 1,
                        "Domain", "_|-", "1.0.2", "Source Name", 1L, 1L, "UTC", "42")).productName());
    }

    @Test
    void testMessageMetaDataVendorName() {
        assertEquals("Event Name",
                (new DefaultFileReadyMessage.MessageMetaData("42", "Priority", "1.0.2", "Reporting Entity Name", 1,
                        "Domain", "Event Name", "1.0.2", "Source Name", 1L, 1L, "UTC", "42")).vendorName());
    }
}

