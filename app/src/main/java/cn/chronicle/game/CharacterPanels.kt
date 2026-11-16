package cn.chronicle.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.chronicle.core.*

@Composable fun CharacterChat(vm:GameViewModel,w:World){
 val chat by vm.chat.collectAsState();val model by vm.models.state.collectAsState()
 val p=w.people.find {it.id==chat.person}?:return
 var text by rememberSaveable(p.id){mutableStateOf("")};var approach by rememberSaveable(p.id){mutableStateOf("倾听")};var formal by rememberSaveable(p.id){mutableStateOf(false)}
 val list=rememberLazyListState()
 LaunchedEffect(chat.records.size,chat.partial){if(chat.records.isNotEmpty())list.animateScrollToItem(chat.records.size-1)}
 Dialog(onDismissRequest={vm.closeChat()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=true)){
  Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().imePadding().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Row {Text("与${p.name}交谈",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick={vm.closeChat()}){Text("关闭对话")}}
   Text("${p.role} · ${DialogueEngine.identity(w,p)} · ${if(model.loaded)"本地 AI"else"规则模板"}",style=MaterialTheme.typography.labelSmall)
   Text("信任${p.trust} / 忠诚${p.loyalty} / 怀疑${p.suspicion}",style=MaterialTheme.typography.labelSmall)
   HorizontalDivider()
   LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=list,verticalArrangement=Arrangement.spacedBy(10.dp)){
    if(chat.records.isEmpty())item {Text("可以询问近况、政策或求学意愿。正式调查、派学和任免需要单独确认。")}
    itemsIndexed(chat.records){_,d->Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=if(d.role=="user")MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)){Column(Modifier.padding(12.dp)){Text(if(d.role=="user")"你 · 第${d.day}日" else "${p.name} · ${if(d.source=="model")"本地模型"else"规则模板"}",style=MaterialTheme.typography.labelSmall);Text(d.text)}}}
    if(chat.partial.isNotBlank())item {Text(chat.partial)}
   }
   Text(chat.note,maxLines=3,style=MaterialTheme.typography.labelSmall)
   if(DialogueEngine.access(w,p.id)==null){
    Row {Checkbox(checked=formal,onCheckedChange={formal=it},enabled=!chat.working);Text("正式交流：2万两 / 1日 / 30日冷却",Modifier.padding(top=12.dp),style=MaterialTheme.typography.labelSmall)}
    if(formal)Row {DialogueEngine.approaches.take(3).forEach {a->FilterChip(selected=a==approach,onClick={approach=a},label={Text(a)})}}
    OutlinedTextField(text,{text=it.take(400)},modifier=Modifier.fillMaxWidth().heightIn(max=120.dp),label={Text("说些什么…")},enabled=!chat.working,maxLines=3)
    Row {if(chat.working)OutlinedButton(onClick={vm.cancelAi()}){Text("取消生成")}else Button(onClick={vm.sendChat(text,approach,formal);text=""},enabled=text.isNotBlank()){Text(if(formal)"确认交流并发送"else"发送对话")}}
   }
  }}
 }
}

@Composable fun AcademyPanel(w:World,ask:(Command)->Unit){
 var section by rememberSaveable {mutableStateOf("学院")};var person by rememberSaveable {mutableIntStateOf(1)};var destination by rememberSaveable {mutableIntStateOf(-1)}
 Row {listOf("学院","派学","在读").forEach {t->FilterChip(selected=t==section,onClick={section=t},label={Text(t)})}}
 when(section){
  "学院"->{Text("国家学院 · ${w.academy.level}级",style=MaterialTheme.typography.titleLarge);Text("学额${w.academy.level}，每季维护${w.academy.level*2}万两。学习收益按每日实际学院等级与讲席能力累计。")
   val tutor=EducationEngine.teacher(w);Text("现任讲席：${tutor?.let {"${it.name} · ${EducationEngine.grade(it)}级"}?:"缺席，国内授课暂停"}")
   if(w.academy.level<5)OutlinedButton(onClick={ask(Command("ACADEMY_UPGRADE"))}){Text("扩建学院 · 120万两 / 180日")}
   var appoint by rememberSaveable {mutableStateOf(false)};TextButton(onClick={appoint=!appoint}){Text("更换讲席")}
   if(appoint)w.people.filter {it.alive&&it.ability>=40&&!EducationEngine.busy(w,it.id)}.forEach {p->TextButton(onClick={ask(Command("APPOINT_TUTOR",target=p.id.toString()))}){Text("聘${p.name} · 师资${EducationEngine.grade(p)}级 · 10万两")}}
  }
  "派学"->{val candidates=w.people.filter {it.alive&&it.ageQuarters>=72};val p=candidates.firstOrNull {it.id==person}?:candidates.firstOrNull()
   if(p!=null){Text("派学人物：${p.name} · 能力${p.ability}");Row {TextButton(onClick={val i=candidates.indexOf(p);person=candidates[(i+candidates.size-1)%candidates.size].id}){Text("上一位")};TextButton(onClick={person=candidates[(candidates.indexOf(p)+1)%candidates.size].id}){Text("下一位")}}
    Column {FilterChip(selected=destination<0,onClick={destination=-1},label={Text("国家学院 · ${w.academy.level}级")});w.neighbors.forEachIndexed {i,n->FilterChip(selected=destination==i,onClick={destination=i},label={Text("${n.name} · 学宫${n.academyLevel}级 / 师资${n.teacherLevel}级")})}}
    Text("完整学期预计能力+${EducationEngine.expected(w,destination)}，受100上限限制；途中不授课，师资缺席或邦交恶化会影响学业。")
    Text("国内：7+90+7日，50万两；国外：往返各${14+destination.coerceAtLeast(0)*7}日，研习180日，140万两。")
    OutlinedButton(onClick={ask(Command(if(destination<0)"STUDY_HOME"else"STUDY_ABROAD",region=destination.coerceAtLeast(0),target=p.id.toString()))}){Text("预览派学")}
   }
  }
  else->{if(w.studies.isEmpty())Text("暂无在读人物。")
   w.studies.forEach {s->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("${w.people.first {it.id==s.person}.name} · ${if(s.destination<0)"国家学院"else w.neighbors[s.destination].name}");Text("${s.phase} · 行旅剩余${s.travel}日 · 课业剩余${s.remaining}日");TextButton(onClick={ask(Command("RECALL_STUDENT",target=s.person.toString()))},enabled=s.phase!="返程"){Text("召回 · 按实际课业结算")}}}}
  }
 }
}
