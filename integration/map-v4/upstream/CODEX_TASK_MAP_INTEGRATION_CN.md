# Codex 云端执行指令｜《山河纪年》世界舆图 v4 接入

## 0. 任务性质
我提供了 `chronicle_world_map_v4.html`、`chronicle_scenario_demo_v4.json`、`chronicle_scenario_blank_v4.json`、`CHRONICLE_MAP_V4_INTEGRATION.md`。以这批实际文件为唯一地图基线，不允许自行重新画一套低功能地图，亦不允许用旧 v3.2 覆盖 v4。

**现在的目标是将地图作为《山河纪年》游戏的独立可选模块接入项目。** 先核查游戏仓库与实际开发分支；若游戏主体尚未建立/未授权指定仓库，必须仅创建独立的可运行 Android 集成样例或 Draft PR，不得修改 DSH、Orbit Mail、Local Dream 等其他项目。

## 1. 工程边界
1. 地图不预设朝代、年号、国家、历史城市名称。默认空白；实际名号、疆域、城邑状态、交通道路由当前游戏存档提供。
2. 地图是 **read-only view + 用户选择界面**；不拥有经济、战斗、反腐、外交、巡游或领土扩张的规则结算权。所有战果与所有权变化只由 GameCore 校验并提交到本地数据库，再以最新快照更新地图。
3. 保留 2.5D 山水、可旋转相机、触控缩放、中文地名、城市搜索、辖区索引、三种主题、剧情槽位和 JSON 导入导出。
4. 地图内置海岸线是简化的东亚参考数据，并非精确历史地图；新版 custom geography 是 **本地 55°～155°E、-4°～65°N** 的示意陆地多边形，不是完整全球引擎，不可在 UI 或产品介绍中冒充全球测绘能力。
5. 允许对地图源文件及新独立集成模块进行修改、测试、生成 APK/ZIP；不允许改动其他 GitHub 仓库、自动发布应用商店、公开私人存档或提交私钥。

## 2. 首先执行的工作
1. 检查目标仓库、分支、最近提交与现有 Android 工程。若未发现《山河纪年》主工程，建立最小独立 Android 地图集成工程，留可接入接口，不虚构已有游戏完成度。
2. 核对输入 HTML 的 SHA256，校验 `window.ChronicleMap.getSchema()` 为 `chronicle-map-scenario/v2`。
3. 打开单文件 HTML，实测空白地图、`疆域 · 势力` 面板、手工圈定、导入虚构演示、旧 v1 JSON 升级、更新增量拒绝重放。
4. 保持可回退基线，提交最小可复现实验，再开始适配。

## 3. Android 集成路线
- 优先 Kotlin + Jetpack Compose。增加独立 `WorldMapScreen`，内部通过 Android WebView 显示 `chronicle_world_map_v4.html`。
- 将 HTML 放入应用 `assets` 并通过 `WebViewAssetLoader` 的 appassets 安全本地域加载。不要依赖 `file://`/明文 HTTP/公网 CDN。只打开受信的本地资源；禁用非必要跨域文件访问和任意外部导航。
- Javascript 为此受信地图页面启用。对 JS→原生的点击事件使用限定 `appassets` origin 的 `WebMessageListener` / WebMessagePort 等经过限制的消息通道；不要向任意网页开放高权限 JS bridge。
- 页面加载完成后，原生从当前游戏存档生成 **完整 v2 场景 JSON**，使用安全编码和 `evaluateJavascript` 调用 `ChronicleMap.applyScenario(...)`。请特别处理引号、换行、反斜杠和 Unicode 转义，禁止拼接未经转义的用户字符串为 JS 代码。
- 游戏中若只是改变当前势力、城邑兴废、道路或疆域，提交数据库事务成功后，发 `ChronicleMap.applyWorldDelta({sceneId,revision,changes})`。其中 `revision` 严格递增；切换存档或地图地理底图时用 `applyScenario()` 全量替换。
- 在 JS 端监听 `chronicle-map:place-select` 和 `chronicle-map:territory-select` 并转发仅包含 sceneId 和稳定地点/疆域 ID 的消息到原生。原生对 ID 及当前存档再验证，展示地区信息、巡游入口、官员审计或城市建设界面。
- 不要允许网页事件直接执行修改财政、军队或官员状态的业务命令。任何操作均需经原生 GameCore 权限与资源检查。

