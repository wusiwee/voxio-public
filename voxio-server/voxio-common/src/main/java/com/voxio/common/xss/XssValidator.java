package com.voxio.common.xss;

import com.voxio.common.utils.StringUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义xss校验注解实现
 * 
 * @author voxio
 */
public class XssValidator implements ConstraintValidator<Xss, String>
{
    private static final String HTML_PATTERN = "<(\\S*?)[^>]*>.*?|<.*? />";

    /**
     * 预编译：这个校验器每个带 @Xss 的请求字段都会走一遍，
     * 原先在 containsHtml 里 compile 等于每次请求都重新解析一遍正则。
     */
    private static final Pattern HTML = Pattern.compile(HTML_PATTERN);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext)
    {
        if (StringUtils.isBlank(value))
        {
            return true;
        }
        return !containsHtml(value);
    }

    public static boolean containsHtml(String value)
    {
        StringBuilder sHtml = new StringBuilder();
        Matcher matcher = HTML.matcher(value);
        while (matcher.find())
        {
            sHtml.append(matcher.group());
        }
        return HTML.matcher(sHtml).matches();
    }
}