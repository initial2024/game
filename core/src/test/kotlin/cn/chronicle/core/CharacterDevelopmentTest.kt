package cn.chronicle.core

import org.junit.Test
import kotlin.test.*
import kotlinx.serialization.json.*

class CharacterDevelopmentTest {
 private fun game()=NewGame.create("承熙","景衡","综合发展","标准",1847)
 private fun submit(w:World,c:Command)=RealtimeEngine.submit(w,c,w.revision).world
 private fun days(w:World,n:Int):World {var x=w;var left=n;while(left>0){val d=minOf(90,left);x=RealtimeEngine.advance(x,d).world;left-=d};return x}
 @Test fun formalConversationCostsTimeHasCooldownAndDoesNotGrantResources(){
  val w=game();val p=w.people[1];val c=Command("CONVERSE",target="1",mode="倾听")
  val queued=submit(w,c);assertEquals(w.nation.treasury-2,queued.nation.treasury);assertEquals(p,queued.people[1]);assertFails {submit(queued,c)}
  val done=days(queued,1);assertEquals(p.trust+3,done.people[1].trust);assertEquals(p.loyalty+1,done.people[1].loyalty);assertFails {submit(done,c)}
  assertNull(Rules.validate(days(done,30),c));assertEquals("威胁",DialogueEngine.approach("倾听","你这个废物"))
 }
 @Test fun maskedNpcHasNoAccessToRoyalIdentityOrCaseTruth(){
  val w=days(submit(game(),Command("DEPART")),7);val p=w.people[6];assertNull(DialogueEngine.access(w,6));assertTrue(DialogueEngine.hidden(w,p))
  val ctx=DialogueEngine.context(w,p);assertFalse(ctx.contains(w.ruler));assertFalse(ctx.contains("guilty"));assertTrue(ctx.contains("商人"));assertNull(DialogueEngine.sanitize(w,p,"陛下，臣已撤职地方官。"))
  val done=days(submit(w,Command("CONVERSE",target="6",mode="勉励")),1);assertEquals(p.loyalty,done.people[6].loyalty);assertTrue(done.people[6].trust>p.trust)
  assertNotNull(DialogueEngine.access(game(),6));assertNotNull(DialogueEngine.access(w.copy(people=w.people.map {if(it.id==6)it.copy(alive=false)else it}),6))
 }
 @Test fun studyRequiresPaidTravelAndSchoolAndTeacherMatter(){
  val w=game();val c=Command("STUDY_HOME",target="1");val enrolled=submit(w,c);assertEquals(w.nation.treasury-50,enrolled.nation.treasury);assertEquals(1,enrolled.studies.size)
  assertEquals(0,days(enrolled,6).studies.single().score);val before=days(enrolled,103);assertEquals(1,before.studies.single().travel)
  val done=days(before,1);assertTrue(done.studies.isEmpty());assertTrue(done.people[1].ability>w.people[1].ability)
  val better=days(submit(w.copy(academy=Academy(5,0)),c),104);assertTrue(better.people[1].ability>done.people[1].ability)
  assertFails {submit(enrolled,c)};assertNotNull(Rules.validate(enrolled,Command("DEPART",companion=1,regent=0)))
 }
 @Test fun deadTeacherPausesAndDeadStudentCannotReceiveReward(){
  var w=days(submit(game(),Command("STUDY_HOME",target="1")),7)
  w=w.copy(people=w.people.map {if(it.id==0)it.copy(alive=false)else it});val paused=days(w,10);assertEquals(90,paused.studies.single().remaining)
  val dead=paused.copy(people=paused.people.map {if(it.id==1)it.copy(alive=false)else it},regions=paused.regions.map {if(it.official==1)it.copy(official=-1)else it});assertTrue(days(dead,1).studies.isEmpty())
 }
 @Test fun overseasStudyUsesDiplomacyAndRecallKeepsTravel(){
  val w=game();val good=w.copy(neighbors=w.neighbors.map {it.copy(relation=70)})
  assertNotNull(Rules.validate(w.copy(neighbors=w.neighbors.map {it.copy(relation=20)}),Command("STUDY_ABROAD",target="1")))
  var x=days(submit(good,Command("STUDY_ABROAD",target="1")),20)
  assertEquals("研习",x.studies.single().phase);x=x.copy(neighbors=x.neighbors.map {it.copy(relation=20)});x=days(x,1);assertEquals("返程",x.studies.single().phase);assertEquals(14,x.studies.single().travel)
  assertTrue(days(x,14).studies.isEmpty())
 }
 @Test fun academyConstructionIsNotInstantAndNoRewardOnRecall(){
  val w=game();val queued=submit(w,Command("ACADEMY_UPGRADE"));assertEquals(1,days(queued,179).academy.level);assertEquals(2,days(queued,180).academy.level)
  val enrolled=submit(w,Command("STUDY_HOME",target="1"));val recalled=submit(enrolled,Command("RECALL_STUDENT",target="1"));val returned=days(recalled,7);assertTrue(returned.studies.isEmpty());assertEquals(w.people[1].ability,returned.people[1].ability)
 }
 @Test fun newContoursAreValidAndLegacyRectanglesMigrateWithoutChangingOwnership(){
  val w=game();val s=MapSnapshotMapper.scene(w,1);assertEquals(7,s.territories.size);assertTrue(s.territories.all {it.polygon.size>=8});MapValidation.verify(s)
  val old=s.copy(territories=s.territories.map {if(it.id=="frontier-0")it.copy(polityId="nation",polygon=listOf(listOf(121.0,31.0),listOf(125.0,31.0),listOf(125.0,35.0),listOf(121.0,35.0)))else it})
  val upgraded=MapSnapshotMapper.scene(w.copy(cartography=old),1).territories.first {it.id=="frontier-0"};assertEquals("nation",upgraded.polityId);assertTrue(upgraded.polygon.size>4);assertEquals(old,w.copy(cartography=old).cartography)
  val custom=old.copy(territories=old.territories.map {if(it.id=="frontier-0")it.copy(polygon=listOf(listOf(120.0,30.0),listOf(123.0,30.0),listOf(122.0,33.0)))else it});assertEquals(custom.territories,MapSnapshotMapper.scene(w.copy(cartography=custom),1).territories)
 }
 @Test fun hugggingFaceSupportsAnyFamilyAndRejectsBadFilesAndPaging(){
  val repo=HubCatalog.repository(Json.parseToJsonElement("""{"id":"bartowski/SmolLM2-135M-Instruct-GGUF","sha":"09816acd5d99df7be770d85ea30822623dab342c","tags":["license:apache-2.0"],"gated":false}""").jsonObject)
  val files=HubCatalog.files(Json.parseToJsonElement("""[{"type":"file","path":"SmolLM2-Q4_K_M.gguf","size":100000000,"lfs":{"oid":"${"a".repeat(64)}"}},{"type":"file","path":"m-00001-of-00002.gguf","size":100000000}]"""));assertEquals(2,files.size);assertTrue(HubCatalog.spec(repo,files.first()).url.contains("SmolLM2"));assertFails {HubCatalog.spec(repo,files.last())}
  val url=HubCatalog.searchUrl("Gemma 中文");assertFalse(url.contains("Qwen"));assertTrue(url.contains("filter=gguf"));assertFails {HubCatalog.next("<https://evil.example/api/models?x=1>; rel=\"next\"",url)}
  assertNotNull(HubCatalog.next("<https://huggingface.co/api/models?cursor=abc>; rel=\"next\"",url));assertFails {HubCatalog.spec(repo.copy(license="unknown"),files.first())}
 }
}
