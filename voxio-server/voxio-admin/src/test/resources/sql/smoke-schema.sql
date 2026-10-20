-- 数据层冒烟测试用 H2 建表（列定义与 sql/voxio_chat.sql 的基座段落对齐，去掉 MySQL 专有语法）
CREATE TABLE sys_dept (
  dept_id    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  parent_id  BIGINT   DEFAULT 0,
  ancestors  VARCHAR(50) DEFAULT '',
  dept_name  VARCHAR(30) DEFAULT '',
  order_num  INT         DEFAULT 0,
  leader     VARCHAR(20) DEFAULT NULL,
  phone      VARCHAR(11) DEFAULT NULL,
  email      VARCHAR(50) DEFAULT NULL,
  status     CHAR(1)     DEFAULT '0',
  del_flag   CHAR(1)     DEFAULT '0',
  create_by  VARCHAR(64) DEFAULT '',
  create_time DATETIME,
  update_by  VARCHAR(64) DEFAULT '',
  update_time DATETIME,
  remark     VARCHAR(500) DEFAULT NULL
);

CREATE TABLE sys_user (
  user_id     BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  dept_id     BIGINT   DEFAULT NULL,
  user_name   VARCHAR(30) NOT NULL,
  nick_name   VARCHAR(30) NOT NULL,
  user_type   VARCHAR(2)  DEFAULT '00',
  email       VARCHAR(50) DEFAULT '',
  phonenumber VARCHAR(11) DEFAULT '',
  sex         CHAR(1)     DEFAULT '0',
  avatar      VARCHAR(100) DEFAULT '',
  password    VARCHAR(100) DEFAULT '',
  status      CHAR(1)     DEFAULT '0',
  del_flag    CHAR(1)     DEFAULT '0',
  login_ip    VARCHAR(128) DEFAULT '',
  login_date  DATETIME,
  pwd_update_date DATETIME,
  create_by   VARCHAR(64) DEFAULT '',
  create_time DATETIME,
  update_by   VARCHAR(64) DEFAULT '',
  update_time DATETIME,
  remark      VARCHAR(500) DEFAULT NULL
);

CREATE TABLE sys_config (
  config_id    INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  config_name  VARCHAR(100) DEFAULT '',
  config_key   VARCHAR(100) DEFAULT '',
  config_value VARCHAR(500) DEFAULT '',
  config_type  CHAR(1)      DEFAULT 'N',
  create_by    VARCHAR(64)  DEFAULT '',
  create_time  DATETIME,
  update_by    VARCHAR(64)  DEFAULT '',
  update_time  DATETIME,
  remark       VARCHAR(500) DEFAULT NULL
);
