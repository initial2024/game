package cn.chronicle.game

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.chronicle.core.*

@Composable fun PalacePanel(w:World,ask:(Command)->Unit){
 var selected by rememberSaveable {mutableStateOf("PALACE")}
 Text("宫廷营建",style=MaterialTheme.typography.titleLarge)
 Row {CrownEngine.buildings.forEach {b->FilterChip(selected=selected==b.id,onClick={selected=b.id},label={Text(b.name)})}}
 val b=CrownEngine.buildings.first {it.id==selected};val level=w.estate.levels.getValue(b.id)
 Text("${b.name} · ${level}级（最高5级）");Text(b.effect)
 w.estate.work?.let {Text("在建：${CrownEngine.buildings.first {b->b.id==it.kind}.name}${it.level}级 · 剩余${it.remainingDays}日")}
 if(level<5){val c=Command("PALACE_BUILD",objective=b.id,level=level+1);Text(RealtimeEngine.describe(c));Button(onClick={ask(c)},enabled=w.estate.work==null){Text("预览营建${b.name}")}}
 Text("宫廷新增维护、扩编军费及新增伴侣照护共${CrownEngine.maintenance(w)}万两/季，纳入财政账簿。工程未竣工不提前获得效果。",style=MaterialTheme.typography.bodySmall)
}
@Composable fun ConsortPanel(w:World,vm:GameViewModel,ask:(Command)->Unit){
 Text("宫廷选妃",style=MaterialTheme.typography.titleLarge)
 Text("礼官面见成年自荐人士，各自记录意愿。双方自愿后才可登记为伴侣；不自动开启备孕。")
 OutlinedButton(onClick={ask(Command("CONSORT_AUDIENCE"))}){Text("安排面见 · 25万两 / 30日")}
 w.household.members.filter {it.alive&&it.role=="待选伴侣"}.forEach {p->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("${p.name} · ${p.ageAt(w.day)}岁");Text("${if(p.willing)"自愿交往"else"婉拒入宫"} · 关系${p.relationship}");TextButton(onClick={vm.openFamilyChat(p.id)}){Text("与${p.name}交谈")};TextButton(onClick={ask(Command("CONSORT_DECLINE",target=p.id.toString()))}){Text("尊重意愿，结束相看")};if(p.willing)TextButton(onClick={ask(Command("CONSORT_ACCEPT",target=p.id.toString()))}){Text("预览登记伴侣 · 15万两 / 7日")}}}}
 if(w.household.members.none {it.alive&&it.role=="待选伴侣"})Text("暂无待选人士。")
}
@Composable fun ArmyPanel(w:World,ask:(Command)->Unit){
 var section by rememberSaveable {mutableStateOf("编制")}
 var chooseLeader by remember {mutableStateOf(false)}
 var selected by rememberSaveable {mutableStateOf("north")};var query by rememberSaveable {mutableStateOf("")}
 val units=CrownEngine.units(w);val u=units.first {it.id==selected};val leader=w.people.find {it.id==u.commander&&it.alive}
 Text("军队与将领",style=MaterialTheme.typography.titleLarge)
 Row{listOf("编制","战事").forEach {s->FilterChip(section==s,{section=s},label={Text(s)})}}
 if(section=="战事"){WarPanel(w,ask);return}
 FlowRow{units.forEach {a->FilterChip(selected=a.id==selected,onClick={selected=a.id},label={Text(a.name)})}}
 Text("${u.name} · 驻${w.regions[u.region].name} · ${u.soldiers}人")
 Text("主将：${leader?.let {it.name+if(EducationEngine.busy(w,it.id))"（求学离岗）"else" · 能力${it.ability}"}?:"暂缺"}")
 Text("训练${u.training} / 士气${u.morale} / 补给${u.supply} · 编制战力${CrownEngine.power(w,u)}")
 Text("编制管理与战略战役相互关联；选择“战事”查看宣战、交战与停战。",style=MaterialTheme.typography.bodySmall)
 Row {OutlinedButton(onClick={ask(Command("ARMY_TRAIN",target=u.id))}){Text("训练 · 40万两")};OutlinedButton(onClick={ask(Command("ARMY_RECRUIT",target=u.id))}){Text("募兵千人 · 35万两")}}
 Text("训练与募兵30日；每新增千人维护2万两/季，募兵民心-1。费用与工期均需确认。",style=MaterialTheme.typography.bodySmall)
 Text("调防目的地");FlowRow{w.regions.filter {it.id!=u.region}.forEach {r->TextButton(onClick={ask(Command("ARMY_MOVE",region=r.id,target=u.id))}){Text(r.name)}}}
 OutlinedButton(onClick={chooseLeader=true}){Text("点选可用将领")}
 if(chooseLeader)PersonPicker(w,"选择可用将领",{p->CrownEngine.validate(w,Command("ARMY_ASSIGN",target=u.id,objective=p.id.toString()))==null},{chooseLeader=false}){ask(Command("ARMY_ASSIGN",target=u.id,objective=it.toString()))}
 OutlinedTextField(query,{query=it.take(30)},label={Text("搜索拟任将领姓名")},singleLine=true,modifier=Modifier.fillMaxWidth())
 if(query.isNotBlank())w.people.filter {it.alive&&it.name.contains(query)}.take(3).forEach {p->TextButton(onClick={ask(Command("ARMY_ASSIGN",target=u.id,objective=p.id.toString()))}){Text("任命${p.name} · 能力${p.ability} · 15万两 / 7日")}}
}
