/*-
 * ============LICENSE_START======================================================================
 * Copyright (C) 2018-2023 Nordix Foundation. All rights reserved.
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

package org.oran.datafile.commons;

import java.nio.file.Path;

import org.oran.datafile.exceptions.DatafileTaskException;

/**
 * A closeable file client.
 */
public interface FileCollectClient extends AutoCloseable {
    public void collectFile(String remoteFile, Path localFile) throws DatafileTaskException;

    public void open() throws DatafileTaskException;
}
