package com.yo.notification.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yo.notification.domain.dto.*;
import com.yo.notification.domain.po.*;
import com.yo.notification.domain.vo.*;
import com.yo.notification.mapper.*;
import com.yo.notification.service.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {
  private final NotificationMapper mapper;
  private final JavaMailSender mail;
  private final boolean enabled;
  private final String from;

  public NotificationServiceImpl(
      NotificationMapper m,
      JavaMailSender mail,
      @Value("${notification.email.enabled:false}") boolean enabled,
      @Value("${spring.mail.username:}") String from) {
    mapper = m;
    this.mail = mail;
    this.enabled = enabled;
    this.from = from;
  }

  @Transactional
  public NotificationVO send(NotificationDTO x) {
    NotificationRecord old =
        mapper.selectOne(
            Wrappers.<NotificationRecord>lambdaQuery()
                .eq(NotificationRecord::getRequestId, x.requestId()));
    if (old != null) return vo(old);
    var n = new NotificationRecord();
    n.requestId = x.requestId();
    n.userId = x.userId();
    n.email = x.email();
    n.subject = x.subject();
    n.body = x.body();
    n.createdAt = Instant.now();
    n.status = x.emailRequested() ? "PENDING" : "IN_APP";
    n.nextAttemptAt = x.emailRequested() ? Instant.now() : null;
    mapper.insert(n);
    if (x.emailRequested()) deliver(n);
    return vo(n);
  }

  public IPage<NotificationVO> page(long current, long size, String userId, Boolean unread) {
    var q =
        Wrappers.<NotificationRecord>lambdaQuery()
            .eq(NotificationRecord::getUserId, userId)
            .eq(Boolean.TRUE.equals(unread), NotificationRecord::isReadFlag, false)
            .orderByDesc(NotificationRecord::getCreatedAt);
    return mapper
        .selectPage(new Page<>(Math.max(1, current), Math.max(1, size)), q)
        .convert(this::vo);
  }

  public long unreadCount(String userId) {
    return mapper.selectCount(
        Wrappers.<NotificationRecord>lambdaQuery()
            .eq(NotificationRecord::getUserId, userId)
            .eq(NotificationRecord::isReadFlag, false));
  }

  @Scheduled(fixedDelayString = "${notification.retry-delay:30000}")
  @Transactional
  public void retryDue() {
    mapper
        .selectList(
            Wrappers.<NotificationRecord>lambdaQuery()
                .in(NotificationRecord::getStatus, "PENDING", "FAILED")
                .lt(NotificationRecord::getAttempts, 5)
                .le(NotificationRecord::getNextAttemptAt, Instant.now())
                .last("limit 100"))
        .forEach(this::deliver);
  }

  private void deliver(NotificationRecord n) {
    n.attempts++;
    if (!enabled || from.isBlank() || n.email == null || n.email.isBlank()) {
      n.status = "PENDING";
      n.failureReason = !enabled ? "EMAIL_DISABLED" : "EMAIL_CONFIGURATION_MISSING";
      n.nextAttemptAt = Instant.now().plus(Duration.ofMinutes(5));
      mapper.updateById(n);
      return;
    }
    try {
      var msg = new SimpleMailMessage();
      msg.setFrom(from);
      msg.setTo(n.email);
      msg.setSubject(n.subject);
      msg.setText(n.body);
      mail.send(msg);
      n.status = "SENT";
      n.sentAt = Instant.now();
      n.nextAttemptAt = null;
      n.failureReason = null;
    } catch (RuntimeException e) {
      n.status = "FAILED";
      n.failureReason = e.getClass().getSimpleName();
      n.nextAttemptAt = Instant.now().plusSeconds(Math.min(1800, 1L << Math.min(n.attempts, 10)));
    }
    mapper.updateById(n);
  }

  @Transactional
  public NotificationVO markRead(long id) {
    var n = Optional.ofNullable(mapper.selectById(id)).orElseThrow();
    n.readFlag = true;
    mapper.updateById(n);
    return vo(n);
  }

  private NotificationVO vo(NotificationRecord n) {
    return new NotificationVO(
        n.id, n.requestId, n.userId, n.subject, n.status, n.readFlag, n.createdAt, n.sentAt);
  }
}
