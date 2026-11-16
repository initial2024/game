package cn.chronicle.core

import kotlinx.serialization.Serializable
@Serializable data class FamilyMember(val id:Int,val name:String,val role:String,val bornDay:Int,
 val alive:Boolean=true,val willing:Boolean=true,val relationship:Int=68,val health:Int=80,val diedDay:Int?=null) {
 fun ageAt(day:Int):Int=((if(alive)day else diedDay?:minOf(day,bornDay+36000))-bornDay)/360
}
@Serializable data class Pregnancy(val parent:Int,val remainingDays:Int=270,val childName:String)
@Serializable data class Household(val allowPregnancy:Boolean=false,val nextChildName:String="景宁",
 val members:List<FamilyMember> = listOf(FamilyMember(1,"林映书","成年伴侣",-27*360)),
 val pregnancy:Pregnancy?=null,val attempts:Int=0)
object FamilyEngine {
 val types=setOf("FAMILY_CONTACT","ERA","RULER_NAME","CHILD_NAME_PLAN","NAME_CHILD","FAMILY_PLAN","TRY_HEIR","FAMILY_TIME")
 fun validName(name:String)=name.isNotBlank() && name.length<=16 && name.none {it.isISOControl() || it=='|'}
 fun validate(w:World,c:Command):String? {
  val h=w.household
  if(c.type=="FAMILY_CONTACT")return when {h.members.none {it.id==c.target.toIntOrNull()&&it.alive}->"须选择在世家族成员。";c.target.toInt() in w.familyPeople->"此人已在对话名册中。";w.people.size>=1000->"人物名册已满，不能新增对话身份。";else->null}
  if(c.type in listOf("ERA","RULER_NAME","CHILD_NAME_PLAN") && !validName(c.target)) return "名称须为 1–16 字且不含控制字符。"
  if(c.type=="FAMILY_PLAN" && c.target !in listOf("允许","暂停")) return "备孕设置无效。"
  if(c.type=="NAME_CHILD") {
   val parts=c.target.split('|',limit=2)
   if(parts.size!=2 || !validName(parts[1])) return "子女姓名格式无效。"
   if(h.members.none {it.id==parts[0].toIntOrNull() && it.role=="子女" && it.alive}) return "请选择在世子女。"
  }
  if(c.type=="TRY_HEIR") {
   if(!h.allowPregnancy) return "备孕尚未开启，可在宗室中自主设置。"
   if(h.pregnancy!=null) return "已有登记孕期，不能重复备孕。"
   if(h.members.none {it.role=="成年伴侣" && it.alive && it.willing && (w.day-it.bornDay)/360 in 18..44 && it.relationship>=60 && it.health>=50}) return "没有同时满足成年、自愿、年龄、健康与关系条件的伴侣。"
   if(h.members.count {it.role=="子女" && it.alive}>=8) return "本版家族管理最多支持 8 名在世子女。"
  }
  return null
 }
 fun execute(w:World,c:Command,events:MutableList<String>):World {
  var x=w;val h=w.household
  when(c.type) {
   "FAMILY_CONTACT"->{val f=h.members.first {it.id==c.target.toInt()};val id=(w.people.maxOfOrNull {it.id}?:-1)+1;val p=Person(id,f.name,f.role,0,((w.day-f.bornDay)/90).coerceAtLeast(0),40,60,"自主表达",if(f.ageAt(w.day)<18)"平安成长与读书"else if(!f.willing)"婉拒入宫，自主生活"else"珍惜家人与自主选择",alive=f.alive,trust=f.relationship);x=w.copy(people=w.people+p,familyPeople=w.familyPeople+(f.id to id));events+="${f.name}的独立交流身份已登记，沿用原家族关系，不收费、不推进时间。"}

   "ERA" -> {x=x.copy(era=c.target,eraDay=w.day);events+="改元${c.target}，旧纪年及历史保留。"}
   "RULER_NAME" -> {x=x.copy(ruler=c.target);events+="君主登记姓名改为${c.target}，此前史料不改写。"}
   "CHILD_NAME_PLAN" -> {x=x.copy(household=h.copy(nextChildName=c.target));events+="下一次登记孕期将使用预备名${c.target}；既有孕期与子女名字不变。"}
   "NAME_CHILD" -> {val parts=c.target.split('|',limit=2);x=x.copy(household=h.copy(members=h.members.map {if(it.id==parts[0].toInt())it.copy(name=parts[1]) else it}));events+="宗室名册登记子女新名${parts[1]}。"}
   "FAMILY_PLAN" -> {x=x.copy(household=h.copy(allowPregnancy=c.target=="允许"));events+=if(c.target=="允许") "开启自愿备孕；仍须单独确认安排，不能保证进入孕期。" else "暂停新的备孕安排，已登记孕期继续正常照护。"}
   "TRY_HEIR" -> {
    val parent=h.members.first {it.role=="成年伴侣" && it.alive && it.willing && (w.day-it.bornDay)/360 in 18..44 && it.relationship>=60 && it.health>=50}
    val success=Math.floorMod(w.seed+w.turn*31L+h.attempts*17L,100L)<65
    x=x.copy(household=h.copy(attempts=h.attempts+1,pregnancy=if(success) Pregnancy(parent.id,270,h.nextChildName) else null))
    events+=if(success) "双方自愿备孕后，医官登记孕期，预计需 270 日。照护开支另记入国库。" else "本次未进入孕期。时间与照护准备费用已经发生，不承诺成功。"
   }
   "FAMILY_TIME" -> {x=x.copy(household=h.copy(members=h.members.map {if(it.alive)it.copy(relationship=(it.relationship+6).coerceAtMost(100)) else it}));events+="安排家庭相处与教育，关系得到改善。"}
  }
  return x
 }
 fun syncPeople(w:World):World {
  if(w.familyPeople.isEmpty())return w
  val members=w.household.members.associateBy {it.id};val mapping=w.familyPeople.entries.associate {it.value to it.key}
  return w.copy(people=w.people.map {p->val f=mapping[p.id]?.let {members[it]};if(f==null)p else p.copy(name=f.name,role=if(f.alive)f.role else "已故",ageQuarters=((if(f.alive)w.day else f.diedDay?:w.day)-f.bornDay)/90,alive=f.alive,trust=f.relationship)})
 }
 fun advance(w:World,days:Int,events:MutableList<String>):World {
  var h=w.household
  h=h.copy(members=h.members.map {m->if(m.alive && w.day-m.bornDay>=100*360){events+="宗室${m.name}寿终，其身份改记已故。";m.copy(alive=false,diedDay=w.day)}else m})
  h.pregnancy?.let {p->
   val parent=h.members.find {it.id==p.parent && it.alive}
   if(parent==null) {h=h.copy(pregnancy=null);events+="登记孕期因家族成员离世结束，史官保留原始记录。"}
   else if(p.remainingDays<=days) {
    val id=(h.members.maxOfOrNull {it.id}?:0)+1
    val child=FamilyMember(id,p.childName,"子女",w.day,willing=false,relationship=55,health=85)
    h=h.copy(pregnancy=null,members=h.members+child);events+="${parent.name}平安分娩，子女${p.childName}登记入册；可在宗室中再次命名。"
   } else h=h.copy(pregnancy=p.copy(remainingDays=p.remainingDays-days))
  }
  return syncPeople(w.copy(household=h))
 }
}
