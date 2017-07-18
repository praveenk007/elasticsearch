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
package org.elasticsearch.search.aggregations;

import org.apache.lucene.index.CompositeReaderContext;
import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCache;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.Weight;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.MockBigArrays;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.cache.query.DisabledQueryCache;
import org.elasticsearch.index.engine.Engine;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.fielddata.IndexFieldDataCache;
import org.elasticsearch.index.fielddata.IndexFieldDataService;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.support.NestedScope;
import org.elasticsearch.indices.breaker.CircuitBreakerService;
import org.elasticsearch.indices.breaker.NoneCircuitBreakerService;
import org.elasticsearch.mock.orig.Mockito;
import org.elasticsearch.search.fetch.FetchPhase;
import org.elasticsearch.search.fetch.subphase.DocValueFieldsFetchSubPhase;
import org.elasticsearch.search.fetch.subphase.FetchSourceSubPhase;
import org.elasticsearch.search.internal.ContextIndexSearcher;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.search.lookup.SearchLookup;
import org.elasticsearch.test.ESTestCase;
import org.mockito.Matchers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base class for testing {@link Aggregator} implementations.
 * Provides helpers for constructing and searching an {@link Aggregator} implementation based on a provided
 * {@link AggregationBuilder} instance.
 */
public abstract class AggregatorTestCase extends ESTestCase {

    protected <B extends AggregationBuilder> AggregatorFactory<?> createAggregatorFactory(B aggregationBuilder,
            IndexSearcher indexSearcher,
            MappedFieldType... fieldTypes) throws IOException {

        IndexSettings indexSettings = new IndexSettings(
            IndexMetaData.builder("_index").settings(Settings.builder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT))
                .numberOfShards(1)
                .numberOfReplicas(0)
                .creationDate(System.currentTimeMillis())
                .build(),
            Settings.EMPTY
        );

        Engine.Searcher searcher = new Engine.Searcher("aggregator_test", indexSearcher);
        QueryCache queryCache = new DisabledQueryCache(indexSettings);
        QueryCachingPolicy queryCachingPolicy = new QueryCachingPolicy() {
            @Override
            public void onUse(Query query) {
            }

            @Override
            public boolean shouldCache(Query query) throws IOException {
                // never cache a query
                return false;
            }
        };
        ContextIndexSearcher contextIndexSearcher = new ContextIndexSearcher(searcher, queryCache, queryCachingPolicy);

        CircuitBreakerService circuitBreakerService = new NoneCircuitBreakerService();
        SearchContext searchContext = mock(SearchContext.class);
        when(searchContext.numberOfShards()).thenReturn(1);
        when(searchContext.searcher()).thenReturn(contextIndexSearcher);
        when(searchContext.bigArrays()).thenReturn(new MockBigArrays(Settings.EMPTY, circuitBreakerService));
        when(searchContext.fetchPhase())
            .thenReturn(new FetchPhase(Arrays.asList(new FetchSourceSubPhase(), new DocValueFieldsFetchSubPhase())));

        // TODO: now just needed for top_hits, this will need to be revised for other agg unit tests:
        MapperService mapperService = mock(MapperService.class);
        when(mapperService.hasNested()).thenReturn(false);
        when(searchContext.mapperService()).thenReturn(mapperService);

        SearchLookup searchLookup = new SearchLookup(mapperService, mock(IndexFieldDataService.class), new String[]{"type"});
        when(searchContext.lookup()).thenReturn(searchLookup);

