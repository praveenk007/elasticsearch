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

package org.elasticsearch.cloud.aws;

import com.amazonaws.auth.AWSCredentialsProvider;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.ESTestCase;

public class SyspropCredentialsTests extends ESTestCase {
    public void test() {
        AWSCredentialsProvider provider =
            InternalAwsS3Service.buildCredentials(logger, deprecationLogger, Settings.EMPTY, Settings.EMPTY, "default");
        // NOTE: sys props are setup by the test runner in gradle
        assertEquals("sysprop_access", provider.getCredentials().getAWSAccessKeyId());
        assertEquals("sysprop_secret", provider.getCredentials().getAWSSecretKey());
        assertWarnings("Supplying S3 credentials through system properties is deprecated. " +
                "See the breaking changes lists in the documentation for details.");
    }
}
