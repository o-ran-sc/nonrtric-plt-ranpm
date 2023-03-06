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

package org.oran.pmproducer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.oran.pmproducer.clients.AsyncRestClient;
import org.oran.pmproducer.exceptions.ServiceException;
import org.oran.pmproducer.r1.ConsumerJobInfo;
import org.oran.pmproducer.r1.ProducerInfoTypeInfo;
import org.oran.pmproducer.r1.ProducerJobInfo;
import org.oran.pmproducer.r1.ProducerRegistrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("IcsSimulatorController")
@Tag(name = "Information Coordinator Service Simulator (exists only in test)")
public class IcsSimulatorController {

    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final static Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public static class TestResults {

        ProducerRegistrationInfo registrationInfo = null;
        Map<String, ProducerInfoTypeInfo> types = Collections.synchronizedMap(new HashMap<>());
        String infoProducerId = null;
        ConsumerJobInfo createdJob = null;

        public TestResults() {}

        public synchronized void reset() {
            registrationInfo = null;
            types.clear();
            infoProducerId = null;
            createdJob = null;
        }

        public void setCreatedJob(ConsumerJobInfo informationJobObject) {
            this.createdJob = informationJobObject;
        }
    }

    final TestResults testResults = new TestResults();
    public static final String API_ROOT = "/data-producer/v1";

    @GetMapping(path = API_ROOT + "/info-producers/{infoProducerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getInfoProducer( //
            @PathVariable("infoProducerId") String infoProducerId) {

        if (testResults.registrationInfo != null) {
            return new ResponseEntity<>(gson.toJson(testResults.registrationInfo), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping(path = API_ROOT + "/info-producers/{infoProducerId}", //
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> putInfoProducer( //
            @PathVariable("infoProducerId") String infoProducerId, //
            @RequestBody ProducerRegistrationInfo registrationInfo) {
        testResults.registrationInfo = registrationInfo;
        testResults.infoProducerId = infoProducerId;
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PutMapping(path = API_ROOT + "/info-types/{infoTypeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> putInfoType( //
            @PathVariable("infoTypeId") String infoTypeId, //
            @RequestBody ProducerInfoTypeInfo registrationInfo) {
        testResults.types.put(infoTypeId, registrationInfo);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PutMapping(path = "/data-consumer/v1/info-jobs/{infoJobId}", //
            produces = MediaType.APPLICATION_JSON_VALUE, //
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> putIndividualInfoJob( //
            @PathVariable("infoJobId") String jobId, //
            @RequestBody ConsumerJobInfo informationJobObject) {
        logger.debug("*** added consumer job {}", jobId);
        testResults.setCreatedJob(informationJobObject);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    public void addJob(ConsumerJobInfo job, String jobId, AsyncRestClient restClient) throws ServiceException {
        String url = this.testResults.registrationInfo.jobCallbackUrl;
        ProducerJobInfo request = new ProducerJobInfo(job.jobDefinition, jobId, job.infoTypeId, job.owner, "TIMESTAMP");
        String body = gson.toJson(request);
        ProducerInfoTypeInfo type = testResults.types.get(job.infoTypeId);
        if (type == null) {
            logger.error("type not found: {} size: {}", job.infoTypeId, testResults.types.size());
        } else {
            assertThat(type).isNotNull();
            validateJsonObjectAgainstSchema(job.jobDefinition, type.jobDataSchema);
            logger.debug("ICS Simulator PUT job: {}", body);
            restClient.post(url, body, MediaType.APPLICATION_JSON).block();
        }
    }

    private void validateJsonObjectAgainstSchema(Object object, Object schemaObj) throws ServiceException {
        if (schemaObj != null) { // schema is optional for now
            try {
                ObjectMapper mapper = new ObjectMapper();

                String schemaAsString = mapper.writeValueAsString(schemaObj);
                JSONObject schemaJSON = new JSONObject(schemaAsString);
                var schema = org.everit.json.schema.loader.SchemaLoader.load(schemaJSON);

                String objectAsString = object.toString();
                JSONObject json = new JSONObject(objectAsString);
                schema.validate(json);
            } catch (Exception e) {
                logger.error("Json validation failure {}", e.toString());
                throw new ServiceException("Json validation failure " + e.toString(), HttpStatus.BAD_REQUEST);
            }
        }
    }

    public void deleteJob(String jobId, AsyncRestClient restClient) {
        String url = this.testResults.registrationInfo.jobCallbackUrl + "/" + jobId;
        logger.debug("ICS Simulator DELETE job: {}", url);
        restClient.delete(url).block();

    }
}
