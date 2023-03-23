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

import java.util.List;

import lombok.Builder;

@Builder
public class FileReadyMessage {

    /**
     * Meta data about a fileReady message.
     */
    @Builder
    public static class MessageMetaData {

        public String eventId;

        public String priority;
        public String version;
        public String reportingEntityName;
        public int sequence;
        public String domain;

        public String eventName;
        public String vesEventListenerVersion;

        public String sourceName;

        public long lastEpochMicrosec;
        public long startEpochMicrosec;

        public String timeZoneOffset;

        public String changeIdentifier;

        /**
         * Gets data from the event name. Defined as:
         * {DomainAbbreviation}_{productName}-{vendorName}_{Description},
         * example: Noti_RnNode-Ericsson_FileReady
         *
         */
        public String productName() {
            String[] eventArray = eventName.split("_|-");
            if (eventArray.length >= 2) {
                return eventArray[1];
            } else {
                return eventName;
            }
        }

        public String vendorName() {
            String[] eventArray = eventName.split("_|-");
            if (eventArray.length >= 3) {
                return eventArray[2];
            } else {
                return eventName;
            }
        }
    }

    @Builder
    public static class FileInfo {
        public String fileFormatType;
        public String location;
        public String fileFormatVersion;
        public String compression;
    }

    @Builder
    public static class ArrayOfNamedHashMap {
        public String name;
        public FileInfo hashMap;
    }

    @Builder
    public static class NotificationFields {
        public String notificationFieldsVersion;
        public String changeType;
        public String changeIdentifier;
        public List<ArrayOfNamedHashMap> arrayOfNamedHashMap;
    }

    @Builder
    public static class Event {
        public MessageMetaData commonEventHeader;
        public NotificationFields notificationFields;
    }

    public Event event;

}
