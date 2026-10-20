package com.voxio.framework.captcha;

import java.security.SecureRandom;

import com.wf.captcha.ArithmeticCaptcha;

/**
 * 彩色算术验证码
 * <p>
 * easy-captcha 1.6.2 的 {@link ArithmeticCaptcha} 用 Nashorn 脚本引擎算结果，
 * JDK 17 已移除 Nashorn（engine 为 null 直接 NPE），故在此用纯 Java 生成表达式并计算，
 * 渲染仍交给父类（每个字符随机颜色）。
 *
 * @author voxio
 */
public class VoxioArithmeticCaptcha extends ArithmeticCaptcha
{
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final char[] OPERATORS = { '+', '-', 'x' };

    public VoxioArithmeticCaptcha(int width, int height, int len)
    {
        super(width, height, len);
    }

    /**
     * 生成表达式并写入父类的答案字段 {@code chars}，渲染文本为 "3x4=?"
     */
    @Override
    protected char[] alphas()
    {
        int count = Math.max(1, this.len);
        String expr;
        int result;
        do
        {
            int[] numbers = new int[count];
            char[] ops = new char[count - 1];
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++)
            {
                if (i > 0)
                {
                    ops[i - 1] = OPERATORS[RANDOM.nextInt(OPERATORS.length)];
                    sb.append(ops[i - 1]);
                }
                numbers[i] = 1 + RANDOM.nextInt(9);
                sb.append(numbers[i]);
            }
            expr = sb.toString();
            result = evaluate(numbers, ops);
        }
        // 避免出现负数答案，用户不必输入减号
        while (result < 0);

        this.chars = String.valueOf(result);
        setArithmeticString(expr + "=?");
        return this.chars.toCharArray();
    }

    /**
     * 按先乘后加减求值，与人工计算习惯一致
     */
    private static int evaluate(int[] numbers, char[] ops)
    {
        int sum = 0;
        int term = numbers[0];
        int sign = 1;
        for (int i = 0; i < ops.length; i++)
        {
            if (ops[i] == 'x')
            {
                term *= numbers[i + 1];
            }
            else
            {
                sum += sign * term;
                sign = ops[i] == '-' ? -1 : 1;
                term = numbers[i + 1];
            }
        }
        return sum + sign * term;
    }
}
