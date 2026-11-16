package cn.chronicle.core

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable data class Canal(val id:String,val from:String,val to:String,val completedDay:Int)
object CanalEngine {
 fun position(w:World,id:String):Pair<Double,Double>?=AdministrativeEngine.seeds.find {it.id==id}?.let {it.lon to it.lat}?:MapSnapshotMapper.state(w).extraPlaces.find {it.id==id}?.let {it.lon to it.lat}
 fun distance(w:World,from:String,to:String):Double? {val a=position(w,from)?:return null;val b=position(w,to)?:return null;return abs(a.first-b.first)+abs(a.second-b.second)}
 fun plan(w:World,from:String,to:String):Command {val t=w.towns.first {it.id==from};val days=120+((distance(w,from,to)?:0.0)*12).toInt();return Command("CANAL",region=t.region,target=from,objective=to,days=days)}
 fun cost(c:Command)=180L+c.days/2
 fun validate(w:World,c:Command):String? {
  val a=w.towns.find {it.id==c.target};val b=w.towns.find {it.id==c.objective};val d=distance(w,c.target,c.objective)
  return when {a==null||b==null||a.id==b.id||a.region!=c.region->"请选择两个不同的已登记城邑。";d==null||d>8.0->"本期支持投影经纬跨度合计8度以内的示意水运工程，请分段修筑。";c.days!=120+(d*12).toInt()->"工程测算已变化，请重新点选预览。";w.canals.size+w.projects.count {it.kind=="CANAL"}>=12->"最多支持12条运河工程。";w.canals.any {setOf(it.from,it.to)==setOf(a.id,b.id)}||w.projects.any {it.kind=="CANAL"&&setOf(it.target,it.objective)==setOf(a.id,b.id)}->"此段运河已建或在建。";listOf(a.id,b.id).any {w.cartography?.placeStates?.get(it)?.status in listOf("ruined","planned","hidden","abandoned")}->"运河端点须为正常使用的城邑。";else->null}
 }
 fun execute(w:World,c:Command,events:MutableList<String>):World {events+="${w.towns.first {it.id==c.target}.name}至${w.towns.first {it.id==c.objective}.name}运河立项，${c.days}日，支出${cost(c)}万两；竣工前不增加收益，每条维护3万两/季。地图为水运连接示意，不是水文勘测。";return w.copy(projects=w.projects+Project("CANAL",c.region,(c.days+89)/90,c.days,c.target,c.objective))}
 fun complete(w:World,p:Project,events:MutableList<String>):World {val canal=Canal("canal-${p.target}-${p.objective}",p.target,p.objective,w.day);events+="运河竣工：${w.towns.first {it.id==p.target}.name}—${w.towns.first {it.id==p.objective}.name}。基础设施+3、商业+2、农业+2；沿线城邑农业与商业各+3，地图登记水运路线。";return w.copy(canals=w.canals+canal,nation=w.nation.copy(infrastructure=(w.nation.infrastructure+3).coerceAtMost(100),commerce=(w.nation.commerce+2).coerceAtMost(100),agriculture=(w.nation.agriculture+2).coerceAtMost(100)),towns=w.towns.map {if(it.id in listOf(p.target,p.objective))it.copy(agriculture=(it.agriculture+3).coerceAtMost(100),commerce=(it.commerce+3).coerceAtMost(100))else it})}
 fun verify(w:World){require(w.canals.size<=12&&w.canals.map {setOf(it.from,it.to)}.distinct().size==w.canals.size);w.canals.forEach {require(it.from!=it.to&&it.completedDay in 0..w.day&&w.towns.any {t->t.id==it.from}&&w.towns.any {t->t.id==it.to})}}
}
