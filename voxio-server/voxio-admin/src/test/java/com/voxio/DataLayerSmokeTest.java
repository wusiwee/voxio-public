package com.voxio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.TestPropertySource;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.pagehelper.autoconfigure.PageHelperAutoConfiguration;
import com.voxio.common.core.domain.entity.SysUser;
import com.voxio.framework.config.MyBatisConfig;
import com.voxio.system.domain.SysConfig;
import com.voxio.system.mapper.SysConfigMapper;
import com.voxio.system.mapper.SysUserMapper;

/**
 * 数据层冒烟测试（不依赖 MySQL/Redis，使用 H2 内存库）
 *
 * 验证目标：
 * 1. 全部 20 个 Mapper XML 能被 MP 工厂解析绑定（namespace/别名/resultMap/MP 通用方法注入）
 * 2. MyBatis-Plus BaseMapper 通用 CRUD 可用（含 @TableName/@TableId/@TableField 映射）
 * 3. LambdaQueryWrapper 可用
 * 4. PageHelper 拦截器挂载到自定义 SqlSessionFactory 后分页生效（存量列表接口）
 */
@SpringBootTest(classes = { DataSourceAutoConfiguration.class, MyBatisConfig.class,
        DataLayerSmokeTest.MapperScanConfig.class })
@ImportAutoConfiguration(PageHelperAutoConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:smoke;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "mybatis.typeAliasesPackage=com.voxio.**.domain",
        "mybatis.mapperLocations=classpath*:mapper/**/*Mapper.xml",
        "mybatis.configLocation=classpath:mybatis/mybatis-config.xml",
        "pagehelper.helperDialect=mysql",
        "pagehelper.supportMethodsArguments=true",
        "pagehelper.params=count=countSql"
})
@Sql(scripts = "/sql/smoke-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
public class DataLayerSmokeTest
{
    @Autowired
    private SysConfigMapper configMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @TestConfiguration
    @org.mybatis.spring.annotation.MapperScan("com.voxio.**.mapper")
    static class MapperScanConfig
    {
    }

    /**
     * 0. 工厂构建即完成全部 XML 解析与 MP 语句注入（能启动说明 namespace/别名/XML 语法全部合法）
     */
    @Test
    public void contextLoadsAndAllMappersBound()
    {
        assertNotNull(sqlSessionFactory);
        // 抽查：MP 为每个 BaseMapper 注入的通用语句存在且命名正确
        assertNotNull(sqlSessionFactory.getConfiguration().getMappedStatement("com.voxio.system.mapper.SysUserMapper.selectById"));
        assertNotNull(sqlSessionFactory.getConfiguration().getMappedStatement("com.voxio.system.mapper.SysUserMapper.insert"));
        assertNotNull(sqlSessionFactory.getConfiguration().getMappedStatement("com.voxio.system.mapper.SysConfigMapper.selectById"));
        // 自定义 XML 语句与 MP 通用语句共存
        assertNotNull(sqlSessionFactory.getConfiguration().getMappedStatement("com.voxio.system.mapper.SysConfigMapper.selectConfigList"));
        assertNotNull(sqlSessionFactory.getConfiguration().getMappedStatement("com.voxio.system.mapper.SysUserMapper.selectUserList"));
    }

    /**
     * 1. MP BaseMapper 通用 CRUD
     */
    @Test
    public void mpBaseMapperCrud()
    {
        SysConfig config = new SysConfig();
        config.setConfigName("冒烟参数");
        config.setConfigKey("smoke.key");
        config.setConfigValue("1");
        config.setConfigType("Y");
        assertEquals(1, configMapper.insert(config));
        assertNotNull(config.getConfigId()); // useGeneratedKeys 回填主键

        SysConfig loaded = configMapper.selectById(config.getConfigId());
        assertEquals("smoke.key", loaded.getConfigKey());

        loaded.setConfigValue("2");
        assertEquals(1, configMapper.updateById(loaded));
        assertEquals("2", configMapper.selectById(config.getConfigId()).getConfigValue());

        assertEquals(1, configMapper.deleteById(config.getConfigId()));
        assertNull(configMapper.selectById(config.getConfigId()));
    }

    /**
     * 2. LambdaQueryWrapper + @TableField(exist=false)/不规则列名映射
     */
    @Test
    public void lambdaWrapperAndFieldMapping()
    {
        SysUser user = new SysUser();
        user.setUserName("smoke_user");
        user.setNickName("冒烟用户");
        user.setPhonenumber("13800000000"); // phonenumber 列名不规则，依赖 @TableField
        user.setEmail("smoke@voxio.com");
        user.setPassword("$2a$10$dummy");
        assertEquals(1, userMapper.insert(user));

        List<SysUser> found = userMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getUserName, "smoke_user")
                        .like(SysUser::getPhonenumber, "13800"));
        assertEquals(1, found.size());
        assertEquals("冒烟用户", found.get(0).getNickName());
        // dept/roles 等 exist=false 字段未进 SQL，params 不映射
        assertNull(found.get(0).getDept());
    }

    /**
     * 3. 存量 PageHelper 分页（XML 动态条件查询 + 拦截器挂自定义工厂生效）
     */
    @Test
    public void pageHelperPagingStillWorksOnMpFactory()
    {
        // 预置数据
        for (int i = 0; i < 5; i++)
        {
            SysConfig c = new SysConfig();
            c.setConfigName("分页参数" + i);
            c.setConfigKey("page.key." + i);
            c.setConfigValue("v");
            c.setConfigType("N");
            configMapper.insert(c);
        }
        try
        {
            com.github.pagehelper.PageHelper.startPage(1, 2);
            SysConfig q = new SysConfig();
            q.setConfigName("分页参数");
            List<SysConfig> page1 = configMapper.selectConfigList(q); // 走 XML + PageHelper
            com.github.pagehelper.PageInfo<SysConfig> info = new com.github.pagehelper.PageInfo<>(page1);
            assertEquals(2, page1.size());
            assertEquals(5, info.getTotal());
        }
        finally
        {
            com.github.pagehelper.PageHelper.clearPage();
        }
    }

    /**
     * 4. MP 分页（新业务方式）在同一工厂上可用
     */
    @Test
    public void mpPageWorks()
    {
        Page<SysUser> page = new Page<>(1, 1);
        IPage<SysUser> result = userMapper.selectPage(page,
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysUser>lambdaQuery().like(SysUser::getUserName, "smoke"));
        assertTrue(result.getTotal() >= 1);
    }
}
