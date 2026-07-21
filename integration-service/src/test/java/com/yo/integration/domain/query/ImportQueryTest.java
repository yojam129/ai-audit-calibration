package com.yo.integration.domain.query;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ImportQueryTest {
  @Test
  void boundsPagination() {
    var q = new ImportQuery(null, null, 0, 999);
    assertEquals(1, q.pageNo());
    assertEquals(100, q.pageSize());
  }
}
