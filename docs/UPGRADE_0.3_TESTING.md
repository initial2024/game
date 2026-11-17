# 0.3.1 开发版：疆域、人物交流与学院

## 实现范围

- 应用资源已实际替换为用户最新 HTML 的升级版，原 v4 Android 文件已删除；构建检查从用户原文件应用严格补丁，逐字验证当前资源。地理、地名节点、地形与交互基础继续保留。原文件和 SHA-256 见 `integration/map-upgrade/PROVENANCE.md`。
- 原创七区非矩形疆域；共享边界、归属颜色、争议斜纹、选中轮廓、避让城名的地区标签、行政层级及城邑状态详情。仍是示意地图，不提供地形寻路。
- 既有矩形存档通过显示迁移获得细化轮廓；自定义边界、稳定 ID、归属及历史保留。SQLite 仍为权威状态。
- 人物对话独立全屏窗口，固定输入区，可取消本地生成。使用 GGUF 自带聊天模板，流式输出经角色信息过滤。无模型或推理失败时明确标示规则模板。
- 正式交流需勾选确认：2万两、1日、每人物30日冷却。倾听/勉励/质询和侮辱产生不同关系结果，数值由引擎结算。普通聊天不刷属性。暗访 NPC 只知道伪装身份。
- 国家学院1–5级、真实在世人物担任讲席，师资由能力推导。国内与海外派学有独立行程、学期、费用、学额、离岗代价和返程；授课收益每日累计，师资缺席暂停，外交恶化可中断，死亡无结业奖励。
- Hugging Face 公开 API 在线搜索任意模型家族；仓库与文件分页、许可显示、固定修订下载、SHA-256、暂停/恢复、本地导入。可搜索不代表任意架构都受当前 llama.cpp 支持。分片、投影附件、无哈希或未知许可不可自动安装。
- SQLite v1→v2 非破坏迁移；对话按槽与人物隔离，备份/导出/导入保留对话。每槽最多2000条，达到上限明确提示导出，不静默删除。

## 本地验证

- 43项 JVM 测试通过，包含1000次季度推进、确定性、证据/身份、学院等级与师资、学业行旅、死亡/外交中断、正式交流冷却、Hugging Face 目录与不受信分页拒绝。
- `:app:lintDebug`、ARM64/x86_64 debug APK 与 androidTest 编译通过。
- Chromium 浏览器：390×844、320×720、844×390；原地图交互、v1场景迁移、只读桥、乱序/无效增量拒绝、自定义地理、疆域详情与城邑链接通过。

## Android 与 APK

最终CI在 Android 15/API35 x86_64 模拟器、390×844、density160、Pixel4、2核、约2.42GiB RAM上通过11项API/界面/数据库测试和1项真实模型综合测试。GPU为软件渲染，推理为CPU；不是ARM64手机性能结果。

