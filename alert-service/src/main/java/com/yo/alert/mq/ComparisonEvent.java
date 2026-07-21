package com.yo.alert.mq;

import java.time.*;
import java.util.*;

public record ComparisonEvent(
    UUID eventId,
    UUID sampleId,
    long comparisonVersion,
    String consistency,
    int riskRank,
    List<String> reasonCodes,
    Instant occurredAt) {}
