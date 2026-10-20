package com.voxio.kf.util;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.voxio.common.utils.crypto.SmCodec;
import com.voxio.common.utils.crypto.SmFieldCipher;
import com.voxio.kf.service.IKfSessionKeyService;

import lombok.RequiredArgsConstructor;

/**
 * 视图行的密文字段读取器
 *
 * 列表类接口用 Map 直接带出消息内容或联系方式（会话预览、聊天记录、会话详情、后台留言），
 * 库里落的是 SM4-GCM 密文，出网前统一在这里解回明文：解密只发生在服务端一次，前端与客服端都只看到明文。
 * 实体字段不经过这里——那些由 {@code SmFieldCipherTypeHandler} 在映射层加解密。
 *
 * @author voxio
 */
@Component
@RequiredArgsConstructor
public class KfCipherRowReader
{
    /** 密钥已失效（换过主密钥或会话密钥材料丢失）时的占位文案，避免整条记录读成空白 */
    public static final String UNDECRYPTABLE = "（密文无法解密：密钥已失效）";

    private final IKfSessionKeyService sessionKeyService;

    private final SmFieldCipher fieldCipher;


    /** 解密消息正文：按会话密钥（行内须带 sessionId） */
    public void decrypt(List<Map<String, Object>> rows, String field)
    {
        for (Map<String, Object> row : rows)
        {
            decrypt(row, field);
        }
    }

    public void decrypt(Map<String, Object> row, String field)
    {
        if (row == null)
        {
            return;
        }
        Object value = row.get(field);
        if (value instanceof String text && text.startsWith(SmCodec.STORE_PREFIX))
        {
            String plain = sessionKeyService.decryptStored(sessionId(row), text);
            row.put(field, plain == null ? UNDECRYPTABLE : plain);
        }
    }

    /** 解密业务字段（手机号/邮箱/联系方式）：按主密钥派生的字段子密钥，与行归属无关 */
    public void decryptFields(List<Map<String, Object>> rows, String... fields)
    {
        for (Map<String, Object> row : rows)
        {
            for (String field : fields)
            {
                Object value = row.get(field);
                if (value instanceof String text && SmCodec.isStoredCipher(text))
                {
                    String plain = fieldCipher.decrypt(text);
                    row.put(field, plain == null ? UNDECRYPTABLE : plain);
                }
            }
        }
    }

    private static Long sessionId(Map<String, Object> row)
    {
        Object id = row.get("sessionId");
        return id instanceof Number number ? number.longValue() : null;
    }
}
