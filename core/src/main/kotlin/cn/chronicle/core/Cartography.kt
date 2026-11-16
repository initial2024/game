package cn.chronicle.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.math.abs

const val MAP_SCHEMA="chronicle-map-scenario/v2"
@Serializable data class MapMeta(val title:String="",val era:String="",val seal:String="")
@Serializable data class MapPolity(val id:String,val name:String="",val color:String="#997a59")
@Serializable data class MapPlace(val name:String="",val rank:String="",val description:String="",val groupId:String="",val polityId:String="")
@Serializable data class MapPlaceState(val status:String="active",val note:String="")
@Serializable data class MapPoint(val id:String,val lon:Double,val lat:Double,val type:String="s")
@Serializable data class MapGroup(val id:String,val name:String="",val rank:String="",val parentId:String="",val polityId:String="",val memberIds:List<String> = emptyList())
@Serializable data class MapTerritory(val id:String,val name:String="",val rank:String="",val polityId:String="",val groupId:String="",val status:String="controlled",val polygon:List<List<Double>>,val description:String="")
@Serializable data class MapRoute(val id:String,val name:String="",val fromId:String,val toId:String,val kind:String="road",val status:String="open")
@Serializable data class MapGeography(val kind:String="builtin",val landPolygons:List<List<List<Double>>> = emptyList())
@Serializable data class MapScenario(val schema:String=MAP_SCHEMA,val id:String="draft",val revision:Long=0,val meta:MapMeta=MapMeta(),val polities:List<MapPolity> = emptyList(),val places:Map<String,MapPlace> = emptyMap(),val placeStates:Map<String,MapPlaceState> = emptyMap(),val groups:List<MapGroup> = emptyList(),val extraPlaces:List<MapPoint> = emptyList(),val territories:List<MapTerritory> = emptyList(),val routes:List<MapRoute> = emptyList(),val geography:MapGeography=MapGeography())

