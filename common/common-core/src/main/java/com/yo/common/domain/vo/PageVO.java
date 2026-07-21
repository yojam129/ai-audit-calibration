package com.yo.common.domain.vo;

import java.util.List;

public record PageVO<T>(long total, long pages, long pageNo, long pageSize, List<T> list) {
  public PageVO {
    list = list == null ? List.of() : List.copyOf(list);
  }
}
