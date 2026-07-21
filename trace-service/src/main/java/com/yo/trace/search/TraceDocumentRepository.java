package com.yo.trace.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface TraceDocumentRepository extends ElasticsearchRepository<TraceDocument, String> {}
