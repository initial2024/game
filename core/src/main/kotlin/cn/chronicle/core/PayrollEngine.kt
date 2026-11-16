package cn.chronicle.core

import kotlinx.serialization.Serializable

@Serializable data class PayrollState(val reserveLiang:Long=0,val arrears:Map<String,Long> = emptyMap(),val lastTurn:Int=-1,val lastPayments:Map<String,Long> = emptyMap())
data class SalaryLine(val key:String,val name:String,val office:String,val liang:Long)
object PayrollEngine {
 fun lines(w:World):List<SalaryLine> {
  val people=w.people.associateBy {it.id}
  val duties=linkedMapOf<Int,Pair<String,Long>>()
  fun post(id:Int,title:String,pay:Long){if(id<0||people[id]?.alive!=true)return;val old=duties[id];if(old==null||old.second<pay)duties[id]=title to pay}
  post(0,"首辅",400);post(1,"边军统领",350);post(2,"监察使",300)
  for(t in w.towns){post(MunicipalEngine.officer(w,t)?.id?:-1,if(t.rank=="县城")"县令"else"地方长官",if(t.rank=="县城")120 else 200);post(t.deputy,"副官",90);post(t.clerk,"主簿",70)}
  for(u in w.localDefense.units){post(u.commander,if(u.kind=="州军")"州统领"else"地方军官",if(u.kind=="州军")180 else 100);post(u.governor,"州刺史",260)}
  for(u in CrownEngine.units(w))post(u.commander,"中央军主将",350)
  post(w.academy.tutor,"国家学院讲席",150)
  return duties.map {(id,d)->SalaryLine("person:$id",people.getValue(id).name,d.first,if(EducationEngine.busy(w,id))d.second*4/5 else d.second)}+CrownEngine.units(w).map {SalaryLine("central:${it.id}",it.name,"军饷",it.soldiers*2L)}+w.localDefense.units.map {SalaryLine("local:${it.id}",it.name,"军饷",it.soldiers*2L)}
 }
 fun salary(w:World,person:Int)=lines(w).find {it.key=="person:$person"}?.liang?:0L
 fun owed(w:World,key:String)=w.payroll.arrears[key]?:0L
 fun quarter(w:World,ledger:MutableList<String>):World {
  if(w.payroll.lastTurn==w.turn)return w
  val lines=lines(w);val need=lines.sumOf {it.liang};val required=((need-w.payroll.reserveLiang).coerceAtLeast(0)+9999)/10000
  val borrow=minOf((required-w.nation.treasury).coerceAtLeast(0),(2000-w.nation.debt).coerceAtLeast(0));val paidWan=minOf(required,w.nation.treasury+borrow);val fund=w.payroll.reserveLiang+paidWan*10000
  val arrears=w.payroll.arrears.toMutableMap();val missed=mutableSetOf<String>();val payments=linkedMapOf<String,Long>();var spent=0L
  for(line in lines){val paid=if(need==0L)0 else minOf(line.liang,fund*line.liang/need);spent+=paid;payments[line.key]=paid;val unpaid=line.liang-paid;if(unpaid>0){arrears[line.key]=((arrears[line.key]?:0)+unpaid).coerceAtMost(1000000000000L);missed+=line.key}}
  val next=w.copy(nation=w.nation.copy(treasury=w.nation.treasury+borrow-paidWan,debt=w.nation.debt+borrow,expense=w.nation.expense+required*4,stability=(w.nation.stability-if(missed.isNotEmpty())2 else 0).coerceAtLeast(0)),payroll=PayrollState(fund-spent,arrears,w.turn,payments),people=w.people.map {if("person:${it.id}" in missed)it.copy(loyalty=(it.loyalty-3).coerceAtLeast(0),memories=(it.memories+"${w.day}日出现欠俸，忠诚-3").takeLast(8))else it},armies=CrownEngine.units(w).map {if("central:${it.id}" in missed)it.copy(morale=(it.morale-5).coerceAtLeast(0))else it},localDefense=w.localDefense.copy(units=w.localDefense.units.map {if("local:${it.id}" in missed)it.copy(morale=(it.morale-5).coerceAtLeast(0))else it}))
  ledger+="公职俸禄与军饷应付${need}两，拨付${paidWan}万两、借款${borrow}万两；薪资基金结余${fund-spent}两，欠俸欠饷合计${arrears.values.sum()}两。已按俸禄明细发放，不重复支付固定行政预算。"
  if(missed.isNotEmpty())ledger+="${missed.size}项薪饷未足额支付：相关官员忠诚-3、部队士气-5、国家稳定-2。欠款逐人逐军记录。"
  return next
 }
 fun settleArrears(w:World,events:MutableList<String>):World {
  var fund=w.payroll.reserveLiang+10000;val arrears=w.payroll.arrears.toMutableMap();val restored=mutableSetOf<String>()
  for(key in arrears.keys.sorted()){val paid=minOf(fund,arrears.getValue(key));fund-=paid;arrears[key]=arrears.getValue(key)-paid;if(paid>0&&arrears[key]==0L)restored+=key;if(fund==0L)break}
  events+="拨付1万两补发欠俸与军饷，尾款${fund}两保留薪资基金；实际获得补发者关系反馈，不改变死者生死与职位。"
  return w.copy(payroll=w.payroll.copy(reserveLiang=fund,arrears=arrears.filterValues {it>0}),people=w.people.map {if(it.alive&&"person:${it.id}" in restored)it.copy(loyalty=(it.loyalty+1).coerceAtMost(100))else it},armies=CrownEngine.units(w).map {if("central:${it.id}" in restored)it.copy(morale=(it.morale+2).coerceAtMost(100))else it},localDefense=w.localDefense.copy(units=w.localDefense.units.map {if("local:${it.id}" in restored)it.copy(morale=(it.morale+2).coerceAtMost(100))else it}))
 }
 fun verify(w:World){require(w.payroll.lastTurn in -1..w.turn&&w.payroll.reserveLiang in 0..100000000&&w.payroll.arrears.size<=1500&&w.payroll.arrears.values.all {it in 1..1000000000000L})}
}
object WorldSetup {
 fun initialize(w:World):World=TerrainEngine.initialize(LocalDefenseEngine.initialize(MunicipalEngine.initialize(w)))
}
