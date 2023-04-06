/*-
 * ========================LICENSE_START=================================
 * O-RAN-SC
 * %%
 * Copyright (C) 2023 Nordix Foundation
 * %%
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
 * ========================LICENSE_END===================================
 */

package org.oran.pmproducer.repository;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import java.lang.invoke.MethodHandles;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import org.oran.pmproducer.configuration.ApplicationConfig;
import org.oran.pmproducer.filter.FilterFactory;
import org.oran.pmproducer.filter.FilteredData;
import org.oran.pmproducer.filter.PmReportFilter;
import org.oran.pmproducer.tasks.TopicListener.DataFromTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ToString
public class Job {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Builder
    @Getter
    @Schema(name = "job_statistics", description = "Statistics information for one job")
    public static class Statistics {

        @JsonProperty(value = "jobId", required = true)
        String jobId;

        @JsonProperty(value = "typeId", required = true)
        String typeId;

        @JsonProperty(value = "inputTopic", required = false)
        String inputTopic;

        @JsonProperty(value = "outputTopic", required = false)
        String outputTopic;

        @JsonProperty(value = "groupId", required = false)
        String groupId;

        @JsonProperty(value = "clientId", required = false)
        String clientId;

        @JsonProperty(value = "noOfReceivedObjects", required = true)
        @Builder.Default
        long noOfReceivedObjects = 0;

        @JsonProperty(value = "noOfReceivedBytes", required = true)
        @Builder.Default
        long noOfReceivedBytes = 0;

        @JsonProperty(value = "noOfSentObjects", required = true)
        @Builder.Default
        long noOfSentObjects = 0;

        @JsonProperty(value = "noOfSentBytes", required = true)
        @Builder.Default
        long noOfSentBytes = 0;

        public void received(byte[] bytes) {
            noOfReceivedBytes += bytes.length;
            noOfReceivedObjects += 1;

        }

        public void filtered(byte[] bytes) {
            noOfSentBytes += bytes.length;
            noOfSentObjects += 1;
        }

    }

    @Builder
    public static class Parameters {

        @Getter
        private PmReportFilter.FilterData filter;

        @Builder
        @EqualsAndHashCode
        public static class KafkaDeliveryInfo {
            @Getter
            private String topic;

            @Getter
            private String bootStrapServers;
        }

        @Getter
        private KafkaDeliveryInfo deliveryInfo;
    }

    @Getter
    private final String id;

    @Getter
    private final InfoType type;

    @Getter
    private final String owner;

    @Getter
    private final Parameters parameters;

    @Getter
    private final String lastUpdated;

    @Getter
    private final PmReportFilter filter;

    @Getter
    private final Statistics statistics;

    public Job(String id, InfoType type, String owner, String lastUpdated, Parameters parameters,
            ApplicationConfig appConfig) {
        this.id = id;
        this.type = type;
        this.owner = owner;
        this.lastUpdated = lastUpdated;
        this.parameters = parameters;
        filter = parameters.filter == null ? null : FilterFactory.create(parameters.getFilter());

        statistics = Statistics.builder() //
                .groupId(type.getKafkaGroupId()) //
                .inputTopic(type.getKafkaInputTopic()) //
                .jobId(id) //
                .outputTopic(parameters.getDeliveryInfo() == null ? "" : parameters.getDeliveryInfo().topic) //
                .typeId(type.getId()) //
                .clientId(type.getKafkaClientId(appConfig)) //
                .build();
    }

    public FilteredData filter(DataFromTopic data) {
        return filter.filter(data);
    }
}
