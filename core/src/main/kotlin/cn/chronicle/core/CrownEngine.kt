package cn.chronicle.core

import kotlinx.serialization.Serializable

@Serializable data class RoyalEstate(val levels:Map<String,Int> = mapOf("PALACE" to 1,"GARDEN" to 0,"ARCHIVE" to 0),val work:UrbanWork?=null)
@Serializable data class ArmyUnit(val id:String,val name:String,val region:Int,val commander:Int=-1,val soldiers:Int,val training:Int=40,val morale:Int=60,val supply:Int=70)
object CrownEngine {
 val types=setOf("PALACE_BUILD","CONSORT_AUDIENCE","CONSORT_ACCEPT","CONSORT_DECLINE","ARMY_ASSIGN","ARMY_TRAIN","ARMY_MOVE","ARMY_RECRUIT","ARMY_DEMOB")
 val buildings=listOf(BuildingSpec("PALACE","宫殿","宫廷",180,150,"竣工行政+2、稳定+1，劳役使民心-2"),BuildingSpec("GARDEN","御苑","宫廷",100,100,"竣工家族关系+3，民心-1"),BuildingSpec("ARCHIVE","典籍馆","宫廷",120,120,"竣工格物/科技+1、行政+1"))
 fun base(w:World)=when(w.scale){"超级大国"->12000;"小国"->2400;else->6000}
 fun units(w:World)=w.armies.ifEmpty {listOf(ArmyUnit("guard","禁卫军",0,-1,base(w)),ArmyUnit("north","北境军",3,if(w.people.any {it.id==1&&it.alive})1 else -1,base(w)),ArmyUnit("field","行营军",1,-1,base(w)))}
 fun cost(c:Command):Long=when(c.type){"PALACE_BUILD"->(buildings.find {it.id==c.objective}?.cost?:0)*c.level;"CONSORT_AUDIENCE"->25;"CONSORT_ACCEPT"->15;"ARMY_ASSIGN"->15;"ARMY_TRAIN"->40;"ARMY_MOVE"->25;"ARMY_RECRUIT"->35;else->0}
 fun maintenance(w:World)=w.estate.levels.entries.sumOf {(kind,level)->(level-if(kind=="PALACE")1 else 0).coerceAtLeast(0)*2L}+w.household.members.count {it.alive&&it.role=="成年伴侣"}.minus(1).coerceAtLeast(0)*2L+units(w).sumOf {(it.soldiers-base(w)).coerceAtLeast(0)/1000*2L}
 fun power(w:World,u:ArmyUnit)=u.soldiers.toLong()*(u.training+u.morale+u.supply+(w.people.find {it.id==u.commander&&it.alive&&!EducationEngine.busy(w,it.id)}?.let {it.ability*it.loyalty/100}?:20))/400
 fun validate(w:World,c:Command):String? {
  when(c.type){
   "PALACE_BUILD"->{val b=buildings.find {it.id==c.objective}?:return "宫廷建筑不存在。";if(w.estate.work!=null)return "已有宫廷工程在建。";if(c.level !in 1..5||c.level!=w.estate.levels.getValue(b.id)+1)return "建筑等级已变化或已达上限。"}
   "CONSORT_AUDIENCE"->{if(w.household.members.size>98)return "宗室档案已满。";if(w.household.members.count {it.alive&&it.role=="待选伴侣"}>=4)return "请先处理现有待选人士。"}
   "CONSORT_DECLINE"->if(w.household.members.none {it.id==c.target.toIntOrNull()&&it.alive&&it.role=="待选伴侣"})return "请选择在世待选人士。"
   "CONSORT_ACCEPT"->{val p=w.household.members.find {it.id==c.target.toIntOrNull()}?:return "此人不存在。";if(!p.alive||p.role!="待选伴侣"||p.ageAt(w.day)<18||!p.willing||p.relationship<50)return "须为在世成年、自愿且关系合适的待选人士。";if(w.household.members.count {it.alive&&it.role=="成年伴侣"}>=4)return "本版最多四位在世伴侣。"}
   else->{val u=units(w).find {it.id==c.target}?:return "军队不存在。";when(c.type){
    "ARMY_ASSIGN"->{val p=w.people.find {it.id==c.objective.toIntOrNull()&&it.alive}?:return "将领须在世。";if(p.ageQuarters<72||p.ability<55||p.id in listOf(0,2)||p.id==w.academy.tutor||IntelligenceEngine.membership(w,p.id)!=null||TalentEngine.town(w,p.id)!=null||LocalDefenseEngine.office(w,p.id)!=null||EducationEngine.busy(w,p.id)||w.journey?.let {p.id==it.companion||p.id==it.regent}==true)return "须选择成年、能力55以上，且未担任地方官、讲席、首辅、监察使或行在职务的可用人才。";if(units(w).any {it.commander==p.id})return "此人已统领军队，不能兼领另一军。"}
    "ARMY_TRAIN"->if(u.training>=100)return "训练已达上限。"
    "ARMY_MOVE"->if(w.war?.army==u.id)return "该军正在出征，须停战后调整本营。"else if(u.region==c.region)return "军队已在该驻地。"
    "ARMY_DEMOB"->if(u.soldiers<1000) return "不足千人，当前不能按千人整编。"
    "ARMY_RECRUIT"->if(u.soldiers>29000||LocalDefenseEngine.troops(w)+1000>w.nation.population/20)return "兵额已达编制或人口承受上限。"
    else->return "未知军政行动。"
   }}
  };return null
 }
 fun execute(w:World,c:Command,events:MutableList<String>):World=when(c.type){
  "PALACE_BUILD"->{val b=buildings.first {it.id==c.objective};val days=b.days+c.level*20;events+="${b.name}${c.level}级开工，需${days}日；拨款${cost(c)}万两，竣工后每新增等级维护2万两/季。";w.copy(estate=w.estate.copy(work=UrbanWork(b.id,c.level,days,cost(c))))}
  "CONSORT_AUDIENCE"->{val start=(w.household.members.maxOfOrNull {it.id}?:0)+1;val candidates=(0..1).map {i->val id=start+i;FamilyMember(id,listOf("苏映澜","谢令仪","温听雪","姚月禾")[Math.floorMod(w.seed+id,4L).toInt()],"待选伴侣",w.day-(21+id%12)*360,willing=Math.floorMod(w.seed+id,3L)!=0L,relationship=55)};events+="礼官登记两位成年自荐人士，意愿各自记录；面见不代表结为伴侣，不自动开启备孕。";w.copy(household=w.household.copy(members=w.household.members+candidates))}
  "CONSORT_DECLINE"->{events+="结束本次相看，尊重对方自主生活，保留人物与往来记录。";w.copy(household=w.household.copy(members=w.household.members.map {if(it.id==c.target.toInt())it.copy(role="离宫人士")else it}))}
  "CONSORT_ACCEPT"->{val p=w.household.members.first {it.id==c.target.toInt()};events+="${p.name}成年且自愿，登记为宫廷伴侣。备孕仍依既有开关与单独安排。";w.copy(household=w.household.copy(members=w.household.members.map {if(it.id==p.id)it.copy(role="成年伴侣")else it}))}
  else->{val old=units(w).first {it.id==c.target};events+="${old.name}完成${Rules.label(c)}；人员、驻地与训练已登记。";val people=if(c.type=="ARMY_ASSIGN")w.people.map {p->when(p.id){c.objective.toInt()->p.copy(role="${old.name}统领",memories=(p.memories+"${w.day}日赴任${old.name}主将").takeLast(8));old.commander->p.copy(role="待选人才",memories=(p.memories+"${w.day}日交卸${old.name}指挥权").takeLast(8));else->p}}else w.people;w.copy(people=people,armies=units(w).map {u->if(u.id!=c.target)u else when(c.type){"ARMY_ASSIGN"->u.copy(commander=c.objective.toInt());"ARMY_TRAIN"->u.copy(training=(u.training+6).coerceAtMost(100));"ARMY_MOVE"->u.copy(region=c.region);"ARMY_DEMOB"->u.copy(soldiers=u.soldiers-1000,morale=(u.morale-3).coerceAtLeast(0));else->u.copy(soldiers=u.soldiers+1000)}},nation=if(c.type=="ARMY_RECRUIT")w.nation.copy(morale=(w.nation.morale-1).coerceAtLeast(0))else w.nation)}
 }
 fun advance(w:World,events:MutableList<String>):World {
  val work=w.estate.work?:return w
  if(work.remainingDays>1)return w.copy(estate=w.estate.copy(work=work.copy(remainingDays=work.remainingDays-1)))
  val n=w.nation;val updated=when(work.kind){"PALACE"->w.copy(nation=n.copy(administration=(n.administration+2).coerceAtMost(100),stability=(n.stability+1).coerceAtMost(100),morale=(n.morale-2).coerceAtLeast(0)));"ARCHIVE"->w.copy(nation=n.copy(technology=(n.technology+1).coerceAtMost(100),administration=(n.administration+1).coerceAtMost(100)));else->w.copy(nation=n.copy(morale=(n.morale-1).coerceAtLeast(0)),household=w.household.copy(members=w.household.members.map {if(it.alive)it.copy(relationship=(it.relationship+3).coerceAtMost(100))else it}))}
  events+="${buildings.first {it.id==work.kind}.name}${work.level}级竣工：${buildings.first {it.id==work.kind}.effect}；维护费纳入季度财政。"
  return updated.copy(estate=updated.estate.copy(levels=updated.estate.levels+(work.kind to work.level),work=null))
 }
 fun quarter(w:World,ledger:MutableList<String>):World {
  val armies=units(w).map {u->val leader=w.people.find {it.id==u.commander&&it.alive&&!EducationEngine.busy(w,it.id)}
   u.copy(commander=if(w.people.any {it.id==u.commander&&it.alive})u.commander else -1,training=(u.training+if(leader!=null&&leader.ability>=70)1 else 0).coerceAtMost(100),supply=((80-w.regions[u.region].corruption/2-if(w.nation.debt>=2000)20 else 0)*w.resources.militaryFoodRatio/100).coerceIn(0,100),morale=(u.morale+if(leader==null)-1 else if(leader.loyalty>=60)1 else -1).coerceIn(0,100))}
  ledger+="驻防结算："+armies.joinToString("；") {"${it.id}训练${it.training}、士气${it.morale}、补给${it.supply}（依将领在岗、忠诚及驻地腐败）"}
  return w.copy(armies=armies)
 }
 fun verify(w:World){
  require(w.estate.levels.keys==buildings.map {it.id}.toSet()&&w.estate.levels.values.all {it in 0..5})
  w.estate.work?.let {v->val b=buildings.first {it.id==v.kind};require(v.level in 1..5&&v.level==w.estate.levels.getValue(v.kind)+1&&v.remainingDays in 1..250&&v.paid==b.cost*v.level)}
  val army=units(w);require(army.size==3&&army.map {it.id}.toSet()==setOf("guard","north","field"));require(army.all {it.region in 0..3&&it.soldiers in 0..30000&&listOf(it.training,it.morale,it.supply).all {v->v in 0..100}&&(it.commander==-1||w.people.any {p->p.id==it.commander})});val leaders=army.map {it.commander}.filter {it>=0};require(leaders.distinct().size==leaders.size)
 }
}