object MapValidation {
 private val idPattern=Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,84}")
 fun id(s:String)=idPattern.matches(s) && s !in setOf("constructor","prototype","__proto__")
 private fun text(s:String,max:Int) {require(s.length<=max && s.none {it<' ' && it !in "\n\r\t"})}
 fun parse(text:String):MapScenario {
  require(text.toByteArray().size<=1024*1024){"地图文件超过1MiB"}
  val raw=SaveJson.parseToJsonElement(text).jsonObject
  val version=raw["schema"]?.jsonPrimitive?.content
  require(version==MAP_SCHEMA || version=="chronicle-map-scenario/v1")
  val upgraded=JsonObject(raw+("schema" to JsonPrimitive(MAP_SCHEMA)))
  return SaveJson.decodeFromJsonElement<MapScenario>(upgraded).also(::verify)
 }
 fun verify(s:MapScenario) {
  require(s.schema==MAP_SCHEMA && id(s.id) && s.id.length<=65 && s.revision in 0..9007199254740991L)
  text(s.meta.title,40);text(s.meta.era,65);text(s.meta.seal,5)
  require(s.polities.size<=40 && s.groups.size<=200 && s.extraPlaces.size<=200 && s.places.size<=1500 && s.placeStates.size<=1700 && s.territories.size<=80 && s.routes.size<=250)
  fun ids(values:List<String>,max:Int=72) {require(values.all {id(it)&&it.length<=max} && values.distinct().size==values.size)}
  ids(s.polities.map {it.id});ids(s.groups.map {it.id});ids(s.extraPlaces.map {it.id},85);require(s.extraPlaces.none {it.id in builtinMapPlaceIds});ids(s.territories.map {it.id});ids(s.routes.map {it.id})
  fun place(id:String)=s.extraPlaces.any {it.id==id} || id in builtinMapPlaceIds
  fun polity(id:String)=id.isEmpty()||s.polities.any {it.id==id}
  fun group(id:String)=id.isEmpty()||s.groups.any {it.id==id}
  s.polities.forEach {text(it.name,50);require(Regex("#[0-9A-Fa-f]{6}").matches(it.color))}
  s.extraPlaces.forEach {require(it.lon.isFinite()&&it.lat.isFinite()&&it.lon in 55.0..155.0&&it.lat in -4.0..65.0&&it.type in listOf("d","c","s","f","h","x","o","p"))}
  s.places.forEach {(id,p)->require(id(id)&&place(id)&&polity(p.polityId)&&group(p.groupId));text(p.name,60);text(p.rank,28);text(p.description,500)}
  s.placeStates.forEach {(id,p)->require(place(id)&&p.status in listOf("active","planned","ruined","abandoned","hidden"));text(p.note,300)}
  s.groups.forEach {g->require(group(g.parentId)&&polity(g.polityId)&&g.memberIds.size<=800&&g.memberIds.distinct().size==g.memberIds.size&&g.memberIds.all(::place));text(g.name,70);text(g.rank,28)
   var parent=g.parentId;val seen=mutableSetOf(g.id)
   while(parent.isNotEmpty()){require(seen.add(parent)){"行政层级循环"};parent=s.groups.first {it.id==parent}.parentId}
  }
  require(s.territories.sumOf {it.polygon.size}<=2500)
  s.territories.forEach {require(polity(it.polityId)&&group(it.groupId)&&it.status in listOf("controlled","contested","neutral"));text(it.name,65);text(it.rank,35);text(it.description,350);ring(it.polygon,60)}
  s.routes.forEach {require(place(it.fromId)&&place(it.toId)&&it.fromId!=it.toId&&it.kind in listOf("road","trail","sea")&&it.status in listOf("open","closed"));text(it.name,60)}
  require(s.geography.kind in listOf("builtin","custom")&&s.geography.landPolygons.size<=12)
  if(s.geography.kind=="custom")require(s.geography.landPolygons.isNotEmpty())
  s.geography.landPolygons.forEach {ring(it,150)}
 }
 fun ring(p:List<List<Double>>,max:Int) {
  require(p.size in 3..max && p.all {it.size==2&&it.all(Double::isFinite)&&it[0] in 55.0..155.0&&it[1] in -4.0..65.0} && p.distinct().size==p.size){"多边形坐标或顶点不合法"}
  require(abs(p.indices.sumOf {i->p[i][0]*p[(i+1)%p.size][1]-p[(i+1)%p.size][0]*p[i][1]})>=.0005)
  fun cross(a:List<Double>,b:List<Double>,c:List<Double>)=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
  fun on(a:List<Double>,b:List<Double>,c:List<Double>)=abs(cross(a,b,c))<1e-9&&c[0] in minOf(a[0],b[0])..maxOf(a[0],b[0])&&c[1] in minOf(a[1],b[1])..maxOf(a[1],b[1])
  for(i in p.indices)for(j in i+1 until p.size){if(j==i+1||i==0&&j==p.lastIndex)continue
   val a=p[i];val b=p[(i+1)%p.size];val c=p[j];val d=p[(j+1)%p.size]
   require(!(cross(a,b,c)*cross(a,b,d)<0&&cross(c,d,a)*cross(c,d,b)<0||on(a,b,c)||on(a,b,d)||on(c,d,a)||on(c,d,b))){"多边形自相交或重叠"}
  }
 }
}

