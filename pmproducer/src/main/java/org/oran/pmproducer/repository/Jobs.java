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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import lombok.Getter;

import org.oran.pmproducer.configuration.ApplicationConfig;
import org.oran.pmproducer.exceptions.ServiceException;
import org.oran.pmproducer.filter.FilterFactory;
import org.oran.pmproducer.filter.FilteredData;
import org.oran.pmproducer.filter.PmReportFilter;
import org.oran.pmproducer.oauth2.SecurityContext;
import org.oran.pmproducer.repository.Job.Parameters;
import org.oran.pmproducer.repository.Job.Parameters.KafkaDeliveryInfo;
import org.oran.pmproducer.tasks.TopicListener.DataFromTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class Jobs {
    public interface Observer {
        void onJobbGroupAdded(JobGroup jobGroup);

        void onJobGroupRemoved(JobGroup jobGroup);
    }

    public static class JobGroup {
        @Getter
        private final KafkaDeliveryInfo deliveryInfo;

        private Map<String, Job> jobs = new HashMap<>();

        @Getter
        private PmReportFilter filter;

        @Getter
        private final InfoType type;

        public JobGroup(InfoType type, KafkaDeliveryInfo deliveryInfo) {
            this.deliveryInfo = deliveryInfo;
            this.type = type;
        }

        public synchronized void add(Job job) {
            this.jobs.put(job.getId(), job);
            this.filter = createFilter();
        }

        public synchronized void remove(Job job) {
            this.jobs.remove(job.getId());
            if (!this.jobs.isEmpty()) {
                this.filter = createFilter();
            }
        }

        public boolean isEmpty() {
            return jobs.isEmpty();
        }

        public FilteredData filter(DataFromTopic data) {
            return filter.filter(data);
        }

        public Job getAJob() {
            if (this.jobs.isEmpty()) {
                return null;
            }
            return this.jobs.values().iterator().next();
        }

        private PmReportFilter createFilter() {
            Collection<PmReportFilter> filterData = new ArrayList<>();
            this.jobs.forEach((key, value) -> filterData.add(value.getFilter()));
            return FilterFactory.createAggregateFilter(filterData);
        }

        public String getId() {
            return deliveryInfo.getTopic();
        }

        public Iterable<Job> getJobs() {
            return this.jobs.values();
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(Jobs.class);

    private Map<String, Job> allJobs = new HashMap<>();
    private MultiMap<Job> jobsByType = new MultiMap<>();
    private Map<String, JobGroup> jobGroups = new HashMap<>(); // Key is Topic
    private final List<Observer> observers = new ArrayList<>();
    private final ApplicationConfig appConfig;

    public Jobs(@Autowired ApplicationConfig applicationConfig, @Autowired SecurityContext securityContext,
            @Autowired ApplicationConfig appConfig) {
        this.appConfig = appConfig;
    }

    public synchronized Job getJob(String id) throws ServiceException {
        Job job = allJobs.get(id);
        if (job == null) {
            throw new ServiceException("Could not find job: " + id, HttpStatus.NOT_FOUND);
        }
        return job;
    }

    public synchronized Job get(String id) {
        return allJobs.get(id);
    }

    public void addJob(String id, InfoType type, String owner, String lastUpdated, Parameters parameters)
            throws ServiceException {

        Job job = new Job(id, type, owner, lastUpdated, parameters, this.appConfig);
        this.put(job);
    }

    public void addObserver(Observer obs) {
        synchronized (observers) {
            this.observers.add(obs);
        }
    }

    private String jobGroupId(Job job) {
        return job.getParameters().getDeliveryInfo().getTopic();
    }

    private synchronized void put(Job job) {
        logger.debug("Put job: {}", job.getId());
        remove(job.getId());

        allJobs.put(job.getId(), job);
        jobsByType.put(job.getType().getId(), job.getId(), job);

        String jobGroupId = jobGroupId(job);
        if (!this.jobGroups.containsKey(jobGroupId)) {
            final JobGroup group = new JobGroup(job.getType(), job.getParameters().getDeliveryInfo());
            this.jobGroups.put(jobGroupId, group);
            group.add(job);
            this.observers.forEach(obs -> obs.onJobbGroupAdded(group));
        } else {
            JobGroup group = this.jobGroups.get(jobGroupId);
            group.add(job);
        }
    }

    public synchronized Iterable<Job> getAll() {
        return new Vector<>(allJobs.values());
    }

    public synchronized Job remove(String id) {
        Job job = allJobs.get(id);
        if (job != null) {
            remove(job);
        }
        return job;
    }

    public void remove(Job job) {
        String groupId = jobGroupId(job);
        JobGroup group = this.jobGroups.get(groupId);
        synchronized (this) {
            this.allJobs.remove(job.getId());
            jobsByType.remove(job.getType().getId(), job.getId());
            group.remove(job);
            if (group.isEmpty()) {
                this.jobGroups.remove(groupId);
            }
        }

        if (group.isEmpty()) {
            this.observers.forEach(obs -> obs.onJobGroupRemoved(group));
        }
    }

    public synchronized int size() {
        return allJobs.size();
    }

    public synchronized Collection<Job> getJobsForType(InfoType type) {
        return jobsByType.get(type.getId());
    }

    public void clear() {

        this.jobGroups.forEach((id, group) -> this.observers.forEach(obs -> obs.onJobGroupRemoved(group)));

        synchronized (this) {
            allJobs.clear();
            jobsByType.clear();
        }
    }
}
