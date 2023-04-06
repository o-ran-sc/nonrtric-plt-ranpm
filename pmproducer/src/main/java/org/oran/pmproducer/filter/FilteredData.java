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

import java.util.ArrayList;

import lombok.Getter;
import lombok.ToString;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.oran.pmproducer.tasks.TopicListener.DataFromTopic;

@ToString
public class FilteredData {
    public final byte[] key;
    public final byte[] value;
    public final String infoTypeId;

    @Getter
    private final boolean isZipped;

    @Getter
    private final String sourceName;

    private static final FilteredData emptyData = new FilteredData(null, null, null, null);

    public boolean isEmpty() {
        return (key == null || key.length == 0) && (value == null || value.length == 0);
    }

    public FilteredData(String sourceName, String typeId, byte[] key, byte[] value) {
        this(sourceName, typeId, key, value, false);
    }

    public FilteredData(String nodeName, String typeId, byte[] key, byte[] value, boolean isZipped) {
        this.key = key;
        this.value = value;
        this.isZipped = isZipped;
        this.infoTypeId = typeId;
        this.sourceName = nodeName;
    }

    public String getValueAString() {
        return value == null ? "" : new String(this.value);
    }

    public static FilteredData empty() {
        return emptyData;
    }

    public Iterable<Header> headers() {
        ArrayList<Header> result = new ArrayList<>();
        if (isZipped()) {
            result.add(new RecordHeader(DataFromTopic.ZIPPED_PROPERTY, null));
        }
        result.add(new RecordHeader(DataFromTopic.TYPE_ID_PROPERTY, infoTypeId.getBytes()));
        if (this.sourceName != null && !this.sourceName.isEmpty()) {
            result.add(new RecordHeader(DataFromTopic.SOURCE_NAME_PROPERTY, this.sourceName.getBytes()));
        }

        return result;
    }

}
