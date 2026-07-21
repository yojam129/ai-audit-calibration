package com.yo.reviewworkflow.infrastructure;

import java.nio.ByteBuffer;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(UUID.class)
@MappedJdbcTypes(JdbcType.BINARY)
public class UuidBinaryTypeHandler extends BaseTypeHandler<UUID> {
  @Override
  public void setNonNullParameter(PreparedStatement statement, int index, UUID value, JdbcType type)
      throws SQLException {
    statement.setBytes(index, toBytes(value));
  }

  @Override
  public UUID getNullableResult(ResultSet resultSet, String column) throws SQLException {
    return fromBytes(resultSet.getBytes(column));
  }

  @Override
  public UUID getNullableResult(ResultSet resultSet, int column) throws SQLException {
    return fromBytes(resultSet.getBytes(column));
  }

  @Override
  public UUID getNullableResult(CallableStatement statement, int column) throws SQLException {
    return fromBytes(statement.getBytes(column));
  }

  private static byte[] toBytes(UUID value) {
    return ByteBuffer.allocate(16)
        .putLong(value.getMostSignificantBits())
        .putLong(value.getLeastSignificantBits())
        .array();
  }

  private static UUID fromBytes(byte[] value) {
    if (value == null || value.length != 16) return null;
    ByteBuffer buffer = ByteBuffer.wrap(value);
    return new UUID(buffer.getLong(), buffer.getLong());
  }
}
