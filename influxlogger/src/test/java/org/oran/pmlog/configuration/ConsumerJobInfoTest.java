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

package org.oran.pmlog.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConsumerJobInfoTest {

    @Test
    void testConstructorAndGetters() {
        // Create test data
        String infoTypeId = "123";
        String owner = "John";
        ConsumerJobInfo.PmFilterData filterData = new ConsumerJobInfo.PmFilterData();
        ConsumerJobInfo.KafkaDeliveryInfo deliveryInfo = new ConsumerJobInfo.KafkaDeliveryInfo("topic", "servers");

        // Create a ConsumerJobInfo instance
        ConsumerJobInfo jobInfo = new ConsumerJobInfo(infoTypeId, new ConsumerJobInfo.PmJobParameters("pmdata", filterData, deliveryInfo), owner);

        // Test constructor and getters
        assertEquals(infoTypeId, jobInfo.getInfoTypeId());
        assertEquals(owner, jobInfo.getOwner());
        assertNotNull(jobInfo.getJobDefinition());

        // Test jobDefinition getters
        assertEquals("pmdata", jobInfo.getJobDefinition().getFilterType());
        assertEquals(filterData, jobInfo.getJobDefinition().getFilter());
        assertEquals(deliveryInfo, jobInfo.getJobDefinition().getDeliveryInfo());
    }

    @Test
    void testSerializationDeserialization() {
        // Create test data
        String infoTypeId = "123";
        String owner = "John";
        ConsumerJobInfo.PmFilterData filterData = new ConsumerJobInfo.PmFilterData();
        ConsumerJobInfo.KafkaDeliveryInfo deliveryInfo = new ConsumerJobInfo.KafkaDeliveryInfo("topic", "servers");

        // Create a ConsumerJobInfo instance
        ConsumerJobInfo jobInfo = new ConsumerJobInfo(infoTypeId, new ConsumerJobInfo.PmJobParameters("pmdata", filterData, deliveryInfo), owner);


        // Serialize to JSON
        Gson gson = new Gson();
        String json = gson.toJson(jobInfo);

        // Deserialize from JSON
        ConsumerJobInfo deserializedJobInfo = gson.fromJson(json, ConsumerJobInfo.class);

        // Verify deserialized object
        assertEquals(jobInfo.getInfoTypeId(), deserializedJobInfo.getInfoTypeId());
        assertEquals(jobInfo.getOwner(), deserializedJobInfo.getOwner());
        assertNotNull(deserializedJobInfo.getJobDefinition());
    }

    @Test
    void testMeasTypeSpec() {
        ConsumerJobInfo.PmFilterData.MeasTypeSpec measTypeSpec = new ConsumerJobInfo.PmFilterData.MeasTypeSpec();
        measTypeSpec.setMeasuredObjClass("Class1");

        Set<String> measTypes = new HashSet<>();
        measTypes.add("Type1");
        measTypes.add("Type2");
        measTypeSpec.getMeasTypes().addAll(measTypes);

        assertThat(measTypeSpec.getMeasuredObjClass()).isEqualTo("Class1");
        assertThat(measTypeSpec.getMeasTypes()).containsExactlyInAnyOrder("Type1", "Type2");
    }

    @Test
    void testKafkaDeliveryInfo() {
        ConsumerJobInfo.KafkaDeliveryInfo kafkaDeliveryInfo = ConsumerJobInfo.KafkaDeliveryInfo.builder()
            .topic("TestTopic")
            .bootStrapServers("localhost:9092")
            .build();

        assertThat(kafkaDeliveryInfo.getTopic()).isEqualTo("TestTopic");
        assertThat(kafkaDeliveryInfo.getBootStrapServers()).isEqualTo("localhost:9092");
    }

    @Test
    void testPmFilterData() {
        ConsumerJobInfo.PmFilterData pmFilterData = new ConsumerJobInfo.PmFilterData();

        // Test sourceNames
        Set<String> sourceNames = new HashSet<>(Arrays.asList("Source1", "Source2"));
        pmFilterData.sourceNames.addAll(sourceNames);
        assertThat(pmFilterData.sourceNames).containsExactlyInAnyOrder("Source1", "Source2");

        // Test measObjInstIds
        Set<String> measObjInstIds = new HashSet<>(Arrays.asList("Id1", "Id2"));
        pmFilterData.measObjInstIds.addAll(measObjInstIds);
        assertThat(pmFilterData.measObjInstIds).containsExactlyInAnyOrder("Id1", "Id2");

        // Test measTypeSpecs
        ConsumerJobInfo.PmFilterData.MeasTypeSpec measTypeSpec1 = new ConsumerJobInfo.PmFilterData.MeasTypeSpec();
        measTypeSpec1.setMeasuredObjClass("Class1");
        measTypeSpec1.getMeasTypes().addAll(Arrays.asList("Type1", "Type2"));

        ConsumerJobInfo.PmFilterData.MeasTypeSpec measTypeSpec2 = new ConsumerJobInfo.PmFilterData.MeasTypeSpec();
        measTypeSpec2.setMeasuredObjClass("Class2");
        measTypeSpec2.getMeasTypes().addAll(Arrays.asList("Type3", "Type4"));

        pmFilterData.measTypeSpecs.add(measTypeSpec1);
        pmFilterData.measTypeSpecs.add(measTypeSpec2);

        assertThat(pmFilterData.measTypeSpecs).hasSize(2);
        assertThat(pmFilterData.measTypeSpecs).extracting("measuredObjClass").containsExactly("Class1", "Class2");

        // Test measuredEntityDns
        Set<String> measuredEntityDns = new HashSet<>(Arrays.asList("Entity1", "Entity2"));
        pmFilterData.measuredEntityDns.addAll(measuredEntityDns);
        assertThat(pmFilterData.measuredEntityDns).containsExactlyInAnyOrder("Entity1", "Entity2");
    }
}

