package cn.chronicle.game

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.chronicle.core.*

@Composable fun ResourceStockPanel(w:World,ask:(Command)->Unit){
 var tab by rememberSaveable {mutableStateOf("库存")};var kind by rememberSaveable {mutableStateOf("GRAIN")};var direction by rememberSaveable {mutableStateOf("采购")};var batches by rememberSaveable {mutableIntStateOf(1)}
 FlowRow{listOf("库存","产量","贸易").forEach {s->FilterChip(tab==s,{tab=s},label={Text(s)})}}
 when(tab){
  "库存"->{Text("国家物资储备",style=MaterialTheme.typography.titleLarge);ResourceEngine.labels.forEach {(id,label)->Row {Text(label,Modifier.weight(1f));Text("${ResourceEngine.available(w,id)} ${ResourceEngine.unit(id)}")}};Text("常平仓容量 ${ResourceEngine.grainCapacity(w)}石",style=MaterialTheme.typography.bodySmall);Text("建设、募兵、出征会消耗实物；产出按季度结算，缺原料的扩建作坊会减产。")}
  "产量"->{val p=w.resources.totals;Text(if(w.resources.lastTurn<0)"首季尚未结算"else"第${w.resources.lastTurn}季物产",style=MaterialTheme.typography.titleLarge)
   listOf("粮食" to "${p.grain}石","木材" to "${p.timber}吨","铁材" to "${p.iron}吨","煤炭" to "${p.coal}吨","石料" to "${p.stone}吨","国营扩建制品" to "${p.goods}箱").forEach {(label,value)->Row{Text(label,Modifier.weight(1f));Text(value)}}
   Text("军粮供给${w.resources.militaryFoodRatio}% · 田赋折粮抵现金税${w.resources.grainTaxValueWan}万两",style=MaterialTheme.typography.bodySmall)
   Text("地力、水源、农政、灌溉与运输影响收成；矿藏会耗竭，森林缓慢再生。物产与国库资金分别记账。")
  }
  else->{Text("物资通商",style=MaterialTheme.typography.titleLarge);FlowRow{ResourceEngine.labels.forEach {(id,label)->FilterChip(kind==id,{kind=id},label={Text(label)})}}
   Row{listOf("采购","出售").forEach {s->FilterChip(direction==s,{direction=s},label={Text(s)})}}
   Row{TextButton(onClick={batches--},enabled=batches>1){Text("−")};Text("$batches 批",Modifier.padding(top=12.dp));TextButton(onClick={batches++},enabled=batches<5){Text("＋")}}
   Text("每批${ResourceEngine.lots.getValue(kind)}${ResourceEngine.unit(kind)}；采购2万两、出售收入1万两。本季剩${5-(w.resources.tradedLots[kind]?:0)}批，须有可通商邻国。")
   OutlinedButton(onClick={ask(Command("TRADE_RESOURCE",target=direction,objective=kind,level=batches))}){Text("预览${direction}物资")}
  }
 }
}
@Composable fun TerrainResourcesPanel(w:World,t:Town){
 var tab by rememberSaveable(t.id){mutableStateOf("地形")};val site=w.resources.sites[t.id];val p=TerrainEngine.profile(w,t.id)
 FlowRow{listOf("地形","矿林","产量").forEach {s->FilterChip(tab==s,{tab=s},label={Text(s)})}}
 when(tab){
  "地形"->{Text(p.kind,style=MaterialTheme.typography.titleLarge);Text("相对高差${p.height} / 起伏${p.slope} / 水源${p.water} / 地力${p.fertility}")
   val factor=TerrainEngine.constructionFactor(w,Command("URBAN_BUILD",region=t.region,target=t.id,objective="SCHOOL",level=1));Text("一般营建造价与工期：基础的${factor}%")
   Text("码头须临水，矿场须有矿源，林场须有林木。道路、码头和运河改善物产运输；险地也影响行军与防守。")
   Text("地形为当前舆图参考指标（0–100），不是实测海拔；矿林是本剧情的资源设定。",style=MaterialTheme.typography.bodySmall)
  }
  "矿林"->{Text("现存可利用资源",style=MaterialTheme.typography.titleLarge);Text("铁矿 ${site?.iron?:0}吨");Text("煤矿 ${site?.coal?:0}吨");Text("石料 ${site?.stone?:0}吨");Text("林木 ${site?.forest?:0} / ${site?.forestCapacity?:0}吨");Text("矿藏采出后永久扣减；林木受存量、容量与再生速度限制。")}
  else->{val y=w.resources.yields[t.id];Text("上季实际产量",style=MaterialTheme.typography.titleLarge);if(y==null)Text("尚未结算。")else {Text("粮食 ${y.grain}石");Text("木材${y.timber}吨 · 铁${y.iron}吨 · 煤${y.coal}吨");Text("石料${y.stone}吨 · 制品${y.goods}箱");Text("扩建作坊实际开工率${y.operation}%")};Text("产量受本地资源、地形、灌溉、建筑等级、技术与运输条件影响。")}
 }
}
@Composable fun PayrollPanel(w:World,ask:(Command)->Unit){
 var group by rememberSaveable {mutableStateOf("官俸")};var page by rememberSaveable {mutableIntStateOf(0)};val all=remember(w.people,w.towns,w.localDefense,w.armies,w.academy,w.studies){PayrollEngine.lines(w)}
 Text("俸禄与军饷",style=MaterialTheme.typography.titleLarge);Text("本季应付${all.sumOf {it.liang}}两 · 欠付${w.payroll.arrears.values.sum()}两");Text("薪资基金结余${w.payroll.reserveLiang}两 · 1万两=10000两",style=MaterialTheme.typography.bodySmall)
 Text("季度统一结算，兼职取较高俸额，不重复领两份；求学期间基本俸额八成。欠俸影响忠诚，欠饷影响士气。")
 OutlinedButton(onClick={ask(Command("PAY_ARREARS"))},enabled=w.payroll.arrears.isNotEmpty()){Text("拨1万两补发欠款")}
 Row{listOf("官俸","军饷").forEach {s->FilterChip(group==s,{group=s;page=0},label={Text(s)})}}
 val lines=all.filter {if(group=="官俸")it.key.startsWith("person:")else!it.key.startsWith("person:")};val pages=maxOf(1,(lines.size+2)/3);val current=page.coerceAtMost(pages-1)
 lines.drop(current*3).take(3).forEach {line->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("${line.name} · ${line.office}");Text("应付${line.liang}两/季 · 上季实付${w.payroll.lastPayments[line.key]?:0}两\n欠款${PayrollEngine.owed(w,line.key)}两")}}}
 Row{TextButton(onClick={page=current-1},enabled=current>0){Text("上一页")};Text("${current+1}/$pages");TextButton(onClick={page=current+1},enabled=current+1<pages){Text("下一页")}}
}
