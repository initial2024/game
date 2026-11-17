package cn.chronicle.core

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

@Serializable data class TerrainProfile(val kind:String,val height:Int,val slope:Int,val water:Int,val fertility:Int,val timber:Int,val iron:Int,val coal:Int,val stone:Int,val coast:Boolean=false,val land:Boolean=true)
@Serializable data class SiteResources(val terrain:TerrainProfile,val iron:Long,val coal:Long,val stone:Long,val forest:Long,val forestCapacity:Long)
data class GeoReference(val lon:Double,val lat:Double,val indices:List<Int>)
object TerrainEngine {
 private val sites by lazy {AtlasTerrainData.siteRows.lines().associate {line->val p=line.split(',');p[0] to GeoReference(p[1].toDouble(),p[2].toDouble(),p.drop(3).map(String::toInt))}}
 private val grid by lazy {AtlasTerrainData.gridRows.lines().map {it.split(',').map(String::toInt)}}
 fun reference(id:String)=sites[id]
 fun sample(lon:Double,lat:Double):List<Int> {val x=((lon-96)*2).roundToInt().coerceIn(0,64);val y=((lat-18)*2).roundToInt().coerceIn(0,66);return grid[y*65+x]}
 fun profile(w:World,id:String):TerrainProfile {
  w.resources.sites[id]?.let {return it.terrain}
  val geo=sites[id]?:run {val p=w.cartography?.extraPlaces?.find {it.id==id};val seed=AdministrativeEngine.seeds.find {it.id==id};GeoReference(p?.lon?:seed?.lon?:109.0,p?.lat?:seed?.lat?:30.0,sample(p?.lon?:seed?.lon?:109.0,p?.lat?:seed?.lat?:30.0))}
  val v=geo.indices;val kind=when {v[6]==0->"海域驻点";v[5]==1->"荒漠";v[0]>=45->"高原";v[3]>=65||v[1]>=65->"山地";v[4]==1->"滨海";v[2]>=65->"河谷";v[3]>=20->"丘陵";else->"平原"}
  val water=if(kind=="荒漠")v[2].coerceAtLeast(5)else v[2].coerceAtLeast(15)
  val fertility=(when(kind){"荒漠"->15;"高原"->30;"山地"->35;"丘陵"->60;"河谷"->85;else->72}+water/8-v[1]/4).coerceIn(5,100)
  val roll=Math.floorMod(w.seed+id.hashCode()*31L,31L).toInt();val rocky=kind in listOf("高原","山地","丘陵")
  return TerrainProfile(kind,v[0].coerceIn(0,100),v[1],water,fertility,(if(rocky)65 else if(kind=="荒漠")10 else 35)+roll/3,if(rocky)25+roll*2 else if(roll%5==0)30 else 0,if(geo.lat>=32&&rocky)30+roll*2 else if(roll%7==0)25 else 0,if(rocky)75 else 30,v[4]==1,v[6]==1)
 }
 fun initialize(w:World):World {
  if(w.towns.isEmpty())return w
  val initial=!w.resources.initialized
  val missing=w.towns.filter {it.id !in w.resources.sites};if(!initial&&missing.isEmpty())return w
  val added=missing.associate {t->val p=profile(w,t.id);val salt=Math.floorMod(w.seed+t.id.hashCode(),5000L);t.id to SiteResources(p,if(initial&&p.iron>0)p.iron*250L+salt else 0,if(initial&&p.coal>0)p.coal*400L+salt else 0,if(initial)p.stone*1200L else 0,if(initial)p.timber*150L else 0,p.timber*180L)}
  val stock=if(initial)mapOf("GRAIN" to minOf(w.nation.population/2,w.towns.sumOf {(it.buildings["GRANARY"]?:0)*20000L}),"TIMBER" to (500L+w.nation.population/10000),"STONE" to (800L+w.nation.population/5000),"IRON" to (200L+w.nation.population/50000),"COAL" to (250L+w.nation.population/40000))else w.resources.stocks
  val pools=if(initial)w.regions.map {it.population*3/2}else w.resources.landPools
  val ports=setOf("place-5","place-14","place-16")
  val towns=if(initial)w.towns.map {t->if(t.id in ports)t.copy(buildings=t.buildings+("PORT" to maxOf(1,t.buildings["PORT"]?:0)))else t}else w.towns
  return w.copy(towns=towns,resources=w.resources.copy(initialized=true,sites=w.resources.sites+added,stocks=stock,landPools=pools))
 }
 fun constructionFactor(w:World,c:Command):Int {
  val t=when(c.type){"URBAN_BUILD"->w.towns.find {it.id==c.target};"FACTORY","WORKSHOP","FARM","ROAD"->w.towns.find {it.id=="city-r${c.region}"};else->null}?:return 100
  val p=profile(w,t.id);return (100+when(p.kind){"高原"->35;"山地"->25;"荒漠"->15;else->0}+(p.slope-40).coerceAtLeast(0)/2+if(c.objective in listOf("ROAD","PORT")&&p.kind=="丘陵")10 else 0).coerceAtMost(200)
 }
 fun position(w:World,id:String)=sites[id]?.let {it.lon to it.lat}?:w.cartography?.extraPlaces?.find {it.id==id}?.let {it.lon to it.lat}?:AdministrativeEngine.seeds.find {it.id==id}?.let {it.lon to it.lat}
 fun route(w:World,from:String,to:String):List<Int> {val a=position(w,from)?:return listOf(0,0,0);val b=position(w,to)?:return listOf(0,0,0);val samples=(0..8).map {i->sample(a.first+(b.first-a.first)*i/8,a.second+(b.second-a.second)*i/8)};return listOf(samples.maxOf {it[1]},samples.count {it[2]>=65},samples.count {it[6]==0})}
 fun marchFactor(w:World,from:String,to:String):Int {val r=route(w,from,to);return 100+r[0]/2+r[1]*2+r[2]*4}
 fun defense(w:World,neighbor:Int):Int {val p=profile(w,"neighbor-city-$neighbor");return (when(p.kind){"山地"->25;"高原"->20;"丘陵"->12;"海域驻点"->15;else->0}+if(p.water>=65)10 else 0).coerceAtMost(40)}
}
