# 山河纪年 · Chronicle AI

原创 Android 原生离线历史策略游戏，采用可暂停连续时间，支持 1×、4×、16×、64×。

[English](README.md) · [完整历史需求（中文）](docs/REQUIREMENTS.zh-CN.md) · [完整需求与验收表（英文）](docs/REQUIREMENTS.md) · [开发接手说明](docs/HANDOFF.md) · [开发源码 Draft PR](https://github.com/initial2024/game/pull/2)

**当前仍是开发版，未完成历次全部需求。** 默认分支只更新文档不代表合并了开发代码；接手请使用 `feature/character-chat-hf-search`。

## 可安装版本与证据

[下载 0.7.1 ARM64 APK](https://raw.githubusercontent.com/initial2024/game/apk-preview/chronicle-ai-0.7.1-arm64.apk)，3,518,016 字节，约 3.36 MiB；模型与 APK 分离，无模型可玩。

SHA-256：`7808992b153d3d6f15566e06572b54924584de639391ad41cc798caf87a474ed`。

[构建与测试报告](https://github.com/initial2024/game/blob/feature/character-chat-hf-search/docs/UPGRADE_0.7.1_TESTING.md)：92 项 JVM 测试、26 项原生回归、两项压缩版真实模型/进程重启验收通过。0.7.1 构建、浏览器、原生设备与压缩版模型作业通过，报告列明可选跳过项和限制。设备是 Android 15 x86_64 模拟器，尚无 vivo Android 16 / ARM64 手机实测。

## 当前已有

古代与工业初兴开局、国家规模与国际格局、经济科技、八州十四府一百七十一县、城市建筑与地形物产、县兵州军和俸禄、巡游微服、证据查案、64 个手写案由、科举任免、学院派学、免费人物聊天和旨意预览、家族命名、成年伴侣与独立备孕许可、初版传承护卫、组织谍报、新手引导、季末奏报、多存档，以及保留用户原图的离线舆图。

模型中心支持 Hugging Face 搜索 GGUF、本地导入、内存推荐、ModelScope / HF Mirror / 原站入口、断点前台下载、校验与本地推理。数值、案件真相、生死和历史由规则引擎裁决。

## 所有后续任务

完整列表见[中文需求登记](docs/REQUIREMENTS.zh-CN.md)，包括发送消息自动加载模型、完整历史策略、独立地方财税、军队战争外交、四级反腐、高质量案件链、人物谱系传承、阴谋组织、AI 长期记忆、一次性回溯、财政平衡、原图图层和移动单位、界面音效、更多模型实测、推理性能、存档迁移和 vivo 真机验收。

“发送消息自动加载已安装默认模型”已在 0.7.1 通过真实 GGUF 验证；地图指挥与战争取舍正在继续实现。没有把未来计划写成已经完成。

## 开发约束

固定包名 `cn.chronicle.shanhe`，固定开发签名私钥不入库，更换签名不能直接覆盖当前 APK。无需付费 API，不自动上传存档，不修改其他项目，不删除用户数据，不未经确认商业发布。政治强制行为须留下真实代价和申诉；生育保持成年、自愿和独立许可。

参考的是实际游戏《历史时代》，以及用户提供的 Tianming / 东方末日设计思路。检查时后两者无可识别仓库许可证，未导入其代码或素材。构建命令、分支、测试证据及注意事项见[英文接手说明](docs/HANDOFF.md)。
