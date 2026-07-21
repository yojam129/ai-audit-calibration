package com.yo.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.risk.domain.po.ReviewerErrorFocus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface ReviewerErrorFocusMapper extends BaseMapper<ReviewerErrorFocus> {
  @Insert("""
      INSERT INTO reviewer_error_focus
        (event_id, reviewer_id, auth_user_id, sample_id, sample_no, chamber, channel_code,
         target_code, predicted_label, truth_label, error_type, occurred_at, created_at)
      VALUES
        (#{focus.eventId}, #{focus.reviewerId}, #{focus.authUserId}, #{focus.sampleId},
         #{focus.sampleNo}, #{focus.chamber}, #{focus.channelCode}, #{focus.targetCode},
         #{focus.predictedLabel}, #{focus.truthLabel}, #{focus.errorType},
         #{focus.occurredAt}, #{focus.createdAt})
      ON DUPLICATE KEY UPDATE
        sample_id=COALESCE(VALUES(sample_id), sample_id),
        sample_no=COALESCE(VALUES(sample_no), sample_no),
        chamber=COALESCE(VALUES(chamber), chamber),
        channel_code=COALESCE(VALUES(channel_code), channel_code),
        target_code=VALUES(target_code), predicted_label=VALUES(predicted_label),
        truth_label=VALUES(truth_label), error_type=VALUES(error_type)
      """)
  int upsert(@Param("focus") ReviewerErrorFocus focus);
}
