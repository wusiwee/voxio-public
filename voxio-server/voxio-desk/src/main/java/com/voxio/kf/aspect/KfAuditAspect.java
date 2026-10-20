package com.voxio.kf.aspect;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.CodeSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.voxio.common.annotation.Log;
import com.voxio.common.core.domain.model.LoginUser;
import com.voxio.common.utils.ExceptionUtil;
import com.voxio.common.utils.SecurityUtils;
import com.voxio.common.utils.ServletUtils;
import com.voxio.common.utils.StringUtils;
import com.voxio.common.utils.ip.IpUtils;
import com.voxio.kf.domain.KfAgentLoginUser;
import com.voxio.kf.domain.KfAuditLog;
import com.voxio.kf.security.KfAgentContext;
import com.voxio.kf.service.IKfAuditLogService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 业务审计留痕切面（阶段10.5 P0-3）
 *
 * 复用控制器上已有的 {@code @Log} 注解作为「这是一次写操作」的唯一判据，
 * 不再另设一套注解——两套注解迟早会出现「加了那个忘了这个」的覆盖缺口。
 * 只作用于 com.voxio.kf.controller 包，若依自身的平台日志仍走 SysOperLogServiceImpl。
 *
 * 三个刻意的取舍：
 * 1. 个人信息与文件内容不进 request_params（审计表不该成为明文二次扩散点）；
 * 2. 留痕失败只告警，绝不影响业务返回（可用性优先，但本地日志一定留线）；
 * 3. 业务抛异常同样留痕，result 记 fail:原因——失败的操作往往比成功的更需要追溯。
 *
 * @author voxio
 */
@Aspect
@Component
@RequiredArgsConstructor
public class KfAuditAspect
{
    private static final Logger log = LoggerFactory.getLogger("kf.security");

    /** 不进审计参数列的顶层参数名（方法签名级别） */
    private static final Set<String> EXCLUDED_PARAMS = Set.of("password", "phone", "email", "contact",
            "content", "comment", "answer", "file", "multipart", "request", "response",
            "httpServletRequest", "httpResponse");

    /**
     * 需要打码的字段名：请求体往往是整个实体（如 @RequestBody KfTicket），
     * 只看方法参数名会漏掉嵌套在实体里的手机号与正文，所以序列化后按 JSON 键逐个打码。
     * 用「解析 + 递归」而不是正则替换：正则改 JSON 一旦遇到转义引号就会漏网或改坏结构。
     */
    private static final Set<String> REDACT_FIELDS = Set.of("password", "phone", "email", "contact",
            "contactphone", "contactemail", "content", "comment", "answer", "question", "keywords",
            "mediaurl", "accessurl", "sm4keycipher", "newpassword", "oldpassword", "note", "remark");

    private final IKfAuditLogService auditLogService;

    @Around(value = "@annotation(controllerLog) && within(com.voxio.kf.controller..*)",
            argNames = "joinPoint,controllerLog")
    public Object around(ProceedingJoinPoint joinPoint, Log controllerLog) throws Throwable
    {
        Object returnValue = null;
        Throwable error = null;
        try
        {
            returnValue = joinPoint.proceed();
            return returnValue;
        }
        catch (Throwable e)
        {
            error = e;
            throw e;
        }
        finally
        {
            try
            {
                auditLogService.record(build(joinPoint, controllerLog, returnValue, error));
            }
            catch (Exception e)
            {
                log.warn("业务审计留痕构造失败：{}", e.getMessage());
            }
        }
    }

    private KfAuditLog build(ProceedingJoinPoint joinPoint, Log controllerLog, Object returnValue, Throwable error)
    {
        KfAuditLog row = new KfAuditLog();
        row.setOperation(controllerLog.title() + ":" + controllerLog.businessType().name());
        HttpServletRequest request = ServletUtils.getRequest();
        String uri = request == null ? "" : StringUtils.nvl(request.getRequestURI(), "");
        applyTarget(row, uri);
        row.setIp(IpUtils.getIpAddr());
        row.setUa(request == null ? "" : StringUtils.nvl(request.getHeader("User-Agent"), ""));
        row.setRequestParams(params(joinPoint));
        row.setCreateTime(new java.util.Date());
        applyOperator(row);
        if (error == null)
        {
            row.setResult(KfAuditLog.RESULT_SUCCESS + codeSuffix(returnValue));
        }
        else
        {
            row.setResult("fail:" + StringUtils.nvl(ExceptionUtil.getExceptionMessage(error), error.getClass().getSimpleName()));
        }
        return row;
    }

