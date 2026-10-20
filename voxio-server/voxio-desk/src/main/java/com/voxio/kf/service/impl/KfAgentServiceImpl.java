package com.voxio.kf.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.voxio.common.exception.ServiceException;
import com.voxio.common.utils.DateUtils;
import com.voxio.common.utils.StringUtils;
import com.voxio.kf.domain.KfAgent;
import com.voxio.kf.mapper.KfAgentMapper;
import com.voxio.kf.service.IKfAgentService;
import com.voxio.kf.service.KfPasswordPolicy;
import com.voxio.kf.util.Sm3PasswordEncoder;
import com.voxio.kf.service.IKfAgentTokenService;

import lombok.RequiredArgsConstructor;

/**
 * 客服账号 服务实现
 * 
 * @author voxio
 */
@Service
@RequiredArgsConstructor
public class KfAgentServiceImpl extends ServiceImpl<KfAgentMapper, KfAgent> implements IKfAgentService
{
    private final Sm3PasswordEncoder passwordEncoder;

    private final IKfAgentTokenService agentTokenService;

    private final KfPasswordPolicy passwordPolicy;

    @Override
    public List<KfAgent> selectAgentList(KfAgent agent)
    {
        LambdaQueryWrapper<KfAgent> wrapper = new LambdaQueryWrapper<KfAgent>()
                .like(StringUtils.isNotEmpty(agent.getAgentNo()), KfAgent::getAgentNo, agent.getAgentNo())
                .like(StringUtils.isNotEmpty(agent.getNickName()), KfAgent::getNickName, agent.getNickName())
                .eq(StringUtils.isNotEmpty(agent.getStatus()), KfAgent::getStatus, agent.getStatus())
                .eq(StringUtils.isNotEmpty(agent.getOnlineStatus()), KfAgent::getOnlineStatus, agent.getOnlineStatus())
                .orderByDesc(KfAgent::getCreateTime);
        return list(wrapper);
    }

    @Override
    public List<KfAgent> selectAgentListByPhoneLike(KfAgent agent)
    {
        // 手机号密文落库后 SQL 的 LIKE 打不到（密文比较无意义），改为取回后解密再内存过滤：
        // 单商户规模下客服数量有限，这条路径只影响「按手机号筛客服」这一个查询框
        String keyword = StringUtils.nvl(agent.getPhone(), "").trim();
        agent.setPhone(null);
        List<KfAgent> matched = new java.util.ArrayList<>();
        for (KfAgent item : selectAgentList(agent))
        {
            if (StringUtils.isNotEmpty(item.getPhone()) && item.getPhone().contains(keyword))
            {
                matched.add(item);
            }
        }
        return matched;
    }

    @Override
    public KfAgent selectByAgentNo(String agentNo)
    {
        return getOne(new LambdaQueryWrapper<KfAgent>().eq(KfAgent::getAgentNo, agentNo), false);
    }

    @Override
    public int insertAgent(KfAgent agent)
    {
        checkAgentNoUnique(agent);
        // 阶段10.4：口令复杂度（长度 + 字符类别数）统一由 KfPasswordPolicy 判，阈值在库里可调
        passwordPolicy.validate(agent.getPassword());
        agent.setPassword(passwordEncoder.encode(agent.getPassword()));
        agent.setPasswordAlgo(Sm3PasswordEncoder.ALGO_SM3);
        agent.setAgentId(null);
        agent.setOnlineStatus(STATUS_OFFLINE);
        agent.setCurrentLoad(0);
        agent.setCreateBy(currentUser());
        agent.setCreateTime(DateUtils.getNowDate());
        if (StringUtils.isNull(agent.getAcceptMax()))
        {
            agent.setAcceptMax(5);
        }
        if (StringUtils.isEmpty(agent.getStatus()))
        {
            agent.setStatus("0");
        }
        return save(agent) ? 1 : 0;
    }

    @Override
    public int updateAgent(KfAgent agent)
    {
        KfAgent old = getById(agent.getAgentId());
        if (StringUtils.isNull(old))
        {
            throw new ServiceException("客服账号不存在");
        }
        agent.setAgentNo(old.getAgentNo());
        agent.setPassword(null);
        agent.setOnlineStatus(null);
        agent.setCurrentLoad(null);
        agent.setUpdateBy(currentUser());
        agent.setUpdateTime(DateUtils.getNowDate());
        return updateById(agent) ? 1 : 0;
    }

