package cn.chronicle.game

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.chronicle.core.*

@Composable fun LocalDefensePanel(w:World,ask:(Command)->Unit,chat:(Int)->Unit,home:String?=null){
 var kind by rememberSaveable(home){mutableStateOf(if(home==null)"州军"else w.localDefense.units.firstOrNull {it.home==home&&it.kind!="州军"}?.kind?:"县兵")};var selected by rememberSaveable(home){mutableStateOf("")};var section by rememberSaveable {mutableStateOf("兵籍")};var number by rememberSaveable {mutableIntStateOf(50)};var army by rememberSaveable {mutableStateOf("north")}
 var chooseHome by remember {mutableStateOf(false)};var role by remember {mutableStateOf<String?>(null)}
 val units=w.localDefense.units.filter {if(home!=null)it.home==home&&it.kind!="州军"else it.kind==kind};val u=units.find {it.id==selected}?:units.firstOrNull()
 if(home==null){Text("州军、府卫与县兵",style=MaterialTheme.typography.titleLarge);FlowRow{listOf("州军","府卫","县兵","卫营").forEach {s->FilterChip(kind==s,{kind=s;selected=""},label={Text(s)})}};OutlinedButton(onClick={chooseHome=true}){Text("点选驻军府县")}}
 if(u==null){Text("此地尚无登记兵籍。");return}
 val index=units.indexOf(u)
 Row{TextButton(onClick={selected=units[(index+units.size-1)%units.size].id},enabled=units.size>1){Text("上一军")};Text(u.name,Modifier.weight(1f).padding(top=12.dp));TextButton(onClick={selected=units[(index+1)%units.size].id},enabled=units.size>1){Text("下一军")}}
 Row{listOf("兵籍","军官").forEach {s->FilterChip(section==s,{section=s},label={Text(s)})}}
 if(section=="兵籍"){
  Text("驻地${w.towns.first {it.id==u.home}.name} · ${u.soldiers}人");Text("训练${u.training} / 士气${u.morale} / 补给${u.supply}");Text("军饷${u.soldiers*2}两/季 · 欠饷${PayrollEngine.owed(w,"local:${u.id}")}两",style=MaterialTheme.typography.bodySmall)
  FlowRow{listOf(50,100,200,500).forEach {n->FilterChip(number==n,{number=n},label={Text("${n}人")})}}
  Row{OutlinedButton(onClick={ask(Command("LOCAL_TRAIN",target=u.id))}){Text("操练 · 10万两")};OutlinedButton(onClick={ask(Command("LOCAL_RECRUIT",target=u.id,level=number))}){Text("招募${number}人")}}
  TextButton(onClick={ask(Command("LOCAL_DEMOB",target=u.id,level=number))},enabled=u.soldiers>=number){Text("裁减${number}人 · 无拨款")}
  Text("征调至中央军，驻地须至少留50人")
  FlowRow{CrownEngine.units(w).forEach {a->FilterChip(army==a.id,{army=a.id},label={Text(a.name)})}}
  OutlinedButton(onClick={ask(Command("LOCAL_MOBILIZE",target=u.id,objective=army,level=number))}){Text("预览征调${number}人")}
 }else{
  val posts=buildList {add("军官" to u.commander);if(u.kind=="州军")add("州官" to u.governor)}
  for((post,id)in posts){val p=w.people.find {it.id==id&&it.alive};OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
   Text(if(post=="州官")"州刺史"else if(u.kind=="县兵")"县尉"else"军队统领",style=MaterialTheme.typography.titleMedium)
   if(p==null)Text("暂缺，可任用合适人才。")else{Text("${p.name} · 能力${p.ability} · 忠诚${p.loyalty}");Text("俸禄${PayrollEngine.salary(w,p.id)}两/季 · 欠俸${PayrollEngine.owed(w,"person:${p.id}")}两",style=MaterialTheme.typography.bodySmall);TextButton(onClick={chat(p.id)}){Text("与${p.name}交谈")};TextButton(onClick={ask(Command("LOCAL_RELEASE",target=u.id,objective=p.id.toString()))}){Text("交卸职务")}}
   OutlinedButton(onClick={role=post}){Text(if(post=="州官")"点选州刺史"else"点选军官")}
  }}}
 }
 if(chooseHome)TownPicker(w,{chooseHome=false}){id->w.localDefense.units.find {it.home==id&&it.kind!="州军"}?.let {kind=it.kind;selected=it.id}}
 role?.let {post->val occupied=remember(w.people,w.towns,w.armies,w.intelligence,w.studies,w.academy){buildSet {addAll(0..2);w.towns.forEach {t->add(MunicipalEngine.officer(w,t)?.id?:-1);add(t.deputy);add(t.clerk)};CrownEngine.units(w).forEach {add(it.commander)};w.intelligence.organizations.forEach {addAll(it.members)};w.studies.forEach {add(it.person)};add(w.academy.tutor);w.journey?.let {add(it.companion);add(it.regent)}}}
  PersonPicker(w,if(post=="州官")"选择州刺史"else"选择地方军官",{p->p.alive&&p.ageQuarters>=72&&p.ability>=(if(post=="州官")60 else 50)&&p.id !in occupied},{role=null}){ask(Command("LOCAL_APPOINT",target=u.id,objective=it.toString(),mode=post))}
 }
}
