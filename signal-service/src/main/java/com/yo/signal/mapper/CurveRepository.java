package com.yo.signal.mapper;

import com.yo.signal.domain.po.CurveDocument;
import java.util.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CurveRepository extends MongoRepository<CurveDocument, String> {
  Optional<CurveDocument> findByRunNoAndChamberAndChannelCodeAndProcessingVersion(
      String r, String c, String ch, String v);

  List<CurveDocument> findByRunNoOrderByChamberAscChannelCodeAsc(String runNo);

  List<CurveDocument> findByRunNoAndTargetCodeOrderByChamberAscChannelCodeAsc(
      String runNo, String targetCode);
}
