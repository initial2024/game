package cn.chronicle.core

import kotlinx.serialization.Serializable

@Serializable data class Organization(val id:String,val name:String,val kind:String,val home:String,val members:List<Int> = emptyList(),val exposure:Int=0)
@Serializable data class IntelReport(val day:Int,val organization:String,val kind:String,val subject:String,val text:String)
@Serializable data class Intelligence(val organizations:List<Organization> = emptyList(),val serial:Int=0,val threat:Int=30,val networks:List<Int> = listOf(0,0,0),val reports:List<IntelReport> = emptyList())
object IntelligenceEngine {
 val types=setOf("ORG_FOUND","ORG_ASSIGN","ORG_RELEASE","INTEL_SCOUT","INTEL_COUNTER","ASSASSINATE")
 val missions=setOf("INTEL_SCOUT","INTEL_COUNTER","ASSASSINATE")
 val kinds=listOf("监察","谍报","刺客")
 fun membership(w:World,id:Int)=w.intelligence.organizations.firstOrNull {id in it.members}
 fun active(w:World,o:Organization)=o.members.mapNotNull {id->w.people.find {it.id==id&&it.alive&&!EducationEngine.busy(w,id)}}
 fun upkeep(w:World)=w.intelligence.organizations.sumOf {4L+it.members.size*2L}
 fun validate(w:World,c:Command):String? {
  val state=w.intelligence
  if(c.type=="ORG_FOUND")return when {state.organizations.size>=4->"本版最多四个组织。";!FamilyEngine.validName(c.text)||c.objective !in kinds||w.towns.none {it.id==c.target}->"请点选组织职责、驻地并填写1–16字名称。";state.organizations.any {it.name==c.text}->"已有同名组织。";else->null}
  val org=state.organizations.find {it.id==c.target}?:return "组织不存在。"
  if(c.type=="ORG_RELEASE")return if(c.objective.toIntOrNull() in org.members)null else "此人不在组织中。"
  if(c.type=="ORG_ASSIGN"){
   val p=w.people.find {it.id==c.objective.toIntOrNull()&&it.alive}?:return "请选择在世人物。"
   return when {p.ageQuarters<72||p.ability<40||p.id==0||p.id==w.academy.tutor||TalentEngine.town(w,p.id)!=null||CrownEngine.units(w).any {it.commander==p.id}||EducationEngine.busy(w,p.id)||w.journey?.let {it.companion==p.id||it.regent==p.id}==true->"须为成年、能力40以上且无地方官、统军、讲席、求学或行在冲突的人才。";p.id in org.members->"此人已在该组织。";org.members.size>=3->"每个组织最多三名核心成员。";else->null}
  }
  if(active(w,org).isEmpty())return "组织缺少在岗成员，请先任用人才。"
  if(w.pending.any {it.command.type in missions&&it.command.target==org.id})return "此组织已有行动在办，须先等待归报。"
  return when(c.type){
   "INTEL_SCOUT"->if(org.kind!="谍报"||c.region !in w.neighbors.indices)"须由谍报组织选择目标国家。"else null
   "INTEL_COUNTER"->if(org.kind=="刺客")"刺客组织不能代行国家反谍与监察。"else null
   "ASSASSINATE"->{val target=w.people.find {it.id==c.objective.toIntOrNull()};if(org.kind!="刺客"||target==null||!target.alive||target.ageQuarters<72||target.id in org.members)"只允许刺客组织以登记在世成年人为游戏目标，不能针对本组织成员。"else null}
   else->"未知组织行动。"
  }
 }
 fun execute(w:World,c:Command,events:MutableList<String>):World {
  val state=w.intelligence
  if(c.type=="ORG_FOUND"){val org=Organization("organization-${state.serial+1}",c.text,c.objective,c.target);events+="${org.name}完成筹建，职责${org.kind}；驻地${w.towns.first {it.id==org.home}.name}，尚无成员。维护4万两/季，每名成员另2万两/季。";return w.copy(intelligence=state.copy(organizations=state.organizations+org,serial=state.serial+1))}
  val org=state.organizations.first {it.id==c.target}
  if(c.type=="ORG_RELEASE"){val id=c.objective.toInt();events+="${w.people.first {it.id==id}.name}交卸${org.name}职务，保留经历。";return w.copy(intelligence=state.copy(organizations=state.organizations.map {if(it.id==org.id)it.copy(members=it.members-id)else it}),people=w.people.map {if(it.id==id&&it.alive)it.copy(role="待选人才")else it})}
  if(c.type=="ORG_ASSIGN"){val id=c.objective.toInt();events+="${w.people.first {it.id==id}.name}加入${org.name}，原组织职务交卸，负责${org.kind}事务。";return w.copy(intelligence=state.copy(organizations=state.organizations.map {if(it.id==org.id)it.copy(members=it.members+id)else it.copy(members=it.members-id)}),people=w.people.map {if(it.id==id)it.copy(role="${org.name}成员",memories=(it.memories+"${w.day}日加入${org.name}").takeLast(8))else it})}
  val team=active(w,org);val skill=team.sumOf {it.ability+it.loyalty/4}/team.size;val roll=Math.floorMod(w.seed+w.day*31L+w.revision*17L+org.id.hashCode(),100L).toInt();var x=w;var intel=state;var heat=org.exposure
  val report=when(c.type){
   "INTEL_SCOUT"->{val n=w.neighbors[c.region];val success=roll<(skill-n.strength/3).coerceIn(10,85);heat=(heat+if(success)8 else 20).coerceAtMost(100);if(success)intel=intel.copy(networks=intel.networks.mapIndexed {i,v->if(i==c.region)(v+12).coerceAtMost(100)else v});if(!success)x=x.copy(neighbors=x.neighbors.mapIndexed {i,v->if(i==c.region)v.copy(relation=(v.relation-4).coerceAtLeast(0))else v});"对${n.name}侦察${if(success)"取得经过登记的军备情报，网络+12；后续交战可削弱敌方信息优势"else"受阻，未获得新情报，暴露引起邦交-4"}。军事实力参考${n.strength}；这是引擎记录，不是AI创造的证据。"}
   "INTEL_COUNTER"->{val reduction=(skill/10+3).coerceAtMost(intel.threat);intel=intel.copy(threat=intel.threat-reduction);heat=(heat-15).coerceAtLeast(0);x=x.copy(nation=x.nation.copy(stability=(x.nation.stability+1).coerceAtMost(100)));"完成反谍排查：渗透威胁-$reduction，稳定+1；线索不等于任何官员有罪，不自动刑事处罚。"}
   else->{val target=x.people.first {it.id==c.objective.toInt()};val security=TalentEngine.town(w,target.id)?.security?:80;val chance=(skill/2+20-security/4-org.exposure/10).coerceIn(5,65);val success=roll<chance;heat=(heat+35).coerceAtMost(100);intel=intel.copy(threat=(intel.threat+10).coerceAtMost(100));if(success)x=removePerson(x,target.id,events);x=x.copy(nation=x.nation.copy(morale=(x.nation.morale-6).coerceAtLeast(0),stability=(x.nation.stability-8).coerceAtLeast(0)));"针对${target.name}的游戏内刺杀${if(success)"成功，目标已故，职位与指挥权清理"else"失败，目标仍在世"}。暴露+35、渗透威胁+10、民心-6、稳定-8，引发政治追查。行动不证明目标有罪。"}
  }
  events+="${org.name}归报：$report";val note=IntelReport(w.day,org.id,c.type,if(c.type=="ASSASSINATE")c.objective else c.region.toString(),report)
  return x.copy(people=x.people.map {p->if(team.any {it.id==p.id})p.copy(memories=(p.memories+"${w.day}日：${report.take(100)}").takeLast(8))else p},intelligence=intel.copy(organizations=intel.organizations.map {if(it.id==org.id)it.copy(exposure=heat)else it},reports=(intel.reports+note).takeLast(12)))
 }
 fun removePerson(w:World,id:Int,events:MutableList<String>):World {
  events+="${w.people.first {it.id==id}.name}遭遇死亡事件；历史身份保留，不能再次任职。"
  val family=w.familyPeople.entries.firstOrNull {it.value==id}?.key
  return w.copy(people=w.people.map {if(it.id==id)it.copy(alive=false,role="已故",memories=(it.memories+"${w.day}日遇害").takeLast(8))else it},regions=w.regions.map {if(it.official==id)it.copy(official=-1)else it},armies=CrownEngine.units(w).map {if(it.commander==id)it.copy(commander=-1)else it},studies=w.studies.filter {it.person!=id},journey=w.journey?.takeUnless {it.companion==id||it.regent==id},household=w.household.copy(members=w.household.members.map {if(it.id==family)it.copy(alive=false,diedDay=w.day)else it},pregnancy=w.household.pregnancy?.takeUnless {it.parent==family}))
 }
 fun quarter(w:World,ledger:MutableList<String>):World {
  val pressure=(3+w.nation.corruption/20+if(w.war!=null)3 else 0);var threat=(w.intelligence.threat+pressure).coerceAtMost(100);var n=w.nation
  if(threat>=75){val loss=minOf(20L,n.treasury);n=n.copy(treasury=n.treasury-loss,stability=(n.stability-3).coerceAtLeast(0),morale=(n.morale-2).coerceAtLeast(0));ledger+="渗透威胁达到$threat，发生破坏：国库-$loss、稳定-3、民心-2；尚无对个人的犯罪认定。"}
  return w.copy(nation=n,intelligence=w.intelligence.copy(threat=threat))
 }
 fun verify(w:World){val s=w.intelligence;require(s.organizations.size<=4&&s.serial in 0..100000&&s.threat in 0..100&&s.networks.size==3&&s.networks.all {it in 0..100}&&s.reports.size<=12);require(s.organizations.map {it.id}.distinct().size==s.organizations.size);val members=s.organizations.flatMap {it.members};require(members.distinct().size==members.size&&members.all {id->w.people.any {it.id==id}});s.organizations.forEach {require(it.kind in kinds&&FamilyEngine.validName(it.name)&&it.exposure in 0..100&&it.members.size<=3&&w.towns.any {t->t.id==it.home})};require(s.reports.all {it.day in 0..w.day&&it.text.length<=600})}
}
