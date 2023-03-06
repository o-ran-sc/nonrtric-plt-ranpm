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

package org.oran.pmproducer.filter;

import com.google.gson.GsonBuilder;

import java.lang.invoke.MethodHandles;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilterFactory {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static com.google.gson.Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    private FilterFactory() {}

    public static PmReportFilter create(Object filter) {
        return new PmReportFilter(createPmFilterData(filter));
    }

    public static PmReportFilter createAggregateFilter(Collection<PmReportFilter> filters) {
        PmReportFilter.FilterData resultFilterData = filters.iterator().next().getFilterData();
        for (PmReportFilter filter : filters) {
            resultFilterData.addAll(filter.getFilterData());
        }
        return new PmReportFilter(resultFilterData);
    }

    private static PmReportFilter.FilterData createPmFilterData(Object filter) {
        String str = gson.toJson(filter);
        return gson.fromJson(str, PmReportFilter.FilterData.class);
    }

}
