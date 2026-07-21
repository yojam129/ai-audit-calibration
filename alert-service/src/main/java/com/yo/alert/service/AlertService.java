package com.yo.alert.service;

import com.yo.alert.domain.vo.*;
import com.yo.alert.mq.*;
import java.util.*;

public interface AlertService {
  void consume(ComparisonEvent e);

  AlertVO claim(UUID id, String owner, long version);

  long reconcileWorkflowLinks();

  List<AlertVO> list();
}
