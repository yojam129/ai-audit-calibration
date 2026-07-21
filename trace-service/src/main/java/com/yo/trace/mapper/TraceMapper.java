package com.yo.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.trace.domain.po.TraceRecord;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TraceMapper extends BaseMapper<TraceRecord> {
  @Select("select * from trace_record order by id desc limit 1 for update")
  TraceRecord latest();

  @Select("select get_lock('trace_hash_chain', 5)")
  Integer acquireHashChainLock();

  @Select("select release_lock('trace_hash_chain')")
  Integer releaseHashChainLock();
}
