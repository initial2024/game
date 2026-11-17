package cn.chronicle.core

import org.junit.Test
import kotlin.test.*
import kotlinx.serialization.encodeToString

class ChronicleExpansionTest {
 private fun game(seed:Long=1847,guided:Boolean=false)=ChronicleEngine.start(NewGame.create("承熙","景衡","综合发展","宽裕",seed),guided,true).let {it.copy(nation=it.nation.copy(treasury=20000))}
 private fun act(w:World,c:Command):World {assertNull(Rules.validate(w,c),"${c.type}/${c.mode}");return Rules.apply(w,c,w.revision,false).world.also {Rules.verify(it)}}
 private fun spawn(w:World,b:CaseBlueprint)=InvestigationEngine.spawn(w,townId=w.towns.first {InvestigationEngine.qualified(w,b,it)}.id,blueprintId=b.id)
 private fun open(w:World):World=act(w,Command("CASE_OPEN",target=w.caseBook.cases.last().id,mode="御前受理"))
 private fun collect(w:World):World {
  var x=open(w);val id=x.caseBook.cases.last().id
  for(type in listOf("CASE_VISIT","CASE_RECORD","CASE_PHYSICAL"))x=act(x,Command(type,target=id,mode="委派"))
  x.caseBook.cases.last().exhibits.forEach {e->x=act(x,Command("CASE_VERIFY",target=id,objective=e.id))}
  x=act(x,Command("CASE_PROTECT",target=id));return act(x,Command("CASE_CROSS",target=id))
 }
 @Test fun sixtyFourAuthoredCasesHaveDeterministicSolvableEvidenceAndDistinctTruths(){
  assertEquals(64,CaseLibrary.all.size);assertEquals(8,CaseLibrary.all.map {it.domain}.distinct().size)
  val base=game().let {it.copy(towns=it.towns.map {t->t.copy(buildings=t.buildings+("WORKSHOP" to 1))})}
  CaseLibrary.all.forEach {b->
   val branches=linkedMapOf<String,World>()
   for(seed in 0L..255){val w=spawn(base.copy(seed=seed),b);branches.putIfAbsent(w.caseBook.cases.last().truth,w);if(branches.size==6)break}
   assertEquals(6,branches.size,b.title)
   branches.forEach {(truth,w)->
    assertEquals(w,spawn(base.copy(seed=w.seed),b));val c=w.caseBook.cases.last();assertEquals(b.id,c.blueprint)
    assertFalse(MemoryEngine.context(w).contains(truth),"secret fact leaked")
    val solved=collect(w);val f=solved.caseBook.cases.last();assertEquals(c.truth,f.truth);assertEquals(c.culprit,f.culprit)
    assertEquals(solved,SaveJson.decodeFromString<World>(SaveJson.encodeToString(solved)))
    val legal=Command("CASE_DECIDE",target=f.id,mode="依法追究")
    if(f.culprit>=0){assertNull(Rules.validate(solved,legal),"${b.title}/$truth/${InvestigationEngine.score(f)}");val done=act(solved,legal);assertTrue(done.caseBook.cases.last().resolved);assertNotNull(Rules.validate(done,legal))}
    else {assertNotNull(Rules.validate(solved,legal));val done=act(solved,legal.copy(mode="制度改革"));assertTrue(done.caseBook.cases.last().resolved)}
   }
  }
 }
 @Test fun coercionNeverManufacturesLegalEvidenceAndFalseMaterialStaysFalse(){
  var x=open(spawn(game(),CaseLibrary.all.first()));val id=x.caseBook.cases.last().id;val before=x.people.first {it.id==x.caseBook.cases.last().witness}
  x=act(x,Command("CASE_PRESSURE",target=id));assertEquals(0,InvestigationEngine.score(x.caseBook.cases.last()));assertTrue(x.people.first {it.id==before.id}.trust<before.trust);assertNotNull(Rules.validate(x,Command("CASE_DECIDE",target=id,mode="依法追究")))
 }
 @Test fun covertIdentityCannotReceiveRoyalPetitionOrOrderPublicJudgment(){
  val base=spawn(game(),CaseLibrary.all.first());val travel=RealtimeEngine.submit(base,Command("DEPART",region=0,days=90),base.revision).world
  val arrived=RealtimeEngine.advance(travel,travel.journey!!.remainingTravel).world;val id=arrived.caseBook.cases.last().id
  assertNotNull(Rules.validate(arrived,Command("CASE_OPEN",target=id,mode="御前受理")))
  val covert=act(arrived,Command("CASE_OPEN",target=id,mode="暗访"));assertNotNull(Rules.validate(covert,Command("CASE_RECORD",target=id)))
  assertNotNull(Rules.validate(covert,Command("CASE_REQUEST")));assertNotNull(Rules.validate(covert,Command("TRIAL")))
 }
 @Test fun stateSizedGuardsReassignExistingTroopsAndProtectionDoesNotReset(){
  for(scale in listOf("小国","中等国家","超级大国")){
   val raw=NewGame.create("承熙","景衡","综合发展","标准",1847,scale=scale);val w=ChronicleEngine.start(raw)
   assertEquals(raw.localDefense.units.sumOf {it.soldiers},w.localDefense.units.sumOf {it.soldiers});assertTrue(w.sovereign.guard.allocated>0);assertTrue(SovereignEngine.guardUpkeep(w)>0)
   val off=act(w,Command("BEGINNER_PROTECTION",mode="关闭"));val late=off.copy(day=400)
   assertEquals(0,act(late,Command("BEGINNER_PROTECTION",mode="开启")).sovereign.beginnerUntil)
   val risky=w.copy(day=40,sovereign=w.sovereign.copy(health=1,warningDue=40,warnings=2,security=0),intelligence=w.intelligence.copy(threat=100),localDefense=w.localDefense.copy(units=emptyList()))
   assertTrue(SovereignEngine.advance(risky,mutableListOf()).sovereign.health>=1)
  }
 }
 @Test fun unwillingAdultArrangementPersistsAndCanLeadToPetitionAndCompensation(){
  var x=game();val id=x.people.maxOf {it.id}+1;val p=Person(id,"闻清和","当地行商",0,100,55,45,"重视自主","平安生活",trust=25)
  x=x.copy(people=x.people+p,adventure=AdventureState(listOf(Encounter(id,0,0,"女",true))))
  val cash=x.nation.treasury;x=act(x,Command("ENCOUNTER_KIN",target="$id",mode="婚配",disguise="强制"));assertEquals(cash-40,x.nation.treasury)
  assertTrue(x.people.last().hostility>=20);assertFalse(x.household.members.last().willing);assertFalse(x.household.allowPregnancy)
  val isolated=x.copy(household=x.household.copy(allowPregnancy=true,members=x.household.members.takeLast(1)))
  assertNotNull(Rules.validate(isolated,Command("TRY_HEIR")))
  x=InvestigationEngine.quarter(x.copy(day=90),mutableListOf());val petition=x.caseBook.cases.first {it.grievancePerson==id};assertEquals("王命强制",petition.truth)
  x=act(x,Command("CASE_OPEN",target=petition.id,mode="御前受理"));x=act(x,Command("CASE_RECORD",target=petition.id));x=act(x,Command("CASE_VERIFY",target=petition.id,objective=x.caseBook.cases.first {it.id==petition.id}.exhibits.single().id))
  x=act(x,Command("CASE_DECIDE",target=petition.id,mode="纠正王命"));assertTrue(x.adventure.encounters.single().released);assertTrue(x.people.first {it.id==id}.hostility>0);assertFalse(x.household.members.last().willing)
 }
 @Test fun minorEncounterCannotMarryAndRevealedIdentityIsRemembered(){
  var w=game();val id=w.people.maxOf {it.id}+1;w=w.copy(people=w.people+Person(id,"若谷","当地学徒",0,56,70,50,"求学","平安成长"),adventure=AdventureState(listOf(Encounter(id,0,0,"男"))))
  assertNotNull(Rules.validate(w,Command("ENCOUNTER_KIN",target="$id",mode="婚配",disguise="强制")));assertTrue(DialogueEngine.hidden(w,w.people.last()))
  val travel=RealtimeEngine.submit(w,Command("DEPART",region=0,days=90),w.revision).world
  val arrived=RealtimeEngine.advance(travel,travel.journey!!.remainingTravel).world
  val reveal=act(arrived,Command("REVEAL"));assertFalse(DialogueEngine.hidden(reveal,reveal.people.last()));assertFalse(DialogueEngine.hidden(reveal.copy(journey=null),reveal.people.last()))
 }
 @Test fun quarterlyReportsStopFastClockAtBoundaryAndBorrowingAlwaysCreatesDebt(){
  val w=game(guided=true).copy(day=80);val out=RealtimeEngine.advance(w,64,true);assertEquals(90,out.world.day);assertTrue(ChronicleEngine.needsPause(out.world));assertEquals(1,out.world.chronicle.bulletins.size)
  val ack=act(out.world,Command("ACK_REPORT"));assertFalse(ChronicleEngine.needsPause(ack));val borrowed=act(ack,Command("BRIDGE_LOAN",level=200));assertEquals(ack.nation.treasury+200,borrowed.nation.treasury);assertEquals(ack.nation.debt+200,borrowed.nation.debt)
 }
 @Test fun secretContractsArePaidReproducibleBoundedAndDoNotHuntSuccessors(){
  val w=game();val command=Command("ROYAL_CONTRACT",target="hired",objective="foreign:0",mode="刺杀",level=200)
  val hired=act(w,command);assertEquals(w.nation.treasury-200,hired.nation.treasury);assertEquals(30,hired.sovereign.missions.single().due)
  val a=SovereignEngine.advance(hired.copy(day=30),mutableListOf());val b=SovereignEngine.advance(hired.copy(day=30),mutableListOf());assertEquals(a,b);assertTrue(a.sovereign.missions.single().result.isNotBlank());assertEquals(a,SovereignEngine.advance(a,mutableListOf()))
  val changed=hired.copy(day=30,sovereign=hired.sovereign.copy(foreign=hired.sovereign.foreign.mapIndexed {i,c->if(i==0)c.copy(generation=2)else c}));val stopped=SovereignEngine.advance(changed,mutableListOf());assertEquals(hired.nation.treasury+160,stopped.nation.treasury);assertEquals(2,stopped.sovereign.foreign.first().generation)
  assertNotNull(Rules.validate(w,command.copy(objective="unregistered")));assertNotNull(Rules.validate(w,command.copy(level=-100)))
 }
 @Test fun heirTransferChangesMonarchAndDoesNotCopyJourney(){
  var w=game().copy(household=Household(members=listOf(FamilyMember(4,"景宁","子女",-14*360))))
  w=act(w,Command("DESIGNATE_HEIR",target="4"));val x=SovereignEngine.succession(w,true,mutableListOf());assertEquals("景宁",x.ruler);assertEquals(2,x.sovereign.generation);assertTrue(x.sovereign.regencyUntil>x.day);assertFalse(x.ended);assertNull(x.journey);assertEquals(-1,x.sovereign.heir)
  assertNotNull(Rules.validate(x.copy(household=x.household.copy(allowPregnancy=true,members=x.household.members+FamilyMember(5,"成年伴侣","成年伴侣",-25*360))),Command("TRY_HEIR")))
  assertTrue(SovereignEngine.succession(game(),true,mutableListOf()).ended)
 }
}
