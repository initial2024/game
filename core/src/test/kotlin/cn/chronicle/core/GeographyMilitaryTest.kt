package cn.chronicle.core

import kotlinx.serialization.encodeToString
import org.junit.Test
import kotlin.test.*

class GeographyMilitaryTest {
 private fun game()=NewGame.create("山河","景衡","综合发展","宽裕",1847)
 private fun apply(w:World,c:Command)=RealtimeEngine.submit(w,c,w.revision).world
 private fun days(w:World,n:Int):World {var x=w;repeat(n/90){x=RealtimeEngine.advance(x,90).world};if(n%90>0)x=RealtimeEngine.advance(x,n%90).world;return x}
 @Test fun localOfficerConversationCanDraftBoundedDecreesWithoutExecuting(){
  val w=game();val u=w.localDefense.units.first {it.kind=="县兵"};val draft=DecreeEngine.preview(w,u.commander,"招募50名县兵")
  assertEquals("LOCAL_RECRUIT",draft.commands.single().type);assertEquals(u.id,draft.commands.single().target);assertEquals(50,draft.commands.single().level)
  assertEquals(w,game());assertTrue(DecreeEngine.preview(w,u.commander,"招募100000名县兵").commands.isEmpty());assertTrue(DecreeEngine.preview(w,u.commander,"不要招募50名县兵").commands.isEmpty())
  val build=DecreeEngine.preview(w,u.commander,"扩建学堂");assertEquals(u.home,build.commands.single().target)
 }
 @Test fun stateGovernorCoordinatesSuppliesButCannotCreateGrain(){
  val w=game();val u=w.localDefense.units.first {it.kind=="州军"}
  fun withGovernor(ability:Int)=w.copy(people=w.people.map {if(it.id==u.governor)it.copy(ability=ability,loyalty=100)else it})
  val strong=LocalDefenseEngine.quarter(withGovernor(100),mutableListOf());val weak=LocalDefenseEngine.quarter(withGovernor(0),mutableListOf())
  assertTrue(strong.localDefense.units.first {it.id==u.id}.supply>weak.localDefense.units.first {it.id==u.id}.supply)
  val starving=LocalDefenseEngine.quarter(withGovernor(100).copy(resources=w.resources.copy(militaryFoodRatio=0)),mutableListOf());assertEquals(0,starving.localDefense.units.first {it.id==u.id}.supply)
 }
 @Test fun geographyComesFromUploadedAtlasAndEngineeringUsesIt(){
  val w=game();assertEquals("2553b2c5af23f80895a315154ece758d728813c39095f9649d9aad230a82fd56",AtlasTerrainData.sourceSha);assertEquals(193,AtlasTerrainData.siteRows.lines().size);assertEquals(4355,AtlasTerrainData.gridRows.lines().size)
  val mountain=TerrainEngine.profile(w,"city-r1");val plain=TerrainEngine.profile(w,"county-jingji-01");assertEquals("高原",mountain.kind);assertTrue(mountain.height>plain.height)
  fun building(id:String):Command {val t=w.towns.first {it.id==id};return Command("URBAN_BUILD",region=t.region,target=id,objective="SCHOOL",level=2)}
  val a=building("city-r1");val b=building("county-jingji-01");assertTrue(Rules.cost(w,a)>Rules.cost(w,b));assertTrue(MunicipalEngine.duration(w,a)>MunicipalEngine.duration(w,b));val built=apply(w,a);assertEquals(w.nation.treasury-Rules.cost(w,a),built.nation.treasury);assertEquals(mountain,w.resources.sites.getValue(a.target).terrain)
 }
 @Test fun resourcesConstrainBuildingsAndOreCannotRegrowOnReload(){
  val w=game();val empty=w.copy(resources=w.resources.copy(stocks=w.resources.stocks+("IRON" to 0)));assertNotNull(Rules.validate(empty,Command("FACTORY")));assertFails {apply(empty,Command("FACTORY"))};assertEquals(0,ResourceEngine.available(empty,"IRON"))
  val t=w.towns.first {TerrainEngine.profile(w,it.id).iron>=50};val c=Command("URBAN_BUILD",region=t.region,target=t.id,objective="MINE",level=1);val initial=w.resources.sites.getValue(t.id).iron;val queued=apply(w,c);val working=days(queued,MunicipalEngine.duration(w,c));val cycle=90-working.day%90;val produced=days(working,cycle);assertTrue(produced.resources.yields.getValue(t.id).iron>0);assertTrue(produced.resources.sites.getValue(t.id).iron<initial)
  assertEquals(produced.resources,WorldSetup.initialize(produced).resources);assertEquals(produced,SaveJson.decodeFromString<World>(SaveJson.encodeToString(produced)))
  val dry=produced.resources.sites.getValue(t.id).copy(iron=0,coal=0,stone=0);val exhausted=days(produced.copy(resources=produced.resources.copy(sites=produced.resources.sites+(t.id to dry))),90);assertEquals(0,exhausted.resources.yields.getValue(t.id).iron)
 }
 @Test fun soilIrrigationAndTransportChangeRealHarvestAndProduction(){
  val w=game();val base=ResourceEngine.quarter(w.copy(day=90),mutableListOf(),mutableListOf());val t=w.towns.first {it.id=="county-jingji-01"};val improved=w.copy(day=90,towns=w.towns.map {if(it.id==t.id)it.copy(buildings=it.buildings+("IRRIGATION" to 2))else it});val result=ResourceEngine.quarter(improved,mutableListOf(),mutableListOf());assertTrue(result.resources.yields.getValue(t.id).grain>base.resources.yields.getValue(t.id).grain)
  val wet=w.resources.sites.values.maxBy {it.terrain.fertility}.terrain;val dry=w.resources.sites.values.minBy {it.terrain.fertility}.terrain;assertTrue(wet.fertility>dry.fertility);assertTrue(base.resources.totals.grain>0);assertTrue(base.resources.grainTaxValueWan>0)
  val duplicate=ResourceEngine.quarter(base,mutableListOf(),mutableListOf());assertEquals(base,duplicate)
 }
 @Test fun localGarrisonsHaveNamedOfficersAndMobilizationConservesPeople(){
  val w=game();assertEquals(778,w.people.size);assertEquals(197,w.localDefense.units.size);assertEquals(171,w.localDefense.units.count {it.kind=="县兵"});assertEquals(8,w.localDefense.units.count {it.kind=="州军"});assertTrue(w.localDefense.units.all {u->w.people.any {it.id==u.commander&&it.alive}})
  val u=w.localDefense.units.first {it.kind=="县兵"};val c=Command("LOCAL_MOBILIZE",target=u.id,objective="north",level=50);val x=days(apply(w,c),RealtimeEngine.duration(w,c));assertEquals(LocalDefenseEngine.troops(w),LocalDefenseEngine.troops(x));assertEquals(u.soldiers-50,x.localDefense.units.first {it.id==u.id}.soldiers);assertEquals(CrownEngine.units(w).first {it.id=="north"}.soldiers+50,CrownEngine.units(x).first {it.id=="north"}.soldiers)
  val p=w.people.first {it.id==u.commander};val context=DialogueEngine.context(w,p,true);assertTrue(context.contains(u.name));assertTrue(context.contains("俸禄"));assertNull(DialogueEngine.access(w,p.id))
 }
 @Test fun salariesUseExactLiangWithFundRemainderAndNoDuplicateQuarter(){
  val w=game().copy(day=90);val lines=PayrollEngine.lines(w);assertTrue(lines.count {it.key.startsWith("person:")}>700);val expected=lines.sumOf {it.liang};val paid=PayrollEngine.quarter(w,mutableListOf());val spent=(w.nation.treasury-paid.nation.treasury)*10000+w.payroll.reserveLiang-paid.payroll.reserveLiang;assertEquals(expected,spent);assertEquals(expected,paid.payroll.lastPayments.values.sum());assertTrue(paid.payroll.arrears.isEmpty());assertEquals(paid,PayrollEngine.quarter(paid,mutableListOf()))
 }
 @Test fun unpaidOfficialsAndTroopsReactAndClearingDebtCostsTreasury(){
  val w=game().copy(day=90).let {it.copy(nation=it.nation.copy(treasury=0,debt=2000))};val u=w.localDefense.units.first();val person=w.people.first {it.id==u.commander};val unpaid=PayrollEngine.quarter(w,mutableListOf());assertTrue(unpaid.payroll.arrears.values.sum()>0);assertTrue(unpaid.people.first {it.id==person.id}.loyalty<person.loyalty);assertTrue(unpaid.localDefense.units.first().morale<u.morale)
  val funded=unpaid.copy(nation=unpaid.nation.copy(treasury=5));val owed=funded.payroll.arrears.values.sum();val settled=apply(funded,Command("PAY_ARREARS"));assertEquals(4,settled.nation.treasury);assertTrue(settled.payroll.arrears.values.sum()<owed);assertTrue(settled.payroll.reserveLiang>=0);Rules.verify(settled)
 }
 @Test fun tradeCannotCreateMoneyByRoundtrippingOrIgnoreSeasonalLimits(){
  val w=game();val buy=Command("TRADE_RESOURCE",target="采购",objective="IRON",level=1);val bought=days(apply(w,buy),7);assertEquals(w.nation.treasury-2,bought.nation.treasury);assertEquals(ResourceEngine.available(w,"IRON")+50,ResourceEngine.available(bought,"IRON"));val sold=days(apply(bought,buy.copy(target="出售")),7);assertEquals(w.nation.treasury-1,sold.nation.treasury);assertEquals(ResourceEngine.available(w,"IRON"),ResourceEngine.available(sold,"IRON"));assertNotNull(Rules.validate(sold,buy.copy(level=4)))
  val full=w.copy(resources=w.resources.copy(stocks=w.resources.stocks+("IRON" to ResourceEngine.capacity(w,"IRON"))));assertTrue(ResourceEngine.tradeValidate(full,buy)!!.contains("容量"));assertFails {apply(full,buy)}
 }
 @Test fun terrainAndSuppliesChangeCampaignConditions(){
  val w=game();assertTrue(TerrainEngine.defense(w,0)>0);assertTrue(!TerrainEngine.profile(w,"neighbor-city-0").land);val noPorts=w.copy(towns=w.towns.map {it.copy(buildings=it.buildings+("PORT" to 0))});assertNotNull(Rules.validate(noPorts,Command("DECLARE_WAR",target="north",region=0)));val noIron=w.copy(resources=w.resources.copy(stocks=w.resources.stocks+("IRON" to 0)));assertNotNull(Rules.validate(noIron,Command("DECLARE_WAR",target="north",region=0)))
 }
}
