package com.yo.model.service;

import com.yo.model.domain.dto.ModelRegisterDTO;
import com.yo.model.domain.vo.ModelVersionVO;

public interface ModelRegistryService {
  ModelVersionVO register(ModelRegisterDTO dto);

  ModelVersionVO deploy(Long id, int trafficPercent);

  ModelVersionVO current(String modelCode);

  ModelVersionVO rollback(Long id, String reason);
}
