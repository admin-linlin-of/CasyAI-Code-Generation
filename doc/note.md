## AI生成应用总结
### 总结一：
1. 在AiCodeGeneratorService（名称自定义）接口中定义方法，添加@SystemMessage，后续会通过动态代理通过这些方法调用大模型
2. AiCodeGeneratorServiceFactory工厂类中，会向Bean容器中注入AiCodeGeneratorService类，需要传递class类、以及（非）流式大模型。
3. 与大模型交互返回内容为String类型并且很冗杂，为了能够获取到我们需要的，可以使用结构化输出 => 使用对象封装（code、description分开）=> 将代码存储到文件中！
4. 存储文件的方法为CodeFileSaver，只需要封装后的结构体对象。流程为：生成文件路径目录 -> 生成唯一路径名 -> 写入到文件 -> 返回目录路径
5. 但是这种处理方式等待的时间比较长 ->流式输出 。通过LangChain4j + Reactor因为流式输出无法直接配合结构化输出使用，所以只能接收数据，然后通过序列化处理数据，最后封装成对象结构，然后交给CodeFileSaver类来创建文件。

    补充：流式输出就是额外多出了：获取所有响应内容 / 正则筛选各部分内容 （CodeParser类） 两部分。
### 总结二：
#### A.保存文件
使用模板设计模式。 原因：虽然保存 HTML单文件 和 多文件的方法不同。但是在整个保存文件的流程中有重复的方法，将重复的地方抽取出来，不同的地方各自实现！
1. 校验输入 （可公共 / 可独自实现 ） validInput(T result) T泛型，由继承类提供类型。
2. 构建文件的目录路径 （公共部分） buildUniqueDirPath()
3. 保存文件 （独自实现） save(String basicFilePath , T result)
4. 返回目录 （公共部分）
执行器类类似于门面类 -> CodeFileSaverExecutor，执行器接收codeResult ，codeGenType来执行不同的方法，我们只需要提供执行器中的接口即可！
#### B.解析Code
使用策略设计模式。原因：流式输出不支持结构化输出，因此要对Flux类型的输出进行解析。但是 单HTML文件 和 多文件的解析方式有一点差别，并且不涉及多流程，因此不适用模板设计模式。
1. 解析单HTML文件的类 -> HtmlCodeParser -> parseCode(String code)
2. 解析多文件的类 -> MultiFileCodeParser -> parseCode(String code)
3. 解析的实现逻辑类似：使用正则表达式对我们的code进行匹配（定义了三种模式：HTML、CSS、JS），将解析到的code作为参数去构造HtmlCodeResult / MultiFileCodeResult类即可。

同样提供执行器类 -> CodeParserExecutor，接收 code 和 codeGenTypeEnum 。根据不同类型代码文件对应不同的解析逻辑。
#### C.门面类
之前是根据不同的codeGenTypeEnum类型，来调用对应不同的处理类。现在Parser类和Saver类都有对应的executor了，因此只需要调用executor提供的方法即可！

## 应用模式总结
### 抽象生成对象
1. 将生成的代码，抽象成“应用”的对象，用户在生成应用时通过应用ID和文件目录绑定，所以调整了生成文件的目录结构，使用了APP和APPVO脱敏数据
### 流式返回调整
1. 使用ServerSentEvent增强了流式返回的内容，使用json包装返回数据，防止空格被忽略，使用自定义的done事件表达生成完成，用来区分正常结束和异常结束
### 生成和部署分离
1. 使用StaticResourceController将本地生成的网站返回前端（如果使用COS对象存储可以直接返回）
2. 部署nginx服务，配置目录为code_deploy，即可访问其下的所有网站
### jsonb的字段
1. 添加了jsonb字段app_type用于记录网站的类型，jsonb支持索引

