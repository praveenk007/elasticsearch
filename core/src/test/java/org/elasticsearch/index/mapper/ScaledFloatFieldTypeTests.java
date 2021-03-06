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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.IOUtils;
import org.elasticsearch.Version;
import org.elasticsearch.action.fieldstats.FieldStats;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.fielddata.AtomicNumericFieldData;
import org.elasticsearch.index.fielddata.IndexNumericFieldData;
import org.elasticsearch.index.fielddata.SortedNumericDoubleValues;
import org.junit.Before;

import java.io.IOException;
import java.util.Arrays;

public class ScaledFloatFieldTypeTests extends FieldTypeTestCase {

    @Override
    protected MappedFieldType createDefaultFieldType() {
        ScaledFloatFieldMapper.ScaledFloatFieldType ft = new ScaledFloatFieldMapper.ScaledFloatFieldType();
        ft.setScalingFactor(100);
        return ft;
    }

    @Before
    public void setupProperties() {
        addModifier(new Modifier("scaling_factor", false) {
            @Override
            public void modify(MappedFieldType ft) {
                ScaledFloatFieldMapper.ScaledFloatFieldType tft = (ScaledFloatFieldMapper.ScaledFloatFieldType)ft;
                tft.setScalingFactor(10);
            }
            @Override
            public void normalizeOther(MappedFieldType other) {
                super.normalizeOther(other);
                ((ScaledFloatFieldMapper.ScaledFloatFieldType) other).setScalingFactor(100);
            }
        });
    }

    public void testTermQuery() {
        ScaledFloatFieldMapper.ScaledFloatFieldType ft = new ScaledFloatFieldMapper.ScaledFloatFieldType();
        ft.setName("scaled_float");
        ft.setScalingFactor(0.1 + randomDouble() * 100);
        double value = (randomDouble() * 2 - 1) * 10000;
        long scaledValue = Math.round(value * ft.getScalingFactor());
        assertEquals(LongPoint.newExactQuery("scaled_float", scaledValue), ft.termQuery(value, null));
    }

    public void testTermsQuery() {
        ScaledFloatFieldMapper.ScaledFloatFieldType ft = new ScaledFloatFieldMapper.ScaledFloatFieldType();
        ft.setName("scaled_float");
        ft.setScalingFactor(0.1 + randomDouble() * 100);
        double value1 = (randomDouble() * 2 - 1) * 10000;
        long scaledValue1 = Math.round(value1 * ft.getScalingFactor());
        double value2 = (randomDouble() * 2 - 1) * 10000;
        long scaledValue2 = Math.round(value2 * ft.getScalingFactor());
        assertEquals(
                LongPoint.newSetQuery("scaled_float", scaledValue1, scaledValue2),
                ft.termsQuery(Arrays.asList(value1, value2), null));
    }

