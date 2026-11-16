# 0.4.0：免费交流与真实府县治理

本次修正0.3.1中交流收费、办理时间和30日冷却的问题。人物交谈现在免费、即时、不推进日历、不受政令队列限制；同一经历下重复话题不重复增加关系，不能刷忠诚。旧版已排队的交流撤销并退回预留资金，记入历史。AI推理的异常截止时间属于运行保护，不是玩家聊天时长或付费额度。

地图沿用用户上传HTML的地理、布局、原城市名及府县节点。此前只有四个大区和少量模拟城市映射；现在增加14个府、59个县，连同原四个治所为77个国内城邑。府县坐标为原图参考点，府域划分为游戏治理示意，不声称真实历史疆界。

每个城邑可打开概况、建筑、官员三个页面：人口、地方预算份额、税收份额、治安、腐败、民心、产业、健康、教育；地方官有实际登记的人物身份、能力与忠诚，可交谈。八类建筑为官署、集市、作坊、驿路、常平仓、医馆、学堂、兵营，具有0–5级、真实造价、施工时长、竣工、财政与属性效果。地方审计与赈济由规则结算，审计不自动定罪。

人口按既有大区人口分配，预算和基础税源属于既有财政的分摊，不因展开地图凭空增加国库。新增建筑才产生额外税源与维护；资金不足受信用上限约束，并会造成扩建建筑退化。县城并非只画了图标：建筑及在建工程保存在世界存档，经过统一事务提交。

旧档读取时以固定规则登记新增府县及地方官，不删除原人物、存档或历史。旧导出文件按原始历史JSON校验，兼容尚无建筑等级字段的命令格式。聊天不再因2000条配额停止，可流式导出全部对话；整档JSON导出仍受原有体积与条数预算约束，超过时明确提示，不静默丢弃记录。

本地54项核心测试、Android编译与lint通过。新增验收包括原图府县层级、人口守恒、工程付款/工期/重复提交、独立城市状态、维护信用约束、旧交流退款，以及县城建筑在进程重启后的持久化。最终构建与13项Android界面/API/存档测试全部通过；详见下列证据。

保留的边界：县级财政目前是大区预算和国家税源的可追踪分摊，不是独立地方税制。国家学院与城内学堂作用不同，未实现全国逐县学籍系统。完整战争、君主继承、每县独立案件与城市街巷自由探索仍未完成。尚无vivo Android16 ARM64真机验收；模拟器和浏览器证据分开记录。

## 实际交付

[下载0.4.0完整ARM64 APK](https://raw.githubusercontent.com/initial2024/game/apk-preview/chronicle-ai-0.4.0-arm64.apk) · [源码Draft PR #2](https://github.com/initial2024/game/pull/2) · [最终成功Actions](https://github.com/initial2024/game/actions/runs/43804878419)

- APK源码：`1ff8d5c49a595db55af883c74a5b216c9c43b2ae`，后续提交仅更新文档与证据。
- 包名`cn.chronicle.shanhe`，版本`0.4.0-dev`，versionCode 7。
- 12,918,160字节（约12.32MiB），原生库Deflate压缩、模型权重与APK分离。
- APK SHA-256：`cc6be752161c448ec6743f0653793dc7aa46a8cc3b9b2f204ccf0d3ce5ffecd2`。
- 沿用固定开发证书，证书SHA-256：`ebd2aa94bee6d781d020e45a203c9b8f2d416a4f4b654da7cfadc9a67e438c49`。可覆盖之前同开发证书的直链安装包，不要求删除存档。正式生产签名仍由用户后续提供，私钥不进仓库。
- 来自Actions的unsigned Release产物，编号14197409478，在私有工作区签名。`apksigner verify`与`zipalign -c -P 16 4`通过，三个ARM64库的所有LOAD段为16384字节对齐。
- APK中地图逐字匹配原上传HTML经升级补丁生成的资源，SHA-256 `58a1b1e41153bf22c8919f6ebddc17599cb02a2a02149e3b2eb5c588e8823270`。
- [签名及构建元数据](upgrade-0.4-evidence/apk-build.json)、[公开直链实取校验](upgrade-0.4-evidence/public-download.json)。

## 测试范围

54项核心测试通过，包括1000次季度推进、确定性、人口守恒、城市工程计费/工期/重复提交、财政信用与维护不足退化、府县层级、免费交谈话题防刷、旧收费交流退款和替任官员函询。

Android实测配置为Android15/API35 x86_64模拟器、Pixel4配置、390×844、density160、2核、约2.42GiB RAM、软件GPU。不是vivo或ARM64真机结果。

- [免费聊天/学院与旧数据库迁移2项](upgrade-0.4-evidence/free-chat-instrumentation.txt)：聊天前后国库与日期相同，没有交流排程，不显示收费入口。
- [原游戏/存档及旧版导出兼容6项](upgrade-0.4-evidence/existing-game-instrumentation.txt)。
- [地图与县城建筑3项](upgrade-0.4-evidence/map-and-cities-instrumentation.txt)：实际触摸查找长安县、打开原生县城、支付80万两升级常平仓、时间推进到竣工，建筑由1级到2级。
- [进程强停后恢复1项](upgrade-0.4-evidence/restart-instrumentation.txt)：同时读取另一个县城存档，确认常平仓仍为2级；不同存档分别保留。
- [实时Hugging Face目录1项](upgrade-0.4-evidence/hf-live-instrumentation.txt)。

浏览器保留390×844、320×720、844×390检查；增加真实Kotlin规则导出的府县地图夹具，验证四大区折叠树、府→县、长安县稳定ID选择及原有地图能力。

本轮[真实模型综合测试子任务](https://github.com/initial2024/game/actions/runs/43802398029/job/133087581057)成功，使用0.4.0模型适配器和官方Qwen2.5-0.5B-Instruct Q4_K_M，哈希与目录一致。该运行的早期UI验收失败，随后修正窗口层级与脚本；最终上方43804878419的全部UI验收通过。模型组件没有在窗口/新官定位修复中改变，不把早期失败的整个工作流称为全部通过。

[真实模型日志](upgrade-0.4-evidence/real-model-model-output.log)记录中文叙事、严格政策JSON、连续推理、取消后恢复，以及应用`import → load → chat`的真实回复「整顿军需，恢复秩序。」：71,259ms、7次流式更新。模型没有固定文字代答；模板界面明确标注模板。模型每次生成有异常截止保护，玩家会话没有时长或次数收费限制。

原生加载调用494ms（已经哈希预热文件，不是手机首次冷启动时间），JNI阶段观察PSS693,279KiB、VmHWM797,704KiB；不能当成vivo手机或整应用全程峰值内存认证。未报告未经测量的手机吞吐、温度或续航。实际vivo Android16兼容与长会话仍待验收。

## 截图

[免费交谈](upgrade-0.4-evidence/free-character-chat.png) · [原图接入后的府县地图](upgrade-0.4-evidence/map-native-ancient.png) · [长安县概况](upgrade-0.4-evidence/county-city-overview.png) · [真实施工](upgrade-0.4-evidence/county-building-started.png) · [竣工后二级常平仓](upgrade-0.4-evidence/county-building-completed.png)。均为Android模拟器原始截图。