object MapSnapshotMapper {
 // Hand-authored fictional region contours. Shared edges use the same vertices in reverse order.
 private val contours=listOf(
  listOf(105 to 27,111 to 26,116 to 29,116 to 33,112 to 34,109 to 34,106 to 33,104 to 31),
  listOf(96 to 32,100 to 30,104 to 31,106 to 33,109 to 34,107 to 36,108 to 39,103 to 40,99 to 38,96 to 35),
  listOf(105 to 27,111 to 26,116 to 29,119 to 28,121 to 25,118 to 22,112 to 20,107 to 22),
  listOf(109 to 34,112 to 34,116 to 33,118 to 36,117 to 39,115 to 42,111 to 44,107 to 43,103 to 40,108 to 39,107 to 36),
  listOf(116 to 33,116 to 29,119 to 28,122 to 30,125 to 32,124 to 35,121 to 36,118 to 36),
  listOf(103 to 40,107 to 43,111 to 44,111 to 48,107 to 51,101 to 50,97 to 46,99 to 42),
  listOf(118 to 36,121 to 36,124 to 39,124 to 43,120 to 46,115 to 45,111 to 44,115 to 42,117 to 39)
 ).map {ring->ring.map {listOf(it.first.toDouble(),it.second.toDouble())}}
 private val oldCenters=listOf(109.0 to 30.0,102.0 to 35.0,114.0 to 25.0,110.0 to 41.0,123.0 to 33.0,104.0 to 46.0,119.0 to 40.0)
 private fun oldRect(index:Int)=oldCenters[index].let {(x,y)->listOf(listOf(x-2,y-2),listOf(x+2,y-2),listOf(x+2,y+2),listOf(x-2,y+2))}
 // Display-only migration: never overwrite imported or hand-authored boundaries, ownership, or history.
 private fun detailed(s:MapScenario)=s.copy(territories=s.territories.map {t->
  val index=when {t.id.startsWith("territory-r")->t.id.removePrefix("territory-r").toIntOrNull()?.takeIf {it in 0..3};t.id.startsWith("frontier-")->t.id.removePrefix("frontier-").toIntOrNull()?.takeIf {it in 0..2}?.plus(4);else->null}
  if(index!=null&&t.polygon==oldRect(index))t.copy(polygon=contours[index])else t
 })
 fun scene(w:World,slot:Int):MapScenario {
  require(slot in 1..8)
  val s=detailed(w.cartography?:base(w))
  return s.copy(id="game-slot-$slot",revision=w.revision,meta=MapMeta(w.name,w.date,if(w.setting=="古代王朝")"古" else "纪"),polities=s.polities.map {if(it.id=="nation")it.copy(name=w.name)else it},groups=s.groups.map {g->w.regions.find {"region-${it.id}"==g.id}?.let {g.copy(name=it.name)}?:g}).also(MapValidation::verify)
 }
 fun regionId(w:World,placeId:String):Int? = (w.cartography?:base(w)).places[placeId]?.let {regionForGroup(w,it.groupId)}
 fun regionForGroup(w:World,groupId:String):Int? {
  val s=w.cartography?:base(w);var id=groupId;val seen=mutableSetOf<String>()
  while(id.isNotEmpty()&&seen.add(id)){
   id.takeIf {it.startsWith("region-")}?.removePrefix("region-")?.toIntOrNull()?.takeIf {n->w.regions.any {it.id==n}}?.let {return it}
   id=s.groups.find {it.id==id}?.parentId?:return null
  }
  return null
 }
 fun base(w:World):MapScenario {
  val positions=listOf(109.0 to 30.0,102.0 to 35.0,114.0 to 25.0,110.0 to 41.0)
  val points=w.regions.map {r->MapPoint("city-r${r.id}",positions[r.id].first,positions[r.id].second,if(r.id==0)"d" else "c")}+listOf(MapPoint("ruin-r0",111.0,31.0,"x"),MapPoint("neighbor-city-0",123.0,33.0,"h"),MapPoint("neighbor-city-1",104.0,46.0,"f"),MapPoint("neighbor-city-2",119.0,40.0,"c"))
  val polities=listOf(MapPolity("nation",w.name,"#b75d45"))+w.neighbors.mapIndexed {i,n->MapPolity("neighbor-$i",n.name,listOf("#5a90a0","#9b8657","#748457")[i])}
  val places=w.regions.associate {r->"city-r${r.id}" to MapPlace(r.name+"城",if(w.setting=="古代王朝")"府城" else "地区中心","地区治理、官员档案与巡游入口。","region-${r.id}","nation")}+mapOf("ruin-r0" to MapPlace("旧津", "旧城","往年水患留下的废城，可以依法拨款恢复。","region-0","nation"))+w.neighbors.mapIndexed {i,n->"neighbor-city-$i" to MapPlace(n.name+"都","邻国都城",polityId="neighbor-$i")}
  val territories=w.regions.map {r->MapTerritory("territory-r${r.id}",r.name,if(w.setting==EraRules.ANCIENT)"府"else"地区","nation","region-${r.id}",polygon=contours[r.id])}+w.neighbors.mapIndexed {i,n->MapTerritory("frontier-$i",n.name+"边地",polityId="neighbor-$i",polygon=contours[i+4])}
  return MapScenario(polities=polities,places=places,extraPlaces=points,placeStates=points.associate {it.id to MapPlaceState(if(it.id=="ruin-r0")"ruined" else "active")},groups=w.regions.map {r->MapGroup("region-${r.id}",r.name,if(w.setting=="古代王朝")"府" else "地区",polityId="nation",memberIds=places.filterValues {it.groupId=="region-${r.id}"}.keys.toList())},territories=territories,routes=(1..3).map {MapRoute("route-$it","驿道","city-r0","city-r$it")})
 }
}

// Only call after the SQLite commit has completed. State is per live WebView, never global.
data class MapDispatch(val method:String,val json:String)
class MapDeltaDispatcher {
 private var last:MapScenario?=null
 fun reset(){last=null}
 fun next(s:MapScenario):MapDispatch? {
  MapValidation.verify(s);val old=last
  if(old!=null && old.id==s.id && s.revision<=old.revision)return null
  val full=old==null||old.id!=s.id||old.geography!=s.geography
  val data=SaveJson.encodeToJsonElement(s).jsonObject
  val result=if(full)MapDispatch("applyScenario",SaveJson.encodeToString(s)) else {
   val oldData=SaveJson.encodeToJsonElement(old!!).jsonObject
   val changes=JsonObject(data.filter {(key,v)->key !in listOf("id","schema","revision") && v!=oldData[key]})
   MapDispatch("applyWorldDelta",buildJsonObject {put("sceneId",s.id);put("revision",s.revision);put("changes",changes)}.toString())
  }
  last=s;return result
 }
}

