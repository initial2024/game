# 《世界舆图》v4｜独立地图与游戏接入说明

## 组成
- `chronicle_world_map_v4.html` — 完整单文件 HTML，包含 JS/CSS，无 CDN 和外部字体依赖；离线可用。
- `chronicle_scenario_blank_v4.json` — v2 空白场景，默认无国名、无年号、无疆域。
- `chronicle_scenario_demo_v4.json` — **虚构演示数据**，含三个势力、三块示意疆域、两个示例新聚落和一条示意道路；不是历史资料。
- `chronicle_world_map_v4_territories.png` / `..._custom.png` — 浏览器实际渲染截图。

## 功能
1. 保留 v3.2 的地图旋转、缩放、主题、查询、历史占位点与县级节点，默认不展示历史名称。
2. 新增地图右侧的剧情疆域渲染，支持归属、无主、争议状态；点击疆域显示详情。
3. 「疆域 · 势力」可修改疆域归属，通过四个以上画面触点圈定新的疆域（建议选择移动或平移模式进行定位后再绘制）。
4. 地理包 `builtin` 使用原先东亚及周边示意底图；`custom` 用场景提供的 `landPolygons` 替换陆地轮廓，并隐藏原有地区/城镇和山河，呈现简化的平坦 2.5D 自定义大陆。
5. 城邑兴废 `placeStates`、逻辑交通线 `routes`、v1→v2 兼容迁移和游戏引擎增量更新接口。

## JSON schema v2 关键字段
```json
{
  "schema":"chronicle-map-scenario/v2",
  "id":"your-save-id",
  "revision":0,
  "meta":{"title":"","era":"","seal":""},
  "polities":[{"id":"faction-1","name":"","color":"#b75d45"}],
  "places":{"place-0":{"name":"","rank":"","groupId":"","polityId":""}},
  "placeStates":{"place-0":{"status":"active","note":""}},
  "groups":[{"id":"region-1","name":"","rank":"","parentId":"","polityId":"","memberIds":["place-0"]}],
  "extraPlaces":[{"id":"new-city-1","lon":109,"lat":30,"type":"s"}],
  "territories":[{"id":"area-1","name":"","polityId":"faction-1","groupId":"region-1","rank":"","status":"controlled","polygon":[[108,30],[111,30],[111,34],[108,34]]}],
  "routes":[{"id":"road-1","name":"","fromId":"place-0","toId":"new-city-1","kind":"road","status":"open"}],
  "geography":{"kind":"builtin","landPolygons":[]}
}
```
- `territories[].status`: `controlled` / `contested` / `neutral`。
- `placeStates[].status`: `active` / `planned` / `ruined` / `abandoned` / `hidden`。
- `geography.kind='custom'` 时必须传入 `landPolygons`: `[[[lon,lat],...],...]`。
- `routes` 当前绘制为两点间的示意虚线，不支持真正的路网寻路。
- `revision` 由游戏存档维护，不是世界内的回合编号。必须严格递增来防止乱序和重放。

## JS 接口
```js
window.ChronicleMap.getSchema();
window.ChronicleMap.getCapabilities();
window.ChronicleMap.applyScenario(fullV2Object); // 新存档或全量数据恢复
window.ChronicleMap.exportScenario(); // 当前场景快照（只包含地图表示数据）
window.ChronicleMap.listPlaces();
window.ChronicleMap.listTerritories();
window.ChronicleMap.listRoutes();
window.ChronicleMap.patchScenario({ places:{'place-0':{name:'示例'}} }); // 手动演示修改
window.ChronicleMap.applyWorldDelta({
  sceneId:'your-save-id',
  revision:42,
  changes:{
    territories:[/* 全部疆域列表（原子替换） */],
    placeStates:{'place-0':{status:'ruined'}}
  }
});
window.addEventListener('chronicle-map:place-select', e=>console.log(e.detail));
window.addEventListener('chronicle-map:territory-select', e=>console.log(e.detail));
window.addEventListener('chronicle-map:world-updated', e=>console.log(e.detail));
```
`patchScenario()` 仅供开发/编辑，**没有** revision 检查；正式游戏更新请统一使用 `applyWorldDelta()`。其中 `territories`/`routes`/`polities`/`groups` 是完整数组替换，`places`/`placeStates` 支持按 ID 局部合并。

## Android 原生调用（概念示例）
需要把 JSON 序列化成**安全的 JavaScript 字符串字面量**，再解析。切勿直接把用户名称拼进 JS 语句。
```kotlin
// 仅在受信 appassets 页面已经加载之后执行
val json = mapSnapshotJsonFromRoom  // 已通过 schema 校验的完整场景 JSON
val js = "window.ChronicleMap.applyScenario(JSON.parse(${org.json.JSONObject.quote(json)}));"
webView.evaluateJavascript(js, null)
```
原生向页面发送增量时也同理。JS→Android 的地点事件推荐使用限制 origin 的 WebMessageListener 或安全的消息端口。不要在第三方网页上注入高权限桥接对象。

## 设计边界
- 本地图不计算国力、官员廉洁、财政、兵员、距离时间或巡游后果。战斗、城市兴废、疆域增减要由 GameCore 结算后刷新地图。
- 原始底图手绘、精度有限。历史城镇坐标、县级关系均为参考点，不代表已经考证的法定县界。演示 JSON 中的疆域是虚构的。
- 自定义地理包受到阿尔伯斯局部投影限制：示例支持经度 55～155、纬度 -4～65。不能表示任何经纬度上的完整全球。
- 目前多边形不支持洞、多部分跨海飞地、拓扑共享边、海岸线自动裁边及地貌高程导入；这些应作为独立后续迭代，不要宣称已完成。
- 浏览器自动化覆盖 390×844、320×720、844×390 与手工圈地用例。**未完成指定 vivo Android 真机验收**。
- 浏览器 `localStorage` 不应当作正式游戏持久化存档；原生集成必须使用 Room/SQLite 并支持备份/导出。
