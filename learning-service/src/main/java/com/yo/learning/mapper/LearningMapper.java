package com.yo.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.learning.domain.po.LearningAssignment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LearningMapper extends BaseMapper<LearningAssignment> {}
