package cn.chronicle.game

import android.content.ComponentCallbacks2
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.chronicle.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Gold=Color(0xFFD9BB7A)
private val Jade=Color(0xFF91C9BA)
class MainActivity:ComponentActivity() {
 private var vm:GameViewModel?=null
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); setContent { val model:GameViewModel=viewModel();vm=model; Chronicle(model) } }
 override fun onStart() {super.onStart();vm?.foreground(true)}
 override fun onStop() {vm?.foreground(false);super.onStop()}
 override fun onTrimMemory(level:Int) { super.onTrimMemory(level); if(level>=ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) { vm?.cancelAi(); vm?.models?.unloadAsync() } }
}

@Composable fun Chronicle(vm:GameViewModel) {
 val ui by vm.state.collectAsStateWithLifecycle(); val ctx=LocalContext.current
 val prefs=remember { ctx.getSharedPreferences("appearance",0) }
 var theme by rememberSaveable { mutableIntStateOf(prefs.getInt("theme",0)) }
 var scale by rememberSaveable { mutableFloatStateOf(prefs.getFloat("scale",1f)) }
 var tab by rememberSaveable { mutableIntStateOf(0) }
 var new by rememberSaveable { mutableStateOf(false) }
 var plan by remember { mutableStateOf<Preview?>(null) }; var expected by remember { mutableLongStateOf(0) }
 val schemes=listOf(darkColorScheme(primary=Gold,secondary=Jade,background=Color(0xFF101D1C),surface=Color(0xFF192B29),surfaceVariant=Color(0xFF253A35)), lightColorScheme(primary=Color(0xFF546839),secondary=Color(0xFF326759),background=Color(0xFFF6F3EA),surface=Color(0xFFFFFDF7)), darkColorScheme(primary=Color(0xFFC8BA86),secondary=Color(0xFFABC09B),background=Color(0xFF22291F),surface=Color(0xFF30392A)))
 MaterialTheme(colorScheme=schemes[theme.coerceIn(0,2)]) {
  val d=LocalDensity.current
  CompositionLocalProvider(LocalDensity provides Density(d.density,d.fontScale*scale)) {
   val snack=remember { SnackbarHostState() }
   LaunchedEffect(ui.message) { if(ui.message.isNotBlank()) snack.showSnackbar(ui.message) }
   fun ask(c:Command) { val w=ui.world?:return;val error=if(c.type=="CANCEL_TASK") null else Rules.validate(w,c); if(error!=null) vm.message(error) else { expected=w.revision;plan=Preview(listOf(c),RealtimeEngine.describe(c)+"。事务随连续时间推进，最多同时办理3项。") } }
   val pageState=rememberSaveableStateHolder()
   Scaffold(snackbarHost={SnackbarHost(snack)},bottomBar={
    NavigationBar { listOf("剧情" to Icons.Outlined.AutoStories,"朝政" to Icons.Outlined.AccountBalance,"世界" to Icons.Outlined.Public,"档案" to Icons.Outlined.Inventory2).forEachIndexed { i,(s,icon) -> NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icon,s)},label={Text(s)},enabled=!ui.busy) } }
   }) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
     Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
      Icon(Icons.Outlined.Landscape,"山河纪年",tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(30.dp));Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) { Text("山 河 纪 年",style=MaterialTheme.typography.titleLarge,fontFamily=FontFamily.Serif); Text(ui.world?.let { "${it.name}  /  ${it.date}" }?:"CHRONICLE AI · 离线历史策略",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary) }
      if(ui.busy) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
     }
     HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f))
     val w=ui.world
     if(w!=null) Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
      TextButton(onClick={vm.toggleClock()}){Text(if(ui.paused) "继续时间" else "暂停时间")}
      TextButton(onClick={vm.changeSpeed()}){Text("×${ui.speed}")}
      Text(if(ui.paused)"时间已暂停" else "时间流动中",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
     }
     if(w==null) Welcome { new=true }
     else pageState.SaveableStateProvider(tab) { when(tab) {
      0 -> Story(w,ui,vm,::ask) { preview -> expected=w.revision;plan=preview }
      1 -> Court(w,::ask)
      2 -> WorldPage(w,::ask)
      else -> Archive(ui,vm,{new=true},theme,{ theme=it;prefs.edit().putInt("theme",it).apply() },scale,{scale=it;prefs.edit().putFloat("scale",it).apply()})
     } }
    }
   }
   if(new) NewDialog({new=false}) { a,b,c,e,f,g,h,i -> vm.newGame(a,b,c,e,f,g,h,i);new=false;tab=0 }
   plan?.let { p -> AlertDialog(onDismissRequest={plan=null},title={Text("确认行动")},text={ Column(Modifier.verticalScroll(rememberScrollState())) { Text(p.description);p.commands.forEach { c -> Spacer(Modifier.height(8.dp));Text(RealtimeEngine.describe(c),color=MaterialTheme.colorScheme.primary) }; if(p.commands.size>1) Text("政策按各自耗时办理，先预留资金，完成时检查条件。",style=MaterialTheme.typography.bodySmall) } },confirmButton={TextButton(onClick={vm.submit(p.commands,expected);plan=null},enabled=p.commands.isNotEmpty()&&!ui.busy){Text("确认执行")}},dismissButton={TextButton(onClick={plan=null}){Text("返回")}}) }
  }
 }
}
@Composable private fun Welcome(start:()->Unit) { Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center) { Mountains(); Spacer(Modifier.height(24.dp));Text("一纸诏令，\n山河自此不同。",style=MaterialTheme.typography.headlineLarge,fontFamily=FontFamily.Serif);Spacer(Modifier.height(16.dp));Text("工业初兴，旧制未退。经营财政、倾听众生，或换一身行装，亲赴澄江查访。\n\n无需联网或模型，每一步都有规则，每个决定都会留下记录。",color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(24.dp));Button(onClick=start,modifier=Modifier.fillMaxWidth()) { Text("开启纪年") } } }
@Composable private fun Mountains() { val c=MaterialTheme.colorScheme.secondary; val g=MaterialTheme.colorScheme.primary; Canvas(Modifier.fillMaxWidth().height(100.dp)) { drawCircle(g.copy(alpha=.65f),15.dp.toPx(),Offset(size.width*.79f,size.height*.22f)); val p=Path().apply {moveTo(0f,size.height);lineTo(size.width*.2f,size.height*.45f);lineTo(size.width*.35f,size.height*.7f);lineTo(size.width*.53f,size.height*.1f);lineTo(size.width*.8f,size.height*.75f);lineTo(size.width,size.height*.4f);lineTo(size.width,size.height);close()};drawPath(p,c.copy(alpha=.3f)); drawLine(g.copy(alpha=.5f),Offset(0f,size.height),Offset(size.width,size.height),2f) } }
@Composable private fun Page(content:@Composable ColumnScope.()->Unit) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content) }
@Composable private fun Panel(title:String,subtitle:String?=null,content:@Composable ColumnScope.()->Unit) { Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) { Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);subtitle?.let {Text(it,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)};content() } } }
@Composable private fun ActionButton(c:Command,ask:(Command)->Unit) { OutlinedButton(onClick={ask(c)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(12.dp)) { Text(Rules.label(c),Modifier.weight(1f));Text("${Rules.cost(c)} 万两",style=MaterialTheme.typography.labelMedium) } }
@Composable private fun Story(w:World,ui:UiState,vm:GameViewModel,ask:(Command)->Unit,custom:(Preview)->Unit) {
 var text by rememberSaveable { mutableStateOf("") }; var ledger by rememberSaveable {mutableStateOf(false)}
 Page {
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { BadgeText("连续时间");BadgeText(if(vm.models.isLoaded) "本地模型已加载" else "规则叙事");BadgeText("已自动存档") }
  Panel("${if(w.journey!=null) "行旅札记" else "御前纪事"}","${w.ruler}执政 · ${w.scale} · ${w.atmosphere}") {
   Text(w.recent.lastOrNull()?.narrative?:"启元初年，蒸汽机的轰鸣初入山河。澄江税册却与民间传言相悖。朝臣等待你的第一道决策。",style=MaterialTheme.typography.bodyLarge,lineHeight=27.sp)
   if(ui.aiText.isNotBlank()) { HorizontalDivider();Text("本地 AI 演绎 · 辅助叙述",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary);Text(ui.aiText,lineHeight=26.sp) }
   if(ui.generating) Row(verticalAlignment=Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Text(" 正在演绎…",Modifier.weight(1f));TextButton(onClick={vm.cancelAi()}){Text("取消")} }
   if(!ui.generating && vm.models.isLoaded) TextButton(onClick={vm.generate()}){Text("本地 AI 演绎本期剧情")}
   if(w.recent.isNotEmpty()) { TextButton(onClick={ledger=!ledger}) {Text(if(ledger) "收起结算账簿" else "查看结算账簿")};if(ledger) w.recent.last().ledger.forEach {Text(it,style=MaterialTheme.typography.bodySmall)} }
  }
  Panel("可行之策","时间自动流动；政策与地方事务按日办理") { RealtimeEngine.suggestions(w).forEach {ActionButton(it,ask)} }
  if(w.pending.isNotEmpty()) Panel("正在办理","取消退还80%预留资金；完成时再次检查执行条件") {w.pending.forEach {order->Row(verticalAlignment=Alignment.CenterVertically){Text("${Rules.label(order.command)} · 剩余${order.remainingDays}日",Modifier.weight(1f));TextButton(onClick={ask(Command("CANCEL_TASK",target=order.id.toString()))}){Text("撤销")}}}}
  Panel("亲拟诏令","规则解析仅执行明确列出的政策，复杂条款会提示") { OutlinedTextField(value=text,onValueChange={text=it.take(600)},label={Text("例如：兴修水渠、资助科研")},minLines=2,maxLines=4,modifier=Modifier.fillMaxWidth());Button(onClick={vm.parse(text,custom)},enabled=text.isNotBlank()&&!ui.busy&&!ui.generating,modifier=Modifier.fillMaxWidth()) {Text("解析并预览")} }
 }
}
@Composable private fun BadgeText(s:String) { Text(s,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary,modifier=Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondary.copy(alpha=.1f)).padding(horizontal=8.dp,vertical=6.dp)) }
@Composable private fun Fold(title:String,openInitially:Boolean=false,content:@Composable ColumnScope.()->Unit) { var open by rememberSaveable {mutableStateOf(openInitially)};Panel(title) { TextButton(onClick={open=!open},modifier=Modifier.fillMaxWidth()) {Text(if(open) "收起  −" else "展开  ＋")};if(open) content() } }
@Composable private fun Stat(name:String,value:Int) { Row(verticalAlignment=Alignment.CenterVertically) { Text(name,Modifier.width(74.dp),style=MaterialTheme.typography.bodyMedium);LinearProgressIndicator(progress={value/100f},modifier=Modifier.weight(1f).height(5.dp),color=MaterialTheme.colorScheme.secondary);Text(" $value",Modifier.width(38.dp),style=MaterialTheme.typography.labelMedium) } }
@Composable private fun Court(w:World,ask:(Command)->Unit) { val n=w.nation
 Page {
  Panel("国库", "资金单位：万两 · 人口单位：人 · 其他发展属性 0–100") { Text("${n.treasury} 万两",style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary);Text("债务 ${n.debt} · 税率 ${n.tax}%");Text("年度折算：收入 ${n.income} / 支出 ${n.expense}",style=MaterialTheme.typography.bodySmall);Text("年度财政为最近季度的四倍估算。",style=MaterialTheme.typography.labelSmall);Row {TextButton(onClick={ask(Command("TAX_DOWN"))}){Text("减税")};TextButton(onClick={ask(Command("TAX_UP"))}){Text("增税")};TextButton(onClick={ask(Command("REPAY"))}){Text("还债")}} }
  Fold("产业与科技",true) { Stat("农业",n.agriculture);Stat("商业",n.commerce);Stat("工业",n.industry);Stat("科技",n.technology);Stat("基础设施",n.infrastructure);Text("研究经费 ${n.research} 万两/季");Row {TextButton(onClick={ask(Command("FACTORY"))}){Text("工厂")};TextButton(onClick={ask(Command("FARM"))}){Text("水利")};TextButton(onClick={ask(Command("RESEARCH"))}){Text("研发")};TextButton(onClick={ask(Command("ROAD"))}){Text("道路")}} }
  Fold("人口与朝政") {Text("全国人口 ${n.population}");Stat("民心",n.morale);Stat("稳定",n.stability);Stat("行政",n.administration);Stat("中央腐败",n.corruption);Stat("军队实力",n.military);ActionButton(Command("RELIEF"),ask);ActionButton(Command("REFORM"),ask);ActionButton(Command("DRILL"),ask)}
  Fold("政策与在建项目") {Text("方向：${w.strategy}");Choices(listOf("工业强国","富民治国","综合发展"),w.strategy,{ask(Command("STRATEGY",target=it))});if(w.projects.isEmpty()) Text("暂无在建项目");w.projects.forEach {Text("${w.regions[it.region].name} · ${Rules.label(Command(it.kind))} · 剩余 ${it.remainingDays} 日")};Text("研究预算持续生效；休养每季削减 2 万两研究预算。",style=MaterialTheme.typography.bodySmall)}
  Fold("近期变化") { w.recent.takeLast(4).reversed().forEach { Text("纪年${it.day}日 · ${it.action}",color=MaterialTheme.colorScheme.secondary);it.ledger.takeLast(3).forEach {l->Text(l,style=MaterialTheme.typography.bodySmall)} } }
 }
}
@Composable private fun WorldPage(w:World,ask:(Command)->Unit) {
 var area by rememberSaveable {mutableIntStateOf(0)};var section by rememberSaveable {mutableIntStateOf(0)}
 Page {
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {listOf("地区","人物","列国","宗室").forEachIndexed {i,s->SegmentedButton(selected=section==i,onClick={section=i},shape=SegmentedButtonDefaults.itemShape(i,4)){Text(s)}}}
  if(section==3) {FamilyPanel(w,ask);return@Page}
  when(section) {
   0 -> { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {w.regions.forEach {r->FilterChip(selected=area==r.id,onClick={area=r.id},label={Text(r.name)})} }
    val r=w.regions[area]
    Panel(r.name,"地方官：${w.people.find {it.id==r.official}?.name?:"暂缺"} · 人口 ${r.population}") {Text("地方财政 ${r.funds} 万两");Stat("农业",r.agriculture);Stat("工业",r.industry);Stat("治安",r.security);Stat("地方腐败",r.corruption);Stat("民心",r.morale);Text("基层腐败线索：${if(area==0) "关卡额外收费" else "尚未核查"}；军事腐败：${if(w.nation.military>60) "需核验军需" else "尚无充分证据"}",style=MaterialTheme.typography.bodySmall)}
    if(w.journey==null) Departure(area,w,ask) else JourneyPanel(w,ask)
    if(area==0) CasePanel(w,ask)
    Fold("地区建设") {ActionButton(Command("FACTORY",region=area),ask);ActionButton(Command("FARM",region=area),ask);ActionButton(Command("ROAD",region=area),ask)}
   }
   1 -> { Fold("主要利益集团",true) {w.factions.forEach {Stat(it.name,it.support)}};w.people.take(10).forEach {p -> Fold("${p.name} · ${p.role}") {Text("${p.ageQuarters/4}岁 · ${w.factions[p.faction].name} · ${p.stance}");Stat("能力",p.ability);Stat("忠诚",p.loyalty);Stat("信任",p.trust);Stat("怀疑",p.suspicion);Stat("敌意",p.hostility);Text("目标：${p.goal}");p.memories.forEach {Text(it,style=MaterialTheme.typography.bodySmall)}}} }
   else -> {w.neighbors.forEach {n->Panel(n.name) {Stat("外交关系",n.relation);Stat("军事实力",n.strength)}};ActionButton(Command("DIPLOMACY"),ask);Fold("天下大事",true) {w.recent.flatMap {it.events}.takeLast(8).forEach {Text(it)}}}
  }
 }
}
@Composable private fun Departure(area:Int,w:World,ask:(Command)->Unit) {
 var mode by rememberSaveable {mutableStateOf("微服私访")};var disguise by rememberSaveable {mutableStateOf("商人")};var companion by rememberSaveable {mutableIntStateOf(2)};var regent by rememberSaveable {mutableIntStateOf(0)};var days by rememberSaveable {mutableIntStateOf(30)};var objective by rememberSaveable {mutableStateOf("财政审计")}
 Fold("安排巡游",true) { Choices(listOf("公开巡幸","微服私访","秘密调查"),mode,{mode=it});if(mode!="公开巡幸") Choices(listOf("商人","书生","百姓"),disguise,{disguise=it});Text("随行人员");Choices(w.people.take(3).filter {it.alive}.map {it.name},w.people.getOrNull(companion)?.name?:"无",{s->companion=w.people.first {it.name==s}.id});Text("留朝代理");Choices(w.people.take(3).filter {it.alive}.map {it.name},w.people.getOrNull(regent)?.name?:"无",{s->regent=w.people.first {it.name==s}.id});Choices(listOf("财政审计","民情巡视","产业考察"),objective,{objective=it});Text("停留上限 $days 日");Slider(value=days.toFloat(),onValueChange={days=it.toInt()},valueRange=10f..90f,steps=7);Text("微服成本低但调查会增加暴露；秘密调查较贵，暴露增长较慢；公开巡幸可直接核验账册。",style=MaterialTheme.typography.bodySmall);ActionButton(Command("DEPART",region=area,mode=mode,disguise=disguise,companion=companion,regent=regent,days=days,objective=objective),ask) }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Choices(items:List<String>,selected:String,onSelect:(String)->Unit) { FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {items.forEach {s->FilterChip(selected=s==selected,onClick={onSelect(s)},label={Text(s,style=MaterialTheme.typography.labelMedium)})} } }
@Composable private fun JourneyPanel(w:World,ask:(Command)->Unit) {val j=w.journey?:return
 Panel("行在 · ${w.regions[j.region].name}","${j.mode} · ${if(j.revealed) "身份已验明" else "伪装${j.disguise}"} · 随行 ${w.people.find {it.id==j.companion}?.name}") {
  Text("${j.location} · 已用 ${j.spentDays}/${j.budgetDays} 日 · 暴露风险 ${j.exposure}%")
  if(j.remainingTravel>0) {Text("距抵达还需 ${j.remainingTravel} 日");Text("继续时间后，旅程会自动推进。",style=MaterialTheme.typography.bodySmall)} else {
   var scene by rememberSaveable {mutableStateOf("集市")};Choices(listOf("集市","客栈","官府","农田","工厂","军营"),scene,{scene=it});ActionButton(Command("VISIT",target=scene),ask)
   if(j.region==0) {Fold("与地方人物交谈") {w.people.filter {it.id in 6..9 && it.alive}.forEach {p->OutlinedButton(onClick={ask(Command("TALK",target=p.id.toString()))},modifier=Modifier.fillMaxWidth()){Text("询问${p.name} · ${p.role}")}}};ActionButton(Command("INVESTIGATE"),ask);ActionButton(Command("AUDIT"),ask)}
   if(!j.revealed) ActionButton(Command("REVEAL"),ask)
  }
  ActionButton(Command("RETURN"),ask)
 }
}
@Composable private fun CasePanel(w:World,ask:(Command)->Unit) { Fold("澄江案卷 · ${w.caseFile.status}",true) {Text("知府陆敬川涉嫌额外摊派。传闻、证言和核验账册必须区分，未核查材料不能直接定罪。");if(w.caseFile.evidence.isEmpty()) Text("尚无材料。可亲访或委派监察使。");w.caseFile.evidence.forEach {Text("• ${it.label}")};if(w.caseFile.remoteDays>0) Text("监察使预计 ${w.caseFile.remoteDays} 日后归报");if(!w.caseFile.resolved) {ActionButton(Command("REMOTE"),ask);ActionButton(Command("TRIAL"),ask);ActionButton(Command("DISMISS"),ask);ActionButton(Command("CLOSE"),ask)} else Text("结案：${w.caseFile.resolution}。地方改革与人事将在后续季度持续生效。") } }
@Composable private fun Archive(ui:UiState,vm:GameViewModel,new:()->Unit,theme:Int,onTheme:(Int)->Unit,scale:Float,onScale:(Float)->Unit) {
 val ctx=LocalContext.current;val scope=rememberCoroutineScope()
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri->if(uri!=null) scope.launch {runCatching {val text=vm.export();withContext(Dispatchers.IO){ctx.contentResolver.openOutputStream(uri)?.use {it.write(text.toByteArray())}?:error("文件无法打开")}}.onSuccess {vm.message("存档已导出到你选择的位置。")}.onFailure {vm.message(it.message?:"导出失败")} }}
 val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null) scope.launch {runCatching {withContext(Dispatchers.IO){ctx.contentResolver.openInputStream(uri)?.use { readBoundedSave(it)}?:error("文件无法打开")}}.onSuccess {vm.import(it)}.onFailure {vm.message(it.message?:"导入失败")} }}
 Page {
  Panel("纪年存档","每次行动事务自动保存 · 最多 8 个相互隔离的槽位") {ui.slots.forEach {slot->OutlinedButton(onClick={vm.load(slot.id)},enabled=!ui.busy,modifier=Modifier.fillMaxWidth()) {Text("${if(slot.id==ui.slot) "●" else "○"} 槽 ${slot.id} · ${slot.name} · ${slot.turn*3}个月")}};Row {TextButton(onClick=new){Text("新建")};TextButton(onClick={vm.manualSave()}){Text("手动快照")}};Row {TextButton(onClick={export.launch("山河纪年-槽${ui.slot}.json")}){Text("导出本地文件")};TextButton(onClick={import.launch(arrayOf("application/json","text/*"))}){Text("导入空槽")}} }
  Fold("史官档案") {Button(onClick={vm.page(0)}){Text("查询最新记录")};ui.archive.forEach {entry->Fold("${entry.day}日 · ${entry.action}") {Text(entry.narrative);entry.ledger.forEach {Text(it,style=MaterialTheme.typography.bodySmall)};Text("种子 ${entry.seed} · 决策 ${entry.revision}",style=MaterialTheme.typography.labelSmall)}};Row {TextButton(onClick={vm.page(ui.offset-20)},enabled=ui.offset>0){Text("上一页")};TextButton(onClick={vm.page(ui.offset+20)},enabled=ui.archive.size==20){Text("更早记录")}}}
  ModelPanel(vm)
  Fold("阅读与主题") {Choices(listOf("深色","正常","护眼"),listOf("深色","正常","护眼")[theme],{onTheme(listOf("深色","正常","护眼").indexOf(it))});Text("字号 ${"%.0f".format(scale*100)}%");Slider(value=scale,onValueChange=onScale,valueRange=.85f..1.3f)}
  Fold("关于与玩法") {Text("山河纪年 0.1 · 原创架空历史\n原生 Kotlin / Compose · 离线优先\n包名 cn.chronicle.shanhe\n\n连续时间，可暂停与调速。政策异步办理，季度交界自动结算国政。\n\nP0 规则版：四地区、项目经济、30 个事件、澄江案件三条处理路线。AI 叙述不参与数值结算。\n\n后续版本：更丰富的战争、地区案件与制度改革。当前不含领土战争或无限制自由指令。");Text("本应用不发送游戏通知，不上传存档。",style=MaterialTheme.typography.bodySmall)}
 }
}
@Composable private fun NewDialog(close:()->Unit,start:(String,String,String,String,String,String,String,String)->Unit) {
 var scale by rememberSaveable {mutableStateOf("中等国家")};var atmosphere by rememberSaveable {mutableStateOf("均势博弈")};var era by rememberSaveable {mutableStateOf("启元")};var name by rememberSaveable {mutableStateOf("承熙")};var ruler by rememberSaveable {mutableStateOf("景衡")};var strategy by rememberSaveable {mutableStateOf("综合发展")};var difficulty by rememberSaveable {mutableStateOf("标准")};var seed by rememberSaveable {mutableStateOf("1847")}
 AlertDialog(onDismissRequest=close,title={Text("开启新的纪年")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(value=name,onValueChange={name=it.take(24)},label={Text("国号")},singleLine=true);OutlinedTextField(value=ruler,onValueChange={ruler=it.take(24)},label={Text("君主")},singleLine=true);OutlinedTextField(value=era,onValueChange={era=it.take(16)},label={Text("纪元名称")},singleLine=true);Choices(listOf("工业强国","富民治国","综合发展"),strategy,{strategy=it});Choices(listOf("宽裕","标准","艰难"),difficulty,{difficulty=it});Text("国家规模");Choices(listOf("超级大国","中等国家","小国","随机"),scale,{scale=it});Text("世界格局");Choices(listOf("和平建设","均势博弈","烽火边疆"),atmosphere,{atmosphere=it});OutlinedTextField(value=seed,onValueChange={seed=it.take(20)},label={Text("世界种子（整数）")},singleLine=true);TextButton(onClick={seed=java.security.SecureRandom().nextInt(1000000).toString()}){Text("随机种子")}}},confirmButton={TextButton(onClick={start(name,ruler,strategy,difficulty,seed,era,scale,atmosphere)},enabled=name.isNotBlank()&&ruler.isNotBlank()&&FamilyEngine.validName(era)&&seed.toLongOrNull()!=null){Text("建立国家")}},dismissButton={TextButton(onClick=close){Text("取消")}})
}

