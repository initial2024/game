package cn.chronicle.core

import org.junit.Test
import kotlin.test.*
import kotlinx.serialization.encodeToString

class SimulationTest {
 private fun game(seed:Long=1847)=NewGame.create("承熙","景衡","综合发展","标准",seed)
 private fun run(w:World,c:Command)=Rules.apply(w,c,w.revision).world
 private fun arrive(mode:String="微服私访",seed:Long=1847):World { var w=run(game(seed),Command("DEPART",mode=mode));w=run(w,Command("TRAVEL"));return w }
 @Test fun thousandQuartersAcrossDifficulties() {
  for(diff in listOf("宽裕","标准","艰难")) for(seed in listOf(-19L,0,1847)) {
   var w=NewGame.create("测试","君主","综合发展",diff,seed)
   repeat(1000) { i -> val candidates=listOf(Command(Rules.policies[Math.floorMod(i*7,Rules.policies.size)],region=i%4),Command("REST")); val c=candidates.first { Rules.validate(w,it)==null };w=run(w,c);Rules.verify(w);assertTrue(w.nation.debt<=2000);assertTrue(w.recent.size<=12) }
   assertEquals(1000,w.turn)
  }
 }
 @Test fun deterministicSeedAndReplay() { var a=game();var b=game();repeat(1000) { val c=Rules.suggestions(a).firstOrNull {Rules.validate(a,it)==null}?:Command("REST");a=run(a,c);b=run(b,c);assertEquals(SaveJson.encodeToString(a),SaveJson.encodeToString(b)) } }
 @Test fun duplicateRevisionRejected() {val w=game();val first=run(w,Command("REST"));assertFailsWith<IllegalArgumentException> {Rules.apply(first,Command("REST"),0)} }
 @Test fun constructionCostsAndTakesTime() {val w=game();val a=run(w,Command("FACTORY"));assertTrue(a.nation.treasury<w.nation.treasury);assertEquals(w.nation.industry,a.nation.industry);assertNotNull(Rules.validate(a,Command("FACTORY")));val b=run(run(a,Command("REST")),Command("REST"));assertTrue(b.nation.industry>w.nation.industry);assertTrue(b.projects.isEmpty())}
 @Test fun debtIsNotFreeMoney() {var w=game().copy(nation=Nation(treasury=0,debt=2000,military=100,research=30,tax=5));repeat(30){w=run(w,Command("REST"));assertTrue(w.nation.debt<=2000);assertTrue(w.nation.treasury<1000)} }
 @Test fun deadPeopleCannotTakeOffice() {var w=game().copy(people=game().people.map {it.copy(ageQuarters=399)});w=run(w,Command("REST"));assertTrue(w.people.take(10).all {!it.alive});w.regions.forEach {r->assertTrue(w.people.first {it.id==r.official}.alive)};assertNotNull(Rules.validate(w,Command("DEPART"))) }
 @Test fun threeCaseRoutesDiffer() {
  val secret=arrive();val open=arrive("公开巡幸");val covert=arrive("秘密调查")
  assertTrue(open.journey!!.revealed);assertFalse(secret.journey!!.revealed)
  assertTrue(open.nation.treasury<secret.nation.treasury)
  val a=run(secret,Command("INVESTIGATE"));val b=run(covert,Command("INVESTIGATE"));assertTrue(a.journey!!.exposure>b.journey!!.exposure)
  var remote=run(game(),Command("REMOTE"));assertNull(remote.journey);assertEquals(0,remote.caseFile.remoteDays);assertTrue(remote.caseFile.evidence.any {it.id=="ledger"})
  remote=run(remote,Command("TRIAL"));assertEquals("免职候审",remote.people[3].role);assertTrue(remote.caseFile.resolved)
 }
 @Test fun verticalJourneyPersistsConsequences() {
  var w=arrive();w=run(w,Command("VISIT",target="集市"));w=run(w,Command("INVESTIGATE"));assertNotNull(Rules.validate(w,Command("TRIAL")));assertNotNull(Rules.validate(w,Command("AUDIT")))
  w=run(w,Command("REVEAL"));w=run(w,Command("AUDIT"));w=run(w,Command("TRIAL"));assertTrue(w.regions[0].corruption<58);w=run(w,Command("RETURN"));w=run(w,Command("REST"));assertNull(w.journey);assertNotEquals(3,w.regions[0].official);assertEquals("免职候审",w.people[3].role)
  val restored=SaveJson.decodeFromString<World>(SaveJson.encodeToString(w));assertEquals(w,restored)
 }
 @Test fun falseAccusationCannotConvict() {var w=arrive("公开巡幸",seed=0);w=run(w,Command("INVESTIGATE"));w=run(w,Command("AUDIT"));assertNotNull(Rules.validate(w,Command("TRIAL")));w=run(w,Command("CLOSE"));assertEquals("发现伪证",w.caseFile.status);assertEquals("澄江知府",w.people[3].role)}
 @Test fun evidenceCannotBeFarmed() {var w=arrive().copy(journey=arrive().journey!!.copy(budgetDays=90));repeat(5){w=run(w,Command("INVESTIGATE"))};assertEquals(2,w.caseFile.evidence.size);assertTrue(w.journey!!.exposure>0)}
 @Test fun noOmniscientIdentityOrTruthInPrompt() {val w=arrive();val p=MemoryEngine.context(w);assertTrue(p.contains("NPC眼中的玩家身份：商人"));assertFalse(p.contains("景衡"));assertFalse(p.contains("guilty"));assertFalse(p.contains("陆敬川贪污"));assertTrue(p.length<=4500)}
 @Test fun invalidTextFallsBack() {assertTrue(IntentParser.parse("立即统一世界").commands.isEmpty());val p=IntentParser.parse("财政盈余的三分之一投入钢铁工业，同时引入民间资本");assertTrue(p.uncertain);assertEquals(listOf(Command("FACTORY")),p.commands)}
 @Test fun strictJsonAndBounds() {assertFails {SaveJson.decodeFromString<Command>("{\"type\":\"REST\",\"money\":99999}")};assertFails {Rules.verify(game().copy(nation=Nation(treasury=-1)))} }
 @Test fun aiStructuredOutputMustMapToLegalPolicies() {
  val w=game()
  val richIncome=w.copy(nation=w.nation.copy(treasury=200,industry=100,commerce=100,agriculture=100,tax=40,corruption=0))
  assertFails {AiValidation.parse("{\"commands\":[{\"type\":\"ROAD\",\"region\":0},{\"type\":\"FARM\",\"region\":1},{\"type\":\"FACTORY\",\"region\":2}]}",richIncome)}
  assertEquals("FARM",AiValidation.parse("{\"commands\":[{\"type\":\"FARM\",\"region\":0}]}",w).commands.single().type)
  listOf("{\"commands\":[{\"type\":\"GIVE_MONEY\"}]}","{\"commands\":[{\"type\":\"REST\",\"treasury\":99999}]}","{\"commands\":[]}","{\"commands\":[{\"type\":\"FARM\",\"region\":9}]}").forEach {bad->assertFails {AiValidation.parse(bad,w)}}
 }
 @Test fun modelCatalogAndHardwareAdviceAreConservative() {
  ModelCatalog.builtIn.forEach {it.verify()}
  assertNull(ModelCatalog.recommend(800L*1048576,8L*1024*1048576,8,true).model)
  assertNull(ModelCatalog.recommend(4000L*1048576,8L*1024*1048576,8,false).model)
  val low=ModelCatalog.recommend(1500L*1048576,4000L*1048576,8,true)
  assertEquals(ModelCatalog.builtIn[1],low.model);assertEquals(512,low.context);assertEquals(4,low.threads)
  assertFails {ModelCatalog.builtIn.first().copy(file="../bad.gguf").verify()}
  assertFails {ModelCatalog.builtIn.first().copy(license="unknown").verify()}
 }
 @Test fun startModesAreReproducibleAndNeverEraseCorruption() {
  for(scale in listOf("超级大国","中等国家","小国","随机")) for(mode in listOf("和平建设","均势博弈","烽火边疆")) {
   val a=NewGame.create("测试","君主","综合发展","标准",42,scale=scale,atmosphere=mode)
   val b=NewGame.create("测试","君主","综合发展","标准",42,scale=scale,atmosphere=mode)
   assertEquals(a,b);assertTrue(a.nation.corruption>0);assertTrue(a.regions.any {it.corruption>0});Rules.verify(a)
   var future=a;repeat(1000){future=run(future,Command("REST"));Rules.verify(future)}
  }
 }
 @Test fun thirtyDistinctValidatedEvents() {
  assertTrue(EventEngine.templates.size>=30);assertEquals(EventEngine.templates.size,EventEngine.templates.map {it.id}.distinct().size)
  val fixtures=mutableListOf<World>()
  for(v in listOf(0,20,50,80,100)) {
   val n=Nation(treasury=v.toLong()*20,debt=v.toLong()*20,population=7000000,tax=if(v==0)5 else v.coerceAtMost(40),agriculture=v,industry=v,commerce=v,technology=v,infrastructure=v,administration=v,military=v,corruption=v,morale=v,stability=v,research=v.coerceAtMost(30),income=900,expense=100)
   fixtures+=game().copy(nation=n)
  }
  val base=game()
  fixtures+=NewGame.create("边疆","君主","综合发展","标准",1847,atmosphere="烽火边疆").copy(nation=Nation(military=20))
  fixtures+=NewGame.create("和平","君主","综合发展","标准",1847,atmosphere="和平建设")
  fixtures+=base.copy(nation=Nation(corruption=60),factions=base.factions.map {it.copy(support=20)})
  fixtures+=base.copy(nation=base.nation.copy(commerce=70,industry=25))
  fixtures+=base.copy(nation=base.nation.copy(morale=80,technology=20))
  fixtures+=base.copy(nation=base.nation.copy(industry=65,infrastructure=25,technology=50,morale=85,stability=85,agriculture=70,commerce=55,administration=55,military=70))
  fixtures+=base.copy(nation=base.nation.copy(technology=50,research=0))
  fixtures+=base.copy(neighbors=base.neighbors.map {it.copy(relation=90)})
  fixtures+=base.copy(factions=base.factions.map {it.copy(support=20)})
  fixtures+=base.copy(factions=base.factions.map {it.copy(support=90)})
  fixtures+=base.copy(caseFile=base.caseFile.copy(resolution="依法撤职追缴"),regions=base.regions.map {it.copy(official=-1)})
  EventEngine.templates.forEach {e->val w=fixtures.firstOrNull(e.trigger);assertNotNull(w,"缺少可触发样本 ${e.id}");val n=e.effect(w.nation);assertNotEquals(w.nation,n,"事件无效果 ${e.id}");assertTrue(e.cooldown>0)}
 }
}
