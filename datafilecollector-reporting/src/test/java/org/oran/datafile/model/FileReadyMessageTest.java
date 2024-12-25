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

import org.junit.jupiter.api.Test;

class FileReadyMessageTest {
    @Test
    void testMessageMetaDataProductName() {
	FileReadyMessage.InternalHeaderFields inHeFi= new FileReadyMessage.InternalHeaderFields("CollectorTimeStamp");
        assertEquals("Event Name",
            (new FileReadyMessage.MessageMetaData("sourceID", "evnetID", "Priority", "version", "ReportingEntityName ", 42, "Domain", "Event Name", "eventType",
            "stndDefinedNamespace", "nfvendorname", "nfNamingCode", "vesEventListenerVersion", "sourceName", 123456789L, 987654321L, "timezoneoffset", "reportEntityID", inHeFi)).productName());
        assertEquals("|", (new FileReadyMessage.MessageMetaData("sourceID", "evnetID", "Priority", "version", "ReportingEntityName ", 42, "Domain", "_|-", "eventType",
            "stndDefinedNamespace", "nfvendorname", "nfNamingCode", "vesEventListenerVersion", "sourceName", 123456789L, 987654321L, "timezoneoffset", "reportEntityID", inHeFi)).productName());
    }
    @Test
    void testMessageMetaDataVendorName() {
	FileReadyMessage.InternalHeaderFields inHeFi= new FileReadyMessage.InternalHeaderFields("CollectorTimeStamp");
        assertEquals("Event Name",
            (new FileReadyMessage.MessageMetaData("sourceID", "evnetID", "Priority", "version", "ReportingEntityName ", 42, "Domain", "Event Name", "eventType",
            "stndDefinedNamespace", "nfvendorname", "nfNamingCode", "vesEventListenerVersion", "sourceName", 123456789L, 987654321L, "timezoneoffset", "reportEntityID", inHeFi)).vendorName());
    }
}

