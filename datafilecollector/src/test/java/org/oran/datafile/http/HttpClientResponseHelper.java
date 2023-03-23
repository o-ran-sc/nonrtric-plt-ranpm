/*-
 * ============LICENSE_START======================================================================
 * Copyright (C) 2023 Nordix Foundation. All rights reserved.
 * Copyright (C) 2020-2021 Nokia. All rights reserved.
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
package org.oran.datafile.http;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.Cookie;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.params.HttpParams;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClientResponse;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

public class HttpClientResponseHelper {

    public static final HttpClientResponse NETTY_RESPONSE_OK = new HttpClientResponse() {

        @Override
        public Map<CharSequence, Set<Cookie>> cookies() {
            return null;
        }

        @Override
        public boolean isKeepAlive() {
            return false;
        }

        @Override
        public boolean isWebsocket() {
            return false;
        }

        @Override
        public HttpMethod method() {
            return null;
        }

        @Override
        public String path() {
            return null;
        }

        @Override
        public String fullPath() {
            return null;
        }

        @Override
        public String requestId() {
            return null;
        }

        @Override
        public String uri() {
            return null;
        }

        @Override
        public HttpVersion version() {
            return null;
        }

        @Override
        public Context currentContext() {
            return null;
        }

        @Override
        public ContextView currentContextView() {
            return null;
        }

        @Override
        public String[] redirectedFrom() {
            return new String[0];
        }

        @Override
        public HttpHeaders requestHeaders() {
            return null;
        }

        @Override
        public String resourceUrl() {
            return null;
        }

        @Override
        public HttpHeaders responseHeaders() {
            return null;
        }

        @Override
        public HttpResponseStatus status() {
            return HttpResponseStatus.OK;
        }

        @Override
        public Mono<HttpHeaders> trailerHeaders() {
            return null;
        }
    };

    public static final HttpClientResponse RESPONSE_ANY_NO_OK = new HttpClientResponse() {

        @Override
        public Map<CharSequence, Set<Cookie>> cookies() {
            return null;
        }

        @Override
        public boolean isKeepAlive() {
            return false;
        }

        @Override
        public boolean isWebsocket() {
            return false;
        }

        @Override
        public HttpMethod method() {
            return null;
        }

        @Override
        public String fullPath() {
            return null;
        }

        @Override
        public String requestId() {
            return null;
        }

        @Override
        public String uri() {
            return null;
        }

        @Override
        public HttpVersion version() {
            return null;
        }

        @Override
        public Context currentContext() {
            return null;
        }

        @Override
        public ContextView currentContextView() {
            return null;
        }

        @Override
        public String[] redirectedFrom() {
            return new String[0];
        }

        @Override
        public HttpHeaders requestHeaders() {
            return null;
        }

        @Override
        public String resourceUrl() {
            return null;
        }

        @Override
        public HttpHeaders responseHeaders() {
            return null;
        }

        @Override
        public HttpResponseStatus status() {
            return HttpResponseStatus.NOT_IMPLEMENTED;
        }

        @Override
        public Mono<HttpHeaders> trailerHeaders() {
            return null;
        }
    };

    public static final CloseableHttpResponse APACHE_RESPONSE_OK = new CloseableHttpResponse() {
        @Override
        public void close() throws IOException {
            getEntity().getContent().close();
        }

        @Override
        public StatusLine getStatusLine() {
            return new StatusLine() {
                @Override
                public ProtocolVersion getProtocolVersion() {
                    return null;
                }

                @Override
                public int getStatusCode() {
                    return 200;
                }

                @Override
                public String getReasonPhrase() {
                    return null;
                }
            };
        }

        @Override
        public void setStatusLine(StatusLine statusLine) {

        }

        @Override
        public void setStatusLine(ProtocolVersion protocolVersion, int i) {

        }

        @Override
        public void setStatusLine(ProtocolVersion protocolVersion, int i, String s) {

        }

        @Override
        public void setStatusCode(int i) throws IllegalStateException {

        }

        @Override
        public void setReasonPhrase(String s) throws IllegalStateException {

        }

        @Override
        public HttpEntity getEntity() {
            return new HttpEntity() {
                @Override
                public boolean isRepeatable() {
                    return false;
                }

                @Override
                public boolean isChunked() {
                    return false;
                }

                @Override
                public long getContentLength() {
                    return 0;
                }

                @Override
                public Header getContentType() {
                    return null;
                }

                @Override
                public Header getContentEncoding() {
                    return null;
                }

                @Override
                public InputStream getContent() throws IOException, UnsupportedOperationException {
                    return new ByteArrayInputStream("abc".getBytes());
                }

                @Override
                public void writeTo(OutputStream outputStream) throws IOException {

                }

                @Override
                public boolean isStreaming() {
                    return false;
                }

                @Override
                public void consumeContent() throws IOException {

                }
            };
        }

        @Override
        public void setEntity(HttpEntity httpEntity) {

        }

        @Override
        public Locale getLocale() {
            return null;
        }

        @Override
        public void setLocale(Locale locale) {

        }

        @Override
        public ProtocolVersion getProtocolVersion() {
            return null;
        }

        @Override
        public boolean containsHeader(String s) {
            return false;
        }

        @Override
        public Header[] getHeaders(String s) {
            return new Header[0];
        }

        @Override
        public Header getFirstHeader(String s) {
            return null;
        }

        @Override
        public Header getLastHeader(String s) {
            return null;
        }

        @Override
        public Header[] getAllHeaders() {
            return new Header[0];
        }

        @Override
        public void addHeader(Header header) {

        }

        @Override
        public void addHeader(String s, String s1) {

        }

        @Override
        public void setHeader(Header header) {

        }

        @Override
        public void setHeader(String s, String s1) {

        }

        @Override
        public void setHeaders(Header[] headers) {

        }

        @Override
        public void removeHeader(Header header) {

        }

        @Override
        public void removeHeaders(String s) {

        }

        @Override
        public HeaderIterator headerIterator() {
            return null;
        }

        @Override
        public HeaderIterator headerIterator(String s) {
            return null;
        }

        @Override
        public HttpParams getParams() {
            return null;
        }

        @Override
        public void setParams(HttpParams params) {
        }
    };
}
