package com.yo.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.notification.domain.po.NotificationRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<NotificationRecord> {}
