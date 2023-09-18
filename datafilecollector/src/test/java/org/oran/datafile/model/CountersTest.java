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

class CountersTest {
    @Test
    void testIncNoOfReceivedEvents() {
        Counters counters = new Counters();
        counters.incNoOfReceivedEvents();
        assertEquals(1L, counters.getTotalReceivedEvents());
    }

    @Test
    void testIncNoOfCollectedFiles() {
        Counters counters = new Counters();
        counters.incNoOfCollectedFiles();
        counters.incNoOfFailedFtp();
        counters.incNoOfFailedFtpAttempts();
        counters.incNoOfFailedHttp();
        counters.incNoOfFailedHttpAttempts();
        counters.incNoOfFailedPublish();
        counters.incNoOfFailedPublishAttempts();
        String actualToStringResult = counters.toString();
        long actualNoOfCollectedFiles = counters.getNoOfCollectedFiles();
        long actualNoOfFailedFtp = counters.getNoOfFailedFtp();
        long actualNoOfFailedFtpAttempts = counters.getNoOfFailedFtpAttempts();
        long actualNoOfFailedHttp = counters.getNoOfFailedHttp();
        long actualNoOfFailedHttpAttempts = counters.getNoOfFailedHttpAttempts();
        long actualNoOfFailedPublish = counters.getNoOfFailedPublish();
        long actualNoOfFailedPublishAttempts = counters.getNoOfFailedPublishAttempts();
        long actualTotalPublishedFiles = counters.getTotalPublishedFiles();
        assertEquals(1L, actualNoOfCollectedFiles);
        assertEquals(1L, actualNoOfFailedFtp);
        assertEquals(1L, actualNoOfFailedFtpAttempts);
        assertEquals(1L, actualNoOfFailedHttp);
        assertEquals(1L, actualNoOfFailedHttpAttempts);
        assertEquals(1L, actualNoOfFailedPublish);
        assertEquals(1L, actualNoOfFailedPublishAttempts);
        assertEquals(0L, actualTotalPublishedFiles);
        assertEquals(0L, counters.getTotalReceivedEvents());
    }
    @Test
    void testIncTotalPublishedFiles() {
        Counters counters = new Counters();
        counters.incTotalPublishedFiles();
        assertEquals(1L, counters.getTotalPublishedFiles());
    }
}

