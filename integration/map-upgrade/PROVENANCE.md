# 用户提供底图升级记录

2026-11-16 收到 `chronicle_world_map_v3_2.html`，SHA-256 `2553b2c5af23f80895a315154ece758d728813c39095f9649d9aad230a82fd56`。用户明确要求在此基础上升级疆域。

该文件自报 v1 协议。保留其地理与画布交互基础，并沿用此前 v4 接入中增加的 v2 数据协议、安全原生桥、城邑兴废、交通线、增量同步与旧档迁移，避免损失功能。未重新绘制替代地图。

与已集成上游的原始常量比对：
- coast: 相同
- rawNames: 相同
- peaks: 相同
- rivers: 相同
- lakes: 相同（位于 sand 的同一行声明，另行核对）
- silk: 相同
- desert: 相同

本次增加共用边界的七块原创剧情示意疆域、选择轮廓、争议纹理、地区标签、图例与层级详情。几何仅作游戏示意，不作历史边界或测绘数据。既有稳定 ID、归属变化和城邑记录保留。

## 实际替换与可复现升级

应用仅加载 `app/src/main/assets/maps/chronicle_world_map.html`，原 v4 Android 资源文件已删除。新版以用户 v3_2 原文件为输入，应用 `upgrade.patch` 得到当前资源；执行 `python scripts/upgrade-map.py --check` 可逐字验证。原 v4 仅留在 integration/map-v4/upstream 作为历史参考，不是 Android 加载资源。旧地址被安全白名单拒绝。