|           | json                             | jsonb                                  |
| --------- | -------------------------------- | -------------------------------------- |
| 存储      | 原文本，保留空格、键顺序、重复键 | 解析后的二进制，不保留上述细节         |
| 写入      | 快（几乎不解析）                 | 慢（要解析再存）                       |
| 查询/运算 | 慢（每次要解析）                 | 快                                     |
| 索引      | 基本不能高效索引                 | 支持 GIN 等索引                        |
| 操作符    | 少                               | 多（`@>`、`?`、`#>`、`jsonb_set` 等）  |
| 相等比较  | 文本级                           | 语义级（`{"a":1}` 与 `{"a": 1}` 相等） |

选用：

- 只做存取、要原样保留输入 → `json`
- 要查、改、索引、比较 → 一般用 `jsonb`（多数业务场景）
  因为需要根据类型搜索这里选择了jsonb类型
2. jsonb字段需要JsonbTypeHandler来解析List<String>的字段，写入数据库前的参数绑定：Java 对象 → PostgreSQL jsonb。 流程：{@code List<String>} → JSON 字符串 → {@link PGobject}(type=jsonb) → PreparedStatement
### 前端的打字机效果
1. 通过requestAnimationFrame(tick)每秒 60 次（每屏刷新一次）会执行你注册的回调，三个字符三个字符的显示
### 前端的代码预览效果
1. 通过monaco-editor实现显示
### 前端深浅模式切换
1. 使用圆心扩散的方式：
    1. 浏览器拍两张“整页照片”
       document.startViewTransition(update) 执行时：
       更新前：把当前页面（旧主题）截成快照 → ::view-transition-old(root)
       执行 update()：改 html[data-theme] 等，页面变成新主题
       更新后：把新页面截成快照 → ::view-transition-new(root)
       两层叠在屏幕上，用户看到的不是立刻闪切，而是这两张快照在过渡。
    
    2. 关掉默认淡入淡出
       theme.css 里把两层默认动画关掉，避免和自定义圆形动画冲突：
        theme.css
        Lines 235-238
        ::view-transition-old(root),
        ::view-transition-new(root) {
         animation: none;
        }
    
    3. 只让“新主题快照”从圆心放大显示
       transition.ready 后对 ::view-transition-new(root) 做 clip-path 动画：
       themeTransition.ts
       Lines 45-57
    
       ```js
       document.documentElement.animate(
           {
               clipPath: [
               `circle(0px at ${x}px ${y}px)`,
               `circle(${radius}px at ${x}px ${y}px)`,
               ],
           },
           {
               duration: 500,
               easing: 'ease-in-out',
               pseudoElement: '::view-transition-new(root)',
           },
       )
       ```
    
       含义：
       circle(0px at x y)：圆心在点击处，半径 0 → 新主题层几乎不可见
       circle(radius px at x y)：圆扩大到盖住整个视口 → 新主题全屏可见
       视觉上就像以按钮为中心的一个圆不断变大，露出下面的新主题
       radius 用 Math.hypot 算的是：圆心到屏幕四角最远那一角的距离，保证圆能盖满全屏：
    
       ```js
       //themeTransition.ts
       //Lines 33-37
       const radius = Math.hypot(
           Math.max(x, window.innerWidth - x),
           Math.max(y, window.innerHeight - y),
       )
       ```
    
       示意图
       [ 旧主题快照 ]  一直铺满屏幕（底层）
       [ 新主题快照 ]  被 clip-path 裁成圆，圆从小变大
    
       ```
       [ 旧主题快照 ]  一直铺满屏幕（底层）
       [ 新主题快照 ]  被 clip-path 裁成圆，圆从小变大
             ●  ← 点击位置为圆心
            ╱ ╲
           ╱   ╲  半径 0 → radius
       ```
    
    4. 总结
    
         | 部分                     | 作用                                           |
         | ------------------------ | ---------------------------------------------- |
         | GlobalHeader             | 传 `MouseEvent`，提供圆心                      |
         | `setTheme`               | 改 `data-theme`、localStorage、Ant Design 主题 |
         | `startViewTransition`    | 生成旧/新整页快照层                            |
         | `clip-path: circle(...)` | 新快照从点击处圆形展开                         |
         | 不支持 API / 无坐标      | 直接 `update()`，无动画                        |
    
         所以这不是 CSS 画一个圆在页面上扩散，而是对新主题那一帧画面做圆形遮罩动画；旧主题仍铺满底层，被逐渐露出的新主题“盖住”

