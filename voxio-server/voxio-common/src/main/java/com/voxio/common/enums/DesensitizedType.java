package com.voxio.common.enums;

import java.util.function.Function;
import java.util.regex.Pattern;
import com.voxio.common.utils.DesensitizedUtil;

/**
 * 脱敏类型
 *
 * @author voxio
 */
public enum DesensitizedType
{
    /**
     * 姓名，第2位星号替换
     */
    USERNAME(s -> Patterns.USERNAME.matcher(s).replaceAll("$1*$2")),

    /**
     * 密码，全部字符都用*代替
     */
    PASSWORD(DesensitizedUtil::password),

    /**
     * 身份证，中间10位星号替换
     */
    ID_CARD(s -> Patterns.ID_CARD.matcher(s).replaceAll("$1** **** ****$2")),

    /**
     * 手机号，中间4位星号替换
     */
    PHONE(s -> Patterns.PHONE.matcher(s).replaceAll("$1****$2")),

    /**
     * 电子邮箱，仅显示第一个字母和@后面的地址显示，其他星号替换
     */
    EMAIL(s -> Patterns.EMAIL.matcher(s).replaceAll("$1****$2")),

    /**
     * 银行卡号，保留最后4位，其他星号替换
     */
    BANK_CARD(s -> Patterns.BANK_CARD.matcher(s).replaceAll("**** **** **** **** $1")),

    /**
     * 车牌号码，包含普通车辆、新能源车辆
     */
    CAR_LICENSE(DesensitizedUtil::carLicense);

    /**
     * 脱敏正则预编译：原本写在 {@code String.replaceAll} 里的字面量，每脱敏一个字段就要重编一次，
     * 而脱敏挂在日志与列表序列化的公共路径上。放在嵌套类里是因为枚举常量先于本类的静态字段初始化，
     * 直接引用会撞编译期的「非法前向引用」。
     */
    private static class Patterns
    {
        static final Pattern USERNAME = Pattern.compile("(\\S)\\S(\\S*)");

        static final Pattern ID_CARD = Pattern.compile("(\\d{4})\\d{10}(\\d{3}[Xx]|\\d{4})");

        static final Pattern PHONE = Pattern.compile("(\\d{3})\\d{4}(\\d{4})");

        static final Pattern EMAIL = Pattern.compile("(^.)[^@]*(@.*$)");

        static final Pattern BANK_CARD = Pattern.compile("\\d{15}(\\d{3})");
    }

    private final Function<String, String> desensitizer;

    DesensitizedType(Function<String, String> desensitizer)
    {
        this.desensitizer = desensitizer;
    }

    public Function<String, String> desensitizer()
    {
        return desensitizer;
    }
}
