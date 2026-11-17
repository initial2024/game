package cn.chronicle.core

import kotlinx.serialization.Serializable

@Serializable data class ForeignCrown(val name:String,val generation:Int=1,val person:Int=-1,val alive:Boolean=true,val captive:Boolean=false,val security:Int=55)
@Serializable data class GuardOffice(val name:String="",val unit:String="",val allocated:Int=0,val upkeep:Long=0)
@Serializable data class RoyalMission(val id:String,val target:String,val targetGeneration:Int,val organization:String,val kind:String,val reward:Long,val due:Int,val issued:Int,val result:String="")
@Serializable data class SovereignState(val initialized:Boolean=false,val bornDay:Int=-35*360,val health:Int=100,val security:Int=55,val guard:GuardOffice=GuardOffice(),val beginnerUntil:Int=0,val beginnerDeadline:Int=360,val heir:Int=-1,val generation:Int=1,val regencyUntil:Int=0,val warnings:Int=0,val warningDue:Int=-1,val delayed:Boolean=false,val lastThreatQuarter:Int=-1,val protectionUntil:Int=0,val foreign:List<ForeignCrown> = emptyList(),val missions:List<RoyalMission> = emptyList(),val missionSerial:Int=0,val coercedPeople:List<Int> = emptyList(),val captives:List<Int> = emptyList(),val successionLog:List<String> = emptyList())
object SovereignEngine {
 val labels=linkedMapOf("BEGINNER_PROTECTION" to "设置新手保护","GUARD_FUND" to "强化护卫","ROYAL_AUDIT" to "核查安保警讯","ITINERARY_CHANGE" to "调整行程","DESIGNATE_HEIR" to "议定储位","ABDICATE" to "安排传位","ROYAL_CONTRACT" to "发布秘密委托","RULER_CONTACT" to "登记邻国君主会谈","RELEASE_CAPTIVE" to "释放被拘人物")
 val types=labels.keys
 fun initialize(w:World,beginner:Boolean):World {
  if(w.sovereign.initialized)return w
  val guard=w.localDefense.units.firstOrNull {it.home=="city-r0"&&it.kind!="州军"};val war=w.atmosphere=="烽火边疆"
  val name=if(w.setting==EraRules.ANCIENT)when(w.scale){"超级大国"->"羽林护卫府";"小国"->"亲随营";else->"宿卫司"}else when(w.scale){"超级大国"->"要员护卫局";"小国"->"内廷卫队";else->"宪卫署"}
  val allocated=minOf((guard?.soldiers?:0)-50,when(w.scale){"超级大国"->300;"小国"->80;else->180}).coerceAtLeast(0)
  val office=GuardOffice(name,guard?.id.orEmpty(),allocated,(when(w.scale){"超级大国"->6;"小国"->1;else->3})+if(war)2L else 0L)
  return w.copy(sovereign=SovereignState(initialized=true,guard=office,beginnerUntil=if(beginner)w.day+360 else 0,beginnerDeadline=w.day+360,foreign=listOf(ForeignCrown("兰祈",security=60),ForeignCrown("阿勒勋",security=65),ForeignCrown("白若衡",security=50))))
 }
 fun guardUpkeep(w:World)=if(w.sovereign.initialized)w.sovereign.guard.upkeep else 0L
 fun guardStrength(w:World):Int {
  val g=w.sovereign.guard;val u=w.localDefense.units.find {it.id==g.unit}?:return 0
  val p=w.people.find {it.id==u.commander&&it.alive}?:return 0
  val assigned=minOf(g.allocated,(u.soldiers-50).coerceAtLeast(0));val service=minOf(u.morale,u.supply,p.loyalty)
  return (assigned.toLong()*service/100/(if(w.scale=="超级大国")15 else 8)).toInt().coerceIn(0,30)
 }
 fun heirName(w:World)=w.household.members.find {it.id==w.sovereign.heir&&it.alive}?.let {"${it.name}（${it.ageAt(w.day)}岁）"}?:"尚未议定"
 fun cost(c:Command):Long=when(c.type){"GUARD_FUND"->45;"ROYAL_AUDIT"->30;"ITINERARY_CHANGE"->15;"DESIGNATE_HEIR"->10;"ABDICATE"->30;"ROYAL_CONTRACT"->c.level.toLong();else->0}
 fun duration(c:Command)=when(c.type){"GUARD_FUND"->14;"ROYAL_AUDIT"->7;"ITINERARY_CHANGE"->3;"DESIGNATE_HEIR"->7;"ABDICATE"->15;else->0}
 fun targetName(w:World,target:String):String=if(target.startsWith("foreign:"))w.sovereign.foreign.getOrNull(target.substringAfter(':').toIntOrNull()?:-1)?.name?:"未知君主" else w.people.find {it.id==target.substringAfter(':').toIntOrNull()}?.name?:"未知人物"
 fun validate(w:World,c:Command):String? {
  if(!w.sovereign.initialized)return "请先开启新章，按国家规模设立护卫。"
  if(c.type=="BEGINNER_PROTECTION")return if(c.mode in listOf("开启","关闭"))null else "请选择保护开关。"
  if(c.type=="DESIGNATE_HEIR"||c.type=="ABDICATE"){
   val id=if(c.type=="ABDICATE")w.sovereign.heir else c.target.toIntOrNull();val heir=w.household.members.find {it.id==id&&it.alive}
   return when {heir==null||heir.role !in listOf("子女","义亲")||heir.ageAt(w.day)<12->"须从在世且至少12岁的子女或义亲中议储；未成年继位将有摄政。";c.type=="ABDICATE"&&w.journey!=null->"请先返回朝廷，完成印信与摄政交接。";else->null}
  }
  if(c.type=="ITINERARY_CHANGE"&&(w.sovereign.warningDue<0||w.sovereign.delayed))return "本次警讯没有待调整的行程，或已延后一次。"
  if(c.type=="RULER_CONTACT")return if(c.region in w.sovereign.foreign.indices&&w.sovereign.foreign[c.region].person<0&&w.people.size<1000)null else "该君主已登记或名册已满。"
  if(c.type=="RELEASE_CAPTIVE")return if(c.target.toIntOrNull() in w.sovereign.captives)null else "此人不在拘留名册中。"
  if(c.type!="ROYAL_CONTRACT")return null
  if(c.level !in listOf(100,200,400)||c.mode !in listOf("刺杀","缉拿"))return "请选择100/200/400万两赏格与刺杀或缉拿；缉拿不等于认定有罪。"
  if(w.sovereign.missions.count {it.result.isBlank()}>=2)return "同时最多两项秘密委托。"
  if(c.target!="hired"){
   val org=w.intelligence.organizations.find {it.id==c.target}?:return "请选择真实存在的组织或雇佣专业人员。"
   if(org.kind!="刺客"||IntelligenceEngine.active(w,org).isEmpty())return "该组织须有在岗成年刺客成员。"
   if(w.sovereign.missions.any {it.result.isBlank()&&it.organization==org.id})return "此组织正在执行一项委托。"
  }
  if(c.objective.startsWith("foreign:")){val i=c.objective.substringAfter(':').toIntOrNull()?:return "目标格式无效。";val crown=w.sovereign.foreign.getOrNull(i)?:return "没有此邻国君主。";if(!crown.alive||crown.captive)return "目标已更替或被拘留，请重新核对。"}
  else if(c.objective.startsWith("person:")){val p=w.people.find {it.id==c.objective.substringAfter(':').toIntOrNull()&&it.alive}?:return "须选择已登记在世人物。";if(p.ageQuarters<72||p.id in w.sovereign.captives)return "此委托系统限已登记的成年虚构人物，已拘留者不可重复悬赏。"}
  else return "只能选择游戏登记的虚构人物或邻国君主，不能输入任意现实目标。"
  return null
 }
 fun execute(w:World,c:Command,events:MutableList<String>):World {
  var x=w;val s=w.sovereign
  when(c.type){
   "BEGINNER_PROTECTION"->{x=x.copy(sovereign=s.copy(beginnerUntil=if(c.mode=="开启"&&w.day<s.beginnerDeadline)s.beginnerDeadline else 0));events+="新手保护${if(x.sovereign.beginnerUntil>w.day)"开启，首次纪年一年内不发生致命袭击；财政、案件、战争成本照常"else"关闭或已到期"}。"}
   "GUARD_FUND"->{x=x.copy(sovereign=s.copy(protectionUntil=w.day+90,security=(s.security+8).coerceAtMost(100)));events+="护卫强化已落实，90日内防护额外+20；使用既有首都卫营，不凭空增加士兵。"}
   "ROYAL_AUDIT"->{x=x.copy(intelligence=w.intelligence.copy(threat=(w.intelligence.threat-18).coerceAtLeast(0)),sovereign=s.copy(security=(s.security+5).coerceAtMost(100)));events+="安保职责与出入登记复核，渗透威胁-18、防护+5；疑点不直接认定某派有罪。"}
   "ITINERARY_CHANGE"->{x=x.copy(sovereign=s.copy(warningDue=s.warningDue+12,delayed=true),intelligence=x.intelligence.copy(threat=(x.intelligence.threat-5).coerceAtLeast(0)));events+="本次行程调整，警讯处理期限延后12日，仅可延后一次。"}
   "DESIGNATE_HEIR"->{x=x.copy(sovereign=s.copy(heir=c.target.toInt()));events+="储位议定：${heirName(x)}。继位不复制先君个人关系，未成年或在外求学需要摄政。"}
   "ABDICATE"->x=succession(x,false,events)
   "RULER_CONTACT"->{val crown=s.foreign[c.region];val id=(w.people.maxOfOrNull {it.id}?:-1)+1;val p=Person(id,crown.name,w.neighbors[c.region].name+"君主",1,140,60+c.region*5,50,"本国利益优先","维持本国权力与边境安全");x=x.copy(people=x.people+p,sovereign=s.copy(foreign=s.foreign.mapIndexed {i,f->if(i==c.region)f.copy(person=id)else f}));events+="${crown.name}以正式邦交身份登记，聊天仍不会直接更改国界、资源或他国命令。"}
   "RELEASE_CAPTIVE"->{val id=c.target.toInt();x=x.copy(sovereign=s.copy(captives=s.captives-id),people=x.people.map {if(it.id==id&&it.alive)it.copy(role="获释待任",memories=(it.memories+"${w.day}日获释；拘留不等于定罪").takeLast(8))else it});events+="被拘人物获释，不撤销原始历史，也不自动恢复信任。"}
   "ROYAL_CONTRACT"->{val serial=s.missionSerial+1;val i=c.objective.removePrefix("foreign:").toIntOrNull();val generation=if(c.objective.startsWith("foreign:"))s.foreign[i!!].generation else 0
    val m=RoyalMission("mission-$serial",c.objective,generation,c.target,c.mode,c.level.toLong(),w.day+if(c.target=="hired")30 else 24,w.day)
    x=x.copy(sovereign=s.copy(missions=(s.missions+m).takeLast(24),missionSerial=serial),nation=x.nation.copy(morale=x.nation.morale-4,stability=x.nation.stability-2),intelligence=x.intelligence.copy(threat=(x.intelligence.threat+8).coerceAtMost(100)))
    events+="对${targetName(w,c.objective)}发布${c.mode}委托，赏格${c.level}万两全额预留，${m.due-w.day}日后结算；不保证成功。失败返40%，目标更替中止返80%，成功不另扣款。政治与反谍风险已经登记。"
   }
  }
  return x
 }
 private fun retire(w:World,id:Int,dead:Boolean,events:MutableList<String>):World {
  var x=w.copy(towns=w.towns.map {it.copy(official=if(it.official==id)-1 else it.official,deputy=if(it.deputy==id)-1 else it.deputy,clerk=if(it.clerk==id)-1 else it.clerk)},regions=w.regions.map {if(it.official==id)it.copy(official=-1)else it},localDefense=w.localDefense.copy(units=w.localDefense.units.map {it.copy(commander=if(it.commander==id)-1 else it.commander,governor=if(it.governor==id)-1 else it.governor)}),armies=CrownEngine.units(w).map {if(it.commander==id)it.copy(commander=-1)else it},studies=w.studies.filter {it.person!=id},intelligence=w.intelligence.copy(organizations=w.intelligence.organizations.map {it.copy(members=it.members-id)}))
  if(w.journey?.let {it.companion==id||it.regent==id}==true)x=x.copy(journey=null)
  val family=w.familyPeople.entries.find {it.value==id}?.key
  x=x.copy(people=x.people.map {if(it.id==id)it.copy(alive=!dead,role=if(dead)"已故"else"拘留待核",memories=(it.memories+"${w.day}日${if(dead)"秘密袭击身亡"else"被拘留，未因此认定犯罪"}").takeLast(8))else it})
  if(family!=null&&dead)x=x.copy(household=x.household.copy(members=x.household.members.map {if(it.id==family)it.copy(alive=false,diedDay=w.day)else it},pregnancy=if(x.household.pregnancy?.parent==family)null else x.household.pregnancy))
  if(!dead)x=x.copy(sovereign=x.sovereign.copy(captives=(x.sovereign.captives+id).distinct()))
  events+="官位、军队、学籍与组织职务同步清理，旧人生经历仍保留。";return x
 }
 fun succession(w:World,death:Boolean,events:MutableList<String>):World {
  val s=w.sovereign;val heir=w.household.members.find {it.id==s.heir&&it.alive&&it.role in listOf("子女","义亲")}
  if(heir==null){if(death){events+="君主身故且无合法储位，本条时间线终止。档案保留；可以读取另存的手动快照继续其他时间线。";return w.copy(ended=true,sovereign=s.copy(health=0,successionLog=(s.successionLog+"${w.day}日${w.ruler}身故，无储").takeLast(12)))};return w}
  val age=heir.ageAt(w.day);val regency=if(age<18)w.day+(18-age)*360 else w.day+7
  events+="${w.ruler}${if(death)"身故"else"传位"}，${heir.name}继位。${if(age<18)"未成年，由在世首辅摄政至成年"else"七日完成印信与仪式交接"}；原行程改记先君史料，不让新君瞬间继承旧旅人身份。"
  val members=w.household.members.map {if(it.id==heir.id)it.copy(role="君主")else it};val mapped=w.familyPeople[heir.id]
  return w.copy(ruler=heir.name,journey=null,household=w.household.copy(members=members),people=w.people.map {if(it.id==mapped)it.copy(role="君主")else it},sovereign=s.copy(bornDay=heir.bornDay,health=heir.health,heir=-1,generation=s.generation+1,regencyUntil=regency,warningDue=-1,successionLog=(s.successionLog+"${w.day}日${w.ruler}→${heir.name}").takeLast(12)),chronicle=w.chronicle.copy(pauseSerial=w.chronicle.pauseSerial+1))
 }
 fun quarter(w:World,events:MutableList<String>):World {
  if(!w.sovereign.initialized||w.sovereign.lastThreatQuarter==w.turn)return w
  var x=w.copy(sovereign=w.sovereign.copy(lastThreatQuarter=w.turn))
  val discontent=w.sovereign.coercedPeople.count {id->w.people.any {it.id==id&&it.alive&&it.loyalty<30}}
  if(discontent>0)x=x.copy(intelligence=x.intelligence.copy(threat=(x.intelligence.threat+discontent*2).coerceAtMost(100)))
  val danger=x.intelligence.threat+(if(x.war!=null)15 else 0)+(if(x.nation.stability<30)20 else 0)
  if(x.sovereign.warningDue<0&&danger>=65&&Seeded.pick(w,"royal-warning:${w.turn}",100)<65){x=x.copy(sovereign=x.sovereign.copy(warningDue=w.day+24,warnings=x.sovereign.warnings+1,delayed=false),chronicle=x.chronicle.copy(pauseSerial=x.chronicle.pauseSerial+1));events+="【安保预兆】出入登记有不合，近臣请核查。尚未证实刺杀或某派有罪；可加强护卫、反谍、调整行程。"}
  return x
 }
 fun advance(w:World,events:MutableList<String>):World {
  if(!w.sovereign.initialized||w.ended)return w
  var x=w
  if(x.sovereign.warningDue>=0&&w.day>=x.sovereign.warningDue){val s=x.sovereign;val real=Seeded.pick(x,"warning-truth:${s.warnings}",5)!=0
   if(!real)events+="安保疑点经核查尚无直接袭击证据。保持留白，不凭风声给任何人定罪。"
   else {val attack=50+x.intelligence.threat/3+Seeded.pick(x,"attack:${s.warnings}",41);val defense=s.security/2+guardStrength(x)+(if(s.protectionUntil>w.day)20 else 0)+x.intelligence.organizations.filter {it.kind in listOf("护卫","监察","谍报")}.sumOf {IntelligenceEngine.active(x,it).sumOf {p->p.ability*p.loyalty/100}/15}
    if(attack<=defense)events+="护卫与核查挫败袭击，未凭空认定幕后主使；线索进入后续调查。"
    else {var damage=(attack-defense+15).coerceIn(10,80);if(s.beginnerUntil>w.day)damage=minOf(damage,(s.health-1).coerceAtLeast(0));val health=(s.health-damage).coerceAtLeast(0);x=x.copy(sovereign=s.copy(health=health),nation=x.nation.copy(stability=x.nation.stability-5));events+="君主遭遇袭击，健康-$damage、稳定-5。${if(s.beginnerUntil>w.day)"新手保护避免致命后果。"else"是否生还由既定防护和健康结算。"}";if(health==0)x=succession(x,true,events)}
   }
   x=x.copy(sovereign=x.sovereign.copy(warningDue=-1),chronicle=x.chronicle.copy(pauseSerial=x.chronicle.pauseSerial+1))
  }
  val missions=x.sovereign.missions.toMutableList()
  for(index in missions.indices){val m=missions[index];if(m.result.isNotBlank()||w.day<m.due)continue
   val foreign=m.target.startsWith("foreign:");val neighbor=if(foreign)m.target.substringAfter(':').toInt()else -1;val crown=if(foreign)x.sovereign.foreign[neighbor]else null;val p=if(foreign)null else x.people.find {it.id==m.target.substringAfter(':').toInt()}
   val valid=if(foreign)crown!!.alive&&!crown.captive&&crown.generation==m.targetGeneration else p?.alive==true&&p.id !in x.sovereign.captives
   if(!valid){x=x.copy(nation=x.nation.copy(treasury=x.nation.treasury+m.reward*4/5));missions[index]=m.copy(result="目标状态改变，中止退80%");events+="${m.id}中止，退回未用赏格80%，不自动追杀继任者。";continue}
   val org=x.intelligence.organizations.find {it.id==m.organization};val skill=if(m.organization=="hired")50+m.reward.toInt()/15 else if(org!=null)IntelligenceEngine.active(x,org).sumOf {it.ability*it.loyalty/100}.coerceAtMost(180)else 0
   val security=crown?.security?:((p?.loyalty?:50)/2+x.nation.administration/4);val chance=(skill+m.reward/10-security/2).toInt().coerceIn(5,85);val success=Seeded.pick(x,m.id+":outcome",100)<chance
   if(success){
    if(foreign){val old=crown!!;if(old.person>=0)x=retire(x,old.person,m.kind=="刺杀",events);val next=if(m.kind=="刺杀")ForeignCrown(old.name.take(1)+listOf("承昭","景明","新衡","继安")[old.generation%4],old.generation+1,security=(old.security+5).coerceAtMost(95))else old.copy(captive=true);x=x.copy(sovereign=x.sovereign.copy(foreign=x.sovereign.foreign.mapIndexed {i,f->if(i==neighbor)next else f}),neighbors=x.neighbors.mapIndexed {i,n->if(i==neighbor)n.copy(relation=(n.relation-35).coerceAtLeast(0),strength=(n.strength-8).coerceAtLeast(0))else n})}
    else x=retire(x,p!!.id,m.kind=="刺杀",events)
    x=x.copy(nation=x.nation.copy(morale=x.nation.morale-7,stability=x.nation.stability-4));missions[index]=m.copy(result="执行成功；发生伤亡/拘留和政治后果")
   }else{x=x.copy(nation=x.nation.copy(treasury=x.nation.treasury+m.reward*2/5),intelligence=x.intelligence.copy(threat=(x.intelligence.threat+12).coerceAtMost(100)));missions[index]=m.copy(result="失败，返40%赏格并增加暴露")}
   events+="${m.id}针对${targetName(w,m.target)}的${m.kind}：${missions[index].result}。不写入细节化暴力描写，也不将缉拿等同定罪。"
  }
  return x.copy(sovereign=x.sovereign.copy(missions=missions))
 }
 fun verify(w:World){val s=w.sovereign;if(!s.initialized)return;require(s.foreign.size==3&&s.health in 0..100&&s.security in 0..100&&s.guard.allocated in 0..1000&&s.guard.upkeep in 0..20&&s.missions.size<=24&&s.missions.count {it.result.isBlank()}<=2&&s.successionLog.size<=12&&s.coercedPeople.size<=1000&&s.captives.size<=1000);s.missions.forEach {require(it.reward in listOf(100L,200L,400L)&&it.due>=it.issued)}}
}
