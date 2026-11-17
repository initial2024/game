# 山河纪年 0.7 验证与交付

本版是可玩的阶段更新，不代表历次请求的全部系统已经终验。游戏保持连续时间与 1/4/16/64 倍速；模型文件单独下载，人物交流免费，不以交流消耗资金或游戏等待时间。

## APK 与来源

[ARM64 APK 直链](https://raw.githubusercontent.com/initial2024/game/apk-preview/chronicle-ai-0.7.0-arm64.apk)。包名 `cn.chronicle.shanhe`，版本 `0.7.0` / code `11`，3518016 字节（约3.52MB/3.36MiB），比0.6交付包减少约73%。R8/资源压缩已开启；为优化包验收和未来性能观测保留了共享 tracing 与 Kotlin 运行库 ABI，Compose 与未使用图标仍正常收缩。DEX 5,044,716字节，单DEX；无 DebugProbesKt.bin。

SHA-256：`e7ee26053f23ca8e653295dcac2eb080ccdbd7347efaaaeb37b124e836cae472`。固定开发证书 SHA-256：`ebd2aa94bee6d781d020e45a203c9b8f2d416a4f4b654da7cfadc9a67e438c49`。APK v2/v3签名、16KiB ELF LOAD 对齐、zipalign及所含原地图逐字比对通过。私钥未提交。

该包由 CI 生成 unsigned Release 后在私钥持有环境签名。以后如改用另一私钥，Android 无法直接覆盖当前签名版本，应先导出存档。

## 本次变化

- 64 个手写案由，财赋、田土、民生、军务、人事、工造、司法、宫务各 8 个。六种固定种子事实分支、来源登记、独立复核、质证、证人保护、伪证处理和处置后续。共享调查规则，不把 384 种组合说成 384 个独立编写的故事。
- 按情境出现的告御状、军士呈情、商会申冤、联名诉状、巡幸拦驾、驿站投帖与微服诉苦。微服身份不能当众收御状或直接调簿裁案。王命强制安排也可能被陈情，并支持核实、纠正和赔偿。
- 可选新手引导与首年致命袭击保护；护卫按规模、时代设名，由既有首都兵额划拨，另记运行费。安保预兆、核查、护卫强化、行程调整与袭击后果。
- 五段章节目标、季末奏报和暂停、四条长期路线、史官起居注、常设购粮/平粜及现行预算四季预测。预测不预知随机事件或尚未下达的新命令。
- 行旅偶遇、招揽、结交、义亲与成年人政治婚配。意愿、年龄和身份知情情况真实登记；强制影响信任、忠诚、敌意、民心与后续呈状。婚配不自动开启备孕；非自愿安排、未成年人或被拘伴侣不满足备孕条件。
- 储位、传位、未成年摄政与求学返程；先君死亡与人物名册同步，邻国君主死亡登记继任者。游戏内成年虚构人物的付费秘密刺杀/缉拿委托，能力/安保/赏格影响结果，目标更替中止，失败部分返款，政治代价不抹除；缉拿不等于定罪，可释放。
- 古代清丈田亩/驿役与工业时代劳工保障。后者有维护预算和国营制品产量成本。
- 模型中心增加 ModelScope 官方 Qwen 与 HF Mirror 入口、自动择源、下载前台服务、断点暂停/续传、完整 SHA-256 与 GGUF 检查。Hugging Face 搜索和本地导入继续保留。
- Release 开启 R8 和资源收缩，保留 JNI 与流式回调，排除 DebugProbes。包名与开发签名身份延续，私钥未入库。原地图未重画，府县、建筑、军队、资源与地形系统保留。

## 已有可核实证据

CI 核心回归 92 项通过，其中一个测试穷举 64×6 个案件事实/证据组合；另含原有连续 1000 季度结算等回归。Lint、ARM64 Release 构建、Android 开发版测试包编译通过。压缩版与独立 instrumentation APK 均能编译。

- 源码/ARM64 构建：`0b5e24912eb96619b297a8f3b4c23bf6c0a71b7a`；[Actions 44063583058](https://github.com/initial2024/game/actions/runs/44063583058)，其中 build / browser-map 成功。此完整运行的两个设备作业在修复测试导航与滚动定位前失败，不能称整次运行全绿。
- 压缩版验收：[Actions 44065322668](https://github.com/initial2024/game/actions/runs/44065322668)，2/2 通过。新建→加载原图/171县→点击长安县→原生更名确认→数据库写入→地图刷新→真实 Qwen 模型加载及县令对话→杀进程→重新启动→地图与城名一致。原生 UI / WebView 协议闭环包含程序触发 DOM 选择；0.6 延续的设备地图测试另有实际触控路径。
- 完整原生回归：[Actions 44067225670](https://github.com/initial2024/game/actions/runs/44067225670)，22/22 通过（Hub 1、人物/朝政 9、界面/存档 7、地图 3、进程重启 1、新篇章/御状 1）。包含实际触控、390×844、离线规则、保存隔离、建筑/军政/运河与新界面检查。修正了旧脚本对底部导航遮挡区域的滚动定位；没有跳过战争断言。原始分组结果见 [设备汇总](upgrade-0.7-evidence/android/summary.json)。
- 优化设备测试使用开发环境编译的固定 x86_64 APK 对，`apk-preview` 修订 `41be113`，文件摘要与来源见该目录 `release-check`。源码变化仅为测试导航修正。APK 来自独立 CI 构建的最终 ARM64 包；两者 DEX SHA-256 都为 `3c702273f18c705d69e2541619dc0bc226a55b61583f74f835a7de90f17af886`，Java/Kotlin 游戏代码相同。ARM64 与 x86_64 原生库仍分别对待。
- 设备配置：GitHub Actions Android API 35 / Android 15，x86_64 Pixel 4 配置，390×844、density 160，约2473MiB RAM、2个CPU线程。真实模型聊天配置2线程/512上下文，模型不嵌入APK。未把它当成 vivo 实测。
- [JUnit 原始结果与汇总](upgrade-0.7-evidence/core-summary.json)、[优化包验收记录](upgrade-0.7-evidence/optimized/instrumentation.txt)、[模型标记](upgrade-0.7-evidence/optimized/model-output.log)、[重启记录](upgrade-0.7-evidence/optimized/restart/instrumentation.txt)均随源码保留。
- [游戏首页截图](upgrade-0.7-evidence/optimized/screenshots/release-new-game.png)、[存档地图截图](upgrade-0.7-evidence/optimized/screenshots/release-map-saved-city.png)、[县令实际模型回复截图](upgrade-0.7-evidence/optimized/screenshots/release-real-county-chat.png)。

2026-11-17 完整获取 ModelScope 官方 Qwen2.5-0.5B-Instruct Q4_K_M，SHA-256 为 `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`，与固定模型清单一致。HF Mirror 仅验证对应 Range 请求、长度和 GGUF 头；未宣称全量校验测试通过。其他模型的 Android 兼容性仍以实际加载为准。

## 未完成与边界

尚未交付一次性季度回溯、新的战争策略分支、数百种子的十年平衡统计、地图税源/治安/民心图层及移动军队/商队、音效/触感/长按用印、实测生成的自有 baseline profile、ARM dotprod/i8mm 运行时选择、KV 前缀复用、GBNF 与温控。更多跨案事件链、NPC 自主生活与复杂政治后果仍需扩展。没有将这些写成已完成。整档导出当前限8MiB/1万条历史/2000条聊天，全部聊天另可流式导出；超过上限不会删除本地记录。

季末奏报当前列经常收入/应支口径，不代表全部现金流；一次性政策预留、突发事件、贸易与战争的额外扣款应查历史账簿。储位/家族是初版，完整宗族谱系、每位君主独立的人际关系、伤病恢复和自然寿命尚未完整实现与验收。

没有 vivo Android 16 或其他 ARM64 真机实测。浏览器回归、Android x86_64 模拟器和 ARM64 APK 静态检查分别报告；模拟器推理不是手机性能承诺。后台下载在 vivo 的省电策略下仍需真机验收。

公开直链已整包重新下载并核验为3,518,016字节，SHA-256与签名后文件一致（`e7ee26053f23ca8e653295dcac2eb080ccdbd7347efaaaeb37b124e836cae472`）。
