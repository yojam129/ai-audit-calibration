package com.yo.integration.service;

import com.yo.common.domain.vo.PageVO;
import com.yo.integration.domain.dto.*;
import com.yo.integration.domain.query.ImportQuery;
import com.yo.integration.domain.vo.*;
import java.util.List;

public interface IntegrationService {
  PresignVO presign(PresignDTO dto);

  FileAssetVO confirmUpload(ConfirmUploadDTO dto);

  FileAssetVO getAsset(long id);

  ImportVO createImport(CreateImportDTO dto);

  PageVO<ImportVO> page(ImportQuery query);

  List<ImportErrorVO> errors(long batchId);

  boolean retryFailedRow(long batchId, int rowNo);

  boolean resolveFailedRow(long batchId, int rowNo, String resolution, String reason);

  ImportVO resolveFailedBatch(long batchId, String resolution, String reason);
}
