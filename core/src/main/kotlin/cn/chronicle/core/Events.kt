package cn.chronicle.core

// Every event has a causal predicate, a finite effect, a cooldown and an audit entry.
data class EventTemplate(val id:String,val title:String,val cooldown:Int,val trigger:(World)->Boolean,val effect:(Nation)->Nation)
object EventEngine {
 val templates=listOf(
  EventTemplate("border_raid","敌对关系与薄弱驻军引来边境劫掠",8,{it.atmosphere=="烽火边疆" && it.neighbors.any {n->n.relation<30 && n.strength>it.nation.military+10}},{it.copy(treasury=(it.treasury-25).coerceAtLeast(0),military=it.military-2)}),
  EventTemplate("gentry_obstruction","贺逢春联合旧势力阻挠监察",10,{it.people.any {p->p.id==9 && p.alive} && it.factions[1].support<45 && it.nation.corruption>40},{it.copy(administration=it.administration-2)}),
  EventTemplate("peace_exchange","和平环境促进边市交流",12,{it.atmosphere=="和平建设" && it.neighbors.all {n->n.relation>=55} && it.nation.commerce<75},{it.copy(commerce=it.commerce+1)}),
  EventTemplate("tax_resist","商户抗税",6,{it.nation.tax>=30},{it.copy(commerce=it.commerce-2,morale=it.morale-2)}),
  EventTemplate("tax_relief","轻徭恢复市集",8,{it.nation.tax<=14 && it.nation.commerce<70},{it.copy(commerce=it.commerce+2)}),
  EventTemplate("debt_alarm","债息挤占预算",6,{it.nation.debt>600},{it.copy(stability=it.stability-2)}),
  EventTemplate("fiscal_buffer","盈余建立储备",12,{it.nation.income>it.nation.expense+100 && it.nation.treasury>800},{it.copy(stability=it.stability+2)}),
  EventTemplate("arrears","国库紧张拖欠薪俸",5,{it.nation.treasury<60},{it.copy(administration=it.administration-2)}),
  EventTemplate("audit","腐败造成税源流失",6,{it.nation.corruption>45},{it.copy(treasury=(it.treasury-20).coerceAtLeast(0))}),
  EventTemplate("factory_workers","工厂吸引新工人",8,{it.nation.industry>40 && it.nation.infrastructure>30},{it.copy(commerce=it.commerce+2)}),
  EventTemplate("industrial_waste","工业排污引发申诉",8,{it.nation.industry>55 && it.nation.administration<65},{it.copy(morale=it.morale-3)}),
  EventTemplate("coal_short","运输制约煤炭供应",5,{it.nation.industry>it.nation.infrastructure+20},{it.copy(industry=it.industry-2)}),
  EventTemplate("machine_tools","研究带动机具改良",10,{it.nation.technology>35 && it.nation.industry>30},{it.copy(industry=it.industry+2)}),
  EventTemplate("workshop","商业订单流入作坊",8,{it.nation.commerce>50 && it.nation.industry<50},{it.copy(industry=it.industry+1)}),
  EventTemplate("grain_short","粮食供需趋紧",5,{it.nation.population>it.nation.agriculture*100000L},{it.copy(morale=it.morale-2)}),
  EventTemplate("irrigation","水利扩大稳定耕地",10,{it.nation.agriculture>55 && it.nation.infrastructure>40},{it.copy(agriculture=it.agriculture+1)}),
  EventTemplate("rural_market","农业余粮进入市场",10,{it.nation.agriculture>60 && it.nation.commerce<65},{it.copy(commerce=it.commerce+2)}),
  EventTemplate("land_strain","人口压力挤占耕地",8,{it.nation.population>6000000 && it.nation.agriculture<65},{it.copy(agriculture=it.agriculture-1)}),
  EventTemplate("school_demand","百姓要求增设学堂",10,{it.nation.morale>65 && it.nation.technology<40},{it.copy(administration=it.administration+1)}),
  EventTemplate("unrest","民怨冲击秩序",5,{it.nation.morale<30},{it.copy(stability=it.stability-3)}),
  EventTemplate("confidence","稳定增强市场信心",12,{it.nation.stability>75 && it.nation.commerce<75},{it.copy(commerce=it.commerce+1)}),
  EventTemplate("relief_trust","赈济形成互助网络",10,{it.nation.morale>75},{it.copy(stability=it.stability+1)}),
  EventTemplate("research_paper","研究院发表成果",8,{it.nation.research>=15 && it.nation.technology<85},{it.copy(technology=it.technology+2)}),
  EventTemplate("brain_drain","薪俸不足流失学者",8,{it.nation.research==0 && it.nation.technology>40},{it.copy(technology=it.technology-1)}),
  EventTemplate("tech_transfer","友邦交流技术",10,{it.neighbors.any {n->n.relation>75} && it.nation.technology<70},{it.copy(technology=it.technology+1)}),
  EventTemplate("border_friction","边境关系紧张",8,{it.neighbors.any {n->n.relation<40} && it.nation.military<45},{it.copy(stability=it.stability-2)}),
  EventTemplate("trade_treaty","互市协定扩大贸易",12,{it.neighbors.all {n->n.relation>=60}},{it.copy(commerce=it.commerce+2)}),
  EventTemplate("supply","道路不足影响军需",6,{it.nation.military>60 && it.nation.infrastructure<40},{it.copy(military=it.military-2)}),
  EventTemplate("drill_results","军队训练改善纪律",10,{it.nation.military>55 && it.nation.administration>50},{it.copy(stability=it.stability+1)}),
  EventTemplate("military_cost","扩军压缩地方支出",8,{it.nation.military>75},{it.copy(morale=it.morale-2)}),
  EventTemplate("faction_conflict","工商派抵制改革",8,{it.factions[1].support<45},{it.copy(administration=it.administration-1)}),
  EventTemplate("reform_support","士林支持考核改革",12,{it.factions[0].support>65 && it.nation.corruption>10},{it.copy(corruption=it.corruption-1)}),
  EventTemplate("case_followup","澄江新官落实追责后改革",10,{it.caseFile.resolution=="依法撤职追缴" && it.regions[0].official!=3},{it.copy(administration=it.administration+1)})
 )
 fun quarter(w:World,ledger:MutableList<String>,events:MutableList<String>):World {
  var x=w
  // Seed controls ordering among eligible events; no unbounded mutable RNG or wall clock.
  val eligible=templates.filter { it.trigger(w) && w.turn-(w.cooldowns[it.id]?:-1000)>=it.cooldown }
   .sortedBy { Math.floorMod(w.seed xor (w.turn*1103515245L) xor it.id.hashCode().toLong(),2147483647L) }.take(2)
  eligible.forEach { t -> val old=x.nation; x=x.copy(nation=t.effect(x.nation),cooldowns=x.cooldowns+(t.id to w.turn)); events+=t.title; ledger+="事件 ${t.id}：${changes(old,x.nation)}；冷却 ${t.cooldown} 季" }
  return x
 }
 private fun changes(a:Nation,b:Nation):String {
  val pairs=listOf("国库" to (b.treasury-a.treasury),"商业" to (b.commerce-a.commerce).toLong(),"工业" to (b.industry-a.industry).toLong(),"农业" to (b.agriculture-a.agriculture).toLong(),"科技" to (b.technology-a.technology).toLong(),"民心" to (b.morale-a.morale).toLong(),"稳定" to (b.stability-a.stability).toLong(),"行政" to (b.administration-a.administration).toLong(),"军力" to (b.military-a.military).toLong(),"腐败" to (b.corruption-a.corruption).toLong())
  return pairs.filter { it.second!=0L }.joinToString { "${it.first} ${if(it.second>0) "+" else ""}${it.second}" }
 }
}

