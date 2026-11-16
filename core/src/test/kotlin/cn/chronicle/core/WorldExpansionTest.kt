package cn.chronicle.core

import org.junit.Test
import kotlin.test.*
import kotlinx.serialization.encodeToString

class WorldExpansionTest {
 private fun game(seed:Long=1847)=NewGame.create("承熙","景衡","综合发展","标准",seed)
 private fun apply(w:World,c:Command)=RealtimeEngine.submit(w,c,w.revision).world
 private fun days(w:World,n:Int):World {var x=w;repeat(n/90){x=RealtimeEngine.advance(x,90).world};if(n%90>0)x=RealtimeEngine.advance(x,n%90).world;return x}
 @Test fun denserAtlasHasRealCountiesStatesBoundariesAndConservedResources(){
  val w=game();assertEquals(189,w.towns.size);assertEquals(171,w.towns.count {it.rank=="县城"});assertEquals(573,w.people.size);assertEquals(w.nation.population,w.towns.sumOf {MunicipalEngine.population(w,it)})
  val s=MapSnapshotMapper.scene(w,1);assertEquals(171,s.groups.count {it.rank=="县"});assertTrue(s.groups.count {it.rank=="州"}>=7);assertTrue(s.territories.count {it.rank=="县"}>=160);assertEquals(120,s.extraPlaces.size);assertEquals(620,w.nation.treasury)
  val original=w.copy(towns=w.towns.filter {it.id !in AdministrativeEngine.extraSeeds.map {s->s.id}},people=w.people.take(237));assertEquals(w,MunicipalEngine.initialize(original));assertEquals(w,MunicipalEngine.initialize(w));MapValidation.verify(s)
 }
 private fun organization(w:World,kind:String):World {
  val founded=days(apply(w,Command("ORG_FOUND",target=w.towns.first().id,objective=kind,text="试行${kind}司")),45)
  assertTrue(founded.intelligence.organizations.single().members.isEmpty());val id=founded.intelligence.organizations.single().id
  return days(apply(founded,Command("ORG_ASSIGN",target=id,objective="2")),7)
 }
 @Test fun organizationsNeedMoneyPersonnelTimeAndReportsAreDeterministic(){
  val w=game();val setup=organization(w,"谍报");assertEquals(w.nation.treasury-100,setup.nation.treasury);assertEquals(52,setup.day);assertEquals(6L,IntelligenceEngine.upkeep(setup))
  val org=setup.intelligence.organizations.single();val cmd=Command("INTEL_SCOUT",target=org.id,region=0);val pending=apply(setup,cmd);assertFails {apply(pending,cmd)};val a=days(pending,30);val b=days(pending,30);assertEquals(a,b);assertEquals(1,a.intelligence.reports.size);assertTrue(a.people[2].memories.last().contains(a.neighbors[0].name))
  val counter=days(apply(a,Command("INTEL_COUNTER",target=org.id)),15);assertTrue(counter.intelligence.threat<a.intelligence.threat);assertTrue(counter.intelligence.reports.last().text.contains("不自动刑事处罚"));Rules.verify(counter)
  val freed=days(apply(counter,Command("ORG_RELEASE",target=org.id,objective="2")),1);assertTrue(freed.intelligence.organizations.single().members.isEmpty());assertNotNull(Rules.validate(freed,cmd))
 }
 @Test fun covertViolenceHasFailureAndDeathBranchesWithoutResurrection(){
  var successes=0;var failures=0
  for(seed in 0L..30L){val w=organization(game(seed),"刺客");val org=w.intelligence.organizations.single();val done=days(apply(w,Command("ASSASSINATE",target=org.id,objective="3")),30);val victim=done.people.first {it.id==3}
   assertTrue(done.nation.morale<w.nation.morale);assertTrue(done.nation.stability<w.nation.stability);assertEquals(35,done.intelligence.organizations.single().exposure)
   if(!victim.alive){successes++;assertNotNull(DialogueEngine.access(done,3));assertNotNull(Rules.validate(done,Command("TRIAL")));assertTrue(done.regions.none {it.official==3});assertEquals("已故",days(done,1).people[3].role)}else failures++
   Rules.verify(done)
  };assertTrue(successes>0&&failures>0)
 }
 private fun strong():World {val w=game();return w.copy(nation=w.nation.copy(treasury=5000),armies=CrownEngine.units(w).map {if(it.id=="north")it.copy(soldiers=30000,training=100,morale=100,supply=100)else it})}
 @Test fun warRequiresMobilizationAndVictoryChangesPersistentMapOwnership(){
  val w=strong();var x=days(apply(w,Command("DECLARE_WAR",target="north",region=0)),7);assertNotNull(x.war);assertNotNull(Rules.validate(x,Command("WAR_BATTLE")));assertEquals(0,x.neighbors[0].relation);x=days(x,14)
  repeat(3){x=days(apply(x,Command("WAR_BATTLE")),14)};assertEquals(75,x.war!!.progress);assertTrue(CrownEngine.units(x).first {it.id=="north"}.soldiers<30000);assertTrue(x.nation.population<w.nation.population);assertEquals(x.nation.population,x.regions.sumOf {it.population})
  x=days(apply(x,Command("WAR_PEACE")),7);assertNull(x.war);assertEquals("nation",MapSnapshotMapper.scene(x,1).territories.first {it.id=="frontier-0"}.polityId);assertTrue(x.nation.treasury<w.nation.treasury);assertTrue(x.nation.morale<w.nation.morale);assertEquals(x,SaveJson.decodeFromString<World>(SaveJson.encodeToString(x)))
  assertNotNull(Rules.validate(x,Command("DECLARE_WAR",target="north",region=0)))
 }
 @Test fun weakArmyLosesAndCannotAcquireLandByTyping(){
  val w=strong().let {w->w.copy(armies=CrownEngine.units(w).map {if(it.id=="north")it.copy(soldiers=1000,training=0,morale=10,supply=30)else it},neighbors=w.neighbors.map {it.copy(strength=100)})};var x=days(apply(w,Command("DECLARE_WAR",target="north",region=1)),21)
  repeat(3){if(x.war!=null)x=days(apply(x,Command("WAR_BATTLE")),14)};assertNull(x.war);assertEquals("neighbor-1",MapSnapshotMapper.scene(x,1).territories.first {it.id=="frontier-1"}.polityId);assertTrue(x.nation.stability<w.nation.stability)
 }
 @Test fun canalsAreRealDelayedProjectsWithMapRoutesAndMaintenance(){
  val w=game();val c=CanalEngine.plan(w,"county-jingji-01","place-0");val x=apply(w,c);assertEquals(w.nation.treasury-Rules.cost(c),x.nation.treasury);assertTrue(x.canals.isEmpty());assertFails {apply(x,c)}
  val before=days(x,c.days-1);assertTrue(before.canals.isEmpty());val done=days(before,1);assertEquals(1,done.canals.size);assertTrue(done.projects.none {it.kind=="CANAL"});assertTrue(MapSnapshotMapper.scene(done,1).routes.any {it.kind=="canal"&&it.fromId==c.target&&it.toId==c.objective});Rules.verify(done)
  assertNotNull(Rules.validate(w,c.copy(days=1)));assertNotNull(Rules.validate(w,c.copy(objective=c.target)));assertEquals(done,SaveJson.decodeFromString<World>(SaveJson.encodeToString(done)))
 }
}
