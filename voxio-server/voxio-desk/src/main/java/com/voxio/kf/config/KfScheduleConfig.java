package com.voxio.kf.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 客服域定时任务开关
 * 
 * 单商户单实例部署，直接用 Spring 调度，不占用若依 quartz 的 sys_job 配置。
 * 
 * @author voxio
 */
@Configuration
@EnableScheduling
public class KfScheduleConfig
{
}
