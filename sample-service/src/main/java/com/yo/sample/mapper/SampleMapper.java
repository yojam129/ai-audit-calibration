package com.yo.sample.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.sample.domain.po.Sample;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SampleMapper extends BaseMapper<Sample> {}
