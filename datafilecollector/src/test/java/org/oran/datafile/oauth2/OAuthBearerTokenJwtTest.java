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

package org.oran.datafile.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.oran.datafile.exceptions.DatafileTaskException;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = {OAuthBearerTokenJwtTest.class})
@ExtendWith(MockitoExtension.class)
class OAuthBearerTokenJwtTest {

    private OAuthBearerTokenJwt token;

    @BeforeEach
    void setUp() throws DatafileTaskException {
        String validJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"; // Replace with a valid JWT token for testing
        token = OAuthBearerTokenJwt.create(validJwt);
    }

    @Test
    void testCreateValidToken() {
        assertNotNull(token);
    }

    @Test
    void testCreateInvalidToken() {
        assertThrows(DatafileTaskException.class, () -> OAuthBearerTokenJwt.create("invalid_token"));
    }

    @Test
    void testTokenValue() {
        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c", token.value());
    }

    @Test
    void testTokenScope() {
        assertEquals(0, token.scope().size());
        assertFalse(token.scope().contains(""));
    }

    @Test
    void testTokenLifetimeMs() {
        assertEquals(Long.MAX_VALUE, token.lifetimeMs());
    }

    @Test
    void testTokenPrincipalName() {
        assertEquals("1234567890", token.principalName());
    }

    @Test
    void testTokenStartTimeMs() {
        assertEquals(1516239022L, token.startTimeMs());
    }

    @Test
    void testCreateTokenFromInvalidPayload() throws DatafileTaskException {
        // Create a JWT with an invalid payload (missing fields)
        String invalidPayload = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
        assertThrows(DatafileTaskException.class, () -> OAuthBearerTokenJwt.create(invalidPayload));
    }

    @Test
    void testCreateTokenWithValidPayload() throws DatafileTaskException {
        // Create a JWT with a valid payload
        String validPayload = "eyJzdWIiOiAiVGVzdCIsICJleHAiOiAxNjM1MTUwMDAwLCAiaWF0IjogMTYzNTA5NTAwMCwgInNjb3BlIjogInNjb3BlX3Rva2VuIiwgImp0aSI6ICJmb28ifQ==";
        OAuthBearerTokenJwt jwt = OAuthBearerTokenJwt.create("header." + validPayload + ".signature");

        assertNotNull(jwt);
        assertEquals("header." + validPayload + ".signature", jwt.value());
        assertEquals(1, jwt.scope().size());
        assertEquals("scope_token", jwt.scope().iterator().next());
        assertEquals("Test", jwt.principalName());
        assertEquals(1635095000, jwt.startTimeMs());
    }

    @Test
    void testCreateThrowsExceptionWithInvalidToken() throws DatafileTaskException {
        String tokenRaw = "your_mocked_token_here";
        assertThrows(DatafileTaskException.class, () -> OAuthBearerTokenJwt.create(tokenRaw));
    }
}
