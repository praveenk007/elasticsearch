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

package org.elasticsearch.action.main;

import org.elasticsearch.Build;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.VersionUtils;

import java.io.IOException;
import java.util.Date;

import static org.elasticsearch.common.xcontent.XContentHelper.toXContent;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertToXContentEquivalent;

public class MainResponseTests extends ESTestCase {

    public static MainResponse createTestItem() {
        String clusterUuid = randomAsciiOfLength(10);
        ClusterName clusterName = new ClusterName(randomAsciiOfLength(10));
        String nodeName = randomAsciiOfLength(10);
        Build build = new Build(randomAsciiOfLength(8), new Date(randomNonNegativeLong()).toString(), randomBoolean());
        Version version = VersionUtils.randomVersion(random());
        boolean available = randomBoolean();
        return new MainResponse(nodeName, version, clusterName, clusterUuid , build, available);
    }

    public void testFromXContent() throws IOException {
        MainResponse mainResponse = createTestItem();
        XContentType xContentType = randomFrom(XContentType.values());
        boolean humanReadable = randomBoolean();
        BytesReference originalBytes = toXContent(mainResponse, xContentType, humanReadable);
        MainResponse parsed;
        try (XContentParser parser = createParser(xContentType.xContent(), originalBytes)) {
            parsed = MainResponse.fromXContent(parser);
            assertNull(parser.nextToken());
        }
        assertEquals(mainResponse.getClusterUuid(), parsed.getClusterUuid());
        assertEquals(mainResponse.getClusterName(), parsed.getClusterName());
        assertEquals(mainResponse.getNodeName(), parsed.getNodeName());
        assertEquals(mainResponse.getBuild(), parsed.getBuild());
        assertEquals(mainResponse.getVersion(), parsed.getVersion());
        // we cannot recreate the "available" flag from xContent, but should be "true" if request came through
        assertEquals(true, parsed.isAvailable());
        assertToXContentEquivalent(originalBytes, toXContent(parsed, xContentType, humanReadable), xContentType);
    }

    public void testToXContent() throws IOException {
        Build build = new Build("buildHash", "2016-11-15".toString(), true);
        Version version = Version.V_2_4_4;
        MainResponse response = new MainResponse("nodeName", version, new ClusterName("clusterName"), "clusterUuid", build, true);
        XContentBuilder builder = XContentFactory.jsonBuilder();
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertEquals("{"
                + "\"name\":\"nodeName\","
                + "\"cluster_name\":\"clusterName\","
                + "\"cluster_uuid\":\"clusterUuid\","
                + "\"version\":{"
                    + "\"number\":\"2.4.4\","
                    + "\"build_hash\":\"buildHash\","
                    + "\"build_date\":\"2016-11-15\","
                    + "\"build_snapshot\":true,"
                    + "\"lucene_version\":\"5.5.2\"},"
                + "\"tagline\":\"You Know, for Search\""
          + "}", builder.string());
    }

}
