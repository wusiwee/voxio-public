-- ============================================================================
-- Voxio 在线客服系统 · 完整建库脚本（一次执行即可得到可运行的库）
-- 文件：sql/voxio_chat.sql   字符集：utf8mb4 / utf8mb4_general_ci   引擎：InnoDB   依赖：MySQL 8
--
-- 全篇按执行顺序排列，段落用 "SECTION" 标记分隔：段名既是文档，也是「只重跑某一段」的截取边界。
-- 需要单独重跑某一段（例如只升级菜单）时，把该 SECTION 与下一个 SECTION 之间的语句截出来执行即可。
-- 段落内容里的「段落说明」写清了这一段解决什么问题、为什么这样设计。
--
-- 幂等性说明：
--   · 基线表段落使用 drop table if exists + create table，重复执行会重建表与种子数据；
--   · kf_* 业务段落多数同样重建，参数/菜单类采用「先删后插」，索引与增列用 information_schema 守卫；
--   · 因此本脚本面向「全新建库」。已上线库请勿整体重跑：基线表段落会 drop table 重建，
--     要补哪一段就按上一条截出那一段执行，或者先备份再恢复。
-- ============================================================================

-- ============================================================================
-- SECTION 1 · voxio_20260915 · 若依系统基线表（sys_user/sys_role/sys_menu/sys_dict_* 等）与基础数据
-- ============================================================================
-- ----------------------------
-- 1、部门表
-- ----------------------------
drop table if exists sys_dept;
create table sys_dept (
  dept_id           bigint(20)      not null auto_increment    comment '部门id',
  parent_id         bigint(20)      default 0                  comment '父部门id',
  ancestors         varchar(50)     default ''                 comment '祖级列表',
  dept_name         varchar(30)     default ''                 comment '部门名称',
  order_num         int(4)          default 0                  comment '显示顺序',
  leader            varchar(20)     default null               comment '负责人',
  phone             varchar(11)     default null               comment '联系电话',
  email             varchar(50)     default null               comment '邮箱',
  status            char(1)         default '0'                comment '部门状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (dept_id)
) engine=innodb auto_increment=200 comment = '部门表';

