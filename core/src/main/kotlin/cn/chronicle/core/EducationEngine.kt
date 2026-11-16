package cn.chronicle.core

import kotlinx.serialization.Serializable

@Serializable data class Academy(val level:Int=1,val tutor:Int=0)
@Serializable data class StudyCourse(val person:Int,val destination:Int=-1,val remaining:Int,val duration:Int,val travel:Int,val returnDays:Int,val score:Int=0,val phase:String="赴学")
object EducationEngine {
 val types=setOf("CONVERSE","ACADEMY_UPGRADE","APPOINT_TUTOR","STUDY_HOME","STUDY_ABROAD","RECALL_STUDENT")
 fun grade(p:Person)=(1+p.ability/25).coerceIn(1,5)
 fun busy(w:World,id:Int)=w.studies.any {it.person==id}
 fun teacher(w:World)=w.people.find {it.id==w.academy.tutor&&it.alive&&!busy(w,it.id)&&w.journey?.companion!=it.id}
 fun expected(w:World,destination:Int):Int {
  val level=if(destination<0)w.academy.level else w.neighbors[destination].academyLevel
  val tutor=if(destination<0)teacher(w)?.let(::grade)?:0 else w.neighbors[destination].teacherLevel
  return if(tutor==0)0 else ((level+tutor)/2+1).coerceAtMost(6)
 }
 fun validate(w:World,c:Command):String? {
  val id=c.target.toIntOrNull();val p=w.people.find {it.id==id}
  if(c.type=="ACADEMY_UPGRADE")return when {w.academy.level>=5->"学院已达五级。";w.projects.any {it.kind==c.type}->"学院扩建正在进行。";else->null}
  if(p==null||!p.alive)return "目标须为已登记且在世的人物。"
  return when(c.type){
   "CONVERSE" -> DialogueEngine.access(w,p.id)?:when {c.mode !in DialogueEngine.approaches->"交谈方式无效。";w.day-(w.socialDays[p.id]?:-1000)<30->"这位人物的关系交流每30日结算一次，仍可继续日常对话。";else->null}
   "APPOINT_TUTOR" -> if(p.ability<40||busy(w,p.id)||w.journey?.companion==p.id)"讲席须有40以上能力，且未赴学或随驾。" else null
   "STUDY_HOME","STUDY_ABROAD" -> when {
    p.ageQuarters<72->"本版派学对象须成年。"
    busy(w,p.id)||w.studies.size>=5->"此人已在读，或五个派学名额已满。"
    w.academy.tutor==p.id->"此人为现任讲席，请先另聘讲席。"
    w.journey?.let {it.companion==p.id||it.regent==p.id}==true->"当前随行与摄政人员不能离岗赴学。"
    c.type=="STUDY_HOME"&&teacher(w)==null->"学院缺少可授课的在世讲席。"
    c.type=="STUDY_HOME"&&w.studies.count {it.destination<0}>=w.academy.level->"国内学额已满；学院每级增加一个学额。"
    c.type=="STUDY_ABROAD"&&c.region !in w.neighbors.indices->"目标国家不存在。"
    c.type=="STUDY_ABROAD"&&w.neighbors[c.region].relation<50->"与目标国家的关系须达到50。"
    else->null
   }
   "RECALL_STUDENT" -> if(!busy(w,p.id))"此人不在派学名册中。" else if(w.studies.first {it.person==p.id}.phase=="返程")"已经在返程中。" else null
   else -> "未知培养行动。"
  }
 }
 fun execute(w:World,c:Command,events:MutableList<String>):World {
  val id=c.target.toIntOrNull();val p=w.people.find {it.id==id}
  return when(c.type){
   "CONVERSE" -> {p!!;val masked=DialogueEngine.hidden(w,p)
    val effect=when(c.mode){"倾听"->listOf(3,if(masked)0 else 1,0,0);"勉励"->listOf(2,if(masked||p.loyalty<30)0 else 2,0,0);"质询"->listOf(-2,if(masked)0 else -1,3,1);else->listOf(-6,if(masked)0 else -4,5,4)}
    events+="与${p.name}${c.mode}交流：信任${effect[0]}、忠诚${effect[1]}、怀疑${effect[2]}、敌意${effect[3]}，受百分制边界约束。${if(masked)"对方只认识当前伪装，不增加对统治者的忠诚。" else ""}"
    w.copy(socialDays=w.socialDays+(p.id to w.day),people=w.people.map {if(it.id==p.id)it.copy(trust=(it.trust+effect[0]).coerceIn(0,100),loyalty=(it.loyalty+effect[1]).coerceIn(0,100),suspicion=(it.suspicion+effect[2]).coerceIn(0,100),hostility=(it.hostility+effect[3]).coerceIn(0,100),memories=(it.memories+"${w.day}日，与${DialogueEngine.identity(w,it)}${c.mode}交流").takeLast(8))else it})
   }
   "ACADEMY_UPGRADE"->{events+="国家学院扩建立项，180日后增级；未竣工前不提前增加学额与教学效果。";w.copy(projects=w.projects+Project(c.type,0,2,180))}
   "APPOINT_TUTOR"->{events+="${p!!.name}兼任国家学院讲席，师资等级${grade(p)}，由能力决定。";w.copy(academy=w.academy.copy(tutor=p.id))}
   "STUDY_HOME","STUDY_ABROAD"->{val abroad=c.type=="STUDY_ABROAD";val travel=if(abroad)14+c.region*7 else 7;val duration=if(abroad)180 else 90
    events+="${p!!.name}已派往${if(abroad)w.neighbors[c.region].name+"学宫" else "国家学院"}，赴学${travel}日、研习${duration}日、返程${travel}日，期间不能随驾或担任摄政；效果按实际授课累计。"
    w.copy(studies=w.studies+StudyCourse(p.id,if(abroad)c.region else -1,duration,duration,travel,travel))
   }
   "RECALL_STUDENT"->{events+="召回${p!!.name}；须走完返程，按已完成课业计收益，不退已发生的学费与行旅支出。";w.copy(studies=w.studies.map {if(it.person==p.id)it.copy(phase="返程",travel=it.returnDays)else it})}
   else->w
  }
 }
 fun advance(w:World,events:MutableList<String>):World {
  var x=w;val active=mutableListOf<StudyCourse>()
  for(old in w.studies){
   val person=x.people.find {it.id==old.person&&it.alive}
   if(person==null){events+="派学人物已故，学籍终止，不再授予结业属性。";continue}
   var s=old
   if(s.phase=="返程"){
    s=s.copy(travel=s.travel-1)
    if(s.travel<=0){val completed=s.remaining==0;val gain=(s.score/(s.duration*2)+if(completed)1 else 0).coerceIn(0,6);val loyalty=if(completed&&s.destination<0)1 else if(completed&&Math.floorMod(w.seed+person.id+w.day,4L)==0L)-1 else 0
     x=x.copy(people=x.people.map {if(it.id==person.id)it.copy(ability=(it.ability+gain).coerceAtMost(100),loyalty=(it.loyalty+loyalty).coerceIn(0,100),memories=(it.memories+"${w.day}日返国结业，能力+$gain").takeLast(8))else it});events+="${person.name}返程完成：能力+$gain、忠诚${if(loyalty>0)"+" else ""}$loyalty，收益来自实际课业、学院等级与讲席能力，受属性上限约束。";continue}
   }else if(s.travel>0){s=s.copy(travel=s.travel-1);if(s.travel==0){s=s.copy(phase="研习");events+="${person.name}抵达学宫开始研习。"}}
   else {
    val teacher=if(s.destination<0)teacher(x)?.let(::grade)?:0 else x.neighbors[s.destination].teacherLevel
    if(s.destination>=0&&x.neighbors[s.destination].relation<30){s=s.copy(phase="返程",travel=s.returnDays);events+="邦交恶化，${person.name}终止留学并返国。"}
    else if(teacher>0){val school=if(s.destination<0)x.academy.level else x.neighbors[s.destination].academyLevel;s=s.copy(remaining=s.remaining-1,score=s.score+school+teacher);if(s.remaining==0){s=s.copy(phase="返程",travel=s.returnDays);events+="${person.name}完成课程，正在返程；到达后结算学业。"}}
   }
   active+=s
  }
  return x.copy(studies=active)
 }
 fun completeAcademy(w:World,events:MutableList<String>):World {events+="国家学院扩建竣工，升至${w.academy.level+1}级，学额与授课质量提高。";return w.copy(academy=w.academy.copy(level=(w.academy.level+1).coerceAtMost(5)))}
}
