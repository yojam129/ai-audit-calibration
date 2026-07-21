package com.yo.judgement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.api.client.sample.SampleClient;
import com.yo.common.domain.vo.ApiResponse;
import com.yo.judgement.domain.vo.ComparisonSummaryRow;
import com.yo.judgement.domain.dto.ComparisonDTO;
import com.yo.judgement.enums.JudgementEnums.Consistency;
import com.yo.judgement.enums.JudgementEnums.Label;
import com.yo.judgement.mapper.ComparisonRunMapper;
import com.yo.judgement.mapper.OutboxEventMapper;
import com.yo.judgement.service.impl.ComparisonServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ComparisonServiceImplTest {
  private final ComparisonRunMapper mapper = mock(ComparisonRunMapper.class);
  private final SampleClient samples = mock(SampleClient.class);
  private final ComparisonServiceImpl service =
      new ComparisonServiceImpl(
          mapper, mock(OutboxEventMapper.class), new ObjectMapper().findAndRegisterModules(), samples);

  @Test
  void pageKeepsDirtyHistoricalRowsAndUsesFallbackSampleNumber() {
    var row = row("invalid-id", "invalid-sample-id");
    var source = new Page<ComparisonSummaryRow>(1, 20);
    source.setTotal(1);
    source.setRecords(List.of(row));
    when(mapper.selectSummaryPage(any(), isNull())).thenReturn(source);

    var result = service.page(1, 20, null);

    assertThat(result.getTotal()).isEqualTo(1);
    assertThat(result.getRecords()).singleElement().satisfies(summary -> {
      assertThat(summary.getId()).isNull();
      assertThat(summary.getSampleId()).isNull();
      assertThat(summary.getSampleNo()).isEqualTo("未关联历史样本");
    });
    verifyNoInteractions(samples);
  }

  @Test
  void detailReturnsSampleNumberAndStructuredTargetEvidence() {
    var comparisonId = UUID.randomUUID();
    var sampleId = UUID.randomUUID();
    var row = row(comparisonId.toString(), sampleId.toString());
    row.setTargetsJson("""
        [{
          "targetCode":"FluA",
          "systemLabel":"POSITIVE",
          "primaryLabel":"NEGATIVE",
          "aiLabel":"INDETERMINATE",
          "consistency":"ALL_DIFFERENT",
          "dissentingSource":null,
          "riskRank":4,
          "reasonCodes":["ALL_THREE_DIFFERENT"]
        }]
        """);
    when(mapper.selectSummaryById(comparisonId.toString())).thenReturn(row);
    when(samples.getByBusinessId(sampleId))
        .thenReturn(ApiResponse.ok(new SampleClient.SampleVO(
            1L, "SAMPLE-001", "ORG-1", "REVIEWING", "SWAB", null)));

    var detail = service.detail(comparisonId);

    assertThat(detail.getSampleNo()).isEqualTo("SAMPLE-001");
    assertThat(detail.getTargets()).singleElement().satisfies(target -> {
      assertThat(target.getTargetCode()).isEqualTo("FluA");
      assertThat(target.getSystemLabel()).isEqualTo("POSITIVE");
      assertThat(target.getPrimaryLabel()).isEqualTo("NEGATIVE");
      assertThat(target.getAiLabel()).isEqualTo("INDETERMINATE");
      assertThat(target.getConsistency()).isEqualTo("ALL_DIFFERENT");
      assertThat(target.getRiskRank()).isEqualTo(4);
      assertThat(target.getReasonCodes()).containsExactly("ALL_THREE_DIFFERENT");
    });
  }

  @Test
  void positiveNegativeAndInvalidAreHighestRiskAllDifferent() {
    var result = service.compare(new ComparisonDTO(
        UUID.randomUUID(), 1L, "primary", 2L, 1000L,
        List.of(new ComparisonDTO.TargetDTO(
            "RSV", Label.POSITIVE, Label.NEGATIVE, Label.INVALID,
            0.9, false, false, false))));

    assertThat(result.consistency()).isEqualTo(Consistency.ALL_DIFFERENT);
    assertThat(result.riskRank()).isEqualTo(4);
    assertThat(result.reasonCodes()).containsExactly("ALL_THREE_DIFFERENT");
  }

  private ComparisonSummaryRow row(String id, String sampleId) {
    var row = new ComparisonSummaryRow();
    row.setId(id);
    row.setSampleId(sampleId);
    row.setComparisonVersion(1);
    row.setConsistency("ALL_DIFFERENT");
    row.setRiskRank(4);
    row.setReasonCodes("ALL_THREE_DIFFERENT");
    row.setCreatedAt(Instant.parse("2026-07-20T00:00:00Z"));
    return row;
  }
}
