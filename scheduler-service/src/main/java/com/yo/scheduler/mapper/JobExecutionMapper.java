package com.yo.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.scheduler.domain.JobExecutionPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobExecutionMapper extends BaseMapper<JobExecutionPO> {}
