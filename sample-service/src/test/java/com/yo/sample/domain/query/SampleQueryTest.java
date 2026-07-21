package com.yo.sample.domain.query;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SampleQueryTest {
  @Test
  void boundsPagination() {
    var q = new SampleQuery(null, null, 0, 999);
    assertEquals(1, q.pageNo());
    assertEquals(100, q.pageSize());
  }
}
