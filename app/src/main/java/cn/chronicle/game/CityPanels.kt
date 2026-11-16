package cn.chronicle.game

import androidx.compose.foundation.layout.*
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

private fun townSeedsForUi(w:World,t:Town)=AdministrativeEngine.seeds.firstOrNull {it.prefecture==t.prefecture}?.let {AdministrativeEngine.state(it)}

@Composable fun CountyBrowser(w:World,region:Int,onCity:(String)->Unit,selectLabel:String="城市情况与建筑"){
 val towns=MunicipalEngine.all(w).filter {it.region==region};val prefs=towns.filter {it.rank=="府城"}
 var state by rememberSaveable(region){mutableStateOf("")}
 var pref by rememberSaveable(region,state){mutableStateOf("")};var page by rememberSaveable(region,pref){mutableIntStateOf(0)}
 val states=prefs.mapNotNull {t->townSeedsForUi(w,t)}.distinct()
 FlowRow{FilterChip(state.isEmpty(),{state=""},label={Text("全州")});states.forEach {id->FilterChip(state==id,{state=id},label={Text(AdministrativeEngine.stateName(w,id))})}}
 Text("${w.regions[region].name}大区 · ${prefs.size}府 · ${towns.count {it.rank=="县城"}}县",style=MaterialTheme.typography.titleMedium)
 FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(pref.isEmpty(),{pref=""},label={Text("全部")});prefs.filter {state.isEmpty()||townSeedsForUi(w,it)==state}.forEach {p->FilterChip(pref==p.prefecture,{pref=p.prefecture},label={Text(MunicipalEngine.prefectureName(p))})}}
 val filtered=towns.filter {(pref.isEmpty()||it.prefecture==pref)&&(state.isEmpty()||townSeedsForUi(w,it)==state)}
 filtered.drop(page*3).take(3).forEach {t->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
  Text("${MunicipalEngine.name(w,t)} · ${t.rank}",style=MaterialTheme.typography.titleMedium)
  Text("人口${MunicipalEngine.population(w,t)} · 地方官${MunicipalEngine.officer(w,t)?.name?:"暂缺"}",style=MaterialTheme.typography.labelSmall)
  TextButton(onClick={onCity(t.id)}){Text(selectLabel)}
 }}}
 Row{TextButton(onClick={page--},enabled=page>0){Text("上一页")};Text("${page+1} / ${maxOf(1,(filtered.size+2)/3)}",Modifier.padding(top=12.dp));TextButton(onClick={page++},enabled=(page+1)*3<filtered.size){Text("下一页")}}
}

