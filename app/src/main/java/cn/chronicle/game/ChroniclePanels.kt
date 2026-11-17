package cn.chronicle.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.chronicle.core.*

@Composable internal fun ObjectiveCard(w:World,ask:(Command)->Unit){
 if(!w.chronicle.enabled){Panel("新篇章可用","原存档、人物和疆域保留"){Text("可开启扩充案簿、季末奏报、护卫与传承。开关引导不会锁住旧存档。");ActionButton(Command("CONTENT_ENABLE",mode="自由",level=1),ask)};return}
 Panel("第${w.chronicle.chapter}章 · ${ChronicleEngine.stages[w.chronicle.stage]}","${w.chronicle.route} · ${if(w.chronicle.guided)"循序引导"else"自由执政"}"){
  Text(ChronicleEngine.objective(w),style=MaterialTheme.typography.bodyMedium)
  if(ChronicleEngine.needsPause(w))Text("重大奏报已暂停时间，请在目标与奏报中阅毕。",color=MaterialTheme.colorScheme.primary)
  if(w.chronicle.outcome.isNotBlank())Text("路线里程碑：${w.chronicle.outcome}")
 }
}
@Composable internal fun ChroniclePanel(w:World,ask:(Command)->Unit){
 if(!w.chronicle.enabled){ObjectiveCard(w,ask);return}
 var section by rememberSaveable {mutableStateOf("奏报")}
 Choices(listOf("奏报","路线","起居注"),section,{section=it})
 when(section){
 "奏报"->Panel("御前奏报","重大事件暂停，可自由选择是否启用引导"){
  val b=w.chronicle.bulletins.lastOrNull()
  if(b==null)Text("首份季末奏报将在第90日呈递。连续时间支持暂停与倍速。")else{
   Text("第${b.quarter}季 · ${b.day}日\n本季收入${b.income} / 应支${b.expense}万两\n结余${b.cash} / 债务${b.debt}万两")
   Text("优先风险："+b.risks.ifEmpty {listOf("暂无触及预警阈值的事项")}.joinToString("；"))
   b.choices.forEach {Text("· $it")}
   Text("建议不自动执行：粮政、借款与补饷请到朝政核对。",style=MaterialTheme.typography.bodySmall)
  }
  if(w.sovereign.warningDue>=w.day)Text("安保警讯将在${w.sovereign.warningDue-w.day}日后到期；可到世界→君主核查与增援。")
  if(ChronicleEngine.needsPause(w))ActionButton(Command("ACK_REPORT"),ask)
  ActionButton(Command("GUIDANCE",mode=if(w.chronicle.guided)"关闭"else"开启"),ask)
  if(w.chronicle.stage==4)ActionButton(Command("CHAPTER_CLOSE"),ask)
 }
 "路线"->Panel("长期路线","改变路线不重置世界状态"){
  Choices(ChronicleEngine.routes,w.chronicle.route,{ask(Command("CHAPTER_ROUTE",mode=it))})
  Text(when(w.chronicle.route){"吏治清明"->"至少结案3件，中央腐败≤35，且没有已登记的错误强制处置。";"富民安邦"->"民心≥75、农业≥70、债务≤500万两，当前没有持续粮荒。";"经世兴业"->if(w.setting==EraRules.ANCIENT)"清丈田亩至少两轮，国家学院至少3级。"else"至少3座国营工厂、科技≥40并颁行劳工保障。";else->"三邻国关系均≥75，无战争与欠俸欠饷。"})
 }
 else->Panel("史官起居注","据已结算事实记功过，不补写证据"){
  if(w.chronicle.records.isEmpty())Text("完成本章目标后可请史官收章；未解线索会留给下一章。")
  w.chronicle.records.takeLast(4).reversed().forEach {r->Fold("第${r.number}章 · ${r.day}日"){r.facts.forEach {Text(it)};Text(r.assessment)}}
 }
 }
}
@Composable internal fun CaseDocketPanel(w:World,vm:GameViewModel,ask:(Command)->Unit){
 var filter by rememberSaveable {mutableStateOf("待核")};var selected by rememberSaveable {mutableStateOf("")};var page by rememberSaveable {mutableIntStateOf(0)}
 val file=w.caseBook.cases.find {it.id==selected}
 if(file==null){
  Choices(listOf("待核","已结"),filter,{filter=it;page=0})
  val cases=w.caseBook.cases.filter {it.resolved==(filter=="已结")}.reversed();val last=(cases.size-1).coerceAtLeast(0)/4
  LaunchedEffect(last){page=page.coerceIn(0,last)}
  Panel("地方陈情案簿","64个案由 · 每案真相固定 · 同时最多8件待核"){
   if(!w.caseBook.enabled){ActionButton(Command("CONTENT_ENABLE",mode="自由",level=1),ask)}else{
    if(cases.isEmpty())Text("暂无${filter}案卷。御状会随国情出现，也可公开征询；澄江教学案见世界→地区→案卷。")
    cases.drop(page*4).take(4).forEach {c->OutlinedButton(onClick={selected=c.id},modifier=Modifier.fillMaxWidth()) {Column {Text(if(c.grievancePerson>=0)"王命强制陈情"else CaseLibrary.get(c.blueprint).title);Text("${w.towns.first {it.id==c.town}.name} · ${c.venue} · ${if(c.resolved)c.verdict else if(c.accepted)"核查中"else"待受理"}",style=MaterialTheme.typography.labelSmall)}}}
    Row {TextButton(onClick={page--},enabled=page>0){Text("前页")};Text("${page+1}/${last+1}");TextButton(onClick={page++},enabled=page<last){Text("后页")}}
    ActionButton(Command("CASE_REQUEST"),ask)
   }
  }
 }else{
  val b=CaseLibrary.get(file.blueprint)
  TextButton(onClick={selected=""}){Text("返回案簿")}
  Panel(if(file.grievancePerson>=0)"王命强制陈情"else b.title,"${w.towns.first {it.id==file.town}.name} · ${file.venue}"){
   Text(InvestigationEngine.visible(w,file))
   Text("陈情人：${w.people.first {it.id==file.witness}.name}；初被指控者：${if(file.grievancePerson>=0)"王命安排的责任机关"else w.people.first {it.id==file.accused}.name}。",style=MaterialTheme.typography.bodySmall)
   TextButton(onClick={vm.openChat(file.witness)}){Text("与陈情人交谈")}
   if(!file.accepted&&!file.resolved){Choices(listOf("御前受理","暗访"),"",{ask(Command("CASE_OPEN",target=file.id,mode=it))})}
   if(file.resolved){Text("结案：${file.verdict}");if(file.followup.isNotBlank())Text(file.followup)}
  }
  if(file.accepted){
   var view by rememberSaveable(file.id){mutableStateOf("证据板")}
   Choices(listOf("证据板","核查","裁处"),view,{view=it})
   when(view){
    "证据板"->Panel("证据与矛盾","传闻不计分 · 待核材料不计分 · 受压笔录不计分"){
     if(file.exhibits.isEmpty())Text("仅有陈情，尚无登记材料。")
     file.exhibits.forEach {e->Surface(color=when {e.coerced->MaterialTheme.colorScheme.errorContainer;e.verified&&e.points==0->MaterialTheme.colorScheme.errorContainer;e.verified->MaterialTheme.colorScheme.secondaryContainer;else->MaterialTheme.colorScheme.surfaceVariant},shape=MaterialTheme.shapes.medium){Column(Modifier.fillMaxWidth().padding(10.dp)){Text("${e.kind} · ${e.title}");Text(e.detail,style=MaterialTheme.typography.bodySmall);Text(if(e.coerced)"受压形成，无合法证据分"else if(e.verified)if(e.points>0)"复核通过：${e.points}分"else"复核不符：0分"else"待独立复核：0分");if(!e.verified&&!file.resolved)TextButton(onClick={ask(Command("CASE_VERIFY",target=file.id,objective=e.id))}){Text("复核此项")}}}}
     Text("证据链：证言 → 账册 ↔ 实物 → ${if(file.contradictions>0)"关键矛盾已质证"else"关键矛盾待质证"}；当前${InvestigationEngine.score(file)}分。")
     if(file.revealedCulprit)Text("责任核查：${if(file.culprit>=0)w.people.first {it.id==file.culprit}.name else "尚无可依法追究的在册个人；核查行政责任与补救"}。")
    }
    "核查"->Panel("选择核查方式","按权限、地点与证人状态执行；每项先预留真实费用"){
     if(!file.resolved){
      ActionButton(Command("CASE_VISIT",target=file.id,mode=if(w.journey?.remainingTravel==0&&w.journey?.region==w.towns.first {it.id==file.town}.region)"亲访"else"委派"),ask)
      listOf("CASE_RECORD","CASE_PHYSICAL","CASE_PROTECT","CASE_CROSS").forEach {ActionButton(Command(it,target=file.id),ask)}
      Fold("强制取供与后果"){Text("施压会损害信任、民心与稳定，受压笔录不能补足合法证据。");ActionButton(Command("CASE_PRESSURE",target=file.id),ask)}
     }else Text("已结案，不能重复取证或刷取关系。")
    }
    else->Panel("拟定裁处","行政任免、刑事责任和制度补救分别登记"){
     if(!file.resolved){listOf("依法追究","行政撤换","制度改革","保留待查","撤销指控").forEach {mode->OutlinedButton(onClick={ask(Command("CASE_DECIDE",target=file.id,mode=mode))},modifier=Modifier.fillMaxWidth()){Text(mode)}}
      if(file.grievancePerson>=0)Button(onClick={ask(Command("CASE_DECIDE",target=file.id,mode="纠正王命"))}){Text("纠正安排与赔偿")}
      Fold("强制处置"){Text("不补足事实、不等于刑事定罪。即使事后查实，也保留程序与民怨后果。");OutlinedButton(onClick={ask(Command("CASE_DECIDE",target=file.id,mode="强制处置"))}){Text("预览强制处置")}}
     }else Text("处置与后续均保存；返回案簿查看其他陈情。")
    }
   }
  }
 }
}
@Composable internal fun GovernmentPanel(w:World,ask:(Command)->Unit){
 Panel("四季财政推演","固定现行税制与预算的估算；不预知随机事件、战争变化或新工程"){
  ChronicleEngine.forecast(w).forEach {f->Text("第${f.quarter}季：收入${f.income} / 支出${f.expense}；余${f.cash}、债${f.debt}"+if(f.shortage>0)"，缺口${f.shortage}"else"")}
 }
 Panel("常设诏令","遵守库存、每季交易限额、7日商路及最多3项并行限制"){
  Text("粮价指数${w.government.grainPrice}（基准100）；购粮2批/次，单季自动采购预算≤6万两，保留20万两现金缓冲。")
  ActionButton(Command("RESERVE_ORDER",mode=if(w.government.reserveOrder)"关闭"else"开启"),ask)
  Text("当前自动购粮${if(w.government.reserveOrder)"开启"else"关闭"}")
  ActionButton(Command("PRICE_ORDER",mode=if(w.government.priceOrder)"关闭"else"开启"),ask)
  Text("当前高价平粜${if(w.government.priceOrder)"开启"else"关闭"}，储备不足时绝不卖空。")
  Fold("救急与时代政令"){
   Choices(listOf("100万","200万","300万"),"",{ask(Command("BRIDGE_LOAN",level=it.removeSuffix("万").toInt()))})
   Text("救急款同时增加国库和债务，债务按2%/季付息。")
   ActionButton(Command("STOP_RESEARCH"),ask)
   if(w.payroll.arrears.isNotEmpty())ActionButton(Command("EMERGENCY_PAY",level=5),ask)
   if(w.setting==EraRules.ANCIENT){ActionButton(Command("LAND_SURVEY"),ask);ActionButton(Command("POST_SERVICE"),ask)}else ActionButton(Command("LABOR_SAFETY"),ask)
  }
 }
}
@Composable internal fun SovereignPanel(w:World,vm:GameViewModel,ask:(Command)->Unit){
 val s=w.sovereign
 if(!s.initialized){ObjectiveCard(w,ask);return}
 var section by rememberSaveable {mutableStateOf("安保")}
 Choices(listOf("安保","储位","列国委托"),section,{section=it})
 when(section){
 "安保"->Panel("${s.guard.name} · 第${s.generation}代君主","健康${s.health} · 基础安保${s.security} · 护卫贡献${SovereignEngine.guardStrength(w)}"){
  Text("护卫由既有首都卫营调配${s.guard.allocated}人，已包含在原兵额中；额外运行费${s.guard.upkeep}万两/季。兵额、忠诚、士气与补给都会影响保护。")
  Text("${if(s.beginnerUntil>w.day)"新手保护尚余${s.beginnerUntil-w.day}日：袭击不会致命"else"新手保护未开启或已到期"}；其他国政成本照常。")
  if(w.day<s.beginnerDeadline)ActionButton(Command("BEGINNER_PROTECTION",mode=if(s.beginnerUntil>w.day)"关闭"else"开启"),ask)
  if(s.warningDue>=0)Text("待核警讯，${(s.warningDue-w.day).coerceAtLeast(0)}日后到期。尚未认定幕后主使。")
  listOf("GUARD_FUND","ROYAL_AUDIT","ITINERARY_CHANGE").forEach {ActionButton(Command(it),ask)}
  s.successionLog.takeLast(3).forEach {Text(it,style=MaterialTheme.typography.bodySmall)}
 }
 "储位"->Panel("储位与摄政","当前：${SovereignEngine.heirName(w)}"){
  val candidates=w.household.members.filter {it.alive&&it.role in listOf("子女","义亲")&&it.ageAt(w.day)>=12}
  if(candidates.isEmpty())Text("尚无至少12岁的子女或义亲。可从宗室培养，或在行旅中建立合适的非婚恋义亲关系。")
  candidates.take(8).forEach {p->OutlinedButton(onClick={ask(Command("DESIGNATE_HEIR",target=p.id.toString()))},modifier=Modifier.fillMaxWidth()){Text("议储：${p.name} · ${p.ageAt(w.day)}岁")}}
  Text("未成年继位须摄政；没有合格储位而君主死亡时，本纪年结束，旧档案仍保留。")
  if(s.regencyUntil>w.day)Text("摄政：${w.people.find {it.id==s.regent}?.name?:"待补任"}，至第${s.regencyUntil}日；在外求学者须实际返程。")
  ActionButton(Command("ABDICATE"),ask)
 }
 else->{
  var target by rememberSaveable {mutableStateOf("foreign:0")};var kind by rememberSaveable {mutableStateOf("缉拿")};var org by rememberSaveable {mutableStateOf("hired")};var reward by rememberSaveable {mutableIntStateOf(100)}
  Panel("列国人物","与在册君主会谈同样使用已加载的本地模型"){
   s.foreign.forEachIndexed {i,f->Row {Text("${w.neighbors[i].name} · ${f.name} · 第${f.generation}代${if(f.captive)"（被拘）"else""}",Modifier.weight(1f));TextButton(onClick={if(f.person>=0)vm.openChat(f.person)else ask(Command("RULER_CONTACT",region=i))}){Text(if(f.person>=0)"交谈"else"联系")};if(f.captive)TextButton(onClick={ask(Command("RELEASE_RULER",region=i))}){Text("释放")}}}
  }
  Panel("秘密委托","限本局已登记的虚构成年人物，真实费用与失败风险"){
   Choices(s.foreign.indices.map {"${it+1}·${s.foreign[it].name}"},s.foreign.indices.firstOrNull {target=="foreign:$it"}?.let {"${it+1}·${s.foreign[it].name}"}.orEmpty(),{target="foreign:"+(it.substringBefore('·').toInt()-1)})
   var search by rememberSaveable {mutableStateOf("")}
   OutlinedTextField(search,{search=it.take(24)},label={Text("查找本局人物")},singleLine=true,modifier=Modifier.fillMaxWidth())
   if(search.isNotBlank())w.people.filter {it.alive&&it.ageQuarters>=72&&it.name.contains(search)}.take(4).forEach {p->FilterChip(selected=target=="person:${p.id}",onClick={target="person:${p.id}"},label={Text(p.name+" · "+p.role)})}
   Text("目标：${SovereignEngine.targetName(w,target)}")
   Choices(listOf("缉拿","刺杀"),kind,{kind=it})
   val groups=linkedMapOf("hired" to "雇佣专业人员").apply {w.intelligence.organizations.filter {it.kind=="刺客"}.forEach {put(it.id,it.name)}}
   Choices(groups.values.toList(),groups[org].orEmpty(),{label->org=groups.entries.first {it.value==label}.key})
   Choices(listOf("100万","200万","400万"),"${reward}万",{reward=it.removeSuffix("万").toInt()})
   Text("赏格预付，成功率受能力、忠诚与目标安保影响；失败返40%，目标状态改变中止返80%。无论成败都会伤害民心、稳定与邦交，缉拿不等于定罪。",style=MaterialTheme.typography.bodySmall)
   OutlinedButton(onClick={ask(Command("ROYAL_CONTRACT",target=org,objective=target,mode=kind,level=reward))},modifier=Modifier.fillMaxWidth()){Text("预览${kind}委托")}
   s.missions.takeLast(4).reversed().forEach {Text("${SovereignEngine.targetName(w,it.target)} · ${it.kind} · ${it.result.ifBlank {"剩余${(it.due-w.day).coerceAtLeast(0)}日"}}",style=MaterialTheme.typography.bodySmall)}
   s.captives.take(4).forEach {id->OutlinedButton(onClick={ask(Command("RELEASE_CAPTIVE",target=id.toString()))}){Text("释放${w.people.find {it.id==id}?.name}（无罪名判定）")}}
  }
 }
 }
}
@Composable internal fun EncounterPanel(w:World,vm:GameViewModel,ask:(Command)->Unit){
 val j=w.journey?:return
 val encounters=w.adventure.encounters.filter {it.region==j.region}.takeLast(5).reversed()
 if(encounters.isEmpty())return
 Fold("行旅相识 · ${encounters.size}人"){
  var selected by rememberSaveable {mutableIntStateOf(-1)}
  encounters.forEach {e->val p=w.people.first {it.id==e.person};TextButton(onClick={selected=p.id}){Text("${p.name} · ${p.ageQuarters/4}岁 · ${e.gender}")}}
  encounters.find {it.person==selected}?.let {e->val p=w.people.first {it.id==e.person};var method by rememberSaveable {mutableStateOf("自愿")}
   Text("${p.role} · 能力${p.ability} · 信任${p.trust} · 敌意${p.hostility}；${if(e.knowsIdentity)"见证过身份验证"else"只认识你的行旅身份"}")
   TextButton(onClick={vm.openChat(p.id)}){Text("交谈（免费，可用本地模型）")}
   Choices(listOf("自愿","强制"),method,{method=it})
   Text("非自愿安排可能导致离心、陈情与政治后果；未成年人只能结交、培养和建立非婚恋义亲关系。",style=MaterialTheme.typography.bodySmall)
   ActionButton(Command("ENCOUNTER_RECRUIT",target=p.id.toString(),disguise=method),ask)
   Choices(if(p.ageQuarters>=72)listOf("结交","义亲","婚配")else listOf("结交","义亲"),e.relationship,{ask(Command("ENCOUNTER_KIN",target=p.id.toString(),mode=it,disguise=method))})
   if(e.forced&&!e.released)ActionButton(Command("ENCOUNTER_RELEASE",target=p.id.toString()),ask)
  }
 }
}
