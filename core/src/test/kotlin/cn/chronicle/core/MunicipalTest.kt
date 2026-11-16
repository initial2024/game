package cn.chronicle.core

import org.junit.Test
import kotlin.test.*
import kotlinx.serialization.encodeToString

class MunicipalTest {
 private fun game()=NewGame.create("承熙","景衡","综合发展","标准",1847)
 private fun command(w:World,id:String="county-jingji-01",kind:String="GRANARY"):Command {val t=w.towns.first {it.id==id};return Command("URBAN_BUILD",t.region,target=id,objective=kind,level=t.buildings.getValue(kind)+1)}
 private fun submit(w:World,c:Command)=RealtimeEngine.submit(w,c,w.revision).world
 private fun days(w:World,n:Int):World {var x=w;var left=n;while(left>0){val d=minOf(90,left);x=RealtimeEngine.advance(x,d).world;left-=d};return x}
 @Test fun originalFourteenPrefecturesAndFiftyNineCountiesAreRegistered(){
  val w=game();assertEquals(77,w.towns.size);assertEquals(14,w.towns.count {it.rank=="府城"});assertEquals(59,w.towns.count {it.rank=="县城"});assertEquals(620,w.nation.treasury)
  assertEquals(w.nation.population,w.towns.sumOf {MunicipalEngine.population(w,it)});assertEquals(83,w.people.size);Rules.verify(w)
  val scene=MapSnapshotMapper.scene(w,1);assertEquals(59,scene.groups.count {it.rank=="县"});assertEquals(14,scene.groups.count {it.rank=="府"});assertEquals("pref-jingji",scene.groups.first {it.id=="admin-county-jingji-01"}.parentId)
  assertEquals(1,MapSnapshotMapper.regionId(w,"county-jingji-01"));assertTrue(scene.places.containsKey("county-jingji-01"));MapValidation.verify(scene)
 }
 @Test fun constructionPaysOnceHasRealDurationAndCannotBeReplayed(){
  val w=game();val c=command(w);val started=submit(w,c);val t=started.towns.first {it.id==c.target}
  assertEquals(w.nation.treasury-Rules.cost(c),started.nation.treasury);assertEquals(1,t.buildings["GRANARY"]);assertEquals(80,t.work!!.remainingDays)
  assertFails {submit(started,c)};assertFails {submit(w,c.copy(level=0))};assertFails {submit(w,c.copy(region=0))}
  val before=days(started,79);assertEquals(1,before.towns.first {it.id==c.target}.work!!.remainingDays)
  val done=days(before,1);val built=done.towns.first {it.id==c.target};assertEquals(2,built.buildings["GRANARY"]);assertNull(built.work);assertEquals(w.nation.agriculture+1,done.nation.agriculture)
  assertEquals(w.nation.population,done.towns.sumOf {MunicipalEngine.population(done,it)});assertFails {submit(done,c)};assertEquals(done.towns,days(done,1).towns)
 }
 @Test fun noExtraTaxIsCreatedByExpandingTheInitialCatalogue(){
  val w=game();val ledger=mutableListOf<String>();assertEquals(w,MunicipalEngine.quarter(w,ledger));assertTrue(ledger.isEmpty())
  val t=w.towns.first();val expanded=w.copy(towns=w.towns.map {if(it.id==t.id)it.copy(buildings=it.buildings+("MARKET" to 2))else it})
  val paid=MunicipalEngine.quarter(expanded,ledger);assertEquals(w.nation.treasury+4,paid.nation.treasury);assertEquals(20,paid.nation.income);assertEquals(4,paid.nation.expense);assertEquals(1,ledger.size)
 }
 @Test fun buildingsAreCitySpecificAndSurviveSerializationAndRename(){
  val w=game();val c=command(w);val started=submit(w,c);val other=w.towns.first {it.id=="county-jingji-02"};assertEquals(other,started.towns.first {it.id==other.id})
  val restored=SaveJson.decodeFromString<World>(SaveJson.encodeToString(started));assertEquals(started,restored)
  val renamed=days(submit(restored,Command("CITY_RENAME",region=1,target=c.target,objective="松河县")),1)
  assertEquals("松河县",renamed.towns.first {it.id==c.target}.name);assertEquals("松河县",MapSnapshotMapper.scene(renamed,1).places[c.target]!!.name);assertEquals("松河县",MapSnapshotMapper.scene(renamed,1).groups.first {it.id=="admin-${c.target}"}.name)
 }
 @Test fun cityPoliciesDoNotAutomaticallyConvictOfficials(){
  val w=game();val t=w.towns.first {it.id=="county-jingji-01"};val x=days(submit(w,Command("URBAN_AUDIT",t.region,target=t.id)),5)
  assertEquals((t.corruption-3).coerceAtLeast(0),x.towns.first {it.id==t.id}.corruption);assertEquals(w.caseFile,x.caseFile);assertEquals(w.people.first {it.id==t.official}.role,x.people.first {it.id==t.official}.role)
 }
 @Test fun legacyExpansionIsDeterministicIdempotentAndDoesNotInventMoney(){
  val fresh=game();val old=fresh.copy(towns=emptyList(),people=fresh.people.take(10));val upgraded=MunicipalEngine.initialize(old)
  assertEquals(fresh,upgraded);assertEquals(upgraded,MunicipalEngine.initialize(upgraded));assertEquals(old.nation,upgraded.nation)
 }
 @Test fun oldQueuedConversationIsRefundedOnceWithoutWaiting(){
  val w=game().let {it.copy(nation=it.nation.copy(treasury=it.nation.treasury-2),pending=listOf(PendingOrder(1,Command("CONVERSE",target="1",mode="倾听"),1,2)))}
  val out=RealtimeEngine.refundLegacyChat(w)!!;assertEquals(w.nation.treasury+2,out.world.nation.treasury);assertTrue(out.world.pending.isEmpty());assertEquals(w.day,out.world.day);assertNull(RealtimeEngine.refundLegacyChat(out.world))
 }
 @Test fun restoredAndNewCitiesAlsoReceiveActualBuildings(){
  var w=submit(game(),Command("CITY_RESTORE",target="ruin-r0"));w=days(w,90);assertTrue(w.towns.any {it.id=="ruin-r0"});assertNull(Rules.validate(w,command(w,"ruin-r0")))
  w=submit(w,Command("CITY_FOUND",region=0));w=days(w,120);assertTrue(w.towns.any {it.id=="settlement-r0"});assertNull(Rules.validate(w,command(w,"settlement-r0")));MapValidation.verify(MapSnapshotMapper.scene(w,1))
 }
 @Test fun maintenanceCannotBypassCreditLimitAndUnfundedUpgradesDecay(){
  val w=game();val t=w.towns.first();val upgraded=w.copy(nation=w.nation.copy(treasury=0,debt=2000),towns=w.towns.map {if(it.id==t.id)it.copy(buildings=it.buildings+("CLINIC" to 3))else it})
  val ledger=mutableListOf<String>();val next=MunicipalEngine.quarter(upgraded,ledger);assertEquals(2000,next.nation.debt);assertEquals(0,next.nation.treasury);assertEquals(2,next.towns.first().buildings["CLINIC"]);assertTrue(ledger.any {it.contains("预算不足")});Rules.verify(next)
 }
 @Test fun censusRemainsConservedAfterQuarterlyPopulationGrowth(){
  var w=game();repeat(8){w=days(w,90);assertEquals(w.nation.population,w.regions.sumOf {it.population});assertEquals(w.nation.population,w.towns.sumOf {t->MunicipalEngine.population(w,t)})}
 }

 @Test fun replacementGovernorCanStillReceiveFreeCourtCorrespondence(){
  val w=game();val id=w.people.maxOf {it.id}+1;val p=Person(id,"新任知府","澄江地方官",0,120,60,65,"依法施政","延续改革")
  val next=w.copy(people=w.people+p,regions=w.regions.map {if(it.id==0)it.copy(official=id)else it})
  assertEquals(p,MunicipalEngine.officer(next,next.towns.first {it.id=="city-r0"}));assertNull(DialogueEngine.access(next,id));assertTrue(DialogueEngine.context(next,p,true).contains("常平仓"));assertNull(Rules.validate(next,Command("CONVERSE",target=id.toString(),mode="倾听",objective="近况")))
 }

}
