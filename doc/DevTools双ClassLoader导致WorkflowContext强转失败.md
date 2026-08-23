# DevTools 双 ClassLoader 导致 WorkflowContext 强转失败

- 接口：`GET /api/workflow/execute-sse`
- 现象：工作流第 1 步刚完成即失败
- 环境：本地 IDEA 启动 + `spring-boot-devtools`

## 1. 报错原文

```
java.lang.ClassCastException: class com.casy.casyaicodemother.langgraph4j.state.WorkflowContext
cannot be cast to class com.casy.casyaicodemother.langgraph4j.state.WorkflowContext
(WorkflowContext is in unnamed module of loader 'app';
 WorkflowContext is in unnamed module of loader org.springframework.boot.devtools.restart.classloader.RestartClassLoader)
```

后续还有第二次失败：`SseEmitter.completeWithError` 把异常交回 Spring，全局异常处理试图用 `text/event-stream` 写 `BaseResponse`，再报 `HttpMessageNotWritableException`。这是连带问题，根因仍是上面的 ClassCast。

崩溃点：

```java
// WorkflowContext.getContext
return (WorkflowContext) state.data().get(WORKFLOW_CONTEXT_KEY);
```

业务代码看起来完全正确：Map 里放的就是 `WorkflowContext`，强转也合理。单测 / 同步跑有时能过，SSE 更容易炸。

## 2. 根因：不是类型写错，是两个同名 Class

JVM 识别一个类的身份是：

```
(全限定名, ClassLoader) → 一个 Class 对象
```

只看名字不够。下面两个不是同一个类：

| 加载器 | 谁在用 | 得到的 Class |
| --- | --- | --- |
| `AppClassLoader`（日志里的 `loader 'app'`） | langgraph4j 克隆 / 反序列化 state | 父加载器里的 `WorkflowContext` |
| `RestartClassLoader` | 业务代码 `getContext` 的强转目标 | 子加载器里的 `WorkflowContext` |

全限定名都是 `com.casy....WorkflowContext`，字节码也一样，但仍是两个 `Class`。`(A) obj` 要求 `obj` 的 Class **就是当前这段代码里的 A**，名字相同不能互转。所以日志会写成「WorkflowContext cannot be cast to WorkflowContext」。

## 3. DevTools 为什么会造出两套类

`spring-boot-devtools` 热重启不关 JVM，只换业务 ClassLoader：

```
AppClassLoader（父，热重启时不卸）
  └── 第三方 jar：Spring、langgraph4j…

RestartClassLoader（子，监听到 classpath 变化就丢掉重建）
  └── 业务类：com.casy...WorkflowContext
```

默认分工：

- 第三方依赖放父加载器，热重启更快
- 自己的类放 `RestartClassLoader`，改代码只重载这一层

副作用：langgraph4j 一直活在父加载器。节点之间拷贝 / 序列化 `MessagesState` 时，会在 **AppClassLoader** 再装一份 `WorkflowContext`。业务代码引用的是子加载器那份。SSE 走虚拟线程时，TCCL 更容易掉回 `app`，所以比同步路径更容易中招。

没有 DevTools（测试、打成 jar 生产启动）时通常只有 `AppClassLoader`，全项目一份 Class，原强转代码可以正常工作。

## 4. 解决思路

目标：让「造对象的库」和「用对象的业务代码」认同一个 `Class`。

### 4.1 治本：把 langgraph4j 拉进 RestartClassLoader

`src/main/resources/META-INF/spring-devtools.properties`：

```properties
restart.include.langgraph4j=/langgraph4j.*\\.jar
```

`restart.include.langgraph4j` 只是配置项名字；后面的正则才匹配 jar。生效后：

```
RestartClassLoader
  ├── 业务类
  └── langgraph4j
```

库和 `WorkflowContext` 绑在同一加载器，clone 出来的实例可以正常强转。

改完必须 **完整重启** 一次，仅热重启不够。

### 4.2 兜底：`getContext` 按 Class 身份取值

即使加载器仍不一致，也不直接强转：

1. `value.getClass() == WorkflowContext.class`（同一个 Class 对象）才强转
2. 否则 JSON 序列化再 `toBean` 成 **当前代码所在加载器** 的实例

JSON 只认字段，不认 ClassLoader，用来跨加载器搬数据。

### 4.3 辅助：虚拟线程带上正确 TCCL

SSE / Flux 里 `Thread.startVirtualThread` 前：

```java
ClassLoader cl = WorkflowContext.class.getClassLoader();
Thread.currentThread().setContextClassLoader(cl);
```

降低 langgraph4j 反序列化时用错加载器的概率。

### 4.4 SSE 走全局异常处理时不要返回 JSON

`completeWithError` 会把异常交给 `GlobalExceptionHandler`。原先处理器一律返回 `BaseResponse`，而响应头已是 `text/event-stream`，于是：

`No converter for BaseResponse with preset Content-Type 'text/event-stream'`

处理：识别 SSE 请求后写 `event: workflow_error` + 同一套 `BaseResponse` JSON，普通接口仍返回 JSON。

## 5. 相关代码位置

| 文件 | 作用 |
| --- | --- |
| `config` 无，见 `META-INF/spring-devtools.properties` | 让 langgraph4j 进 RestartClassLoader |
| `langgraph4j/state/WorkflowContext.java` | `getContext` 按 Class 身份 + JSON 兜底 |
| `langgraph4j/workflow/CodeGenConcurrentWorkflow.java` | 虚拟线程 TCCL；SSE `completeWithError` |
| `exception/GlobalExceptionHandler.java` | SSE 请求写 `workflow_error` 事件，避免 JSON 转换器冲突 |

## 6. 一句话

DevTools 为热重启把类空间切成两半；langgraph4j 在父加载器里拷贝业务对象，造出另一份同名 Class。业务逻辑没写错，是运行时类身份对不上。
