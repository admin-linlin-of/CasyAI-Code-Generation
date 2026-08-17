package com.casy.casyaicodemother.langgraph4j.demo;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Define the state for our graph
// 1. 定义状态：我们的状态将包含一个消息列表
class SimpleState extends AgentState {
    public static final String MESSAGES_KEY = "messages";

    // Define the schema for the state.
    // MESSAGES_KEY will hold a list of strings, and new messages will be appended.
    /**
     * Java 9 新增，返回的是不可变 Map，不是 HashMap 实例
     * 底层实现是 JDK 内部私有类，不是 HashMap
     * ❌ 不能 put、remove、clear，调用会抛 UnsupportedOperationException
     * 最多只能放 10 个键值对（有重载方法，0~10 组 k-v）
     * key、value 都不能为 null，传 null 直接空指针
     */
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            MESSAGES_KEY, Channels.appender(ArrayList::new)
    );

    public SimpleState(Map<String, Object> initData) {
        super(initData);
    }

    public List<String> messages() {
        return this.<List<String>>value("messages")
                .orElse( List.of() );
    }
}