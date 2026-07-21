package com.yo.notification.service;

import com.yo.notification.domain.dto.*;
import com.yo.notification.domain.vo.*;

public interface NotificationService {
  NotificationVO send(NotificationDTO x);

  NotificationVO markRead(long id);

  com.baomidou.mybatisplus.core.metadata.IPage<NotificationVO> page(
      long current, long size, String userId, Boolean unread);

  long unreadCount(String userId);

  void retryDue();
}
