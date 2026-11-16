package cn.chronicle.core

import kotlin.math.max
import kotlin.math.min

object Rules {
 val policies = listOf("WORKSHOP","FACTORY","FARM","RESEARCH","ROAD","TAX_DOWN","TAX_UP","DIPLOMACY","DRILL","RELIEF","REPAY","REFORM","REST")
 private val labels = mapOf("WAR_WITHDRAW" to "无条件撤军","CONSORT_DECLINE" to "结束宫廷相看","CANAL" to "修筑运河","ORG_RELEASE" to "交卸组织职务","ORG_FOUND" to "筹建组织", "ORG_ASSIGN" to "任用组织成员", "INTEL_SCOUT" to "派遣间谍", "INTEL_COUNTER" to "反谍排查", "ASSASSINATE" to "秘密刺杀", "DECLARE_WAR" to "宣战动员", "WAR_BATTLE" to "前线交战", "WAR_PEACE" to "谈判停战","FAMILY_CONTACT" to "登记家族交流身份","PALACE_BUILD" to "营建宫廷建筑", "CONSORT_AUDIENCE" to "安排选妃面见", "CONSORT_ACCEPT" to "登记自愿伴侣", "ARMY_ASSIGN" to "任命将领", "ARMY_TRAIN" to "军队训练", "ARMY_MOVE" to "调防军队", "ARMY_RECRUIT" to "募集兵员","EXAM" to "举办科举", "RECRUIT" to "公开招贤", "APPOINT" to "任命地方官","URBAN_BUILD" to "升级城内建筑", "URBAN_AUDIT" to "城邑财政审计", "URBAN_RELIEF" to "城邑赈济","CONVERSE" to "人物交谈", "ACADEMY_UPGRADE" to "扩建国家学院", "APPOINT_TUTOR" to "聘任讲席", "STUDY_HOME" to "入国家学院", "STUDY_ABROAD" to "派往海外学习", "RECALL_STUDENT" to "召回学员", "WORKSHOP" to "营造官营作坊", "CITY_FOUND" to "营建新邑", "CITY_RESTORE" to "修复旧城", "CITY_RENAME" to "城邑更名", "BORDER_TREATY" to "议定边地移交", "ERA" to "更改纪元", "RULER_NAME" to "君主更名", "CHILD_NAME_PLAN" to "预设子女名字", "NAME_CHILD" to "子女更名", "FAMILY_PLAN" to "设置备孕", "TRY_HEIR" to "自愿备孕", "FAMILY_TIME" to "陪伴家人", "STRATEGY" to "调整战略", "FACTORY" to "筹建工厂", "FARM" to "兴修水利", "RESEARCH" to "资助研究", "ROAD" to "修筑道路", "TAX_DOWN" to "减税惠民", "TAX_UP" to "提高税率", "DIPLOMACY" to "派遣使节", "DRILL" to "整训军队", "RELIEF" to "赈济百姓", "REPAY" to "偿还债务", "REFORM" to "考核改革", "REST" to "休养生息", "DEPART" to "出发巡游", "TRAVEL" to "继续行程", "VISIT" to "探访场景", "TALK" to "询问人物", "INVESTIGATE" to "暗访取证", "AUDIT" to "核验账册", "REVEAL" to "公开身份", "TRIAL" to "依法审理", "DISMISS" to "行政撤换", "CLOSE" to "撤销指控", "REMOTE" to "委派监察官", "RETURN" to "返回朝廷")
 fun label(c: Command)=if(c.type=="CANCEL_TASK") "撤销事务" else labels[c.type] ?: c.type
 fun cost(c: Command): Long = when(c.type) { "CANAL"->CanalEngine.cost(c); "ORG_FOUND"->90;"ORG_ASSIGN"->10;"INTEL_SCOUT"->35;"INTEL_COUNTER"->25;"ASSASSINATE"->85;"DECLARE_WAR"->120;"WAR_BATTLE"->35;"WAR_PEACE"->40; in CrownEngine.types->CrownEngine.cost(c); "EXAM"->60;"RECRUIT"->20;"APPOINT"->25; in MunicipalEngine.types->MunicipalEngine.cost(c); "CONVERSE"->0; "APPOINT_TUTOR"->10; "STUDY_HOME"->50; "STUDY_ABROAD"->140; "ACADEMY_UPGRADE"->120; "WORKSHOP" -> 100; "CITY_FOUND" -> 120; "CITY_RESTORE" -> 80; "CITY_RENAME" -> 5; "BORDER_TREATY" -> 180; "TRY_HEIR" -> 20; "FAMILY_TIME" -> 10; "FACTORY" -> 140; "FARM" -> 75; "RESEARCH" -> 65; "ROAD" -> 100; "DIPLOMACY" -> 35; "DRILL" -> 60; "RELIEF" -> 50; "REPAY" -> 80; "REFORM" -> 95; "DEPART" -> if(c.mode=="公开巡幸") 95 else if(c.mode=="秘密调查") 55 else 35; "TRAVEL" -> 8; "TALK" -> 0; "VISIT" -> 3; "INVESTIGATE" -> 12; "AUDIT" -> 20; "REVEAL" -> 10; "TRIAL" -> 25; "DISMISS" -> 20; "REMOTE" -> 65; "RETURN" -> 20; else -> 0 }
 fun duration(c: Command)=when(c.type) { "URBAN_BUILD" -> 0; "URBAN_AUDIT","URBAN_RELIEF"->5; "CONVERSE" -> 0; "APPOINT_TUTOR" -> 7; "ACADEMY_UPGRADE","STUDY_HOME","STUDY_ABROAD","RECALL_STUDENT" -> 0; "ERA","RULER_NAME","CHILD_NAME_PLAN","NAME_CHILD","FAMILY_PLAN","STRATEGY" -> 0; "DEPART" -> 1; "TRAVEL" -> 7; "TALK" -> 0; "VISIT" -> 2; "INVESTIGATE","AUDIT" -> 5; "REVEAL","TRIAL","DISMISS","CLOSE" -> 1; "RETURN" -> 7; else -> 90 }
 fun suggestions(w: World): List<Command> {
  val j=w.journey
  if(j!=null) return if(j.remainingTravel>0) listOf(Command("TRAVEL"),Command("RETURN"),Command("REST")) else listOf(Command("INVESTIGATE"),Command("AUDIT"),Command("RETURN"))
  return when(w.strategy) { "工业强国","商工兴国" -> listOf(Command(EraRules.factory(w)),Command("RESEARCH"),Command("REST")); "富民治国" -> listOf(Command("FARM"),Command("RELIEF"),Command("REST")); else -> listOf(Command("ROAD"),Command("DIPLOMACY"),Command("REST")) }
 }
 fun validate(w: World, c: Command): String? {
  if(c.text.length>600)return "行动原文过长。"
  if(w.ended) return "本纪年已结束，请新建或读取存档。"
  if(w.day>900000) return "已达到本版纪年上限，请保留档案。"
  if(c.type=="FACTORY" && w.setting==EraRules.ANCIENT)return "纯古代模式使用手工作坊，不可建设工业工厂。"
  if(c.type=="WORKSHOP" && w.setting!=EraRules.ANCIENT)return "工业时代请使用工厂项目。"
  if(c.type !in labels) return "未知的行动类型。"
  if(c.region !in 0..3) return "目标地区不存在。"
  if(c.type in FamilyEngine.types) FamilyEngine.validate(w,c)?.let {return it}
  if(c.type in MapActions.types) MapActions.validate(w,c)?.let {return it}
  if(c.type in MunicipalEngine.types)MunicipalEngine.validate(w,c)?.let {return it}
  if(c.type in EducationEngine.types)EducationEngine.validate(w,c)?.let {return it}
  if(c.type in TalentEngine.types)TalentEngine.validate(w,c)?.let {return it}
  if(c.type in CrownEngine.types)CrownEngine.validate(w,c)?.let {return it}
  if(c.type in IntelligenceEngine.types)IntelligenceEngine.validate(w,c)?.let {return it}
  if(c.type in WarfareEngine.types)WarfareEngine.validate(w,c)?.let {return it}
  if(c.type=="CANAL")CanalEngine.validate(w,c)?.let {return it}
  if(w.nation.treasury<cost(c)) return "国库不足：需要 ${cost(c)} 万两。可休养、调整税率或削减研究。"
  if(c.type in listOf("FACTORY","WORKSHOP","FARM","ROAD") && w.projects.any { it.kind==c.type && it.region==c.region }) return "该地区已有同类在建项目。"
  if(c.type=="STRATEGY" && c.target !in listOf("工业强国","商工兴国","富民治国","综合发展")) return "发展方向无效。"
  if(c.type in listOf("TRIAL","DISMISS")&&w.people.none {it.id==w.caseFile.accused&&it.alive})return "涉案人物已故，保留案卷，不能再任免或提审本人。"
  if(c.type=="CLOSE" && w.caseFile.evidence.isEmpty()) return "尚无核查材料，无需结案。"
  if(c.type=="REPAY" && w.nation.debt==0L) return "当前没有债务。"
  if(c.type=="TAX_UP" && w.nation.tax>=40) return "税率已达 40% 上限。"
  if(c.type=="TAX_DOWN" && w.nation.tax<=5) return "税率已达 5% 下限。"
  if(c.type=="DEPART") {
   if(w.journey!=null) return "已有进行中的行程。"
   if(c.mode !in listOf("微服私访","公开巡幸","秘密调查") || c.disguise !in listOf("商人","书生","百姓")) return "出行方式或身份无效。"
   if(c.days !in 10..90) return "计划停留须为 10–90 日。"
   if(c.companion==c.regent || listOf(c.companion,c.regent).any { id -> w.people.none { it.id==id && it.alive && it.id<3 && !EducationEngine.busy(w,it.id) } }) return "随从与摄政须为不同的在世朝臣。"
  }
  if(c.type in listOf("TRAVEL","VISIT","TALK","INVESTIGATE","AUDIT","REVEAL","RETURN")) {
   if(w.journey==null) return "请先从世界页面出发。"
   if(c.type !in listOf("TRAVEL","RETURN") && w.journey.remainingTravel>0) return "仍在途中，尚未抵达。"
   if(c.type !in listOf("RETURN","TRAVEL","REVEAL") && w.journey.spentDays>=w.journey.budgetDays) return "停留期限已到，请返回朝廷。"
  }
  if(c.type=="VISIT" && c.target !in listOf("集市","客栈","官府","农田",if(w.setting==EraRules.ANCIENT)"作坊" else "工厂","军营")) return "场景不存在。"
  if(c.type=="TALK" && (c.target.toIntOrNull() !in 6..9 || w.people.none { it.id==c.target.toIntOrNull() && it.alive })) return "此地没有这位可交谈人物。"
  if(c.type in listOf("INVESTIGATE","AUDIT","TALK") && w.journey?.region!=0) return "本版案件与地方人物交互设于澄江。其他地区可巡视。"
  if(c.type=="REVEAL" && w.journey?.revealed==true) return "身份已经公开。"
  if(c.type=="AUDIT" && w.journey?.revealed!=true) return "正式核验官府账册需要出示印信，或委派监察官。"
  if(c.type=="REMOTE" && (EducationEngine.busy(w,2) || w.caseFile.resolved || w.caseFile.remoteDays>0 || w.people.none { it.id==2 && it.alive })) return "案件已处理、调查进行中或监察使已不在世。"
  if(c.type in listOf("TRIAL","DISMISS","CLOSE")) {
   if(w.caseFile.resolved) return "案件已结，不可重复处分。"
   if(c.type=="TRIAL" && (w.caseFile.evidence.filter { it.authentic }.sumOf { it.strength }<70 || w.caseFile.evidence.none { it.id=="ledger" })) return "证据未充分核验；须有账册与至少 70 点真实证据。"
   if(c.type=="DISMISS" && w.caseFile.status=="未立案") return "尚无调查依据。"
  }
  return null
 }
 fun apply(w: World, c: Command, expectedRevision: Long, advanceDays:Boolean=true): Outcome {
  require(expectedRevision==w.revision) { "回合已改变，未重复执行。" }
  require(validate(w,c)==null) { validate(w,c) ?: "无效行动" }
  val elapsed=if(advanceDays) duration(c) else 0
  val ledger=mutableListOf<String>(); val events=mutableListOf<String>()
  var x=w.copy(nation=w.nation.copy(treasury=w.nation.treasury-cost(c)))
  ledger += "${label(c)}支出：-${cost(c)} 万两"
  fun nation(f:(Nation)->Nation) { x=x.copy(nation=f(x.nation)) }
  fun evidence(e: Evidence) {
   if(x.caseFile.evidence.none { it.id==e.id }) x=x.copy(caseFile=x.caseFile.copy(evidence=x.caseFile.evidence+e, status="存在嫌疑"))
  }
  when(c.type) {
   "STRATEGY" -> { x=x.copy(strategy=c.target); events+="国家发展方向调整为${c.target}；既有建设与历史继续保留。" }
   "FACTORY","WORKSHOP","FARM","ROAD" -> { x=x.copy(projects=x.projects+Project(c.type,c.region,if(c.type in listOf("FACTORY","WORKSHOP")) 3 else 2)); events+="${x.regions[c.region].name}项目立项，完工前不增加产能。" }
   "RESEARCH" -> nation { it.copy(research=min(30,it.research+5),technology=it.technology+1) }
   "TAX_UP" -> nation { it.copy(tax=it.tax+3,morale=it.morale-4,commerce=it.commerce-2) }
   "TAX_DOWN" -> nation { it.copy(tax=it.tax-3,morale=it.morale+4,commerce=it.commerce+1) }
   "DIPLOMACY" -> x=x.copy(neighbors=x.neighbors.map { it.copy(relation=min(100,it.relation+5)) })
   "DRILL" -> nation { it.copy(military=it.military+4,stability=it.stability+1) }
   "RELIEF" -> nation { it.copy(morale=it.morale+6,stability=it.stability+3) }
   "REPAY" -> { val paid=min(80L,x.nation.debt); nation { it.copy(debt=it.debt-paid,treasury=it.treasury+80-paid) }; ledger+="偿还本金 $paid 万两" }
   "REFORM" -> { nation { it.copy(administration=it.administration+4,corruption=it.corruption-3) }; x=x.copy(factions=x.factions.mapIndexed { i,f -> f.copy(support=(f.support+if(i==0) 4 else -3).coerceIn(0,100)) },regions=x.regions.map { it.copy(reform=it.reform+1,corruption=max(0,it.corruption-2)) }) }
   "REST" -> nation { it.copy(morale=it.morale+1,research=max(0,it.research-2)) }
   "DEPART" -> { val public=c.mode=="公开巡幸"; x=x.copy(journey=Journey(c.region,c.mode,c.disguise,c.companion,c.regent,c.objective,c.days,7+c.region*3,exposure=if(public) 100 else if(c.companion==1) 35 else 8,revealed=public)); events+="由${x.people[c.regent].name}代理朝政；抵达后方可进入场景。" }
   "TRAVEL" -> x=x.copy(journey=x.journey!!.copy(remainingTravel=max(0,x.journey!!.remainingTravel-7),location=if(x.journey!!.remainingTravel<=7) "客栈" else "驿道"))
   "VISIT" -> { x=x.copy(journey=x.journey!!.copy(location=c.target)); events+=when(c.target) { "集市" -> "商贩抱怨关卡另收费用，传闻尚待核查。"; "官府" -> if(x.journey!!.revealed) "官府验过印信，开放卷宗核查。" else "门吏将你视为${x.journey!!.disguise}，要求按章递交文书。"; "军营" -> "士兵谈及粮饷，请核对名册再下结论。"; "农田" -> "田间沟渠的损坏可被直接观察；农民口述仅作线索。"; "工厂","作坊" -> "工匠展示机具；扩产仍需项目资金与工期。"; else -> "旅人各执一词，流言并非证据。" } }
   "TALK" -> { val id=c.target.toInt(); val p=x.people[id]; val key="线索:${x.caseFile.status}:${x.journey!!.disguise}";val known=x.socialTopics[id].orEmpty();val repeated=key in known; val suspicion=if(x.journey!!.revealed) 0 else 7; val trust=if(repeated)0 else if(x.journey!!.disguise=="商人" && id==6)12 else 6
    x=x.copy(socialTopics=x.socialTopics+(id to (known+key).distinct().takeLast(64)),people=x.people.map { if(it.id==id) it.copy(trust=(it.trust+trust).coerceAtMost(100),suspicion=(it.suspicion+suspicion).coerceAtMost(100),memories=(it.memories+"第${x.day}日与${if(x.journey!!.revealed) "君主" else x.journey!!.disguise}交谈").takeLast(8)) else it });
    events+=if(id==9) "${p.name}声称知府一向清廉；他与地方特许经营存在利益联系。" else "${p.name}愿意提供线索，但仍需独立查证。"
    if(id==7 && x.people[id].trust>=47) evidence(Evidence("witness","农户具名证言",25,x.caseFile.guilty))
   }
   "INVESTIGATE" -> { val j=x.journey!!; val bump=if(j.mode=="秘密调查") 6 else if(j.revealed) 0 else 14
    x=x.copy(journey=j.copy(exposure=min(100,j.exposure+bump)))
    when { x.caseFile.evidence.none { it.id=="receipt" } -> evidence(Evidence("receipt","集市收费凭据",30,x.caseFile.guilty)); x.caseFile.evidence.none { it.id=="witness" } -> evidence(Evidence("witness","农户具名证言",25,x.caseFile.guilty)); else -> events+="已核查现有线索，未获得新的独立证据。" }
    events+=if(x.caseFile.guilty) "费用与公开章程不符；仍需与官府账册交叉核验。" else "收费传言与原始记录有矛盾；不能仅凭传言定罪。"
   }
   "AUDIT" -> { evidence(Evidence("ledger","经核验的原始账册",50,true)); events+=if(x.caseFile.guilty) "账册与收费凭据存在差额，财政损失被登记。" else "核验账册后发现手续完整，部分证言存在伪造嫌疑。" }
   "REVEAL" -> { x=x.copy(journey=x.journey!!.copy(revealed=true,exposure=100)); nation { it.copy(stability=it.stability-1) }; events+="随行朝臣与驿站印信完成双重验认，地方官才确认君主身份；公开行迹增加戒备。" }
   "REMOTE" -> { x=x.copy(caseFile=x.caseFile.copy(remoteDays=30,status="监察使调查中")); events+="监察使携带授权前往澄江，30 日后呈交核验材料。" }
   "TRIAL" -> {
    if(x.caseFile.guilty) {
     x=x.copy(caseFile=x.caseFile.copy(resolved=true,status="证据充分",resolution="依法撤职追缴"),people=x.people.map { if(it.id==x.caseFile.accused) it.copy(role="免职候审",loyalty=max(0,it.loyalty-20),hostility=it.hostility+25,memories=(it.memories+"澄江案依法免职").takeLast(8)) else it },regions=x.regions.map { if(it.id==0) it.copy(official=-1,corruption=max(0,it.corruption-18),morale=min(100,it.morale+8),reform=it.reform+1) else it })
     nation { it.copy(treasury=it.treasury+45,morale=it.morale+5,administration=it.administration-2) }; ledger+="依法追缴：+45 万两（一次性）"; events+="知府被免职，地方职位暂缺；下季度任命新官。"
    } else error("无充分真实证据，不得定罪")
   }
   "DISMISS" -> { x=x.copy(caseFile=x.caseFile.copy(resolved=true,status="行政结案",resolution="未定罪撤换"),people=x.people.map { if(it.id==3) it.copy(role="待任官员",hostility=it.hostility+30) else it },regions=x.regions.map { if(it.id==0) it.copy(official=-1,corruption=max(0,it.corruption-5),reform=it.reform+1) else it }); nation { it.copy(administration=it.administration-5,stability=it.stability-3) }; events+="撤换属于人事措施，未认定犯罪，未追缴财产；派系对程序提出异议。" }
   "CLOSE" -> { x=x.copy(caseFile=x.caseFile.copy(resolved=true,status=if(x.caseFile.guilty) "证据不足" else "发现伪证",resolution="撤销指控")); nation { it.copy(stability=it.stability+2,morale=it.morale+if(x.caseFile.guilty) -3 else 3) }; events+="保留核查记录，不作刑事处罚。" }
   "RETURN" -> { x=x.copy(journey=null); events+="结束行程返回朝廷，地方决定与人物记忆继续保留。" }
  }
  if(c.type in FamilyEngine.types) x=FamilyEngine.execute(x,c,events)
  if(c.type in MapActions.types) x=MapActions.execute(x,c,events)
  if(c.type in MunicipalEngine.types)x=MunicipalEngine.execute(x,c,events)
  if(c.type in EducationEngine.types)x=EducationEngine.execute(x,c,events)
  if(c.type in TalentEngine.types)x=TalentEngine.execute(x,c,events)
  if(c.type in CrownEngine.types)x=CrownEngine.execute(x,c,events)
  if(c.type in IntelligenceEngine.types)x=IntelligenceEngine.execute(x,c,events)
  if(c.type in WarfareEngine.types)x=WarfareEngine.execute(x,c,events)
  if(c.type=="CANAL")x=CanalEngine.execute(x,c,events)
  if(x.journey!=null) {
   var j=x.journey!!.copy(spentDays=x.journey!!.spentDays+elapsed)
   if(j.exposure>=85 && !j.revealed) { j=j.copy(revealed=true); events+="调查动静与朝臣随行引起驿站核查，身份经印信验明确认。" }
   x=x.copy(journey=j)
  }
  val oldQuarter=x.day/90; val newDay=x.day+elapsed
  if(x.caseFile.remoteDays>0) {
   val left=max(0,x.caseFile.remoteDays-elapsed); x=x.copy(caseFile=x.caseFile.copy(remoteDays=left))
   if(left==0) { evidence(Evidence("ledger","监察使核验账册",50,true)); if(x.caseFile.guilty) evidence(Evidence("receipt","监察使复核收费凭据",30,true)); else evidence(Evidence("forgery","证人笔迹不符的伪证",0,false)); events+="监察使归报，材料已存入案卷；处分仍须另行决定。" }
  }
  repeat(elapsed){x=EducationEngine.advance(x.copy(day=x.day+1),events);x=MunicipalEngine.advance(x,events);x=CrownEngine.advance(x,events);x=WarfareEngine.advance(x,ledger)}
  x=FamilyEngine.advance(x.copy(day=newDay),elapsed,events)
  repeat(newDay/90-oldQuarter) {
   x=EconomyEngine.quarter(x,ledger,events);x=MunicipalEngine.quarter(x,ledger)
   x=CharacterEngine.quarter(x,events)
   x=TalentEngine.quarter(x,ledger);x=CrownEngine.quarter(x,ledger);x=IntelligenceEngine.quarter(x,ledger)
   x=EventEngine.quarter(x,ledger,events)
  }
  val clamped=bounded(FamilyEngine.syncPeople(x))
  if(clamped!=x) ledger+="部分属性触及规定边界，超出部分未计入。"
  x=clamped
  ledger+=delta(w.nation,x.nation)
  val text=buildString { append("${x.name} · ${x.date}\n"); append("${w.ruler}下令${label(c)}。 "); append(events.joinToString(" ")); if(events.isEmpty()) append("各司依章执行，成效记入本期账簿。") }
  val entry=Entry(w.revision+1,x.turn,x.day,EraRules.label(c,w.setting),EraRules.render(text,w.setting),ledger.map {EraRules.render(it,w.setting)},events.map {EraRules.render(it,w.setting)},w.seed,commands=listOf(c))
  x=x.copy(revision=w.revision+1,recent=(w.recent+entry).takeLast(12),summary=if(x.turn%8==0) "截至${x.date}，工业${x.nation.industry}，民心${x.nation.morale}；澄江案${x.caseFile.status}。" else x.summary)
  return Outcome(x,entry)
 }
 private fun delta(a:Nation,b:Nation)=listOf("结余变化 ${b.treasury-a.treasury} 万两；债务变化 ${b.debt-a.debt} 万两", "工业 ${a.industry}→${b.industry}；农业 ${a.agriculture}→${b.agriculture}；科技 ${a.technology}→${b.technology}","民心 ${a.morale}→${b.morale}；稳定 ${a.stability}→${b.stability}；腐败 ${a.corruption}→${b.corruption}")
 fun bounded(w:World):World { val n=w.nation; fun p(v:Int)=v.coerceIn(0,100)
  return w.copy(nation=n.copy(treasury=n.treasury.coerceIn(0,1000000),debt=n.debt.coerceIn(0,1000000),population=n.population.coerceIn(100000,100000000),tax=n.tax.coerceIn(5,40),morale=p(n.morale),stability=p(n.stability),administration=p(n.administration),corruption=p(n.corruption),agriculture=p(n.agriculture),commerce=p(n.commerce),industry=p(n.industry),technology=p(n.technology),infrastructure=p(n.infrastructure),military=p(n.military),research=n.research.coerceIn(0,30)),people=w.people.map { it.copy(ability=p(it.ability),loyalty=p(it.loyalty),hostility=p(it.hostility),trust=p(it.trust),fear=p(it.fear),suspicion=p(it.suspicion)) })
 }
 fun verify(w:World) {
  require(w.familyPeople.size<=100&&w.familyPeople.values.distinct().size==w.familyPeople.size&&w.familyPeople.all {(family,person)->w.household.members.any {it.id==family}&&w.people.any {it.id==person}})
  CanalEngine.verify(w)
  WarfareEngine.verify(w);IntelligenceEngine.verify(w)
  CrownEngine.verify(w)
  TalentEngine.verify(w)
  MunicipalEngine.verify(w)
  w.cartography?.let(MapValidation::verify)
  require(w.academy.level in 1..5&&w.people.any {it.id==w.academy.tutor})
  require(w.studies.size<=5&&w.studies.map {it.person}.distinct().size==w.studies.size)
  require(w.studies.all {s->w.people.any {it.id==s.person&&it.alive}&&s.destination in -1..2&&s.remaining in 0..s.duration&&s.duration in listOf(90,180)&&s.travel in 0..28&&s.returnDays in 7..28&&s.score in 0..1800&&s.phase in listOf("赴学","研习","返程")})
  require(w.socialTopics.size<=1000&&w.socialTopics.all {(id,keys)->w.people.any {it.id==id}&&keys.size<=64&&keys.distinct().size==keys.size&&keys.all {it.length<=100}})
  require(w.socialDays.size<=1000&&w.socialDays.all {(id,day)->w.people.any {it.id==id}&&day in 0..w.day})
  require(w.neighbors.all {it.academyLevel in 1..5&&it.teacherLevel in 1..5})
  require(w.setting in listOf("古代王朝","工业初兴"))
  require(w.schema==1 && w.revision>=0 && w.day in 0..900090 && w.name.length in 1..24 && w.ruler.length in 1..24)
  require(w==bounded(w)); require(w.regions.size==4 && w.regions.map { it.id }==listOf(0,1,2,3))
  require(w.people.size in 10..1000 && w.people.map { it.id }.distinct().size==w.people.size)
  require(w.people.all { it.ageQuarters in 0..20000 && it.memories.size<=8 })
  require(w.regions.all { r -> r.official==-1 || w.people.any { it.id==r.official && it.alive } })
  require(w.pending.size<=3 && w.pending.map {it.id}.distinct().size==w.pending.size && w.pending.all {it.remainingDays in 1..90 && it.escrow in 0L..1000L})
  require(w.scale in listOf("超级大国","中等国家","小国") && w.atmosphere in listOf("和平建设","均势博弈","烽火边疆"))
  require(FamilyEngine.validName(w.era) && w.eraDay in 0..w.day)
  require(w.household.members.size<=100 && w.household.members.map {it.id}.distinct().size==w.household.members.size)
  require(w.household.members.all {FamilyEngine.validName(it.name) && it.bornDay<=w.day && it.relationship in 0..100 && it.health in 0..100 && (it.diedDay==null || (!it.alive && it.diedDay in it.bornDay..w.day))})
  require(w.household.pregnancy==null || (w.household.pregnancy.remainingDays in 1..270 && w.household.members.any {it.id==w.household.pregnancy.parent && it.alive && (w.day-it.bornDay)/360>=18}))
  require(w.recent.size<=12 && w.projects.size<=12 && w.neighbors.size==3 && w.factions.size==3)
  require(w.caseFile.evidence.size<=8 && w.caseFile.evidence.map { it.id }.distinct().size==w.caseFile.evidence.size)
  require(w.caseFile.evidence.all { it.strength in 0..50 && it.id in listOf("ledger","receipt","witness","forgery") })
  require(w.journey==null || (w.journey.region in 0..3 && w.journey.exposure in 0..100))
 }
}