@Composable fun CityDialog(w:World,id:String,close:()->Unit,ask:(Command)->Unit,chat:(Int)->Unit){
 val t=MunicipalEngine.all(w).find {it.id==id}?:return
 var tab by rememberSaveable(id){mutableStateOf("概况")};var category by rememberSaveable(id){mutableStateOf("民生")};var rename by rememberSaveable(id){mutableStateOf(MunicipalEngine.name(w,t))}
 val stats=MunicipalEngine.stats(t)
 Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=true)){
  Surface(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Row{Column(Modifier.weight(1f)){Text(MunicipalEngine.name(w,t),style=MaterialTheme.typography.headlineSmall);Text("${w.regions[t.region].name}大区 / ${MunicipalEngine.prefectureName(t)} / ${t.rank}",style=MaterialTheme.typography.labelSmall)};TextButton(onClick=close){Text("关闭城邑")}}
   SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){listOf("概况","建筑","官员","兵籍","资源").forEachIndexed {i,s->SegmentedButton(selected=tab==s,onClick={tab=s},shape=SegmentedButtonDefaults.itemShape(i,5)){Text(s)}}}
   Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
    when(tab){
     "概况"->{Text("人口 ${MunicipalEngine.population(w,t)} 人",style=MaterialTheme.typography.titleMedium)
      Text("本地预算份额 ${MunicipalEngine.share(w,t,w.regions[t.region].funds)} 万两 · 建设由国库拨款",style=MaterialTheme.typography.bodySmall)
      val annual=w.nation.income*w.regions[t.region].population/w.nation.population
      Text(if(w.nation.income==0L)"年度税收份额：首季结算后显示"else"年度税收份额 ${MunicipalEngine.share(w,t,annual)} 万两（已计入国家财政）",style=MaterialTheme.typography.bodySmall)
      stats.forEach {(name,value)->Column{Row{Text(name,Modifier.weight(1f));Text(value.toString())};LinearProgressIndicator(progress={value/100f},modifier=Modifier.fillMaxWidth())}}
      Text("交通：${if(t.buildings.getValue("ROAD")>=2)"驿路已扩建"else"基础驿路"} · 常平仓容量 ${t.buildings.getValue("GRANARY")*20000} 石",style=MaterialTheme.typography.bodySmall)
     }
     "建筑"->{FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("民生","经济","治理","资源","交通","农水").forEach {s->FilterChip(selected=s==category,onClick={category=s},label={Text(s)})}}
      t.work?.let {work->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("在建：${MunicipalEngine.buildings.first {it.id==work.kind}.name}${work.level}级");Text("剩余${work.remainingDays}日 · 已拨款${work.paid}万两");Text("继续游戏时间后自动施工，未竣工前不提前生效。",style=MaterialTheme.typography.labelSmall)}}}
      MunicipalEngine.buildings.filter {it.category==category}.forEach {b->val level=t.buildings.getValue(b.id)
       OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("${b.name} · ${level}级",style=MaterialTheme.typography.titleMedium);Text(b.effect,style=MaterialTheme.typography.bodySmall)
        if(level<5){val c=Command("URBAN_BUILD",region=t.region,target=t.id,objective=b.id,level=level+1)
         Text("工期${MunicipalEngine.duration(w,c)}日 · ${Rules.cost(w,c)}万两",style=MaterialTheme.typography.labelSmall)
         Button(onClick={ask(c)},enabled=t.work==null){Text("升级${b.name}至${level+1}级")}
        }else Text("已达最高等级")
       }}
      }
     }
     "兵籍"->LocalDefensePanel(w,ask,chat,t.id)
     "资源"->TerrainResourcesPanel(w,t)
     else->{OutlinedTextField(rename,{rename=it.take(16)},label={Text("城邑新名称")},singleLine=true);TextButton(onClick={ask(Command("CITY_RENAME",region=t.region,target=t.id,objective=rename))}){Text("预览更名 · 5万两")}
      val p=MunicipalEngine.officer(w,t)
      if(p==null)Text("地方职位暂缺。旧任者的经历仍保存在人物档案。")else{Text("${p.name} · ${p.role}",style=MaterialTheme.typography.titleMedium);Text("${p.ageQuarters/4}岁 · 能力${p.ability} · 忠诚${p.loyalty}");Text("立场：${p.stance}；目标：${p.goal}");Text("俸禄${PayrollEngine.salary(w,p.id)}两/季 · 欠俸${PayrollEngine.owed(w,"person:${p.id}")}两",style=MaterialTheme.typography.bodySmall);TextButton(onClick={close();chat(p.id)}){Text("与地方官交谈")}}
      listOf("副官" to t.deputy,"主簿" to t.clerk).forEach {(post,id)->val aide=w.people.find {it.id==id&&it.alive}
       if(aide==null)Text("${TalentEngine.role(t,post)}：暂缺")else OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("${aide.name} · ${TalentEngine.role(t,post)}");Text("能力${aide.ability} · 忠诚${aide.loyalty} · ${aide.goal}",style=MaterialTheme.typography.bodySmall);TextButton(onClick={close();chat(aide.id)}){Text("与${aide.name}交谈")}}}
      }
      OutlinedButton(onClick={ask(Command("URBAN_AUDIT",region=t.region,target=t.id))}){Text("地方财政审计 · 15万两 / 5日")}
      OutlinedButton(onClick={ask(Command("URBAN_RELIEF",region=t.region,target=t.id))}){Text("地方赈济 · 20万两 / 5日")}
      Text("审计可改善财政记录与腐败情况，不能仅凭数值认定官员有罪。",style=MaterialTheme.typography.bodySmall)
     }
    }
   }
  }}
 }
}
