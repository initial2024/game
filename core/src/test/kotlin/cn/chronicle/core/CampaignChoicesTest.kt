package cn.chronicle.core

import kotlinx.serialization.encodeToString
import org.junit.Test
import kotlin.test.*

class CampaignChoicesTest {
 private fun front():World {
  val w=NewGame.create("战役验收","明衡","综合发展","宽裕",1847,scale="超级大国")
  return w.copy(day=21,nation=w.nation.copy(treasury=20000),armies=CrownEngine.units(w).map {if(it.id=="north")it.copy(soldiers=30000,training=100,morale=100,supply=100)else it},war=WarFront(0,"north",days=14,enemy=3500,campaign="21-1"))
 }
 private fun act(w:World,c:Command)=RealtimeEngine.submit(w,c,w.revision).world
 private fun days(w:World,n:Int):World {var x=w;repeat(n){x=RealtimeEngine.advance(x,1).world};return x}
 @Test fun strategiesChangeActualTimeCashMaterialsSupplyAndCasualties() {
  val w=front();val outcomes=WarfareEngine.approaches.associate {spec->
   val c=Command("WAR_BATTLE",mode=spec.mode);val queued=act(w,c)
   assertEquals(spec.cost,w.nation.treasury-queued.nation.treasury)
   assertEquals(spec.days,queued.pending.single().remainingDays)
   assertEquals(w.resources.stocks,queued.resources.stocks)
   val end=days(queued,spec.days);assertTrue(end.pending.isEmpty());assertEquals(spec.mode,end.war!!.lastApproach)
   assertEquals(spec.victory,end.war!!.progress)
   assertEquals(w.nation.population-end.nation.population,30000L-CrownEngine.units(end).first {it.id=="north"}.soldiers)
   Rules.verify(end);spec.mode to end
  }
  val rapid=outcomes.getValue("速攻");val siege=outcomes.getValue("围困");val steady=outcomes.getValue("稳战")
  assertTrue(CrownEngine.units(rapid).first {it.id=="north"}.soldiers<CrownEngine.units(steady).first {it.id=="north"}.soldiers)
  assertTrue(CrownEngine.units(siege).first {it.id=="north"}.soldiers>CrownEngine.units(steady).first {it.id=="north"}.soldiers)
  assertTrue(rapid.resources.stocks.getValue("IRON")<steady.resources.stocks.getValue("IRON"))
  assertTrue(siege.resources.stocks.getValue("TIMBER")<steady.resources.stocks.getValue("TIMBER"))
  assertTrue(siege.resources.stocks.getValue("GRAIN")<steady.resources.stocks.getValue("GRAIN"))
  assertTrue(CrownEngine.units(rapid).first {it.id=="north"}.supply<CrownEngine.units(steady).first {it.id=="north"}.supply)
 }
 @Test fun unknownStrategiesAndUnfundedSiegesAreRejected() {
  val w=front();assertNotNull(Rules.validate(w,Command("WAR_BATTLE",mode="立即统一世界")))
  val low=w.copy(armies=CrownEngine.units(w).map {if(it.id=="north")it.copy(supply=17)else it})
  assertNotNull(Rules.validate(low,Command("WAR_BATTLE",mode="速攻")))
  val noTimber=w.copy(resources=w.resources.copy(stocks=w.resources.stocks+("TIMBER" to 0)))
  assertNotNull(Rules.validate(noTimber,Command("WAR_BATTLE",mode="围困")))
  val few=w.copy(armies=CrownEngine.units(w).map {if(it.id=="north")it.copy(soldiers=1000)else it})
  assertNotNull(Rules.validate(few,Command("WAR_BATTLE",mode="围困")))
  val defeated=w.copy(war=w.war!!.copy(enemy=0));assertNotNull(Rules.validate(defeated,Command("WAR_BATTLE")));assertNull(Rules.validate(defeated,Command("WAR_PEACE")))
 }
 @Test fun realEnvoyCanNegotiatePeaceWithoutInventingConquest() {
  val w=front().let {it.copy(war=it.war!!.copy(progress=30,enemyWeariness=80))}
  val c=Command("WAR_ENVOY",target="2");assertNull(Rules.validate(w,c));assertNotNull(Rules.validate(w,Command("WAR_PEACE")))
  val queued=act(w,c);assertEquals(60,w.nation.treasury-queued.nation.treasury)
  assertTrue(Rules.validate(queued,Command("STUDY_HOME",target="2"))!!.contains("出使"))
  assertNotNull(Rules.validate(queued,Command("WAR_BATTLE")))
  val end=days(queued,21);assertNull(end.war);assertEquals(35,end.neighbors[0].relation)
  assertEquals("neighbor-0",MapSnapshotMapper.state(end).territories.first {it.id=="frontier-0"}.polityId)
  assertTrue(end.people.first {it.id==2}.memories.any {it.contains("议和获准")})
 }
 @Test fun weakLeverageFailsAndRetainsTheCostAndCooldown() {
  val w=front().let {it.copy(armies=CrownEngine.units(it).map {u->if(u.id=="north")u.copy(soldiers=2000)else u},war=it.war!!.copy(progress=-20),people=it.people.map {p->if(p.id==2)p.copy(ability=40)else p})}
  val end=days(act(w,Command("WAR_ENVOY",target="2")),21)
  assertNotNull(end.war);assertEquals(-23,end.war!!.progress);assertTrue(end.nation.treasury<w.nation.treasury-59)
  assertTrue(Rules.validate(end,Command("WAR_ENVOY",target="2"))!!.contains("60日"))
  assertTrue(end.people.first {it.id==2}.memories.any {it.contains("和议遭拒")})
  assertNotNull(Rules.validate(end,Command("WAR_ENVOY",target="1")))
 }
 @Test fun unavailableEnvoyAbortsWithAuditedPartialRefund() {
  val w=front();val queued=act(w,Command("WAR_ENVOY",target="2"))
  val died=queued.copy(people=queued.people.map {if(it.id==2)it.copy(alive=false,role="已故")else it})
  val dayBefore=days(died,20);val result=RealtimeEngine.advance(dayBefore,1)
  assertEquals(48,result.world.nation.treasury-dayBefore.nation.treasury)
  assertTrue(result.entry.ledger.any {it.contains("退还 48")})
  assertTrue(result.world.people.first {it.id==2}.memories.none {it.contains("议和")})
 }
 @Test fun withdrawnOrdersCannotSettleAgainstANewCampaign() {
  val w=front();val queued=act(w,Command("WAR_BATTLE",mode="围困"));val oldOrder=queued.pending.single().command
  val ended=days(act(queued,Command("WAR_WITHDRAW")),7)
  assertNull(ended.war);assertTrue(ended.pending.isEmpty())
  val next=ended.copy(war=WarFront(1,"north",days=14,enemy=4000,campaign="80-9"))
  assertTrue(Rules.validate(next,oldOrder)!!.contains("已经结束的战役"))
 }
 @Test fun outcomesReplayAndOldWarSavesReceiveDefaults() {
  val w=front();val c=Command("WAR_BATTLE",mode="速攻")
  val a=days(act(w,c),7);val b=days(act(SaveJson.decodeFromString<World>(SaveJson.encodeToString(w)),c),7)
  assertEquals(a,b)
  val legacy=SaveJson.decodeFromString<WarFront>("""{"neighbor":0,"army":"north","enemy":3500}""")
  assertEquals("legacy",legacy.campaign);assertEquals(0,legacy.enemyWeariness)
  assertEquals(-360,legacy.lastParley);assertEquals("稳战",legacy.lastApproach)
 }
}