### 计划

版本控制，代码区别想要添加这些功能需要了解完成对话记忆之后在实现


## 坑
### AI响应的内容为JSON格式
1. **response-format: json_object**
强制模型只返回合法 JSON 对象（对应 OpenAI 的 response_format: { type: "json_object" }）。输出必须是 JSON，不能是普通文本或 Markdown。

2. **strict-json-schema: true** 开启 Structured Outputs 的严格 JSON Schema 模式。配合 AiServices 使用时，LangChain4j 会根据返回类型（如 HtmlCodeResult、MultiFileCodeResult）及其 @Description 注解自动生成 Schema，
  并要求模型严格按 Schema 输出：字段名、类型、必填项都要匹配，不能多字段、不能漏字段。

总结：response-format: json_object 是模型级全局配置，流式（Flux<String>）也会被强制输出 JSON，和你 prompt 里的 Markdown 代码块冲突，注释掉即可。 
strict-json-schema: true 只作用于 AiServices 返回 POJO 的方法（generateHtmlCode、generateMultiFileCode），LangChain4j 会按请求单独走 json_schema，不依赖 json_object，可以保留。
如果你只用流式、不用那两个结构化方法，两个都可以注释，strict-json-schema 对流式本来就没影响

### redis保留对话记时报错 
报错：RuntimeException
redis.clients.jedis.exceptions.JedisDataException: ERR unknown command 'JSON.GET', with args beginning with: 

原因：RedisChatMemoryStore 用的是 RedisJSON 命令（JSON.GET / JSON.SET），你本地是普通 Redis，没装 RedisJSON 模块。

## 对话记忆总结
### 1. 记忆持久化和游标查询：
- 将对话记录保存到表中包括用户和助手消息
- 使用游标查询：使用时间为游标值，解决记忆不断生成，分页查询容易导致重复的问题，并且游标查询解决深度分页的问题，并且给用户看的对话历史暂时是不需要查看全部的，用户在对话框中向上移动的场景会很适合游标查询
### 2. 使用redis存储对话记忆
- langchain可以直接对接redis默认是使用JSON格式，并且需要使用redis-stack才有json格式
- 使用redis存储对话记忆的优点：速度比数据库快，另外redis也能有持久化机制
### 3. 按memoryId隔离记忆
- 对不同的对话单独生成一个service，对不同的模型也单独生成一个service,但是对话记忆仍要使用一个memoryId
### 4. 版本号功能
- 添加版本号功能最多只能生成10个版本
最后的扩展模块
- 记录应用对话总轮次
  统计每个应用的对话轮数，这个数据可以用于分析用户使用习惯，也可以作为应用复杂度的参考指标，这个我准备做完第七章后在实现
- 对话历史导出功能
  支持导出对话记录为 Markdown 文件，方便用户保存和分享开发过程。和第八期的导出代码一起实现
- 智能记忆管理（较难）
利用 AI 分析对话次数较多的应用，智能总结过去的对话历史，节省 Token 的同时优化记忆效果。做完第七章在实现，需要更加全面的分析项目




vue版本记录方案

现在有个问题，比如我生成了v1之后，用户要修改部分网页内容，比如用户输入了：标题改成“欢迎来到张三的个人博客”，这时ai只会调用工具只修改标题所在的文件，但是这样会生成一个新的目录如图中v2，这个目录中只有这一个修改后的文件，这个有什么解决方案吗？先说方案代码不要改