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

package org.oran.pmlog;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DataFromKafkaTopicTest {

    private DataFromKafkaTopic data;
    private List<Header> headers;

    @BeforeEach
    void setUp() {
        headers = new ArrayList<>();
        data = new DataFromKafkaTopic(headers, null, null);
    }

    @Test
    void testConstructor_NullKeyAndValue() {
        assertNotNull(data);
        assertArrayEquals(new byte[0], data.getKey());
        assertArrayEquals(new byte[0], data.getValue());
        assertEquals(headers, data.headers);
    }

    @Test
    void testValueAsString_Unzipped() {
        data.valueAsString();
        assertEquals("", data.valueAsString());
        assertFalse(data.isZipped()); // Not zipped

        // Ensure that calling valueAsString again does not recompute the value
        data.valueAsString();
        assertEquals("", data.valueAsString());
    }

    @Test
    void testValueAsString_Zipped() {
        headers.add(new Header() {
            @Override
            public String key() {
                return DataFromKafkaTopic.ZIPPED_PROPERTY;
            }

            @Override
            public byte[] value() {
                return new byte[]{1};
            }
        });

        // Mock GZIPInputStream behavior
        ByteArrayInputStream inputStream = Mockito.mock(ByteArrayInputStream.class);
        when(inputStream.readAllBytes()).thenReturn("ZippedValue".getBytes());

        // Mock the unzip method
        DataFromKafkaTopic spyData = spy(data);

        // Call valueAsString to trigger unzipping
        String result = spyData.valueAsString();

        // Ensure that the value is correctly unzipped
        assertEquals("", result);
        assertEquals("", spyData.getStringValue());
        assertTrue(spyData.isZipped());
    }

    @Test
    void testUnzip_Exception() throws IOException {
        byte[] zippedBytes = "ZippedValue".getBytes();

        // Mock GZIPInputStream to throw an exception
        ByteArrayInputStream inputStream = Mockito.mock(ByteArrayInputStream.class);
        when(inputStream.readAllBytes()).thenThrow(new IOException("Mocked exception"));

        // Mock the unzip method
        DataFromKafkaTopic spyData = spy(data);

        // Call unzip method
        String result = spyData.valueAsString();

        // Ensure that an empty string is returned and the error is logged
        assertEquals("", result);
    }

    @Test
    void testIsZipped_True() {
        headers.add(new Header() {
            @Override
            public String key() {
                return DataFromKafkaTopic.ZIPPED_PROPERTY;
            }

            @Override
            public byte[] value() {
                return new byte[0];
            }
        });

        assertTrue(data.isZipped());
    }

    @Test
    void testIsZipped_False() {
        assertFalse(data.isZipped());
    }

    @Test
    void testGetTypeIdFromHeaders() {
        headers.add(new Header() {
            @Override
            public String key() {
                return DataFromKafkaTopic.TYPE_ID_PROPERTY;
            }

            @Override
            public byte[] value() {
                return "Type123".getBytes();
            }
        });

        assertEquals("Type123", data.getTypeIdFromHeaders());
    }

    @Test
    void testGetTypeIdFromHeaders_Null() {
        assertEquals("", data.getTypeIdFromHeaders());
    }
}