-- ----------------------------
-- 初始化-部门表数据
-- ----------------------------
insert into sys_dept values(100,  0,   '0',          'Voxio科技',   0, 'Voxio', '15888888888', 'voxio@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(101,  100, '0,100',      '深圳总公司', 1, 'Voxio', '15888888888', 'voxio@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(102,  100, '0,100',      '长沙分公司', 2, 'Voxio', '15888888888', 'voxio@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(103,  101, '0,100,101',  '研发部门',   1, 'Voxio', '15888888888', 'voxio@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(104,  101, '0,100,101',  '市场部门',   2, 'Voxio', '15888888888', 'voxio@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(105,  101, '0,100,101',  '测试部门',   3, 'Voxio', '15888888888', 'voxio@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(106,  101, '0,100,101',  '财务部门',   4, 'Voxio', '15888888888', 'voxio@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(107,  101, '0,100,101',  '运维部门',   5, 'Voxio', '15888888888', 'voxio@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(108,  102, '0,100,102',  '市场部门',   1, 'Voxio', '15888888888', 'voxio@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(109,  102, '0,100,102',  '财务部门',   2, 'Voxio', '15888888888', 'voxio@qq.com', '0', '0', 'admin', sysdate(), '', null);

-- ----------------------------
-- 2、用户信息表
-- ----------------------------
drop table if exists sys_user;
create table sys_user (
  user_id           bigint(20)      not null auto_increment    comment '用户ID',
  dept_id           bigint(20)      default null               comment '部门ID',
  user_name         varchar(30)     not null                   comment '用户账号',
  nick_name         varchar(30)     not null                   comment '用户昵称',
  user_type         varchar(2)      default '00'               comment '用户类型（00系统用户）',
  email             varchar(50)     default ''                 comment '用户邮箱',
  phonenumber       varchar(11)     default ''                 comment '手机号码',
  sex               char(1)         default '0'                comment '用户性别（0男 1女 2未知）',
  avatar            varchar(100)    default ''                 comment '头像地址',
  password          varchar(100)    default ''                 comment '密码',
  status            char(1)         default '0'                comment '账号状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  login_ip          varchar(128)    default ''                 comment '最后登录IP',
  login_date        datetime                                   comment '最后登录时间',
  pwd_update_date   datetime                                   comment '密码最后更新时间',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (user_id)
) engine=innodb auto_increment=100 comment = '用户信息表';

-- ----------------------------
-- 初始化-用户信息表数据
-- ----------------------------
insert into sys_user values(1,  103, 'admin', 'Voxio', '00', 'voxio@163.com', '15888888888', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '管理员');
insert into sys_user values(2,  105, 'sb',    'Voxio', '00', 'voxio@qq.com',  '15666666666', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '测试员');

-- ----------------------------
-- 3、岗位信息表
-- ----------------------------
drop table if exists sys_post;
create table sys_post
(
  post_id       bigint(20)      not null auto_increment    comment '岗位ID',
  post_code     varchar(64)     not null                   comment '岗位编码',
  post_name     varchar(50)     not null                   comment '岗位名称',
  post_sort     int(4)          not null                   comment '显示顺序',
  status        char(1)         not null                   comment '状态（0正常 1停用）',
  create_by     varchar(64)     default ''                 comment '创建者',
  create_time   datetime                                   comment '创建时间',
  update_by     varchar(64)     default ''			       comment '更新者',
  update_time   datetime                                   comment '更新时间',
  remark        varchar(500)    default null               comment '备注',
  primary key (post_id)
) engine=innodb comment = '岗位信息表';

-- ----------------------------
-- 初始化-岗位信息表数据
-- ----------------------------
insert into sys_post values(1, 'ceo',  '董事长',    1, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(2, 'se',   '项目经理',  2, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(3, 'hr',   '人力资源',  3, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(4, 'user', '普通员工',  4, '0', 'admin', sysdate(), '', null, '');

-- ----------------------------
-- 4、角色信息表
-- ----------------------------
drop table if exists sys_role;
create table sys_role (
  role_id              bigint(20)      not null auto_increment    comment '角色ID',
  role_name            varchar(30)     not null                   comment '角色名称',
  role_key             varchar(100)    not null                   comment '角色权限字符串',
  role_sort            int(4)          not null                   comment '显示顺序',
  data_scope           char(1)         default '1'                comment '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  menu_check_strictly  tinyint(1)      default 1                  comment '菜单树选择项是否关联显示',
  dept_check_strictly  tinyint(1)      default 1                  comment '部门树选择项是否关联显示',
  status               char(1)         not null                   comment '角色状态（0正常 1停用）',
  del_flag             char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by            varchar(64)     default ''                 comment '创建者',
  create_time          datetime                                   comment '创建时间',
  update_by            varchar(64)     default ''                 comment '更新者',
  update_time          datetime                                   comment '更新时间',
  remark               varchar(500)    default null               comment '备注',
  primary key (role_id)
) engine=innodb auto_increment=100 comment = '角色信息表';

-- ----------------------------
-- 初始化-角色信息表数据
-- ----------------------------
insert into sys_role values('1', '超级管理员',  'admin',  1, 1, 1, 1, '0', '0', 'admin', sysdate(), '', null, '超级管理员');
insert into sys_role values('2', '普通角色',    'common', 2, 2, 1, 1, '0', '0', 'admin', sysdate(), '', null, '普通角色');

-- ----------------------------
-- 5、菜单权限表
-- ----------------------------
drop table if exists sys_menu;
create table sys_menu (
  menu_id           bigint(20)      not null auto_increment    comment '菜单ID',
  menu_name         varchar(50)     not null                   comment '菜单名称',
  parent_id         bigint(20)      default 0                  comment '父菜单ID',
  order_num         int(4)          default 0                  comment '显示顺序',
  path              varchar(200)    default ''                 comment '路由地址',
  component         varchar(255)    default null               comment '组件路径',
  query             varchar(255)    default null               comment '路由参数',
  route_name        varchar(50)     default ''                 comment '路由名称',
  is_frame          int(1)          default 1                  comment '是否为外链（0是 1否）',
  is_cache          int(1)          default 0                  comment '是否缓存（0缓存 1不缓存）',
  menu_type         char(1)         default ''                 comment '菜单类型（M目录 C菜单 F按钮）',
  visible           char(1)         default 0                  comment '菜单状态（0显示 1隐藏）',
  status            char(1)         default 0                  comment '菜单状态（0正常 1停用）',
  perms             varchar(100)    default null               comment '权限标识',
  icon              varchar(100)    default '#'                comment '菜单图标',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default ''                 comment '备注',
  primary key (menu_id)
) engine=innodb auto_increment=2000 comment = '菜单权限表';

-- ----------------------------
-- 初始化-菜单信息表数据
-- ----------------------------
-- 一级菜单
insert into sys_menu values('1', '系统管理', '0', '1', 'system',           null, '', '', 1, 0, 'M', '0', '0', '', 'system',   'admin', sysdate(), '', null, '系统管理目录');
insert into sys_menu values('2', '系统监控', '0', '2', 'monitor',          null, '', '', 1, 0, 'M', '0', '0', '', 'monitor',  'admin', sysdate(), '', null, '系统监控目录');
insert into sys_menu values('3', '系统工具', '0', '3', 'tool',             null, '', '', 1, 0, 'M', '0', '0', '', 'tool',     'admin', sysdate(), '', null, '系统工具目录');
-- 二级菜单
insert into sys_menu values('100',  '用户管理', '1',   '1', 'user',       'system/user/index',        '', '', 1, 0, 'C', '0', '0', 'system:user:list',        'user',          'admin', sysdate(), '', null, '用户管理菜单');
insert into sys_menu values('101',  '角色管理', '1',   '2', 'role',       'system/role/index',        '', '', 1, 0, 'C', '0', '0', 'system:role:list',        'peoples',       'admin', sysdate(), '', null, '角色管理菜单');
insert into sys_menu values('102',  '菜单管理', '1',   '3', 'menu',       'system/menu/index',        '', '', 1, 0, 'C', '0', '0', 'system:menu:list',        'tree-table',    'admin', sysdate(), '', null, '菜单管理菜单');
insert into sys_menu values('103',  '部门管理', '1',   '4', 'dept',       'system/dept/index',        '', '', 1, 0, 'C', '0', '0', 'system:dept:list',        'tree',          'admin', sysdate(), '', null, '部门管理菜单');
insert into sys_menu values('104',  '岗位管理', '1',   '5', 'post',       'system/post/index',        '', '', 1, 0, 'C', '0', '0', 'system:post:list',        'post',          'admin', sysdate(), '', null, '岗位管理菜单');
insert into sys_menu values('105',  '字典管理', '1',   '6', 'dict',       'system/dict/index',        '', '', 1, 0, 'C', '0', '0', 'system:dict:list',        'dict',          'admin', sysdate(), '', null, '字典管理菜单');
insert into sys_menu values('106',  '参数设置', '1',   '7', 'config',     'system/config/index',      '', '', 1, 0, 'C', '0', '0', 'system:config:list',      'edit',          'admin', sysdate(), '', null, '参数设置菜单');
insert into sys_menu values('107',  '通知公告', '1',   '8', 'notice',     'system/notice/index',      '', '', 1, 0, 'C', '0', '0', 'system:notice:list',      'message',       'admin', sysdate(), '', null, '通知公告菜单');
insert into sys_menu values('108',  '日志管理', '1',   '9', 'log',        '',                         '', '', 1, 0, 'M', '0', '0', '',                        'log',           'admin', sysdate(), '', null, '日志管理菜单');
insert into sys_menu values('109',  '在线用户', '2',   '1', 'online',     'monitor/online/index',     '', '', 1, 0, 'C', '0', '0', 'monitor:online:list',     'online',        'admin', sysdate(), '', null, '在线用户菜单');
insert into sys_menu values('110',  '定时任务', '2',   '2', 'job',        'monitor/job/index',        '', '', 1, 0, 'C', '0', '0', 'monitor:job:list',        'job',           'admin', sysdate(), '', null, '定时任务菜单');
insert into sys_menu values('111',  '数据监控', '2',   '3', 'druid',      'monitor/druid/index',      '', '', 1, 0, 'C', '0', '0', 'monitor:druid:list',      'druid',         'admin', sysdate(), '', null, '数据监控菜单');
insert into sys_menu values('112',  '服务监控', '2',   '4', 'server',     'monitor/server/index',     '', '', 1, 0, 'C', '0', '0', 'monitor:server:list',     'server',        'admin', sysdate(), '', null, '服务监控菜单');
insert into sys_menu values('113',  '缓存监控', '2',   '5', 'cache',      'monitor/cache/index',      '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis',         'admin', sysdate(), '', null, '缓存监控菜单');
insert into sys_menu values('114',  '缓存列表', '2',   '6', 'cacheList',  'monitor/cache/list',       '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis-list',    'admin', sysdate(), '', null, '缓存列表菜单');
insert into sys_menu values('115',  '表单构建', '3',   '1', 'build',      'tool/build/index',         '', '', 1, 0, 'C', '0', '0', 'tool:build:list',         'build',         'admin', sysdate(), '', null, '表单构建菜单');
insert into sys_menu values('117',  '系统接口', '3',   '3', 'swagger',    'tool/swagger/index',       '', '', 1, 0, 'C', '0', '0', 'tool:swagger:list',       'swagger',       'admin', sysdate(), '', null, '系统接口菜单');
-- 三级菜单
insert into sys_menu values('500',  '操作日志', '108', '1', 'operlog',    'monitor/operlog/index',    '', '', 1, 0, 'C', '0', '0', 'monitor:operlog:list',    'form',          'admin', sysdate(), '', null, '操作日志菜单');
insert into sys_menu values('501',  '登录日志', '108', '2', 'logininfor', 'monitor/logininfor/index', '', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor',    'admin', sysdate(), '', null, '登录日志菜单');
-- 用户管理按钮
insert into sys_menu values('1000', '用户查询', '100', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1001', '用户新增', '100', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1002', '用户修改', '100', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1003', '用户删除', '100', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1004', '用户导出', '100', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:export',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1005', '用户导入', '100', '6',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:import',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1006', '重置密码', '100', '7',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd',       '#', 'admin', sysdate(), '', null, '');
-- 角色管理按钮
insert into sys_menu values('1007', '角色查询', '101', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1008', '角色新增', '101', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1009', '角色修改', '101', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1010', '角色删除', '101', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1011', '角色导出', '101', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:export',         '#', 'admin', sysdate(), '', null, '');
-- 菜单管理按钮
insert into sys_menu values('1012', '菜单查询', '102', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1013', '菜单新增', '102', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1014', '菜单修改', '102', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1015', '菜单删除', '102', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove',         '#', 'admin', sysdate(), '', null, '');
-- 部门管理按钮
insert into sys_menu values('1016', '部门查询', '103', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1017', '部门新增', '103', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1018', '部门修改', '103', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1019', '部门删除', '103', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove',         '#', 'admin', sysdate(), '', null, '');
-- 岗位管理按钮
insert into sys_menu values('1020', '岗位查询', '104', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1021', '岗位新增', '104', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1022', '岗位修改', '104', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1023', '岗位删除', '104', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1024', '岗位导出', '104', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:export',         '#', 'admin', sysdate(), '', null, '');
-- 字典管理按钮
insert into sys_menu values('1025', '字典查询', '105', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1026', '字典新增', '105', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1027', '字典修改', '105', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1028', '字典删除', '105', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1029', '字典导出', '105', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:export',         '#', 'admin', sysdate(), '', null, '');
-- 参数设置按钮
insert into sys_menu values('1030', '参数查询', '106', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1031', '参数新增', '106', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1032', '参数修改', '106', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1033', '参数删除', '106', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:remove',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1034', '参数导出', '106', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:export',       '#', 'admin', sysdate(), '', null, '');
-- 通知公告按钮
insert into sys_menu values('1035', '公告查询', '107', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1036', '公告新增', '107', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1037', '公告修改', '107', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1038', '公告删除', '107', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:remove',       '#', 'admin', sysdate(), '', null, '');
-- 操作日志按钮
insert into sys_menu values('1039', '操作查询', '500', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:query',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1040', '操作删除', '500', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:remove',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1041', '日志导出', '500', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:export',     '#', 'admin', sysdate(), '', null, '');
-- 登录日志按钮
insert into sys_menu values('1042', '登录查询', '501', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:query',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1043', '登录删除', '501', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:remove',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1044', '日志导出', '501', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:export',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1045', '账户解锁', '501', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:unlock',  '#', 'admin', sysdate(), '', null, '');
-- 在线用户按钮
insert into sys_menu values('1046', '在线查询', '109', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:query',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1047', '批量强退', '109', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1048', '单条强退', '109', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', sysdate(), '', null, '');
-- 定时任务按钮
insert into sys_menu values('1049', '任务查询', '110', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1050', '任务新增', '110', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1051', '任务修改', '110', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1052', '任务删除', '110', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1053', '状态修改', '110', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:changeStatus',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1054', '任务导出', '110', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:export',         '#', 'admin', sysdate(), '', null, '');

-- ----------------------------
-- 6、用户和角色关联表  用户N-1角色
-- ----------------------------
drop table if exists sys_user_role;
create table sys_user_role (
  user_id   bigint(20) not null comment '用户ID',
  role_id   bigint(20) not null comment '角色ID',
  primary key(user_id, role_id)
) engine=innodb comment = '用户和角色关联表';

-- ----------------------------
-- 初始化-用户和角色关联表数据
-- ----------------------------
insert into sys_user_role values ('1', '1');
insert into sys_user_role values ('2', '2');

-- ----------------------------
-- 7、角色和菜单关联表  角色1-N菜单
-- ----------------------------
drop table if exists sys_role_menu;
create table sys_role_menu (
  role_id   bigint(20) not null comment '角色ID',
  menu_id   bigint(20) not null comment '菜单ID',
  primary key(role_id, menu_id)
) engine=innodb comment = '角色和菜单关联表';

-- ----------------------------
-- 初始化-角色和菜单关联表数据
-- ----------------------------
insert into sys_role_menu values ('2', '1');
insert into sys_role_menu values ('2', '2');
insert into sys_role_menu values ('2', '3');
insert into sys_role_menu values ('2', '4');
insert into sys_role_menu values ('2', '100');
insert into sys_role_menu values ('2', '101');
insert into sys_role_menu values ('2', '102');
insert into sys_role_menu values ('2', '103');
insert into sys_role_menu values ('2', '104');
insert into sys_role_menu values ('2', '105');
insert into sys_role_menu values ('2', '106');
insert into sys_role_menu values ('2', '107');
insert into sys_role_menu values ('2', '108');
insert into sys_role_menu values ('2', '109');
insert into sys_role_menu values ('2', '110');
insert into sys_role_menu values ('2', '111');
insert into sys_role_menu values ('2', '112');
insert into sys_role_menu values ('2', '113');
insert into sys_role_menu values ('2', '114');
insert into sys_role_menu values ('2', '115');
insert into sys_role_menu values ('2', '117');
insert into sys_role_menu values ('2', '500');
insert into sys_role_menu values ('2', '501');
insert into sys_role_menu values ('2', '1000');
insert into sys_role_menu values ('2', '1001');
insert into sys_role_menu values ('2', '1002');
insert into sys_role_menu values ('2', '1003');
insert into sys_role_menu values ('2', '1004');
insert into sys_role_menu values ('2', '1005');
insert into sys_role_menu values ('2', '1006');
insert into sys_role_menu values ('2', '1007');
insert into sys_role_menu values ('2', '1008');
insert into sys_role_menu values ('2', '1009');
insert into sys_role_menu values ('2', '1010');
insert into sys_role_menu values ('2', '1011');
insert into sys_role_menu values ('2', '1012');
insert into sys_role_menu values ('2', '1013');
insert into sys_role_menu values ('2', '1014');
insert into sys_role_menu values ('2', '1015');
insert into sys_role_menu values ('2', '1016');
insert into sys_role_menu values ('2', '1017');
insert into sys_role_menu values ('2', '1018');
insert into sys_role_menu values ('2', '1019');
insert into sys_role_menu values ('2', '1020');
insert into sys_role_menu values ('2', '1021');
insert into sys_role_menu values ('2', '1022');
insert into sys_role_menu values ('2', '1023');
insert into sys_role_menu values ('2', '1024');
insert into sys_role_menu values ('2', '1025');
insert into sys_role_menu values ('2', '1026');
insert into sys_role_menu values ('2', '1027');
insert into sys_role_menu values ('2', '1028');
insert into sys_role_menu values ('2', '1029');
insert into sys_role_menu values ('2', '1030');
insert into sys_role_menu values ('2', '1031');
insert into sys_role_menu values ('2', '1032');
insert into sys_role_menu values ('2', '1033');
insert into sys_role_menu values ('2', '1034');
insert into sys_role_menu values ('2', '1035');
insert into sys_role_menu values ('2', '1036');
insert into sys_role_menu values ('2', '1037');
insert into sys_role_menu values ('2', '1038');
insert into sys_role_menu values ('2', '1039');
insert into sys_role_menu values ('2', '1040');
insert into sys_role_menu values ('2', '1041');
insert into sys_role_menu values ('2', '1042');
insert into sys_role_menu values ('2', '1043');
insert into sys_role_menu values ('2', '1044');
insert into sys_role_menu values ('2', '1045');
insert into sys_role_menu values ('2', '1046');
insert into sys_role_menu values ('2', '1047');
insert into sys_role_menu values ('2', '1048');
insert into sys_role_menu values ('2', '1049');
insert into sys_role_menu values ('2', '1050');
insert into sys_role_menu values ('2', '1051');
insert into sys_role_menu values ('2', '1052');
insert into sys_role_menu values ('2', '1053');
insert into sys_role_menu values ('2', '1054');

-- ----------------------------
-- 8、角色和部门关联表  角色1-N部门
-- ----------------------------
drop table if exists sys_role_dept;
create table sys_role_dept (
  role_id   bigint(20) not null comment '角色ID',
  dept_id   bigint(20) not null comment '部门ID',
  primary key(role_id, dept_id)
) engine=innodb comment = '角色和部门关联表';

-- ----------------------------
-- 初始化-角色和部门关联表数据
-- ----------------------------
insert into sys_role_dept values ('2', '100');
insert into sys_role_dept values ('2', '101');
insert into sys_role_dept values ('2', '105');

-- ----------------------------
-- 9、用户与岗位关联表  用户1-N岗位
-- ----------------------------
drop table if exists sys_user_post;
create table sys_user_post
(
  user_id   bigint(20) not null comment '用户ID',
  post_id   bigint(20) not null comment '岗位ID',
  primary key (user_id, post_id)
) engine=innodb comment = '用户与岗位关联表';

-- ----------------------------
-- 初始化-用户与岗位关联表数据
-- ----------------------------
insert into sys_user_post values ('1', '1');
insert into sys_user_post values ('2', '2');

-- ----------------------------
-- 10、操作日志记录
-- ----------------------------
drop table if exists sys_oper_log;
create table sys_oper_log (
  oper_id           bigint(20)      not null auto_increment    comment '日志主键',
  title             varchar(50)     default ''                 comment '模块标题',
  business_type     int(2)          default 0                  comment '业务类型（0其它 1新增 2修改 3删除）',
  method            varchar(200)    default ''                 comment '方法名称',
  request_method    varchar(10)     default ''                 comment '请求方式',
  operator_type     int(1)          default 0                  comment '操作类别（0其它 1后台用户 2手机端用户）',
  oper_name         varchar(50)     default ''                 comment '操作人员',
  dept_name         varchar(50)     default ''                 comment '部门名称',
  oper_url          varchar(255)    default ''                 comment '请求URL',
  oper_ip           varchar(128)    default ''                 comment '主机地址',
  oper_location     varchar(255)    default ''                 comment '操作地点',
  oper_param        varchar(2000)   default ''                 comment '请求参数',
  json_result       varchar(2000)   default ''                 comment '返回参数',
  status            int(1)          default 0                  comment '操作状态（0正常 1异常）',
  error_msg         varchar(2000)   default ''                 comment '错误消息',
  oper_time         datetime                                   comment '操作时间',
  cost_time         bigint(20)      default 0                  comment '消耗时间',
  primary key (oper_id),
  key idx_sys_oper_log_bt (business_type),
  key idx_sys_oper_log_s  (status),
  key idx_sys_oper_log_ot (oper_time)
) engine=innodb auto_increment=100 comment = '操作日志记录';

-- ----------------------------
-- 11、字典类型表
-- ----------------------------
drop table if exists sys_dict_type;
create table sys_dict_type
(
  dict_id          bigint(20)      not null auto_increment    comment '字典主键',
  dict_name        varchar(100)    default ''                 comment '字典名称',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_id),
  unique (dict_type)
) engine=innodb auto_increment=100 comment = '字典类型表';

insert into sys_dict_type values(1,  '用户性别', 'sys_user_sex',        '0', 'admin', sysdate(), '', null, '用户性别列表');
insert into sys_dict_type values(2,  '菜单状态', 'sys_show_hide',       '0', 'admin', sysdate(), '', null, '菜单状态列表');
insert into sys_dict_type values(3,  '系统开关', 'sys_normal_disable',  '0', 'admin', sysdate(), '', null, '系统开关列表');
insert into sys_dict_type values(4,  '任务状态', 'sys_job_status',      '0', 'admin', sysdate(), '', null, '任务状态列表');
insert into sys_dict_type values(5,  '任务分组', 'sys_job_group',       '0', 'admin', sysdate(), '', null, '任务分组列表');
insert into sys_dict_type values(6,  '系统是否', 'sys_yes_no',          '0', 'admin', sysdate(), '', null, '系统是否列表');
insert into sys_dict_type values(7,  '通知类型', 'sys_notice_type',     '0', 'admin', sysdate(), '', null, '通知类型列表');
insert into sys_dict_type values(8,  '通知状态', 'sys_notice_status',   '0', 'admin', sysdate(), '', null, '通知状态列表');
insert into sys_dict_type values(9,  '操作类型', 'sys_oper_type',       '0', 'admin', sysdate(), '', null, '操作类型列表');
insert into sys_dict_type values(10, '系统状态', 'sys_common_status',   '0', 'admin', sysdate(), '', null, '登录状态列表');

-- ----------------------------
-- 12、字典数据表
-- ----------------------------
drop table if exists sys_dict_data;
create table sys_dict_data
(
  dict_code        bigint(20)      not null auto_increment    comment '字典编码',
  dict_sort        int(4)          default 0                  comment '字典排序',
  dict_label       varchar(100)    default ''                 comment '字典标签',
  dict_value       varchar(100)    default ''                 comment '字典键值',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  css_class        varchar(100)    default null               comment '样式属性（其他样式扩展）',
  list_class       varchar(100)    default null               comment '表格回显样式',
  is_default       char(1)         default 'N'                comment '是否默认（Y是 N否）',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_code)
) engine=innodb auto_increment=100 comment = '字典数据表';

insert into sys_dict_data values(1,  1,  '男',       '0',       'sys_user_sex',        '',   '',        'Y', '0', 'admin', sysdate(), '', null, '性别男');
insert into sys_dict_data values(2,  2,  '女',       '1',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别女');
insert into sys_dict_data values(3,  3,  '未知',     '2',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别未知');
insert into sys_dict_data values(4,  1,  '显示',     '0',       'sys_show_hide',       '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '显示菜单');
insert into sys_dict_data values(5,  2,  '隐藏',     '1',       'sys_show_hide',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '隐藏菜单');
insert into sys_dict_data values(6,  1,  '正常',     '0',       'sys_normal_disable',  '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(7,  2,  '停用',     '1',       'sys_normal_disable',  '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(8,  1,  '正常',     '0',       'sys_job_status',      '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(9,  2,  '暂停',     '1',       'sys_job_status',      '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(10, 1,  '默认',     'DEFAULT', 'sys_job_group',       '',   '',        'Y', '0', 'admin', sysdate(), '', null, '默认分组');
insert into sys_dict_data values(11, 2,  '系统',     'SYSTEM',  'sys_job_group',       '',   '',        'N', '0', 'admin', sysdate(), '', null, '系统分组');
insert into sys_dict_data values(12, 1,  '是',       'Y',       'sys_yes_no',          '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '系统默认是');
insert into sys_dict_data values(13, 2,  '否',       'N',       'sys_yes_no',          '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '系统默认否');
insert into sys_dict_data values(14, 1,  '通知',     '1',       'sys_notice_type',     '',   'warning', 'Y', '0', 'admin', sysdate(), '', null, '通知');
insert into sys_dict_data values(15, 2,  '公告',     '2',       'sys_notice_type',     '',   'success', 'N', '0', 'admin', sysdate(), '', null, '公告');
insert into sys_dict_data values(16, 1,  '正常',     '0',       'sys_notice_status',   '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(17, 2,  '关闭',     '1',       'sys_notice_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '关闭状态');
insert into sys_dict_data values(18, 99, '其他',     '0',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '其他操作');
insert into sys_dict_data values(19, 1,  '新增',     '1',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '新增操作');
insert into sys_dict_data values(20, 2,  '修改',     '2',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '修改操作');
insert into sys_dict_data values(21, 3,  '删除',     '3',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '删除操作');
insert into sys_dict_data values(22, 4,  '授权',     '4',       'sys_oper_type',       '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '授权操作');
insert into sys_dict_data values(23, 5,  '导出',     '5',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导出操作');
insert into sys_dict_data values(24, 6,  '导入',     '6',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导入操作');
insert into sys_dict_data values(25, 7,  '强退',     '7',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '强退操作');
insert into sys_dict_data values(26, 8,  '生成代码', '8',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '生成操作');
insert into sys_dict_data values(27, 9,  '清空数据', '9',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '清空操作');
insert into sys_dict_data values(28, 1,  '成功',     '0',       'sys_common_status',   '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(29, 2,  '失败',     '1',       'sys_common_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');

-- ----------------------------
-- 13、参数配置表
-- ----------------------------
drop table if exists sys_config;
create table sys_config (
  config_id         int(5)          not null auto_increment    comment '参数主键',
  config_name       varchar(100)    default ''                 comment '参数名称',
  config_key        varchar(100)    default ''                 comment '参数键名',
  config_value      varchar(500)    default ''                 comment '参数键值',
  config_type       char(1)         default 'N'                comment '系统内置（Y是 N否）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (config_id)
) engine=innodb auto_increment=100 comment = '参数配置表';

insert into sys_config values(1, '主框架页-默认皮肤样式名称',     'sys.index.skinName',               'skin-blue',     'Y', 'admin', sysdate(), '', null, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow' );
insert into sys_config values(2, '用户管理-账号初始密码',         'sys.user.initPassword',            '123456',        'Y', 'admin', sysdate(), '', null, '初始化密码 123456' );
insert into sys_config values(3, '主框架页-侧边栏主题',           'sys.index.sideTheme',              'theme-dark',    'Y', 'admin', sysdate(), '', null, '深色主题theme-dark，浅色主题theme-light' );
insert into sys_config values(4, '账号自助-验证码开关',           'sys.account.captchaEnabled',       'true',          'Y', 'admin', sysdate(), '', null, '是否开启验证码功能（true开启，false关闭）');
insert into sys_config values(5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser',         'false',         'Y', 'admin', sysdate(), '', null, '是否开启注册用户功能（true开启，false关闭）');
insert into sys_config values(6, '用户登录-黑名单列表',           'sys.login.blackIPList',            '',              'Y', 'admin', sysdate(), '', null, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');
insert into sys_config values(7, '用户管理-初始密码修改策略',     'sys.account.initPasswordModify',   '1',             'Y', 'admin', sysdate(), '', null, '0：初始密码修改策略关闭，没有任何提示，1：提醒用户，如果未修改初始密码，则在登录时就会提醒修改密码对话框');
insert into sys_config values(8, '用户管理-账号密码更新周期',     'sys.account.passwordValidateDays', '0',             'Y', 'admin', sysdate(), '', null, '密码更新周期（填写数字，数据初始化值为0不限制，若修改必须为大于0小于365的正整数），如果超过这个周期登录系统时，则在登录时就会提醒修改密码对话框');
insert into sys_config values(9, '用户管理-密码字符范围',         'sys.account.chrtype',              '0',             'Y', 'admin', sysdate(), '', null, '默认任意字符范围，0任意（密码可以输入任意字符），1数字（密码只能为0-9数字），2英文字母（密码只能为a-z和A-Z字母），3字母和数字（密码必须包含字母，数字）,4字母数字和特殊字符（目前支持的特殊字符包括：~!@#$%^&*()-=_+）');

-- ----------------------------
-- 14、系统访问记录
-- ----------------------------
drop table if exists sys_logininfor;
create table sys_logininfor (
  info_id        bigint(20)     not null auto_increment   comment '访问ID',
  user_name      varchar(50)    default ''                comment '用户账号',
  ipaddr         varchar(128)   default ''                comment '登录IP地址',
  login_location varchar(255)   default ''                comment '登录地点',
  browser        varchar(50)    default ''                comment '浏览器类型',
  os             varchar(50)    default ''                comment '操作系统',
  status         char(1)        default '0'               comment '登录状态（0成功 1失败）',
  msg            varchar(255)   default ''                comment '提示消息',
  login_time     datetime                                 comment '访问时间',
  primary key (info_id),
  key idx_sys_logininfor_s  (status),
  key idx_sys_logininfor_lt (login_time)
) engine=innodb auto_increment=100 comment = '系统访问记录';

-- ----------------------------
-- 15、定时任务调度表
-- ----------------------------
drop table if exists sys_job;
create table sys_job (
  job_id              bigint(20)    not null auto_increment    comment '任务ID',
  job_name            varchar(64)   default ''                 comment '任务名称',
  job_group           varchar(64)   default 'DEFAULT'          comment '任务组名',
  invoke_target       varchar(500)  not null                   comment '调用目标字符串',
  cron_expression     varchar(255)  default ''                 comment 'cron执行表达式',
  misfire_policy      varchar(20)   default '3'                comment '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  concurrent          char(1)       default '1'                comment '是否并发执行（0允许 1禁止）',
  status              char(1)       default '0'                comment '状态（0正常 1暂停）',
  create_by           varchar(64)   default ''                 comment '创建者',
  create_time         datetime                                 comment '创建时间',
  update_by           varchar(64)   default ''                 comment '更新者',
  update_time         datetime                                 comment '更新时间',
  remark              varchar(500)  default ''                 comment '备注信息',
  primary key (job_id, job_name, job_group)
) engine=innodb auto_increment=100 comment = '定时任务调度表';

insert into sys_job values(1, '系统默认（无参）', 'DEFAULT', 'voxioTask.voxioNoParams',        '0/10 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(2, '系统默认（有参）', 'DEFAULT', 'voxioTask.voxioParams(\'sb\')',  '0/15 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(3, '系统默认（多参）', 'DEFAULT', 'voxioTask.voxioMultipleParams(\'sb\', true, 2000L, 316.50D, 100)',  '0/20 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');

-- ----------------------------
-- 16、定时任务调度日志表
-- ----------------------------
drop table if exists sys_job_log;
create table sys_job_log (
  job_log_id          bigint(20)     not null auto_increment    comment '任务日志ID',
  job_name            varchar(64)    not null                   comment '任务名称',
  job_group           varchar(64)    not null                   comment '任务组名',
  invoke_target       varchar(500)   not null                   comment '调用目标字符串',
  job_message         varchar(500)                              comment '日志信息',
  status              char(1)        default '0'                comment '执行状态（0正常 1失败）',
  exception_info      varchar(2000)  default ''                 comment '异常信息',
  start_time          datetime                                  comment '执行开始时间',
  end_time            datetime                                  comment '执行结束时间',
  create_time         datetime                                  comment '创建时间',
  primary key (job_log_id)
) engine=innodb comment = '定时任务调度日志表';

-- ----------------------------
-- 17、通知公告表
-- ----------------------------
drop table if exists sys_notice;
create table sys_notice (
  notice_id         int(4)          not null auto_increment    comment '公告ID',
  notice_title      varchar(50)     not null                   comment '公告标题',
  notice_type       char(1)         not null                   comment '公告类型（1通知 2公告）',
  notice_content    longblob        default null               comment '公告内容',
  status            char(1)         default '0'                comment '公告状态（0正常 1关闭）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(255)    default null               comment '备注',
  primary key (notice_id)
) engine=innodb auto_increment=10 comment = '通知公告表';

-- ----------------------------
-- 初始化-公告信息表数据
-- ----------------------------
insert into sys_notice values('1', '温馨提醒：2018-07-01 Voxio新版本发布啦', '2', '新版本内容', '0', 'admin', sysdate(), '', null, '管理员');
insert into sys_notice values('2', '维护通知：2018-07-01 Voxio系统凌晨维护', '1', '维护内容',   '0', 'admin', sysdate(), '', null, '管理员');

-- ----------------------------
-- 18、公告已读记录表
-- ----------------------------
drop table if exists sys_notice_read;
create table sys_notice_read (
  read_id          bigint(20)       not null auto_increment    comment '已读主键',
  notice_id        int(4)           not null                   comment '公告id',
  user_id          bigint(20)       not null                   comment '用户id',
  read_time        datetime         not null                   comment '阅读时间',
  primary key (read_id),
  unique key uk_user_notice (user_id, notice_id)   comment '同一用户同一公告只记录一次'
) engine=innodb auto_increment=1 comment='公告已读记录表';

-- ----------------------------
-- ============================================================================
-- SECTION 2 · quartz · Quartz 定时任务表（会话超时自动结束等 Job 依赖）
-- ============================================================================

DROP TABLE IF EXISTS QRTZ_FIRED_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_PAUSED_TRIGGER_GRPS;
DROP TABLE IF EXISTS QRTZ_SCHEDULER_STATE;
DROP TABLE IF EXISTS QRTZ_LOCKS;
DROP TABLE IF EXISTS QRTZ_SIMPLE_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_SIMPROP_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_CRON_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_BLOB_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_JOB_DETAILS;
DROP TABLE IF EXISTS QRTZ_CALENDARS;

-- ----------------------------
-- 1、存储每一个已配置的 jobDetail 的详细信息
-- ----------------------------
create table QRTZ_JOB_DETAILS (
    sched_name           varchar(120)    not null            comment '调度名称',
    job_name             varchar(200)    not null            comment '任务名称',
    job_group            varchar(200)    not null            comment '任务组名',
    description          varchar(250)    null                comment '相关介绍',
    job_class_name       varchar(250)    not null            comment '执行任务类名称',
    is_durable           varchar(1)      not null            comment '是否持久化',
    is_nonconcurrent     varchar(1)      not null            comment '是否并发',
    is_update_data       varchar(1)      not null            comment '是否更新数据',
    requests_recovery    varchar(1)      not null            comment '是否接受恢复执行',
    job_data             blob            null                comment '存放持久化job对象',
    primary key (sched_name, job_name, job_group)
) engine=innodb comment = '任务详细信息表';

-- ----------------------------
-- 2、 存储已配置的 Trigger 的信息
-- ----------------------------
create table QRTZ_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment '触发器的名字',
    trigger_group        varchar(200)    not null            comment '触发器所属组的名字',
    job_name             varchar(200)    not null            comment 'qrtz_job_details表job_name的外键',
    job_group            varchar(200)    not null            comment 'qrtz_job_details表job_group的外键',
    description          varchar(250)    null                comment '相关介绍',
    next_fire_time       bigint(13)      null                comment '上一次触发时间（毫秒）',
    prev_fire_time       bigint(13)      null                comment '下一次触发时间（默认为-1表示不触发）',
    priority             integer         null                comment '优先级',
    trigger_state        varchar(16)     not null            comment '触发器状态',
    trigger_type         varchar(8)      not null            comment '触发器的类型',
    start_time           bigint(13)      not null            comment '开始时间',
    end_time             bigint(13)      null                comment '结束时间',
    calendar_name        varchar(200)    null                comment '日程表名称',
    misfire_instr        smallint(2)     null                comment '补偿执行的策略',
    job_data             blob            null                comment '存放持久化job对象',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, job_name, job_group) references QRTZ_JOB_DETAILS(sched_name, job_name, job_group)
) engine=innodb comment = '触发器详细信息表';

-- ----------------------------
-- 3、 存储简单的 Trigger，包括重复次数，间隔，以及已触发的次数
-- ----------------------------
create table QRTZ_SIMPLE_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    repeat_count         bigint(7)       not null            comment '重复的次数统计',
    repeat_interval      bigint(12)      not null            comment '重复的间隔时间',
    times_triggered      bigint(10)      not null            comment '已经触发的次数',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = '简单触发器的信息表';

-- ----------------------------
-- 4、 存储 Cron Trigger，包括 Cron 表达式和时区信息
-- ---------------------------- 
create table QRTZ_CRON_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    cron_expression      varchar(200)    not null            comment 'cron表达式',
    time_zone_id         varchar(80)                         comment '时区',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = 'Cron类型的触发器表';

-- ----------------------------
-- 5、 Trigger 作为 Blob 类型存储(用于 Quartz 用户用 JDBC 创建他们自己定制的 Trigger 类型，JobStore 并不知道如何存储实例的时候)
-- ---------------------------- 
create table QRTZ_BLOB_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    blob_data            blob            null                comment '存放持久化Trigger对象',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = 'Blob类型的触发器表';

-- ----------------------------
-- 6、 以 Blob 类型存储存放日历信息， quartz可配置一个日历来指定一个时间范围
-- ---------------------------- 
create table QRTZ_CALENDARS (
    sched_name           varchar(120)    not null            comment '调度名称',
    calendar_name        varchar(200)    not null            comment '日历名称',
    calendar             blob            not null            comment '存放持久化calendar对象',
    primary key (sched_name, calendar_name)
) engine=innodb comment = '日历信息表';

-- ----------------------------
-- 7、 存储已暂停的 Trigger 组的信息
-- ---------------------------- 
create table QRTZ_PAUSED_TRIGGER_GRPS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    primary key (sched_name, trigger_group)
) engine=innodb comment = '暂停的触发器表';

-- ----------------------------
-- 8、 存储与已触发的 Trigger 相关的状态信息，以及相联 Job 的执行信息
-- ---------------------------- 
create table QRTZ_FIRED_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    entry_id             varchar(95)     not null            comment '调度器实例id',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    instance_name        varchar(200)    not null            comment '调度器实例名',
    fired_time           bigint(13)      not null            comment '触发的时间',
    sched_time           bigint(13)      not null            comment '定时器制定的时间',
    priority             integer         not null            comment '优先级',
    state                varchar(16)     not null            comment '状态',
    job_name             varchar(200)    null                comment '任务名称',
    job_group            varchar(200)    null                comment '任务组名',
    is_nonconcurrent     varchar(1)      null                comment '是否并发',
    requests_recovery    varchar(1)      null                comment '是否接受恢复执行',
    primary key (sched_name, entry_id)
) engine=innodb comment = '已触发的触发器表';

-- ----------------------------
-- 9、 存储少量的有关 Scheduler 的状态信息，假如是用于集群中，可以看到其他的 Scheduler 实例
-- ---------------------------- 
create table QRTZ_SCHEDULER_STATE (
    sched_name           varchar(120)    not null            comment '调度名称',
    instance_name        varchar(200)    not null            comment '实例名称',
    last_checkin_time    bigint(13)      not null            comment '上次检查时间',
    checkin_interval     bigint(13)      not null            comment '检查间隔时间',
    primary key (sched_name, instance_name)
) engine=innodb comment = '调度器状态表';

-- ----------------------------
-- 10、 存储程序的悲观锁的信息(假如使用了悲观锁)
-- ---------------------------- 
create table QRTZ_LOCKS (
    sched_name           varchar(120)    not null            comment '调度名称',
    lock_name            varchar(40)     not null            comment '悲观锁名称',
    primary key (sched_name, lock_name)
) engine=innodb comment = '存储的悲观锁信息表';

-- ----------------------------
-- 11、 Quartz集群实现同步机制的行锁表
-- ---------------------------- 
create table QRTZ_SIMPROP_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    str_prop_1           varchar(512)    null                comment 'String类型的trigger的第一个参数',
    str_prop_2           varchar(512)    null                comment 'String类型的trigger的第二个参数',
    str_prop_3           varchar(512)    null                comment 'String类型的trigger的第三个参数',
    int_prop_1           int             null                comment 'int类型的trigger的第一个参数',
    int_prop_2           int             null                comment 'int类型的trigger的第二个参数',
    long_prop_1          bigint          null                comment 'long类型的trigger的第一个参数',
    long_prop_2          bigint          null                comment 'long类型的trigger的第二个参数',
    dec_prop_1           numeric(13,4)   null                comment 'decimal类型的trigger的第一个参数',
    dec_prop_2           numeric(13,4)   null                comment 'decimal类型的trigger的第二个参数',
    bool_prop_1          varchar(1)      null                comment 'Boolean类型的trigger的第一个参数',
    bool_prop_2          varchar(1)      null                comment 'Boolean类型的trigger的第二个参数',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = '同步机制的行锁表';

commit;

-- ============================================================================
-- SECTION 3 · kf_01_schema · 客服业务表结构（8 张 kf_* 表）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：客服业务表结构
-- 字符集：utf8mb4 / utf8mb4_general_ci   引擎：InnoDB
-- 约束：单商户，无 tenant_id；无外键约束（应用层维护）；不使用多语言/复杂审批字段
-- ----------------------------------------------------------------------------
-- ----------------------------
-- 1、客服账号表（独立于 sys_user，管理员仍用 sys_user）
-- ----------------------------
drop table if exists kf_agent;
create table kf_agent (
  agent_id        bigint(20)     not null auto_increment    comment '客服ID',
  agent_no        varchar(30)    not null                   comment '客服登录账号',
  password        varchar(100)   not null default ''        comment '登录密码（BCrypt）',
  nick_name       varchar(30)    not null default ''        comment '客服昵称（访客可见）',
  avatar          varchar(255)   default ''                 comment '头像地址',
  phone           varchar(11)    default ''                 comment '手机号码',
  email           varchar(50)    default ''                 comment '邮箱',
  online_status   char(1)        default '0'                comment '在线状态（0离线 1在线 2忙碌）',
  accept_max      int            default 5                  comment '最大并发接待会话数',
  current_load    int            default 0                  comment '当前接待中会话数',
  welcome_text    varchar(500)   default null               comment '个人欢迎语，为空则使用全局欢迎语',
  status          char(1)        default '0'                comment '账号状态（0正常 1停用）',
  del_flag        char(1)        default '0'                comment '删除标志（0代表存在 2代表删除）',
  login_ip        varchar(50)    default ''                 comment '最后登录IP',
  login_date      datetime                                  comment '最后登录时间',
  create_by       varchar(64)    default ''                 comment '创建者',
  create_time     datetime                                  comment '创建时间',
  update_by       varchar(64)    default ''                 comment '更新者',
  update_time     datetime                                  comment '更新时间',
  remark          varchar(500)   default null               comment '备注',
  primary key (agent_id),
  unique key uk_agent_no (agent_no),
  key idx_status_online (status, online_status)
) engine=innodb auto_increment=100 default charset=utf8mb4 collate=utf8mb4_general_ci comment='客服账号表';

-- ----------------------------
-- 2、访客表（匿名访客，visitor_no 由前端生成并持久化）
-- ----------------------------
drop table if exists kf_visitor;
create table kf_visitor (
  visitor_id      bigint(20)     not null auto_increment    comment '访客ID',
  visitor_no      varchar(64)    not null                   comment '访客唯一标识（前端 uuid）',
  nickname        varchar(50)    default ''                 comment '访客昵称',
  ip              varchar(50)    default ''                 comment '最近访问IP',
  user_agent      varchar(512)   default ''                 comment '浏览器信息',
  referrer        varchar(255)   default ''                 comment '来源页面',
  source          varchar(30)    default 'web'              comment '来源渠道标识',
  contact_phone   varchar(11)    default ''                 comment '联系方式-手机',
  contact_email   varchar(50)    default ''                 comment '联系方式-邮箱',
  session_count   int            default 0                  comment '累计会话数',
  last_visit_time datetime                                  comment '最近访问时间',
  create_by       varchar(64)    default ''                 comment '创建者',
  create_time     datetime                                  comment '创建时间',
  update_by       varchar(64)    default ''                 comment '更新者',
  update_time     datetime                                  comment '更新时间',
  remark          varchar(500)   default null               comment '备注',
  primary key (visitor_id),
  unique key uk_visitor_no (visitor_no),
  key idx_last_visit (last_visit_time)
) engine=innodb auto_increment=1000 default charset=utf8mb4 collate=utf8mb4_general_ci comment='访客表';

-- ----------------------------
-- 3、会话表
-- ----------------------------
drop table if exists kf_session;
create table kf_session (
  session_id      bigint(20)     not null auto_increment    comment '会话ID',
  session_no      varchar(32)    not null                   comment '会话编号',
  visitor_id      bigint(20)     not null                   comment '访客ID',
  agent_id        bigint(20)     default null               comment '接待客服ID（空=未分配）',
  status          char(1)        not null default '0'       comment '会话状态（0排队中 1接待中 2已结束）',
  queue_time      datetime                                  comment '进入排队时间',
  assign_time     datetime                                  comment '分配客服时间',
  first_resp_time datetime                                  comment '客服首次响应时间',
  end_time        datetime                                  comment '结束时间',
  end_by          char(1)        default null               comment '结束方（1访客 2客服 3系统超时）',
  close_reason    varchar(200)   default null               comment '结束原因',
  from_agent_id   bigint(20)     default null               comment '最近一次转接来源客服ID',
  unread_visitor  int            default 0                  comment '访客未读消息数',
  unread_agent    int            default 0                  comment '客服未读消息数',
  last_seq        int            default 0                  comment '会话内已分配的最大消息序号',
  last_msg_time   datetime                                  comment '最后一条消息时间',
  source_page     varchar(255)   default ''                 comment '访客发起页面',
  create_by       varchar(64)    default ''                 comment '创建者',
  create_time     datetime                                  comment '创建时间',
  update_by       varchar(64)    default ''                 comment '更新者',
  update_time     datetime                                  comment '更新时间',
  remark          varchar(500)   default null               comment '备注',
  primary key (session_id),
  unique key uk_session_no (session_no),
  key idx_agent_status (agent_id, status),
  key idx_status_queue (status, queue_time),
  key idx_visitor_last (visitor_id, last_msg_time),
  key idx_status_last (status, last_msg_time)
) engine=innodb auto_increment=10000 default charset=utf8mb4 collate=utf8mb4_general_ci comment='客服会话表';

-- ----------------------------
-- 4、消息表
-- ----------------------------
drop table if exists kf_message;
create table kf_message (
  msg_id          bigint(20)     not null auto_increment    comment '消息ID',
  session_id      bigint(20)     not null                   comment '所属会话ID',
  seq             int            not null                   comment '会话内递增序号',
  client_msg_id   varchar(64)    default null               comment '客户端幂等标识',
  sender_type     char(1)        not null                   comment '发送方类型（1访客 2客服 3系统）',
  sender_id       bigint(20)     default null               comment '发送方ID（访客ID/客服ID，系统消息为空）',
  msg_type        char(1)        not null default '1'       comment '消息类型（1文本 4系统事件）',
  content         text                                      comment '文本内容或系统事件描述',
  deliver_status  char(1)        default '0'                comment '投递状态（0已入库 1已送达 2已读 3失败）',
  read_time       datetime                                  comment '已读时间',
  create_time     datetime                                  comment '创建时间',
  primary key (msg_id),
  unique key uk_session_seq (session_id, seq),
  unique key uk_session_client (session_id, client_msg_id),
  key idx_session_create (session_id, create_time),
  key idx_create_time (create_time)
) engine=innodb auto_increment=100000 default charset=utf8mb4 collate=utf8mb4_general_ci comment='客服消息表';

-- ----------------------------
-- 5、工单表（独立管理，不做复杂工作流）
-- ----------------------------
drop table if exists kf_ticket;
create table kf_ticket (
  ticket_id       bigint(20)     not null auto_increment    comment '工单ID',
  ticket_no       varchar(32)    not null                   comment '工单编号',
  title           varchar(100)   not null                   comment '工单标题',
  content         text                                      comment '工单内容',
  category        varchar(30)    default 'other'            comment '工单分类（字典 kf_ticket_category）',
  priority        char(1)        default '2'                comment '优先级（1低 2中 3高）',
  status          char(1)        not null default '0'       comment '状态（0待处理 1处理中 2已解决 3已关闭）',
  source          char(1)        default '3'                comment '来源（1会话内创建 2离线留言转入 3后台手动）',
  visitor_id      bigint(20)     default null               comment '关联访客ID',
  session_id      bigint(20)     default null               comment '关联会话ID',
  contact         varchar(50)    default ''                 comment '访客联系方式',
  creator_type    char(1)        default '1'                comment '创建人类型（1管理员 2客服 3系统）',
  creator_id      bigint(20)     default null               comment '创建人ID',
  handler_agent_id bigint(20)    default null               comment '处理客服ID',
  finish_time     datetime                                  comment '解决时间',
  close_time      datetime                                  comment '关闭时间',
  create_by       varchar(64)    default ''                 comment '创建者',
  create_time     datetime                                  comment '创建时间',
  update_by       varchar(64)    default ''                 comment '更新者',
  update_time     datetime                                  comment '更新时间',
  remark          varchar(500)   default null               comment '备注',
  primary key (ticket_id),
  unique key uk_ticket_no (ticket_no),
  key idx_status_create (status, create_time),
  key idx_handler_status (handler_agent_id, status),
  key idx_visitor (visitor_id),
  key idx_category_status (category, status)
) engine=innodb auto_increment=1000 default charset=utf8mb4 collate=utf8mb4_general_ci comment='客服工单表';

-- ----------------------------
-- 6、工单回复/流转记录表（一张表承载回复、状态变更、内部备注）
-- ----------------------------
drop table if exists kf_ticket_reply;
create table kf_ticket_reply (
  reply_id        bigint(20)     not null auto_increment    comment '回复ID',
  ticket_id       bigint(20)     not null                   comment '工单ID',
  reply_type      char(1)        not null default '1'       comment '类型（1对外回复 2状态变更 3内部备注）',
  content         varchar(2000)  default ''                 comment '回复/备注内容',
  from_status     char(1)        default null               comment '变更前状态',
  to_status       char(1)        default null               comment '变更后状态',
  operator_type   char(1)        default '1'                comment '操作人类型（1管理员 2客服 3访客）',
  operator_id     bigint(20)     default null               comment '操作人ID',
  operator_name   varchar(50)    default ''                 comment '操作人名称（冗余展示用）',
  create_time     datetime                                  comment '创建时间',
  primary key (reply_id),
  key idx_ticket_create (ticket_id, create_time)
) engine=innodb auto_increment=1000 default charset=utf8mb4 collate=utf8mb4_general_ci comment='工单回复与流转记录表';

-- ----------------------------
-- 10、客服业务参数表（与若依 sys_config 分离，专管客服域配置）
-- ----------------------------
drop table if exists kf_config;
create table kf_config (
  config_id       bigint(20)     not null auto_increment    comment '参数ID',
  config_key      varchar(100)   not null                   comment '参数键名',
  config_value    varchar(2000)  default ''                 comment '参数键值',
  config_name     varchar(100)   not null default ''        comment '参数名称',
  config_type     char(1)        default '0'                comment '系统内置（0是 1否）',
  create_by       varchar(64)    default ''                 comment '创建者',
  create_time     datetime                                  comment '创建时间',
  update_by       varchar(64)    default ''                 comment '更新者',
  update_time     datetime                                  comment '更新时间',
  remark          varchar(500)   default null               comment '备注',
  primary key (config_id),
  unique key uk_config_key (config_key)
) engine=innodb auto_increment=10 default charset=utf8mb4 collate=utf8mb4_general_ci comment='客服业务参数表';

-- ----------------------------
-- 11、离线留言表（客服全部离线时访客留言，可转工单）
-- ----------------------------
drop table if exists kf_offline_message;
create table kf_offline_message (
  msg_id          bigint(20)     not null auto_increment    comment '留言ID',
  visitor_id      bigint(20)     not null                   comment '访客ID',
  session_id      bigint(20)     default null               comment '关联会话ID',
  contact         varchar(50)    default ''                 comment '联系方式',
  content         varchar(2000)  not null                   comment '留言内容',
  status          char(1)        not null default '0'       comment '状态（0待处理 1已转工单 2已关闭）',
  ticket_id       bigint(20)     default null               comment '转入的工单ID',
  handler_agent_id bigint(20)    default null               comment '处理客服ID',
  create_time     datetime                                  comment '创建时间',
  update_time     datetime                                  comment '更新时间',
  primary key (msg_id),
  key idx_status_create (status, create_time),
  key idx_visitor (visitor_id)
) engine=innodb auto_increment=100 default charset=utf8mb4 collate=utf8mb4_general_ci comment='离线留言表';

-- ============================================================================
-- SECTION 4 · kf_02_init_data · 基础数据：示例客服账号与业务参数
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：阶段1 基础数据初始化

-- 说明：管理员沿用若依 sys_user（admin / admin123）；示例客服密码统一 admin123
-- ----------------------------------------------------------------------------
-- ----------------------------
-- 示例客服账号（密码均为 admin123，BCrypt）
-- ----------------------------
insert into kf_agent (agent_no, password, nick_name, phone, email, online_status, accept_max, status, create_by, create_time, remark) values
('kefu01', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '小张', '13800000001', 'kefu01@voxio.com', '0', 5, '0', 'admin', sysdate(), '示例客服-售前咨询'),
('kefu02', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '小李', '13800000002', 'kefu02@voxio.com', '0', 5, '0', 'admin', sysdate(), '示例客服-售后支持'),
('kefu03', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '小王', '13800000003', 'kefu03@voxio.com', '0', 3, '1', 'admin', sysdate(), '示例客服-已停用（用于停用态联调）');

-- ----------------------------
-- 客服业务参数（内置项）
-- ----------------------------
insert into kf_config (config_id, config_key, config_value, config_name, config_type, create_by, create_time, remark) values
(1, 'kf.welcome.text', '您好，请描述您的问题', '访客进入欢迎语', '0', 'admin', sysdate(), '会话建立/人工客服接入时下发的第一句话'),
(2, 'kf.queue.tip', '当前客服都在接待中，您排在第 {position} 位，请稍候。', '排队提示语', '0', 'admin', sysdate(), '{position} 会被替换为实时排队位置（第 1 位表示前面无人等待）'),
(3, 'kf.agent.offline.tip', '当前客服不在线，您可以留言或提交工单，我们会尽快联系您。', '无客服在线提示语', '0', 'admin', sysdate(), '全部客服离线时下发'),
(4, 'kf.session.timeout.minutes', '30', '会话无活动自动结束（分钟）', '0', 'admin', sysdate(), '超过该分钟数无消息即由系统自动结束（end_by=3），0 表示不自动结束'),
(8, 'kf.rate.limit.perMinute', '20', '访客每分钟消息上限', '0', 'admin', sysdate(), '网关连接鉴权时读取；无值时回退 kf.gateway.visitor-rate-per-minute'),
(9, 'kf.openapi.secret', 'voxio-open-please-change-me', '访客端签名密钥（预留，未启用）', '0', 'admin', sysdate(), '访客侧已改用访客令牌鉴权，当前版本不做时间戳签名；本参数不下发到 /kf/open/config'),
(10, 'kf.assign.strategy', 'least_load', '会话分配策略（固定空闲优先）', '0', 'admin', sysdate(), '当前版本固定按 current_load 最小优先，未实现轮询等多策略切换');

-- ============================================================================
-- SECTION 5 · kf_03_menu · 客服管理菜单与按钮权限
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：管理后台菜单与权限初始化

-- 说明：菜单 ID 固定使用 2000-2019 段；本文件可重复执行（先删后插）
-- ----------------------------------------------------------------------------

delete from sys_role_menu where menu_id between 2000 and 2019;
delete from sys_menu where menu_id between 2000 and 2019;

-- 一级目录
-- 超级管理员（role_key=admin / user_id=1）在若依中自动拥有全部菜单，无需授权
insert into sys_menu values('2000', '客服管理', '0', '4', 'kf', null, '', '', 1, 0, 'M', '0', '0', '', 'service', 'admin', sysdate(), '', null, '客服业务目录');

-- 客服账号
insert into sys_menu values('2010', '客服账号', '2000', '1', 'agent', 'kf/agent/index', '', '', 1, 0, 'C', '0', '0', 'kf:agent:list', 'user', 'admin', sysdate(), '', null, '客服账号管理菜单');
insert into sys_menu values('2011', '客服查询', '2010', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:agent:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2012', '客服新增', '2010', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:agent:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2013', '客服修改', '2010', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:agent:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2014', '客服删除', '2010', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:agent:remove',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2015', '重置密码', '2010', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:agent:resetPwd',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2016', '状态变更', '2010', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:agent:changeStatus', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2017', '强制下线', '2010', '7', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:agent:forceOffline', '#', 'admin', sysdate(), '', null, '');

-- 角色：客服主管（可查看客服账号与会话数据，不可维护系统管理）
delete from sys_role_menu where role_id = 3;
delete from sys_role where role_id = 3;
insert into sys_role (role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark) values
(3, '客服主管', 'kf_supervisor', 3, '1', 1, 1, '0', '0', 'admin', sysdate(), '阶段2 内置角色：客服业务只读与账号维护');

insert into sys_role_menu (role_id, menu_id) values
(3, '2000'), (3, '2010'), (3, '2011'), (3, '2012'), (3, '2013'), (3, '2014'), (3, '2015'), (3, '2016'), (3, '2017');

-- 演示账号：客服主管（sys_user + 角色3），密码 admin123，仅拥有客服管理菜单
delete from sys_user_role where user_id = 100;
delete from sys_user where user_id = 100;
insert into sys_user values(100, 103, 'kfleader', '客服主管', '00', 'kfleader@voxio.com', '15800000000', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '阶段2 演示：客服主管，仅客服管理权限');
insert into sys_user_role values ('100', '3');

-- ============================================================================
-- SECTION 6 · kf_04_dict · 客服业务字典（在线状态/会话状态/工单状态与优先级等）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：客服业务字典初始化

-- 说明：可重复执行（先删后插）。账号启停复用若依 sys_normal_disable，不重复建字典
-- ----------------------------------------------------------------------------

delete from sys_dict_data where dict_type in ('kf_online_status', 'kf_session_status', 'kf_msg_type', 'kf_sender_type', 'kf_deliver_status');
delete from sys_dict_type where dict_type in ('kf_online_status', 'kf_session_status', 'kf_msg_type', 'kf_sender_type', 'kf_deliver_status');

insert into sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) values
(120, '客服在线状态', 'kf_online_status',   '0', 'admin', sysdate(), '客服在线/忙碌/离线状态'),
(121, '会话状态',     'kf_session_status',  '0', 'admin', sysdate(), '会话排队/接待/结束状态'),
(122, '消息类型',     'kf_msg_type',        '0', 'admin', sysdate(), '聊天消息类型'),
(123, '消息发送方',   'kf_sender_type',     '0', 'admin', sysdate(), '消息发送方类型'),
(124, '消息投递状态', 'kf_deliver_status',  '0', 'admin', sysdate(), '消息投递与已读状态');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values
(1, '离线', '0', 'kf_online_status',  '', 'info',    'Y', '0', 'admin', sysdate(), '客服离线，不参与分配'),
(2, '在线', '1', 'kf_online_status',  '', 'success', 'N', '0', 'admin', sysdate(), '客服在线，可被分配会话'),
(3, '忙碌', '2', 'kf_online_status',  '', 'warning', 'N', '0', 'admin', sysdate(), '客服忙碌，达到接待上限时不分配'),
(1, '排队中', '0', 'kf_session_status', '', 'warning', 'Y', '0', 'admin', sysdate(), '等待分配客服'),
(2, '接待中', '1', 'kf_session_status', '', 'primary', 'N', '0', 'admin', sysdate(), '客服接待中'),
(3, '已结束', '2', 'kf_session_status', '', 'info',    'N', '0', 'admin', sysdate(), '会话已结束'),
(1, '文本', '1', 'kf_msg_type', '', 'default', 'Y', '0', 'admin', sysdate(), '文本消息'),
(4, '系统', '4', 'kf_msg_type', '', 'info',    'N', '0', 'admin', sysdate(), '系统事件提示'),
(1, '访客', '1', 'kf_sender_type', '', 'default', 'Y', '0', 'admin', sysdate(), '访客发送'),
(2, '客服', '2', 'kf_sender_type', '', 'primary', 'N', '0', 'admin', sysdate(), '客服发送'),
(3, '系统', '3', 'kf_sender_type', '', 'info',    'N', '0', 'admin', sysdate(), '系统消息'),
(1, '已入库', '0', 'kf_deliver_status', '', 'info',    'Y', '0', 'admin', sysdate(), '已落库，尚未送达'),
(2, '已送达', '1', 'kf_deliver_status', '', 'primary', 'N', '0', 'admin', sysdate(), '对端已收到'),
(3, '已读',   '2', 'kf_deliver_status', '', 'success', 'N', '0', 'admin', sysdate(), '对端已读'),
(4, '失败',   '3', 'kf_deliver_status', '', 'danger',  'N', '0', 'admin', sysdate(), '投递失败');

-- ============================================================================
-- SECTION 7 · kf_05_menu_config · 客服参数配置菜单与权限
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：阶段2.2 配置类业务菜单与权限

-- 说明：菜单 ID 使用 2020-2069 段；可重复执行（先删后插）
-- ----------------------------------------------------------------------------

delete from sys_role_menu where menu_id between 2020 and 2069;
delete from sys_menu where menu_id between 2020 and 2069;

-- 参数配置
insert into sys_menu values('2060', '参数配置', '2000', '6', 'config', 'kf/config/index', '', '', 1, 0, 'C', '0', '0', 'kf:config:list', 'edit', 'admin', sysdate(), '', null, '客服业务参数配置');
insert into sys_menu values('2061', '参数查询', '2060', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:config:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2062', '参数新增', '2060', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:config:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2063', '参数修改', '2060', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:config:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2064', '参数删除', '2060', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:config:remove', '#', 'admin', sysdate(), '', null, '');

-- 客服主管角色补充授权（总览 + 配置维护，不含删除）
insert into sys_role_menu (role_id, menu_id) values
(3, '2060'), (3, '2061'), (3, '2063');

-- ============================================================================
-- SECTION 8 · kf_06_config_patch · 业务参数补丁（文案、开关、上限）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：阶段2.2 新增客服业务参数

-- 说明：可重复执行（按键名先删后插）
-- ----------------------------------------------------------------------------

delete from kf_config where config_key in
('kf.assign.enabled', 'kf.assign.maxPerAgent', 'kf.offline.enabled');

insert into kf_config (config_key, config_value, config_name, config_type, create_by, create_time, remark) values
('kf.assign.enabled', 'true', '会话自动分配开关', '0', 'admin', sysdate(), 'false 时新会话只进排队，由主管手动分配'),
('kf.assign.maxPerAgent', '5', '单个客服默认最大并发会话数', '0', 'admin', sysdate(), '客服个人 kf_agent.accept_max 优先于该默认值'),
('kf.offline.enabled', 'true', '无客服在线时是否允许离线留言', '0', 'admin', sysdate(), '关闭时改为提示访客提交工单');

-- ============================================================================
-- SECTION 9 · kf_07_gateway · 网关链路增量（消息幂等与序列字段、索引）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：阶段3 网关与会话消息链路相关变更

-- 说明：可重复执行（先删后建）
-- ----------------------------------------------------------------------------
-- 消息表补充：未送达消息扫描索引（离线补发与未读统计）
alter table kf_message add index idx_session_deliver (session_id, deliver_status);

-- 访客表补充：最近会话定位（重连恢复会话）
alter table kf_session add index idx_status_agent (status, agent_id);

-- ============================================================================
-- SECTION 10 · kf_08_visitor_config · 访客端增量（离线留言、服务时间与入口配置）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：阶段 4 访客端参数与字典

-- 说明：可重复执行（kf_config 走 uk_config_key 幂等 upsert，字典先删后插）
-- ----------------------------------------------------------------------------

insert into kf_config (config_key, config_name, config_value, config_type, create_by, create_time, remark)
values ('kf.service.enabled', '是否限制服务时间', 'false', '0', 'admin', sysdate(), 'true 时访客在非服务时间只能留言')
on duplicate key update config_name = values(config_name), remark = values(remark);

insert into kf_config (config_key, config_name, config_value, config_type, create_by, create_time, remark)
values ('kf.service.time', '服务时间段', '09:00-18:00', '0', 'admin', sysdate(), '格式 HH:mm-HH:mm，支持跨零点如 20:00-02:00')
on duplicate key update config_name = values(config_name), remark = values(remark);

insert into kf_config (config_key, config_name, config_value, config_type, create_by, create_time, remark)
values ('kf.offline.reply.tip', '留言成功提示语', '留言已提交，客服会尽快通过您留下的联系方式回复您。', '0', 'admin', sysdate(), '访客端留言成功后的回执文案')
on duplicate key update config_name = values(config_name), remark = values(remark);

delete from sys_dict_data where dict_type = 'kf_offline_status';
delete from sys_dict_type where dict_type = 'kf_offline_status';

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
values ('留言处理状态', 'kf_offline_status', '0', 'admin', sysdate(), '管理后台离线留言处理状态');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values
(1, '待处理',   '0', 'kf_offline_status', '', 'warning', 'Y', '0', 'admin', sysdate(), '访客刚提交的留言'),
(2, '已转工单', '1', 'kf_offline_status', '', 'primary', 'N', '0', 'admin', sysdate(), '已转为工单跟进'),
(3, '已关闭',   '2', 'kf_offline_status', '', 'info',    'N', '0', 'admin', sysdate(), '无需跟进');

-- ============================================================================
-- SECTION 11 · kf_09_ticket_dict · 工单字典（分类/状态/优先级）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：工单模块所需的下拉字典（分类/状态/优先级）

-- 说明：可重复执行（先删后插）。工单表与流转记录表在阶段 1 已建，本阶段只补下拉字典
-- ----------------------------------------------------------------------------

delete from sys_dict_data where dict_type in ('kf_ticket_category', 'kf_ticket_status', 'kf_ticket_priority');
delete from sys_dict_type where dict_type in ('kf_ticket_category', 'kf_ticket_status', 'kf_ticket_priority');

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark) values
('工单分类',   'kf_ticket_category',  '0', 'admin', sysdate(), '客服创建工单时选择'),
('工单状态',   'kf_ticket_status',    '0', 'admin', sysdate(), '待处理/处理中/已解决/已关闭'),
('工单优先级', 'kf_ticket_priority',  '0', 'admin', sysdate(), '低/中/高');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values
(1, '使用咨询', 'consult',   'kf_ticket_category', '', 'primary', 'N', '0', 'admin', sysdate(), '功能与使用类问题'),
(2, '账户问题', 'account',   'kf_ticket_category', '', 'primary', 'N', '0', 'admin', sysdate(), '登录、绑定、实名等'),
(3, '订单物流', 'logistics', 'kf_ticket_category', '', 'success', 'N', '0', 'admin', sysdate(), '下单、发货、物流跟踪'),
(4, '售后退款', 'refund',    'kf_ticket_category', '', 'warning', 'N', '0', 'admin', sysdate(), '退换货与退款'),
(5, '投诉建议', 'complaint', 'kf_ticket_category', '', 'danger',  'N', '0', 'admin', sysdate(), '投诉与建议'),
(6, '其他',     'other',     'kf_ticket_category', '', 'info',    'Y', '0', 'admin', sysdate(), '未归类问题'),

(1, '待处理', '0', 'kf_ticket_status', '', 'warning', 'Y', '0', 'admin', sysdate(), '新建工单默认状态'),
(2, '处理中', '1', 'kf_ticket_status', '', 'primary', 'N', '0', 'admin', sysdate(), '客服跟进中'),
(3, '已解决', '2', 'kf_ticket_status', '', 'success', 'N', '0', 'admin', sysdate(), '已给出解决方案'),
(4, '已关闭', '3', 'kf_ticket_status', '', 'info',    'N', '0', 'admin', sysdate(), '终态，不可再流转'),

(1, '低', '1', 'kf_ticket_priority', '', 'info',    'N', '0', 'admin', sysdate(), '可延后处理'),
(2, '中', '2', 'kf_ticket_priority', '', 'warning', 'Y', '0', 'admin', sysdate(), '默认优先级'),
(3, '高', '3', 'kf_ticket_priority', '', 'danger',  'N', '0', 'admin', sysdate(), '需优先响应');

-- ============================================================================
-- SECTION 12 · kf_10_admin · 管理后台业务增量（监控/工单/留言菜单与权限）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：阶段6 管理后台业务完善与统计

-- 说明：菜单 ID 使用 2070-2149 段；可重复执行（先删后插）
-- ----------------------------------------------------------------------------

delete from sys_role_menu where menu_id between 2070 and 2149;
delete from sys_menu where menu_id between 2070 and 2149;

-- 会话监控（管理员只读）
insert into sys_menu values('2070', '会话监控', '2000', '7', 'monitor', 'kf/monitor/index', '', '', 1, 0, 'C', '0', '0', 'kf:monitor:list', 'eye-open', 'admin', sysdate(), '', null, '进行中会话与历史会话只读监控');
insert into sys_menu values('2071', '监控查询', '2070', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:monitor:query',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2072', '会话详情', '2070', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:monitor:detail',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2073', '人工分配', '2070', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:monitor:assign',  '#', 'admin', sysdate(), '', null, '');

-- 工单管理
insert into sys_menu values('2090', '工单管理', '2000', '9', 'ticket', 'kf/ticket/index', '', '', 1, 0, 'C', '0', '0', 'kf:ticket:list', 'form', 'admin', sysdate(), '', null, '工单独立管理与简单状态流转');
insert into sys_menu values('2091', '工单查询', '2090', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:ticket:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2092', '工单新增', '2090', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:ticket:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2093', '工单修改', '2090', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:ticket:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2094', '工单流转', '2090', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:ticket:status', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2095', '工单删除', '2090', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:ticket:remove', '#', 'admin', sysdate(), '', null, '');

-- 离线留言处理
insert into sys_menu values('2110', '离线留言', '2000', '11', 'offline', 'kf/offline/index', '', '', 1, 0, 'C', '0', '0', 'kf:offline:list', 'message', 'admin', sysdate(), '', null, '离线留言查看、转工单与关闭');
insert into sys_menu values('2111', '留言查询', '2110', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:offline:query',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2112', '转为工单', '2110', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:offline:toticket',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2113', '关闭留言', '2110', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:offline:close',     '#', 'admin', sysdate(), '', null, '');

-- 业务设置（单商户集中配置，管理员专属）
insert into sys_menu values('2130', '业务设置', '2000', '13', 'settings', 'kf/settings/index', '', '', 1, 0, 'C', '0', '0', 'kf:settings:list', 'edit', 'admin', sysdate(), '', null, '欢迎语/离线留言/自动分配/最大会话数/评价开关');
insert into sys_menu values('2131', '设置修改', '2130', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:settings:edit', '#', 'admin', sysdate(), '', null, '');

-- 角色 3（客服主管）：客服业务全部菜单，业务设置保留给管理员，用于验证权限边界
insert into sys_role_menu values('3', '2070'), ('3', '2071'), ('3', '2072'), ('3', '2073');
insert into sys_role_menu values('3', '2090'), ('3', '2091'), ('3', '2092'), ('3', '2093'), ('3', '2094'), ('3', '2095');
insert into sys_role_menu values('3', '2110'), ('3', '2111'), ('3', '2112'), ('3', '2113');

-- ----------------------------------------------------------------------------
-- 字典：工单来源、会话结束方（管理后台列表展示用）
-- ----------------------------------------------------------------------------
delete from sys_dict_data where dict_type in ('kf_ticket_source', 'kf_session_end_by');
delete from sys_dict_type where dict_type in ('kf_ticket_source', 'kf_session_end_by');

insert into sys_dict_type values(130, '工单来源', 'kf_ticket_source', '0', 'admin', sysdate(), '', null, '1会话内创建 2离线留言转入 3后台手动');
insert into sys_dict_data values(260, 1, '会话内创建', '1', 'kf_ticket_source', '', 'primary', 'N', '0', 'admin', sysdate(), '', null, '');
insert into sys_dict_data values(261, 2, '离线留言转入', '2', 'kf_ticket_source', '', 'warning', 'N', '0', 'admin', sysdate(), '', null, '');
insert into sys_dict_data values(262, 3, '后台手动', '3', 'kf_ticket_source', '', 'info', 'N', '0', 'admin', sysdate(), '', null, '');

insert into sys_dict_type values(131, '会话结束方', 'kf_session_end_by', '0', 'admin', sysdate(), '', null, '1访客 2客服 3系统');
insert into sys_dict_data values(263, 1, '访客结束', '1', 'kf_session_end_by', '', 'info', 'N', '0', 'admin', sysdate(), '', null, '');
insert into sys_dict_data values(264, 2, '客服结束', '2', 'kf_session_end_by', '', 'primary', 'N', '0', 'admin', sysdate(), '', null, '');
insert into sys_dict_data values(265, 3, '系统结束', '3', 'kf_session_end_by', '', 'warning', 'N', '0', 'admin', sysdate(), '', null, '');

-- ----------------------------------------------------------------------------
-- 查询索引：聊天记录与历史会话按时间/客服维度筛选
-- ----------------------------------------------------------------------------
set @exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'kf_session' and index_name = 'idx_create_time');
set @sql = if(@exists = 0, 'alter table kf_session add index idx_create_time (create_time)', 'select 1');
prepare s from @sql; execute s; drop prepare s;

set @exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'kf_message' and index_name = 'idx_sender_time');
set @sql = if(@exists = 0, 'alter table kf_message add index idx_sender_time (sender_type, sender_id, create_time)', 'select 1');
prepare s from @sql; execute s; drop prepare s;

-- ============================================================================
-- SECTION 13 · kf_11_menu_ia · 信息架构规整（一级菜单固定、非运营菜单隐藏、主管补授）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：阶段8 菜单信息架构规整（UI/UX 打磨）

-- 说明：
--   1) 沿用若依权限体系，仅调整 sys_menu 的 parent_id / order_num / visible / path / icon / menu_name，
--      不新增权限点、不改 perms、不重写权限引擎；
--   2) 非运营菜单（系统工具、系统监控、日志、菜单/部门/岗位/参数/通知/字典）用 visible='1' 隐藏，
--      路由与权限仍然保留，便于运维临时访问，不删除数据；
--   3) 一级菜单固定顺序：总览、客服管理、会话管理、工单管理、内容管理、统计报表、系统设置；
--   4) 本文件可重复执行（先删后插新增目录，更新语句天然幂等）。
-- ----------------------------------------------------------------------------
-- ==================== 1. 新增一级目录（3000 段） ====================
delete from sys_role_menu where menu_id between 3000 and 3039;
delete from sys_menu where menu_id between 3000 and 3039;

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values
(3000, '会话管理', 0, 3, 'conversation', null, '', '', 1, 0, 'M', '0', '0', '', 'message', 'admin', sysdate(), '阶段8：会话监控/聊天记录/离线留言'),
(3010, '工单管理', 0, 4, 'ticket',      null, '', '', 1, 0, 'M', '0', '0', '', 'clipboard', 'admin', sysdate(), '阶段8：客服工单');

-- 客服主管角色补充新增目录授权（子菜单与按钮沿用 kf_03/kf_05 已有授权）
insert into sys_role_menu (role_id, menu_id) values
(3, '3000'), (3, '3010');

-- 客服参数（2060）迁到「系统设置」后，需同时授予该目录，否则主管侧栏看不到参数页
-- （用户管理/角色管理/业务设置未授予，仍然不可见）
insert ignore into sys_role_menu (role_id, menu_id) values (3, '1');

-- ==================== 3. 客服管理（order 2）：客服账号 ====================
update sys_menu set order_num = 2, menu_name = '客服管理', icon = 'people', update_by = 'admin', update_time = sysdate()
 where menu_id = 2000;

update sys_menu set parent_id = 2000, order_num = 1, icon = 'user', update_by = 'admin', update_time = sysdate()
 where menu_id = 2010;

-- ==================== 4. 会话管理（order 3）：会话监控 + 离线留言 ====================
update sys_menu set parent_id = 3000, order_num = 1, menu_name = '会话监控', icon = 'eye-open', update_by = 'admin', update_time = sysdate()
 where menu_id = 2070;

update sys_menu set parent_id = 3000, order_num = 3, menu_name = '离线留言', icon = 'form', update_by = 'admin', update_time = sysdate()
 where menu_id = 2110;

-- ==================== 5. 工单管理（order 4）：工单处理 ====================
update sys_menu set parent_id = 3010, order_num = 1, path = 'work', menu_name = '工单处理', icon = 'clipboard', update_by = 'admin', update_time = sysdate()
 where menu_id = 2090;

-- ==================== 8. 系统设置（order 7）：仅保留运营必需项 ====================
update sys_menu set order_num = 7, menu_name = '系统设置', icon = 'system', update_by = 'admin', update_time = sysdate()
 where menu_id = 1;

update sys_menu set order_num = 1, visible = '0', icon = 'user', update_by = 'admin', update_time = sysdate()
 where menu_id = 100;

update sys_menu set order_num = 2, visible = '0', icon = 'peoples', update_by = 'admin', update_time = sysdate()
 where menu_id = 101;

-- 客服业务参数与业务设置归入系统设置，路径改名避免与 sys_config 参数设置(/system/config)冲突
update sys_menu set parent_id = 1, order_num = 3, path = 'kfConfig', menu_name = '客服参数', icon = 'edit', update_by = 'admin', update_time = sysdate()
 where menu_id = 2060;

update sys_menu set parent_id = 1, order_num = 4, path = 'kfSettings', menu_name = '业务设置', icon = 'skill', update_by = 'admin', update_time = sysdate()
 where menu_id = 2130;

-- ==================== 9. 隐藏非运营菜单（保留路由与权限，仅不显示） ====================
-- 系统管理下的非运营项：菜单/部门/岗位/字典/参数设置/通知公告/日志管理
update sys_menu set visible = '1', update_by = 'admin', update_time = sysdate()
 where menu_id in (102, 103, 104, 105, 106, 107, 108);

-- 日志管理子菜单（操作日志/登录日志）
update sys_menu set visible = '1', update_by = 'admin', update_time = sysdate()
 where menu_id in (500, 501);

-- 系统监控整块（在线用户/定时任务/数据监控/服务监控/缓存监控/缓存列表）
update sys_menu set visible = '1', update_by = 'admin', update_time = sysdate()
 where menu_id in (2, 109, 110, 111, 112, 113, 114);

-- 系统工具（表单构建/系统接口）
update sys_menu set visible = '1', update_by = 'admin', update_time = sysdate()
 where menu_id in (3, 115, 117);

-- ============================================================================
-- SECTION 14 · kf_12_gap_closure · 功能遗漏收口（人工分配权限、参数口径标注）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：功能遗漏收口（阶段8 之后）

-- 说明：
--   1) 「会话监控」补一个按钮权限 kf:monitor:assign：业务设置里关闭自动分配后，
--      新会话只进排队，必须由主管在会话监控人工分配，否则访客会无限等待；
--      人工分配只针对排队中（status=0）的会话，接待中的会话仍然不改派。
--   2) 两个参数没有消费方（访客侧鉴权已改为访客令牌、分配策略固定为空闲优先），
--      把名称与备注改成「预留」口径，避免运营以为改了就会生效。
--   3) 三个现在真正生效的参数补一句来源说明。
--   4) 可重复执行。
-- ----------------------------------------------------------------------------

delete from sys_role_menu where menu_id = 2073;
delete from sys_menu where menu_id = 2073;

insert into sys_menu values('2073', '人工分配', '2070', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'kf:monitor:assign', '#', 'admin', sysdate(), '', null, '把排队中的会话人工分配给在线/忙碌客服');

-- 客服主管需要能人工分配（admin 自动拥有全部菜单）
insert into sys_role_menu values('3', '2073');

-- ==================== 无消费方参数改为「预留」口径 ====================
update kf_config set config_name = '访客端签名密钥（预留，未启用）',
       remark = '访客侧已改用访客令牌鉴权（阶段4 变更），当前版本不做时间戳签名，本参数保留但未使用；仍不下发到 /kf/open/config',
       update_by = 'admin', update_time = sysdate()
 where config_key = 'kf.openapi.secret';

update kf_config set config_name = '会话分配策略（固定空闲优先）',
       remark = '当前版本固定按 current_load 最小优先分配，未实现轮询等多策略切换，本参数保留但未使用',
       update_by = 'admin', update_time = sysdate()
 where config_key = 'kf.assign.strategy';

-- ==================== 现在真正生效的参数，写明读取方 ====================
update kf_config set remark = '关闭后新会话只进排队，由主管在「会话监控 > 人工分配」指定客服',
       update_by = 'admin', update_time = sysdate()
 where config_key = 'kf.assign.enabled';

update kf_config set remark = '访客每分钟消息上限，网关在连接鉴权时读取本参数；库内无值时回退 kf.gateway.visitor-rate-per-minute',
       update_by = 'admin', update_time = sysdate()
 where config_key = 'kf.rate.limit.perMinute';

update kf_config set remark = '进行中会话超过该分钟数没有任何消息即由系统自动结束（end_by=3），0 表示不自动结束',
       update_by = 'admin', update_time = sysdate()
 where config_key = 'kf.session.timeout.minutes';

-- ============================================================================
-- SECTION 15 · kf_13_ticket_push · 工单第三方手动同步（留痕三列 + 四个参数）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：工单第三方推送（阶段9）

-- 说明：
--   1) 只做「手动一键同步」：工单详情点一下把当前工单推给第三方，成功/失败就地留痕，
--      不建重试队列、不引消息中间件（单商户规模下重试只会带来不可控的重复推送）。
--   2) 推送结果落在 kf_ticket 自身三列，而不是新建 kf_ticket_push_log：
--      一个工单只关心「最后一次推送」的状态，历史上一次的结果对客服与主管都没有操作价值，
--      多一张表就要多一套清理与联表逻辑，收益不匹配。
--   3) 四个参数走 kf_config（业务设置页维护），密钥类内容只存鉴权头字符串，
--      不在 /kf/open/config 白名单内，访客端登录前读不到。
--   4) 可重复执行：列存在则跳过 ALTER，参数先删后插。
-- ----------------------------------------------------------------------------
-- ==================== kf_ticket 推送留痕三列 ====================
set @ddl = (select if(count(*) = 0,
  'alter table kf_ticket add column push_status char(1) default ''0'' comment ''第三方推送状态（0未推送 1已推送 2推送失败）'' after close_time',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_ticket' and column_name = 'push_status');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = (select if(count(*) = 0,
  'alter table kf_ticket add column push_time datetime default null comment ''最近一次推送时间'' after push_status',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_ticket' and column_name = 'push_time');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = (select if(count(*) = 0,
  'alter table kf_ticket add column push_response varchar(500) default null comment ''最近一次推送响应摘要（状态码与返回体前 200 字）'' after push_time',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_ticket' and column_name = 'push_response');
prepare s from @ddl; execute s; deallocate prepare s;

-- 推送状态检索（工单列表按「未推送/失败」筛选）
set @ddl = (select if(count(*) = 0,
  'alter table kf_ticket add key idx_push_status (push_status)',
  'do 0') from information_schema.statistics where table_schema = database() and table_name = 'kf_ticket' and index_name = 'idx_push_status');
prepare s from @ddl; execute s; deallocate prepare s;

-- ==================== 推送参数（业务设置 > 工单第三方推送） ====================
delete from kf_config where config_key in ('kf.ticket.push.enabled', 'kf.ticket.push.url', 'kf.ticket.push.headers', 'kf.ticket.push.timeoutSeconds');

insert into kf_config (config_key, config_value, config_name, config_type, create_by, create_time, remark) values
('kf.ticket.push.enabled', 'false', '工单第三方推送开关', '0', 'admin', sysdate(), '关闭时工单详情的「同步」按钮直接提示未启用，不发起任何外呼'),
('kf.ticket.push.url', '', '工单推送地址', '0', 'admin', sysdate(), '完整 http(s) URL，由第三方系统提供；本系统按 JSON POST 当前工单，不做字段映射'),
('kf.ticket.push.headers', '', '推送鉴权头', '0', 'admin', sysdate(), '形如 Authorization=Bearer xxx，多个用英文分号分隔；仅管理后台可见，不下发访客端'),
('kf.ticket.push.timeoutSeconds', '5', '推送超时（秒）', '0', 'admin', sysdate(), '单次同步的读写超时，超时即记为推送失败，不重试');

-- ============================================================================
-- SECTION 16 · kf_14_security · 国密安全策略参数（阶段10.1）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：等保三级加密改造的策略开关表

-- 说明：
--   1) 与 kf_config 分开：kf_config 的键会被 /kf/open/config 白名单挑出下发给访客端，
--      加密开关、密钥版本、登录锁定阈值这类安全参数绝不能走那条路，否则等于把
--      「生产是否明文运行」暴露给外部并可被猜测。
--   2) encrypt.enabled 只在对 voxio.crypto.enforce=false（开发机）时才生效；
--      交付环境强制加密，表里写 false 也不会退化成明文。
--   3) 建表与插入都做成幂等，已上线库可以单独重跑本段：
--      只重跑本段（SECTION kf_14_security）：截出本段语句执行

create table if not exists kf_security_config (
  config_id       bigint(20)     not null auto_increment    comment '参数ID',
  config_key      varchar(100)   not null                   comment '参数键名',
  config_value    varchar(2000)  default ''                 comment '参数键值',
  description     varchar(500)   default ''                 comment '参数说明',
  create_by       varchar(64)    default ''                 comment '创建者',
  create_time     datetime                                  comment '创建时间',
  update_by       varchar(64)    default ''                 comment '更新者',
  update_time     datetime                                  comment '更新时间',
  remark          varchar(500)   default null               comment '备注',
  primary key (config_id),
  unique key uk_security_config_key (config_key)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='安全与加密策略参数表';

delete from kf_security_config where config_key in ('encrypt.enabled', 'encrypt.algo');

insert into kf_security_config (config_key, config_value, description, create_by, create_time, remark) values
('encrypt.enabled', 'true', '业务数据加密开关', 'admin', sysdate(), '仅当 jar 内 voxio.crypto.enforce=false 时可改；交付环境恒为加密'),
('encrypt.algo', 'SM4-GCM', '聊天消息与业务字段加密算法', 'admin', sysdate(), 'SM4-GCM 认证加密，编码契约见 com.voxio.common.utils.crypto.SmCodec');

-- ============================================================================
-- SECTION 17 · kf_15_chat_crypto · 聊天消息加密（会话密钥与密文标记）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：阶段10.2 国密聊天加密的库内变更

-- 说明：
--   1) 会话密钥 K 由客户端生成、SM2 加密上送，服务端只存「主密钥包装后的 K」，
--      因此库里拿到 kf_session 也解不出消息；SM3 摘要用于解包后校验 K 未被替换。
--   2) kf_message 的密文标记与算法/密钥版本随行保存，读取侧据此判断该行是密文还是
--      存量明文（阶段10.6 迁移期间两者共存，见 sm4-gcm$ 前缀契约）。
--   3) 全部用 information_schema 守卫，可反复执行：
--      只重跑本段（SECTION kf_15_chat_crypto）：截出本段语句执行

set @ddl = (select if(count(*) = 0,
  'alter table kf_session add column sm4_key_cipher varchar(255) default null comment ''会话 SM4 密钥（主密钥 SM4-GCM 包装后的 base64，非密钥本身）'' after last_msg_time',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_session' and column_name = 'sm4_key_cipher');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = (select if(count(*) = 0,
  'alter table kf_session add column sm4_key_digest char(64) default null comment ''会话密钥明文材料的 SM3 摘要，解包后校验用'' after sm4_key_cipher',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_session' and column_name = 'sm4_key_digest');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = (select if(count(*) = 0,
  'alter table kf_session add column sm4_key_version int default 0 comment ''会话密钥版本，0 表示尚未协商'' after sm4_key_digest',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_session' and column_name = 'sm4_key_version');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = (select if(count(*) = 0,
  'alter table kf_message add column encrypt_flag char(1) default ''0'' comment ''内容是否密文存储（0明文存量 1密文）'' after content',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_message' and column_name = 'encrypt_flag');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = (select if(count(*) = 0,
  'alter table kf_message add column algo_type varchar(20) default null comment ''内容加密算法标识（SM4-GCM）'' after encrypt_flag',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_message' and column_name = 'algo_type');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = (select if(count(*) = 0,
  'alter table kf_message add column key_version int default null comment ''加密所用会话密钥版本'' after algo_type',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_message' and column_name = 'key_version');
prepare s from @ddl; execute s; deallocate prepare s;

-- 安全策略：聊天加密与完整性算法口径入库（不随访客公开配置下发）
delete from kf_security_config where config_key in ('chat.encrypt.algo', 'chat.hmac.algo', 'chat.key.rotate.minutes');

insert into kf_security_config (config_key, config_value, description, create_by, create_time, remark) values
('chat.encrypt.algo', 'SM4-GCM', '聊天正文端到端加密算法', 'admin', sysdate(), '网关 CHAT 帧 body.content 与 kf_message.content 使用同一算法，编码契约见 SmCodec'),
('chat.hmac.algo', 'HMAC-SM3', '控制帧完整性算法', 'admin', sysdate(), '密钥协商完成后，控制帧的 hmac 字段由 SM3-HKDF 派生的 hmac 子密钥计算'),
('chat.key.rotate.minutes', '0', '会话密钥强制轮换间隔（分钟）', 'admin', sysdate(), '0 表示不主动轮换，随会话结束失效；单商户自建场景不需要前向保密的时间窗');

-- ============================================================================
-- SECTION 18 · kf_16_field_crypto · 业务字段加密（阶段10.3）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：等保三级要求个人信息密文落库，密文列值形如 sm4-gcm$v1$base64(...)，比原文长得多

-- 说明：
--   1) 手机号/邮箱/联系方式由 SmFieldCipherTypeHandler 写入密文、读取时解回明文；
--      列宽从 varchar(11)/varchar(50) 扩到 varchar(255) 才装得下「前缀 + base64」。
--   2) 存量明文行由阶段10.6 的迁移任务统一加密，本段只保证「装得下」，不改数据。
--   3) 全部用 information_schema 守卫，可反复执行：
--      只重跑本段（SECTION kf_16_field_crypto）：截出本段语句执行

set @ddl = (select if(column_type <> 'varchar(255)',
  'alter table kf_agent modify column phone varchar(255) default null comment ''手机号码（SM4-GCM 密文落库）''',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_agent' and column_name = 'phone');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = (select if(column_type <> 'varchar(255)',
  'alter table kf_agent modify column email varchar(255) default null comment ''邮箱（SM4-GCM 密文落库）''',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_agent' and column_name = 'email');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = (select if(column_type <> 'varchar(255)',
  'alter table kf_visitor modify column contact_phone varchar(255) default '''' comment ''联系方式-手机（SM4-GCM 密文落库）''',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_visitor' and column_name = 'contact_phone');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = (select if(column_type <> 'varchar(255)',
  'alter table kf_visitor modify column contact_email varchar(255) default '''' comment ''联系方式-邮箱（SM4-GCM 密文落库）''',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_visitor' and column_name = 'contact_email');
prepare s from @ddl; execute s; deallocate prepare s;

-- 工单正文与留言（同段一并扩列，阶段10.3 工单/留言内容加密后为密文）
set @ddl = (select if(data_type <> 'text',
  'alter table kf_ticket modify column content text comment ''工单内容（SM4-GCM 密文落库）''',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_ticket' and column_name = 'content');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = (select if(data_type <> 'text',
  'alter table kf_ticket_reply modify column content text comment ''回复/备注内容（工单回复为 SM4-GCM 密文落库）''',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_ticket_reply' and column_name = 'content');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = (select if(data_type <> 'text',
  'alter table kf_offline_message modify column content text comment ''留言内容（SM4-GCM 密文落库）''',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_offline_message' and column_name = 'content');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = (select if(column_type <> 'varchar(255)',
  'alter table kf_offline_message modify column contact varchar(255) default '''' comment ''联系方式（SM4-GCM 密文落库）''',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_offline_message' and column_name = 'contact');
prepare s from @ddl; execute s; deallocate prepare s;

set @ddl = (select if(column_type <> 'varchar(255)',
  'alter table kf_ticket modify column contact varchar(255) default '''' comment ''访客联系方式（SM4-GCM 密文落库）''',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_ticket' and column_name = 'contact');
prepare s from @ddl; execute s; deallocate prepare s;

-- 客服密码算法标记（阶段10.3：SM3 加盐哈希，存量 bcrypt 记录登录成功后渐进升级）
set @ddl = (select if(count(*) = 0,
  'alter table kf_agent add column password_algo varchar(20) default ''bcrypt'' comment ''密码哈希算法（sm3-salt 国密 / bcrypt 存量待升级）'' after password',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_agent' and column_name = 'password_algo');
prepare s from @ddl; execute s; deallocate prepare s;

-- sm3$轮数$盐hex(32)$摘要hex(64) 组合起来比 BCrypt 的 60 字符长，varchar(100) 装不下
set @ddl = (select if(column_type <> 'varchar(160)',
  'alter table kf_agent modify column password varchar(160) not null default '''' comment ''登录密码哈希（sm3$轮数$盐$摘要；存量 bcrypt）''',
  'do 0') from information_schema.columns where table_schema = database() and table_name = 'kf_agent' and column_name = 'password');
prepare s from @ddl; execute s; deallocate prepare s;

-- 字段加密与密码哈希口径入安全策略表（不随访客公开配置下发）
delete from kf_security_config where config_key in ('field.encrypt.algo',
        'password.hash.algo', 'password.hash.rounds');

insert into kf_security_config (config_key, config_value, description, create_by, create_time, remark) values
('field.encrypt.algo', 'SM4-GCM', '业务字段（手机号/邮箱/联系方式/工单与留言正文）加密算法', 'admin', sysdate(), '密钥为主密钥经 SM3-HKDF 派生的 field 子密钥，与聊天会话密钥不同串'),
('password.hash.algo', 'SM3-SALT', '客服账号密码哈希算法', 'admin', sysdate(), 'kf_agent 密码用 SM3 加盐迭代哈希；若依 sys_user 仍按原逻辑不变'),
('password.hash.rounds', '10000', '客服密码哈希迭代轮数', 'admin', sysdate(), '轮数抬高单次爆破成本，改轮数只影响新写入的哈希（存量按记录里的轮数校验）');

-- ============================================================================
-- SECTION 19 · kf_17_perf_index · 高频查询索引收口（阶段11 性能）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：
--   1) 只调整索引，不动表与列（阶段11 的硬约束是「不改数据库核心结构」）。
--   2) 每条新增都对应一条实际慢查询的 EXPLAIN 证据，见 docs/performance-tuning.md；
--      被新索引前缀完全覆盖的旧索引一律删掉，不留冗余索引（净增 4 个、删除 2 个）。
--   3) 全部用 information_schema 守卫，可反复执行：
--      只重跑本段（SECTION kf_17_perf_index）：截出本段语句执行
-- ----------------------------------------------------------------------------

-- kf_message：聊天记录列表按 (create_time desc, msg_id desc) 取一页，
-- 单列 idx_create_time 只能定住第一个排序列，排序仍要回表比 msg_id；显式带上 msg_id 后整条排序走索引（可反向扫描）
set @exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'kf_message' and index_name = 'idx_create_msg');
set @sql = if(@exists = 0, 'alter table kf_message add index idx_create_msg (create_time, msg_id)', 'select 1');
prepare s from @sql; execute s; drop prepare s;

-- 旧单列索引是 idx_create_msg 的左前缀，留着只会给优化器多一个更差的选择
set @exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'kf_message' and index_name = 'idx_create_time');
set @sql = if(@exists > 0, 'alter table kf_message drop index idx_create_time', 'select 1');
prepare s from @sql; execute s; drop prepare s;

-- kf_session：会话列表与客服工作台列表的排序是 (status, last_msg_time desc, session_id desc)，
-- 原 idx_status_last 少一个 session_id，同秒消息的顺序还得再排一次；带后两列的降序索引可让 limit 提前停止
set @exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'kf_session' and index_name = 'idx_status_last_msg');
set @sql = if(@exists = 0, 'alter table kf_session add index idx_status_last_msg (status, last_msg_time desc, session_id desc)', 'select 1');
prepare s from @sql; execute s; drop prepare s;

set @exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'kf_session' and index_name = 'idx_status_last');
set @sql = if(@exists > 0, 'alter table kf_session drop index idx_status_last', 'select 1');
prepare s from @sql; execute s; drop prepare s;

-- kf_session：客服侧「我的会话」按 agent_id + status 过滤再按活跃时间排序，原来要 filesort
set @exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'kf_session' and index_name = 'idx_agent_status_last');
set @sql = if(@exists = 0, 'alter table kf_session add index idx_agent_status_last (agent_id, status, last_msg_time desc, session_id desc)', 'select 1');
prepare s from @sql; execute s; drop prepare s;

set @exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'kf_session' and index_name = 'idx_agent_status');
set @sql = if(@exists > 0, 'alter table kf_session drop index idx_agent_status', 'select 1');
prepare s from @sql; execute s; drop prepare s;

-- kf_session：总览的「今日结束会话数」与趋势按 end_time 分组，原索引只有 queue_time
set @exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'kf_session' and index_name = 'idx_status_end');
set @sql = if(@exists = 0, 'alter table kf_session add index idx_status_end (status, end_time)', 'select 1');
prepare s from @sql; execute s; drop prepare s;

-- kf_ticket：工单列表默认按创建时间倒序，原来只有 (status, create_time)，不带筛选条件时整表扫描 + filesort
set @exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'kf_ticket' and index_name = 'idx_create_ticket');
set @sql = if(@exists = 0, 'alter table kf_ticket add index idx_create_ticket (create_time, ticket_id desc)', 'select 1');
prepare s from @sql; execute s; drop prepare s;

-- 说明：本系统只有文本消息，不存在附件与富媒体列，索引口径按会话/消息两条主链路收口；
-- 第 3 段 kf_message 的 uk_session_seq(session_id,seq) 与 idx_session_deliver(session_id,deliver_status) 已覆盖补发查询；
-- kf_message 的补发查询走 uk_session_seq(session_id,seq) 与 idx_session_deliver(session_id,deliver_status)，
-- 游标分页直接用主键 msg_id —— 这两类都不需要新索引，重复建只会拖慢写入。

-- ============================================================================
-- SECTION 20 · kf_18_login_security · 登录加固（阶段10.4）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：去验证码后的登录侧安全参数、客服登录日志表与查看入口

-- 说明：
--   1) 登录不再走图形验证码（人机校验交给「失败锁定 + 口令复杂度 + 登录日志 + 会话超时」这条链），
--      /captchaImage 接口保留但恒返回 captchaEnabled=false，只为兼容尚未升级的旧客户端；
--      sys.account.captchaEnabled 一并改成 false 并在备注里写明已停用，避免运营以为还能打开。
--   2) 五个安全策略参数放 kf_security_config，不放 kf_config：kf_config 的键会被
--      /kf/open/config 白名单挑出下发给访客端，锁定阈值与口令策略属于「告诉攻击者规则」。
--   3) 锁定阈值两端统一为「连续 5 次失败锁 30 分钟」：管理端在 application.yml
--      （user.password.maxRetryCount / lockTime），客服端读这里的两个参数（改完即时生效，不用重启）。
--   4) kf_agent_login_log 只增不改不删（除保留期清理），status 沿用若依口径：0 成功 1 失败。
--      留存 180 天由 KfLoginLogCleanupJob 每日凌晨按 login.log.retention.days 清理，
--      参数低于 180 时按 180 执行（等保三级要求登录日志留存不少于六个月，不给改小的口子）。
--   5) 可重复执行：表用 if not exists，参数先删后插，菜单先删后插。
--      只重跑本段（SECTION kf_18_login_security）：截出本段语句执行
-- ----------------------------------------------------------------------------

create table if not exists kf_agent_login_log (
  log_id        bigint(20)     not null auto_increment    comment '日志ID',
  agent_no      varchar(30)    not null                   comment '登录账号（账号不存在时也记尝试值）',
  agent_id      bigint(20)     default null               comment '客服ID（账号存在时回填）',
  status        char(1)        not null default '0'       comment '登录结果（0成功 1失败）',
  msg           varchar(200)   default ''                 comment '结果说明（失败原因、锁定提示）',
  login_ip      varchar(50)    default ''                 comment '登录IP',
  login_ua      varchar(512)   default ''                 comment '客户端标识（User-Agent 前 500 字）',
  login_time    datetime       not null                   comment '登录时间',
  primary key (log_id),
  key idx_agent_time (agent_no, login_time),
  key idx_status_time (status, login_time)
) engine=innodb auto_increment=100 default charset=utf8mb4 collate=utf8mb4_general_ci comment='客服登录日志表（阶段10.4，留存不少于180天）';

-- ==================== 登录安全策略参数 ====================
delete from kf_security_config where config_key in ('login.lock.max-retry', 'login.lock.minutes',
        'login.log.retention.days', 'password.policy.min-length', 'password.policy.min-categories');

insert into kf_security_config (config_key, config_value, description, create_by, create_time, remark) values
('login.lock.max-retry', '5', '客服登录连续失败多少次后锁定账号', 'admin', sysdate(), 'KfAgentLoginService 每次登录前读取；按「IP + 账号」计数，改完即时生效不用重启'),
('login.lock.minutes', '30', '客服登录锁定时长（分钟）', 'admin', sysdate(), '与管理端 application.yml 的 user.password.lockTime 保持同口径；计数键到期自动解锁，不做人工解锁入口'),
('login.log.retention.days', '180', '客服登录日志留存天数', 'admin', sysdate(), 'KfLoginLogCleanupJob 每日凌晨清理更早记录；低于 180 按 180 执行（等保三级要求登录日志留存不少于六个月）'),
('password.policy.min-length', '8', '客服口令最小长度', 'admin', sysdate(), 'KfPasswordPolicy 在校验「新建客服」与「重置密码」时读取；存量弱口令不批量重置，只在下次改口令时强制'),
('password.policy.min-categories', '3', '客服口令至少覆盖几类字符', 'admin', sysdate(), '四类＝大写、小写、数字、符号；命中类别数低于该值即拒绝，前端 utils/kfPasswordRule.js 同口径提示');

-- ==================== 验证码参数改为已停用 ====================
update sys_config set config_value = 'false',
       remark = '阶段10.4 起登录不再校验图形验证码，本参数保留仅为兼容 /captchaImage 的返回口径，改值不影响登录',
       update_by = 'admin', update_time = sysdate()
 where config_key = 'sys.account.captchaEnabled';

-- ==================== 客服登录日志菜单与权限 ====================
delete from sys_role_menu where menu_id = 2140;
delete from sys_menu where menu_id = 2140;

insert into sys_menu values('2140', '客服登录日志', '1', '7', 'kfLoginLog', 'kf/loginlog/index', '', '', 1, 0, 'C', '0', '0', 'kf:loginlog:list', 'documentation', 'admin', sysdate(), '', null, '客服端登录成功与失败记录（含IP与客户端标识，留存180天）');

-- 客服主管需要能查看登录日志（admin 自动拥有全部菜单）
insert into sys_role_menu values('3', '2140');

-- ============================================================================
-- SECTION 21 · kf_19_audit_config · 审计留痕防删除与留存期（阶段10.5 P0-3）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：操作日志与登录日志摘除人工删除入口后，留存期改由应用按天收敛，参数与菜单权限一并登记

-- 说明：
--   1) SysOperlogController / SysLogininforController 的 DELETE /{ids} 与 DELETE /clean 已整体摘除，
--      这里同步删掉承载「删除/清空」按钮的菜单权限（1040 操作删除、1043 登录删除）及其角色授权，
--      避免「菜单里有按钮、点了必失败」与「有授权无实现」两种失真。
--      查询（1039/1042）与导出（1041/1044）保留：只读动作不影响留痕完整性。
--   2) audit.log.retention.days 放 kf_security_config（与 10.4 的登录策略同表，不进 kf_config 白名单），
--      KfAuditRetentionJob 每日 03:40 按 max(参数, 180) 天清理更早记录；低于 180 天按 180 天执行。
--   3) 可重复执行：参数先删后插；菜单只删不插，重复执行结果一致。
--      只重跑本段（SECTION kf_19_audit_config）：截出本段语句执行
-- ----------------------------------------------------------------------------