    /**
     * 操作人：客服端优先取独立登录上下文（kf_agent），其次才取若依登录用户。
     * 顺序不能反——客服端请求里若依上下文为空，而两端同时有值的场景不存在。
     */
    private void applyOperator(KfAuditLog row)
    {
        KfAgentLoginUser agent = KfAgentContext.get();
        if (agent != null)
        {
            row.setUserType(KfAuditLog.USER_AGENT);
            row.setUserId(agent.getAgentId());
            return;
        }
        try
        {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            if (loginUser != null)
            {
                row.setUserType(KfAuditLog.USER_ADMIN);
                row.setUserId(loginUser.getUserId());
            }
        }
        catch (Exception e)
        {
            row.setUserType("anonymous");
        }
    }

    /**
     * 对象类型取「第一个数字段之前的那一段」，对象标识取第一个数字段：
     * /kf/agent-app/ticket → ticket；/kf/agent-app/ticket/1463/status → ticket + 1463；
     * /kf/monitor/session/11402/assign/100 → session + 11402。
     * 这样新建（无 id 在路径上）与改删（id 在路径上）都能落到同一个对象类型上。
     */
    private void applyTarget(KfAuditLog row, String uri)
    {
        String path = uri.startsWith("/customer") ? uri.substring("/customer".length()) : uri;
        String[] parts = path.split("/");
        int numeric = -1;
        for (int i = 0; i < parts.length; i++)
        {
            if (StringUtils.isNotEmpty(parts[i]) && Character.isDigit(parts[i].charAt(0)))
            {
                numeric = i;
                break;
            }
        }
        if (numeric > 0)
        {
            row.setTargetId(parts[numeric]);
            row.setTargetType(parts[numeric - 1]);
            return;
        }
        for (int i = parts.length - 1; i >= 0; i--)
        {
            if (StringUtils.isNotEmpty(parts[i]) && !parts[i].startsWith("."))
            {
                row.setTargetType(parts[i]);
                return;
            }
        }
    }

    private String params(ProceedingJoinPoint joinPoint)
    {
        try
        {
            String[] names = ((CodeSignature) joinPoint.getSignature()).getParameterNames();
            Object[] values = joinPoint.getArgs();
            Map<String, Object> kept = new LinkedHashMap<>();
            for (int i = 0; i < values.length; i++)
            {
                Object value = values[i];
                String name = i < names.length ? names[i] : ("arg" + i);
                if (value == null || value instanceof MultipartFile || value instanceof HttpServletRequest
                        || value instanceof HttpServletResponse || KfAgentLoginUser.class.isInstance(value))
                {
                    continue;
                }
                if (EXCLUDED_PARAMS.contains(name.toLowerCase()))
                {
                    kept.put(name, "***");
                    continue;
                }
                kept.put(name, value);
            }
            if (kept.isEmpty())
            {
                return "";
            }
            Object tree = JSON.parse(JSON.toJSONString(kept));
            redact(tree);
            return JSON.toJSONString(tree);
        }
        catch (Exception e)
        {
            return "";
        }
    }

    /** 递归打码：命中敏感键直接替换值，其余继续下钻，数组同样处理 */
    private void redact(Object node)
    {
        if (node instanceof JSONObject object)
        {
            for (String key : object.keySet())
            {
                if (REDACT_FIELDS.contains(key.toLowerCase()))
                {
                    object.put(key, "***");
                }
                else
                {
                    redact(object.get(key));
                }
            }
            return;
        }
        if (node instanceof JSONArray array)
        {
            for (Object item : array)
            {
                redact(item);
            }
        }
    }

    /** 若依风格返回体里带上业务码，便于按 result 直接筛出「HTTP 200 但业务失败」的操作 */
    private String codeSuffix(Object returnValue)
    {
        if (returnValue instanceof com.voxio.common.core.domain.AjaxResult result)
        {
            Object code = result.get("code");
            return code == null ? "" : ":code=" + code;
        }
        return "";
    }
}
