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

import java.util.List;

import lombok.Builder;

@Builder
public class DefaultFileReadyMessage implements FileReadyMessage {

    /**
     * Meta data about a fileReady message.
     */
    @Builder
    public static class MessageMetaData {

        @SuppressWarnings("java:S1104")
        public String eventId;

        @SuppressWarnings("java:S1104")
        public String priority;

        @SuppressWarnings("java:S1104")
        public String version;

        @SuppressWarnings("java:S1104")
        public String reportingEntityName;

        @SuppressWarnings("java:S1104")
        public int sequence;

        @SuppressWarnings("java:S1104")
        public String domain;

        @SuppressWarnings("java:S1104")
        public String eventName;

        @SuppressWarnings("java:S1104")
        public String vesEventListenerVersion;

        @SuppressWarnings("java:S1104")
        public String sourceName;

        @SuppressWarnings("java:S1104")
        public long lastEpochMicrosec;

        @SuppressWarnings("java:S1104")
        public long startEpochMicrosec;

        @SuppressWarnings("java:S1104")
        public String timeZoneOffset;

        @SuppressWarnings("java:S1104")
        public String changeIdentifier;

        /**
         * Gets data from the event name. Defined as:
         * {DomainAbbreviation}_{productName}-{vendorName}_{Description},
         * example: Noti_RnNode-Ericsson_FileReady
         *
         */
        @SuppressWarnings("java:S6035")
        public String productName() {
            String[] eventArray = eventName.split("_|-");
            if (eventArray.length >= 2) {
                return eventArray[1];
            } else {
                return eventName;
            }
        }

        @SuppressWarnings("java:S6035")
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
        @SuppressWarnings("java:S1104")
        public String fileFormatType;

        @SuppressWarnings("java:S1104")
        public String location;

        @SuppressWarnings("java:S1104")
        public String fileFormatVersion;

        @SuppressWarnings("java:S1104")
        public String compression;
    }

    @Builder
    public static class ArrayOfNamedHashMap {
        @SuppressWarnings("java:S1104")
        public String name;

        @SuppressWarnings("java:S1104")
        public FileInfo hashMap;
    }

    @Builder
    public static class NotificationFields {
        @SuppressWarnings("java:S1104")
        public String notificationFieldsVersion;

        @SuppressWarnings("java:S1104")
        public String changeType;

        @SuppressWarnings("java:S1104")
        public String changeIdentifier;

        @SuppressWarnings("java:S1104")
        public List<ArrayOfNamedHashMap> arrayOfNamedHashMap;
    }

    @Builder
    public static class Event {
        @SuppressWarnings("java:S1104")
        public MessageMetaData commonEventHeader;

        @SuppressWarnings("java:S1104")
        public NotificationFields notificationFields;
    }

    @SuppressWarnings("java:S1104")
    public Event event;

}