object EconomyEngine {
 fun quarter(w:World,ledger:MutableList<String>,events:MutableList<String>,advanceProjects:Boolean=true):World {
  var n=w.nation
  val base=100L+n.agriculture*2+n.commerce*2+n.industry*3
  val income=base*n.tax/100*(100-n.corruption/2)/100
  val familyCost=w.household.members.count {it.alive && it.role=="子女"}*3L + if(w.household.pregnancy!=null) 6L else 0L
  val borderCost=if(w.atmosphere=="烽火边疆" && w.neighbors.any {it.relation<40}) 15L else 0L
  val mapCost=(w.cartography?.territories?.count {it.id.startsWith("frontier-")&&it.polityId=="nation"}?:0)*5L
  if(mapCost>0)ledger+="边地驻防 -$mapCost 万两（计入季度经常支出）"
  val absent=w.studies.count {s->w.regions.any {it.official==s.person}||s.person in 0..2}
  if(absent>0){n=n.copy(administration=n.administration-absent);ledger+="官员赴学离岗，行政效率 -$absent"}
  val tuitionMaintenance=w.academy.level*2L
  ledger+="国家学院维护 -$tuitionMaintenance 万两（计入季度开支）"
  val crownCost=CrownEngine.maintenance(w)+IntelligenceEngine.upkeep(w)+w.canals.size*3L
  if(crownCost>0)ledger+="宫廷新增维护与扩编军费 -$crownCost 万两（计入季度开支）"
  val expense=crownCost+tuitionMaintenance+mapCost+35L+n.military/2+n.infrastructure/4+n.research+n.debt*2/100 + familyCost + borderCost + if(w.difficulty=="艰难") 12 else 0
  var cash=n.treasury+income-expense; var debt=n.debt
  if(cash<0) { val need=-cash; val borrowed=min(need,max(0,2000-debt)); debt+=borrowed; cash+=borrowed; ledger+="赤字借款 $borrowed 万两"; if(cash<0) { events+="信用枯竭，削减预算并发生财政危机。"; n=n.copy(stability=n.stability-7,morale=n.morale-5,military=max(0,n.military-4),research=0); cash=0 } }
  if(borderCost>0) ledger+="边境战备 -$borderCost 万两（计入季度经常支出）"
  ledger+="宗室照护与教育 -$familyCost 万两（计入季度经常支出）"
  ledger+="季度税收 +$income 万两；经常支出 -$expense 万两（含利息 ${n.debt*2/100}）"
  val food=n.agriculture*100000L
  val growth=(n.population*(if(food>n.population) 3 else -2)/1000)
  n=n.copy(treasury=cash,debt=debt,income=income*4,expense=expense*4,population=n.population+growth,
   technology=n.technology+if(n.research>=5 && n.technology<90) 1 else 0,
   industry=n.industry-if(w.turn%12==0) 1 else 0,
   infrastructure=n.infrastructure-if(w.turn%8==0) 1 else 0,
   morale=n.morale+(if(food<n.population) -2 else 0)+(if(n.tax>=32) -2 else 0),stability=n.stability+if(n.morale<25) -2 else if(n.morale>65 && n.stability<75) 1 else 0)
  ledger+="人口因粮食供需 ${if(growth>=0) "+" else ""}$growth 人；研发预算 ${n.research} 万两/季"
  val completed=if(advanceProjects) w.projects.filter { it.quarters<=1 } else emptyList()
  var mapWorld=w
  var regions=w.regions
  completed.forEach { p ->
   if(p.kind=="CANAL"){mapWorld=CanalEngine.complete(mapWorld.copy(nation=n,regions=regions),p,events);n=mapWorld.nation;regions=mapWorld.regions;return@forEach}
   if(p.kind=="ACADEMY_UPGRADE")mapWorld=EducationEngine.completeAcademy(mapWorld,events)
   if(p.kind in listOf("CITY_FOUND","CITY_RESTORE"))mapWorld=MapActions.complete(mapWorld,p,events)
   when(p.kind) { "FACTORY","WORKSHOP" -> n=n.copy(industry=n.industry+6,commerce=n.commerce+2); "FARM" -> n=n.copy(agriculture=n.agriculture+7); "ROAD" -> n=n.copy(infrastructure=n.infrastructure+7,commerce=n.commerce+2) }
   regions=regions.map { if(it.id==p.region) it.copy(industry=min(100,it.industry+if(p.kind in listOf("FACTORY","WORKSHOP")) 8 else 0),agriculture=min(100,it.agriculture+if(p.kind=="FARM") 8 else 0)) else it }
   events+="${w.regions[p.region].name}${Rules.label(Command(p.kind))}完工，开始产生效果。"
  }
  regions=regions.map { it.copy(funds=(it.funds+income/8-10).coerceIn(0,100000),morale=(it.morale+if(it.reform>0 && it.morale<75) 1 else 0).coerceIn(0,100),population=n.population/4+if(it.id==3)n.population%4 else 0) }
  return mapWorld.copy(nation=n,regions=regions,projects=if(advanceProjects) w.projects.filter { it.quarters>1 }.map { it.copy(quarters=it.quarters-1,remainingDays=(it.remainingDays-90).coerceAtLeast(1)) } else w.projects)
 }
}
object CharacterEngine {
 fun quarter(w:World,events:MutableList<String>):World {
  var people=w.people.map { p ->
   if(!p.alive||p.id in w.familyPeople.values) p else {
    val age=p.ageQuarters+1
    val dies=age>=320 && Math.floorMod(w.seed+p.id*37L+w.turn*17L,19L)==0L || age>=400
    if(dies) { events+="${p.name}去世，原职不再保留。"; p.copy(ageQuarters=age,alive=false,role="已故",memories=(p.memories+"寿终").takeLast(8)) }
    else p.copy(ageQuarters=age,ability=min(100,p.ability+if(w.turn%16==0 && age<220) 1 else 0))
   }
  }
  val regions=w.regions.map { r ->
   if(r.official==-1 || people.none { it.id==r.official && it.alive }) {
    val id=(people.maxOf { it.id }+1)
    // Reuse the finite pool of successors, while preserving old identities in history.
    if(people.size<1000) { people=people+Person(id,"${r.name}新任官${id}","${r.name}地方官",0,120,60,65,"依法施政","延续地方改革"); events+="${r.name}任命新官，延续已有改革。"; r.copy(official=id) } else r.copy(official=-1)
   } else r
  }
  val j=w.journey?.let { if(people.none { p->p.id==it.companion && p.alive } || people.none { p->p.id==it.regent && p.alive }) { events+="随行或摄政人事变化，行程提前结束。"; null } else it }
  return w.copy(people=people,regions=regions,journey=j,studies=w.studies.filter {s->people.any {it.id==s.person&&it.alive}})
 }
}