object MapActions {
 val types=setOf("CITY_FOUND","CITY_RESTORE","CITY_RENAME","BORDER_TREATY")
 fun validate(w:World,c:Command):String? {
  val s=w.cartography?:MapSnapshotMapper.base(w)
  if(w.pending.any {it.command.type==c.type && it.command.region==c.region} || w.projects.any {it.kind==c.type&&it.region==c.region})return "同一地区已有这项事务。"
  return when(c.type){
   "CITY_FOUND" -> if(s.places.containsKey("settlement-r${c.region}"))"此地新城已登记。" else if(s.extraPlaces.none {it.id=="city-r${c.region}"&&it.lon<=154&&it.lat>=-3.3})"当前剧情没有可用的地区营建点。" else null
   "CITY_RESTORE" -> if(c.target!="ruin-r0"||c.region!=0||s.placeStates[c.target]?.status !in listOf("ruined","abandoned"))"目标不是可恢复的已登记旧城。" else null
   "CITY_RENAME" -> if(MapSnapshotMapper.regionId(w,c.target)!=c.region || !FamilyEngine.validName(c.objective))"地点或新名称无效。" else null
   "BORDER_TREATY" -> if(c.target!="frontier-0"||w.neighbors[0].relation<80||s.territories.none {it.id==c.target&&it.polityId=="neighbor-0"})"须与东部邻国关系达到80，且边地尚未移交。" else null
   else -> "未知地图行动"
  }
 }
 fun execute(w:World,c:Command,events:MutableList<String>):World {
  var s=w.cartography?:MapSnapshotMapper.base(w)
  when(c.type){
   "CITY_FOUND" -> {val id="settlement-r${c.region}";val main=s.extraPlaces.first {it.id=="city-r${c.region}"}
    s=s.copy(extraPlaces=s.extraPlaces+MapPoint(id,main.lon+1.0,main.lat-.7,"s"),places=s.places+(id to MapPlace(w.regions[c.region].name+"新邑","新邑",groupId="region-${c.region}",polityId="nation")),placeStates=s.placeStates+(id to MapPlaceState("planned")),groups=s.groups.map {if(it.id=="region-${c.region}")it.copy(memberIds=it.memberIds+id)else it},routes=s.routes+MapRoute("new-route-${c.region}","新邑道路","city-r${c.region}",id))
    events+="新邑已登记为规划中；120日工期完成后方可居住。"
   }
   "CITY_RESTORE" -> {s=s.copy(placeStates=s.placeStates+(c.target to MapPlaceState("planned","修复中")));events+="旧津开始修复，90日后验收，不会即刻恢复。"}
   "CITY_RENAME" -> {s=s.copy(places=s.places+(c.target to s.places.getValue(c.target).copy(name=c.objective)));events+="城邑名称改为${c.objective}，稳定地点ID和过去历史保留。"}
   "BORDER_TREATY" -> {s=s.copy(territories=s.territories.map {if(it.id==c.target)it.copy(polityId="nation",description="两国议定移交，仍保留原有地理位置。")else it},places=s.places+("neighbor-city-0" to s.places.getValue("neighbor-city-0").copy(polityId="nation")))
    events+="两国按约完成东部边地移交；交割成本已付，今后每季承担5万两驻防支出。"
   }
  }
  return w.copy(cartography=s,projects=if(c.type in listOf("CITY_FOUND","CITY_RESTORE")) w.projects+Project(c.type,c.region,2,if(c.type=="CITY_FOUND")120 else 90) else w.projects,neighbors=if(c.type=="BORDER_TREATY")w.neighbors.mapIndexed {i,n->if(i==0)n.copy(relation=n.relation-15)else n}else w.neighbors)
 }
 fun complete(w:World,p:Project,events:MutableList<String>):World {
  val s=w.cartography?:MapSnapshotMapper.base(w);val id=if(p.kind=="CITY_FOUND")"settlement-r${p.region}" else "ruin-r0"
  events+="${s.places[id]?.name}验收完成，由规划中变为现存，城邑记录已保存。"
  return w.copy(cartography=s.copy(placeStates=s.placeStates+(id to MapPlaceState("active","完成验收"))))
 }
}
