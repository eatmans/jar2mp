# jar2mp 还原差距收敛方案

## 目标

把当前“可编译、可启动、可生成报告”的恢复能力，继续推进到更接近原始 jar 的结构一致性、行为一致性和资源一致性。

当前项目已经覆盖了：

- 资源分类与目标路径映射
- 启动入口推断
- 字节码对照报告
- Maven 构建验证
- 反编译失败的原始 class 回退

剩余差距主要在三类：

1. 反编译结果和原始字节码的语义偏差
2. 运行时行为无法从静态字节码完全恢复
3. 构建/运行环境相关的隐式依赖无法静态推断

## 方案总览

采用三条线并行推进：

1. 静态还原增强
2. 运行时取证增强
3. 验证与评分增强

三条线共享同一份分析结果，但输出不同层面的证据和报告。

## 方案 A: 静态还原增强

### 目标

尽量提高源码层面的还原质量，减少反编译损失，补足当前对变量名、匿名类、内联 lambda、控制流边角的还原缺口。

### 主要工作

- 引入多反编译器策略
  - 让 CFR 继续作为默认引擎
  - 增加第二、第三个反编译器作为对照源
  - 以“同一 class 的多份输出 + 字节码指纹”选择最优结果
- 强化源码归一化
  - 对 import、泛型、注解、lambda、switch、try/catch/finally 做结构化比对
  - 保留原始 class 的方法签名、字段签名、异常表和签名信息
- 加强调试信息利用
  - 尽量使用 LocalVariableTable、LineNumberTable、Signature、Annotation、BootstrapMethods
  - 对缺失调试信息的类明确降级，不假装恢复成功
- 扩展类粒度映射
  - 区分外部类、匿名类、内部类、lambda 相关合成类
  - 对 multi-release jar、module-info、package-info 做专门处理

### 输出

- `decompile-parity-report.md` 升级为多源比对报告
- 新增 `static-fidelity-report.md`
- 失败类继续保留 `target/original-classes/`

### 关联代码

- `src/main/java/com/z0fsec/jar2mp/core/DecompilerBridge.java`
- `src/main/java/com/z0fsec/jar2mp/core/DecompileParityReporter.java`
- `src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java`

## 方案 B: 运行时取证增强

### 目标

补静态分析无法恢复的部分，重点是反射、动态加载、资源访问、Spring/Servlet 装配、文件和网络行为。

### 主要工作

- 运行原 jar 的受控烟雾测试
  - 使用 agent、JFR、Java instrumentation 或包装启动脚本收集运行痕迹
  - 仅在安全、可控、允许执行的样本上启用
- 记录动态行为证据
  - `Class.forName`、`getMethod`、`invoke` 等反射链路
  - `getResource*`、文件访问、模板加载、SPI 读取
  - Spring bean 初始化、Controller/Servlet 路由启动痕迹
  - 网络、数据库、外部依赖调用的调用点
- 回填静态恢复结果
  - 将运行时证据映射到源文件和资源文件
  - 标注“静态未知但运行时可证实”的路径

### 输出

- `runtime-trace-report.md`
- `runtime-entrypoint-report.md`
- `runtime-resource-usage.md`

### 关联代码

- `src/main/java/com/z0fsec/jar2mp/core/StartupDetector.java`
- `src/main/java/com/z0fsec/jar2mp/core/ProjectVerifier.java`
- `src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java`

## 方案 C: 验证与评分增强

### 目标

把“还差多少”从主观判断变成可量化结果。

### 主要工作

- 引入分层评分
  - 类级别：源码可恢复、仅部分恢复、无法恢复
  - 资源级别：已恢复、路径可疑、缺失、跳过
  - 启动级别：可直接启动、需补参数、需外部容器、未知
- 扩大验证维度
  - 继续保留 Maven compile/package 验证
  - 增加可选 smoke run
  - 对关键类做 round-trip 检查
- 生成总评分
  - 输出整体恢复度
  - 输出按模块/包/类型分组的残余缺口

### 输出

- `restoration-score.md`
- `verification-report.md` 扩展为多阶段结果
- `gap-summary.md`

### 关联代码

- `src/main/java/com/z0fsec/jar2mp/core/ProjectVerifier.java`
- `src/main/java/com/z0fsec/jar2mp/core/RestorationReportWriter.java`
- `src/main/java/com/z0fsec/jar2mp/core/JarAnalyzer.java`

## 推荐执行顺序

1. 先做方案 A
   - 风险最低
   - 对现有工具侵入最小
   - 能立刻改善报告质量和源码质量
2. 再做方案 B
   - 这是缩小“语义差距”的核心手段
   - 直接补静态分析补不上的动态行为
3. 最后做方案 C
   - 把前两条产生的证据统一成评分体系
   - 让“离 100% 还差多少”变成可追踪指标

## 成功标准

- 反编译失败类数量明显下降
- 动态调用、资源访问、启动路径有可追踪证据
- 能给每个项目输出一个清晰的恢复评分
- 对“结构还原”和“语义还原”分开报告，不再混为一谈

## 风险与边界

- 原 jar 可能包含恶意逻辑或高副作用行为，不能默认安全执行
- 外部服务、私有配置、生产环境参数无法完全复现
- 运行时取证只能逼近行为，不可能保证绝对等价
- 多反编译器的结果会带来噪声，需要明确仲裁规则

