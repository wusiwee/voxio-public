package com.voxio.kf.service;

import org.springframework.stereotype.Component;

import com.voxio.common.exception.ServiceException;
import com.voxio.common.utils.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 客服口令复杂度策略（阶段10.4）
 *
 * 阈值来自 kf_security_config：password.policy.min-length（默认 8）与
 * password.policy.min-categories（默认 3，四类＝大写、小写、数字、符号）。
 * 只在新建客服与重置密码时校验：存量弱口令不批量改他人口令，等下次自然改密时收敛，
 * 与阶段10.3 的「BCrypt 渐进升级 SM3」保持同一套处置哲学。
 *
 * @author voxio
 */
@Component
@RequiredArgsConstructor
public class KfPasswordPolicy
{
    /** 最小长度缺省值 */
    private static final int DEFAULT_MIN_LENGTH = 8;

    /** 需覆盖的字符类别数缺省值 */
    private static final int DEFAULT_MIN_CATEGORIES = 3;

    private static final String[] CATEGORY_NAMES = { "大写字母", "小写字母", "数字", "符号" };

    private final IKfSecurityService securityService;

    /**
     * 校验口令，不合规直接抛业务异常（错误信息里写清缺哪几类，避免客服反复试）
     *
     * @param password 明文口令
     */
    public void validate(String password)
    {
        int minLength = readInt("password.policy.min-length", DEFAULT_MIN_LENGTH);
        int minCategories = readInt("password.policy.min-categories", DEFAULT_MIN_CATEGORIES);
        if (StringUtils.isEmpty(password))
        {
            throw new ServiceException("客服密码不能为空");
        }
        if (password.length() < minLength)
        {
            throw new ServiceException(StringUtils.format("客服密码不能少于{}位", minLength));
        }
        int hit = matchedCategories(password);
        if (hit < minCategories)
        {
            throw new ServiceException(StringUtils.format("客服密码需包含{}中的至少{}类（当前只含 {} 类）",
                    String.join("、", CATEGORY_NAMES), minCategories, hit));
        }
    }

    /**
     * 命中的字符类别数：大写 / 小写 / 数字 / 符号（非字母数字即算符号）
     */
    private int matchedCategories(String password)
    {
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean symbol = false;
        for (char c : password.toCharArray())
        {
            if (c >= 'A' && c <= 'Z')
            {
                upper = true;
            }
            else if (c >= 'a' && c <= 'z')
            {
                lower = true;
            }
            else if (c >= '0' && c <= '9')
            {
                digit = true;
            }
            else
            {
                symbol = true;
            }
        }
        int hit = 0;
        if (upper)
        {
            hit++;
        }
        if (lower)
        {
            hit++;
        }
        if (digit)
        {
            hit++;
        }
        if (symbol)
        {
            hit++;
        }
        return hit;
    }

    private int readInt(String key, int defaultValue)
    {
        try
        {
            return Integer.parseInt(securityService.getConfig(key, String.valueOf(defaultValue)).trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }
}
