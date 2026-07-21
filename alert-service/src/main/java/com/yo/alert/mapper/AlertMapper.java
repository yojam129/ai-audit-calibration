package com.yo.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.alert.domain.po.AlertPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlertMapper extends BaseMapper<AlertPO> {}
