package cn.chronicle.core
import kotlinx.serialization.Serializable

@Serializable data class PendingOrder(val id:Long,val command:Command,val remainingDays:Int,val escrow:Long)
object RealtimeEngine {
 private val immediate=setOf("ERA","RULER_NAME","CHILD_NAME_PLAN","NAME_CHILD","FAMILY_PLAN","STRATEGY","FACTORY","FARM","ROAD","DEPART","REMOTE","REPAY")
 fun duration(c:Command)=when(c.type) {
  in immediate -> 0
  "RESEARCH","DRILL","TRY_HEIR","REST" -> 30
  "REFORM" -> 60
  "DIPLOMACY" -> 20
  "RELIEF","RETURN" -> 7
  "AUDIT","INVESTIGATE" -> 5
  "TAX_UP","TAX_DOWN" -> 5
  "FAMILY_TIME" -> 15
  "TRIAL","DISMISS","CLOSE" -> 3
  else -> 1
 }
 fun describe(c:Command):String {
  val time=when(c.type){"FACTORY"->"即刻立项，约270日后完工";"FARM","ROAD"->"即刻立项，约180日后完工";"REMOTE"->"即刻委派，30日后归报";"DEPART"->"即刻出发，旅程随时间推进";else->if(duration(c)==0)"即刻登记" else "办理${duration(c)}日"}
  return "${Rules.label(c)} · ${Rules.cost(c)}万两 · $time"
 }
 fun suggestions(w:World):List<Command> {
  if(w.journey?.remainingTravel==0 && w.journey?.region==0) return listOf(Command("INVESTIGATE"),Command("AUDIT"),Command("RETURN"))
  return Rules.suggestions(w.copy(journey=null))
 }
 fun submit(w:World,c:Command,expected:Long):Outcome {
  require(w.revision==expected){"局势已改变，请重新下令。"}
  if(c.type=="CANCEL_TASK") {
   val task=w.pending.find {it.id==c.target.toLongOrNull()}?:error("事务已完成或不存在。")
   val refund=task.escrow*4/5
   return finish(w,w.copy(pending=w.pending.filter {it.id!=task.id},nation=w.nation.copy(treasury=(w.nation.treasury+refund).coerceAtMost(1000000))),"撤销${Rules.label(task.command)}",listOf("退还预留资金 $refund 万两；准备费用 ${task.escrow-refund} 万两不退还。"),listOf("撤销尚未完成的事务。"))
  }
  require(c.type!="TRAVEL"){"旅程会随连续时间自动推进，无需按次赶路。"}
  require(Rules.validate(w,c)==null){Rules.validate(w,c)?:"指令无效"}
  if(c.type in immediate) return Rules.apply(w,c,w.revision,advanceDays=false)
  require(w.pending.size<3){"同时最多办理3项事务，请等待或撤销一项。"}
  require(w.pending.none {it.command.type==c.type || (c.type in listOf("TAX_UP","TAX_DOWN") && it.command.type in listOf("TAX_UP","TAX_DOWN"))}){"同类事务正在办理，不能重复排程。"}
  val cost=Rules.cost(c)
  val task=PendingOrder(w.revision+1,c,duration(c),cost)
  return finish(w,w.copy(pending=w.pending+task,nation=w.nation.copy(treasury=w.nation.treasury-cost)),"安排${Rules.label(c)}",listOf("预留资金 -$cost 万两，办理 ${task.remainingDays} 日；取消退还80%预留资金。"),listOf("${Rules.label(c)}已交付办理，时间连续推进，完成后另行记录结果。"))
 }
 fun advance(w:World,days:Int):Outcome {
  require(days in 1..90 && w.day+days<=900090)
  var x=w;val ledger=mutableListOf<String>();val events=mutableListOf<String>()
  repeat(days) {
   x=x.copy(day=x.day+1)
   x.journey?.let {j->
    val travel=(j.remainingTravel-1).coerceAtLeast(0)
    x=x.copy(journey=j.copy(remainingTravel=travel,spentDays=j.spentDays+1,location=if(j.remainingTravel>0 && travel==0)"客栈" else j.location))
    if(j.remainingTravel>0 && travel==0)events+="抵达${x.regions[j.region].name}，可以进入地区场景。"
   }
   if(x.caseFile.remoteDays>0) {
    val left=x.caseFile.remoteDays-1
    x=x.copy(caseFile=x.caseFile.copy(remoteDays=left))
    if(left==0) {
     val evidence=listOf(Evidence("ledger","监察使核验账册",50,true),if(x.caseFile.guilty)Evidence("receipt","监察使复核收费凭据",30,true) else Evidence("forgery","证人笔迹不符的伪证",0,false))
     x=x.copy(caseFile=x.caseFile.copy(evidence=(x.caseFile.evidence+evidence).distinctBy {it.id},status="监察使已归报"));events+="监察使呈交已核验材料，处分须另行决定。"
    }
   }
   x=FamilyEngine.advance(x,1,events)
   x=ProjectEngine.advance(x,events)
   if(x.day%90==0) {x=EconomyEngine.quarter(x,ledger,events,advanceProjects=false);x=CharacterEngine.quarter(x,events);x=EventEngine.quarter(x,ledger,events)}
   val due=x.pending.filter {it.remainingDays<=1}
   x=x.copy(pending=x.pending.filter {it.remainingDays>1}.map {it.copy(remainingDays=it.remainingDays-1)})
   due.forEach {task->
    val funded=x.copy(nation=x.nation.copy(treasury=x.nation.treasury+task.escrow))
    val error=Rules.validate(funded,task.command)
    if(error==null) {
     val result=Rules.apply(funded,task.command,funded.revision,advanceDays=false)
     x=result.world.copy(revision=w.revision,recent=w.recent)
     ledger+="${Rules.label(task.command)}结转预留 ${task.escrow} 万两，不重复扣款。"
     ledger+=result.entry.ledger.drop(1)
     events+="完成${Rules.label(task.command)}。";events+=result.entry.events
    } else {
     val refund=task.escrow*4/5
     x=x.copy(nation=x.nation.copy(treasury=(x.nation.treasury+refund).coerceAtMost(1000000)))
     ledger+="${Rules.label(task.command)}因条件变化未完成：$error；退还 $refund 万两。";events+="${Rules.label(task.command)}中止，结果已登记。"
    }
   }
   x=Rules.bounded(x)
  }
  return finish(w,x,"时光流转",ledger,events,record=ledger.isNotEmpty()||events.isNotEmpty())
 }
 private fun finish(old:World,next:World,action:String,ledger:List<String>,events:List<String>,record:Boolean=true):Outcome {
  val entry=Entry(old.revision+1,next.turn,next.day,action,"${next.name} · ${next.date}\n${events.joinToString(" ").ifBlank {"朝政、生产与地方事务随时间推进。"}}",ledger,events,old.seed)
  val world=next.copy(revision=old.revision+1,recent=if(record)(old.recent+entry).takeLast(12) else old.recent)
  Rules.verify(world);return Outcome(world,entry)
 }
}

object ProjectEngine {
 fun advance(w:World,events:MutableList<String>):World {
  var n=w.nation;var regions=w.regions
  w.projects.filter {it.remainingDays<=1}.forEach {p->
   val delta=when(p.kind) {"FACTORY"->{n=n.copy(industry=n.industry+6,commerce=n.commerce+2);"工业+6、商业+2"};"FARM"->{n=n.copy(agriculture=n.agriculture+7);"农业+7"};else->{n=n.copy(infrastructure=n.infrastructure+7,commerce=n.commerce+2);"基建+7、商业+2"}}
   regions=regions.map {if(it.id==p.region)it.copy(industry=(it.industry+if(p.kind=="FACTORY")8 else 0).coerceAtMost(100),agriculture=(it.agriculture+if(p.kind=="FARM")8 else 0).coerceAtMost(100))else it}
   events+="${w.regions[p.region].name}${Rules.label(Command(p.kind))}竣工：$delta（受百分制上限约束）。"
  }
  return w.copy(nation=n,regions=regions,projects=w.projects.filter {it.remainingDays>1}.map {it.copy(remainingDays=it.remainingDays-1,quarters=((it.remainingDays-1+89)/90))})
 }
}
