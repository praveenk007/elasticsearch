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

package org.elasticsearch.index.mapper;

import org.apache.lucene.index.IndexOptions;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.mapper.TextFieldMapper.TextFieldType;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESSingleNodeTestCase;
import org.elasticsearch.test.InternalSettingsPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;

public class StringMappingUpgradeTests extends ESSingleNodeTestCase {

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return pluginList(InternalSettingsPlugin.class);
    }

    public void testUpgradeDefaults() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string").endObject().endObject()
                .endObject().endObject().string();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping));
        FieldMapper field = mapper.mappers().getMapper("field");
        assertThat(field, instanceOf(TextFieldMapper.class));
        assertWarnings("The [string] field is deprecated, please use [text] or [keyword] instead on [field]");
    }

    public void testUpgradeAnalyzedString() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string").field("index", "analyzed").endObject().endObject()
                .endObject().endObject().string();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping));
        FieldMapper field = mapper.mappers().getMapper("field");
        assertThat(field, instanceOf(TextFieldMapper.class));
        assertWarnings("The [string] field is deprecated, please use [text] or [keyword] instead on [field]");
    }

    public void testUpgradeNotAnalyzedString() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string")
                .field("index", "not_analyzed").endObject().endObject()
                .endObject().endObject().string();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping));
        FieldMapper field = mapper.mappers().getMapper("field");
        assertThat(field, instanceOf(KeywordFieldMapper.class));
        assertWarnings("The [string] field is deprecated, please use [text] or [keyword] instead on [field]");
    }

    public void testUpgradeNotIndexedString() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string").field("index", "no").endObject().endObject()
                .endObject().endObject().string();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping));
        FieldMapper field = mapper.mappers().getMapper("field");
        assertThat(field, instanceOf(KeywordFieldMapper.class));
        assertEquals(IndexOptions.NONE, field.fieldType().indexOptions());
        assertWarnings("The [string] field is deprecated, please use [text] or [keyword] instead on [field]");
    }

    public void testUpgradeIndexOptions() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string")
                .field("index_options", "offsets").endObject().endObject()
                .endObject().endObject().string();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping));
        FieldMapper field = mapper.mappers().getMapper("field");
        assertThat(field, instanceOf(TextFieldMapper.class));
        assertEquals(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS, field.fieldType().indexOptions());
        assertWarnings("The [string] field is deprecated, please use [text] or [keyword] instead on [field]");
    }

    public void testUpgradePositionGap() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string")
                .field("position_increment_gap", 42).endObject().endObject()
                .endObject().endObject().string();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping));
        FieldMapper field = mapper.mappers().getMapper("field");
        assertThat(field, instanceOf(TextFieldMapper.class));
        assertEquals(42, field.fieldType().indexAnalyzer().getPositionIncrementGap("field"));
        assertWarnings("The [string] field is deprecated, please use [text] or [keyword] instead on [field]");
    }

    public void testIllegalIndexValue() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties")
                    .startObject("field")
                        .field("type", "string")
                        .field("index", false)
                    .endObject()
                .endObject() .endObject().endObject().string();
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> parser.parse("type", new CompressedXContent(mapping)));
        assertThat(e.getMessage(),
                containsString("Can't parse [index] value [false] for field [field], expected [no], [not_analyzed] or [analyzed]"));
    }

    public void testNotSupportedUpgrade() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string")
                .field("index", "not_analyzed").field("analyzer", "keyword").endObject().endObject()
                .endObject().endObject().string();
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> parser.parse("type", new CompressedXContent(mapping)));
        assertThat(e.getMessage(), containsString("The [string] type is removed in 5.0"));
    }

    public void testUpgradeFielddataSettings() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String format = randomFrom("paged_bytes", "disabled");
        String loading = randomFrom("lazy", "eager", "eager_global_ordinals");
        boolean keyword = random().nextBoolean();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties")
                    .startObject("field")
                        .field("type", "string")
                        .field("index", keyword ? "not_analyzed" : "analyzed")
                        .startObject("fielddata")
                            .field("format", format)
                            .field("loading", loading)
                            .startObject("filter")
                                .startObject("frequency")
                                    .field("min", 3)
                                .endObject()
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject()
                .endObject().endObject().string();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping));
        FieldMapper field = mapper.mappers().getMapper("field");
        if (keyword) {
            assertThat(field, instanceOf(KeywordFieldMapper.class));
        } else {
            assertThat(field, instanceOf(TextFieldMapper.class));
            TextFieldType fieldType = (TextFieldType) field.fieldType();
            assertEquals("disabled".equals(format) == false, fieldType.fielddata());
            assertEquals(3, fieldType.fielddataMinFrequency(), 0d);
            assertEquals(Integer.MAX_VALUE, fieldType.fielddataMaxFrequency(), 0d);
        }
        assertEquals("eager_global_ordinals".equals(loading), field.fieldType().eagerGlobalOrdinals());
        assertWarnings("The [string] field is deprecated, please use [text] or [keyword] instead on [field]");
    }

    public void testUpgradeIgnoreAbove() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string")
                .field("index", "not_analyzed").field("ignore_above", 200).endObject().endObject()
                .endObject().endObject().string();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping));
        FieldMapper field = mapper.mappers().getMapper("field");
        assertThat(field, instanceOf(KeywordFieldMapper.class));
        assertEquals(200, ((KeywordFieldMapper) field).ignoreAbove());
        assertWarnings("The [string] field is deprecated, please use [text] or [keyword] instead on [field]");
    }

    public void testUpgradeAnalyzer() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string")
                .field("analyzer", "standard")
                .field("search_analyzer", "whitespace")
                .field("search_quote_analyzer", "keyword").endObject().endObject()
                .endObject().endObject().string();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping));
        FieldMapper field = mapper.mappers().getMapper("field");
        assertThat(field, instanceOf(TextFieldMapper.class));
        assertEquals("standard", field.fieldType().indexAnalyzer().name());
        assertEquals("whitespace", field.fieldType().searchAnalyzer().name());
        assertEquals("keyword", field.fieldType().searchQuoteAnalyzer().name());
        assertWarnings("The [string] field is deprecated, please use [text] or [keyword] instead on [field]");
    }

    public void testUpgradeTextIncludeInAll() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string")
                .field("include_in_all", false).endObject().endObject()
                .endObject().endObject().string();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping));
        FieldMapper field = mapper.mappers().getMapper("field");
        assertThat(field, instanceOf(TextFieldMapper.class));
        assertFalse(((TextFieldMapper) field).includeInAll());
        assertWarnings("The [string] field is deprecated, please use [text] or [keyword] instead on [field]",
                "field [include_in_all] is deprecated, as [_all] is deprecated, and will be disallowed " +
                        "in 6.0, use [copy_to] instead.");
    }

    public void testUpgradeKeywordIncludeInAll() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string")
                .field("index", "not_analyzed").field("include_in_all", true).endObject().endObject()
                .endObject().endObject().string();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping));
        FieldMapper field = mapper.mappers().getMapper("field");
        assertThat(field, instanceOf(KeywordFieldMapper.class));
        assertTrue(((KeywordFieldMapper) field).includeInAll());
        assertWarnings("The [string] field is deprecated, please use [text] or [keyword] instead on [field]",
                "field [include_in_all] is deprecated, as [_all] is deprecated, and will be disallowed" +
                        " in 6.0, use [copy_to] instead.");
    }

    public void testUpgradeRandomMapping() throws IOException {
        final int iters = 20;
        for (int i = 0; i < iters; ++i) {
            List<String> warnings = doTestUpgradeRandomMapping(i);
            if (warnings.isEmpty() == false) {
                assertWarnings(warnings.toArray(new String[warnings.size()]));
            }
        }
    }

    private List<String> doTestUpgradeRandomMapping(int iter) throws IOException {
        List<String> warnings = new ArrayList<>();
        IndexService indexService;
        boolean oldIndex = randomBoolean();
        String indexName = "test" + iter;
        if (oldIndex) {
            Settings settings = Settings.builder()
                    .put(IndexMetaData.SETTING_VERSION_CREATED, Version.V_2_3_0)
                    .build();
            indexService = createIndex(indexName, settings);
        } else {
            indexService = createIndex(indexName);
        }
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string");
        boolean keyword = randomBoolean();
        boolean hasNorms = keyword == false;
        boolean shouldUpgrade = true;
        if (keyword) {
            mapping.field("index", randomBoolean() ? "not_analyzed" : "no");
        } else if (randomBoolean()) {
            mapping.field("index", "analyzed");
        }
        //TODO why don't we ever have warnings about index not being a boolean?
        //e.g. "Expected a boolean for property [index] but got [no]" or "Expected a boolean for property [index] but got [not_analyzed]"
        if (randomBoolean()) {
            Object store;
            if (randomBoolean()) {
                store = randomFrom("yes", "no");
                warnings.add("Expected a boolean [true/false] for property [field.store] but got [" + store + "]");
            } else {
                store = randomFrom(true, false);
            }
            mapping.field("store", store);
        }
        if (keyword && randomBoolean()) {
            mapping.field("doc_values", randomBoolean());
        }
        if (keyword == false && randomBoolean()) {
            mapping.field("analyzer", "keyword");
        }
        if (randomBoolean()) {
            //TODO does it make sense that the norms warnings are emitted only for old indices?
            hasNorms = randomBoolean();
            if (randomBoolean()) {
                mapping.field("omit_norms", hasNorms == false);
                if (oldIndex) {
                    warnings.add("[omit_norms] is deprecated, please use [norms] instead with the opposite boolean value");
                }
            } else {
                mapping.field("norms", Collections.singletonMap("enabled", hasNorms));
                if (oldIndex) {
                    warnings.add("The [norms{enabled:true/false}] way of specifying norms is deprecated, " +
                            "please use [norms:true/false] instead");
                }
            }
        }
        if (randomBoolean()) {
            //TODO fielddata and frequency filter are randomized but never used throughout the test
            Map<String, Object> fielddata = new HashMap<>();
            if (randomBoolean()) {
                fielddata.put("format", randomFrom("paged_bytes", "disabled"));
            }
            if (randomBoolean()) {
                fielddata.put("loading", randomFrom("lazy", "eager", "eager_global_ordinals"));
            }
            if (randomBoolean()) {
                Map<String, Object> frequencyFilter = new HashMap<>();
                frequencyFilter.put("min", 10);
                frequencyFilter.put("max", 1000);
                frequencyFilter.put("min_segment_size", 10000);
            }
        }
        if (randomBoolean()) {
            mapping.startObject("fields").startObject("raw").field("type", "keyword").endObject().endObject();
        }
        if (randomBoolean()) {
            mapping.field("copy_to", "bar");
        }
        if (randomBoolean()) {
            // this option is not upgraded automatically
            if (keyword) {
                mapping.field("index_options", "docs");
            } else {
                mapping.field("ignore_above", 30);
            }
            shouldUpgrade = false;
        }
        mapping.endObject().endObject().endObject().endObject();

        if (oldIndex == false && shouldUpgrade == false) {
            IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                    () -> parser.parse("type", new CompressedXContent(mapping.string())));
            assertThat(e.getMessage(), containsString("The [string] type is removed in 5.0"));
            //no deprecation warnings, we fail fast in this case, before any warning could be emitted.
            return Collections.emptyList();
        }
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping.string()));
        FieldMapper field = mapper.mappers().getMapper("field");
        if (oldIndex) {
            assertThat(field, instanceOf(StringFieldMapper.class));
        } else {
            warnings.add("The [string] field is deprecated, please use [text] or [keyword] instead on [field]");
            if (keyword) {
                assertThat(field, instanceOf(KeywordFieldMapper.class));
            } else {
                assertThat(field, instanceOf(TextFieldMapper.class));
            }
        }
        if (field.fieldType().indexOptions() != IndexOptions.NONE) {
            assertEquals(hasNorms, field.fieldType().omitNorms() == false);
        }
        return warnings;
    }

    public void testUpgradeTemplateWithDynamicType() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startArray("dynamic_templates")
                    .startObject()
                        .startObject("my_template")
                            .field("match_mapping_type", "string")
                            .startObject("mapping")
                                .field("store", true)
                            .endObject()
                        .endObject()
                    .endObject()
                .endArray()
                .endObject().endObject().string();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping));
        BytesReference source = XContentFactory.jsonBuilder().startObject().field("foo", "bar").endObject().bytes();
        ParsedDocument doc = mapper.parse("test", "type", "id", source);
        Mapper fooMapper = doc.dynamicMappingsUpdate().root().getMapper("foo");
        assertThat(fooMapper, instanceOf(TextFieldMapper.class));
        assertTrue(((TextFieldMapper) fooMapper).fieldType().stored());
    }

    public void testUpgradeTemplateWithDynamicType2() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startArray("dynamic_templates")
                    .startObject()
                        .startObject("my_template")
                            .field("match_mapping_type", "string")
                            .startObject("mapping")
                                .field("type", "{dynamic_type}")
                                .field("store", true)
                            .endObject()
                        .endObject()
                    .endObject()
                .endArray()
                .endObject().endObject().string();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping));
        BytesReference source = XContentFactory.jsonBuilder().startObject().field("foo", "bar").endObject().bytes();
        ParsedDocument doc = mapper.parse("test", "type", "id", source);
        Mapper fooMapper = doc.dynamicMappingsUpdate().root().getMapper("foo");
        assertThat(fooMapper, instanceOf(TextFieldMapper.class));
        assertTrue(((TextFieldMapper) fooMapper).fieldType().stored());
    }

    public void testUpgradeTemplateWithDynamicTypeKeyword() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startArray("dynamic_templates")
                    .startObject()
                        .startObject("my_template")
                            .field("match_mapping_type", "string")
                            .startObject("mapping")
                                .field("index", "not_analyzed")
                            .endObject()
                        .endObject()
                    .endObject()
                .endArray()
                .endObject().endObject().string();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping));
        BytesReference source = XContentFactory.jsonBuilder().startObject().field("foo", "bar").endObject().bytes();
        ParsedDocument doc = mapper.parse("test", "type", "id", source);
        Mapper fooMapper = doc.dynamicMappingsUpdate().root().getMapper("foo");
        assertThat(fooMapper, instanceOf(KeywordFieldMapper.class));
        assertWarnings("Expected a boolean [true/false] for property [index] but got [not_analyzed]");
    }

    public void testUpgradeTemplateWithDynamicTypeKeyword2() throws IOException {
        IndexService indexService = createIndex("test");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startArray("dynamic_templates")
                    .startObject()
                        .startObject("my_template")
                            .field("match_mapping_type", "string")
                            .startObject("mapping")
                                .field("type", "{dynamic_type}")
                                .field("index", "not_analyzed")
                            .endObject()
                        .endObject()
                    .endObject()
                .endArray()
                .endObject().endObject().string();
        DocumentMapper mapper = parser.parse("type", new CompressedXContent(mapping));
        BytesReference source = XContentFactory.jsonBuilder().startObject().field("foo", "bar").endObject().bytes();
        ParsedDocument doc = mapper.parse("test", "type", "id", source);
        Mapper fooMapper = doc.dynamicMappingsUpdate().root().getMapper("foo");
        assertThat(fooMapper, instanceOf(KeywordFieldMapper.class));
        assertWarnings("Expected a boolean [true/false] for property [index] but got [not_analyzed]");
    }
}
