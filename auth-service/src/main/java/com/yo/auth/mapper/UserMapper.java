package com.yo.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.auth.domain.po.UserPO;
import java.util.Optional;
import java.util.Set;
import org.apache.ibatis.annotations.*;

public interface UserMapper extends BaseMapper<UserPO> {
  @Select(
      """
      select *
      from sys_user
      where username = #{username}
        and enabled = true
        and status = 'ACTIVE'
        and deleted = false
      limit 1
      """)
  Optional<UserPO> findByUsername(String username);

  @Select(
      """
      select r.role_code
      from sys_role r
      join sys_user_role ur on ur.role_id = r.id
      where ur.user_id = #{userId}
        and r.status = 'ACTIVE'
        and r.deleted = false
        and (ur.expires_at is null or ur.expires_at > current_timestamp)
      """)
  Set<String> findRoles(Long userId);

  @Select(
      """
      select distinct p.permission_code
      from sys_permission p
      join sys_role_permission rp on rp.permission_id = p.id
      join sys_role r on r.id = rp.role_id
      join sys_user_role ur on ur.role_id = r.id
      where ur.user_id = #{userId}
        and p.status = 'ACTIVE'
        and p.deleted = false
        and r.status = 'ACTIVE'
        and r.deleted = false
        and (ur.expires_at is null or ur.expires_at > current_timestamp)
        and not exists (
          select 1 from sys_user_permission_override o
          where o.user_id = ur.user_id and o.permission_code = p.permission_code
            and o.disabled = true
        )
      """)
  Set<String> findPermissions(Long userId);

  @Insert(
      """
      insert into sys_user_permission_override(user_id,permission_code,disabled,reason,updated_by)
      values(#{userId},#{permission},#{disabled},#{reason},#{operatorId})
      on duplicate key update disabled=values(disabled),reason=values(reason),
        updated_by=values(updated_by),updated_at=current_timestamp(3)
      """)
  void setPermissionOverride(
      @Param("userId") Long userId,
      @Param("permission") String permission,
      @Param("disabled") boolean disabled,
      @Param("reason") String reason,
      @Param("operatorId") Long operatorId);

  @Update("update sys_user set token_version=token_version+1 where id=#{userId}")
  int incrementTokenVersion(@Param("userId") Long userId);

  @Insert(
      """
      insert ignore into auth_operation_event
        (event_id,user_id,operation,approved_by_auth_user_id,reason,created_at)
      values(#{eventId},#{userId},#{operation},#{approvedBy},#{reason},current_timestamp(3))
      """)
  int claimOperation(
      @Param("eventId") String eventId,
      @Param("userId") Long userId,
      @Param("operation") String operation,
      @Param("approvedBy") Long approvedBy,
      @Param("reason") String reason);
}
