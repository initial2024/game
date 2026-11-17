package cn.chronicle.game

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.chronicle.core.*

@Composable fun IntelligencePanel(w:World,ask:(Command)->Unit){
 var tab by rememberSaveable {mutableStateOf("总览")};var kind by rememberSaveable {mutableStateOf("谍报")};var name by rememberSaveable {mutableStateOf("谍报司")};var home by rememberSaveable {mutableStateOf(w.towns.firstOrNull()?.id?:"")};var selected by rememberSaveable {mutableStateOf("")};var country by rememberSaveable {mutableIntStateOf(0)}
 var pickHome by remember {mutableStateOf(false)};var pickMember by remember {mutableStateOf(false)};var pickTarget by remember {mutableStateOf(false)}
 val state=w.intelligence;val org=state.organizations.find {it.id==selected}?:state.organizations.firstOrNull()
 Text("组织与谍报",style=MaterialTheme.typography.titleLarge)
 FlowRow{listOf("总览","筹建","行动","归报").forEach {s->FilterChip(tab==s,{tab=s},label={Text(s)})}}
 Text("渗透威胁${state.threat}/100 · 组织维护${IntelligenceEngine.upkeep(w)}万两/季",style=MaterialTheme.typography.bodySmall)
 if(tab!="筹建")FlowRow{state.organizations.forEach {o->FilterChip(org?.id==o.id,{selected=o.id},label={Text(o.name)})}}
 when(tab){
  "筹建"->{FlowRow{IntelligenceEngine.kinds.forEach {s->FilterChip(kind==s,{kind=s;name=when(s){"监察"->"御史台";"谍报"->"谍报司";else->"隐锋阁"}},label={Text(s)})}}
   OutlinedTextField(name,{name=it.take(16)},label={Text("组织名称")},singleLine=true,modifier=Modifier.fillMaxWidth())
   Text("驻地：${w.towns.find {it.id==home}?.let {MunicipalEngine.name(w,it)}?:"未选"}");OutlinedButton(onClick={pickHome=true}){Text("点选组织驻地")}
   Text("筹建90万两、45日；每季维护4万两，每位核心成员另2万两。最多4个组织，每个3人，成员须另行任用。")
   Button(onClick={ask(Command("ORG_FOUND",target=home,objective=kind,text=name))}){Text("预览筹建组织")}
  }
  "归报"->{if(state.reports.isEmpty())Text("暂无任务归报。")else state.reports.takeLast(4).reversed().forEach {r->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("第${r.day}日 · ${state.organizations.find {it.id==r.organization}?.name?:r.organization}");Text(r.text,style=MaterialTheme.typography.bodySmall)}}}}
  else->if(org==null){Text("暂无组织，先筹建部门或自建势力，再任用人才。");TextButton(onClick={tab="筹建"}){Text("进入组织筹建")}}
  else {Text("${org.name} · ${org.kind} · 暴露${org.exposure}/100")
   if(tab=="总览"){
    Text("驻地：${w.towns.first {it.id==org.home}.name}")
    org.members.forEach {id->w.people.find {it.id==id}?.let {Text("${it.name} · 能力${it.ability} · 忠诚${it.loyalty} · ${if(!it.alive)"已故"else if(EducationEngine.busy(w,id))"求学离岗"else"在岗"}")}}
    OutlinedButton(onClick={pickMember=true}){Text("点选组织成员")}
    org.members.forEach {id->TextButton(onClick={ask(Command("ORG_RELEASE",target=org.id,objective=id.toString()))}){Text("交卸${w.people.first {it.id==id}.name}的组织职务")}}
    Text("监察与谍报部门可反谍，谍报组织可经营外国网络；刺客组织的秘密暴力行动具有严重政治后果。组织成员不能兼任地方官或统军。",style=MaterialTheme.typography.bodySmall)
   }else{
    if(org.kind=="谍报"){Text("侦察目标");FlowRow{w.neighbors.forEachIndexed {i,n->FilterChip(country==i,{country=i},label={Text(n.name)})}};Text("情报网络${state.networks[country]}/100，影响后续交战的信息优势。");OutlinedButton(onClick={ask(Command("INTEL_SCOUT",region=country,target=org.id))}){Text("派遣间谍 · 35万两 / 30日")}}
    if(org.kind!="刺客")OutlinedButton(onClick={ask(Command("INTEL_COUNTER",target=org.id))}){Text("反谍排查 · 25万两 / 15日")}
    else {Text("秘密刺杀不保证成功，也不证明目标有罪。无论成败均降低民心6、稳定8，增加组织暴露与渗透威胁。",color=MaterialTheme.colorScheme.error);OutlinedButton(onClick={pickTarget=true}){Text("点选游戏内目标并预览")}}
    w.pending.filter {it.command.target==org.id}.forEach {Text("${Rules.label(it.command)} · 剩余${it.remainingDays}日")}
   }
  }
 }
 if(pickHome)TownPicker(w,{pickHome=false}){home=it}
 if(pickMember&&org!=null)PersonPicker(w,"选择组织成员",{p->IntelligenceEngine.validate(w,Command("ORG_ASSIGN",target=org.id,objective=p.id.toString()))==null},{pickMember=false}){ask(Command("ORG_ASSIGN",target=org.id,objective=it.toString()))}
 if(pickTarget&&org!=null)PersonPicker(w,"选择游戏内行动目标",{p->IntelligenceEngine.validate(w,Command("ASSASSINATE",target=org.id,objective=p.id.toString()))==null},{pickTarget=false}){ask(Command("ASSASSINATE",target=org.id,objective=it.toString()))}
}
@Composable fun WarPanel(w:World,ask:(Command)->Unit){
 var country by rememberSaveable {mutableIntStateOf(0)};var armyId by rememberSaveable {mutableStateOf("north")}
 var approach by rememberSaveable {mutableStateOf("稳战")};var envoy by rememberSaveable {mutableIntStateOf(2)};var pickEnvoy by remember {mutableStateOf(false)}
 val war=w.war
 Text("战事与民心",style=MaterialTheme.typography.titleLarge);Text("民心${w.nation.morale} / 稳定${w.nation.stability} / 国库${w.nation.treasury}万两")
 if(war==null){
  Text("出征军队");FlowRow{CrownEngine.units(w).forEach {u->FilterChip(armyId==u.id,{armyId=u.id},label={Text(u.name)})}}
  Text("目标国家");FlowRow{w.neighbors.forEachIndexed {i,n->FilterChip(country==i,{country=i},label={Text(n.name)})}}
  val n=w.neighbors[country];Text("${n.name} · 邦交${n.relation} · 军力${n.strength} · 情报网络${w.intelligence.networks[country]}")
  Text("宣战120万两/7日，完成后动员14日；民心-8、稳定-4，目标关系归零，其余国家关系-5。每30日支付40万两加每千名出征军士1万两。")
  OutlinedButton(onClick={ask(Command("DECLARE_WAR",region=country,target=armyId))}){Text("预览宣战与动员")}
 }else{
  val u=CrownEngine.units(w).first {it.id==war.army}
  Text("对${w.neighbors[war.neighbor].name}战争 · 第${war.days}日");Text("${u.name} · ${u.soldiers}人 · 补给${u.supply} · 编制战力${CrownEngine.power(w,u)}")
  Text("已交战${war.battles}次 · 优势${war.progress} · 敌战力${war.enemy} · 双方厌战${war.weariness}/${war.enemyWeariness}")
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){listOf("稳战","速攻","围困","外交").forEachIndexed {i,s->SegmentedButton(selected=approach==s,onClick={approach=s},shape=SegmentedButtonDefaults.itemShape(i,4)){Text(s)}}}
  if(approach=="外交"){
   val selected=w.people.firstOrNull {it.id==envoy&&WarfareEngine.envoyAvailable(w,it)}?:w.people.firstOrNull {WarfareEngine.envoyAvailable(w,it)}
   Text("使者：${selected?.let {"${it.name} · 能力${it.ability}"}?:"暂无合适人选"}")
   TextButton(onClick={pickEnvoy=true}){Text("点选出使人物")}
   val order=Command("WAR_ENVOY",target=selected?.id?.toString().orEmpty())
   Text(WarfareEngine.describe(w,order),style=MaterialTheme.typography.bodySmall)
   OutlinedButton(onClick={ask(order)},enabled=selected!=null&&war.days>=14){Text("预览出使议和 · 60万两 / 21日")}
  }else{
   val order=Command("WAR_BATTLE",mode=approach);val spec=WarfareEngine.approach(order)
   Text("${spec.label} · ${spec.cost}万两 / ${spec.days}日",style=MaterialTheme.typography.titleSmall)
   Text(WarfareEngine.describe(w,order),style=MaterialTheme.typography.bodySmall)
   OutlinedButton(onClick={ask(order)},enabled=war.days>=14){Text(if(war.days<14)"动员尚需${14-war.days}日"else if(approach=="稳战")"预览前线交战"else"预览${spec.label}")}
  }
  OutlinedButton(onClick={ask(Command("WAR_PEACE"))}){Text("预览停战 · 40万两 / 7日")}
  TextButton(onClick={ask(Command("WAR_WITHDRAW"))}){Text("无条件撤军 · 无拨款 / 7日")}
  Text("撤军不获领土，民心-3、稳定-3，邦交保持敌对。",style=MaterialTheme.typography.bodySmall)
 }
 Text("这是持续时间驱动的战略战役，结果会改变军队、财政、民心和地图归属。",style=MaterialTheme.typography.bodySmall)
 if(pickEnvoy)PersonPicker(w,"选择出使人物",{WarfareEngine.envoyAvailable(w,it)},{pickEnvoy=false}){envoy=it}
}
