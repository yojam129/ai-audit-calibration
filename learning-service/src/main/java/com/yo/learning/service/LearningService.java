package com.yo.learning.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yo.learning.domain.dto.*;
import com.yo.learning.domain.vo.*;

public interface LearningService {
  long assign(LearningDTO x);

  PermissionRestoreApplicationVO exam(long id, ExamDTO x);

  ExamVO startExam(long assignmentId);

  PermissionRestoreApplicationVO completeTraining(long assignmentId);

  void markExamRequired(long id, String workflowToken);

  void markRestorePending(long id, String workflowToken);

  void restorePermission(long id, String workflowToken);

  IPage<PermissionRestoreApplicationVO> page(
      long current, long size, String reviewerId, String status);
}
