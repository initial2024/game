package cn.chronicle.core
import org.junit.Test
import kotlin.test.*
class RealtimeTest {
 private fun game()=NewGame.create("承熙","景衡","综合发展","标准",1847)
 private fun submit(w:World,c:Command)=RealtimeEngine.submit(w,c,w.revision).world
 private fun advance(w:World,n:Int)=RealtimeEngine.advance(w,n).world
 @Test fun ordersDoNotAdvanceTimeAndFinishWithoutNextTurnButton() {
  val w=game();val queued=submit(w,Command("DRILL"));assertEquals(Command("DRILL"),queued.recent.last().commands.single());assertEquals(0,queued.day);assertEquals(w.nation.treasury-60,queued.nation.treasury)
  val before=advance(queued,29);assertEquals(w.nation.military,before.nation.military);assertEquals(1,before.pending.single().remainingDays)
  val done=advance(before,1);assertEquals(w.nation.military+4,done.nation.military);assertTrue(done.pending.isEmpty());assertEquals(queued.nation.treasury,done.nation.treasury)
 }
 @Test fun threeParallelOrdersAndCancellationAreBounded() {
  var w=submit(game(),Command("DRILL"));assertFails {submit(w,Command("DRILL"))};w=submit(w,Command("RESEARCH"));w=submit(w,Command("DIPLOMACY"));assertFails {submit(w,Command("RELIEF"))}
  val money=w.nation.treasury;val id=w.pending.first().id;w=submit(w,Command("CANCEL_TASK",target=id.toString()));assertEquals(money+48,w.nation.treasury);assertEquals(2,w.pending.size)
 }
 @Test fun travelAndConstructionUseExactDays() {
  var w=submit(game(),Command("DEPART"));w=advance(w,6);assertEquals(1,w.journey!!.remainingTravel);w=advance(w,1);assertEquals(0,w.journey!!.remainingTravel)
  var factory=submit(game(),Command("FACTORY"));repeat(2){factory=advance(factory,90)};factory=advance(factory,89);assertEquals(1,factory.projects.single().remainingDays);val industry=factory.nation.industry;factory=advance(factory,1);assertTrue(factory.projects.isEmpty());assertTrue(factory.nation.industry>=industry+6)
 }
 @Test fun realTimePregnancyAndReplayAreDeterministic() {
  var a=submit(submit(game(),Command("FAMILY_PLAN",target="允许")),Command("TRY_HEIR"));var b=a
  repeat(1000){a=advance(a,90);b=advance(b,90);assertEquals(a,b);Rules.verify(a)}
  assertTrue(a.household.members.any {it.role=="子女"});assertEquals(90000,a.day)
 }
 @Test fun investigationCanCompleteDuringContinuousTime() {
  var w=submit(game(),Command("DEPART"));w=advance(w,7);w=submit(w,Command("INVESTIGATE"));w=advance(w,5);assertTrue(w.caseFile.evidence.any {it.id=="receipt"})
  w=submit(w,Command("REVEAL"));w=advance(w,1);w=submit(w,Command("AUDIT"));w=advance(w,5);w=submit(w,Command("TRIAL"));w=advance(w,3);assertTrue(w.caseFile.resolved)
  w=submit(w,Command("RETURN"));w=advance(w,7);assertNull(w.journey);w=advance(w,90);assertNotEquals(3,w.regions[0].official);assertTrue(w.regions[0].official>=0);assertTrue(w.people.any {it.id==w.regions[0].official && it.alive})
 }
}
