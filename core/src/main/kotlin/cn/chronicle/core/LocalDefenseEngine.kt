package cn.chronicle.core

import kotlinx.serialization.Serializable

@Serializable data class Garrison(val id:String,val name:String,val kind:String,val home:String,val state:String="",val commander:Int=-1,val governor:Int=-1,val soldiers:Int,val training:Int=35,val morale:Int=60,val supply:Int=65)
@Serializable data class LocalDefense(val units:List<Garrison> = emptyList(),val initialized:Boolean=false)
object LocalDefenseEngine {
 val types=setOf("LOCAL_APPOINT","LOCAL_RELEASE","LOCAL_TRAIN","LOCAL_RECRUIT","LOCAL_MOBILIZE","LOCAL_DEMOB")
 fun initialize(w:World):World {
  if(w.towns.isEmpty())return w
  if(w.localDefense.initialized){val missing=w.towns.filter {t->w.localDefense.units.none {it.home==t.id&&it.kind!="州军"}};if(missing.isEmpty())return w;return w.copy(localDefense=w.localDefense.copy(units=w.localDefense.units+missing.map {Garrison("garrison-${it.id}",it.name+"县兵","县兵",it.id,soldiers=0)}))}
  val people=w.people.toMutableList();var next=(people.maxOfOrNull {it.id}?:-1)+1
  fun officer(name:String,role:String):Int {if(people.size>=1000)return -1;val id=next++;people+=Person(id,listOf("邢","韦","袁","廖","唐","孟","冯")[id%7]+listOf("守川","景武","怀山","正安","清平","靖远","望岳")[(id/7+id)%7],name+role,id%3,112+id%80,45+Math.floorMod(w.seed+id,38L).toInt(),52+id%28,"谨慎守土","维持${name}军政与民生");return id}
  val multiplier=when(w.scale){"超级大国"->3;"小国"->1;else->2}
  val units=w.towns.map {t->val title=if(t.rank=="县城")"县尉"else if(t.rank=="府城")"府都尉"else"营都尉";val base=if(t.rank=="县城")60 else if(t.rank=="府城")120 else 240;Garrison("garrison-${t.id}",t.name+if(t.rank=="县城")"县兵"else if(t.rank=="府城")"府卫"else"卫营",if(t.rank=="县城")"县兵"else if(t.rank=="府城")"府卫"else"卫营",t.id,commander=officer(t.name,title),soldiers=base*multiplier)}.toMutableList()
  val states=AdministrativeEngine.seeds.filter {it.rank=="府城"}.distinctBy {AdministrativeEngine.state(it)}
  for(s in states){val id=AdministrativeEngine.state(s);val name=AdministrativeEngine.stateName(w,id);units+=Garrison("garrison-$id",name+"军","州军",s.id,id,officer(name,"统领"),officer(name,"刺史"),400*multiplier)}
  return w.copy(people=people,localDefense=LocalDefense(units,true))
 }
 fun office(w:World,person:Int)=w.localDefense.units.firstOrNull {it.commander==person||it.governor==person}
 fun troops(w:World)=w.localDefense.units.sumOf {it.soldiers}+CrownEngine.units(w).sumOf {it.soldiers}
 fun cost(c:Command):Long=when(c.type){"LOCAL_RECRUIT"->(c.level/50*2).toLong();"LOCAL_APPOINT"->15;"LOCAL_TRAIN"->10;"LOCAL_MOBILIZE"->(c.level/50).toLong();else->0}
 fun validate(w:World,c:Command):String? {
  val u=w.localDefense.units.find {it.id==c.target}?:return "地方兵籍不存在。"
  return when(c.type){
   "LOCAL_RELEASE"->if(c.objective.toIntOrNull() !in listOf(u.commander,u.governor))"此人未任此地军政职务。"else null
   "LOCAL_APPOINT"->{val p=w.people.find {it.id==c.objective.toIntOrNull()&&it.alive};when {p==null||p.ageQuarters<72||p.ability<(if(c.mode=="州官")60 else 50)->"任职者须成年、在世，军官能力50以上，州官60以上。";c.mode !in listOf("军官","州官")||c.mode=="州官"&&u.kind!="州军"->"请选择相符的地方军政职位。";p.id in 0..2||p.id==w.academy.tutor||TalentEngine.town(w,p.id)!=null||CrownEngine.units(w).any {it.commander==p.id}||IntelligenceEngine.membership(w,p.id)!=null||EducationEngine.busy(w,p.id)||w.journey?.let {it.companion==p.id||it.regent==p.id}==true->"此人有朝廷、地方文官、统军、组织、求学或行在职务冲突。";p.id==if(c.mode=="州官")u.governor else u.commander->"此人已在任。";else->null}}
   "LOCAL_TRAIN"->if(u.training>=100||u.morale<20||w.people.none {it.id==u.commander&&it.alive&&!EducationEngine.busy(w,it.id)})"训练需要在岗军官、20以上士气，且训练未满。"else null
   "LOCAL_RECRUIT"->if(c.level !in listOf(50,100,200,500)||u.soldiers+c.level>(if(u.kind=="州军")10000 else 2000)||troops(w)+c.level>w.nation.population/20)"招募数须为预设兵额，并受当地编制与全国人口承受上限约束。"else null
   "LOCAL_DEMOB"->if(c.level !in listOf(50,100,200,500)||u.soldiers<c.level)"请选择不超过现有兵额的预设裁军数。"else null
   "LOCAL_MOBILIZE"->{val army=CrownEngine.units(w).find {it.id==c.objective};if(army==null||c.level !in listOf(50,100,200,500)||u.soldiers-c.level<50||army.soldiers+c.level>30000)"须选择中央军与可调兵额，驻地至少留50人，中央军不超三万人。"else null}
   else->"未知地方军政行动。"
  }
 }
 fun execute(w:World,c:Command,events:MutableList<String>):World {
  val u=w.localDefense.units.first {it.id==c.target}
  if(c.type=="LOCAL_APPOINT"||c.type=="LOCAL_RELEASE"){
   val id=c.objective.toInt();val old=if(c.type=="LOCAL_RELEASE")id else if(c.mode=="州官")u.governor else u.commander
   val units=w.localDefense.units.map {g->var a=g.copy(commander=if(g.commander==id)-1 else g.commander,governor=if(g.governor==id)-1 else g.governor);if(g.id==u.id&&c.type=="LOCAL_APPOINT")a=if(c.mode=="州官")a.copy(governor=id)else a.copy(commander=id);a}
   val title=if(c.mode=="州官")AdministrativeEngine.stateName(w,u.state)+"刺史"else u.name+"统领"
   val people=w.people.map {p->when {p.id==id&&c.type=="LOCAL_APPOINT"->p.copy(role=title,memories=(p.memories+"${w.day}日赴任$title").takeLast(8));p.id==old&&p.alive->p.copy(role="待选人才",memories=(p.memories+"${w.day}日交卸${u.name}职务").takeLast(8));else->p}}
   events+="${w.people.first {it.id==id}.name}${if(c.type=="LOCAL_RELEASE")"交卸"else"就任"}${u.name}职务，兵籍与俸禄登记同步更新。";return w.copy(people=people,localDefense=w.localDefense.copy(units=units))
  }
  val updated=when(c.type){"LOCAL_TRAIN"->{val p=w.people.first {it.id==u.commander};u.copy(training=(u.training+1+p.ability*p.loyalty/2000).coerceAtMost(100))};"LOCAL_RECRUIT"->u.copy(soldiers=u.soldiers+c.level);"LOCAL_DEMOB"->u.copy(soldiers=u.soldiers-c.level,morale=(u.morale-3).coerceAtLeast(0));else->u.copy(soldiers=u.soldiers-c.level)}
  events+="${u.name}${Rules.label(c)}完成：兵额${updated.soldiers}，训练${updated.training}；工资、粮需随兵额变化。"
  return w.copy(localDefense=w.localDefense.copy(units=w.localDefense.units.map {if(it.id==u.id)updated else it}),armies=if(c.type=="LOCAL_MOBILIZE")CrownEngine.units(w).map {if(it.id==c.objective)it.copy(soldiers=it.soldiers+c.level)else it}else w.armies,nation=if(c.type=="LOCAL_RECRUIT")w.nation.copy(morale=(w.nation.morale-1).coerceAtLeast(0))else w.nation)
 }
 fun quarter(w:World,ledger:MutableList<String>):World {
  val people=w.people.associateBy {it.id};val studying=w.studies.map {it.person}.toSet()
  val units=w.localDefense.units.map {u->val p=people[u.commander]?.takeIf {it.alive&&it.id !in studying};val skill=p?.let {it.ability*it.loyalty/100}?:0;val governor=people[u.governor]?.takeIf {it.alive&&it.id !in studying};val coordination=if(u.kind=="州军")governor?.let {it.ability*it.loyalty/1000}?:-5 else 0;u.copy(commander=if(people[u.commander]?.alive==true)u.commander else -1,governor=if(people[u.governor]?.alive==true)u.governor else -1,training=(u.training+if(skill>=60)1 else 0).coerceAtMost(100),morale=(u.morale+if(skill>=45)1 else -1).coerceIn(0,100),supply=((60+skill/5+coordination)*w.resources.militaryFoodRatio/100).coerceIn(0,100))}
  ledger+="地方兵籍核算：${units.size}支州军/府卫/县兵，共${units.sumOf {it.soldiers}}人，训练与士气按军官在岗、能力及忠诚结算；州刺史在岗、能力与忠诚影响州军补给协调，不能制造缺失军粮。"
  val towns=w.towns.map {t->val guard=units.firstOrNull {it.home==t.id&&it.kind!="州军"};val secure=guard!=null&&guard.soldiers>=50&&guard.morale>=40&&guard.supply>=30;val delta=if(secure)1 else -1;t.copy(security=(t.security+delta).coerceIn(0,100))}
  ledger+="县府驻防按兵额、粮饷与士气影响地方治安；不足50人或士气/补给不足的驻地治安下降。"
  return w.copy(towns=towns,localDefense=w.localDefense.copy(units=units))
 }
 fun verify(w:World){val s=w.localDefense;val people=w.people.map {it.id}.toSet();val towns=w.towns.map {it.id}.toSet();require(s.units.size<=210&&s.units.map {it.id}.distinct().size==s.units.size);val officers=s.units.flatMap {listOf(it.commander,it.governor)}.filter {it>=0};require(officers.distinct().size==officers.size&&officers.all {it in people});s.units.forEach {require(it.soldiers in 0..10000&&it.training in 0..100&&it.morale in 0..100&&it.supply in 0..100&&it.home in towns)}}
}
