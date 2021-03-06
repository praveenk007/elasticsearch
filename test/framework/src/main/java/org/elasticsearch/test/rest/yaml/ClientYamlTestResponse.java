/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.test.rest.yaml;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.Version;
import org.elasticsearch.client.Response;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Response obtained from a REST call, eagerly reads the response body into a string for later optional parsing.
 * Supports parsing the response body when needed and returning specific values extracted from it.
 */
public class ClientYamlTestResponse {

    private final Response response;
    private final String body;
    private final Version nodeVersion;
    private ObjectPath parsedResponse;

    ClientYamlTestResponse(Response response, Version version) throws IOException {
        this.response = response;
        this.nodeVersion = version;
        if (response.getEntity() != null) {
            try {
                this.body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                EntityUtils.consumeQuietly(response.getEntity());
                throw new RuntimeException(e);
            }
        } else {
            this.body = null;
        }
        parseResponseBody();
    }

    private void parseResponseBody() throws IOException {
        if (body != null) {
            String contentType = response.getHeader("Content-Type");
            XContentType xContentType = XContentType.fromMediaTypeOrFormat(contentType);
            //skip parsing if we got text back (e.g. if we called _cat apis)
            if (xContentType == XContentType.JSON || xContentType == XContentType.YAML) {
                this.parsedResponse = ObjectPath.createFromXContent(xContentType.xContent(), body);
            }
        }
    }

    public int getStatusCode() {
        return response.getStatusLine().getStatusCode();
    }

    public String getReasonPhrase() {
        return response.getStatusLine().getReasonPhrase();
    }

    /**
     * Get a list of all of the values of all warning headers returned in the response.
     */
    public List<String> getWarningHeaders() {
        List<String> warningHeaders = new ArrayList<>();
        for (Header header : response.getHeaders()) {
            if (header.getName().equals("Warning")) {
                if (nodeVersion.onOrAfter(Version.V_5_3_0) && response.getRequestLine().getMethod().equals("GET")
                    && response.getRequestLine().getUri().contains("source")
                    && response.getRequestLine().getUri().contains("source_content_type") == false && header.getValue().contains(
                        "Deprecated use of the [source] parameter without the [source_content_type] parameter.")) {
                    // this is because we do not send the source content type header when the node is 5.3.0 or below and the request
                    // might have been sent to a node with a version > 5.3.0 when running backwards 5.0 tests. The Java RestClient
                    // has control of the node the request is sent to so we can only detect this after the fact right now
                } else {
                    warningHeaders.add(header.getValue());
                }

            }
        }
        return warningHeaders;
    }

    /**
     * Returns the body properly parsed depending on the content type.
     * Might be a string or a json object parsed as a map.
     */
    public Object getBody() throws IOException {
        if (parsedResponse != null) {
            return parsedResponse.evaluate("");
        }
        return body;
    }

    /**
     * Returns the body as a string
     */
    public String getBodyAsString() {
        return body;
    }

    public boolean isError() {
        return response.getStatusLine().getStatusCode() >= 400;
    }

    /**
     * Parses the response body and extracts a specific value from it (identified by the provided path)
     */
    public Object evaluate(String path) throws IOException {
        return evaluate(path, Stash.EMPTY);
    }

    /**
     * Parses the response body and extracts a specific value from it (identified by the provided path)
     */
    public Object evaluate(String path, Stash stash) throws IOException {
        if (response == null) {
            return null;
        }

        if (parsedResponse == null) {
            //special case: api that don't support body (e.g. exists) return true if 200, false if 404, even if no body
            //is_true: '' means the response had no body but the client returned true (caused by 200)
            //is_false: '' means the response had no body but the client returned false (caused by 404)
            if ("".equals(path) && HttpHead.METHOD_NAME.equals(response.getRequestLine().getMethod())) {
                return isError() == false;
            }
            return null;
        }

        return parsedResponse.evaluate(path, stash);
    }
}
