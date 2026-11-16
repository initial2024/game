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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.chronicle.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LocalWorldSetting=staticCompositionLocalOf {"工业初兴"}
private val Gold=Color(0xFFD9BB7A)
private val Jade=Color(0xFF91C9BA)
class MainActivity:ComponentActivity() {
 private var vm:GameViewModel?=null
 var mapController:MapWebController?=null
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); vm=androidx.lifecycle.ViewModelProvider(this)[GameViewModel::class.java];enableEdgeToEdge(); setContent { val model:GameViewModel=viewModel();vm=model; Chronicle(model) } }
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
 var plan by remember { mutableStateOf<Preview?>(null) }; var expected by remember { mutableLongStateOf(0) };var decreePerson by remember {mutableStateOf<Int?>(null)};var decreeText by remember {mutableStateOf("")};var planSlot by remember {mutableIntStateOf(0)}
 val schemes=listOf(darkColorScheme(primary=Gold,secondary=Jade,background=Color(0xFF101D1C),surface=Color(0xFF192B29),surfaceVariant=Color(0xFF253A35),surfaceContainer=Color(0xFF192B29),surfaceContainerHigh=Color(0xFF203631),surfaceContainerHighest=Color(0xFF29423B),primaryContainer=Color(0xFF3C4B34),secondaryContainer=Color(0xFF29463E),onPrimary=Color(0xFF17261F)), lightColorScheme(primary=Color(0xFF546839),secondary=Color(0xFF326759),background=Color(0xFFF6F3EA),surface=Color(0xFFFFFDF7)), darkColorScheme(primary=Color(0xFFC8BA86),secondary=Color(0xFFABC09B),background=Color(0xFF22291F),surface=Color(0xFF30392A)))
 MaterialTheme(colorScheme=schemes[theme.coerceIn(0,2)]) {
  val d=LocalDensity.current
  CompositionLocalProvider(LocalDensity provides Density(d.density,d.fontScale*scale),LocalWorldSetting provides (ui.world?.setting?:"工业初兴")) {
   var showMessage by remember {mutableStateOf(false)}
   fun ask(c:Command) { val w=ui.world?:return;val error=if(c.type=="CANCEL_TASK") null else Rules.validate(w,c); if(error!=null) vm.message(error) else { expected=w.revision;val detail=when(c.type){"ORG_FOUND"->"${c.text} · ${c.objective} · ${w.towns.find {it.id==c.target}?.name}";"ORG_ASSIGN","ORG_RELEASE","ASSASSINATE"->"${w.intelligence.organizations.find {it.id==c.target}?.name} → ${w.people.find {it.id==c.objective.toIntOrNull()}?.name}";"DECLARE_WAR"->"${CrownEngine.units(w).find {it.id==c.target}?.name} → ${w.neighbors[c.region].name}";"CANAL"->"${w.towns.find {it.id==c.target}?.name} → ${w.towns.find {it.id==c.objective}?.name}";"ARMY_ASSIGN"->"${w.people.find {it.id==c.objective.toIntOrNull()}?.name} → ${CrownEngine.units(w).find {it.id==c.target}?.name}";else->""};plan=Preview(listOf(c),detail+"\n"+RealtimeEngine.describe(c)+"。事务随连续时间推进，最多同时办理3项。"+if(c.type=="ASSASSINATE")"此为游戏内暴力行动，不保证成功，无论成败民心-6、稳定-8，增加暴露与追查。目标死亡无法撤销。"else"") } }
   LaunchedEffect(ui.slot){plan=null;decreePerson=null}
   val pageState=rememberSaveableStateHolder()
   Scaffold(bottomBar={
    NavigationBar(containerColor=MaterialTheme.colorScheme.surface) { listOf("剧情" to Icons.Outlined.AutoStories,"朝政" to Icons.Outlined.AccountBalance,"世界" to Icons.Outlined.Public,"档案" to Icons.Outlined.Inventory2).forEachIndexed { i,(s,icon) -> NavigationBarItem(modifier=Modifier.testTag("nav-$i"),selected=tab==i,onClick={tab=i},icon={Icon(icon,s)},label={Text(s)},enabled=!ui.busy) } }
   }) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
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
     if(ui.message.isNotBlank()) Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.secondaryContainer) {Row(Modifier.padding(start=16.dp),verticalAlignment=Alignment.CenterVertically) {Text(ui.message,Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall);TextButton(onClick={showMessage=true}){Text("详情")};IconButton(onClick={vm.message("")}){Icon(Icons.Outlined.Close,"关闭反馈")}}}
     if(!ui.ready) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
     else if(w==null) Welcome { new=true }
     else pageState.SaveableStateProvider(tab) { when(tab) {
      0 -> Story(w,ui,vm,::ask) { preview -> expected=w.revision;plan=preview }
      1 -> Court(w,::ask)
      2 -> WorldPage(w,ui.slot,vm,::ask)
      else -> Archive(ui,vm,{new=true},theme,{ theme=it;prefs.edit().putInt("theme",it).apply() },scale,{scale=it;prefs.edit().putFloat("scale",it).apply()})
     } }
    }
   }
   ui.world?.let {world->ui.cityId?.let {id->CityDialog(world,id,{vm.closeCity()},::ask,{vm.closeCity();vm.openChat(it)})};CharacterChat(vm,world){person,text->decreePerson=person;decreeText=text.take(600);planSlot=ui.slot}}
   decreePerson?.let {person->AlertDialog(onDismissRequest={decreePerson=null},title={Text("拟定旨意")},text={Column(Modifier.verticalScroll(rememberScrollState())){Text("对话尚未执行政策。请整理为明确命令，再核对实际行动。辖区："+(ui.world?.let {w->DecreeEngine.town(w,person)?.let {MunicipalEngine.name(w,it)}}?:"国家政策"));OutlinedTextField(decreeText,{decreeText=it.take(600)},label={Text("旨意内容")},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=6)}},confirmButton={TextButton(onClick={val w=vm.state.value.world;if(w!=null&&vm.state.value.slot==planSlot){expected=w.revision;plan=DecreeEngine.preview(w,person,decreeText)};decreePerson=null}){Text("预览旨意")}},dismissButton={TextButton(onClick={decreePerson=null}){Text("放弃拟旨")}})}
   if(showMessage) AlertDialog(onDismissRequest={showMessage=false},title={Text("执行反馈")},text={Text(ui.message,Modifier.verticalScroll(rememberScrollState()))},confirmButton={TextButton(onClick={showMessage=false}){Text("关闭")}})
   if(new) NewDialog({new=false}) { a,b,c,e,f,g,h,i,j -> vm.newGame(a,b,c,e,f,g,h,i,j);new=false;tab=0 }
   plan?.let { p -> AlertDialog(onDismissRequest={plan=null},title={Text("确认行动")},text={ Column(Modifier.verticalScroll(rememberScrollState())) { Text(p.description);p.commands.forEach { c -> Spacer(Modifier.height(8.dp));Text(EraRules.render(RealtimeEngine.describe(c),ui.world?.setting?:"工业初兴"),color=MaterialTheme.colorScheme.primary) }; if(p.commands.size>1) Text("政策按各自耗时办理，先预留资金，完成时检查条件。",style=MaterialTheme.typography.bodySmall) } },confirmButton={TextButton(onClick={vm.submit(p.commands,expected);plan=null},enabled=p.commands.isNotEmpty()&&!ui.busy){Text("确认执行")}},dismissButton={TextButton(onClick={plan=null}){Text("返回")}}) }
  }
 }
}
@Composable private fun Welcome(start:()->Unit) { Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center) { Mountains(); Spacer(Modifier.height(24.dp));Text("一纸诏令，\n山河自此不同。",style=MaterialTheme.typography.headlineLarge,fontFamily=FontFamily.Serif);Spacer(Modifier.height(16.dp));Text("古代王朝或工业初兴，由你选择。经营财政、倾听众生，或换一身行装，亲赴澄江查访。\n\n无需联网或模型，每一步都有规则，每个决定都会留下记录。",color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(24.dp));Button(onClick=start,modifier=Modifier.fillMaxWidth()) { Text("开启纪年") } } }
@Composable private fun Mountains() { val c=MaterialTheme.colorScheme.secondary; val g=MaterialTheme.colorScheme.primary; Canvas(Modifier.fillMaxWidth().height(100.dp)) { drawCircle(g.copy(alpha=.65f),15.dp.toPx(),Offset(size.width*.79f,size.height*.22f)); val p=Path().apply {moveTo(0f,size.height);lineTo(size.width*.2f,size.height*.45f);lineTo(size.width*.35f,size.height*.7f);lineTo(size.width*.53f,size.height*.1f);lineTo(size.width*.8f,size.height*.75f);lineTo(size.width,size.height*.4f);lineTo(size.width,size.height);close()};drawPath(p,c.copy(alpha=.3f)); drawLine(g.copy(alpha=.5f),Offset(0f,size.height),Offset(size.width,size.height),2f) } }
@Composable private fun Page(content:@Composable ColumnScope.()->Unit) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content) }
@Composable private fun Panel(title:String,subtitle:String?=null,content:@Composable ColumnScope.()->Unit) { Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) { Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);subtitle?.let {Text(it,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)};content() } } }
@Composable private fun ActionButton(c:Command,ask:(Command)->Unit) { OutlinedButton(onClick={ask(c)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(12.dp)) { Text(EraRules.label(c,LocalWorldSetting.current),Modifier.weight(1f));Text("${Rules.cost(c)} 万两",style=MaterialTheme.typography.labelMedium) } }
@Composable private fun Story(w:World,ui:UiState,vm:GameViewModel,ask:(Command)->Unit,custom:(Preview)->Unit) {
 var text by rememberSaveable {mutableStateOf("")};var group by rememberSaveable {mutableStateOf("纪事")}
 Page {
  Choices(listOf("纪事","行动","事务"),group,{group=it})
  when(group) {
   "纪事" -> {
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {BadgeText("连续时间");BadgeText(if(vm.models.isLoaded)"本地模型已加载" else "规则叙事");BadgeText("已自动存档")}
    Panel(if(w.journey!=null)"行旅札记" else "御前纪事","${w.ruler}执政 · ${w.scale} · ${w.atmosphere}") {
     Column(Modifier.heightIn(max=260.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
      Text(w.recent.lastOrNull()?.narrative?:if(w.setting==EraRules.ANCIENT)"${w.era}初年，农桑与百工维系山河，驿骑往来府县。澄江税册与民间传言相悖，朝臣等待你的决策。" else "${w.era}初年，蒸汽机的轰鸣初入山河。澄江税册却与民间传言相悖。朝臣等待你的第一道决策。",style=MaterialTheme.typography.bodyLarge,lineHeight=27.sp)
      if(ui.aiText.isNotBlank()){HorizontalDivider();Text("本地AI演绎 · 辅助叙述",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary);Text(ui.aiText,lineHeight=26.sp)}
     }
     if(ui.generating)Row(verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Text(" 正在演绎…",Modifier.weight(1f));TextButton(onClick={vm.cancelAi()}){Text("取消")}}
     if(!ui.generating && vm.models.isLoaded)TextButton(onClick={vm.generate()}){Text("演绎当前局势")}
    }
    Button(onClick={group="行动"},modifier=Modifier.fillMaxWidth()){Text("前往下令")}
   }
   "行动" -> {
    Panel("可行之策","时间自动流动，政策和地方事务按日办理") {RealtimeEngine.suggestions(w).forEach {ActionButton(it,ask)}}
    Fold("亲拟诏令") {OutlinedTextField(value=text,onValueChange={text=it.take(600)},label={Text(if(w.setting==EraRules.ANCIENT)"例如：兴修水渠、资助格物" else "例如：兴修水渠、资助科研")},minLines=2,maxLines=4,modifier=Modifier.fillMaxWidth());Button(onClick={vm.parse(text,custom)},enabled=text.isNotBlank()&&!ui.busy&&!ui.generating,modifier=Modifier.fillMaxWidth()){Text("解析并预览")}}
   }
   else -> {
    Panel("正在办理","最多3项并行事务；取消退还80%预留资金") {
     if(w.pending.isEmpty())Text("暂无待办政策，时间仍会推动生产与国政。")
     w.pending.forEach {order->Row(verticalAlignment=Alignment.CenterVertically){Text("${Rules.label(order.command)} · ${order.remainingDays}日",Modifier.weight(1f));TextButton(onClick={ask(Command("CANCEL_TASK",target=order.id.toString()))}){Text("撤销")}}}
     w.journey?.let {Text("行旅：${w.regions[it.region].name} · ${if(it.remainingTravel>0) "距抵达${it.remainingTravel}日" else "已抵达"}")}
     Text("在建项目见朝政 → 项目。",style=MaterialTheme.typography.bodySmall)
    }
    w.recent.lastOrNull()?.let {e->Panel("最近结算","${e.day}日 · ${e.action}"){Column(Modifier.heightIn(max=220.dp).verticalScroll(rememberScrollState())){e.ledger.forEach {Text(it,style=MaterialTheme.typography.bodySmall)}}}}
   }
  }
 }
}
@Composable private fun BadgeText(s:String) { Text(s,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary,modifier=Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondary.copy(alpha=.1f)).padding(horizontal=8.dp,vertical=6.dp)) }
@Composable private fun Fold(title:String,openInitially:Boolean=false,content:@Composable ColumnScope.()->Unit) { var open by rememberSaveable {mutableStateOf(openInitially)};Panel(title) { TextButton(onClick={open=!open},modifier=Modifier.fillMaxWidth()) {Text(if(open) "收起  −" else "展开  ＋")};if(open) content() } }
@Composable private fun Stat(name:String,value:Int) { Row(verticalAlignment=Alignment.CenterVertically) { Text(name,Modifier.width(74.dp),style=MaterialTheme.typography.bodyMedium);LinearProgressIndicator(progress={value/100f},modifier=Modifier.weight(1f).height(5.dp),color=MaterialTheme.colorScheme.secondary);Text(" $value",Modifier.width(38.dp),style=MaterialTheme.typography.labelMedium) } }
@Composable private fun Court(w:World,ask:(Command)->Unit) {
 val n=w.nation;var group by rememberSaveable {mutableStateOf("财政")};var projectPage by rememberSaveable {mutableIntStateOf(0)}
 LaunchedEffect(w.projects.size) {projectPage=projectPage.coerceAtMost((w.projects.size-1).coerceAtLeast(0)/4)}
 Page {
  Choices(listOf("财政","产业","民生","军政","建设","谍报","项目","趋势"),group,{group=it})
  when(group) {
   "财政" -> Panel("国库","资金：万两 · 年度数为最近季度的四倍估算") {
    Text("${n.treasury} 万两",style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary)
    Text("债务 ${n.debt} · 税率 ${n.tax}%");Text("年度收入 ${n.income} / 支出 ${n.expense}")
    Row {TextButton(onClick={ask(Command("TAX_DOWN"))}){Text("减税")};TextButton(onClick={ask(Command("TAX_UP"))}){Text("增税")};TextButton(onClick={ask(Command("REPAY"))}){Text("还债")}}
   }
   "产业" -> Panel(if(w.setting==EraRules.ANCIENT)"百工与格物" else "产业与科技","发展属性均为0–100") {
    Stat("农业",n.agriculture);Stat("商业",n.commerce);Stat(EraRules.industry(w),n.industry);Stat(if(w.setting==EraRules.ANCIENT)"格物" else "科技",n.technology);Stat("基础设施",n.infrastructure)
    Text("持续研究 ${n.research} 万两/季",style=MaterialTheme.typography.bodySmall)
    Row {TextButton(onClick={ask(Command(EraRules.factory(w)))}){Text(if(w.setting==EraRules.ANCIENT)"作坊"else"工厂")};TextButton(onClick={ask(Command("FARM"))}){Text("水利")};TextButton(onClick={ask(Command("RESEARCH"))}){Text(if(w.setting==EraRules.ANCIENT)"格物"else"研发")};TextButton(onClick={ask(Command("ROAD"))}){Text("道路")}}
   }
   "民生" -> Panel("人口与朝政") {
    Text("全国人口 ${n.population} 人");Stat("民心",n.morale);Stat("稳定",n.stability);Stat("行政",n.administration);Stat("中央腐败",n.corruption);Stat("军队实力",n.military)
    ActionButton(Command("RELIEF"),ask);ActionButton(Command("REFORM"),ask);ActionButton(Command("DRILL"),ask)
   }
   "军政" -> ArmyPanel(w,ask)
   "建设" -> {var section by rememberSaveable {mutableStateOf("宫廷")};Choices(listOf("宫廷","运河"),section,{section=it});if(section=="宫廷")PalacePanel(w,ask)else CanalPanel(w,ask)}
   "谍报" -> IntelligencePanel(w,ask)
   "项目" -> Panel("政策与在建项目") {
    Text("当前方向：${w.strategy}");Choices(listOf(if(w.setting==EraRules.ANCIENT)"商工兴国" else "工业强国","富民治国","综合发展"),w.strategy,{ask(Command("STRATEGY",target=it))})
    w.towns.filter {it.work!=null}.forEach {t->Text("${MunicipalEngine.name(w,t)} · ${MunicipalEngine.buildings.first {it.id==t.work!!.kind}.name} · 剩余${t.work!!.remainingDays}日")}
    if(w.projects.isEmpty()&&w.towns.none {it.work!=null})Text("暂无在建项目")
    w.projects.drop(projectPage*4).take(4).forEach {Text("${w.regions[it.region].name} · ${Rules.label(Command(it.kind))} · 剩余${it.remainingDays}日")}
    if(w.projects.size>4)Row {TextButton(onClick={projectPage--},enabled=projectPage>0){Text("上一页")};TextButton(onClick={projectPage++},enabled=(projectPage+1)*4<w.projects.size){Text("下一页")}}
    Text("研究预算持续生效，休养会逐步削减预算。",style=MaterialTheme.typography.bodySmall)
   }
   else -> Panel("近期属性变化","每次变化均可在档案查询原始决策与账簿") {
    w.recent.takeLast(3).reversed().forEach {e->Fold("${e.day}日 · ${e.action}") {e.ledger.takeLast(3).forEach {Text(it,style=MaterialTheme.typography.bodySmall)}}}
   }
  }
 }
}
@Composable private fun WorldPage(w:World,slot:Int,vm:GameViewModel,ask:(Command)->Unit) {
 var showMap by rememberSaveable {mutableStateOf(false)}
 var area by rememberSaveable {mutableIntStateOf(0)};var section by rememberSaveable {mutableIntStateOf(0)}
 var regionGroup by rememberSaveable {mutableStateOf("府县")}
 if(showMap){androidx.compose.ui.window.Dialog(onDismissRequest={showMap=false},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=true)){Surface(Modifier.fillMaxSize()){WorldMapScreen(w,slot,{showMap=false},ask,{area=it;section=0;regionGroup="府县";showMap=false})}}}
 Page {
  OutlinedButton(onClick={showMap=true},modifier=Modifier.fillMaxWidth()){Text("打开世界舆图")}
  Text("${w.towns.count {it.rank=="府城"}}府 · ${w.towns.count {it.rank=="县城"}}县 · 城市情况与建筑可逐一查看",style=MaterialTheme.typography.labelSmall)
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {listOf("地区","人物","列国","宗室","学院").forEachIndexed {i,t->SegmentedButton(selected=section==i,onClick={section=i},shape=SegmentedButtonDefaults.itemShape(i,5)){Text(t)}}}
  when(section) {
   0 -> {
    Choices(w.regions.map {it.name},w.regions[area].name,{name->area=w.regions.first {it.name==name}.id})
    Choices(listOf("概览","府县","行旅","案卷","建设"),regionGroup,{regionGroup=it})
    val r=w.regions[area]
    when(regionGroup) {
     "府县" -> CountyBrowser(w,area,{vm.openCity(it)})
     "概览" -> Panel(r.name,"地方官：${w.people.find {it.id==r.official}?.name?:"暂缺"} · 人口 ${r.population}") {
      Text("地方财政 ${r.funds} 万两");Stat("农业",r.agriculture);Stat(EraRules.industry(w),r.industry);Stat("治安",r.security);Stat("地方腐败",r.corruption);Stat("民心",r.morale)
      Row {TextButton(onClick={regionGroup="行旅"}){Text("安排行程")};TextButton(onClick={regionGroup="案卷"}){Text("查看案件")}}
     }
     "行旅" -> if(w.journey==null)Departure(area,w,ask) else JourneyPanel(w,ask)
     "案卷" -> if(area==0)CasePanel(w,ask) else Panel("地方案卷") {Text("此地暂无已登记的完整案件。澄江为首个示范地区；地方腐败仍保留在地区状态中。")}
     else -> Panel("${r.name}建设") {w.projects.filter {it.region==area}.forEach {Text("${Rules.label(Command(it.kind))} · 剩余${it.remainingDays}日")};ActionButton(Command(EraRules.factory(w),region=area),ask);ActionButton(Command("FARM",region=area),ask);ActionButton(Command("ROAD",region=area),ask)}
    }
   }
   1 -> {
    Fold("主要利益集团") {w.factions.forEach {Stat(it.name,it.support)}}
    PersonnelPanel(w,vm,ask)

   }
   2 -> {
    w.neighbors.forEach {n->Panel(n.name) {Stat("外交关系",n.relation);Stat("军事实力",n.strength)}}
    ActionButton(Command("DIPLOMACY"),ask)
    Fold("天下大事") {Column(Modifier.heightIn(max=220.dp).verticalScroll(rememberScrollState())) {w.recent.flatMap {it.events}.takeLast(8).forEach {Text(it)}}}
   }
   3 -> FamilyPanel(w,vm,ask)
   else -> AcademyPanel(w,ask)
  }
 }

}
@Composable private fun Departure(area:Int,w:World,ask:(Command)->Unit) {
 var mode by rememberSaveable {mutableStateOf("微服私访")};var disguise by rememberSaveable {mutableStateOf("商人")};var companion by rememberSaveable {mutableIntStateOf(2)};var regent by rememberSaveable {mutableIntStateOf(0)}
 var days by rememberSaveable {mutableIntStateOf(30)};var objective by rememberSaveable {mutableStateOf("财政审计")};var form by rememberSaveable {mutableStateOf("行程")}
 Panel("安排巡游","${w.regions[area].name} · ${mode} · 随行与摄政分别指定") {
  Choices(listOf("行程","人员"),form,{form=it})
  if(form=="行程") {
   Choices(listOf("公开巡幸","微服私访","秘密调查"),mode,{mode=it})
   if(mode!="公开巡幸")Choices(listOf("商人","书生","百姓"),disguise,{disguise=it})
   Choices(listOf("财政审计","民情巡视","产业考察"),objective,{objective=it})
   Text("停留上限 $days 日");Slider(value=days.toFloat(),onValueChange={days=it.toInt()},valueRange=10f..90f,steps=7)
  } else {
   Text("随行人员");Choices(w.people.take(3).filter {it.alive}.map {it.name},w.people.getOrNull(companion)?.name?:"无",{name->companion=w.people.first {it.name==name}.id})
   Text("留朝代理");Choices(w.people.take(3).filter {it.alive}.map {it.name},w.people.getOrNull(regent)?.name?:"无",{name->regent=w.people.first {it.name==name}.id})
  }
  Text("微服较便宜，调查增加暴露；秘密调查较贵但暴露较慢；公开巡幸可直接核验账册。",style=MaterialTheme.typography.bodySmall)
  ActionButton(Command("DEPART",region=area,mode=mode,disguise=disguise,companion=companion,regent=regent,days=days,objective=objective),ask)
 }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Choices(items:List<String>,selected:String,onSelect:(String)->Unit) { FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {items.forEach {s->FilterChip(selected=s==selected,onClick={onSelect(s)},label={Text(s,style=MaterialTheme.typography.labelMedium)})} } }
@Composable private fun JourneyPanel(w:World,ask:(Command)->Unit) {val j=w.journey?:return
 Panel("行在 · ${w.regions[j.region].name}","${j.mode} · ${if(j.revealed) "身份已验明" else "伪装${j.disguise}"} · 随行 ${w.people.find {it.id==j.companion}?.name}") {
  Text("${j.location} · 已用 ${j.spentDays}/${j.budgetDays} 日 · 暴露风险 ${j.exposure}%")
  if(j.remainingTravel>0) {Text("距抵达还需 ${j.remainingTravel} 日");Text("继续时间后，旅程会自动推进。",style=MaterialTheme.typography.bodySmall)} else {
   var scene by rememberSaveable {mutableStateOf("集市")};Choices(listOf("集市","客栈","官府","农田",if(w.setting==EraRules.ANCIENT)"作坊" else "工厂","军营"),scene,{scene=it});ActionButton(Command("VISIT",target=scene),ask)
   if(j.region==0) {Fold("与地方人物交谈") {w.people.filter {it.id in 6..9 && it.alive}.forEach {p->OutlinedButton(onClick={ask(Command("TALK",target=p.id.toString()))},modifier=Modifier.fillMaxWidth()){Text("询问${p.name} · ${p.role}")}}};ActionButton(Command("INVESTIGATE"),ask);ActionButton(Command("AUDIT"),ask)}
   if(!j.revealed) ActionButton(Command("REVEAL"),ask)
  }
  ActionButton(Command("RETURN"),ask)
 }
}
@Composable private fun CasePanel(w:World,ask:(Command)->Unit) { Fold("澄江案卷 · ${w.caseFile.status}",true) {Text("知府陆敬川涉嫌额外摊派。传闻、证言和核验账册必须区分，未核查材料不能直接定罪。");if(w.caseFile.evidence.isEmpty()) Text("尚无材料。可亲访或委派监察使。");w.caseFile.evidence.forEach {Text("• ${it.label}")};if(w.caseFile.remoteDays>0) Text("监察使预计 ${w.caseFile.remoteDays} 日后归报");if(!w.caseFile.resolved) {ActionButton(Command("REMOTE"),ask);ActionButton(Command("TRIAL"),ask);ActionButton(Command("DISMISS"),ask);ActionButton(Command("CLOSE"),ask)} else Text("结案：${w.caseFile.resolution}。地方改革与人事将在后续季度持续生效。") } }
@Composable private fun Archive(ui:UiState,vm:GameViewModel,new:()->Unit,theme:Int,onTheme:(Int)->Unit,scale:Float,onScale:(Float)->Unit) {
 val ctx=LocalContext.current;val scope=rememberCoroutineScope();val version=remember {ctx.packageManager.getPackageInfo(ctx.packageName,0).versionName}
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri->if(uri!=null) scope.launch {runCatching {val text=vm.export();withContext(Dispatchers.IO){ctx.contentResolver.openOutputStream(uri)?.use {it.write(text.toByteArray())}?:error("文件无法打开")}}.onSuccess {vm.message("存档已导出到你选择的位置。")}.onFailure {vm.message(it.message?:"导出失败")} }}
 val exportChats=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)scope.launch {runCatching {vm.exportDialogues(uri)}.onSuccess {vm.message("全部对话已导出，游戏内记录仍保留。")}.onFailure {vm.message("导出失败：${it.message}")}}}
 val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null) scope.launch {runCatching {withContext(Dispatchers.IO){ctx.contentResolver.openInputStream(uri)?.use { readBoundedSave(it)}?:error("文件无法打开")}}.onSuccess {vm.import(it)}.onFailure {vm.message(it.message?:"导入失败")} }}
 var group by rememberSaveable {mutableStateOf("存档")};var savePage by rememberSaveable {mutableIntStateOf(0)};var selectedEntry by remember {mutableStateOf<Entry?>(null)}
 LaunchedEffect(group) {if(group=="史书")vm.page(0)}
 Page {
  Choices(listOf("存档","史书","模型","设置"),group,{group=it})
  when(group) {
   "存档" -> Panel("纪年存档","自动保存 · 最多8个独立槽位") {
    ui.slots.drop(savePage*4).take(4).forEach {slot->OutlinedButton(onClick={vm.load(slot.id)},enabled=!ui.busy,modifier=Modifier.fillMaxWidth()){Text("${if(slot.id==ui.slot) "●" else "○"} 槽${slot.id} · ${slot.name} · ${slot.turn*3}个月")}}
    if(ui.slots.size>4)Row {TextButton(onClick={savePage--},enabled=savePage>0){Text("上一页")};TextButton(onClick={savePage++},enabled=(savePage+1)*4<ui.slots.size){Text("下一页")}}
    Row {TextButton(onClick=new){Text("新建")};TextButton(onClick={vm.manualSave()}){Text("手动快照")}}
    TextButton(onClick={exportChats.launch("山河纪年-全部对话-槽${ui.slot}.json")}){Text("导出全部对话")}
    Row {TextButton(onClick={export.launch("山河纪年-槽${ui.slot}.json")}){Text("导出本地文件")};TextButton(onClick={import.launch(arrayOf("application/json","text/*"))}){Text("导入空槽")}}
   }
   "史书" -> Panel("史官档案","每页5项，点开查看剧情与结算账簿") {
    if(ui.archive.isEmpty())Text("本页暂无记录")
    ui.archive.forEach {entry->OutlinedButton(onClick={selectedEntry=entry},modifier=Modifier.fillMaxWidth()){Text("${entry.day}日 · ${entry.action}")}}
    Row {TextButton(onClick={vm.page(ui.offset-5)},enabled=ui.offset>0){Text("上一页")};TextButton(onClick={vm.page(ui.offset+5)},enabled=ui.archive.size==5){Text("更早记录")}}
    TextButton(onClick={vm.page(0)}){Text("回到最新")}
   }
   "模型" -> ModelPanel(vm)
   else -> {
    Panel("阅读与主题") {Choices(listOf("深色","正常","护眼"),listOf("深色","正常","护眼")[theme],{onTheme(listOf("深色","正常","护眼").indexOf(it))});Text("字号 ${"%.0f".format(scale*100)}%");Slider(value=scale,onValueChange=onScale,valueRange=.85f..1.3f)}
    Fold("关于山河纪年") {Text("山河纪年 $version · 原创离线文字策略\n连续时间，可暂停与调速。政策异步办理，季度交界自动结算国政。\n\n包含四地区、项目经济、33个规则事件和澄江案件三条处理路线。AI不参与数值结算。\n\n完整战争、继承和多地区案件尚在后续阶段。本应用不上传存档，不发送不必要的提醒。")}
   }
  }
 }
 selectedEntry?.let {e->AlertDialog(onDismissRequest={selectedEntry=null},title={Text("${e.day}日 · ${e.action}")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(e.narrative);HorizontalDivider();e.ledger.forEach {Text(it,style=MaterialTheme.typography.bodySmall)};Text("种子${e.seed} · 决策${e.revision}",style=MaterialTheme.typography.labelSmall)}},confirmButton={TextButton(onClick={selectedEntry=null}){Text("关闭")}})}
}
@Composable private fun NewDialog(close:()->Unit,start:(String,String,String,String,String,String,String,String,String)->Unit) {
 var name by rememberSaveable {mutableStateOf("承熙")};var ruler by rememberSaveable {mutableStateOf("景衡")};var era by rememberSaveable {mutableStateOf("启元")};var strategy by rememberSaveable {mutableStateOf("综合发展")}
 var setting by rememberSaveable {mutableStateOf("古代王朝")}
 var difficulty by rememberSaveable {mutableStateOf("标准")};var scale by rememberSaveable {mutableStateOf("中等国家")};var atmosphere by rememberSaveable {mutableStateOf("均势博弈")};var seed by rememberSaveable {mutableStateOf("1847")};var advanced by rememberSaveable {mutableStateOf(false)}
 AlertDialog(onDismissRequest=close,title={Text("开启新的纪年")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  OutlinedTextField(value=name,onValueChange={name=it.take(24)},label={Text("国号")},singleLine=true,modifier=Modifier.fillMaxWidth())
  OutlinedTextField(value=ruler,onValueChange={ruler=it.take(24)},label={Text("君主")},singleLine=true,modifier=Modifier.fillMaxWidth())
  OutlinedTextField(value=era,onValueChange={era=it.take(16)},label={Text("纪元名称")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Text("世界时代");Choices(listOf("古代王朝","工业初兴"),setting,{setting=it;strategy="综合发展"})
  Choices(listOf(if(setting==EraRules.ANCIENT)"商工兴国" else "工业强国","富民治国","综合发展"),strategy,{strategy=it})
  TextButton(onClick={advanced=!advanced}){Text(if(advanced)"收起开局与世界设置" else "开局与世界设置 ＋")}
  if(advanced) {
   Text("难度");Choices(listOf("宽裕","标准","艰难"),difficulty,{difficulty=it})
   Text("国家规模");Choices(listOf("超级大国","中等国家","小国","随机"),scale,{scale=it})
   Text("世界格局");Choices(listOf("和平建设","均势博弈","烽火边疆"),atmosphere,{atmosphere=it})
   OutlinedTextField(value=seed,onValueChange={seed=it.take(20)},label={Text("世界种子（整数）")},singleLine=true,modifier=Modifier.fillMaxWidth())
   TextButton(onClick={seed=java.security.SecureRandom().nextInt(1000000).toString()}){Text("随机种子")}
  }
 }},confirmButton={TextButton(onClick={start(name,ruler,strategy,difficulty,seed,era,scale,atmosphere,setting)},enabled=name.isNotBlank()&&ruler.isNotBlank()&&FamilyEngine.validName(era)&&seed.toLongOrNull()!=null){Text("建立国家")}},dismissButton={TextButton(onClick=close){Text("取消")}})
}
private fun readBoundedSave(input:java.io.InputStream):String {
 val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(32768)
 while(true) {val n=input.read(buffer);if(n<0)break;require(out.size()+n<=8*1024*1024){"文件超过8MB"};out.write(buffer,0,n)}
 return out.toString("UTF-8")
}

@Composable private fun FamilyPanel(w:World,vm:GameViewModel,ask:(Command)->Unit) {
 val h=w.household;var group by rememberSaveable {mutableStateOf("家族")};var nameGroup by rememberSaveable {mutableStateOf("君主")}
 var ruler by rememberSaveable(w.ruler){mutableStateOf(w.ruler)};var era by rememberSaveable(w.era){mutableStateOf(w.era)};var childName by rememberSaveable(h.nextChildName){mutableStateOf(h.nextChildName)}
 var memberPage by rememberSaveable {mutableIntStateOf(0)};var childPage by rememberSaveable {mutableIntStateOf(0)};var renameId by rememberSaveable {mutableIntStateOf(-1)};var renameText by rememberSaveable {mutableStateOf("")}
 Choices(listOf("家族","选妃","命名","名册"),group,{group=it})
 when(group) {
  "家族" -> Panel("宗室与家族","君主：${w.ruler} · 纪元：${w.era}") {
   Row {Switch(checked=h.allowPregnancy,onCheckedChange={ask(Command("FAMILY_PLAN",target=if(it)"允许" else "暂停"))});Text(" 允许新的自愿备孕",Modifier.padding(top=12.dp))}
   Text("仅成年、自愿且健康与关系条件合适的伴侣可备孕。暂停不改变已有孕期。",style=MaterialTheme.typography.bodySmall)
   h.pregnancy?.let {Text("还需${it.remainingDays}日 · 预备名${it.childName}",color=MaterialTheme.colorScheme.primary)}?:Text("当前没有登记孕期")
   ActionButton(Command("TRY_HEIR"),ask);ActionButton(Command("FAMILY_TIME"),ask)
  }
  "选妃" -> ConsortPanel(w,vm,ask)
  "命名" -> Panel("自主命名","旧历史保留，新的姓名与纪元从登记后生效") {
   Choices(listOf("君主","纪元","子女"),nameGroup,{nameGroup=it})
   when(nameGroup) {
    "君主" -> {OutlinedTextField(value=ruler,onValueChange={ruler=it.take(16)},label={Text("君主姓名")},singleLine=true,modifier=Modifier.fillMaxWidth());TextButton(onClick={ask(Command("RULER_NAME",target=ruler))},enabled=FamilyEngine.validName(ruler)){Text("登记新姓名")}}
    "纪元" -> {OutlinedTextField(value=era,onValueChange={era=it.take(16)},label={Text("纪元")},singleLine=true,modifier=Modifier.fillMaxWidth());TextButton(onClick={ask(Command("ERA",target=era))},enabled=FamilyEngine.validName(era)){Text("确认改元")}}
    else -> {
     OutlinedTextField(value=childName,onValueChange={childName=it.take(16)},label={Text("下一次备孕的预备名")},singleLine=true,modifier=Modifier.fillMaxWidth());TextButton(onClick={ask(Command("CHILD_NAME_PLAN",target=childName))},enabled=FamilyEngine.validName(childName)){Text("保存预备名")}
     val children=h.members.filter {it.role=="子女" && it.alive}
     if(children.isEmpty())Text("暂无在世子女")
     children.drop(childPage*2).take(2).forEach {p->Row(verticalAlignment=Alignment.CenterVertically){Text("${p.name} · ${p.ageAt(w.day)}岁",Modifier.weight(1f));TextButton(onClick={renameId=p.id;renameText=p.name}){Text("更名")}}}
     if(children.size>2)Row {TextButton(onClick={childPage--},enabled=childPage>0){Text("上一页")};TextButton(onClick={childPage++},enabled=(childPage+1)*2<children.size){Text("下一页")}}
    }
   }
  }
  else -> Panel("家族名册","每页3名，保留已故成员记录") {
   h.members.drop(memberPage*3).take(3).forEach {p->Text("${p.name} · ${p.role} · ${p.ageAt(w.day)}岁 · ${if(p.alive) "在世" else "已故"}");Text("关系${p.relationship} / 健康${p.health}",style=MaterialTheme.typography.bodySmall);TextButton(onClick={vm.openFamilyChat(p.id)}){Text("与${p.name}交谈")};HorizontalDivider()}
   Row {TextButton(onClick={memberPage--},enabled=memberPage>0){Text("上一页")};TextButton(onClick={memberPage++},enabled=(memberPage+1)*3<h.members.size){Text("下一页")}}
  }
 }
 if(renameId>=0)AlertDialog(onDismissRequest={renameId=-1},title={Text("子女更名")},text={OutlinedTextField(value=renameText,onValueChange={renameText=it.take(16)},label={Text("子女姓名")},singleLine=true)},confirmButton={TextButton(onClick={ask(Command("NAME_CHILD",target="$renameId|$renameText"));renameId=-1},enabled=FamilyEngine.validName(renameText)){Text("预览更名")}},dismissButton={TextButton(onClick={renameId=-1}){Text("取消")}})
}
