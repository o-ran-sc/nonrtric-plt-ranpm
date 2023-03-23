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

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Various counters that can be shown via a REST API.
 *
 */
public class Counters {

    private long noOfCollectedFiles = 0;
    private long noOfFailedFtpAttempts = 0;
    private long noOfFailedHttpAttempts = 0;
    private long noOfFailedFtp = 0;
    private long noOfFailedHttp = 0;
    private long noOfFailedPublishAttempts = 0;
    private long totalPublishedFiles = 0;
    private long noOfFailedPublish = 0;
    private Instant lastPublishedTime = Instant.MIN;
    private long totalReceivedEvents = 0;
    private Instant lastEventTime = Instant.MIN;

    public final AtomicInteger threadPoolQueueSize = new AtomicInteger();

    public synchronized void incNoOfReceivedEvents() {
        totalReceivedEvents++;
        lastEventTime = Instant.now();
    }

    public synchronized void incNoOfCollectedFiles() {
        noOfCollectedFiles++;
    }

    public synchronized void incNoOfFailedFtpAttempts() {
        noOfFailedFtpAttempts++;
    }

    public synchronized void incNoOfFailedHttpAttempts() {
        noOfFailedHttpAttempts++;
    }

    public synchronized void incNoOfFailedFtp() {
        noOfFailedFtp++;
    }

    public synchronized void incNoOfFailedHttp() {
        noOfFailedHttp++;
    }

    public synchronized void incNoOfFailedPublishAttempts() {
        noOfFailedPublishAttempts++;
    }

    public synchronized void incTotalPublishedFiles() {
        totalPublishedFiles++;
        lastPublishedTime = Instant.now();
    }

    public synchronized void incNoOfFailedPublish() {
        noOfFailedPublish++;
    }

    @Override
    public synchronized String toString() {
        StringBuilder str = new StringBuilder();
        str.append(format("totalReceivedEvents", totalReceivedEvents));
        str.append(format("lastEventTime", lastEventTime));
        str.append("\n");
        str.append(format("collectedFiles", noOfCollectedFiles));
        str.append(format("failedFtpAttempts", noOfFailedFtpAttempts));
        str.append(format("failedHttpAttempts", noOfFailedHttpAttempts));
        str.append(format("failedFtp", noOfFailedFtp));
        str.append(format("failedHttp", noOfFailedHttp));
        str.append("\n");
        str.append(format("totalPublishedFiles", totalPublishedFiles));
        str.append(format("lastPublishedTime", lastPublishedTime));

        str.append(format("failedPublishAttempts", noOfFailedPublishAttempts));
        str.append(format("noOfFailedPublish", noOfFailedPublish));

        return str.toString();
    }

    private static String format(String name, Object value) {
        String header = name + ":";
        return String.format("%-24s%-22s%n", header, value);
    }

    public long getNoOfCollectedFiles() {
        return noOfCollectedFiles;
    }

    public long getNoOfFailedFtpAttempts() {
        return noOfFailedFtpAttempts;
    }

    public long getNoOfFailedHttpAttempts() {
        return noOfFailedHttpAttempts;
    }

    public long getNoOfFailedFtp() {
        return noOfFailedFtp;
    }

    public long getNoOfFailedHttp() {
        return noOfFailedHttp;
    }

    public long getNoOfFailedPublishAttempts() {
        return noOfFailedPublishAttempts;
    }

    public long getTotalPublishedFiles() {
        return totalPublishedFiles;
    }

    public long getNoOfFailedPublish() {
        return noOfFailedPublish;
    }

    public long getTotalReceivedEvents() {
        return totalReceivedEvents;
    }
}
