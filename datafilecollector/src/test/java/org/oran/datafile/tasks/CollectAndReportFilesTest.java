/*-
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2023 Nordix Foundation.
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

package org.oran.datafile.tasks;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.oran.datafile.configuration.AppConfig;
import org.oran.datafile.model.Counters;
import org.oran.datafile.model.FilePublishInformation;
import org.oran.datafile.oauth2.SecurityContext;
import org.springframework.test.context.ContextConfiguration;
import reactor.core.publisher.DirectProcessor;
import reactor.kafka.sender.SenderResult;

@ContextConfiguration(classes = {CollectAndReportFiles.class})
@ExtendWith(MockitoExtension.class)
class CollectAndReportFilesTest {
    @Mock
    private AppConfig appConfig;

    @Mock
    private CollectAndReportFiles collectAndReportFilesMock;

    @Mock
    private SecurityContext securityContext;

    @Test
    void testStart() {
        doNothing().when(collectAndReportFilesMock).start();
        collectAndReportFilesMock.start();
        verify(collectAndReportFilesMock).start();
    }
    @Test
    void testCreateMainTask() {
        DirectProcessor<FilePublishInformation> createResult = DirectProcessor.create();
        when(collectAndReportFilesMock.createMainTask()).thenReturn(createResult);
        assertSame(createResult, collectAndReportFilesMock.createMainTask());
        verify(collectAndReportFilesMock).createMainTask();
    }
    @Test
    void testSendDataToStream() {
        DirectProcessor<SenderResult<Integer>> createResult = DirectProcessor.create();
        when(
            collectAndReportFilesMock.sendDataToStream(Mockito.<String>any(), Mockito.<String>any(), Mockito.<String>any()))
            .thenReturn(createResult);
        assertSame(createResult, collectAndReportFilesMock.sendDataToStream("Topic", "Source Name", "42"));
        verify(collectAndReportFilesMock).sendDataToStream(Mockito.<String>any(), Mockito.<String>any(),
            Mockito.<String>any());
    }
    @Test
    void testCreateFileCollector() {
        FileCollector fileCollector = new FileCollector(securityContext, appConfig, new Counters());

        when(collectAndReportFilesMock.createFileCollector()).thenReturn(fileCollector);
        assertSame(fileCollector, collectAndReportFilesMock.createFileCollector());
        verify(collectAndReportFilesMock).createFileCollector();
    }
    @Test
    void testParseReceivedFileReadyMessage() {
        when(collectAndReportFilesMock.parseReceivedFileReadyMessage(Mockito.<KafkaTopicListener.DataFromTopic>any()))
            .thenReturn(null);
        assertNull(
            collectAndReportFilesMock.parseReceivedFileReadyMessage(new KafkaTopicListener.DataFromTopic("Key", "42")));
        verify(collectAndReportFilesMock).parseReceivedFileReadyMessage(Mockito.<KafkaTopicListener.DataFromTopic>any());
    }
}

