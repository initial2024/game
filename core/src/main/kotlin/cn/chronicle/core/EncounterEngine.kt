package cn.chronicle.core

import kotlinx.serialization.Serializable

@Serializable data class Encounter(val person:Int,val region:Int,val day:Int,val gender:String,val knowsIdentity:Boolean=false,val recruited:Boolean=false,val relationship:String="初识",val forced:Boolean=false,val released:Boolean=false)
@Serializable data class AdventureState(val encounters:List<Encounter> = emptyList(),val lastEncounterDay:Int=-30,val serial:Int=0)
object EncounterEngine {
 val labels=linkedMapOf("ENCOUNTER_RECRUIT" to "招揽同行","ENCOUNTER_KIN" to "结交与结亲","ENCOUNTER_RELEASE" to "解除强制安排")
 val types=labels.keys
 fun cost(c:Command):Long=when(c.type){"ENCOUNTER_RECRUIT"->20;"ENCOUNTER_KIN"->if(c.mode=="婚配")40 else 15;"ENCOUNTER_RELEASE"->30;else->0}
 fun duration(c:Command)=when(c.type){"ENCOUNTER_KIN"->7;else->3}
 fun advance(w:World,events:MutableList<String>):World {
  val j=w.journey?:return w
  if(!w.chronicle.enabled||j.remainingTravel>0||w.day-w.adventure.lastEncounterDay<10||w.people.size>=1000)return w
  if(Seeded.pick(w,"encounter:${w.day}:${j.region}",100)>=20)return w
  val serial=w.adventure.serial+1;val id=(w.people.maxOf {it.id})+1;val age=listOf(10,14,18,25,34,52,68,83)[Seeded.pick(w,"encounter-age:$serial",8)];val ability=10+Seeded.pick(w,"encounter-ability:$serial",86)
  val role=when {age<18->"当地学徒";age>=70->"乡间长者";else->listOf("行商","农人","工匠","游学者","退伍军士","地方艺人")[Seeded.pick(w,"encounter-role:$serial",6)]}
  val p=Person(id,listOf("舒","闻","池","宁","阮","莫","何","江")[serial%8]+listOf("知遥","清和","见山","临溪","映雪","安舟","若谷","云舒")[Seeded.pick(w,"encounter-name:$serial",8)],w.regions[j.region].name+role,Seeded.pick(w,"encounter-faction:$serial",3),age*4,ability,25+Seeded.pick(w,"encounter-loyalty:$serial",55),listOf("重视自主","求得安稳","珍惜乡里","谨慎交往")[serial%4],if(age<18)"读书学艺与平安成长"else if(age>=70)"保全乡里与传授经历"else"寻找合适的生计与归属")
  val e=Encounter(id,j.region,w.day,if(serial%2==0)"女"else"男",j.revealed)
  events+="【行旅偶遇】${p.name}，${age}岁${role}。${if(j.revealed)"知晓已验证的君主身份，但不保证服从。"else"只把你当作${j.disguise}，不会无故识破。"}可交谈、招揽、结交或在合适条件下议亲。"
  return w.copy(people=w.people+p,adventure=w.adventure.copy(encounters=(w.adventure.encounters+e).takeLast(100),lastEncounterDay=w.day,serial=serial))
 }
 fun reveal(w:World,events:MutableList<String>):World {
  val j=w.journey?:return w;var people=w.people
  val encounters=w.adventure.encounters.map {e->if(!e.knowsIdentity&&e.region==j.region&&w.day-e.day<90){
   val p=people.first {it.id==e.person};val reaction=when {p.hostility>=45->"仍怀旧怨，不因身份而顺从";p.trust>=55->"愿意献策，信任与敬畏上升";w.regions[e.region].morale<40->"惊惧中仍有民怨";else->"先惊讶后谨慎观察"}
   people=people.map {if(it.id==p.id)it.copy(fear=(it.fear+8).coerceAtMost(100),trust=(it.trust+if(it.trust>=55)3 else 0).coerceAtMost(100),hostility=(it.hostility+if(w.regions[e.region].morale<40)4 else 0).coerceAtMost(100),memories=(it.memories+"${w.day}日见印信与知情朝臣验明身份：$reaction").takeLast(8))else it};events+="${p.name}得知身份后$reaction。";e.copy(knowsIdentity=true)
  }else e}
  return w.copy(people=people,adventure=w.adventure.copy(encounters=encounters))
 }
 fun validate(w:World,c:Command):String? {
  val e=w.adventure.encounters.find {it.person==c.target.toIntOrNull()}?:return "此人尚未通过行旅相遇。";val p=w.people.find {it.id==e.person&&it.alive}?:return "当事人已故。"
  if(c.type=="ENCOUNTER_RELEASE")return if(e.forced&&!e.released)null else "没有尚未解除的强制安排。"
  if(c.disguise !in listOf("自愿","强制"))return "请选择邀请或强制，不能把沉默当作同意。"
  if(c.disguise=="强制"&&w.journey?.revealed==false)return "伪装身份没有公开调兵拘人的权限；须先验明身份。"
  if(c.type=="ENCOUNTER_RECRUIT"&&e.recruited)return "已登记招揽结果，不重复领用安置预算。"
  if(c.type=="ENCOUNTER_KIN"){
   if(c.mode !in listOf("结交","义亲","婚配"))return "请选择结交、义亲或婚配。"
   if(e.relationship==c.mode)return "已有此关系，不重复安排。"
   if(c.mode=="婚配"&&(p.ageQuarters<72||w.day-w.sovereign.bornDay<18*360))return "婚配只涉及成年人；未成年人可结交、培养或建立非婚恋义亲关系。"
   if(c.mode in listOf("义亲","婚配")&&w.household.members.size>=100)return "家族名册已满。"
   if(c.mode=="婚配"&&w.household.members.count {it.role=="成年伴侣"&&it.alive}>=4)return "本版最多四位在世伴侣。"
  }
  return null
 }
 fun execute(w:World,c:Command,events:MutableList<String>):World {
  val e=w.adventure.encounters.first {it.person==c.target.toInt()};val p=w.people.first {it.id==e.person};val forced=c.disguise=="强制";var x=w;var updated=e
  if(c.type=="ENCOUNTER_RELEASE"){
   updated=e.copy(released=true,forced=false,relationship="旧识");val family=w.familyPeople.entries.find {it.value==p.id}?.key
   x=x.copy(sovereign=x.sovereign.copy(coercedPeople=x.sovereign.coercedPeople-p.id),household=x.household.copy(members=x.household.members.map {if(it.id==family)it.copy(role="解除安排的旧识",willing=false)else it}),people=x.people.map {if(it.id==p.id)it.copy(role="自由生活",trust=(it.trust+3).coerceAtMost(100),hostility=(it.hostility-3).coerceAtLeast(0),memories=(it.memories+"${w.day}日强制安排解除并得到补偿，旧怨并未自动消除").takeLast(8))else it});events+="解除强制与补偿已登记，不抹去此前行为，也不等于当事人愿意继续亲密关系。"
  }else{
   val accepts=forced||p.trust>=55||Seeded.pick(w,"invite:${p.id}:${c.type}:${c.mode}",100)<(p.trust+p.loyalty/4).coerceAtMost(80)
   if(!accepts){events+="${p.name}婉拒${if(c.type=="ENCOUNTER_RECRUIT")"招揽"else c.mode}，安置与沟通费用已经发生；拒绝没有被自动改写成同意。";return w.copy(people=w.people.map {if(it.id==p.id)it.copy(memories=(it.memories+"${w.day}日拒绝了一次${c.mode}安排").takeLast(8))else it})}
   if(c.type=="ENCOUNTER_RECRUIT"){updated=e.copy(recruited=true,forced=forced);x=x.copy(people=x.people.map {if(it.id==p.id)it.copy(role=if(p.ageQuarters<72)"待培养学徒"else if(p.ageQuarters>=280)"待聘顾问"else"待选人才")else it});events+="${p.name}登记为${if(p.ageQuarters<72)"学徒，按年龄培养，不能直接授予成年官职或军职"else"候选人才；仍须考核能力与职位条件后任用"}。"}
   else{
    updated=e.copy(relationship=c.mode,forced=forced)
    if(c.mode in listOf("义亲","婚配")){val old=w.familyPeople.entries.find {it.value==p.id}?.key;val id=old?:((w.household.members.maxOfOrNull {it.id}?:0)+1);val f=FamilyMember(id,p.name,if(c.mode=="婚配")"成年伴侣"else"义亲",w.day-p.ageQuarters*90,willing=!forced,relationship=(p.trust+if(forced)-15 else 4).coerceIn(0,100),health=80);x=x.copy(household=x.household.copy(members=x.household.members.filter {it.id!=id}+f),familyPeople=x.familyPeople+(id to p.id))}
    events+="${p.name}与玩家的${c.mode}安排已登记。婚配不自动开启备孕，非自愿安排不满足自愿备孕条件。"
   }
   x=x.copy(people=x.people.map {if(it.id==p.id)it.copy(loyalty=(it.loyalty+if(forced)-20 else 3).coerceIn(0,100),trust=(it.trust+if(forced)-15 else 4).coerceIn(0,100),fear=(it.fear+if(forced)20 else 0).coerceAtMost(100),hostility=(it.hostility+if(forced)20 else 0).coerceAtMost(100),memories=(it.memories+"${w.day}日${if(forced)"非自愿承受"else"接受"}${if(c.type=="ENCOUNTER_RECRUIT")"招揽"else c.mode}，记录意愿与后果").takeLast(8))else it})
   if(forced){x=x.copy(sovereign=x.sovereign.copy(coercedPeople=(x.sovereign.coercedPeople+p.id).distinct()),nation=x.nation.copy(morale=x.nation.morale-5,stability=x.nation.stability-3));events+="强制代价：忠诚-20、信任-15、敌意+20、民心-5、稳定-3；可能带来呈状、离心或安保风险，恐惧不等于忠诚。"}
  }
  return FamilyEngine.syncPeople(x.copy(adventure=x.adventure.copy(encounters=x.adventure.encounters.map {if(it.person==p.id)updated else it})))
 }
 fun verify(w:World){require(w.adventure.encounters.size<=100&&w.adventure.encounters.map {it.person}.distinct().size==w.adventure.encounters.size&&w.adventure.encounters.all {e->w.people.any {it.id==e.person}&&e.region in 0..3&&e.day<=w.day})}
}
