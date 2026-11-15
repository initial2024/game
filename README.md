# 山河纪年 · ARM64 开发测试包

最新版：[下载0.2.1完整APK](https://raw.githubusercontent.com/initial2024/game/apk-preview/chronicle-ai-0.2.1-arm64.apk)

0.2.1修复重启后的地图首帧就绪与刷新，须等到真实绘制完成再确认就绪。地图 v4 与纯古代开局已接入原生游戏，保留连续时间、暂停和倍速，无模型可玩。模型独立下载。此分支存放测试安装包；源码见 [Draft PR #1](https://github.com/initial2024/game/pull/1)。

- 大小：12,799,379字节，约12.2 MiB
- 包名：cn.chronicle.shanhe
- 版本：0.2.1-dev，versionCode 4
- 游戏代码：9a2e2d61d58dc20826059ceff408f86b069cd214；后续提交仅补测试、工作流与文档
- 固定开发证书SHA-256：ebd2aa94bee6d781d020e45a203c9b8f2d416a4f4b654da7cfadc9a67e438c49
- APK SHA-256：684adbab16b276377758015f215e6b0875653a68f16a8188b975b2a74a625aa1

可覆盖此前同签名0.1.0/0.1.1/0.2.0直链包。由 [GitHub Actions](https://github.com/initial2024/game/actions/runs/43735828356/job/132878112990) 构建 release，再在私密环境使用既有开发证书签名；私钥未提交。正式签名按用户安排后续接入。

[本次接入说明](https://github.com/initial2024/game/blob/feature/world-map-v4/docs/MAP_V4_INTEGRATION.md)。vivo Android16 / ARM64真机验收、完整战争与继承仍未完成；模拟器结果不代表手机性能。

历史包：[0.1.1](https://raw.githubusercontent.com/initial2024/game/apk-preview/chronicle-ai-0.1.1-arm64.apk)。map-check开头的文件仅用于x86_64模拟器成对验收，不是手机安装包。

0.2.0属于早期测试包，有重启首帧空白问题，请优先安装0.2.1。
