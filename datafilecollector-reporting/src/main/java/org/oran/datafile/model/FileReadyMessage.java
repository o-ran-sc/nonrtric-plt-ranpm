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
    
    @Builder
    public static class InternalHeaderFields {
        @SuppressWarnings("java:S1104")
        public String collectorTimeStamp;
    }

    /**
     * Meta data about a fileReady message.
     */
    @Builder
    public static class MessageMetaData {

        @SuppressWarnings("java:S1104")
        public String sourceId;

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
        public String eventType;

        @SuppressWarnings("java:S1104")
        public String stndDefinedNamespace;

        @SuppressWarnings("java:S1104")
        public String nfVendorName;

        @SuppressWarnings("java:S1104")
        public String nfNamingCode;

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
        public String reportingEntityId;

        @SuppressWarnings("java:S1104")
        public InternalHeaderFields internalHeaderFields;
        
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
        public int fileSize;

        @SuppressWarnings("java:S1104")
        public String fileLocation;

        @SuppressWarnings("java:S1104")
        public String fileReadyTime;

        @SuppressWarnings("java:S1104")
        public String fileFormat;

        @SuppressWarnings("java:S1104")
        public String fileDataType;

        @SuppressWarnings("java:S1104")
        public String fileExpirationTime;

        @SuppressWarnings("java:S1104")
        public String fileCompression;
        
	@SuppressWarnings("java:S6035")
        public String fileName() {
            String[] fileArray = fileLocation.split("/");
            if (fileArray.length >= 5) {
                return fileArray[4];
            } else {
                return fileLocation;
            }
        }

	@SuppressWarnings("java:S6035")
        public String filePath() {
            String fileP = fileLocation.substring(0, fileLocation.lastIndexOf('/')) + "/";
            return fileP;
        }
    }

    @Builder
    public static class DataFields {
        @SuppressWarnings("java:S1104")
        public String systemDN;

        @SuppressWarnings("java:S1104")
        public String additionalText;

        @SuppressWarnings("java:S1104")
        public String eventTime;

        @SuppressWarnings("java:S1104")
        public int notificationId;

        @SuppressWarnings("java:S1104")
        public String href;

        @SuppressWarnings("java:S1104")
        public String notificationType;

        @SuppressWarnings("java:S1104")
        public List<FileInfo> fileInfoList;
    }

    @Builder
    public static class StandardDefinedFields {
        @SuppressWarnings("java:S1104")
        public String stndDefinedFieldsVersion;

        @SuppressWarnings("java:S1104")
        public String schemaReference;

        @SuppressWarnings("java:S1104")
        public DataFields data;
    }

    @Builder
    public static class Event {
        @SuppressWarnings("java:S1104")
        public MessageMetaData commonEventHeader;

        @SuppressWarnings("java:S1104")
        public StandardDefinedFields stndDefinedFields;
    }

    @SuppressWarnings("java:S1104")
    public Event event;

}
