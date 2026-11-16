package cn.chronicle.core

import kotlinx.serialization.Serializable

@Serializable data class DialogueRecord(val person:Int,val role:String,val text:String,val day:Int,val source:String="player")
data class SpokenReply(val text:String,val source:String)
object DialogueEngine {
 val approaches=listOf("倾听","勉励","质询","威胁")
 fun access(w:World,id:Int):String? {
  val p=w.people.find {it.id==id}?:return "人物不存在。"
  if(!p.alive)return "此人已故，旧对话仅供查阅。"
  return null // Every living registered person can converse, in person or by correspondence.
 }
 fun hidden(w:World,p:Person)=p.id !in w.familyPeople.values&&p.id>=3&&w.journey?.revealed==false
 fun identity(w:World,p:Person)=if(hidden(w,p))w.journey!!.disguise else if(p.id<6||p.id in w.familyPeople.values||TalentEngine.town(w,p.id)!=null)"经朝廷文书确认的统治者" else "已验明身份的来访者"
 fun approach(requested:String,text:String):String {
  require(requested in approaches)
  return if(listOf("废物","蠢货","处死你","杀你全家","滚蛋").any(text::contains))"威胁" else requested
 }
 fun topic(text:String)=when {
  listOf("学习","学院","学堂","留学","学业","老师").any(text::contains)->"学业"
  listOf("证据","贪污","摊派","账册","调查").any(text::contains)->"案件"
  listOf("军队","士兵","军需","边防","军饷").any(text::contains)->"军务"
  listOf("百姓","农田","粮食","赈济","民生").any(text::contains)->"民生"
  listOf("目标","打算","志向","抱负").any(text::contains)->"志向"
  listOf("家乡","近况","身体","休息","家人").any(text::contains)->"近况"
  listOf("政策","改革","朝政","税收").any(text::contains)->"政务"
  else->"闲谈"
 }
 fun socialKey(w:World,p:Person,topic:String):String {
  val context=when(topic){"学业"->w.studies.firstOrNull {it.person==p.id}?.let {"${it.destination}-${it.phase}"}?:"能力${p.ability/10}";"案件"->w.caseFile.status;"军务"->"军备${w.nation.military/20}";"民生"->"民心${w.nation.morale/20}";"政务"->"改革${w.nation.administration/20}";"志向"->p.role;else->"日常"}
  return "$topic:$context".take(100)
 }
 fun exchangeKey(w:World,p:Person,topic:String,text:String):String {
  val base=socialKey(w,p,topic)
  val normalized=text.lowercase().filter {it.isLetter()}.replace("您","你").take(400)
  if(normalized.isBlank())return base
  val hash=java.security.MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray()).take(8).joinToString("") {"%02x".format(it)}
  return "$base|$hash".take(100)
 }
 fun relationship(before:Person,after:Person):String {
  fun change(label:String,a:Int,b:Int)="$label${if(b-a>=0)"+"else ""}${b-a}"
  return listOf(change("信任",before.trust,after.trust),change("忠诚",before.loyalty,after.loyalty),change("怀疑",before.suspicion,after.suspicion),change("敌意",before.hostility,after.hostility)).joinToString(" · ")
 }
 fun context(w:World,p:Person,compact:Boolean=false):String {
  require(access(w,p.id)==null)
  val private=hidden(w,p)
  return buildString {
   append("只扮演${p.name}，${p.role}。时代${w.setting}。来客身份：${identity(w,p)}。")
   append("只依给定信息回应，不猜测未给身份，不创设证据、判决或政策结果；不执行资源操作。回答简短中文。")
   if(p.ageQuarters<72)append("角色未成年，只谈日常、家人、学业与成长，语气符合年龄。")
   if(w.setting==EraRules.ANCIENT)append("仅农耕、手工作坊、驿道、冷兵器。")
   append("立场${p.stance}，目标${p.goal}。态度${if(p.hostility>60)"戒备敌视" else if(p.trust>=60)"愿意信任" else "谨慎"}。")
   TalentEngine.town(w,p.id)?.let {town->append("任所${MunicipalEngine.name(w,town)}，常平仓${town.buildings.getValue("GRANARY")}级，学堂${town.buildings.getValue("SCHOOL")}级。${town.work?.let {"有${MunicipalEngine.buildings.first {b->b.id==it.kind}.name}在建，尚未竣工。"}?:""}")}

   run {
    val memories=p.memories.takeLast(if(compact)1 else 2).filterNot {private&&listOf(w.ruler,"陛下","君主","皇帝","印信").any(it::contains)}
    if(memories.isNotEmpty())append("经历：${memories.joinToString("；").take(if(compact)60 else 180)}。")
    if(!private&&p.id<6)append("已公开案卷：${w.caseFile.status}。只有已登记材料，不得创造新证据。")
   }
  }
 }
 fun sanitize(w:World,p:Person,text:String):String? {
  val s=text.trim().take(1200)
  if(s.isBlank())return null
  if(hidden(w,p)&&listOf(w.ruler,"陛下","皇帝","君主","天子").any(s::contains))return null
  if(w.setting==EraRules.ANCIENT&&listOf("蒸汽机","火车","机枪","飞机","电力").any(s::contains))return null
  if(listOf("已为您增加国库","已经将他定罪","新的证据已经入库","已经撤职").any(s::contains))return null
  return s
 }
 fun fallback(w:World,p:Person,text:String):String {
  if(listOf("证据","贪污","摊派","账册").any(text::contains))return "${p.name}：传言和个人判断不能当作证据。请从调查与核验账册取得登记材料，再决定如何办理。"
  if(listOf("学院","学习","留学").any(text::contains))return "${p.name}：学业须有合适的讲席与时日。请在人物培养中核对学院、师资、费用和行期。"
  if(listOf("任命","建设","派遣","赈济").any(text::contains))return "${p.name}：此事需要正式办理。可先商议，实际执行还须从政令或地方行动确认。"
  return "${p.name}：${if(p.hostility>60)"我对这番谈话仍有保留。" else if(p.trust>=60)"我愿坦诚相告。" else "容我谨慎回应。"}我关心${p.goal}，主张${p.stance}。你最想了解哪一件事？"
 }
 fun safeText(s:String)=s.replace(Regex("<\\|[^>]{0,80}\\|>")," ").replace("[INST]","［INST］").replace("[/INST]","［/INST］")
}
