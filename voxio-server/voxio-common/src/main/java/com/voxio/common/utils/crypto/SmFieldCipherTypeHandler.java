package com.voxio.common.utils.crypto;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * 业务字段密文类型处理器（阶段10.3）
 *
 * 实体字段标了它之后，写入自动加密、读取自动解密，业务代码看到的始终是明文：
 * 更新流程里「先读旧值再写回」不会被二次加密，也避免了在每个 service 里逐处手搓加解密而漏掉某个出口。
 *
 * 注意：它只影响实体映射。查询条件里的等值/模糊匹配仍是对密文列做比较，密文列不能作为 SQL 检索条件，
 * 需要按明文检索的字段请在服务层解密后内存过滤。
 *
 * @author voxio
 */
public class SmFieldCipherTypeHandler extends BaseTypeHandler<String>
{
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException
    {
        ps.setString(i, SmFieldCipher.get().encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException
    {
        return SmFieldCipher.get().decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException
    {
        return SmFieldCipher.get().decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException
    {
        return SmFieldCipher.get().decrypt(cs.getString(columnIndex));
    }
}