delete from kf_security_config where config_key = 'audit.log.retention.days';

insert into kf_security_config (config_key, config_value, description, create_by, create_time, remark) values
('audit.log.retention.days', '180', '审计留痕（操作日志/登录日志）留存天数', 'admin', sysdate(),
 'KfAuditRetentionJob 每日 03:40 清理更早记录；低于 180 按 180 执行（等保三级要求审计日志留存不少于六个月）。应用侧已无人工清空入口');

delete from sys_role_menu where menu_id in (1040, 1043);
delete from sys_menu where menu_id in (1040, 1043);

-- ============================================================================
-- SECTION 22 · kf_20_audit_log · 业务审计留痕（阶段10.5 P0-3）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：新增只增不删的业务审计表，与若依 sys_oper_log 并存但职责不同

-- 说明：
--   1) sys_oper_log 是若依平台日志（含登录/异常/通用字段），等保测评要看的是「谁在什么时候改了哪条业务对象」，
--      需要一张字段口径明确、且代码里根本不存在删除与修改入口的表，这就是 kf_audit_log。
--   2) 只增不删的落地方式：实体没有 @TableLogic、Mapper 只用 BaseMapper 的 insert、
--      Service 只暴露 record 与 selectLogList、Controller 只有一个 @GetMapping("/list")；
--      全仓库不存在针对本表的 delete/update SQL（门禁 J17 直接扫源码断言这一点）。
--      留存期与归档由 KfAuditRetentionJob 统一处理（≥180 天下限），不开放给人工。
--   3) request_params 落库前按字段名结构化打码（password/newpassword/phone/email/contact/content/comment
--      /note/remark/answer/question/keywords/媒体URL 等一律写成 ***），嵌套在实体里的同样打码，
--      避免「为了审计而把明文二次扩散」；Service 侧再截断 2000 字符。
--   4) 只读查询能力对人开放：菜单 2150 承载 kf:auditlog:list，与 10.4 的客服登录日志一起收进 2145「日志留痕」目录，
--      守住阶段8 的「二级菜单不超过 5 个」；没有新增/修改/删除按钮，也没有对应接口。
--   5) 可重复执行：建表 if not exists，菜单先删后插。
--      只重跑本段（SECTION kf_20_audit_log）：截出本段语句执行
-- ----------------------------------------------------------------------------

