package com.voxio.kf.service;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.voxio.common.core.redis.RedisCache;
import com.voxio.common.exception.ServiceException;
import com.voxio.common.utils.ServletUtils;
import com.voxio.common.utils.StringUtils;
import com.voxio.common.utils.ip.IpUtils;
import com.voxio.kf.domain.KfAgent;
import com.voxio.kf.domain.KfAgentLoginLog;
import com.voxio.kf.domain.KfAgentLoginUser;
import com.voxio.kf.util.Sm3PasswordEncoder;

import lombok.RequiredArgsConstructor;

/**
 * 客服登录校验
 * 
 * @author voxio
 */
@Service
@RequiredArgsConstructor
public class KfAgentLoginService
{
    /** 密码错误计数缓存前缀 */
    private static final String PWD_ERR_CNT_KEY = "kf_ag_pwd_err:";

    /** 连续失败锁定阈值缺省值，实际取 kf_security_config.login.lock.max-retry */
    private static final int DEFAULT_MAX_RETRY_COUNT = 5;

    /** 锁定时长（分钟）缺省值，实际取 kf_security_config.login.lock.minutes */
    private static final int DEFAULT_LOCK_MINUTES = 30;

    private final IKfAgentService agentService;

    private final IKfAgentTokenService tokenService;

    private final Sm3PasswordEncoder passwordEncoder;

    private final RedisCache redisCache;

    private final IKfSecurityService securityService;

    private final IKfAgentLoginLogService loginLogService;

    /**
     * 客服账号登录
     * 
     * 阶段10.4 起本端不再有图形验证码，爆破全靠这里的锁定，因此阈值从写死改成读库内参数
     * （改完即时生效，不用重启），且成功与失败都留一条 kf_agent_login_log。
     *
     * @param agentNo 登录账号
     * @param password 明文密码
     * @return 客服令牌
     */
    public String login(String agentNo, String password)
    {
        if (StringUtils.isEmpty(agentNo) || StringUtils.isEmpty(password))
        {
            throw new ServiceException("客服账号和密码不能为空");
        }
        int maxRetry = readInt("login.lock.max-retry", DEFAULT_MAX_RETRY_COUNT);
        int lockMinutes = readInt("login.lock.minutes", DEFAULT_LOCK_MINUTES);
        String ip = IpUtils.getIpAddr();
        String errKey = PWD_ERR_CNT_KEY + ip + ":" + agentNo;
        Integer errCount = redisCache.getCacheObject(errKey);
        if (StringUtils.isNotNull(errCount) && errCount >= maxRetry)
        {
            recordLogin(agentNo, null, KfAgentLoginLog.STATUS_FAIL,
                    StringUtils.format("密码连续输错{}次，锁定{}分钟", errCount, lockMinutes), ip);
            throw new ServiceException(StringUtils.format("密码连续输错{}次，请{}分钟后再试", maxRetry, lockMinutes));
        }

        KfAgent agent = agentService.selectByAgentNo(agentNo);
        if (StringUtils.isNull(agent) || !passwordEncoder.matches(password, agent.getPassword()))
        {
            redisCache.setCacheObject(errKey, (errCount == null ? 0 : errCount) + 1, lockMinutes, TimeUnit.MINUTES);
            recordLogin(agentNo, agent == null ? null : agent.getAgentId(), KfAgentLoginLog.STATUS_FAIL,
                    StringUtils.isNull(agent) ? "客服账号不存在" : "账号或密码错误", ip);
            throw new ServiceException("客服账号或密码错误");
        }
        if ("1".equals(agent.getStatus()))
        {
            recordLogin(agentNo, agent.getAgentId(), KfAgentLoginLog.STATUS_FAIL, "客服账号已停用", ip);
            throw new ServiceException("客服账号已停用，请联系管理员");
        }

        redisCache.deleteObject(errKey);
        // 存量 BCrypt 账号在口令校验通过后就地升级为国密哈希：不批量重置他人口令，
        // 只要求「用过一次密码」的记录换成 SM3，密码列里永远不会出现明文
        if (passwordEncoder.needsUpgrade(agent.getPassword()))
        {
            agentService.upgradePassword(agent.getAgentId(), passwordEncoder.encode(password),
                    Sm3PasswordEncoder.ALGO_SM3);
        }
        agentService.recordLoginInfo(agent.getAgentId(), ip);
        recordLogin(agentNo, agent.getAgentId(), KfAgentLoginLog.STATUS_SUCCESS, "登录成功", ip);

        KfAgentLoginUser loginUser = new KfAgentLoginUser();
        loginUser.setAgentId(agent.getAgentId());
        loginUser.setAgentNo(agent.getAgentNo());
        loginUser.setNickName(agent.getNickName());
        loginUser.setAvatar(agent.getAvatar());
        loginUser.setOnlineStatus(agent.getOnlineStatus());
        loginUser.setAcceptMax(agent.getAcceptMax());
        loginUser.setIpaddr(ip);
        // 同一客服重复登录时，旧令牌立即失效
        tokenService.delLoginUserByAgentId(agent.getAgentId());
        return tokenService.createToken(loginUser);
    }

    /**
     * 读一个整数型安全策略参数，库里写脏了（非数字）就退回缺省值，不能因此把登录拦死
     */
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

    private void recordLogin(String agentNo, Long agentId, String status, String msg, String ip)
    {
        loginLogService.record(agentNo, agentId, status, msg, ip,
                ServletUtils.getRequest().getHeader("User-Agent"));
    }

    /**
     * 客服登出
     * 
     * @param loginUser 当前客服登录信息
     */
    public void logout(KfAgentLoginUser loginUser)
    {
        tokenService.delLoginUser(loginUser.getToken());
        tokenService.delLoginUserByAgentId(loginUser.getAgentId());
    }
}
