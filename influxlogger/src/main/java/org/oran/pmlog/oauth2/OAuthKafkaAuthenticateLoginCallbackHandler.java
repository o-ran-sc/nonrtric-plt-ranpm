//  ============LICENSE_START===============================================
//  Copyright (C) 2023 Nordix Foundation. All rights reserved.
//  ========================================================================
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//  ============LICENSE_END=================================================
//

package org.oran.pmlog.oauth2;

import java.io.IOException;
import java.util.*;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;

import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.apache.kafka.common.security.auth.SaslExtensions;
import org.apache.kafka.common.security.auth.SaslExtensionsCallback;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerTokenCallback;
import org.oran.pmlog.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OAuthKafkaAuthenticateLoginCallbackHandler implements AuthenticateCallbackHandler {
    private final Logger logger = LoggerFactory.getLogger(OAuthKafkaAuthenticateLoginCallbackHandler.class);

    private boolean isConfigured = false;

    @Override
    public void configure(Map<String, ?> map, String saslMechanism, List<AppConfigurationEntry> jaasConfigEntries) {

        if (!OAuthBearerLoginModule.OAUTHBEARER_MECHANISM.equals(saslMechanism))
            throw new IllegalArgumentException(String.format("Unexpected SASL mechanism: %s", saslMechanism));
        if (Objects.requireNonNull(jaasConfigEntries).size() != 1 || jaasConfigEntries.get(0) == null)
            throw new IllegalArgumentException(
                    String.format("Must supply exactly 1 non-null JAAS mechanism configuration (size was %d)",
                            jaasConfigEntries.size()));
        isConfigured = true;
    }

    @Override
    public void close() {}

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {

        if (!this.isConfigured)
            throw new IllegalStateException("Callback handler not configured");
        for (Callback callback : callbacks) {
            logger.debug("callback " + callback.toString());
            if (callback instanceof OAuthBearerTokenCallback) {
                handleCallback((OAuthBearerTokenCallback) callback);
            } else if (callback instanceof SaslExtensionsCallback) {
                handleCallback((SaslExtensionsCallback) callback);
            } else {
                logger.error("Unsupported callback: {}", callback);
                throw new UnsupportedCallbackException(callback);
            }
        }
    }

    private void handleCallback(SaslExtensionsCallback callback) {
        callback.extensions(SaslExtensions.empty());
    }

    private void handleCallback(OAuthBearerTokenCallback callback) {
        try {
            if (callback.token() != null) {
                throw new ServiceException("Callback had a token already");
            }

            String accessToken = SecurityContext.getInstance().getBearerAuthToken();
            OAuthBearerTokenJwt token = OAuthBearerTokenJwt.create(accessToken);

            callback.token(token);
        } catch (Exception e) {
            logger.error("Could not handle login callback: {}", e.getMessage());
        }
    }

}