create table if not exists kf_audit_log (
  log_id         bigint(20)    not null auto_increment  comment '审计ID',
  user_type      varchar(20)   not null default ''      comment '操作人类型（admin 管理端 / agent 客服端）',
  user_id        bigint(20)    default null             comment '操作人ID（sys_user.user_id 或 kf_agent.agent_id）',
  operation      varchar(100)  not null default ''      comment '操作动作（@Log 标题 + 业务类型）',
  target_type    varchar(50)   default ''               comment '操作对象类型（按接口路径推导：agent/ticket/session…）',
  target_id      varchar(64)   default ''               comment '操作对象标识（路径变量或请求体主键，取不到为空）',
  ip             varchar(50)   default ''               comment '操作IP',
  ua             varchar(512)  default ''               comment '客户端标识（User-Agent 前 500 字）',
  request_params text                                   comment '请求参数（已剔除个人信息与文件内容，截断 2000）',
  result         varchar(200)  default ''               comment '执行结果（success 或 fail:原因）',
  create_time    datetime      not null                 comment '操作时间',
  primary key (log_id),
  key idx_user_time (user_type, create_time),
  key idx_target (target_type, target_id)
) engine=innodb auto_increment=1 default charset=utf8mb4 collate=utf8mb4_general_ci comment='业务审计日志表（阶段10.5，只增不删不改，留存不少于180天）';

