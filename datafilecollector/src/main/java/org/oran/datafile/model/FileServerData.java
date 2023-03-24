/*-
 * ============LICENSE_START======================================================================
 * Copyright (C) 2018-2023 Nordix Foundation. All rights reserved.
 * Modifications copyright (C) 2021 Nokia. All rights reserved.
 * ===============================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 * ============LICENSE_END========================================================================
 */

package org.oran.datafile.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.ToString;

import org.apache.hc.core5.http.NameValuePair;

/**
 * Data about the file server to collect a file from.
 * In case of http protocol it also contains data required to recreate target
 * uri
 */
@Builder
@ToString
public class FileServerData {

    public String serverAddress;
    public String userId;

    @ToString.Exclude
    public String password;

    @Builder.Default
    @ToString.Exclude
    public List<NameValuePair> queryParameters = new ArrayList<>();

    @Builder.Default
    public String uriRawFragment = "";

    public Integer port;
}