        QueryShardContext queryShardContext = queryShardContextMock(fieldTypes, indexSettings, circuitBreakerService);
        when(searchContext.getQueryShardContext()).thenReturn(queryShardContext);
        return aggregationBuilder.build(searchContext, null);
    }

    protected <A extends Aggregator, B extends AggregationBuilder> A createAggregator(B aggregationBuilder,
            IndexSearcher indexSearcher,
            MappedFieldType... fieldTypes) throws IOException {
        AggregatorFactory<?> factory = createAggregatorFactory(aggregationBuilder, indexSearcher, fieldTypes);
        @SuppressWarnings("unchecked")
        A aggregator = (A) factory.create(null, true);
        return aggregator;
    }

    /**
     * sub-tests that need a more complex mock can overwrite this
     */
    protected MapperService mapperServiceMock() {
        return mock(MapperService.class);
    }

    /**
     * sub-tests that need a more complex mock can overwrite this
     */
    protected QueryShardContext queryShardContextMock(MappedFieldType[] fieldTypes, IndexSettings indexSettings,
            CircuitBreakerService circuitBreakerService) {
        QueryShardContext queryShardContext = mock(QueryShardContext.class);
        for (MappedFieldType fieldType : fieldTypes) {
            IndexFieldData<?> fieldData = fieldType.fielddataBuilder().build(indexSettings, fieldType,
                new IndexFieldDataCache.None(), circuitBreakerService, mock(MapperService.class));
            when(queryShardContext.fieldMapper(fieldType.name())).thenReturn(fieldType);
            when(queryShardContext.getForField(fieldType)).thenReturn(fieldData);
        }
        NestedScope nestedScope = new NestedScope();
        when(queryShardContext.isFilter()).thenCallRealMethod();
        Mockito.doCallRealMethod().when(queryShardContext).setIsFilter(Matchers.anyBoolean());
        when(queryShardContext.nestedScope()).thenReturn(nestedScope);
        return queryShardContext;
    }

    protected <A extends InternalAggregation, C extends Aggregator> A search(IndexSearcher searcher,
                                                                             Query query,
                                                                             AggregationBuilder builder,
                                                                             MappedFieldType... fieldTypes) throws IOException {
        try (C a = createAggregator(builder, searcher, fieldTypes)) {
            a.preCollection();
            searcher.search(query, a);
            a.postCollection();
            @SuppressWarnings("unchecked")
            A internalAgg = (A) a.buildAggregation(0L);
            return internalAgg;
        }
    }

    /**
     * Divides the provided {@link IndexSearcher} in sub-searcher, one for each segment,
     * builds an aggregator for each sub-searcher filtered by the provided {@link Query} and
     * returns the reduced {@link InternalAggregation}.
     */
    protected <A extends InternalAggregation, C extends Aggregator> A searchAndReduce(IndexSearcher searcher,
                                                                                      Query query,
                                                                                      AggregationBuilder builder,
                                                                                      MappedFieldType... fieldTypes) throws IOException {
        final IndexReaderContext ctx = searcher.getTopReaderContext();

        final ShardSearcher[] subSearchers;
        if (ctx instanceof LeafReaderContext) {
            subSearchers = new ShardSearcher[1];
            subSearchers[0] = new ShardSearcher((LeafReaderContext) ctx, ctx);
        } else {
            final CompositeReaderContext compCTX = (CompositeReaderContext) ctx;
            final int size = compCTX.leaves().size();
            subSearchers = new ShardSearcher[size];
            for(int searcherIDX=0;searcherIDX<subSearchers.length;searcherIDX++) {
                final LeafReaderContext leave = compCTX.leaves().get(searcherIDX);
                subSearchers[searcherIDX] = new ShardSearcher(leave, compCTX);
            }
        }

        List<InternalAggregation> aggs = new ArrayList<> ();
        Query rewritten = searcher.rewrite(query);
        Weight weight = searcher.createWeight(rewritten, true);
        C root = createAggregator(builder, searcher, fieldTypes);
        try {
            for (ShardSearcher subSearcher : subSearchers) {
                C a = createAggregator(builder, subSearcher, fieldTypes);
                try {
                    a.preCollection();
                    subSearcher.search(weight, a);
                    a.postCollection();
                    aggs.add(a.buildAggregation(0L));
                } finally {
                    closeAgg(a);
                }
            }
            if (aggs.isEmpty()) {
                return null;
            } else {
                @SuppressWarnings("unchecked")
                A internalAgg = (A) aggs.get(0).doReduce(aggs, new InternalAggregation.ReduceContext(root.context().bigArrays(), null));
                return internalAgg;
            }
        } finally {
            closeAgg(root);
        }
    }

    private void closeAgg(Aggregator agg) {
        agg.close();
        for (Aggregator sub : ((AggregatorBase) agg).subAggregators) {
            closeAgg(sub);
        }
    }

    private static class ShardSearcher extends IndexSearcher {
        private final List<LeafReaderContext> ctx;

        ShardSearcher(LeafReaderContext ctx, IndexReaderContext parent) {
            super(parent);
            this.ctx = Collections.singletonList(ctx);
        }

        public void search(Weight weight, Collector collector) throws IOException {
            search(ctx, weight, collector);
        }

        @Override
        public String toString() {
            return "ShardSearcher(" + ctx.get(0) + ")";
        }
    }
}
