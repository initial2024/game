# 世界舆图 v4 / 0.2.1-dev 交付报告

[下载完整 ARM64 APK](https://raw.githubusercontent.com/initial2024/game/apk-preview/chronicle-ai-0.2.1-arm64.apk) · [源码 Draft PR #1](https://github.com/initial2024/game/pull/1) · [完整源码 ZIP](https://github.com/initial2024/game/archive/refs/heads/feature/world-map-v4.zip) · [实测截图](MAP_V4_SCREENSHOTS.md)

## 安装与来源

版本0.2.1-dev，versionCode 4，固定包名 cn.chronicle.shanhe；minSdk26 / targetSdk36；仅打包arm64-v8a。APK 12,799,379字节，约12.2 MiB，原生库使用ZIP压缩，模型权重不打包。

APK SHA-256：`684adbab16b276377758015f215e6b0875653a68f16a8188b975b2a74a625aa1`。

由 [最终绿色 Actions](https://github.com/initial2024/game/actions/runs/43735828356) 构建unsigned release，再于私密环境使用既有开发证书签名。实际下载了CI产物，并非用本地重编译包冒充CI构建。CI未签名产物SHA-256：`e6c574113d3ac03ced34bd2ee807f2b8dbaf7e4c6f4f7c50731a3dc80a7c6a9c`。

游戏代码：`9a2e2d61d58dc20826059ceff408f86b069cd214`。其后的提交只补充测试、工作流和文档；app/src/main、core/src/main与构建依赖未改变。仍保留 [CI原始产物](https://github.com/initial2024/game/actions/runs/43735828356/artifacts/14170249073)，该ZIP中的包未签名，不应直接安装。

开发证书SHA-256：`ebd2aa94bee6d781d020e45a203c9b8f2d416a4f4b654da7cfadc9a67e438c49`。apksigner v2/v3及zipalign验证通过，APK内三项ARM64原生库的ELF LOAD均至少16KiB对齐。重新下载了公开直链并比对长度与完整哈希。可覆盖之前同签名0.1.0/0.1.1/0.2.0直链包；Actions常规debug包使用另一测试签名，不要混作覆盖更新。

私钥未提交。正式签名Secrets按用户安排后续接入，signed-apk任务本次跳过；换正式证书的安装迁移仍需单独处理。本次没有公开商业发行或应用商店上架。

0.2.1额外修复了重启后的首帧空白：JS完成真实Canvas绘制后回传只读frame状态，原生再等待WebView.postVisualStateCallback与显示帧、主动失效重绘，之后才确认场景ID和版本已就绪。不能再把JSON接口可读当成画面可见。绘制有12秒超时，返回前台会重新发送可信快照；不会改写存档。完整回归8项与重启画面再次在 https://github.com/initial2024/game/actions/runs/43737056199 通过。

0.2.1完整工作流在首次坐标测试中受到键盘动画影响，重跑已通过；后续测试加入DOM/原生尺寸连续稳定等待，完整8项Android验收又在独立专项通过。截图专项关闭模拟器Vulkan并等待空闲帧，以排除旋转时旧缓冲画面；这不是vivo图形性能数据。

## 已实现闭环

1. 载入SQLite中的当前存档，并显示国家、四地区、三邻国、城市、疆域及道路。
2. 通过真实Android触控点选查找和城市结果，JS限定origin回传sceneId/placeId，原生再次验证目标。
3. 在原生面板申请更名，确认后扣除5万两，经过1日办理，历史保留原记录。
4. 在原生面板营建新邑，扣除120万两，地图先显示planned，120日后由规则验收为active；可以再次点选新城查看真实状态。
5. 在两个存档中分别显示不同国家和同一稳定地点的不同名称；切换后不串线。
6. 横竖屏重建后重新加载当前状态，反复进入退出4次，活动组件计数归零。
7. 沙盘加载虚构示例，不改变游戏World；返回后恢复游戏地图。
8. 通过adb真正force-stop目标进程，另起instrumentation与Activity，再读取上次存档。城名、版本号和已建新邑状态一致，读档默认暂停。

原始日志包含MAP_NATIVE_CLOSED_LOOP=true与MAP_PROCESS_RESTART_CONSISTENT=true。这里的进程重启不是仅调用Activity.recreate。

同时加入纯古代王朝开局，保留工业初兴。古代使用独立WORKSHOP指令与时代合法性校验，不能直接建设工业工厂；自由文本拒绝铁路/蒸汽等近现代设施。模板叙事、手工业/格物标签、驿道及邻国名适配古代。旧存档不自动变成古代。

修复了WebView在零尺寸时初始化导致的空白，以及嵌入页面把系统栏区域计入CSS视口导致的触控偏移。地图窗口处理系统栏，嵌入页面关闭viewport-fit=cover并使用interactive-widget=resizes-content；记录的DOM与原生尺寸一致后才执行点选验收。

## 测试范围与环境

| 层级 | 实际结果 | 环境 |
| --- | --- | --- |
| 规则JVM | 35项通过：原25项+地图/时代10项 | JDK17，纯Kotlin，与WebView和模型隔离 |
| 浏览器 | 原始v4与适配版均通过三视口、圈地、迁移和协议检查 | Playwright1.56.1 / Chromium141.0.7390.37，桌面Linux |
| Android | 原有界面/存档5项+地图2项+进程重启1项，共8项通过 | GitHub Actions KVM，Android15/API35，x86_64，Pixel4配置，2vCPU，约2.42GiB RAM |
| 手机 | 尚未测试 | 无连接的vivo Android16/ARM64手机 |

浏览器视口为390×844、320×720、844×390。Android覆写390×844、density160，横屏重建另行验证；WebView为Chrome124.0.6367.219。模拟器与桌面结果都不能称为真机通过或手机性能。

规则测试覆盖1000季度多种子/难度模拟、确定性重放、资源与并行事务、人物生死、证据和案件路线，并新增地图稳定ID、原县级节点兼容、v1场景升级、几何/循环引用/保留ID拒绝、版本调度、计费工期、更名审计、条约前置条件和古代1000季度模拟。

浏览器实测包括空白场景、三个势力着色、修改展示归属、查找城市、主题/缩放/拖动、实际四点圈地、无效/自交多边形拒绝、多剧情隔离与JSON往返。增量revision重放、错误sceneId和非法更新在失败前后保持场景字节不变；自定义地理隐藏原底图城市；正式模式拒绝编辑并回传稳定ID。

Android安全与迁移测试覆盖原保存JSON缺省字段兼容且读取不重写、多槽隔离、事务/版本检查、错误origin/文件URL/场景/地点/未知字段拒绝，以及引号、反斜杠、换行和U+2028/U+2029编码。所有Android规则与地图验收在来宾Wi-Fi和移动数据开关均关闭后运行。

反复退出测试验证组件销毁路径和活动数量归零，尚不是完整heap泄漏分析。SAF文件选择器的全部供应商、物理双指手势、极大字体、低内存杀进程、连续数小时负载及地图与模型同时运行，需要手机继续验收。

[规则与完整Android CI](https://github.com/initial2024/game/actions/runs/43735828356) · [独立进程重启专项](https://github.com/initial2024/game/actions/runs/43737056199)。快速专项使用本地构建、同证书重签并做哈希校验的配套x86_64 APK以减少调试等待；最终交付APK来自前述完整CI，二者未混称。

## 本地模型回归

本次没有改变JNI推理实现。[0.2.0开发中的原生GGUF专项任务](https://github.com/initial2024/game/actions/runs/43728800069/job/132858177484)成功，Android模型测试1项通过；该旧工作流的地图UI当时尚未通过，地图最终验收使用上面的绿色工作流。

实际Qwen2.5-0.5B Q4_K_M（491,400,032字节，Apache2.0），CPU2线程、512上下文：加载调用616ms，短问候14768ms，真实中文叙事和FARM JSON有效，连续生成、取消后恢复与卸载通过。加载计时不含下载/哈希，可能为热缓存；12 tokens是上限，不是实际生成数，不能据此推导tokens/s。PSS单次691,286KiB，进程VmHWM793,860KiB；这是含测试和UI的x86_64模拟器进程数据，不是模型独占内存或vivo峰值。

本次最终地图工作流未重复下载模型；不能将native-model-test的skipped记作新推理通过。古代长篇质量、地图与模型同时负载以及其他量化/1.5B候选尚未完成手机实测。

## 尚未完成

- 关于页文字仍沿用0.2.0，实际系统包信息为0.2.1-dev / versionCode4；后续统一展示。
- vivo Android16/ARM64实机安装更新、真实多指触控、低内存、温度/耗电与持续性能；16KiB对齐检查不代替真机验收。
- 自定义地理仅局部投影55–155E、-4–65N，不是全球测绘；无洞/跨日界线/共享边拓扑，路线只为示意，不是地形寻路。
- 新邑每地区一个预设节点，旧城修复为首个示范；废弃/毁坏/隐藏状态在协议中支持，但战争/灾害的完整城市兴废链尚未完成。
- 示范边地移交是付费且有前置条件的和平条约，不是完整战争；暂不自动新增第五个可治理地区。
- 原生游戏地理通过可信存档提供，沙盘不能一键覆盖正式世界。原生默认开局仍为四地区；规模模式改变国力，不改变默认底图面积。
- 完整继承、更多地方案件、阴谋网络、朝代特定制度和时代跃迁等既有P2项未在本次完成。

源码与接口细节、原始地图哈希及回退点见[接入说明](MAP_V4_INTEGRATION.md)。旧版TESTING.md只记录0.1.1，不代表此次地图验收。
