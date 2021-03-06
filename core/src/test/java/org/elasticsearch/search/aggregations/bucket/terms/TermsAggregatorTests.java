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
package org.elasticsearch.search.aggregations.bucket.terms;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.index.mapper.KeywordFieldMapper;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.search.aggregations.AggregatorTestCase;
import org.elasticsearch.search.aggregations.support.ValueType;

public class TermsAggregatorTests extends AggregatorTestCase {

    public void testTermsAggregator() throws Exception {
        Directory directory = newDirectory();
        RandomIndexWriter indexWriter = new RandomIndexWriter(random(), directory);
        Document document = new Document();
        document.add(new SortedSetDocValuesField("string", new BytesRef("a")));
        document.add(new SortedSetDocValuesField("string", new BytesRef("b")));
        indexWriter.addDocument(document);
        document = new Document();
        document.add(new SortedSetDocValuesField("string", new BytesRef("c")));
        document.add(new SortedSetDocValuesField("string", new BytesRef("a")));
        indexWriter.addDocument(document);
        document = new Document();
        document.add(new SortedSetDocValuesField("string", new BytesRef("b")));
        document.add(new SortedSetDocValuesField("string", new BytesRef("d")));
        indexWriter.addDocument(document);
        indexWriter.close();

        IndexReader indexReader = DirectoryReader.open(directory);
        // We do not use LuceneTestCase.newSearcher because we need a DirectoryReader
        IndexSearcher indexSearcher = new IndexSearcher(indexReader);

        for (TermsAggregatorFactory.ExecutionMode executionMode : TermsAggregatorFactory.ExecutionMode.values()) {
            TermsAggregationBuilder aggregationBuilder = new TermsAggregationBuilder("_name", ValueType.STRING)
                .executionHint(executionMode.toString())
                .field("string")
                .order(Terms.Order.term(true));
            MappedFieldType fieldType = new KeywordFieldMapper.KeywordFieldType();
            fieldType.setName("string");
            fieldType.setHasDocValues(true );
            try (TermsAggregator aggregator = createAggregator(aggregationBuilder, indexSearcher, fieldType)) {
                aggregator.preCollection();
                indexSearcher.search(new MatchAllDocsQuery(), aggregator);
                aggregator.postCollection();
                Terms result = (Terms) aggregator.buildAggregation(0L);
                assertEquals(4, result.getBuckets().size());
                assertEquals("a", result.getBuckets().get(0).getKeyAsString());
                assertEquals(2L, result.getBuckets().get(0).getDocCount());
                assertEquals("b", result.getBuckets().get(1).getKeyAsString());
                assertEquals(2L, result.getBuckets().get(1).getDocCount());
                assertEquals("c", result.getBuckets().get(2).getKeyAsString());
                assertEquals(1L, result.getBuckets().get(2).getDocCount());
                assertEquals("d", result.getBuckets().get(3).getKeyAsString());
                assertEquals(1L, result.getBuckets().get(3).getDocCount());
            }
        }
        indexReader.close();
        directory.close();
    }
}
