package cn.chronicle.game

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.chronicle.core.*

@Composable fun PersonnelPanel(w:World,vm:GameViewModel,ask:(Command)->Unit){
 var chooseTown by remember {mutableStateOf(false)}
 var selected by remember {mutableStateOf<Int?>(null)}
 var tab by rememberSaveable {mutableStateOf("名册")};var query by rememberSaveable {mutableStateOf("")};var group by rememberSaveable {mutableStateOf("朝廷")};var page by rememberSaveable {mutableIntStateOf(0)}
 var candidate by rememberSaveable {mutableIntStateOf(-1)};var townId by rememberSaveable {mutableStateOf(w.towns.firstOrNull()?.id?:"")};var townQuery by rememberSaveable {mutableStateOf("")};var post by rememberSaveable {mutableStateOf("长官")}
 Row{listOf("名册","招贤","任官").forEach {s->FilterChip(selected=tab==s,onClick={tab=s},label={Text(s)})}}
 when(tab){
  "名册"->{FlowRow{listOf("朝廷","地方","将领","待选","全部").forEach {s->FilterChip(selected=group==s,onClick={group=s;page=0},label={Text(s)})}}
   OutlinedTextField(query,{query=it;page=0},label={Text("查找姓名、官职或辖区")},singleLine=true,modifier=Modifier.fillMaxWidth())
   val people=w.people.filter {p->(group=="全部"||when(group){"朝廷"->p.id<10;"地方"->TalentEngine.town(w,p.id)!=null;"将领"->CrownEngine.units(w).any {it.commander==p.id};else->p.role=="待选人才"})&&(query.isBlank()||p.name.contains(query)||p.role.contains(query))}
   val pages=maxOf(1,(people.size+2)/3);val current=page.coerceAtMost(pages-1)
   people.drop(current*3).take(3).forEach {p->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
    Text("${p.name} · ${p.role}",style=MaterialTheme.typography.titleMedium);Text("${p.ageQuarters/4}岁 · 能力${p.ability} · 忠诚${p.loyalty} · 信任${p.trust}",style=MaterialTheme.typography.bodySmall)
    Text("${p.stance} · ${p.goal}",style=MaterialTheme.typography.bodySmall)
    TextButton(onClick={vm.openChat(p.id)}){Text("与${p.name}交谈")}
    TextButton(onClick={selected=p.id}){Text("人物档案")}
    if(p.alive)TextButton(onClick={candidate=p.id;TalentEngine.town(w,p.id)?.let {townId=it.id};tab="任官"}){Text("安排官位")}
    if(p.memories.isNotEmpty())Text(p.memories.last(),maxLines=2,style=MaterialTheme.typography.labelSmall)
   }}}
   if(people.isEmpty())Text("暂无匹配人物。")
   Row{TextButton(onClick={page=(current-1).coerceAtLeast(0)},enabled=current>0){Text("上一页")};Text("${current+1} / $pages · ${people.size}人");TextButton(onClick={page=current+1},enabled=current+1<pages){Text("下一页")}}
  }
  "招贤"->{Text(if(w.setting==EraRules.ANCIENT)"科举与招贤"else"国家考试与公开招贤",style=MaterialTheme.typography.titleLarge)
   Text("科举90日、60万两，每届结束后至少360日再办；招贤30日、20万两，每轮结束后筹备30日。每次登记两名候选人才，不自动授官。")
   Text("考核参考世界种子与学院等级；能力、立场与忠诚各自记录。国家学院培养仍需另行派学。",style=MaterialTheme.typography.bodySmall)
   OutlinedButton(onClick={ask(Command("EXAM"))}){Text("筹办科举 · 60万两")}
   OutlinedButton(onClick={ask(Command("RECRUIT"))}){Text("公开招贤 · 20万两")}
   w.pending.filter {it.command.type in listOf("EXAM","RECRUIT")}.forEach {Text("${Rules.label(it.command)} · 剩余${it.remainingDays}日")}
   Text("待选人才 ${w.people.count {it.alive&&it.role=="待选人才"}} 人 / 名册 ${w.people.size} 人")
   TextButton(onClick={group="待选";page=0;tab="名册"}){Text("查看待选人才")}
  }
  else->{val p=w.people.find {it.id==candidate};val t=w.towns.find {it.id==townId}
   Text("任命与调任",style=MaterialTheme.typography.titleLarge)
   Text(p?.let {"拟任：${it.name} · 能力${it.ability} · ${it.role}"}?:"请先在名册选择人物，再点“安排官位”。")
   OutlinedButton(onClick={chooseTown=true}){Text("点选任职州府县")}
   OutlinedTextField(townQuery,{townQuery=it.take(60)},label={Text("搜索任职府县")},singleLine=true,modifier=Modifier.fillMaxWidth())
   Text("选定辖区：${t?.let {MunicipalEngine.name(w,it)}?:"未选择"}")
   w.towns.filter {townQuery.isNotBlank()&&(MunicipalEngine.name(w,it).contains(townQuery)||MunicipalEngine.prefectureName(it).contains(townQuery))}.take(3).forEach {place->TextButton(onClick={townId=place.id;townQuery=""}){Text("选择${MunicipalEngine.name(w,place)} · ${place.rank}")}}
   Row{TalentEngine.posts.forEach {s->FilterChip(selected=post==s,onClick={post=s},label={Text(t?.let {TalentEngine.role(it,s)}?:s)})}}
   Text("长官能力须≥50，属官≥35。任官25万两、7日，届时重新检查在世、求学与现任职务；调任腾出原职位，前任转入待选名册。行政调任不判定有罪。",style=MaterialTheme.typography.bodySmall)
   Button(onClick={if(p!=null&&t!=null)ask(Command("APPOINT",region=t.region,target=p.id.toString(),objective=t.id,mode=post))},enabled=p!=null&&t!=null){Text("预览任命")}
  }
 }
 if(chooseTown)TownPicker(w,{chooseTown=false}){townId=it;townQuery=""}
 selected?.let {id->w.people.find {it.id==id}?.let {p->AlertDialog(onDismissRequest={selected=null},title={Text("${p.name} · ${p.role}")},text={Column {Text("能力${p.ability} / 忠诚${p.loyalty} / 信任${p.trust} / 怀疑${p.suspicion} / 敌意${p.hostility}");p.memories.takeLast(4).forEach {Text(it,style=MaterialTheme.typography.bodySmall)}}},confirmButton={TextButton(onClick={selected=null}){Text("关闭档案")}})}}
}