[最终Actions 43794323760全部必需任务成功](https://github.com/initial2024/game/actions/runs/43794323760)。签名Secrets任务按用户决定未启用；实际APK由CI的unsigned Release产物在私有工作区签名。尚无 vivo Android16 ARM64 真机，不能将浏览器、桌面或模拟器测试称为手机验收。

## 限制

学院的海外师资为已登记国家的固定教学属性；尚未模拟外国完整教师谱系。对话是人物交流，不会自行任命、判罪或执行政策。模型输出可能不准确，不作为证据或结算。大模型、所有 HF 架构与任意模板并未逐一验证。地图沙盘编辑不改变游戏财政或领土；真实变化仍需原生规则行动。

## 实际 APK 检查

最终开发包：`cn.chronicle.shanhe` / `0.3.1-dev` / versionCode 6，仅ARM64。

- 构建源码：`6d2f28b79fc65ad78c6382ed0b634889ea0e5c11`。
- Actions构建：[43794323760](https://github.com/initial2024/game/actions/runs/43794323760)。Release unsigned产物编号：14193926594。
- 在私有工作区使用已有稳定开发证书签名，私钥未上传。
- APK大小12,869,008字节（约12.27MiB），原生库使用Deflate压缩，权重不包含在APK中。
- APK SHA-256：`5d200b4852f89f3b09f24f129c28b29d5f13418182a4c7d63f1007569680458b`。
- 开发证书SHA-256：`ebd2aa94bee6d781d020e45a203c9b8f2d416a4f4b654da7cfadc9a67e438c49`。
- APK内仅有新路径`assets/maps/chronicle_world_map.html`，无旧v4地图资源。文件逐字匹配从用户上传HTML升级得到的结果，SHA-256为`5746a8bae74c8a4638b9cbfeb967478e4abd88b4060630ed802d9e6b513a272a`。
- `apksigner verify`、`zipalign -c -P 16 4`通过；三个ARM64动态库的全部LOAD段均为16384字节对齐。

详细元数据：[apk-build.json](upgrade-0.3-evidence/apk-build.json)。开发版使用现有私有开发身份，可覆盖同签名旧开发版；用户后续提供正式签名。

## 测试证据与真实模型

- [界面/存档5项](upgrade-0.3-evidence/existing-game-instrumentation.txt)、[人物聊天/派学/数据库迁移2项](upgrade-0.3-evidence/character-instrumentation.txt)、[地图闭环2项](upgrade-0.3-evidence/map-instrumentation.txt)、[强制停止后地图恢复1项](upgrade-0.3-evidence/map-restart-instrumentation.txt)、[实时HF目录1项](upgrade-0.3-evidence/hf-live-instrumentation.txt)均成功。
- [真实模型综合测试](upgrade-0.3-evidence/real-model-instrumentation.txt)成功，耗时418.968秒。使用官方Qwen2.5-0.5B-Instruct Q4_K_M，完整491,400,032字节，SHA-256与目录一致。模型不在APK内。
- [实际推理日志](upgrade-0.3-evidence/real-model-model-output.log)：中文叙事、结构化FARM指令、重复短推理、JNI中断后恢复、流式人物回复通过。进一步通过应用本身的`ModelManager.import → load → chat`，不是仅测试JNI或固定文字。
- 应用适配器实际回复为「我主要的目标是整顿军需，确保国家的经济和军事实力得到提升。」，18次流式更新，104,765ms。聊天最多96个输出token、120秒原生截止时间，仍可取消；超时/错误明确回到模板。
- 原生加载调用256ms（此前文件哈希已读入缓存，不是手机冷启动速度）；JNI测试阶段PSS观察值693,091KiB、VmHWM797,972KiB。该值不能视为vivo手机或完整应用适配器全程的峰值内存认证。没有足够数据报告可靠token/s。
- [实时HF记录](upgrade-0.3-evidence/hf-live-model-output.log)实际返回SmolLM2仓库、12项结果、固定修订、105,454,432字节Q4_K_M及SHA-256。此模型只验证了目录元数据，未冒充已做权重推理。
- 模型测试时模拟器网络开关已关闭；先在线取得的目录与文件不影响离线规则/推理。Compose对话界面测试使用明确标注的模板，真实模型另外验证应用适配器。加载模型时的完整触控取消流程、ARM64手机连续推理/温度/电量/长会话仍待真机验收。

截图：[实际升级地图](upgrade-0.3-evidence/map-native-ancient.png)、[人物对话界面（模板）](upgrade-0.3-evidence/character-chat.png)、[派学名册](upgrade-0.3-evidence/academy-student.png)。[浏览器图例截图](upgrade-0.3-evidence/browser-legend-390.png)仅为浏览器验证，不冒充Android截图。

## 交付链接

[完整已签名ARM64 APK直链](https://raw.githubusercontent.com/initial2024/game/apk-preview/chronicle-ai-0.3.1-arm64.apk) · [源代码Draft PR #2](https://github.com/initial2024/game/pull/2) · [签名与哈希元数据](https://raw.githubusercontent.com/initial2024/game/apk-preview/chronicle-ai-0.3.1-build.json)

[公开下载实取校验](upgrade-0.3-evidence/public-download.json)记录HTTP状态、完整字节数和SHA-256。
