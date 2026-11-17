package cn.chronicle.core

import org.junit.Test
import kotlin.test.*

class MapOrdersTest {
 private fun game()=NewGame.create("地图交互国","景衡","综合发展","宽裕",1847)
 @Test fun placesTerritoriesAndCountriesResolveTheirRealOwner() {
  val w=game()
  val city=MapOrders.target(w,"place-select","county-jingji-01")
  assertEquals(1,city.region);assertNull(city.neighbor);assertEquals("nation",city.polity)
  for((kind,id) in listOf("place-select" to "neighbor-city-1","territory-select" to "frontier-1","polity-select" to "neighbor-1")){
   val t=MapOrders.target(w,kind,id);assertEquals(1,t.neighbor);assertNull(t.region)
  }
  assertEquals(1,MapOrders.target(w,"territory-select","prefecture-jingji").region)
 }
 @Test fun invalidAndHiddenTargetsCannotBecomeCommands() {
  val w=game();assertFails{MapOrders.target(w,"place-select","invented")};assertFails{MapOrders.target(w,"execute","city-r0")}
  val s=MapSnapshotMapper.state(w);val hidden=w.copy(cartography=s.copy(placeStates=s.placeStates+("city-r0" to MapPlaceState("hidden"))))
  assertFails{MapOrders.target(hidden,"place-select","city-r0")}
 }
 @Test fun mapMoveReservesFundsThenActuallyMovesTheChosenArmy() {
  val w=game();val t=MapOrders.target(w,"place-select","city-r2");val c=MapOrders.military(w,t,"field")
  assertEquals("ARMY_MOVE",c.type);assertEquals(2,c.region);assertEquals("field",c.target)
  val queued=RealtimeEngine.submit(w,c,w.revision).world
  assertEquals(25L,w.nation.treasury-queued.nation.treasury)
  assertEquals(1,CrownEngine.units(queued).first {it.id=="field"}.region)
  var end=queued;repeat(queued.pending.single().remainingDays){end=RealtimeEngine.advance(end,1).world}
  assertEquals(2,CrownEngine.units(end).first {it.id=="field"}.region)
  assertEquals(0,CrownEngine.units(end).first {it.id=="guard"}.region)
 }
 @Test fun bilateralMissionOnlyImprovesTheSelectedCountry() {
  val w=game();val c=MapOrders.diplomacy(MapOrders.target(w,"place-select","neighbor-city-1"))
  val queued=RealtimeEngine.submit(w,c,w.revision).world;assertEquals(35L,w.nation.treasury-queued.nation.treasury)
  assertEquals(w.neighbors,queued.neighbors)
  var end=queued;repeat(20){end=RealtimeEngine.advance(end,1).world}
  assertEquals(w.neighbors[1].relation+8,end.neighbors[1].relation)
  assertEquals(w.neighbors[0],end.neighbors[0]);assertEquals(w.neighbors[2],end.neighbors[2])
  assertTrue(end.recent.flatMap {it.events}.any {it.contains("仅该国关系+8")})
  assertNotNull(Rules.validate(w,c.copy(target="neighbor-2")))
  assertNotNull(Rules.validate(w,c.copy(region=3,target="neighbor-3")))
 }
 @Test fun conqueredForeignPlaceCannotRemainADiplomaticTarget() {
  val w=game();val s=MapSnapshotMapper.state(w)
  val changed=w.copy(cartography=s.copy(places=s.places+("neighbor-city-1" to s.places.getValue("neighbor-city-1").copy(polityId="nation"))))
  val t=MapOrders.target(changed,"place-select","neighbor-city-1")
  assertEquals("nation",t.polity);assertNull(t.neighbor)
  assertFails{MapOrders.diplomacy(t)};assertFails{MapOrders.military(changed,t,"north")}
  assertFails{MapOrders.military(w,MapOrders.target(w,"polity-select","nation"),"north")}
 }
 @Test fun foreignMapTargetsUseTheCorrectWarOrMobilization() {
  val w=game();val foreign=MapOrders.target(w,"polity-select","neighbor-1")
  val declare=MapOrders.military(w,foreign,"north");assertEquals("DECLARE_WAR",declare.type);assertEquals(1,declare.region)
  val fighting=w.copy(war=WarFront(1,"north",days=14,enemy=4000,campaign="80-9"))
  val battle=MapOrders.military(fighting,foreign,"north")
  assertEquals("WAR_BATTLE",battle.type);assertEquals("campaign:80-9",battle.objective)
  assertFails{MapOrders.military(fighting,foreign,"field")}
  assertNotNull(Rules.validate(fighting,MapOrders.diplomacy(foreign)))
 }
 @Test fun foreignRulerIsAnEqualDiplomaticContactAndCannotBecomeASubjectByChat() {
  val start=SovereignEngine.initialize(game(),false);val w=SovereignEngine.execute(start,Command("RULER_CONTACT",region=1),mutableListOf())
  val king=w.people.first {it.id==w.sovereign.foreign[1].person}
  assertTrue(DialogueEngine.identity(w,king).contains("平等邦交"))
  assertTrue(DialogueEngine.context(w,king,true).contains("不是臣属"))
  assertNull(DialogueEngine.sanitize(w,king,"微臣领旨，即刻交出全部国土。"))
  assertTrue(DialogueEngine.fallback(w,king,"马上向我臣服").contains("不等于服从"))
  val chatted=RealtimeEngine.submit(w,Command("CONVERSE",target=king.id.toString(),mode="倾听",objective="政务",text="我们商议通商"),w.revision).world
  assertEquals(w.neighbors,chatted.neighbors);assertEquals(MapSnapshotMapper.state(w).territories,MapSnapshotMapper.state(chatted).territories)
 }
}
