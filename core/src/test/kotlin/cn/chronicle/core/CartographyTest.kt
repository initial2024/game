package cn.chronicle.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class CartographyTest {
 private fun game()=NewGame.create("古\"国\\名\n山河","景衡","综合发展","标准",1847)
 private fun submit(w:World,c:Command)=RealtimeEngine.submit(w,c,w.revision).world
 @Test fun oldWorldLoadsWithoutChangingItsEraOrInventingHistory(){
  val json=SaveJson.encodeToJsonElement(game()).jsonObject
  val legacy=JsonObject(json.filterKeys {it !in listOf("cartography","setting")})
  val w=SaveJson.decodeFromJsonElement<World>(legacy);assertNull(w.cartography);assertEquals("工业初兴",w.setting)
  val s=MapSnapshotMapper.scene(w,1);MapValidation.verify(s);assertEquals(8,s.extraPlaces.size);assertEquals(w.name,s.polities.first().name);assertTrue(w.recent.isEmpty())
  assertEquals(s,MapValidation.parse(SaveJson.encodeToString(s)))
 }
 @Test fun stableIdsSurviveNamesAndWorldSlotsAreIsolated(){
  val w=game();val a=MapSnapshotMapper.scene(w,1);val b=MapSnapshotMapper.scene(w.copy(name="另一国"),2)
  assertNotEquals(a.id,b.id);assertEquals(a.extraPlaces,b.extraPlaces);assertNotEquals(a.meta.title,b.meta.title)
  assertEquals(0,MapSnapshotMapper.regionId(w,"city-r0"));assertNull(MapSnapshotMapper.regionId(w,"neighbor-city-0"));assertNull(MapSnapshotMapper.regionId(w,"invented"))
 }
 @Test fun fullAndVersionedDeltaDispatch(){
  val s=MapSnapshotMapper.scene(game(),1);val d=MapDeltaDispatcher();assertEquals("applyScenario",d.next(s)!!.method);assertNull(d.next(s));assertNull(d.next(s.copy(revision=0)))
  val changed=s.copy(revision=1,placeStates=s.placeStates+("ruin-r0" to MapPlaceState("planned")))
  val message=d.next(changed)!!;assertEquals("applyWorldDelta",message.method);val delta=SaveJson.parseToJsonElement(message.json).jsonObject;assertEquals(1,delta["revision"]!!.jsonPrimitive.int);assertEquals(setOf("placeStates"),delta["changes"]!!.jsonObject.keys)
  assertEquals("applyScenario",d.next(changed.copy(id="game-slot-2"))!!.method)
  assertEquals("applyScenario",d.next(changed.copy(id="game-slot-2",revision=2,geography=MapGeography("custom",listOf(listOf(listOf(90.0,20.0),listOf(120.0,20.0),listOf(110.0,40.0))))))!!.method)
 }
 @Test fun rejectsBadGeographyReferencesCyclesAndPrototypeIds(){
  val s=MapSnapshotMapper.scene(game(),1)
  assertFails {MapValidation.verify(s.copy(extraPlaces=s.extraPlaces+MapPoint("evil",Double.NaN,30.0)))}
  assertFails {MapValidation.verify(s.copy(places=s.places+("constructor" to MapPlace())))}
  assertFails {MapValidation.verify(s.copy(groups=listOf(MapGroup("cycle",parentId="cycle"))))}
  assertFails {MapValidation.verify(s.copy(routes=listOf(MapRoute("r",fromId="missing",toId="city-r0"))))}
  assertFails {MapValidation.verify(s.copy(placeStates=mapOf("city-r0" to MapPlaceState("immortal"))))}
  assertFails {MapValidation.verify(s.copy(geography=MapGeography("custom")))}
  for(p in listOf(listOf(listOf(100.0,30.0),listOf(104.0,34.0),listOf(100.0,34.0),listOf(104.0,30.0)),listOf(listOf(100.0,30.0),listOf(104.0,30.0),listOf(102.0,30.0),listOf(104.0,34.0))))assertFails {MapValidation.ring(p,60)}
 }
 @Test fun v1ScenarioUpgradesAndCustomGeographyIsSeparate(){
  val old="""{"schema":"chronicle-map-scenario/v1","id":"old","places":{"place-0":{"name":"旧城"}}}"""
  val s=MapValidation.parse(old);assertEquals(MAP_SCHEMA,s.schema);assertEquals("旧城",s.places["place-0"]!!.name)
  assertFails {MapValidation.parse(old.replace("\"旧城\"","\"旧城\",\"treasury\":999"))}
 }
 @Test fun constructionAndRestorationRequirePaidRealTime(){
  var w=game();val money=w.nation.treasury;w=submit(w,Command("CITY_FOUND",region=1));assertEquals(money-120,w.nation.treasury);assertEquals("planned",w.cartography!!.placeStates["settlement-r1"]!!.status)
  assertFails {submit(w,Command("CITY_FOUND",region=1))}
  w=RealtimeEngine.advance(w,90).world;w=RealtimeEngine.advance(w,29).world;assertEquals("planned",w.cartography!!.placeStates["settlement-r1"]!!.status)
  w=RealtimeEngine.advance(w,1).world;assertEquals("active",w.cartography!!.placeStates["settlement-r1"]!!.status)
  w=submit(w,Command("CITY_RESTORE",target="ruin-r0"));w=RealtimeEngine.advance(w,90).world;assertEquals("active",w.cartography!!.placeStates["ruin-r0"]!!.status)
  assertNotNull(Rules.validate(w,Command("CITY_RESTORE",target="city-r0")));Rules.verify(w)
 }
 @Test fun nativeRenameIsTimedPaidAndAudited(){
  val w=game();val renamed=submit(w,Command("CITY_RENAME",target="city-r0",objective="临江"));assertEquals(w.nation.treasury-5,renamed.nation.treasury);assertNull(renamed.cartography)
  val done=RealtimeEngine.advance(renamed,1).world;assertEquals("临江",done.cartography!!.places["city-r0"]!!.name);assertTrue(done.recent.last().events.any {it.contains("临江")});assertEquals(w.nation.treasury-5,done.nation.treasury)
 }
 @Test fun treatyHasPrerequisitesAndChangesOwnershipOnlyAfterCompletion(){
  val w=game();assertNotNull(Rules.validate(w,Command("BORDER_TREATY",target="frontier-0")))
  var x=submit(w.copy(neighbors=w.neighbors.map {it.copy(relation=85)}),Command("BORDER_TREATY",target="frontier-0"));assertEquals(w.nation.treasury-180,x.nation.treasury)
  assertEquals("neighbor-0",MapSnapshotMapper.scene(x,1).territories.first {it.id=="frontier-0"}.polityId)
  x=RealtimeEngine.advance(x,90).world;assertEquals("nation",MapSnapshotMapper.scene(x,1).territories.first {it.id=="frontier-0"}.polityId)
  assertEquals(70,x.neighbors[0].relation);assertNotNull(Rules.validate(x,Command("BORDER_TREATY",target="frontier-0")))
  val ledger=mutableListOf<String>();EconomyEngine.quarter(x,ledger,mutableListOf(),false);assertTrue(ledger.any {it.contains("驻防 -5")})
 }
 @Test fun ancientModeHasDistinctActionsAndCannotBecomeSteamIndustry(){
  var w=NewGame.create("景朝","君主","商工兴国","标准",42,setting=EraRules.ANCIENT)
  assertEquals("WORKSHOP",Rules.suggestions(w).first().type);assertNotNull(Rules.validate(w,Command("FACTORY")));assertTrue(EraRules.parse("兴建铁路",w).commands.isEmpty());assertEquals("WORKSHOP",EraRules.parse("建设作坊",w).commands.single().type)
  w=submit(w,Command("WORKSHOP"));assertEquals(270,w.projects.single().remainingDays)
  repeat(1000){w=RealtimeEngine.advance(w,90).world;Rules.verify(w);assertTrue(w.recent.all {e->listOf("蒸汽","工厂","铁路").none(e.narrative::contains)})}
  assertTrue(MemoryEngine.context(w).contains("冷兵器"));assertEquals("府",MapSnapshotMapper.scene(w,1).groups.first().rank)
 }
}
