package cn.chronicle.core

/** Bounded, deterministic personnel registry. Posts store IDs, not names or generated prose. */
object TalentEngine {
 val types=setOf("EXAM","RECRUIT","APPOINT")
 val posts=listOf("长官","副官","主簿")
 fun ids(w:World,t:Town)=listOf(MunicipalEngine.officer(w,t)?.id?:-1,t.deputy,t.clerk)
 fun town(w:World,person:Int)=w.towns.firstOrNull {person in ids(w,it)}
 fun role(t:Town,post:String)=when(post){"长官"->if(t.rank=="县城")"县令"else if(t.rank=="府城")"知府"else"总督";"副官"->if(t.rank=="县城")"县丞"else"同知";else->"主簿"}
 fun staff(w:World):World {
  if(w.towns.all {it.staffed})return w
  val people=w.people.toMutableList();var next=(people.maxOfOrNull {it.id}?:-1)+1
  val towns=w.towns.map {t->if(t.staffed||people.size+2>1000)t else {
   fun aide(post:String):Int {val id=next++;val n=if(post=="副官")"${t.name.take(3)}·周谨"else"${t.name.take(3)}·柳书";people+=Person(id,n,"${t.name}${role(t,post)}",Math.floorMod(id,3),104+id%100,42+Math.floorMod(w.seed+id,40L).toInt(),48+id%31,"恪守职分",if(post=="副官")"协理治安与政务"else"核对户籍与钱粮");return id}
   t.copy(deputy=aide("副官"),clerk=aide("主簿"),staffed=true)
  }}
  return w.copy(towns=towns,people=people)
 }
 fun validate(w:World,c:Command):String? = when(c.type){
  "EXAM","RECRUIT"->when {w.people.size>998->"人才名册已满（最多1000人），请保留现有人物。";c.type=="EXAM"&&w.day-w.lastExam<360->"本届科举已结束，下一届须相隔360日。";c.type=="RECRUIT"&&w.day-w.lastRecruit<30->"本轮招贤刚结束，须筹备30日。";else->null}
  "APPOINT"->{val p=w.people.find {it.id==c.target.toIntOrNull()};val t=w.towns.find {it.id==c.objective};when {
   p==null||!p.alive||p.ageQuarters<72->"须选择在世成年人物。"
   t==null||c.mode !in posts||c.region!=t.region->"官职或辖区无效。"
   p.id<3||w.academy.tutor==p.id||w.journey?.let {it.companion==p.id||it.regent==p.id}==true->"此人正担任朝廷要职、讲席或行在职务，不能兼任地方官。"
   LocalDefenseEngine.office(w,p.id)!=null->"此人已有地方军政职务，请先交卸或由地方军政调任。"
   IntelligenceEngine.membership(w,p.id)!=null->"此人已有组织职务，须先交卸。"
   CrownEngine.units(w).any {it.commander==p.id}->"此人正在统领军队，不能兼任地方官。"
   EducationEngine.busy(w,p.id)->"此人正在求学，须返程后任职。"
   p.ability<if(c.mode=="长官")50 else 35->"能力未达任职门槛（长官50，属官35）。"
   ids(w,t)[posts.indexOf(c.mode)]==p.id->"此人已在该职位，无需重复任命。"
   else->null
  }}
  else->"未知人事行动。"
 }
 fun execute(w:World,c:Command,events:MutableList<String>):World {
  if(c.type!="APPOINT"){
   val exam=c.type=="EXAM";val start=(w.people.maxOfOrNull {it.id}?:-1)+1
   val candidates=(0..1).map {i->val id=start+i;val roll=Math.floorMod(w.seed+w.talentRound*7919L+i*104729L,35L).toInt();Person(id,listOf("裴","姚","卢","章")[Math.floorMod(w.talentRound+i,4)]+listOf("景文","知礼","怀瑾","明策")[Math.floorMod(w.talentRound*3+i,4)],"待选人才",Math.floorMod(id,3),88+roll,((if(exam)50 else 40)+roll/2+if(exam)w.academy.level*2 else 0).coerceAtMost(90),48+roll,"审慎施政",if(exam)"经世致用"else"施展才学",memories=listOf("${w.day}日通过${if(exam)"科举策论"else"公开招贤考核"}，列入候选名册"))}
   events+="${if(exam)"本届科举"else"本轮招贤"}完成：${candidates.joinToString {"${it.name}（能力${it.ability}）"}}。已登记为待选人才，尚未获得官职；能力参考考核与学院水平，忠诚不保证。"
   return w.copy(people=w.people+candidates,talentRound=w.talentRound+1,lastExam=if(exam)w.day else w.lastExam,lastRecruit=if(exam)w.lastRecruit else w.day)
  }
  val id=c.target.toInt();val target=w.towns.first {it.id==c.objective};val displaced=ids(w,target)[posts.indexOf(c.mode)]
  val towns=w.towns.map {t->var next=t.copy(official=if(t.official==id)-1 else t.official,deputy=if(t.deputy==id)-1 else t.deputy,clerk=if(t.clerk==id)-1 else t.clerk)
   if(t.id==target.id)next=when(c.mode){"长官"->next.copy(official=id);"副官"->next.copy(deputy=id);else->next.copy(clerk=id)};next}
  var regions=w.regions.map {if(it.official==id)it.copy(official=-1)else it}
  if(target.id.startsWith("city-r")&&c.mode=="长官")regions=regions.map {if(it.id==target.region)it.copy(official=id)else it}
  val place=MunicipalEngine.name(w,target);val title=role(target,c.mode)
  val people=w.people.map {p->when(p.id){id->p.copy(role="$place$title",loyalty=(p.loyalty+1).coerceAtMost(100),memories=(p.memories+"${w.day}日奉命赴任$place$title").takeLast(8));displaced->if(!p.alive)p else p.copy(role="待选人才",loyalty=(p.loyalty-2).coerceAtLeast(0),memories=(p.memories+"${w.day}日调离$place$title，未经刑事定罪").takeLast(8));else->p}}
  events+="${people.first {it.id==id}.name}赴任$place$title；原职腾空。${if(displaced>=0)"前任转入待选名册，忠诚-2；行政调任不等于定罪。"else"补足空缺。"}新任忠诚+1（受上限约束），履历与管辖关系已更新。"
  return w.copy(towns=towns,regions=regions,people=people)
 }
 fun quarter(w:World,ledger:MutableList<String>):World {
  fun active(id:Int)=w.people.find {it.id==id&&it.alive&&!EducationEngine.busy(w,id)}
  val changes=mutableListOf<String>()
  val towns=w.towns.map {t->val deputy=active(t.deputy);val clerk=active(t.clerk);val governor=MunicipalEngine.officer(w,t)?.takeUnless {EducationEngine.busy(w,it.id)}
   val next=t.copy(security=(t.security+if(deputy!=null&&deputy.ability>=65&&deputy.loyalty>=50)1 else 0).coerceAtMost(100),corruption=(t.corruption-if(clerk!=null&&clerk.ability>=75&&clerk.loyalty>=60)1 else 0).coerceAtLeast(0),morale=(t.morale+if(governor!=null&&governor.ability>=60&&governor.loyalty>=60&&t.corruption<35)1 else 0).coerceAtMost(100))
   if(next!=t)changes+="${t.id}：治安${next.security-t.security}、腐败${next.corruption-t.corruption}、民心${next.morale-t.morale}"
   next}
  if(changes.isNotEmpty())ledger+="地方官按能力、忠诚与在岗情况履职（初始薪俸已计入原财政；非犯罪认定）："+changes.joinToString("；")
  return w.copy(towns=towns)
 }
 fun verify(w:World){
  require(w.talentRound in 0..100000&&w.lastExam in -360..w.day&&w.lastRecruit in -30..w.day)
  val aides=w.towns.flatMap {listOf(it.deputy,it.clerk)}.filter {it>=0};require(aides.distinct().size==aides.size&&aides.all {id->w.people.any {it.id==id}})
  require(w.towns.none {t->t.deputy>=0&&t.deputy==t.clerk})
 }
}