    @Override
    @Transactional
    public int deleteAgentByIds(Long[] agentIds)
    {
        int rows = 0;
        for (Long agentId : agentIds)
        {
            KfAgent agent = getById(agentId);
            if (StringUtils.isNull(agent))
            {
                continue;
            }
            if (agent.getCurrentLoad() != null && agent.getCurrentLoad() > 0)
            {
                throw new ServiceException(StringUtils.format("客服【{}】仍有接待中的会话，请先结束会话", agent.getNickName()));
            }
            agentTokenService.delLoginUserByAgentId(agentId);
            rows += baseMapper.deleteAgentById(agentId);
        }
        return rows;
    }

    @Override
    public int resetPwd(Long agentId, String password)
    {
        passwordPolicy.validate(password);
        KfAgent agent = getById(agentId);
        if (StringUtils.isNull(agent))
        {
            throw new ServiceException("客服账号不存在");
        }
        KfAgent update = new KfAgent();
        update.setAgentId(agentId);
        update.setPassword(passwordEncoder.encode(password));
        update.setPasswordAlgo(Sm3PasswordEncoder.ALGO_SM3);
        update.setUpdateBy(currentUser());
        update.setUpdateTime(DateUtils.getNowDate());
        boolean ok = updateById(update);
        if (ok)
        {
            agentTokenService.delLoginUserByAgentId(agentId);
        }
        return ok ? 1 : 0;
    }

    @Override
    public int upgradePassword(Long agentId, String passwordHash, String passwordAlgo)
    {
        KfAgent update = new KfAgent();
        update.setAgentId(agentId);
        update.setPassword(passwordHash);
        update.setPasswordAlgo(passwordAlgo);
        update.setUpdateTime(DateUtils.getNowDate());
        return updateById(update) ? 1 : 0;
    }

    @Override
    public int changeStatus(KfAgent agent)
    {
        KfAgent old = getById(agent.getAgentId());
        if (StringUtils.isNull(old))
        {
            throw new ServiceException("客服账号不存在");
        }
        KfAgent update = new KfAgent();
        update.setAgentId(agent.getAgentId());
        update.setStatus(agent.getStatus());
        update.setUpdateBy(currentUser());
        update.setUpdateTime(DateUtils.getNowDate());
        boolean ok = updateById(update);
        if (ok && "1".equals(agent.getStatus()))
        {
            forceOffline(agent.getAgentId());
        }
        return ok ? 1 : 0;
    }

    @Override
    public int forceOffline(Long agentId)
    {
        KfAgent agent = getById(agentId);
        if (StringUtils.isNull(agent))
        {
            throw new ServiceException("客服账号不存在");
        }
        KfAgent update = new KfAgent();
        update.setAgentId(agentId);
        update.setOnlineStatus(STATUS_OFFLINE);
        update.setUpdateTime(DateUtils.getNowDate());
        boolean ok = updateById(update);
        agentTokenService.delLoginUserByAgentId(agentId);
        return ok ? 1 : 0;
    }

    @Override
    public void recordLoginInfo(Long agentId, String ip)
    {
        KfAgent update = new KfAgent();
        update.setAgentId(agentId);
        update.setLoginIp(ip);
        update.setLoginDate(DateUtils.getNowDate());
        updateById(update);
    }

    /**
     * 登录账号唯一性校验（已逻辑删除的记录不会命中，删除时已释放账号名）
     */
    private void checkAgentNoUnique(KfAgent agent)
    {
        if (StringUtils.isEmpty(agent.getAgentNo()) || agent.getAgentNo().length() < 2 || agent.getAgentNo().length() > 30)
        {
            throw new ServiceException("客服登录账号长度必须在2到30个字符之间");
        }
        long count = count(new LambdaQueryWrapper<KfAgent>()
                .eq(KfAgent::getAgentNo, agent.getAgentNo())
                .ne(StringUtils.isNotNull(agent.getAgentId()), KfAgent::getAgentId, agent.getAgentId()));
        if (count > 0)
        {
            throw new ServiceException(StringUtils.format("客服登录账号【{}】已存在", agent.getAgentNo()));
        }
    }

    private String currentUser()
    {
        return com.voxio.common.utils.SecurityUtils.getUsername();
    }
}