-- ==================== 业务审计日志菜单与权限（只读） ====================
-- 系统设置下的可见二级菜单已经排到「用户/角色/客服参数/业务设置/客服登录日志」，再加一项就破了阶段8 定的
-- 「一级不超过 7、二级不超过 5」硬约束。这里不动若依自带菜单，只把两张只读留痕表收进一个「日志留痕」目录节点：
-- 若依对非根目录的 M 节点会自动用 ParentView 承载，路由变成 /system/log/xxx，层级仍然可点。
delete from sys_role_menu where menu_id in (2145, 2150);
delete from sys_menu where menu_id in (2145, 2150);

insert into sys_menu values('2145', '日志留痕', '1', '7', 'log', null, '', '', 1, 0, 'M', '0', '0', '', 'log', 'admin', sysdate(), '', null, '客服登录日志与业务审计日志的只读入口（阶段10.5 批次1 菜单密度收口）');
insert into sys_menu values('2150', '业务审计日志', '2145', '2', 'kfAuditLog', 'kf/auditlog/index', '', '', 1, 0, 'C', '0', '0', 'kf:auditlog:list', 'form', 'admin', sysdate(), '', null, '业务写操作留痕（只增不删，含操作人/对象/IP，留存180天）');

update sys_menu set parent_id = 2145, order_num = 1 where menu_id = 2140;

