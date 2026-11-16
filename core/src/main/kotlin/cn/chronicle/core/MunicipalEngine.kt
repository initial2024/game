package cn.chronicle.core

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable data class UrbanWork(val kind:String,val level:Int,val remainingDays:Int,val paid:Long,val factor:Int=100)
@Serializable data class Town(val id:String,val name:String,val prefecture:String,val region:Int,val rank:String,val official:Int,
 val security:Int,val corruption:Int,val morale:Int,val agriculture:Int,val commerce:Int,val industry:Int,
 val buildings:Map<String,Int>,val work:UrbanWork?=null,val deputy:Int=-1,val clerk:Int=-1,val staffed:Boolean=false)
data class BuildingSpec(val id:String,val name:String,val category:String,val cost:Long,val days:Int,val effect:String)
object MunicipalEngine {
 val types=setOf("URBAN_BUILD","URBAN_AUDIT","URBAN_RELIEF")
 val buildings=listOf(
  BuildingSpec("YAMEN","官署","治理",35,45,"每级改善治安与行政条件"),
  BuildingSpec("MARKET","集市","经济",50,60,"每个新增等级提高商业，并增加季度税源5万两、维护1万两"),
  BuildingSpec("WORKSHOP","作坊","经济",65,75,"每个新增等级提高手工业，增加季度税源4万两、维护2万两"),
  BuildingSpec("ROAD","驿路","交通",45,60,"提高交通与商业，改善本城交通连接"),
  BuildingSpec("GRANARY","常平仓","民生",40,60,"增加本城储粮容量并改善农业"),
  BuildingSpec("CLINIC","医馆","民生",45,60,"改善本城健康与民心"),
  BuildingSpec("SCHOOL","学堂","民生",55,75,"提高本城教育；国家学院仍按自身等级授课"),
  BuildingSpec("MINE","矿场","资源",75,90,"按本地矿藏、等级开采铁、煤及石料，矿藏耗尽后不再产出"),
  BuildingSpec("SAWMILL","林场","资源",55,75,"按森林、林场等级采木，受林木储量与再生限制"),
  BuildingSpec("PORT","码头","交通",80,90,"须临河或近海，增加水运与海运承载能力"),
  BuildingSpec("IRRIGATION","灌溉渠","农水",60,90,"须有水源，改善当地农业产量"),
  BuildingSpec("BARRACKS","兵营","治理",60,75,"提高本城治安与驻防能力")
 )
 fun baseline(kind:String,rank:String)=if(kind in listOf("MINE","SAWMILL","PORT","IRRIGATION"))0 else if(kind=="BARRACKS"&&rank=="县城")0 else 1
 internal fun template(id:String,name:String,pref:String,region:Int,rank:String,official:Int,seed:Long,r:Region):Town {
  val variation=Math.floorMod(seed+id.hashCode(),13L).toInt()-6
  return Town(id,name,pref,region,rank,official,(r.security+variation).coerceIn(0,100),(r.corruption-variation).coerceIn(0,100),(r.morale+variation).coerceIn(0,100),r.agriculture,30+variation,r.industry,buildings.associate {it.id to baseline(it.id,rank)})
 }
 fun all(w:World):List<Town> {
  if(w.towns.isNotEmpty())return w.towns
  if(w.cartography?.let {it.geography.kind=="custom"||it.extraPlaces.none {p->p.id=="city-r0"}}==true)return emptyList()
  val next=(w.people.maxOfOrNull {it.id}?:9)+1
  val capitals=w.regions.map {r->template("city-r${r.id}",r.name+"城","",r.id,"大区治所",r.official,w.seed,r)}
  return capitals+townSeeds.mapIndexed {i,s->template(s.id,s.name,s.prefecture,s.region,s.rank,next+i,w.seed,w.regions[s.region])}
 }
 fun initialize(w:World):World {
  if(w.towns.isNotEmpty()){val normalized=w.copy(towns=w.towns.map {t->t.copy(buildings=buildings.associate {b->b.id to (t.buildings[b.id]?:baseline(b.id,t.rank))})});return AdministrativeEngine.expand(TalentEngine.staff(normalized))}
  if(w.cartography?.let {it.geography.kind=="custom"||it.extraPlaces.none {p->p.id=="city-r0"}}==true)return w
  val towns=all(w)
  val surnames=listOf("陈","谢","江","林","程","苏","杜","叶","宋","黎","温","方","季")
  val given=listOf("明远","知行","清岳","文昭","守正","怀川","景和","子谦","闻礼","承安","望舒","若衡","慎言")
  val people=towns.filter {t->w.people.none {it.id==t.official}}.mapIndexed {i,t->
   Person(t.official,surnames[i%surnames.size]+given[(i/surnames.size+i)%given.size],"${t.name}${if(t.rank=="县城")"县令"else"知府"}",i%3,112+i%96,45+i%39,50+i%31,"依法施政","治理${t.name}")
  }
  return AdministrativeEngine.expand(TalentEngine.staff(w.copy(towns=towns,people=w.people+people)))
 }
 fun register(w:World,id:String,region:Int,name:String):World {
  val x=initialize(w);if(x.towns.any {it.id==id})return x
  val pref=townSeeds.firstOrNull {it.region==region&&it.rank=="府城"}?.prefecture?:""
  return WorldSetup.initialize(TalentEngine.staff(x.copy(towns=x.towns+template(id,name,pref,region,"县城",-1,x.seed,x.regions[region]))))
 }
 fun prefectureName(t:Town)=townSeeds.firstOrNull {it.prefecture==t.prefecture}?.administration?:""
 fun name(w:World,t:Town)=w.cartography?.places?.get(t.id)?.name?.takeIf {it.isNotBlank()}?:t.name
 fun officer(w:World,t:Town)=w.people.find {it.id==(if(t.id.startsWith("city-r"))w.regions[t.region].official else t.official)&&it.alive}
 fun extra(t:Town,kind:String)=(t.buildings.getValue(kind)-(if(kind=="PORT"&&t.id in setOf("place-5","place-14","place-16"))1 else baseline(kind,t.rank))).coerceAtLeast(0)
 private fun weight(t:Town)=(if(t.rank=="县城")1 else if(t.rank=="府城")3 else 4)+extra(t,"CLINIC")+extra(t,"GRANARY")
 fun share(w:World,t:Town,total:Long):Long {
  val group=all(w).filter {it.region==t.region};val sum=group.sumOf(::weight).coerceAtLeast(1)
  val basic=total*weight(t)/sum
  return basic+if(t.id==group.lastOrNull()?.id)total-group.sumOf {total*weight(it)/sum} else 0L
 }
 fun population(w:World,t:Town)=share(w,t,w.regions[t.region].population)
 fun stats(t:Town):Map<String,Int> =linkedMapOf(
  "治安" to (t.security+extra(t,"YAMEN")*3+extra(t,"BARRACKS")*4).coerceIn(0,100),
  "腐败" to t.corruption,
  "民心" to (t.morale+extra(t,"CLINIC")*3).coerceIn(0,100),
  "农业" to (t.agriculture+extra(t,"GRANARY")*4).coerceIn(0,100),
  "商业" to (t.commerce+extra(t,"MARKET")*5+extra(t,"ROAD")*3).coerceIn(0,100),
  "手工业" to (t.industry+extra(t,"WORKSHOP")*5).coerceIn(0,100),
  "健康" to (45+t.buildings.getValue("CLINIC")*7-t.corruption/10).coerceIn(0,100),
  "教育" to (t.buildings.getValue("SCHOOL")*18).coerceAtMost(100)
 )
 fun cost(c:Command)=when(c.type){"URBAN_BUILD"->(buildings.find {it.id==c.objective}?.cost?:0L)*c.level;"URBAN_AUDIT"->15L;"URBAN_RELIEF"->20L;else->0L}
 fun duration(c:Command)=(buildings.find {it.id==c.objective}?.days?:0)+c.level*10
 fun duration(w:World,c:Command)=duration(c)*TerrainEngine.constructionFactor(w,c)/100
 fun validate(w:World,c:Command):String? {
  val t=all(w).find {it.id==c.target}?:return "此城邑尚未登记。"
  if(t.region!=c.region)return "城邑所属大区不匹配。"
  val s=w.cartography
  if(s!=null&&s.geography.kind=="custom"&&!s.places.containsKey(c.target))return "当前剧情没有这个城邑。"
  if(s?.placeStates?.get(c.target)?.status in listOf("planned","ruined","abandoned","hidden"))return "此城邑尚未恢复正常使用。"
  return when(c.type){
   "URBAN_BUILD"->when {TerrainEngine.profile(w,t.id).let {!it.land&&!it.coast}&&c.objective!="PORT"->"此地点为水域参考点，当前不支持该陆上建筑。";c.objective=="PORT"&&TerrainEngine.profile(w,t.id).let {!it.coast&&it.water<50}->"码头须临河或近海。";c.objective=="IRRIGATION"&&TerrainEngine.profile(w,t.id).water<10->"水源不足，当前不能开灌溉渠。";c.objective=="MINE"&&TerrainEngine.profile(w,t.id).let {it.iron==0&&it.coal==0&&it.stone<40}->"本地没有已登记可采矿源。";c.objective=="SAWMILL"&&TerrainEngine.profile(w,t.id).timber<20->"当地林木条件不适合林场。";c.objective !in t.buildings->"建筑类型无效。";c.level !in 1..5||c.level!=t.buildings.getValue(c.objective)+1->"建筑等级已变化，请重新预览。";t.work!=null->"本城已有在建项目。";all(w).count {it.work!=null}>=6->"最多同时建设六项城内工程。";else->null}
   "URBAN_AUDIT"->if(t.corruption==0)"本城腐败记录已为零，不重复拨款审计。"else null
   "URBAN_RELIEF"->if(t.morale>=100)"当前民心已达上限。"else null
   else->"未知城邑行动。"
  }
 }
 fun execute(w:World,c:Command,events:MutableList<String>):World {
  val x=initialize(w);val t=x.towns.first {it.id==c.target}
  val updated=when(c.type){
   "URBAN_BUILD"->{events+="${name(x,t)}${buildings.first {it.id==c.objective}.name}升至${c.level}级已立项，${duration(w,c)}日后验收；费用已从国库支付，完工前不增加效果。";t.copy(work=UrbanWork(c.objective,c.level,duration(w,c),Rules.cost(w,c),TerrainEngine.constructionFactor(w,c)))}
   "URBAN_AUDIT"->{events+="${name(x,t)}完成财政审计，腐败记录下降3；这不构成对官员犯罪的自动认定。";t.copy(corruption=(t.corruption-3).coerceAtLeast(0))}
   else->{events+="${name(x,t)}完成地方赈济，民心提高5，已登记拨款支出。";t.copy(morale=(t.morale+5).coerceAtMost(100))}
  }
  return x.copy(towns=x.towns.map {if(it.id==t.id)updated else it})
 }
 fun advance(w:World,events:MutableList<String>):World {
  if(w.towns.none {it.work!=null})return w
  var n=w.nation
  val towns=w.towns.map {t->val work=t.work?:return@map t
   if(work.remainingDays>1)t.copy(work=work.copy(remainingDays=work.remainingDays-1))
   else {
    val building=buildings.first {it.id==work.kind}
    events+="${name(w,t)}${building.name}${work.level}级竣工，城市档案与财政效果已更新。"
    n=when(work.kind){"GRANARY"->n.copy(agriculture=n.agriculture+1);"SCHOOL"->n.copy(technology=n.technology+1);"WORKSHOP"->n.copy(industry=n.industry+1);"ROAD"->n.copy(infrastructure=n.infrastructure+1);"BARRACKS"->n.copy(military=n.military+1);else->n}
    t.copy(buildings=t.buildings+(work.kind to work.level),work=null)
   }
  }
  return w.copy(towns=towns,nation=n)
 }
 fun quarter(w:World,ledger:MutableList<String>):World {
  val income=w.towns.sumOf {extra(it,"MARKET")*5L+extra(it,"WORKSHOP")*4L*(w.resources.yields[it.id]?.operation?:100)/100}
  val expense=w.towns.sumOf {t->buildings.sumOf {extra(t,it.id)*(if(it.id=="WORKSHOP"||it.id=="BARRACKS")2L else 1L)}}
  if(income==0L&&expense==0L)return w
  ledger+="城邑新增建筑税源 +$income 万两、维护 -$expense 万两；初始建筑已包含于原财政，不重复新增税收。"
  val cash=w.nation.treasury+income-expense
  val borrowed=if(cash<0)minOf(-cash,(2000-w.nation.debt).coerceAtLeast(0))else 0L
  val shortfall=(cash+borrowed)<0
  if(borrowed>0)ledger+="城邑维护赤字借款 $borrowed 万两，受国家信用上限约束。"
  if(shortfall)ledger+="城邑维护预算不足，扩建建筑退化一级；在建同类工程保留已支付的工期。"
  val towns=if(shortfall)w.towns.map {t->t.copy(buildings=t.buildings.mapValues {(kind,level)->if(kind!=t.work?.kind)(level-1).coerceAtLeast(baseline(kind,t.rank))else level})}else w.towns
  return w.copy(towns=towns,nation=w.nation.copy(treasury=(cash+borrowed).coerceAtLeast(0),debt=w.nation.debt+borrowed,stability=(w.nation.stability-if(shortfall)2 else 0).coerceAtLeast(0),income=w.nation.income+income*4,expense=w.nation.expense+expense*4))
 }
 fun verify(w:World){
  require(w.towns.size<=200&&w.towns.map {it.id}.distinct().size==w.towns.size)
  w.towns.forEach {t->require(t.region in 0..3&&t.id in (AdministrativeEngine.seeds.map {it.id}+w.regions.map {"city-r${it.id}"}+w.regions.map {"settlement-r${it.id}"}+"ruin-r0")&&t.name.length in 1..60&&t.prefecture.length<=30&&t.rank in listOf("县城","府城","大区治所"));require(w.people.any {it.id==t.official}||t.official==-1)
   require(listOf(t.security,t.corruption,t.morale,t.agriculture,t.commerce,t.industry).all {it in 0..100});require(t.buildings.keys==buildings.map {it.id}.toSet()&&t.buildings.values.all {it in 0..5})
   t.work?.let {work->require(work.kind in t.buildings&&work.level in 1..5&&work.level==t.buildings.getValue(work.kind)+1&&work.factor in 100..200&&work.remainingDays in 1..250&&work.paid==buildings.first {it.id==work.kind}.cost*work.level*work.factor/100)}
  }
 }
}
