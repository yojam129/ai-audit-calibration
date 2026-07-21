package com.yo.model.service.impl;

import com.yo.model.domain.dto.ModelRegisterDTO;
import com.yo.model.domain.po.ModelVersionPO;
import com.yo.model.domain.vo.ModelVersionVO;
import com.yo.model.mapper.ModelVersionMapper;
import com.yo.model.enums.ModelStatus;
import com.yo.model.service.ModelArtifactVerifier;
import com.yo.model.service.ModelRegistryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelRegistryServiceImpl implements ModelRegistryService {
  private final ModelVersionMapper mapper;
  private final ModelArtifactVerifier artifactVerifier;
  private final JdbcTemplate jdbc;

  public ModelRegistryServiceImpl(
      ModelVersionMapper mapper, ModelArtifactVerifier artifactVerifier, JdbcTemplate jdbc) {
    this.mapper = mapper;
    this.artifactVerifier = artifactVerifier;
    this.jdbc = jdbc;
  }

  @Transactional
  public ModelVersionVO register(ModelRegisterDTO d) {
    ModelVersionPO existing =
        mapper.selectOne(
            new LambdaQueryWrapper<ModelVersionPO>()
                .eq(ModelVersionPO::getModelCode, d.modelCode())
                .eq(ModelVersionPO::getVersion, d.version())
                .last("LIMIT 1"));
    if (existing != null) {
      if (!existing.checksum.equalsIgnoreCase(d.checksum())
          || !existing.artifactUri.equals(d.artifactUri()))
        throw new IllegalStateException("model version already exists with different artifact");
      return vo(existing);
    }
    var p = new ModelVersionPO();
    p.modelCode = d.modelCode();
    p.version = d.version();
    p.runtime = d.runtime();
    p.artifactUri = d.artifactUri();
    p.checksum = d.checksum();
    p.metricsJson = d.metricsJson();
    artifactVerifier.verify(d.artifactUri(), d.checksum());
    p.status = ModelStatus.VALIDATED.name();
    p.trafficPercent = 0;
    p.createdAt = LocalDateTime.now();
    mapper.insert(p);
    audit(p.id, null, ModelStatus.VALIDATED, 0, "artifact SHA-256 verified");
    return vo(p);
  }

  @Transactional
  public ModelVersionVO deploy(Long id, int percent) {
    if (percent < 0 || percent > 100)
      throw new IllegalArgumentException("trafficPercent must be 0..100");
    var p = mapper.selectById(id);
    if (p == null) throw new IllegalArgumentException("model version not found");
    ModelStatus current = ModelStatus.valueOf(p.status);
    ModelStatus target = percent == 100 ? ModelStatus.ACTIVE : percent > 0 ? ModelStatus.CANARY : ModelStatus.INACTIVE;
    if (current == target && p.trafficPercent == percent) return vo(p);
    if (!current.canTransitionTo(target))
      throw new IllegalStateException("Illegal model transition: " + current + " -> " + target);
    if (target == ModelStatus.ACTIVE) {
      for (ModelVersionPO active :
          mapper.selectList(
              new LambdaQueryWrapper<ModelVersionPO>()
                  .eq(ModelVersionPO::getModelCode, p.modelCode)
                  .eq(ModelVersionPO::getStatus, ModelStatus.ACTIVE.name()))) {
        active.status = ModelStatus.INACTIVE.name();
        active.trafficPercent = 0;
        mapper.updateById(active);
        audit(active.id, ModelStatus.ACTIVE, ModelStatus.INACTIVE, 0, "superseded by activation");
      }
    }
    p.status = target.name();
    p.trafficPercent = percent;
    mapper.updateById(p);
    audit(p.id, current, target, percent, "deployment changed");
    return vo(p);
  }

  @Override
  public ModelVersionVO current(String modelCode) {
    ModelVersionPO p =
        mapper.selectOne(
            new LambdaQueryWrapper<ModelVersionPO>()
                .eq(ModelVersionPO::getModelCode, modelCode)
                .eq(ModelVersionPO::getStatus, ModelStatus.ACTIVE.name())
                .last("LIMIT 1"));
    if (p == null) throw new IllegalArgumentException("No active model: " + modelCode);
    return vo(p);
  }

  @Override
  @Transactional
  public ModelVersionVO rollback(Long id, String reason) {
    ModelVersionPO failed = mapper.selectById(id);
    if (failed == null) throw new IllegalArgumentException("model version not found");
    ModelStatus from = ModelStatus.valueOf(failed.status);
    if (!from.canTransitionTo(ModelStatus.ROLLED_BACK))
      throw new IllegalStateException("Model cannot be rolled back from " + from);
    failed.status = ModelStatus.ROLLED_BACK.name();
    failed.trafficPercent = 0;
    mapper.updateById(failed);
    audit(failed.id, from, ModelStatus.ROLLED_BACK, 0, reason);
    ModelVersionPO previous =
        mapper.selectOne(
            new LambdaQueryWrapper<ModelVersionPO>()
                .eq(ModelVersionPO::getModelCode, failed.modelCode)
                .eq(ModelVersionPO::getStatus, ModelStatus.INACTIVE.name())
                .orderByDesc(ModelVersionPO::getId)
                .last("LIMIT 1"));
    if (previous == null) throw new IllegalStateException("No validated previous model for rollback");
    previous.status = ModelStatus.ACTIVE.name();
    previous.trafficPercent = 100;
    mapper.updateById(previous);
    audit(previous.id, ModelStatus.INACTIVE, ModelStatus.ACTIVE, 100, "rollback target");
    return vo(previous);
  }

  private void audit(
      Long id, ModelStatus from, ModelStatus to, int trafficPercent, String reason) {
    jdbc.update(
        "INSERT INTO model_lifecycle_audit(model_version_id,from_status,to_status,traffic_percent,reason) VALUES (?,?,?,?,?)",
        id,
        from == null ? null : from.name(),
        to.name(),
        trafficPercent,
        reason == null ? "" : reason);
  }

  private ModelVersionVO vo(ModelVersionPO p) {
    return new ModelVersionVO(
        p.id,
        p.modelCode,
        p.version,
        p.runtime,
        p.artifactUri,
        p.checksum,
        p.status,
        p.trafficPercent,
        p.createdAt);
  }
}
