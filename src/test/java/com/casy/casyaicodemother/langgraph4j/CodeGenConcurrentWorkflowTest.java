package com.casy.casyaicodemother.langgraph4j;

import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.langgraph4j.workflow.CodeGenConcurrentWorkflow;
import com.casy.casyaicodemother.model.enums.CodeGenTypeEnum;
import com.casy.casyaicodemother.model.enums.ModelTypeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CodeGenConcurrentWorkflowTest {

    /**
     * 无 appId：对齐首页发起会话，自动创建应用后走完整生成流程。
     * 指定 HTML + Flash，避免路由到 Vue 工程导致测试过慢。
     */
    @Test
    void testConcurrentWorkflow() {
        WorkflowContext result = new CodeGenConcurrentWorkflow().executeWorkflow(
                "创建一个技术博客网站，需要展示编程教程和系统架构",
                null, null, CodeGenTypeEnum.HTML, ModelTypeEnum.DEEPSEEKFLASH);
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getAppId());
        Assertions.assertEquals(CodeGenTypeEnum.HTML, result.getGenerationType());
        System.out.println("应用ID: " + result.getAppId());
        System.out.println("生成类型: " + result.getGenerationType());
        System.out.println("生成模型: " + result.getModelTypeEnum());
        System.out.println("生成的代码目录: " + result.getGeneratedCodeDir());
        System.out.println("构建结果目录: " + result.getBuildResultDir());
        System.out.println("收集的图片数量: " + (result.getImageList() != null ? result.getImageList().size() : 0));
    }

    /**
     * 无 appId：指定 Vue 工程模式，走质检通过后的 project_builder。
     */
    @Test
    void testVueProjectWorkflow() {
        WorkflowContext result = new CodeGenConcurrentWorkflow().executeWorkflow(
                "创建一个Vue前端项目，包含用户管理和数据展示功能",
                null, null, CodeGenTypeEnum.VUE_PROJECT, ModelTypeEnum.DEEPSEEKFLASH);
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getAppId());
        Assertions.assertEquals(CodeGenTypeEnum.VUE_PROJECT, result.getGenerationType());
        System.out.println("应用ID: " + result.getAppId());
        System.out.println("生成类型: " + result.getGenerationType());
        System.out.println("生成模型: " + result.getModelTypeEnum());
        System.out.println("生成的代码目录: " + result.getGeneratedCodeDir());
        System.out.println("构建结果目录: " + result.getBuildResultDir());
        System.out.println("收集的图片数量: " + (result.getImageList() != null ? result.getImageList().size() : 0));
    }

    /**
     * 无 appId、不指定类型和模型：完全走首页「自动选择」路径。
     */
    @Test
    void testEcommerceWorkflow() {
        WorkflowContext result = new CodeGenConcurrentWorkflow().executeWorkflow(
                "创建一个简单的个人介绍页，包含姓名、简介和联系方式");
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getAppId());
        System.out.println("应用ID: " + result.getAppId());
        System.out.println("生成类型: " + result.getGenerationType());
        System.out.println("生成模型: " + result.getModelTypeEnum());
        System.out.println("生成的代码目录: " + result.getGeneratedCodeDir());
        System.out.println("收集的图片数量: " + (result.getImageList() != null ? result.getImageList().size() : 0));
    }
}
