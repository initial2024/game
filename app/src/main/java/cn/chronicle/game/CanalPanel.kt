package cn.chronicle.game

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import cn.chronicle.core.*

@Composable fun CanalPanel(w:World,ask:(Command)->Unit){
 var from by rememberSaveable {mutableStateOf(w.towns.first {it.rank=="县城"}.id)};var to by rememberSaveable {mutableStateOf(w.towns.first {it.rank=="府城"}.id)};var choosing by remember {mutableIntStateOf(0)}
 Text("运河与水运",style=MaterialTheme.typography.titleLarge)
 Text("起点：${w.towns.firstOrNull {it.id==from}?.name?:"未选"}");OutlinedButton(onClick={choosing=1}){Text("点选运河起点")}
 Text("终点：${w.towns.firstOrNull {it.id==to}?.name?:"未选"}");OutlinedButton(onClick={choosing=2}){Text("点选运河终点")}
 val command=CanalEngine.plan(w,from,to);val error=CanalEngine.validate(w,command)
 Text("预算${Rules.cost(command)}万两 · 工期${command.days}日 · 维护3万两/季")
 Text("竣工：国家基础设施+3、商业+2、农业+2；沿线城邑农业与商业各+3。人口和预算不凭空增加。地图显示水运示意连接，尚无真实水文与地形开凿模型。")
 if(error!=null)Text(error,color=MaterialTheme.colorScheme.error)
 Button(onClick={ask(command)},enabled=error==null){Text("预览运河立项")}
 w.projects.filter {it.kind=="CANAL"}.take(3).forEach {p->Text("在建：${w.towns.first {it.id==p.target}.name}—${w.towns.first {it.id==p.objective}.name} · 剩余${p.remainingDays}日")}
 Text("已竣工${w.canals.size}条运河，水运线可在世界舆图查看。")
 if(choosing!=0)TownPicker(w,{choosing=0}){id->if(choosing==1)from=id else to=id}
}