-- 客服主管需要能查看业务审计留痕（admin 自动拥有全部菜单）
insert into sys_role_menu values('3', '2145');
insert into sys_role_menu values('3', '2150');

-- ============================================================================
-- SECTION 23 · kf_22_openapi_replay_decision · 开放接口防重放决策留痕（阶段10.5 批次3 / P1-6）
-- ============================================================================
-- ----------------------------------------------------------------------------
-- 段落说明：把 kf.openapi.secret 从「预留」改判为「不启用」，并把理由与残余风险写进参数备注

-- 说明：
--   1) 结论：不对 /kf/open/** 做客户端时间戳 + HMAC 签名校验，本参数保持不下发（不在 /kf/open/config 白名单）。
--   2) 判定理由（三条都成立才敢不做，不是嫌麻烦）：
--      a. 访客端是公开浏览器页面，任何"下发给客户端的密钥"都能从 JS 里取出来，
--         能重放请求的人同样能重新算一次签名与随机数—— HMAC 在这里不提供超出 TLS 的属性。
--      b. 线路重放已由 TLS 挡住：生产 nginx 只监听 443，HTTP 一律 301 跳 HTTPS，明文不在链路上。
--         部署时必须配好证书链，否则这条前提不成立。
--      c. 强制要求签名头会同时改动访客端的十余个调用点，
--         换来的是一个对真实威胁（被控客户端自重放）无效的开关。
--   3) 已落地的等效缓解（前两条可被门禁现场验证）：
--      同一 visitorNo 重复建会话命中既有活动会话（重放建会话不会堆出第二条）；消息按 clientMsgId 幂等不重复落库。
--      另有访客消息限流 kf.rate.limit.perMinute，但要说清它的作用面：限流在 Netty 网关的聊天消息上（ChatServer），
--      **HTTP 开放接口本身没有速率限制**——这一条连同「被控客户端可自造新随机数」一起记在残余风险里，不是已解决。
--      可追溯性同理来自会话表与消息表的幂等键：kf_audit_log 不覆盖访客开放接口（量大且业务表本身留痕）。
--   4) 未来若出现「服务器对服务器」的开放调用方（有自己的凭据保管能力），再按 per-client secret
--      落地 timestamp(±窗口) + nonce(Redis 唯一性) + HMAC，本段备注即决策基线。
--   5) 可重复执行：只按 config_key 定点更新备注。
--      只重跑本段（SECTION kf_22_openapi_replay_decision）：截出本段语句执行
-- ----------------------------------------------------------------------------

update kf_config
   set remark = '阶段10.5 批次3 判定：不启用。访客端是公开浏览器，客户端持有的密钥不算秘密，能重放的人同样能重新生成时间戳与随机数，HMAC 在这里不提供超出 TLS 的重放防护（生产 nginx 强制 HTTPS，明文不上线）。缓解：会话复用幂等 + 消息 clientMsgId 幂等；残余风险：HTTP 开放接口无速率限制（限流只在网关聊天消息上）、被控客户端自重放无法用签名拦。若将来出现服务器对服务器的开放调用方，再按 per-client secret 落地 timestamp+nonce+HMAC',
       config_name = '访客端签名密钥（判定不启用，理由见备注）',
       update_by = 'admin',
       update_time = sysdate()
 where config_key = 'kf.openapi.secret';
