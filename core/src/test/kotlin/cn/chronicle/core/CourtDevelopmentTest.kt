package cn.chronicle.core

import org.junit.Test
import kotlin.test.*
import kotlinx.serialization.encodeToString

class CourtDevelopmentTest {
 private fun game()=NewGame.create("承熙","景衡","综合发展","标准",1847)
 private fun apply(w:World,c:Command)=RealtimeEngine.submit(w,c,w.revision).world
 private fun days(w:World,n:Int):World {var x=w;repeat(n/90){x=RealtimeEngine.advance(x,90).world};if(n%90>0)x=RealtimeEngine.advance(x,n%90).world;return x}
 @Test fun distinctConversationGrowsRelationsAndRepeatedPunctuationDoesNotFarm(){
  val w=game();val c=Command("CONVERSE",target="1",mode="倾听",objective="近况",text="家乡近况如何")
  val a=apply(w,c);val b=apply(a,c.copy(text="家人身体是否安好"));assertEquals(w.people[1].trust+4,b.people[1].trust);assertEquals(w.people[1].loyalty+2,b.people[1].loyalty)
  val repeated=apply(b,c.copy(text="家乡近况如何？？123"));assertEquals(b.people[1],repeated.people[1]);assertEquals(w.day,b.day);assertEquals(w.nation.treasury,b.nation.treasury)
  var x=b;listOf("谈谈你的休息","我愿听听你的困难","随便换句话","又换一句话").forEach {x=apply(x,c.copy(text=it))};assertEquals(w.people[1].trust+6,x.people[1].trust)
  val hostile=apply(x,c.copy(mode="威胁"));assertTrue(hostile.people[1].trust<x.people[1].trust);assertTrue(hostile.people[1].hostility>x.people[1].hostility)
 }
 @Test fun decreeIsReadOnlyScopedAndOnlyConfirmedCommandsSpendMoney(){
  val w=game();val t=w.towns.first {it.rank=="县城"};val id=t.official
  val p=DecreeEngine.preview(w,id,"扩建学堂");val c=p.commands.single();assertEquals("URBAN_BUILD",c.type);assertEquals(t.id,c.target);assertEquals(2,c.level);assertNull(t.work)
  assertEquals(110L,Rules.cost(c));val x=apply(w,c);assertEquals(w.nation.treasury-110,x.nation.treasury);assertNotNull(x.towns.first {it.id==t.id}.work);assertEquals(1,x.towns.first {it.id==t.id}.buildings["SCHOOL"])
  assertEquals(2,days(x,MunicipalEngine.duration(c)).towns.first {it.id==t.id}.buildings["SCHOOL"])
  assertTrue(DecreeEngine.preview(x,id,"不要扩建学堂").commands.isEmpty());assertTrue(DecreeEngine.preview(w.copy(nation=w.nation.copy(treasury=0)),id,"扩建学堂").commands.isEmpty());assertTrue(DecreeEngine.preview(w,id,"立即定罪撤职").commands.isEmpty());assertTrue(DecreeEngine.preview(w,id,"扩建学堂，同时扩建常平仓").commands.isEmpty())
 }
 @Test fun allLivingPeopleShareDialogueAndEachCountyHasThreePosts(){
  val w=game();assertEquals(573,w.people.size);assertTrue(w.people.all {DialogueEngine.access(w,it.id)==null})
  w.towns.filter {it.id !in AdministrativeEngine.extraSeeds.map {s->s.id}}.forEach {t->assertEquals(3,TalentEngine.ids(w,t).distinct().size);assertTrue(t.deputy>=0&&t.clerk>=0)}
  val t=w.towns.first {it.rank=="县城"};val p=w.people.first {it.id==t.clerk};val context=DialogueEngine.context(w,p,true);assertTrue(context.contains(t.name));assertTrue(context.contains("主簿"));assertFalse(context.contains("guilty"));assertNotNull(DialogueEngine.access(w.copy(people=w.people.map {if(it.id==p.id)it.copy(alive=false)else it}),p.id))
  val legacy=w.copy(towns=w.towns.filter {it.id !in AdministrativeEngine.extraSeeds.map {s->s.id}}.map {it.copy(deputy=-1,clerk=-1,staffed=false)},people=w.people.take(83));assertEquals(w,MunicipalEngine.initialize(legacy));assertEquals(w,MunicipalEngine.initialize(w))
 }
 @Test fun examsRecruitRealCandidatesAndAppointmentTransfersOnlyAfterTime(){
  val w=game();val scheduled=apply(w,Command("EXAM"));assertEquals(w.people.size,days(scheduled,89).people.size);assertFails {apply(scheduled,Command("EXAM"))}
  val result=days(scheduled,90);assertEquals(w.people.size+2,result.people.size);assertEquals(2,result.people.count {it.role=="待选人才"});assertNotNull(Rules.validate(result,Command("EXAM")))
  val candidate=result.people.last();val t=result.towns.first {it.rank=="县城"};val old=t.official;val appointed=days(apply(result,Command("APPOINT",region=t.region,target=candidate.id.toString(),objective=t.id,mode="长官")),7)
  assertEquals(candidate.id,appointed.towns.first {it.id==t.id}.official);assertEquals("待选人才",appointed.people.first {it.id==old}.role);assertTrue(appointed.people.last().memories.last().contains("赴任"))
  val second=appointed.towns.first {it.id!=t.id&&it.rank=="县城"};val moved=days(apply(appointed,Command("APPOINT",region=second.region,target=candidate.id.toString(),objective=second.id,mode="副官")),7)
  assertEquals(-1,moved.towns.first {it.id==t.id}.official);assertEquals(candidate.id,moved.towns.first {it.id==second.id}.deputy);Rules.verify(moved)
  val restored=SaveJson.decodeFromString<World>(SaveJson.encodeToString(moved));assertEquals(moved,restored)
 }
 @Test fun conversationMandateCanNominateKnownPeopleWithoutInventingOffices(){
  val w=days(apply(game(),Command("EXAM")),90);val person=w.people.first {it.role=="待选人才"};val t=w.towns.first {it.rank=="县城"}
  val appointment=DecreeEngine.preview(w,person.id,"任命你为${t.name}县丞");assertEquals("APPOINT",appointment.commands.single().type);assertEquals(t.id,appointment.commands.single().objective);assertTrue(appointment.description.contains(person.name))
  assertTrue(DecreeEngine.preview(w,person.id,"任命你为天下大元帅").commands.isEmpty());assertEquals("EXAM",DecreeEngine.preview(game(),1,"筹办科举").commands.single().type)
 }
 @Test fun appointmentRejectsDeadUnderqualifiedAndStudyAbsence(){
  val w=game();val t=w.towns.first();val c=Command("APPOINT",region=t.region,target="6",objective=t.id,mode="长官")
  assertNotNull(Rules.validate(w.copy(people=w.people.map {if(it.id==6)it.copy(alive=false)else it}),c));assertNotNull(Rules.validate(w.copy(people=w.people.map {if(it.id==6)it.copy(ability=5)else it}),c));assertNotNull(Rules.validate(apply(w,Command("STUDY_HOME",target="6")),c));assertNotNull(Rules.validate(w,c.copy(target="0")))
 }
 @Test fun palaceConstructionHasCostDelayUpkeepAndBoundedEffects(){
  val w=game();val c=Command("PALACE_BUILD",objective="PALACE",level=2);val x=apply(w,c);assertEquals(w.nation.treasury-360,x.nation.treasury);assertEquals(1,x.estate.levels["PALACE"]);assertFails {apply(x,c)}
  val before=days(x,189);assertNotNull(before.estate.work);val done=days(before,1);assertNull(done.estate.work);assertEquals(2,done.estate.levels["PALACE"]);assertEquals(2L,CrownEngine.maintenance(done));Rules.verify(done)
  assertTrue(DecreeEngine.preview(w,1,"扩建宫殿").commands.single().type=="PALACE_BUILD")
 }
 @Test fun consortsAreAdultVoluntaryAndNeverEnablePregnancy(){
  val w=game();val x=days(apply(w,Command("CONSORT_AUDIENCE")),30);val candidates=x.household.members.filter {it.role=="待选伴侣"};assertEquals(2,candidates.size);assertTrue(candidates.all {it.ageAt(x.day)>=18});assertFalse(x.household.allowPregnancy);assertNull(x.household.pregnancy)
  val p=candidates.first {it.willing};val accepted=days(apply(x,Command("CONSORT_ACCEPT",target=p.id.toString())),7);assertEquals("成年伴侣",accepted.household.members.first {it.id==p.id}.role);assertFalse(accepted.household.allowPregnancy)
  val refused=x.copy(household=x.household.copy(members=x.household.members.map {if(it.id==p.id)it.copy(willing=false)else it}));assertNotNull(Rules.validate(refused,Command("CONSORT_ACCEPT",target=p.id.toString())))
 }
 @Test fun householdConversationsShareStableIdentityRelationshipAndLifeState(){
  val w=game();val registered=apply(w,Command("FAMILY_CONTACT",target="1"));val id=registered.familyPeople.getValue(1);assertEquals(w.nation.treasury,registered.nation.treasury);assertEquals(w.day,registered.day)
  val talked=apply(registered,Command("CONVERSE",target=id.toString(),mode="倾听",objective="近况",text="你近来可好"));assertEquals(w.household.members.first().relationship+3,talked.household.members.first().relationship);assertEquals(talked.household.members.first().relationship,talked.people.last().trust)
  val deceased=talked.copy(household=talked.household.copy(members=talked.household.members.map {it.copy(alive=false,diedDay=talked.day)}));val synced=FamilyEngine.syncPeople(deceased);assertFalse(synced.people.last().alive);assertNotNull(DialogueEngine.access(synced,id));assertEquals(id,FamilyEngine.syncPeople(synced).familyPeople[1]);Rules.verify(synced)
 }
 @Test fun armiesKeepRealCommandersCostsSoldiersAndDeployment(){
  val w=game();val before=CrownEngine.units(w).first();val x=days(apply(w,Command("ARMY_RECRUIT",target=before.id)),30);assertEquals(before.soldiers+1000,CrownEngine.units(x).first().soldiers);assertEquals(w.nation.treasury-35,x.nation.treasury);assertEquals(2L,CrownEngine.maintenance(x))
  val trained=days(apply(x,Command("ARMY_TRAIN",target=before.id)),30);assertEquals(before.training+6,CrownEngine.units(trained).first().training)
  val moved=days(apply(trained,Command("ARMY_MOVE",target=before.id,region=2)),20);assertEquals(2,CrownEngine.units(moved).first().region)
  val recruits=days(apply(w,Command("EXAM")),90);val leader=recruits.people.drop(w.people.size).first {it.ability>=55};val assigned=days(apply(recruits,Command("ARMY_ASSIGN",target="guard",objective=leader.id.toString())),7);assertEquals(leader.id,CrownEngine.units(assigned).first().commander);assertFails {apply(assigned,Command("ARMY_ASSIGN",target="field",objective=leader.id.toString()))}
 }
}
