package com.voxio.common.utils;

/**
 * 操作者标识上下文（阶段10.5 P0-3）
 *
 * 若依的审计切面靠 {@code SecurityUtils.getLoginUser()} 取操作人，客服端走独立令牌 + 拦截器、
 * 不进入若依权限体系，因此切面里那次取用户会直接抛异常，整条审计记录被 catch 丢掉——
 * 表现为「客服端所有写操作都没有留痕」。这里放一个 common 层的兜底标识，
 * 由客服端拦截器写入、审计切面在取不到若依用户时读取，避免 framework 反向依赖 kf 模块。
 *
 * @author voxio
 */
public class OperatorContext
{
    private static final ThreadLocal<String> OPERATOR = new ThreadLocal<>();

    public static void set(String operator)
    {
        OPERATOR.set(operator);
    }

    /**
     * 取当前操作人标识，取不到返回 null（不抛异常，审计不能因为标识缺失而整条丢失）
     */
    public static String get()
    {
        return OPERATOR.get();
    }

    public static void clear()
    {
        OPERATOR.remove();
    }
}
