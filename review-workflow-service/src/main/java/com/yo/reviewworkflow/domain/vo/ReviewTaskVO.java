package com.yo.reviewworkflow.domain.vo;

import com.yo.reviewworkflow.enums.ReviewEnums.*;
import java.util.*;

public record ReviewTaskVO(
    UUID id,
    UUID sampleId,
    String sampleNo,
    String priority,
    String consistency,
    Status status,
    String ownerId,
    String processInstanceId,
    String sourceTargetsJson,
    long version) {}
