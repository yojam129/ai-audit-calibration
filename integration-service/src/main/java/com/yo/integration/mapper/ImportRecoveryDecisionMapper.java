package com.yo.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.integration.domain.po.ImportRecoveryDecision;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ImportRecoveryDecisionMapper extends BaseMapper<ImportRecoveryDecision> {
  @Insert("""
      INSERT IGNORE INTO import_recovery_decision
          (workflow_token, failure_scope, subject_id, resolution, process_instance_id, resolved_at)
      VALUES
          (#{workflowToken}, #{failureScope}, #{subjectId}, #{resolution},
           #{processInstanceId}, #{resolvedAt})
      """)
  int insertIgnore(ImportRecoveryDecision decision);
}
