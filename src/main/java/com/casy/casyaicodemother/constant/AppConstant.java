package com.casy.casyaicodemother.constant;

public interface AppConstant {

    /**
     * 精选应用的优先级
     */
    Integer GOOD_APP_PRIORITY = 99;

    /**
     * 默认应用优先级
     */
    Integer DEFAULT_APP_PRIORITY = 0;

    /**
     * 应用不公布
     */
    Integer APP_NOT_PUBLISH = 0;

    /**
     * 应用已公布
     */
    Integer APP_PUBLISHED = 1;

    /**
     * 用户分页查询最大条数
     */
    int MAX_PAGE_SIZE = 20;

    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";

}
