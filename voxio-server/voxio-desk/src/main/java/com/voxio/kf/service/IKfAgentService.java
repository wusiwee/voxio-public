package com.voxio.kf.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.voxio.kf.domain.KfAgent;

/**
 * 客服账号 服务层
 * 
 * @author voxio
 */
public interface IKfAgentService extends IService<KfAgent>
{
    /** 在线状态：离线 */
    public static final String STATUS_OFFLINE = "0";

    /** 在线状态：在线 */
    public static final String STATUS_ONLINE = "1";

    /** 在线状态：忙碌 */
    public static final String STATUS_BUSY = "2";

    /**
     * 查询客服账号列表
     * 
     * @param agent 查询条件
     * @return 客服账号集合
     */
    public List<KfAgent> selectAgentList(KfAgent agent);

    /**
     * 按手机号模糊筛选客服账号（手机号已密文落库，只能解密后内存过滤）
     * 
     * @param agent 查询条件（phone 为模糊关键词）
     * @return 命中的客服账号
     */
    public List<KfAgent> selectAgentListByPhoneLike(KfAgent agent);

    /**
     * 按登录账号查询客服（不含已删除）
     * 
     * @param agentNo 登录账号
     * @return 客服账号
     */
    public KfAgent selectByAgentNo(String agentNo);

    /**
     * 新增客服账号
     * 
     * @param agent 客服账号（password 为明文，落库前加密）
     * @return 结果
     */
    public int insertAgent(KfAgent agent);

    /**
     * 修改客服账号（不处理密码字段）
     * 
     * @param agent 客服账号
     * @return 结果
     */
    public int updateAgent(KfAgent agent);

    /**
     * 批量逻辑删除客服账号
     * 
     * @param agentIds 客服ID数组
     * @return 结果
     */
    public int deleteAgentByIds(Long[] agentIds);

    /**
     * 重置密码
     * 
     * @param agentId 客服ID
     * @param password 新密码（明文）
     * @return 结果
     */
    public int resetPwd(Long agentId, String password);

    /**
     * 就地升级口令哈希（存量 BCrypt 账号在登录校验通过后改写为国密 SM3 加盐哈希）
     * 
     * @param agentId 客服ID
     * @param passwordHash 已哈希的口令
     * @param passwordAlgo 哈希算法标识
     * @return 结果
     */
    public int upgradePassword(Long agentId, String passwordHash, String passwordAlgo);

    /**
     * 启用/停用账号，停用时同步强制下线
     * 
     * @param agent 客服账号（agentId、status）
     * @return 结果
     */
    public int changeStatus(KfAgent agent);

    /**
     * 强制下线：置为离线并清除客服令牌（不影响 current_load，会话仍归属该客服）
     * 
     * @param agentId 客服ID
     * @return 结果
     */
    public int forceOffline(Long agentId);

    /**
     * 记录最后登录信息
     * 
     * @param agentId 客服ID
     * @param ip 登录IP
     */
    public void recordLoginInfo(Long agentId, String ip);
}
