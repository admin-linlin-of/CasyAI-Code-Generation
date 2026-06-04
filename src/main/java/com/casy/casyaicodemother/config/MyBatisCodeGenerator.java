package com.casy.casyaicodemother.config;

import cn.hutool.core.lang.Dict;
import cn.hutool.setting.yaml.YamlUtil;
import com.mybatisflex.codegen.Generator;
import com.mybatisflex.codegen.config.GlobalConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.util.Map;

public class MyBatisCodeGenerator {

    // 需要生成的表名
    private static final String[] TABLE_NAMES = {"t_app_version"};

    public static void main(String[] args) {
        // 从项目内读取 application.yaml。IDE/命令行 的工作目录可能不同：若在项目根目录跑 main 用第一路径；若在多模块仓库根则尝试第二路径。
        File yaml = new File("src/main/resources/application.yaml");
        if (!yaml.isFile()) {
            yaml = new File("casy-ai-code-mother/src/main/resources/application.yaml");
        }
        Dict dict = YamlUtil.loadByPath(yaml.getAbsolutePath());
        Map<String, Object> dataSourceConfig = dict.getByPath("spring.datasource");
        String url = String.valueOf(dataSourceConfig.get("url"));
        String username = String.valueOf(dataSourceConfig.get("username"));
        String password = String.valueOf(dataSourceConfig.get("password"));
        HikariDataSource dataSource = new HikariDataSource();
        // 显式指定驱动，与 spring.datasource.url 中的 postgresql 一致，避免 Hikari 只凭 URL 推断时偶发不加载驱动。
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        // 创建配置内容
        GlobalConfig globalConfig = createGlobalConfig();

        // 通过 datasource 和 globalConfig 创建代码生成器
        Generator generator = new Generator(dataSource, globalConfig);

        // 生成代码
        generator.generate();
    }

    // 详细配置见：https://mybatis-flex.com/zh/others/codegen.html
    public static GlobalConfig createGlobalConfig() {
        // 创建配置内容
        GlobalConfig globalConfig = new GlobalConfig();

        // 设置根包，建议先生成到一个临时目录下，生成代码后，再移动到项目目录下
        globalConfig.getPackageConfig()
                .setBasePackage("com.casy.casyaicodemother.genresult");

        // 设置表前缀和只生成哪些表，setGenerateTable 未配置时，生成所有表
        globalConfig.getStrategyConfig()
                // PostgreSQL 里表挂在 schema 下；不设时 JDBC 元数据可能扫到别的对象/读不准主键。你库表建在 public 下，故指定 public。
                .setGenerateSchema("public")
                .setGenerateTable(TABLE_NAMES)
                .setLogicDeleteColumn("isDelete");

        globalConfig.enableEntity()
                .setWithLombok(true)
                .setJdkVersion(21)
                .setAlwaysGenColumnAnnotation(true);
        globalConfig.enableMapper();
        globalConfig.enableMapperXml();
        globalConfig.enableService();
        globalConfig.enableServiceImpl();
        globalConfig.enableController();

        // 默认可不覆盖已存在文件。若曾生成出空类或要反复生成，需 true 才能覆盖；放在 enable* 之后，确保各模块 Config 已初始化。
        globalConfig.getEntityConfig().setOverwriteEnable(true);
        globalConfig.getControllerConfig().setOverwriteEnable(true);
        globalConfig.getServiceImplConfig().setOverwriteEnable(true);

        globalConfig.getJavadocConfig()
                .setAuthor("<a href=\"https://gitee.com/linlinyes/casy-ai-code-mother\">程序员Casy</a>")
                .setSince("");
        return globalConfig;
    }
}