object MemoryEngine {
 fun context(w:World):String = buildString {
  val j=w.journey
  append("你是原创架空历史游戏的叙事员，只描述已结算事实，不能更改数值或创造证据。\n")
  append("时间：${w.date}；国家：${w.name}；工业${w.nation.industry}，农业${w.nation.agriculture}，国库${w.nation.treasury}万两。\n")
  if(j!=null) {
   append("当前地点：${w.regions[j.region].name}${j.location}。NPC眼中的玩家身份：${if(j.revealed) "经印信确认的君主" else j.disguise}。")
   if(!j.revealed) append("NPC不知道真实身份，禁止提及君主、陛下、皇帝或泄露秘密。")
  }
  append("\n可见案卷状态：${w.caseFile.status}；材料：${w.caseFile.evidence.joinToString { it.label }}。不能认定材料真实性或擅自判案。\n")
  append(w.summary.take(300)); append("\n近期：")
  w.recent.takeLast(3).forEach { append(if(j!=null && !j.revealed) "${it.day}日：${it.action}" else it.narrative.take(500)); append('\n') }
  w.people.filter { it.alive && (j==null || j.revealed || it.id in 6..9) }.take(3).forEach { append("${it.name}：${it.role}；${it.memories.takeLast(2).joinToString()}\n") }
 }.take(4500)
}
