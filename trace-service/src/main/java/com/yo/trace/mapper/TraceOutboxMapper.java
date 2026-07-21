package com.yo.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.trace.domain.po.TraceOutbox;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TraceOutboxMapper extends BaseMapper<TraceOutbox> {}
