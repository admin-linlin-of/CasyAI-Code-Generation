-- 登录页体验账号：参数表可动态改展示文案；用户表提供可实际登录的 user01 / 1234567
INSERT IGNORE INTO t_sys_param (id, param_key, param_value, param_name, remark, enabled, is_public, sort_order)
VALUES
    (3, 'login.demo.account', 'user01', '登录页体验账号',
     '展示在登录页的默认账号，修改后刷新登录页生效。实际登录仍以用户表为准', 1, 1, 30),
    (4, 'login.demo.password', '1234567', '登录页体验密码',
     '展示在登录页的默认密码。若修改此项，请同步改用户表中对应账号的密码', 1, 1, 40);

INSERT IGNORE INTO t_user (id, user_account, user_password, user_name, user_role, is_delete)
VALUES (10001, 'user01', 'c9a6c9a4d0475bf71553d89234e5569e', '体验用户', 'user', 0);
