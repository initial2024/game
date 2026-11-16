package cn.chronicle.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.chronicle.core.*

@Composable fun TownPicker(w:World,close:()->Unit,pick:(String)->Unit){
 var area by remember {mutableIntStateOf(0)}
 AlertDialog(onDismissRequest=close,title={Text("选择州府县")},text={Column(Modifier.heightIn(max=470.dp).verticalScroll(rememberScrollState())){FlowRow{w.regions.forEach {r->FilterChip(area==r.id,{area=r.id},label={Text(r.name)})}};CountyBrowser(w,area,{pick(it);close()},"选定此地")}},confirmButton={TextButton(onClick=close){Text("关闭选择")}})
}
@Composable fun PersonPicker(w:World,title:String="选择人物",eligible:(Person)->Boolean={it.alive},close:()->Unit,pick:(Int)->Unit){
 var query by remember {mutableStateOf("")};var page by remember {mutableIntStateOf(0)}
 val people=w.people.filter {eligible(it)&&(query.isBlank()||it.name.contains(query)||it.role.contains(query))};val pages=maxOf(1,(people.size+2)/3);val current=page.coerceAtMost(pages-1)
 AlertDialog(onDismissRequest=close,title={Text(title)},text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(query,{query=it;page=0},label={Text("可选：搜索姓名或官职")},singleLine=true);people.drop(current*3).take(3).forEach {p->OutlinedButton(onClick={pick(p.id);close()},modifier=Modifier.fillMaxWidth()){Text("${p.name} · ${p.role}\n能力${p.ability} / 忠诚${p.loyalty}")}};if(people.isEmpty())Text("暂无符合条件的人物，可先招贤或培养人才。");Row{TextButton(onClick={page=current-1},enabled=current>0){Text("上一组")};Text("${current+1}/$pages");TextButton(onClick={page=current+1},enabled=current+1<pages){Text("下一组")}}}},confirmButton={TextButton(onClick=close){Text("关闭选择")}})
}
