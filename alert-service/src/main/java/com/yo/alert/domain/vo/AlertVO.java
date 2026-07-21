package com.yo.alert.domain.vo;

import com.yo.alert.enums.AlertEnums.*;
import java.time.*;
import java.util.*;

public record AlertVO(
    UUID id,
    UUID sampleId,
    long comparisonVersion,
    Level level,
    Status status,
    List<String> reasonCodes,
    String alertLogic,
    Instant slaDueAt,
    String ownerId,
    String processInstanceId,
    String flowableTaskId,
    long version) {}
