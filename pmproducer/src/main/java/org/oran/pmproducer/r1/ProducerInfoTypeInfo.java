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

package org.oran.pmproducer.r1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "producer_info_type_info", description = "Information for an Information Type")
public class ProducerInfoTypeInfo {

    @SuppressWarnings("java:S1874")
    @Schema(name = "info_job_data_schema", description = "Json schema for the job data", required = true)
    @SerializedName("info_job_data_schema")
    @JsonProperty(value = "info_job_data_schema", required = true)
    public Object jobDataSchema;

    @SuppressWarnings("java:S1874")
    @Schema(name = "info_type_information", description = "Type specific information for the information type",
            required = true)
    @SerializedName("info_type_information")
    @JsonProperty(value = "info_type_information", required = true)
    public Object typeSpecificInformation;

    public ProducerInfoTypeInfo(Object jobDataSchema, Object typeSpecificInformation) {
        this.jobDataSchema = jobDataSchema;
        this.typeSpecificInformation = typeSpecificInformation;
    }

    public ProducerInfoTypeInfo() {}

}
