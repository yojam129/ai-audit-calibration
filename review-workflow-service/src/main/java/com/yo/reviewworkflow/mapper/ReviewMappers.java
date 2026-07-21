package com.yo.reviewworkflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.reviewworkflow.domain.po.GroundTruthPO;
import com.yo.reviewworkflow.domain.po.ReviewOutboxPO;
import com.yo.reviewworkflow.domain.po.ReviewTaskPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.*;
import java.time.Instant;

public final class ReviewMappers {
  private ReviewMappers() {}

  @Mapper
  public interface Task extends BaseMapper<ReviewTaskPO> {
    @Update("""
        UPDATE review_task SET status='IN_REVIEW', owner_id=#{ownerId},
          owner_auth_user_id=#{authUserId}, claimed_at=#{claimedAt}, version=version+1
        WHERE id=#{id,typeHandler=com.yo.reviewworkflow.infrastructure.UuidBinaryTypeHandler}
          AND status IN ('PENDING','ESCALATED') AND version=#{version}
        """)
    int claim(@Param("id") java.util.UUID id, @Param("version") long version,
        @Param("ownerId") String ownerId, @Param("authUserId") long authUserId,
        @Param("claimedAt") Instant claimedAt);

    @Update("""
        UPDATE review_task SET status='ARCHIVED', archived_at=#{archivedAt}, version=version+1
        WHERE id=#{id,typeHandler=com.yo.reviewworkflow.infrastructure.UuidBinaryTypeHandler}
          AND status='IN_REVIEW' AND version=#{version} AND owner_auth_user_id=#{authUserId}
        """)
    int finalizeTask(@Param("id") java.util.UUID id, @Param("version") long version,
        @Param("authUserId") long authUserId, @Param("archivedAt") Instant archivedAt);
  }

  @Mapper
  public interface Truth extends BaseMapper<GroundTruthPO> {}

  @Mapper
  public interface Outbox extends BaseMapper<ReviewOutboxPO> {}
}
