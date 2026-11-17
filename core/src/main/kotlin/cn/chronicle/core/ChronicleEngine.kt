package cn.chronicle.core

import kotlinx.serialization.Serializable

@Serializable data class ChapterRecord(val number:Int,val day:Int,val title:String,val facts:List<String>,val assessment:String)
@Serializable data class Bulletin(val quarter:Int,val day:Int,val income:Long,val expense:Long,val cash:Long,val debt:Long,val risks:List<String>,val choices:List<String>)
@Serializable data class ChronicleState(val enabled:Boolean=false,val guided:Boolean=false,val stage:Int=0,val chapter:Int=1,val route:String="吏治清明",val stageDay:Int=0,val flags:List<String> = emptyList(),val records:List<ChapterRecord> = emptyList(),val bulletins:List<Bulletin> = emptyList(),val pauseSerial:Int=0,val acknowledged:Int=0,val autoPause:Boolean=true,val completedCases:Int=0,val outcome:String="",val activeCase:String="",val rewindUsed:Boolean=false)
@Serializable data class GovernmentState(val reserveOrder:Boolean=false,val priceOrder:Boolean=false,val reserveLots:Int=2,val budget:Long=6,val grainPrice:Int=100,val lastQuarter:Int=-1,val shortages:Int=0,val arrearQuarters:Int=0,val laborSafety:Boolean=false,val landSurvey:Int=0,val postService:Boolean=false,val routeChoices:Int=0)
data class FiscalForecast(val quarter:Int,val income:Long,val expense:Long,val cash:Long,val debt:Long,val shortage:Long)
object ChronicleEngine {
 val stages=listOf("察 · 查访取证","断 · 预览裁处","营 · 治理建设","应 · 外压取舍","传 · 史官传承")
 val routes=listOf("吏治清明","富民安邦","经世兴业","和邻经略")
 val labels=linkedMapOf("CONTENT_ENABLE" to "开启新章","GUIDANCE" to "设置新手引导","ACK_REPORT" to "阅毕奏报","CHAPTER_ROUTE" to "选择长期路线","CHAPTER_CLOSE" to "史官收章","RESERVE_ORDER" to "常设购粮诏令","PRICE_ORDER" to "常设平粜诏令","BRIDGE_LOAN" to "筹借救急款","STOP_RESEARCH" to "暂停研究拨款","LAND_SURVEY" to "清丈田亩","POST_SERVICE" to "整饬驿役","LABOR_SAFETY" to "颁行劳工保障","EMERGENCY_PAY" to "集中补发薪饷")
 val types=labels.keys
 fun start(w:World,guided:Boolean=true,protection:Boolean=true):World {
  if(w.chronicle.enabled)return w
  return SovereignEngine.initialize(w.copy(caseBook=w.caseBook.copy(enabled=true),chronicle=ChronicleState(enabled=true,guided=guided,autoPause=guided,stage=if(w.caseFile.resolved)2 else 0,stageDay=w.day)),protection)
 }
 fun objective(w:World):String=when(w.chronicle.stage){0->if(w.chronicle.chapter==1)"澄江税册与传闻不合：亲访或委派监察，先取得可核材料。"else"新案已列入案簿：受理后独立走访、调簿与复核，先核事实。";1->"先核账与取证，再选择依法追究、行政撤换或保留指控；不凭传闻定罪。";2->"用现有预算立一项工程，预留工期与薪饷；阅读一份季末奏报。";3->"应对粮价、欠饷或外部压力：做出一次外交、粮政或军政取舍。";else->"史官据实际结算写起居注；安排储位或明确摄政，再收束本章。"}
 fun unlocked(w:World,system:String):Boolean= !w.chronicle.enabled||!w.chronicle.guided||when(system){"时间","行旅","案件","档案","模型"->true;"财政","民生","任官"->w.chronicle.stage>=1;"工程","学院","运河"->w.chronicle.stage>=2;"军政","外交","谍报"->w.chronicle.stage>=3;else->w.chronicle.stage>=4}
 fun needsPause(w:World)=w.chronicle.enabled&&w.chronicle.autoPause&&w.chronicle.pauseSerial>w.chronicle.acknowledged
 fun cost(c:Command):Long=when(c.type){"LAND_SURVEY"->50;"POST_SERVICE"->25;"LABOR_SAFETY"->30;"EMERGENCY_PAY"->c.level.toLong();else->0}
 fun duration(c:Command)=when(c.type){"LAND_SURVEY"->60;"POST_SERVICE","LABOR_SAFETY"->30;else->0}
 fun validate(w:World,c:Command):String? {
  if(c.type=="CONTENT_ENABLE")return if(w.chronicle.enabled)"新章已开启。"else null
  if(!w.chronicle.enabled)return "请先开启新章；旧存档和历史不会删除。"
  if(c.type=="GUIDANCE"&&c.mode !in listOf("开启","关闭"))return "请选择引导开关。"
  if(c.type=="CHAPTER_ROUTE"&&c.mode !in routes)return "请选择已有长期路线。"
  if(c.type=="CHAPTER_CLOSE"&&w.chronicle.stage!=4)return "尚未走完本章的查访、治理和外压取舍。"
  if(c.type=="BRIDGE_LOAN"&&(c.level !in listOf(100,200,300)||w.nation.debt+c.level>2000))return "救急借款须为100/200/300万两且不突破2000万两信用上限，之后按现行债务付息。"
  if(c.type=="STOP_RESEARCH"&&w.nation.research==0)return "没有持续研究拨款。"
  if(c.type=="LAND_SURVEY"&&(w.setting!=EraRules.ANCIENT||w.government.landSurvey>=3))return "清丈田亩为古代专属，本版最多三轮。"
  if(c.type=="POST_SERVICE"&&(w.setting!=EraRules.ANCIENT||w.government.postService))return "驿役整饬为古代专属且已经登记的不重复征发。"
  if(c.type=="LABOR_SAFETY"&&(w.setting==EraRules.ANCIENT||w.government.laborSafety))return "劳工保障为工业时代专属，不重复颁行。"
  if(c.type=="EMERGENCY_PAY"&&(c.level !in listOf(1,5,10,20)||w.payroll.arrears.isEmpty()))return "请选择1/5/10/20万两补发已有欠俸欠饷。"
  if(c.type in listOf("RESERVE_ORDER","PRICE_ORDER")&&c.mode !in listOf("开启","关闭"))return "请选择常设诏令开关。"
  return null
 }
 fun execute(w:World,c:Command,events:MutableList<String>):World {
  var x=w
  when(c.type){
   "CONTENT_ENABLE"->{x=start(w,c.mode!="自由",c.level!=0);events+="保留原有存档与历史，开启案件、奏报、护卫和章节。"}
   "GUIDANCE"->x=x.copy(chronicle=x.chronicle.copy(guided=c.mode=="开启"))
   "ACK_REPORT"->x=x.copy(chronicle=x.chronicle.copy(acknowledged=x.chronicle.pauseSerial))
   "CHAPTER_ROUTE"->x=x.copy(chronicle=x.chronicle.copy(route=c.mode))
   "RESERVE_ORDER"->x=x.copy(government=x.government.copy(reserveOrder=c.mode=="开启"))
   "PRICE_ORDER"->x=x.copy(government=x.government.copy(priceOrder=c.mode=="开启"))
   "BRIDGE_LOAN"->{x=x.copy(nation=x.nation.copy(treasury=x.nation.treasury+c.level,debt=x.nation.debt+c.level));events+="筹借${c.level}万两，现金与债务同时增加；每季利息按债务的2%计入，不是无成本财政。"}
   "STOP_RESEARCH"->{x=x.copy(nation=x.nation.copy(research=0));events+="暂停以后季度的研究拨款，已发生费用不返还。"}
   "LAND_SURVEY"->{x=x.copy(government=x.government.copy(landSurvey=x.government.landSurvey+1),nation=x.nation.copy(agriculture=x.nation.agriculture+3,corruption=x.nation.corruption-2),factions=x.factions.mapIndexed {i,f->if(i==1)f.copy(support=(f.support-4).coerceAtLeast(0))else f});events+="清丈完毕：农业+3、腐败-2，涉及旧地权，工商/地方利益支持-4。旧契与复核仍是案件证据，清丈不自动判罪。"}
   "POST_SERVICE"->{x=x.copy(government=x.government.copy(postService=true),nation=x.nation.copy(infrastructure=x.nation.infrastructure+5,morale=x.nation.morale-4));events+="驿役改制：基建+5、民心-4，每季另支2万两用于补偿与巡检。"}
   "LABOR_SAFETY"->{x=x.copy(government=x.government.copy(laborSafety=true),nation=x.nation.copy(stability=x.nation.stability+4,morale=x.nation.morale+4));events+="劳工保障颁行：稳定/民心各+4，每季预算6万两；国营扩建制品产量让出8%用于安全轮休。"}
   "EMERGENCY_PAY"->{repeat(c.level){x=PayrollEngine.settleArrears(x,events)};events+="本次集中补发${c.level}万两，尾款保留薪资基金。"}
   "CHAPTER_CLOSE"->{val ch=x.chronicle;val record=ChapterRecord(ch.chapter,w.day,"${stages.last()} · ${ch.route}",listOf("澄江案：${w.caseFile.resolution.ifBlank {w.caseFile.status}}","本章已结地方案件${ch.completedCases}件","国库${w.nation.treasury}万两、债务${w.nation.debt}万两","民心${w.nation.morale}、腐败${w.nation.corruption}","储位：${SovereignEngine.heirName(w)}"),if(w.caseBook.witnessedRulerAbuse>0)"强制处置留下异议，史官不将权力等同公正。"else if(w.nation.debt>1200)"措置虽成，财政仍待后继者收拾。"else"功过据实记载，未明之处留待后章。")
    x=x.copy(chronicle=ch.copy(chapter=ch.chapter+1,stage=0,stageDay=w.day,flags=emptyList(),records=(ch.records+record).takeLast(24)));x=InvestigationEngine.spawn(x);x=x.copy(chronicle=x.chronicle.copy(activeCase=x.caseBook.cases.lastOrNull {!it.resolved}?.id.orEmpty()));events+="史官写下本章起居注；下一章留下${if(w.caseBook.witnessedRulerAbuse>0)"强制处置的追问"else"新的地方线索"}，不改写已经发生的记录。"}
  }
  events+="${labels.getValue(c.type)}已登记。";return x
 }
 fun progress(w:World,c:Command?,events:MutableList<String>):World {
  if(!w.chronicle.enabled)return w
  var ch=w.chronicle;var flags=ch.flags
  c?.let {if(it.type in listOf("URBAN_BUILD","FACTORY","WORKSHOP","FARM","ROAD","CANAL","ACADEMY_UPGRADE"))flags=(flags+"工程立项").distinct();if(it.type in listOf("DIPLOMACY","TRADE_RESOURCE","RELIEF","ARMY_TRAIN","INTEL_COUNTER","WAR_PEACE","WAR_WITHDRAW","WAR_MEDIATE","EMERGENCY_PAY"))flags=(flags+"外压取舍").distinct()}
  if(events.any {it.contains("竣工")||it.contains("完工")})flags=(flags+"工程竣工").distinct()
  val selected=w.caseBook.cases.find {it.id==ch.activeCase};val resolved=if(ch.chapter==1)w.caseFile.resolved else selected?.resolved==true;val ready=if(ch.chapter==1)w.caseFile.evidence.size>=2 else selected?.let {InvestigationEngine.score(it)>=65}==true
  val stage=when(ch.stage){0->if(resolved)2 else if(ready)1 else 0;1->if(resolved)2 else 1;2->if("工程立项" in flags&&ch.bulletins.isNotEmpty()&&("工程竣工" in flags||w.towns.any {(it.buildings["SCHOOL"]?:0)>=2}))3 else 2;3->if("外压取舍" in flags)4 else 3;else->4}
  if(stage!=ch.stage){events+="【章节目标】${stages[stage]}：${objective(w.copy(chronicle=ch.copy(stage=stage)))}";ch=ch.copy(stage=stage,stageDay=w.day,pauseSerial=ch.pauseSerial+1)}
  val closed=w.caseBook.cases.count {it.resolved};var ending=ch.outcome
  if(ending.isBlank())ending=when(ch.route){"吏治清明"->if(closed>=3&&w.nation.corruption<=35&&w.caseBook.witnessedRulerAbuse==0)"清明之治"else"";"富民安邦"->if(w.nation.morale>=75&&w.nation.agriculture>=70&&w.nation.debt<=500&&w.government.shortages==0)"仓廪安民"else"";"经世兴业"->if(w.setting==EraRules.ANCIENT&&w.government.landSurvey>=2&&w.academy.level>=3)"经世之学"else if(w.setting!=EraRules.ANCIENT&&w.resources.plants.sum()>=3&&w.nation.technology>=40&&w.government.laborSafety)"百工立国"else"";else->if(w.neighbors.all {it.relation>=75}&&w.war==null&&w.payroll.arrears.isEmpty())"和邻守成"else""}
  if(ending.isNotBlank()&&ch.outcome.isBlank())events+="【路线里程碑】${ending}达成，可以继续经营；不自动终止存档。"
  return w.copy(chronicle=ch.copy(flags=flags.takeLast(24),completedCases=closed,outcome=ending))
 }
 fun risks(w:World):List<String> = buildList {if(w.nation.treasury<60)add("现金缓冲不足，须保工资与军粮");if(w.payroll.arrears.isNotEmpty())add("欠俸欠饷已影响忠诚和士气");if(w.resources.militaryFoodRatio<80)add("军粮供给不足，出征与驻防受影响");if(w.government.grainPrice>=125)add("粮价指数${w.government.grainPrice}，民间已有不安");if(w.intelligence.threat>=60)add("内外渗透有征兆，可核安保而非随意抓人");if(w.war!=null)add("战争持续消耗月费与兵员");if(w.nation.debt>=1600)add("接近信用上限，不宜再扩张预算")}.take(3)
 fun quarter(w:World,ledger:MutableList<String>,events:MutableList<String>):World {
  if(!w.chronicle.enabled||w.government.lastQuarter==w.turn)return w
  val food=w.resources.foodAvailable.coerceAtLeast(1);val price=(w.nation.population*100/food).toInt().coerceIn(75,200)
  var x=w.copy(government=w.government.copy(lastQuarter=w.turn,grainPrice=price,shortages=if(price>=130)w.government.shortages+1 else 0,arrearQuarters=if(w.payroll.arrears.isNotEmpty())w.government.arrearQuarters+1 else 0))
  val expense=(if(x.government.laborSafety)6L else 0)+(if(x.government.postService)2L else 0)+SovereignEngine.guardUpkeep(x)
  val canPay=minOf(x.nation.treasury,expense);x=x.copy(nation=x.nation.copy(treasury=x.nation.treasury-canPay,expense=x.nation.expense+expense*4));ledger+="新制度与护卫经费-${canPay}万两，应支${expense}万两；预算不足不会凭空补齐。"
  if(canPay<expense){x=x.copy(sovereign=x.sovereign.copy(security=(x.sovereign.security-3).coerceAtLeast(0)));events+="制度和护卫预算不足，执行效果受限。"}
  if(x.government.arrearQuarters==1)events+="【预兆】军中议论欠饷，尚未认定任何将领谋反。可补发、借款、裁军或停战。"
  if(x.government.arrearQuarters>=2&&x.localDefense.units.any {it.morale<30&&it.soldiers>=50}){val u=x.localDefense.units.first {it.morale<30&&it.soldiers>=50};val lost=(u.soldiers/20).coerceAtLeast(1);x=x.copy(localDefense=x.localDefense.copy(units=x.localDefense.units.map {if(it.id==u.id)it.copy(soldiers=it.soldiers-lost,morale=(it.morale-3).coerceAtLeast(0))else it}),nation=x.nation.copy(stability=x.nation.stability-3));events+="${u.name}欠饷与低士气持续，${lost}人离队，稳定-3；未把全营自动认作叛军。"}
  var spent=0L;val reserve=(x.nation.population/8).coerceAtMost(ResourceEngine.grainCapacity(x)/2)
  val trade=when {x.government.reserveOrder&&ResourceEngine.available(x,"GRAIN")<reserve->Command("TRADE_RESOURCE",target="采购",objective="GRAIN",level=x.government.reserveLots);x.government.priceOrder&&price>=130&&ResourceEngine.available(x,"GRAIN")>=reserve+100000->Command("TRADE_RESOURCE",target="出售",objective="GRAIN",level=1);else->null}
  if(trade!=null&&x.pending.size<3&&x.pending.none {it.command.type=="TRADE_RESOURCE"}&&Rules.validate(x,trade)==null&&Rules.cost(x,trade)<=x.government.budget&&x.nation.treasury>=Rules.cost(x,trade)+20){val out=RealtimeEngine.submit(Rules.bounded(x),trade,x.revision);spent=Rules.cost(x,trade);x=out.world.copy(revision=x.revision,recent=x.recent);ledger+="常设诏令已预留${spent}万两办理${trade.target}粮食，仍须走完7日商路并遵守每季限额。"}
  if(price>=130)events+="【预兆】粮价走高；低储备应采购或止售，储备充足才平粜，不能卖空粮仓。"
  val risk=risks(x);val choices=if(x.payroll.arrears.isNotEmpty())listOf("补发欠饷并缩减预算","借款救急，承担后续利息")else if(price>=125)listOf("采购储粮，保留军粮","外交稳贸，减少边患")else listOf("留存缓冲，核对在建工程","择一投资，承担真实工期")
  val bulletin=Bulletin(w.turn,w.day,x.nation.income/4,x.nation.expense/4,x.nation.treasury,x.nation.debt,risk,choices)
  x=x.copy(chronicle=x.chronicle.copy(bulletins=(x.chronicle.bulletins+bulletin).takeLast(4),pauseSerial=x.chronicle.pauseSerial+1))
  return progress(x,null,events)
 }
 fun forecast(w:World):List<FiscalForecast> {
  var cash=w.nation.treasury;var debt=w.nation.debt;val taxes=(100L+w.nation.agriculture*2+w.nation.commerce*2+w.nation.industry*3-w.resources.industrialTaxBaseLoss).coerceAtLeast(100)*w.nation.tax/100*(100-w.nation.corruption/2)/100-w.resources.grainTaxValueWan
  val localIncome=w.towns.sumOf {MunicipalEngine.extra(it,"MARKET")*5L+MunicipalEngine.extra(it,"WORKSHOP")*4L*(w.resources.yields[it.id]?.operation?:100)/100}
  val localCost=w.towns.sumOf {t->MunicipalEngine.buildings.sumOf {MunicipalEngine.extra(t,it.id)*(if(it.id in listOf("WORKSHOP","BARRACKS"))2L else 1L)}}
  val familyCost=w.household.members.count {it.alive&&it.role=="子女"}*3L+(if(w.household.pregnancy!=null)6 else 0)
  val borderCost=(if(w.atmosphere=="烽火边疆"&&w.neighbors.any {it.relation<40})15L else 0)+(w.cartography?.territories?.count {it.id.startsWith("frontier-")&&it.polityId=="nation"}?:0)*5L
  val fixed=localCost+familyCost+borderCost+(if(w.difficulty=="艰难")12 else 0)+20L+w.nation.military/3+w.nation.infrastructure/4+w.nation.research+w.academy.level*2+CrownEngine.maintenance(w)+IntelligenceEngine.upkeep(w)+w.canals.size*3+SovereignEngine.guardUpkeep(w)+(if(w.government.laborSafety)6 else 0)+(if(w.government.postService)2 else 0)+(PayrollEngine.lines(w).sumOf {it.liang}+9999)/10000+(if(w.war!=null)(40+CrownEngine.units(w).first {it.id==w.war.army}.soldiers/1000)*3 else 0)
  return (1..4).map {q->val expense=fixed+debt*2/100;val balance=cash+taxes.coerceAtLeast(0)+localIncome-expense;val borrow=minOf((-balance).coerceAtLeast(0),(2000-debt).coerceAtLeast(0));debt+=borrow;cash=(balance+borrow).coerceAtLeast(0);FiscalForecast(w.turn+q,taxes.coerceAtLeast(0)+localIncome,expense,cash,debt,(-balance-borrow).coerceAtLeast(0))}
 }
 fun verify(w:World){val c=w.chronicle;require(c.stage in 0..4&&c.chapter in 1..100000&&c.route in routes&&c.records.size<=24&&c.flags.size<=24&&c.bulletins.size<=4&&c.acknowledged in 0..c.pauseSerial&&w.government.reserveLots in 1..3&&w.government.budget in 0..20&&w.government.grainPrice in 75..200&&w.government.landSurvey in 0..3)}
}
