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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FilePublishInformationTest {
    @Test
    void testCanEqual() {
        assertFalse(
            (new FilePublishInformation("Product Name", "Vendor Name", 1L, "Source Name", 1L, "UTC", "Compression",
                "File Format Type", "1.0.2", "Name", "42", "s3://bucket-name/object-key")).canEqual("Other"));
    }

    @Test
    void testCanEqual2() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertTrue(buildResult.canEqual(buildResult2));
    }

    @Test
    void testConstructor() {
        FilePublishInformation actualFilePublishInformation =
            new FilePublishInformation("Product Name", "Vendor Name", 1L,
                "Source Name", 1L, "UTC", "Compression", "File Format Type", "1.0.2", "Name", "42",
                "s3://bucket-name/object-key");

        assertEquals("Name", actualFilePublishInformation.getName());
        assertEquals("Vendor Name", actualFilePublishInformation.vendorName);
        assertEquals("UTC", actualFilePublishInformation.timeZoneOffset);
        assertEquals(1L, actualFilePublishInformation.startEpochMicrosec);
        assertEquals("Product Name", actualFilePublishInformation.productName);
        assertEquals("s3://bucket-name/object-key", actualFilePublishInformation.objectStoreBucket);
        assertEquals(1L, actualFilePublishInformation.lastEpochMicrosec);
        assertEquals("1.0.2", actualFilePublishInformation.fileFormatVersion);
        assertEquals("File Format Type", actualFilePublishInformation.fileFormatType);
        assertEquals("Compression", actualFilePublishInformation.compression);
        assertEquals("42", actualFilePublishInformation.changeIdentifier);
        assertEquals("Source Name", actualFilePublishInformation.getSourceName());
    }

    @Test
    void testEquals() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(null, buildResult);
    }
    @Test
    void testEquals2() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals("Different type to FilePublishInformation", buildResult );
    }
    @Test
    void testEquals3() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertEquals(buildResult, buildResult);
        int expectedHashCodeResult = buildResult.hashCode();
        assertEquals(expectedHashCodeResult, buildResult.hashCode());
    }
    @Test
    void testEquals4() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertEquals(buildResult, buildResult2);
        int expectedHashCodeResult = buildResult.hashCode();
        assertEquals(expectedHashCodeResult, buildResult2.hashCode());
    }
    @Test
    void testEquals5() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("Product Name")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals6() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier(null)
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals7() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Product Name")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals8() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression(null)
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals9() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("Product Name")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals10() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType(null)
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals11() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("Product Name")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals12() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion(null)
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals13() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(3L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals14() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Product Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals15() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name(null)
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals16() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("Product Name")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals17() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket(null)
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals18() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Vendor Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals19() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName(null)
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals20() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Product Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals21() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName(null)
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals22() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(3L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals23() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("Europe/London")
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals24() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset(null)
            .vendorName("Vendor Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals25() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Product Name")
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testEquals26() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName(null)
            .build();
        FilePublishInformation buildResult2 = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        assertNotEquals(buildResult, buildResult2);
    }
    @Test
    void testGetName() {
        FilePublishInformation buildResult = FilePublishInformation.builder()
            .changeIdentifier("42")
            .compression("Compression")
            .fileFormatType("File Format Type")
            .fileFormatVersion("1.0.2")
            .lastEpochMicrosec(1L)
            .name("Name")
            .objectStoreBucket("s3://bucket-name/object-key")
            .productName("Product Name")
            .sourceName("Source Name")
            .startEpochMicrosec(1L)
            .timeZoneOffset("UTC")
            .vendorName("Vendor Name")
            .build();
        String actualName = buildResult.getName();
        assertEquals("Name", actualName);
        assertEquals("Source Name", buildResult.getSourceName());
    }
}

