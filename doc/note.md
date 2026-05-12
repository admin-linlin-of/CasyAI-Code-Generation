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