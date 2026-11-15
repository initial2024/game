package cn.chronicle.core
import org.junit.Test
import kotlin.test.*
import kotlinx.serialization.encodeToString
class FamilyTest {
 private fun game()=NewGame.create("承熙","自定君主","综合发展","标准",1847,"长宁")
 private fun run(w:World,c:Command)=Rules.apply(w,c,w.revision).world
 @Test fun rulerEraAndChildNamesArePersisted() {
  var w=game();assertTrue(w.date.startsWith("长宁"))
  w=run(w,Command("RULER_NAME",target="昭和"));assertEquals("昭和",w.ruler)
  w=run(w,Command("ERA",target="永泰"));assertTrue(w.date.startsWith("永泰1年"))
  w=run(w,Command("CHILD_NAME_PLAN",target="知遥"));w=run(w,Command("FAMILY_PLAN",target="允许"));w=run(w,Command("TRY_HEIR"));assertNotNull(w.household.pregnancy)
  w=run(w,Command("REST"));w=run(w,Command("REST"));assertNull(w.household.pregnancy)
  val child=w.household.members.single {it.role=="子女"};assertEquals("知遥",child.name)
  w=run(w,Command("NAME_CHILD",target="${child.id}|山岚"));assertEquals("山岚",w.household.members.last().name)
  assertEquals(w,SaveJson.decodeFromString<World>(SaveJson.encodeToString(w)));Rules.verify(w)
 }
 @Test fun disabledMinorUnwillingAndDuplicatePregnanciesAreRejected() {
  val w=game();assertNotNull(Rules.validate(w,Command("TRY_HEIR")))
  val enabled=run(w,Command("FAMILY_PLAN",target="允许"))
  val minor=enabled.copy(household=enabled.household.copy(members=enabled.household.members.map {it.copy(bornDay=-17*360)}))
  assertNotNull(Rules.validate(minor,Command("TRY_HEIR")))
  val unwilling=enabled.copy(household=enabled.household.copy(members=enabled.household.members.map {it.copy(willing=false)}))
  assertNotNull(Rules.validate(unwilling,Command("TRY_HEIR")))
  val pregnant=run(enabled,Command("TRY_HEIR"));assertNotNull(Rules.validate(pregnant,Command("TRY_HEIR")))
  val paused=run(pregnant,Command("FAMILY_PLAN",target="暂停"));assertEquals(pregnant.household.pregnancy,paused.household.pregnancy)
 }
 @Test fun pregnancyConsumesTimeAndIsDeterministic() {
  val w=run(game(),Command("FAMILY_PLAN",target="允许"));val a=run(w,Command("TRY_HEIR"));val b=run(w,Command("TRY_HEIR"));assertEquals(a,b);assertEquals(90,a.day);assertTrue(a.recent.last().ledger.any {it.contains("宗室照护")})
 }
}
