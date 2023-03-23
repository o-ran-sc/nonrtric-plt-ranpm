/*-
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2019-2023 Nordix Foundation.
 *  Copyright (C) 2020 Nokia. All rights reserved.
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

package org.oran.datafile.controllers;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.oran.datafile.model.Counters;
import org.oran.datafile.tasks.CollectAndReportFiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
public class StatusControllerTest {
    @Mock
    CollectAndReportFiles scheduledTasksMock;

    StatusController controllerUnderTest;

    @BeforeEach
    public void setup() {
        controllerUnderTest = new StatusController(scheduledTasksMock);
    }

    @Test
    public void heartbeat_success() {
        HttpHeaders httpHeaders = new HttpHeaders();

        Mono<ResponseEntity<String>> result = controllerUnderTest.heartbeat(httpHeaders);

        String body = result.block().getBody();
        assertTrue(body.startsWith("I'm living!"));
    }

    @Test
    public void status() {
        Counters counters = new Counters();
        doReturn(counters).when(scheduledTasksMock).getCounters();

        HttpHeaders httpHeaders = new HttpHeaders();

        Mono<ResponseEntity<String>> result = controllerUnderTest.status(httpHeaders);

        String body = result.block().getBody();
        System.out.println(body);
    }

}