    public void testRangeQuery() throws IOException {
        // make sure the accuracy loss of scaled floats only occurs at index time
        // this test checks that searching scaled floats yields the same results as
        // searching doubles that are rounded to the closest half float
        ScaledFloatFieldMapper.ScaledFloatFieldType ft = new ScaledFloatFieldMapper.ScaledFloatFieldType();
        ft.setName("scaled_float");
        ft.setScalingFactor(0.1 + randomDouble() * 100);
        Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(null));
        final int numDocs = 1000;
        for (int i = 0; i < numDocs; ++i) {
            Document doc = new Document();
            double value = (randomDouble() * 2 - 1) * 10000;
            long scaledValue = Math.round(value * ft.getScalingFactor());
            double rounded = scaledValue / ft.getScalingFactor();
            doc.add(new LongPoint("scaled_float", scaledValue));
            doc.add(new DoublePoint("double", rounded));
            w.addDocument(doc);
        }
        final DirectoryReader reader = DirectoryReader.open(w);
        w.close();
        IndexSearcher searcher = newSearcher(reader);
        final int numQueries = 1000;
        for (int i = 0; i < numQueries; ++i) {
            Double l = randomBoolean() ? null : (randomDouble() * 2 - 1) * 10000;
            Double u = randomBoolean() ? null : (randomDouble() * 2 - 1) * 10000;
            boolean includeLower = randomBoolean();
            boolean includeUpper = randomBoolean();
            Query doubleQ = NumberFieldMapper.NumberType.DOUBLE.rangeQuery("double", l, u, includeLower, includeUpper);
            Query scaledFloatQ = ft.rangeQuery(l, u, includeLower, includeUpper, null);
            assertEquals(searcher.count(doubleQ), searcher.count(scaledFloatQ));
        }
        IOUtils.close(reader, dir);
    }

    public void testValueForSearch() {
        ScaledFloatFieldMapper.ScaledFloatFieldType ft = new ScaledFloatFieldMapper.ScaledFloatFieldType();
        ft.setName("scaled_float");
        ft.setScalingFactor(0.1 + randomDouble() * 100);
        assertNull(ft.valueForDisplay(null));
        assertEquals(10/ft.getScalingFactor(), ft.valueForDisplay(10L));
    }

    public void testStats() throws IOException {
        ScaledFloatFieldMapper.ScaledFloatFieldType ft = new ScaledFloatFieldMapper.ScaledFloatFieldType();
        ft.setName("scaled_float");
        ft.setScalingFactor(0.1 + randomDouble() * 100);
        Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(null));
        try (DirectoryReader reader = DirectoryReader.open(w)) {
            assertNull(ft.stats(reader));
        }
        Document doc = new Document();
        doc.add(new StoredField("scaled_float", -1));
        w.addDocument(doc);
        try (DirectoryReader reader = DirectoryReader.open(w)) {
            // field exists, but has no point values
            FieldStats<?> stats = ft.stats(reader);
            assertFalse(stats.hasMinMax());
            assertNull(stats.getMinValue());
            assertNull(stats.getMaxValue());
        }
        LongPoint point = new LongPoint("scaled_float", -1);
        doc.add(point);
        w.addDocument(doc);
        point.setLongValue(10);
        w.addDocument(doc);
        try (DirectoryReader reader = DirectoryReader.open(w)) {
            FieldStats<?> stats = ft.stats(reader);
            assertEquals(-1/ft.getScalingFactor(), stats.getMinValue());
            assertEquals(10/ft.getScalingFactor(), stats.getMaxValue());
            assertEquals(3, stats.getMaxDoc());
        }
        w.deleteAll();
        try (DirectoryReader reader = DirectoryReader.open(w)) {
            assertNull(ft.stats(reader));
        }
        IOUtils.close(w, dir);
    }

    public void testFieldData() throws IOException {
        ScaledFloatFieldMapper.ScaledFloatFieldType ft = new ScaledFloatFieldMapper.ScaledFloatFieldType();
        ft.setScalingFactor(0.1 + randomDouble() * 100);
        Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(null));
        Document doc = new Document();
        doc.add(new SortedNumericDocValuesField("scaled_float1", 10));
        doc.add(new SortedNumericDocValuesField("scaled_float2", 5));
        doc.add(new SortedNumericDocValuesField("scaled_float2", 12));
        w.addDocument(doc);
        try (DirectoryReader reader = DirectoryReader.open(w)) {
            IndexMetaData indexMetadata = new IndexMetaData.Builder("index").settings(
                    Settings.builder()
                    .put("index.version.created", Version.CURRENT)
                    .put("index.number_of_shards", 1)
                    .put("index.number_of_replicas", 0).build()).build();
            IndexSettings indexSettings = new IndexSettings(indexMetadata, Settings.EMPTY);

            // single-valued
            ft.setName("scaled_float1");
            IndexNumericFieldData fielddata = (IndexNumericFieldData) ft.fielddataBuilder().build(indexSettings, ft, null, null, null);
            assertEquals(fielddata.getNumericType(), IndexNumericFieldData.NumericType.DOUBLE);
            AtomicNumericFieldData leafFieldData = fielddata.load(reader.leaves().get(0));
            SortedNumericDoubleValues values = leafFieldData.getDoubleValues();
            values.setDocument(0);
            assertEquals(1, values.count());
            assertEquals(10/ft.getScalingFactor(), values.valueAt(0), 10e-5);

            // multi-valued
            ft.setName("scaled_float2");
            fielddata = (IndexNumericFieldData) ft.fielddataBuilder().build(indexSettings, ft, null, null, null);
            leafFieldData = fielddata.load(reader.leaves().get(0));
            values = leafFieldData.getDoubleValues();
            values.setDocument(0);
            assertEquals(2, values.count());
            assertEquals(5/ft.getScalingFactor(), values.valueAt(0), 10e-5);
            assertEquals(12/ft.getScalingFactor(), values.valueAt(1), 10e-5);
        }
        IOUtils.close(w, dir);
    }
}
