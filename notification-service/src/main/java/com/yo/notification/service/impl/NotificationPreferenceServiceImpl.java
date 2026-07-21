package com.yo.notification.service.impl;

import com.yo.notification.domain.dto.NotificationPreferenceDTO;
import com.yo.notification.domain.po.NotificationPreference;
import com.yo.notification.domain.vo.NotificationPreferenceVO;
import com.yo.notification.mapper.NotificationPreferenceMapper;
import com.yo.notification.service.NotificationPreferenceService;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {
  private final NotificationPreferenceMapper mapper;

  public NotificationPreferenceServiceImpl(NotificationPreferenceMapper mapper) {
    this.mapper = mapper;
  }

  public NotificationPreferenceVO get(String userId) {
    var p = mapper.selectById(userId);
    return p == null ? new NotificationPreferenceVO(userId, null, true, false, Set.of()) : vo(p);
  }

  @Transactional
  public NotificationPreferenceVO update(String userId, NotificationPreferenceDTO d) {
    var p = Optional.ofNullable(mapper.selectById(userId)).orElseGet(NotificationPreference::new);
    p.userId = userId;
    p.email = d.email();
    p.inAppEnabled = d.inAppEnabled();
    p.emailEnabled = d.emailEnabled();
    p.eventTypes = d.eventTypes() == null ? "" : String.join(",", d.eventTypes());
    p.updatedAt = Instant.now();
    if (mapper.selectById(userId) == null) mapper.insert(p);
    else mapper.updateById(p);
    return vo(p);
  }

  public Decision decide(String userId, String type) {
    var p = mapper.selectById(userId);
    if (p == null) return new Decision(true, false, null);
    var types = types(p.eventTypes);
    boolean subscribed = types.isEmpty() || types.contains(type);
    return new Decision(subscribed && p.inAppEnabled, subscribed && p.emailEnabled, p.email);
  }

  private NotificationPreferenceVO vo(NotificationPreference p) {
    return new NotificationPreferenceVO(
        p.userId, p.email, p.inAppEnabled, p.emailEnabled, types(p.eventTypes));
  }

  private Set<String> types(String value) {
    return value == null || value.isBlank()
        ? Set.of()
        : Set.copyOf(Arrays.asList(value.split(",")));
  }
}
