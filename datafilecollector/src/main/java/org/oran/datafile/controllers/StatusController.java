/*-
 * ============LICENSE_START======================================================================
 * Copyright (C) 2018 NOKIA Intellectual Property. All rights reserved.
 * Copyright (C) 2018-2023 Nordix Foundation. All rights reserved.
 * ===============================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 * ============LICENSE_END========================================================================
 */

package org.oran.datafile.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.oran.datafile.model.Counters;
import org.oran.datafile.tasks.CollectAndReportFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST Controller to check the heart beat and status of the DFC.
 */
@RestController
@Tag(name = "StatusController")
public class StatusController {

    private static final Logger logger = LoggerFactory.getLogger(StatusController.class);

    private final CollectAndReportFiles collectAndReportFiles;

    public StatusController(CollectAndReportFiles task) {
        this.collectAndReportFiles = task;
    }

    /**
     * Checks the heart beat of DFC.
     *
     * @return the heart beat status of DFC.
     */
    @GetMapping("/heartbeat")
    @Operation(summary = "Returns liveness of DATAFILE service")
    @ApiResponses(
        value = { //
            @ApiResponse(responseCode = "200", description = "DATAFILE service is living"),
            @ApiResponse(responseCode = "401", description = "You are not authorized to view the resource"),
            @ApiResponse(
                responseCode = "403",
                description = "Accessing the resource you were trying to reach is forbidden"),
            @ApiResponse(responseCode = "404", description = "The resource you were trying to reach is not found")})
    public Mono<ResponseEntity<String>> heartbeat(@RequestHeader HttpHeaders headers) {
        logger.info("ENTRY {}", "Heartbeat request");

        String statusString = "I'm living!";

        Mono<ResponseEntity<String>> response = Mono.just(new ResponseEntity<>(statusString, HttpStatus.OK));
        logger.info("EXIT {}", "Heartbeat request");
        return response;
    }

    /**
     * Returns diagnostics and statistics information. It is intended for testing
     * and trouble
     * shooting.
     *
     * @return information.
     */
    @GetMapping("/status")
    @Operation(summary = "Returns status and statistics of DATAFILE service")
    @ApiResponses(
        value = { //
            @ApiResponse(responseCode = "200", description = "DATAFILE service is living"),
            @ApiResponse(responseCode = "401", description = "You are not authorized to view the resource"),
            @ApiResponse(
                responseCode = "403",
                description = "Accessing the resource you were trying to reach is forbidden"),
            @ApiResponse(responseCode = "404", description = "The resource you were trying to reach is not found")})
    public Mono<ResponseEntity<String>> status(@RequestHeader HttpHeaders headers) {

        logger.info("ENTRY {}", "Status request");

        Counters counters = collectAndReportFiles.getCounters();
        Mono<ResponseEntity<String>> response = Mono.just(new ResponseEntity<>(counters.toString(), HttpStatus.OK));
        logger.info("EXIT {}", "Status request");
        return response;
    }
}
