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

package org.oran.pmproducer.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SecurityContextTest {

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testConstructorWithAuthTokenFilename() {
        SecurityContext securityContext = new SecurityContext("auth-token-file.txt");
        assertNotNull(securityContext.getAuthTokenFilePath());
        assertEquals(Path.of("auth-token-file.txt"), securityContext.getAuthTokenFilePath());
    }

    @Test
    void testConstructorWithoutAuthTokenFilename() {
        SecurityContext securityContext = new SecurityContext("");
        assertNull(securityContext.getAuthTokenFilePath());
    }

    @Test
    void testIsConfigured() {
        SecurityContext securityContext = new SecurityContext("auth-token-file.txt");
        assertTrue(securityContext.isConfigured());
    }

    @Test
    void testIsNotConfigured() {
        SecurityContext securityContext = new SecurityContext("");
        assertFalse(securityContext.isConfigured());
    }

    @Test
    void testGetBearerAuthToken() {
        assertEquals("", SecurityContext.getInstance().getBearerAuthToken());
        assertEquals("", (new SecurityContext("foo.txt")).getBearerAuthToken());
    }

    @Test
    void testGetBearerAuthTokenWhenNotConfigured() {
        SecurityContext securityContext = new SecurityContext("");
        assertEquals("", securityContext.getBearerAuthToken());
    }
}