private fun readBoundedSave(input:java.io.InputStream):String {
 val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(32768)
 while(true) {val n=input.read(buffer);if(n<0)break;require(out.size()+n<=8*1024*1024){"文件超过8MB"};out.write(buffer,0,n)}
 return out.toString("UTF-8")
}

@Composable private fun FamilyPanel(w:World,ask:(Command)->Unit) {
 val h=w.household
 var ruler by rememberSaveable(w.ruler){mutableStateOf(w.ruler)};var era by rememberSaveable(w.era){mutableStateOf(w.era)}
 var childName by rememberSaveable(h.nextChildName){mutableStateOf(h.nextChildName)}
 Panel("宗室与家族","所有命名、备孕与出生结果都会登记并自动保存") {
  Text("君主：${w.ruler} · 纪元：${w.era}")
  Row {Switch(checked=h.allowPregnancy,onCheckedChange={ask(Command("FAMILY_PLAN",target=if(it)"允许" else "暂停"))});Text(" 允许新的自愿备孕",Modifier.padding(top=12.dp))}
  Text("仅成年、自愿且健康与关系条件合适的伴侣可备孕。暂停新安排不改变已有孕期。",style=MaterialTheme.typography.bodySmall)
  h.pregnancy?.let {Text("已登记孕期：预计还需 ${it.remainingDays} 日 · 预备名 ${it.childName}",color=MaterialTheme.colorScheme.primary)}?:Text("当前没有登记孕期")
  ActionButton(Command("TRY_HEIR"),ask);ActionButton(Command("FAMILY_TIME"),ask)
 }
 Fold("君主姓名与纪元") {
  OutlinedTextField(value=ruler,onValueChange={ruler=it.take(16)},label={Text("君主姓名")},singleLine=true,modifier=Modifier.fillMaxWidth())
  TextButton(onClick={ask(Command("RULER_NAME",target=ruler))},enabled=FamilyEngine.validName(ruler)){Text("登记新姓名")}
  OutlinedTextField(value=era,onValueChange={era=it.take(16)},label={Text("纪元")},singleLine=true,modifier=Modifier.fillMaxWidth())
  TextButton(onClick={ask(Command("ERA",target=era))},enabled=FamilyEngine.validName(era)){Text("确认改元")}
  Text("改元从当前年份记元年，原始日期和历史文本不重写。",style=MaterialTheme.typography.bodySmall)
 }
 Fold("子女命名",true) {
  OutlinedTextField(value=childName,onValueChange={childName=it.take(16)},label={Text("下一次备孕的预备名")},singleLine=true,modifier=Modifier.fillMaxWidth())
  TextButton(onClick={ask(Command("CHILD_NAME_PLAN",target=childName))},enabled=FamilyEngine.validName(childName)){Text("保存预备名")}
  Text("子女出生后仍可更名；已登记孕期保留当时的预备名。",style=MaterialTheme.typography.bodySmall)
  val children=h.members.filter {it.role=="子女" && it.alive}
  if(children.isEmpty())Text("暂无在世子女")
  children.forEach {p->var name by rememberSaveable(p.id){mutableStateOf(p.name)};Text("${p.name} · ${(w.day-p.bornDay)/360}岁");OutlinedTextField(value=name,onValueChange={name=it.take(16)},label={Text("子女姓名")},singleLine=true,modifier=Modifier.fillMaxWidth());TextButton(onClick={ask(Command("NAME_CHILD",target="${p.id}|$name"))},enabled=FamilyEngine.validName(name)){Text("为${p.name}更名")}}
 }
 Fold("家族名册") {h.members.takeLast(12).forEach {p->Text("${p.name} · ${p.role} · ${(w.day-p.bornDay)/360}岁 · ${if(p.alive) "在世" else "已故"}");Text("关系 ${p.relationship} / 健康 ${p.health}${if(p.role=="成年伴侣") " / 自愿意愿${if(p.willing) "已确认" else "未确认"}" else ""}",style=MaterialTheme.typography.bodySmall)}}
}
