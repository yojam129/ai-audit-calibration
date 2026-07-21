package com.yo.notification.controller;

import com.yo.notification.domain.dto.*;
import com.yo.notification.domain.vo.*;
import com.yo.notification.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final NotificationService s;
  private final NotificationPreferenceService preferences;

  public NotificationController(NotificationService s, NotificationPreferenceService preferences) {
    this.s = s;
    this.preferences = preferences;
  }

  @PostMapping
  public NotificationVO send(@Valid @RequestBody NotificationDTO x) {
    return s.send(x);
  }

  @PostMapping("/{id}/read")
  public NotificationVO read(@PathVariable long id) {
    return s.markRead(id);
  }

  @GetMapping
  public com.baomidou.mybatisplus.core.metadata.IPage<NotificationVO> page(
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "20") long size,
      @RequestParam String userId,
      @RequestParam(required = false) Boolean unread) {
    return s.page(current, Math.min(size, 100), userId, unread);
  }

  @GetMapping("/unread-count")
  public long unreadCount(@RequestParam String userId) {
    return s.unreadCount(userId);
  }

  @PostMapping("/replay")
  public void replay() {
    s.retryDue();
  }

  @GetMapping("/preferences/{userId}")
  public NotificationPreferenceVO preference(@PathVariable String userId) {
    return preferences.get(userId);
  }

  @PutMapping("/preferences/{userId}")
  public NotificationPreferenceVO updatePreference(
      @PathVariable String userId, @Valid @RequestBody NotificationPreferenceDTO dto) {
    return preferences.update(userId, dto);
  }
}
