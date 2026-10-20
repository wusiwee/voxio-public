package com.voxio.kf.util;

import com.voxio.common.utils.StringUtils;

/**
 * 管理后台查询参数处理：日期区间补齐时分秒，避免只传 yyyy-MM-dd 时漏掉当天数据
 * 
 * @author voxio
 */
public final class KfQuerySupport
{
    private KfQuerySupport()
    {
    }

    /** 起始时间：纯日期补 00:00:00 */
    public static String begin(String value)
    {
        return fill(value, " 00:00:00");
    }

    /** 结束时间：纯日期补 23:59:59 */
    public static String end(String value)
    {
        return fill(value, " 23:59:59");
    }

    private static String fill(String value, String suffix)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        String text = value.trim();
        return text.length() == 10 ? text + suffix : text;
    }
}
