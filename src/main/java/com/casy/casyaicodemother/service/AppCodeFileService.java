package com.casy.casyaicodemother.service;

import com.casy.casyaicodemother.model.entity.User;

import java.util.List;

/**
 * Vue 项目源码文件列表服务（供前端代码预览面板使用）。
 */
public interface AppCodeFileService {

    /**
     * 列出指定应用版本目录下的相对文件路径（排除 node_modules、dist 等）。
     */
    List<String> listVueProjectFiles(Long appId, String codeDir, User loginUser);
}
