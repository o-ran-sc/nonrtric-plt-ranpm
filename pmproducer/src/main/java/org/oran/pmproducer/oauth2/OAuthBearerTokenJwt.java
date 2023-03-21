//  ============LICENSE_START===============================================
//  Copyright (C) 2022 Nordix Foundation. All rights reserved.
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

package org.oran.pmproducer.oauth2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.apache.kafka.common.security.oauthbearer.OAuthBearerToken;
import org.oran.pmproducer.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OAuthBearerTokenJwt implements OAuthBearerToken {
    private final Logger logger = LoggerFactory.getLogger(OAuthBearerTokenJwt.class);
    private static final com.google.gson.Gson gson = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    private final String jwtTokenRaw;
    private final JwtTokenBody tokenBody;

    private static class JwtTokenBody {
        String sub = ""; // principalName
        long exp = 0; // expirationTime
        long iat = 0; // startTime
    }

    public static OAuthBearerTokenJwt create(String tokenRaw)
            throws ServiceException, JsonMappingException, JsonProcessingException {
        String[] chunks = tokenRaw.split("\\.");
        Base64.Decoder decoder = Base64.getUrlDecoder();
        if (chunks.length < 2) {
            throw new ServiceException("Could not parse JWT token: " + tokenRaw);

        }
        String payloadStr = new String(decoder.decode(chunks[1]));
        JwtTokenBody token = gson.fromJson(payloadStr, JwtTokenBody.class);

        return new OAuthBearerTokenJwt(token, tokenRaw);
    }

    private OAuthBearerTokenJwt(JwtTokenBody jwtTokenBody, String accessToken) {
        super();
        this.jwtTokenRaw = accessToken;
        this.tokenBody = jwtTokenBody;
    }

    @Override
    public String value() {
        return jwtTokenRaw;
    }

    @Override
    public Set<String> scope() {
        return new HashSet<>();
    }

    @Override
    public long lifetimeMs() {
        if (this.tokenBody.exp == 0) {
            return Long.MAX_VALUE;
        }
        return this.tokenBody.exp - this.tokenBody.iat;
    }

    @Override
    public String principalName() {
        return this.tokenBody.sub;
    }

    @Override
    public Long startTimeMs() {
        return this.tokenBody.iat;
    }

}
