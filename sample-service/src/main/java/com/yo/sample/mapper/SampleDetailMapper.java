package com.yo.sample.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SampleDetailMapper {
  @Select("""
      SELECT r.run_no
      FROM sample s
      JOIN detection_order o ON o.sample_id = s.id
      JOIN instrument_run r ON r.order_id = o.id
      WHERE s.business_id = #{businessId}
      ORDER BY r.ended_at DESC, r.id DESC
      LIMIT 1
      """)
  String selectLatestRunNoByBusinessId(@Param("businessId") String businessId);

  @Select("""
      SELECT o.id AS order_id, o.order_no, o.assay_code, o.status AS order_status,
             r.id AS run_id, r.run_no, r.instrument_no, r.module_position,
             r.panel_code, r.instrument_type, r.status AS run_status,
             r.qc_status, r.qc_evidence_json,
             r.target_mapping_json, r.overall_result_json,
             r.started_at, r.ended_at, c.cartridge_no, l.lot_no AS reagent_lot_no,
             t.chamber, t.channel_code, t.target_code, t.system_judgement, t.ct_value,
             t.concentration_value, t.concentration_unit, t.risk_level, t.risk_flags
      FROM detection_order o
      LEFT JOIN instrument_run r ON r.order_id = o.id
      LEFT JOIN cartridge c ON c.id = r.cartridge_id
      LEFT JOIN reagent_lot l ON l.id = c.reagent_lot_id
      LEFT JOIN target_judgement t ON t.run_id = r.id
      WHERE o.sample_id = #{sampleId}
      ORDER BY o.id DESC, r.id DESC, t.chamber ASC, t.target_code ASC
      """)
  List<DetailRow> selectSampleDetail(@Param("sampleId") long sampleId);

  record DetailRow(
      Long orderId,
      String orderNo,
      String assayCode,
      String orderStatus,
      Long runId,
      String runNo,
      String instrumentNo,
      String modulePosition,
      String panelCode,
      String instrumentType,
      String runStatus,
      String qcStatus,
      String qcEvidenceJson,
      String targetMappingJson,
      String overallResultJson,
      LocalDateTime startedAt,
      LocalDateTime endedAt,
      String cartridgeNo,
      String reagentLotNo,
      String chamber,
      String channelCode,
      String targetCode,
      String systemJudgement,
      Double ctValue,
      Double concentrationValue,
      String concentrationUnit,
      String riskLevel,
      String riskFlags) {}
}
