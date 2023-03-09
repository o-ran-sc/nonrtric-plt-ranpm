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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.invoke.MethodHandles;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.oran.pmproducer.tasks.TopicListener;
import org.oran.pmproducer.tasks.TopicListener.DataFromTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PmReportFilterTest {

    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static Gson gson = new GsonBuilder() //
            .disableHtmlEscaping() //
            .create(); //

    private String filterReport(PmReportFilter filter) throws Exception {

        TopicListener.DataFromTopic data =
                new TopicListener.DataFromTopic("typeId", null, null, loadReport().getBytes());
        FilteredData filtered = filter.filter(data);

        String reportAfterFilter = gson.toJson(data.getCachedPmReport());
        String reportBeforeFilter = gson.toJson(gson.fromJson(loadReport(), PmReport.class));

        assertThat(reportAfterFilter).isEqualTo(reportBeforeFilter);

        return filtered.getValueAString();
    }

    @Test
    void testPmFilterMeasTypes() throws Exception {

        PmReportFilter.FilterData filterData = new PmReportFilter.FilterData();
        filterData.addMeasTypes("UtranCell", "succImmediateAssignProcs");

        PmReportFilter filter = new PmReportFilter(filterData);
        String filtered = filterReport(filter);

        assertThat(filtered).contains("succImmediateAssignProcs").doesNotContain("\"p\":2").contains("\"p\":1")
                .contains("Gbg-997");

        // Test that no report is returned if not meas types were found
        filterData = new PmReportFilter.FilterData();
        filterData.addMeasTypes("junk", "succImmediateAssignProcs");

        filter = new PmReportFilter(filterData);
        filtered = filterReport(filter);
        assertThat(filtered).isEmpty();
    }

    @Test
    void testMeasObjInstIds() throws Exception {
        PmReportFilter.FilterData filterData = new PmReportFilter.FilterData();
        filterData.measObjInstIds.add("junk");
        PmReportFilter filter = new PmReportFilter(filterData);
        String filtered = filterReport(filter);
        assertThat(filtered).isEmpty();

        filterData = new PmReportFilter.FilterData();
        filterData.measObjInstIds.add("UtranCell=Gbg-997");
        filter = new PmReportFilter(filterData);
        filtered = filterReport(filter);
        assertThat(filtered).contains("Gbg-997").doesNotContain("Gbg-998");
    }

    @Test
    void testMeasObjClass() throws Exception {
        {
            PmReportFilter.FilterData filterData = new PmReportFilter.FilterData();
            filterData.addMeasTypes("junk");
            PmReportFilter filter = new PmReportFilter(filterData);
            String filtered = filterReport(filter);
            assertThat(filtered).isEmpty();
        }

        {
            TopicListener.DataFromTopic data =
                    new TopicListener.DataFromTopic("typeId", null, null, loadReport().getBytes());

            PmReportFilter.FilterData utranCellFilter = new PmReportFilter.FilterData();
            utranCellFilter.addMeasTypes("UtranCell");
            FilteredData filtered = new PmReportFilter(utranCellFilter).filter(data);
            assertThat(filtered.getValueAString()).contains("UtranCell").doesNotContain("ENodeBFunction");

            PmReportFilter.FilterData eNodeBFilter = new PmReportFilter.FilterData();
            eNodeBFilter.addMeasTypes("ENodeBFunction");
            filtered = new PmReportFilter(eNodeBFilter).filter(data);
            assertThat(filtered.getValueAString()).contains("ENodeBFunction").doesNotContain("UtranCell");
        }
    }

    @Test
    void testSourceNames() throws Exception {
        PmReportFilter.FilterData filterData = new PmReportFilter.FilterData();
        filterData.sourceNames.add("junk");
        PmReportFilter filter = new PmReportFilter(filterData);
        String filtered = filterReport(filter);
        assertThat(filtered).isEmpty();

        filterData = new PmReportFilter.FilterData();
        filterData.sourceNames.add("O-DU-1122");
        filter = new PmReportFilter(filterData);
        filtered = filterReport(filter);
        assertThat(filtered).contains("O-DU-1122");
    }

    // @Test
    void testSomeCharacteristics() throws Exception {
        String path = "./src/test/resources/A20000626.2315+0200-2330+0200_HTTPS-6-73.json";

        String pmReportJson = Files.readString(Path.of(path), Charset.defaultCharset());

        int TIMES = 100000;

        {
            path = "./src/test/resources/A20000626.2315+0200-2330+0200_HTTPS-6-73.json.gz";
            byte[] pmReportZipped = Files.readAllBytes(Path.of(path));

            Instant startTime = Instant.now();
            for (int i = 0; i < TIMES; ++i) {
                TopicListener.unzip(pmReportZipped);
            }

            printDuration("Unzip", startTime, TIMES);
        }
        {

            PmReportFilter.FilterData filterData = new PmReportFilter.FilterData();
            filterData.addMeasTypes("NRCellCU", "pmCounterNumber0");
            PmReportFilter filter = new PmReportFilter(filterData);
            DataFromTopic topicData = new DataFromTopic("typeId", null, null, pmReportJson.getBytes());

            Instant startTime = Instant.now();
            for (int i = 0; i < TIMES; ++i) {
                filter.filter(topicData);
            }
            printDuration("PM Filter", startTime, TIMES);
        }
        {
            Instant startTime = Instant.now();
            for (int i = 0; i < TIMES; ++i) {
                gson.fromJson(pmReportJson, PmReport.class);
            }
            printDuration("Json parsing", startTime, TIMES);
        }

    }

    void printDuration(String str, Instant startTime, int noOfIterations) {
        final long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
        logger.info("*** Duration (ms) " + str + " :" + durationMs + ", objects/second: "
                + (noOfIterations * 1000) / durationMs);
    }

    @Test
    void testMeasuredEntityDns() throws Exception {
        PmReportFilter.FilterData filterData = new PmReportFilter.FilterData();
        filterData.measuredEntityDns.add("junk");
        PmReportFilter filter = new PmReportFilter(filterData);
        String filtered = filterReport(filter);
        assertThat(filtered).isEmpty();

        filterData = new PmReportFilter.FilterData();
        filterData.measuredEntityDns.add("ManagedElement=RNC-Gbg-1");
        filter = new PmReportFilter(filterData);
        filtered = filterReport(filter);
        assertThat(filtered).contains("ManagedElement=RNC-Gbg-1");
    }

    @Test
    void testCrapInput() {
        PmReportFilter.FilterData filterData = new PmReportFilter.FilterData();
        PmReportFilter filter = new PmReportFilter(filterData);

        FilteredData filtered = filter.filter(new TopicListener.DataFromTopic("typeId", null, null, "junk".getBytes()));
        assertThat(filtered.isEmpty()).isTrue();

        filtered = filter
                .filter(new TopicListener.DataFromTopic("typeId", null, null, reQuote("{'msg': 'test'}").getBytes()));
        assertThat(filtered.isEmpty()).isTrue();

    }

    private String reQuote(String str) {
        return str.replaceAll("'", "\\\"");
    }

    @Test
    void testParse() throws Exception {
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
        PmReport report = gson.fromJson(loadReport(), PmReport.class);

        String dn = report.event.getPerf3gppFields().getMeasDataCollection().getMeasuredEntityDn();
        String json = gson.toJson(report);
        report = gson.fromJson(json, PmReport.class);

        // '=' is escaped to unicode by gson. but converted back
        assertThat(report.event.getPerf3gppFields().getMeasDataCollection().getMeasuredEntityDn()).isEqualTo(dn);
    }

    private String loadReport() throws Exception {
        String path = "./src/test/resources/pm_report.json";
        return Files.readString(Path.of(path), Charset.defaultCharset());
    }

}
