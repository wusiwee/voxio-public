package com.voxio.common.utils.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * 帧完整性（SM3-HMAC）：连接级密钥由鉴权令牌派生，摘要覆盖帧头字段与规范化负载
 *
 * 规范化串（三端必须逐字符一致，由固定向量断言守住）：
 * <pre>
 * cmd LF seq LF sid LF ts LF type LF mid LF encrypt LF algo LF keyVersion LF canon(body)
 * canon(body) = 按键升序拼接 k=v，用 & 连接；值为 null 的键两端都跳过
 * 转义只做 %25 / %26 / %3D / %0A 四条，避免同一份负载在 Java / sm-crypto / BouncyCastle 里编出两种结果
 * </pre>
 */
public final class SmFrameMac
{
    /** HKDF 盐明文，三端各自 utf8 转 hex */
    public static final String CONTROL_SALT_TEXT = "voxio-desk-control-v1";

    /** HKDF 上下文标签 */
    public static final String CONTROL_INFO = "kf-control-hmac";

    private static final String LF = String.valueOf((char) 10);

    private SmFrameMac()
    {
    }

    /** 连接级完整性密钥：HKDF-SM3(ikm=SM3(token), salt=CONTROL_SALT_TEXT, info=CONTROL_INFO, 16 字节) */
    public static String controlKeyHex(String token)
    {
        if (token == null || token.isEmpty())
        {
            return null;
        }
        return Sm3Hkdf.deriveHex(Sm3Utils.hashHex(token.getBytes(StandardCharsets.UTF_8)),
                SmCodec.hex(CONTROL_SALT_TEXT.getBytes(StandardCharsets.UTF_8)), CONTROL_INFO, 16);
    }

    public static String canonical(String cmd, Long seq, Long sid, Long ts, String type, String mid,
            String encrypt, String algo, Integer keyVersion, Map<String, Object> body)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(nz(cmd)).append(LF).append(nz(seq)).append(LF).append(nz(sid)).append(LF).append(nz(ts))
                .append(LF).append(nz(type)).append(LF).append(nz(mid)).append(LF).append(nz(encrypt))
                .append(LF).append(nz(algo)).append(LF).append(nz(keyVersion)).append(LF)
                .append(canonicalBody(body));
        return sb.toString();
    }

    public static String sign(String hexKey, String canonical)
    {
        return Sm3Utils.hmacHex(hexKey, canonical.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean verify(String hexKey, String canonical, String hmac)
    {
        return hexKey != null && hmac != null
                && Sm3Utils.constantTimeEquals(sign(hexKey, canonical), hmac.toLowerCase());
    }

    private static String canonicalBody(Map<String, Object> body)
    {
        if (body == null || body.isEmpty())
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : new TreeMap<>(body).entrySet())
        {
            // 值为 null 的键两端都不参与摘要：fastjson2 序列化时本就会省略它们
            if (entry.getKey() == null || entry.getValue() == null)
            {
                continue;
            }
            if (sb.length() > 0)
            {
                sb.append('&');
            }
            sb.append(escape(entry.getKey())).append('=').append(escape(value(entry.getValue())));
        }
        return sb.toString();
    }

    /** 数值统一按十进制整数写，避免 3 与 3.0 在不同语言里算出两个摘要 */
    private static String value(Object raw)
    {
        if (raw instanceof Boolean)
        {
            return raw.toString();
        }
        if (raw instanceof Number)
        {
            double d = ((Number) raw).doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 9.007199254740992E15)
            {
                return String.valueOf(((Number) raw).longValue());
            }
            return String.valueOf(raw);
        }
        return String.valueOf(raw);
    }

    private static String escape(String text)
    {
        return text.replace("%", "%25").replace("&", "%26").replace("=", "%3D").replace(LF, "%0A");
    }

    private static String nz(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }
}
