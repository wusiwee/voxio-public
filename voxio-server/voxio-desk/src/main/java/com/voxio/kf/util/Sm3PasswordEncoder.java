package com.voxio.kf.util;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import com.voxio.common.utils.StringUtils;
import com.voxio.common.utils.crypto.Sm3Utils;
import com.voxio.common.utils.crypto.SmCodec;
import com.voxio.kf.service.IKfSecurityService;

import lombok.RequiredArgsConstructor;

/**
 * 客服账号密码哈希（阶段10.3，功能点23）
 *
 * 等保三级要求口令用国密算法保存：kf_agent 密码改为 {@code sm3$轮数$盐$摘要}，
 * 每账号独立随机盐 + SM3 迭代（把单次爆破的成本抬到可接受），列里存的既不是明文也不是可逆密文。
 *
 * 存量账号仍是 BCrypt，不批量重置别人密码：登录校验通过后就地升级成 SM3（渐进迁移），
 * {@code kf_agent.password_algo} 记录每条用的是哪种，管理员看不出「谁的口令还没换」。
 * 若依 sys_user 的口令逻辑保持原样，不在本次改造范围。
 *
 * @author voxio
 */
@Component
@RequiredArgsConstructor
public class Sm3PasswordEncoder
{
    /** 国密 SM3 加盐哈希 */
    public static final String ALGO_SM3 = "sm3-salt";

    /** 存量 BCrypt，等待登录时渐进升级 */
    public static final String ALGO_BCRYPT = "bcrypt";

    private static final String PREFIX = "sm3$";

    /** 默认迭代轮数，实际取 {@code kf_security_config.password.hash.rounds} */
    private static final int DEFAULT_ROUNDS = 10000;

    /** 盐长度（字节） */
    private static final int SALT_BYTES = 16;

    private final IKfSecurityService securityService;

    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder legacyEncoder;

    private final SecureRandom random = new SecureRandom();

    /** 生成 SM3 加盐迭代哈希 */
    public String encode(String raw)
    {
        int rounds = rounds();
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return PREFIX + rounds + "$" + SmCodec.hex(salt) + "$" + SmCodec.hex(digest(salt, raw, rounds));
    }

    /**
     * 校验口令
     *
     * @param raw 明文口令
     * @param stored 列内保存值
     * @return 是否匹配；非 SM3 形态的存量值按 BCrypt 校验
     */
    public boolean matches(String raw, String stored)
    {
        if (StringUtils.isEmpty(raw) || StringUtils.isEmpty(stored))
        {
            return false;
        }
        if (!stored.startsWith(PREFIX))
        {
            return legacyEncoder.matches(raw, stored);
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4)
        {
            return false;
        }
        int rounds;
        try
        {
            rounds = Integer.parseInt(parts[1]);
        }
        catch (NumberFormatException e)
        {
            return false;
        }
        byte[] salt = SmCodec.unhex(parts[2]);
        return SmCodec.hex(digest(salt, raw, rounds)).equals(parts[3]);
    }

    /** 这条记录是否还需要升级到国密哈希 */
    public boolean needsUpgrade(String stored)
    {
        return StringUtils.isNotEmpty(stored) && !stored.startsWith(PREFIX);
    }

    /** 轮数从安全策略表读；改轮数只影响新写入的哈希（已存的按自己记录的轮数校验） */
    public int rounds()
    {
        String configured = securityService.getConfig("password.hash.rounds", String.valueOf(DEFAULT_ROUNDS));
        try
        {
            int parsed = Integer.parseInt(configured.trim());
            return parsed >= 1000 ? parsed : DEFAULT_ROUNDS;
        }
        catch (Exception e)
        {
            return DEFAULT_ROUNDS;
        }
    }

    /** SM3(salt‖口令) 之后反复 SM3(摘要‖salt) */
    private static byte[] digest(byte[] salt, String raw, int rounds)
    {
        byte[] acc = Sm3Utils.hash(SmCodec.concat(salt, SmCodec.utf8(raw)));
        for (int i = 1; i < Math.max(rounds, 1); i++)
        {
            acc = Sm3Utils.hash(SmCodec.concat(acc, salt));
        }
        return acc;
    }
}
