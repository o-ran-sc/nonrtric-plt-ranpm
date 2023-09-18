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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import org.apache.kafka.common.security.auth.SaslExtensionsCallback;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerTokenCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OAuthKafkaAuthenticateLoginCallbackHandlerTest {

    private OAuthKafkaAuthenticateLoginCallbackHandler callbackHandler;

    @BeforeEach
    void setUp() {
        callbackHandler = new OAuthKafkaAuthenticateLoginCallbackHandler();
    }

    @Test
    void testConfigureWithValidSaslMechanismAndConfigEntry() {
        String saslMechanism = OAuthBearerLoginModule.OAUTHBEARER_MECHANISM;
        List<AppConfigurationEntry> jaasConfigEntries = Collections.singletonList(Mockito.mock(AppConfigurationEntry.class));

        callbackHandler.configure(new HashMap<>(), saslMechanism, jaasConfigEntries);

        assertTrue(callbackHandler.isConfigured());
    }

    @SuppressWarnings("java:S5778")
    @Test
    void testConfigureWithInvalidSaslMechanism() {
        String invalidSaslMechanism = "InvalidMechanism";
        List<AppConfigurationEntry> jaasConfigEntries = Collections.singletonList(Mockito.mock(AppConfigurationEntry.class));

        assertThrows(IllegalArgumentException.class, () -> callbackHandler.configure(new HashMap<>(), invalidSaslMechanism, jaasConfigEntries));

        assertFalse(callbackHandler.isConfigured());
    }

    @SuppressWarnings("java:S5778")
    @Test
    void testConfigureWithEmptyJaasConfigEntries() {
        String saslMechanism = OAuthBearerLoginModule.OAUTHBEARER_MECHANISM;
        List<AppConfigurationEntry> emptyJaasConfigEntries = Collections.emptyList();

        assertThrows(IllegalArgumentException.class, () -> callbackHandler.configure(new HashMap<>(), saslMechanism, emptyJaasConfigEntries));

        assertFalse(callbackHandler.isConfigured());
    }

    @Test
    void testHandleSaslExtensionsCallback() throws IOException, UnsupportedCallbackException {
        String saslMechanism = OAuthBearerLoginModule.OAUTHBEARER_MECHANISM;
        List<AppConfigurationEntry> jaasConfigEntries = Collections.singletonList(Mockito.mock(AppConfigurationEntry.class));

        callbackHandler.configure(new HashMap<>(), saslMechanism, jaasConfigEntries);
        SaslExtensionsCallback callback = mock(SaslExtensionsCallback.class);

        callbackHandler.handle(new Callback[]{callback});
        verify(callback).extensions(any());
    }

    @Test
    void testHandleUnsupportedCallback() {
        Callback unsupportedCallback = mock(Callback.class);
        String saslMechanism = OAuthBearerLoginModule.OAUTHBEARER_MECHANISM;
        List<AppConfigurationEntry> jaasConfigEntries = Collections.singletonList(Mockito.mock(AppConfigurationEntry.class));

        callbackHandler.configure(new HashMap<>(), saslMechanism, jaasConfigEntries);
        assertThrows(UnsupportedCallbackException.class, () -> callbackHandler.handle(new Callback[]{unsupportedCallback}));
    }

    @Test
    void testHandleOAuthBearerTokenCallback() throws IOException, UnsupportedCallbackException {

        String saslMechanism = OAuthBearerLoginModule.OAUTHBEARER_MECHANISM;
        List<AppConfigurationEntry> jaasConfigEntries = Collections.singletonList(Mockito.mock(AppConfigurationEntry.class));
        String validJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

        callbackHandler.configure(new HashMap<>(), saslMechanism, jaasConfigEntries);

        OAuthBearerTokenCallback oauthBearerTokenCallback = Mockito.mock(OAuthBearerTokenCallback.class);
        SecurityContext securityContextMock = Mockito.mock(SecurityContext.class);
        when(oauthBearerTokenCallback.token()).thenReturn(null); // Ensure the callback has no token initially
        when(oauthBearerTokenCallback.token()).thenAnswer(invocation -> {
            return OAuthBearerTokenJwt.create(validJwt);
        });

        when(securityContextMock.getBearerAuthToken()).thenReturn(validJwt);
        callbackHandler.handle(new Callback[]{oauthBearerTokenCallback});
        verify(oauthBearerTokenCallback).token();
    }
}
