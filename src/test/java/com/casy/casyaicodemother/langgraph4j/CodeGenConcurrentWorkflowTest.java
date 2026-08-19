package com.casy.casyaicodemother.langgraph4j;

import com.casy.casyaicodemother.langgraph4j.state.WorkflowContext;
import com.casy.casyaicodemother.langgraph4j.workflow.CodeGenConcurrentWorkflow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CodeGenConcurrentWorkflowTest {

    @Test
    void testConcurrentWorkflow() {
        WorkflowContext result = new CodeGenConcurrentWorkflow().executeWorkflow("创建一个技术博客网站，需要展示编程教程和系统架构", 741582369L);
        Assertions.assertNotNull(result);
        System.out.println("生成类型: " + result.getGenerationType());
        System.out.println("生成模型: " + result.getModelTypeEnum());
        System.out.println("生成的代码目录: " + result.getGeneratedCodeDir());
        System.out.println("构建结果目录: " + result.getBuildResultDir());
        System.out.println("收集的图片数量: " + (result.getImageList() != null ? result.getImageList().size() : 0));
    }

    @Test
    void testEcommerceWorkflow() {
        WorkflowContext result = new CodeGenConcurrentWorkflow().executeWorkflow("创建一个电子商务网站，需要商品展示、购物车和支付功能", 456789L);
        Assertions.assertNotNull(result);
        System.out.println("生成类型: " + result.getGenerationType());
        System.out.println("生成的代码目录: " + result.getGeneratedCodeDir());
        System.out.println("收集的图片数量: " + (result.getImageList() != null ? result.getImageList().size() : 0));
    }
}
