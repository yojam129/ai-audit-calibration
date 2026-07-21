package com.yo.judgement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yo.judgement.domain.po.ComparisonRunPO;
import com.yo.judgement.domain.vo.ComparisonSummaryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ComparisonRunMapper extends BaseMapper<ComparisonRunPO> {
  @Select("""
      SELECT BIN_TO_UUID(id) AS id, BIN_TO_UUID(sample_id) AS sample_id,
             comparison_version, consistency, risk_rank, reason_codes, created_at
      FROM comparison_run
      WHERE #{sampleId} IS NULL OR sample_id = UUID_TO_BIN(#{sampleId})
      ORDER BY created_at DESC
      """)
  IPage<ComparisonSummaryRow> selectSummaryPage(
      Page<ComparisonSummaryRow> page, @Param("sampleId") String sampleId);

  @Select("""
      SELECT BIN_TO_UUID(id) AS id, BIN_TO_UUID(sample_id) AS sample_id,
             comparison_version, consistency, risk_rank, reason_codes, targets_json, created_at
      FROM comparison_run
      WHERE id = UUID_TO_BIN(#{id})
      """)
  ComparisonSummaryRow selectSummaryById(@Param("id") String id);
}
