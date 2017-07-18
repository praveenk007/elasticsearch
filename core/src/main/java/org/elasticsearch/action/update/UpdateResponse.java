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

package org.elasticsearch.action.update;

import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.get.GetResult;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;
import java.util.function.BiConsumer;

public class UpdateResponse extends DocWriteResponse {

    private static final String GET = "get";

    private GetResult getResult;

    public UpdateResponse() {
    }

    /**
     * Constructor to be used when a update didn't translate in a write.
     * For example: update script with operation set to none
     */
    public UpdateResponse(ShardId shardId, String type, String id, long version, Result result) {
        this(new ShardInfo(0, 0), shardId, type, id, version, result);
    }

    public UpdateResponse(ShardInfo shardInfo, ShardId shardId, String type, String id,
                          long version, Result result) {
        super(shardId, type, id, version, result);
        setShardInfo(shardInfo);
    }

    public void setGetResult(GetResult getResult) {
        this.getResult = getResult;
    }

    public GetResult getGetResult() {
        return this.getResult;
    }

    @Override
    public RestStatus status() {
        return this.result == Result.CREATED ? RestStatus.CREATED : super.status();
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        if (in.readBoolean()) {
            getResult = GetResult.readGetResult(in);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        if (getResult == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            getResult.writeTo(out);
        }
    }

    @Override
    public XContentBuilder innerToXContent(XContentBuilder builder, Params params) throws IOException {
        super.innerToXContent(builder, params);
        if (getGetResult() != null) {
            builder.startObject(GET);
            getGetResult().toXContentEmbedded(builder, params);
            builder.endObject();
        }
        return builder;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("UpdateResponse[");
        builder.append("index=").append(getIndex());
        builder.append(",type=").append(getType());
        builder.append(",id=").append(getId());
        builder.append(",version=").append(getVersion());
        builder.append(",result=").append(getResult().getLowercase());
        builder.append(",shards=").append(getShardInfo());
        return builder.append("]").toString();
    }

    private static final ConstructingObjectParser<UpdateResponse, Void> PARSER;
    static {
        PARSER = new ConstructingObjectParser<>(UpdateResponse.class.getName(),
                args -> {
                    // index uuid and shard id are unknown and can't be parsed back for now.
                    String index = (String) args[0];
                    ShardId shardId = new ShardId(new Index(index, IndexMetaData.INDEX_UUID_NA_VALUE), -1);
                    String type = (String) args[1];
                    String id = (String) args[2];
                    long version = (long) args[3];
                    ShardInfo shardInfo = (ShardInfo) args[5];

                    Result result = null;
                    for (Result r : Result.values()) {
                        if (r.getLowercase().equals(args[4])) {
                            result = r;
                            break;
                        }
                    }

                    UpdateResponse updateResponse = null;
                    if (shardInfo != null) {
                        updateResponse = new UpdateResponse(shardInfo, shardId, type, id, version, result);
                    } else {
                        updateResponse = new UpdateResponse(shardId, type, id, version, result);
                    }
                    return updateResponse;
                });

        DocWriteResponse.declareParserFields(PARSER);
        BiConsumer<UpdateResponse, GetResult> setGetResult = (update, get) ->
            update.setGetResult(new GetResult(update.getIndex(), update.getType(), update.getId(), update.getVersion(),
                    get.isExists(), get.internalSourceRef(), get.getFields()));
        PARSER.declareObject(setGetResult, (parser, context) -> GetResult.fromXContentEmbedded(parser), new ParseField(GET));
    }

    public static UpdateResponse fromXContent(XContentParser parser) {
        return PARSER.apply(parser, null);
    }
}