## 4. 游戏数据映射
- 城市使用稳定 `placeId`；省郡县等行政层级由 `groups` 的父子关系和 `places[placeId].groupId` 决定。
- `polities` 是势力 ID、名称和展示色；所有权以 `territories[].polityId` 为准；`status='contested'` 为边界争议视觉提示。
- `placeStates[placeId].status` 支持 active / planned / ruined / abandoned / hidden，状态变化必须由游戏世界推进决定。不能因为 UI 上选择“废墟”而扣除游戏经济资源。
- `routes` 是两地点之间的逻辑关系和简化视觉连线，**不是**经过地形寻路的道路。真实巡游路线、行军路线和运输时间需要独立的 Pathfinding/TravelEngine 与道路网数据支持。
- `geography.kind='custom'` 可在 v4 中替换陆地轮廓，仍使用局部投影与平坦地形，禁止显示旧东亚城镇或旧山川；未来若实现真正的架空全球大陆，则另建地理包和投影接口，不能靠修改旧数据名伪装。
- 支持一个存档多个时间点/多个势力拥有地；每次数据更新要先校验 ID、范围和多边形，不允许异常数据破坏正在运行的地图。
- 每个剧情保存其独立地理覆盖、命名和所属权。地图 HTML 不作为原生存档唯一来源：以 Room/SQLite 事务为主，HTML 本地存储只是单文件演示缓存。

## 5. 验收路径
### 静态与浏览器
- 运行 `node --check` 验证单文件脚本。
- 在 390×844、320×720、844×390 三种视口进行移动渲染与交互测试，记录浏览器与真机的实际测试环境。
- 测试默认无朝代、无国名；导入演示才出现虚构势力和边界。
- 测试三个疆域分别着色；选中疆域和改变所有者；点城市出现状态；地图点击增加 4 个多边形顶点并保存；不合法与自相交多边形被拒绝。
- v1 文件可以升级为 v2；旧槽位在多剧情切换中不相互覆盖；允许导出并再次导入。
- 发 `revision:1` 更新成功，再用同一个 revision 重放必须被拒绝；sceneId 不匹配也必须拒绝；失败前后的存档字节状态不应改变。

### Android 验收
- APK 在真实 Android 设备打开地图、转动、双指缩放、搜索、切换主题、载入当前存档。
- 模拟原生经济/战争事件引发一条 **已完成事务** 的疆域转移或城市兴废，再通过 JS 接口显示变化；关闭并重开后仍正确。
- 多存档 A/B 中同一地点分别命名，两条世界线不串线。
- 网络断开仍能打开地图与游戏存档；不访问互联网下载地图脚本。
- 后台恢复、低内存、横竖屏以及多次进入退出地图无明显内存泄漏。
- 若暂时没有真实 vivo 设备，不得将桌面 Chromium 或模拟器测试称为真机通过。

## 6. 交付
- Android 工程完整代码及 v4 HTML 原文件。
- `WorldMapScreen` 和安全 WebView 集成组件。
- `MapSnapshotMapper`：GameCore 世界快照→v2 JSON。
- `MapDeltaDispatcher`：Room 事务完成→严格递增的版本更新。
- 原生对外暴露“打开地区详情 / 巡游目标 / 反腐调查详情”的事件接口，但**不要**凭空宣称巡游/反腐系统已经实现。
- 单元测试、UI 测试、浏览器交互测试及真实 CI 日志。
- 固定包名、固定签名策略，签名密钥不提交到公共仓库。
- GitHub Actions 真实构建的 APK 和 ZIP 下载链接；如没有构建成功或权限缺失，说明阻断，绝不伪造直链。
- 各个接口能力、尚未实现内容、可回退提交 SHA 及证据。

## 7. 停止条件
发现仓库身份不明、旧存档迁移可能丢数据、地图权限越界、任意网页可访问原生敏感 bridge、强制覆盖生产签名密钥、真机表现与测试声明不一致时停止相关高风险操作，保留可运行阶段成果和证据，不绕过限制。

现在先完成真实工程审计和可运行的 v4 地图接入样例；仓库实际存在且拥有写入授权时再提交独立分支/Draft PR。不是制作第二张地图，也不是只生成策划文档。
