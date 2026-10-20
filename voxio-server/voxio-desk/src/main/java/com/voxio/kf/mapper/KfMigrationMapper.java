package com.voxio.kf.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 存量迁移专用数据层（阶段10.6 批次2）
 *
 * 这里刻意绕开实体映射：迁移要看到「库里到底存了什么」（密文还是明文），而实体的
 * {@code SmFieldCipherTypeHandler} 在读取时就把密文解回明文了，用它取数会看不清现状。
 *
 * 表名与列名用 ${} 拼接，但取值只能来自 LegacyDataMigration 里写死的白名单常量，
 * 外部参数（主键值、列值）一律 #{ 绑定，不接受任何来自接口或配置文件的标识符。
 *
 * @author voxio
 */
public interface KfMigrationMapper
{
    /** 列值现状：密文行数（带 sm4-gcm$ 前缀） */
    @Select("select count(*) from ${table} where ${column} like 'sm4-gcm$%'")
    int countCipher(@Param("table") String table, @Param("column") String column);

    /** 列值现状：仍是存量的非空明文行数 */
    @Select("select count(*) from ${table} where ${column} is not null and ${column} <> '' "
            + "and ${column} not like 'sm4-gcm$%'")
    int countPlain(@Param("table") String table, @Param("column") String column);

    /** 列值现状：非空值行数（密文 + 明文），迁移前后必须相等 */
    @Select("select count(*) from ${table} where ${column} is not null and ${column} <> ''")
    int countFilled(@Param("table") String table, @Param("column") String column);

    /** 整表行数，用来证明迁移没增删行 */
    @Select("select count(*) from ${table}")
    int countRows(@Param("table") String table);

    /**
     * 取待备份与待迁移的行。extra 是迁移需要但不在目标列上的辅助列（kf_message 要 session_id 才能按会话子钥加密），
     * 没有辅助列的表由白名单写死成字面量 null，避免注解里再嵌一层动态 SQL。
     */
    @Select("select ${pk} pk_value, ${column} col_value, ${extra} extra_value from ${table} order by ${pk}")
    List<Map<String, Object>> selectAllRows(@Param("table") String table, @Param("pk") String pk,
            @Param("column") String column, @Param("extra") String extra);

    /** 写入列值原样落库（密文就是密文、明文就是明文），迁移与回滚共用 */
    @Update("update ${table} set ${column} = #{value} where ${pk} = #{pkValue}")
    int updateValue(@Param("table") String table, @Param("pk") String pk, @Param("column") String column,
            @Param("pkValue") Object pkValue, @Param("value") String value);

    /**
     * 迁移 kf_message 时必须连带对齐标记列
     *
     * 读路径（KfMessageServiceImpl.decryptInPlace）是按 encrypt_flag='1' 决定要不要解密的，
     * 只改 content 而留着 encrypt_flag='0'，客服端与访客端就会把密文原样显示出来。
     */
    @Update("update kf_message set content = #{value}, encrypt_flag = '1', algo_type = 'SM4-GCM', "
            + "key_version = #{keyVersion} where msg_id = #{msgId}")
    int updateMessageCipher(@Param("msgId") Object msgId, @Param("value") String value,
            @Param("keyVersion") Integer keyVersion);

    /** 把「库里已是密文但标记还是 0」的历史行对齐（异步落库等写路径遗留的形态），只动标记列 */
    @Update("update kf_message set encrypt_flag = '1', algo_type = 'SM4-GCM', key_version = #{keyVersion} "
            + "where msg_id = #{msgId}")
    int markMessageCipher(@Param("msgId") Object msgId, @Param("keyVersion") Integer keyVersion);

    /** 已是密文形态但标记没对齐的消息行（迁移与体检共用这一条判据；量级只有几十行，直接带上 content） */
    @Select("select msg_id pk_value, content col_value from kf_message where content like 'sm4-gcm$%' "
            + "and (encrypt_flag is null or encrypt_flag <> '1') order by msg_id")
    List<Map<String, Object>> selectMislabelledMessages();

    /** 回滚 kf_message 时把标记一并退回（恢复成迁移前那一刻库里到底是什么样） */
    @Update("update kf_message set content = #{value}, encrypt_flag = #{flag}, algo_type = #{algo}, "
            + "key_version = #{keyVersion} where msg_id = #{msgId}")
    int restoreMessageRow(@Param("msgId") Object msgId, @Param("value") String value, @Param("flag") String flag,
            @Param("algo") String algo, @Param("keyVersion") Integer keyVersion);

    /** 备份消息行时把标记列一起带走，回滚才能还原到原形态 */
    @Select("select msg_id pk_value, content col_value, session_id extra_value, encrypt_flag flag_value, "
            + "algo_type algo_value, key_version kv_value from kf_message order by msg_id")
    List<Map<String, Object>> selectMessageRows();

    /**
     * 未升级到 SM3 加盐口令的客服账号（批次2 交付的查询口径）
     *
     * 口径：只看在用账号，password_algo 不是 sm3-salt 的都算「未升级」。这些账号仍能登录（bcrypt 校验分支保留），
     * 改口令时才强制升级到 SM3 加盐——批量重置别人的口令不在授权范围内。
     */
    @Select("select agent_id, agent_no, coalesce(password_algo, '') password_algo, del_flag "
            + "from kf_agent where del_flag = '0' and coalesce(password_algo, '') <> 'sm3-salt' order by agent_id")
    List<Map<String, Object>> selectNotUpgradedAgents();
}
