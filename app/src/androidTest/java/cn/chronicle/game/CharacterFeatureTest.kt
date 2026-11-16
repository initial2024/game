package cn.chronicle.game

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import cn.chronicle.core.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CharacterFeatureTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun screenshot(name:String){
  compose.waitForIdle();Thread.sleep(500)
  val ctx=InstrumentationRegistry.getInstrumentation().targetContext;val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?:error("Screenshot unavailable")
  val values=android.content.ContentValues().apply {put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChronicleCI")}
  val uri=ctx.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!;ctx.contentResolver.openOutputStream(uri)!!.use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
 }
 @Test fun offlineChatPersistsAndAcademyIsPlayable(){
  val vm=ViewModelProvider(compose.activity)[GameViewModel::class.java];compose.waitUntil(30000){vm.state.value.ready}
  if(vm.state.value.world==null){vm.newGame("对话测试","景衡","综合发展","标准","1847");compose.waitUntil(10000){vm.state.value.world!=null&&!vm.state.value.busy}}
  if(!vm.state.value.paused)vm.toggleClock()
  compose.onNodeWithText("世界",useUnmergedTree=true).performClick();compose.onNodeWithText("人物",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithText("与韩砺交谈").performScrollTo().performClick();compose.waitUntil(10000){vm.chat.value.person==1}
  compose.onNodeWithText("说些什么…").performTextInput("你希望到国家学院学习吗？");compose.onNodeWithText("发送对话").performClick()
  compose.waitUntil(15000){!vm.chat.value.working&&vm.chat.value.records.size>=2};assertEquals("template",vm.chat.value.records.last().source)
  screenshot("character-chat");val store=Store(compose.activity);assertTrue(store.dialogues(vm.state.value.slot,1).size>=2);store.close()
  compose.onNodeWithText("关闭对话").performClick();compose.onNodeWithText("学院",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithText("派学",useUnmergedTree=true).performScrollTo().performClick();compose.onNodeWithText("预览派学").performScrollTo().performClick();compose.onNodeWithText("确认执行").performClick()
  compose.waitUntil(10000){vm.state.value.world!!.studies.any {it.person==1}}
  compose.onNodeWithText("在读",useUnmergedTree=true).performScrollTo().performClick();compose.onNodeWithText("韩砺 · 国家学院").assertIsDisplayed();screenshot("academy-student")
 }
 @Test fun oldDatabaseMigratesWithoutChangingWorldAndChatsStayIsolated(){
  val ctx=InstrumentationRegistry.getInstrumentation().targetContext;val name="character-migration-test.db";ctx.deleteDatabase(name)
  val w=NewGame.create("旧档","景衡","综合发展","标准",1847);val json=SaveJson.encodeToString(w)
  ctx.openOrCreateDatabase(name,0,null).use {db->db.execSQL("CREATE TABLE saves(slot INTEGER PRIMARY KEY,revision INTEGER NOT NULL,name TEXT NOT NULL,turn INTEGER NOT NULL,json TEXT NOT NULL)");db.execSQL("CREATE TABLE history(slot INTEGER NOT NULL,revision INTEGER NOT NULL,json TEXT NOT NULL,PRIMARY KEY(slot,revision))");db.execSQL("INSERT INTO saves VALUES(1,0,'旧档',0,?)",arrayOf(json));db.version=1}
  Store(ctx,name).use {s->assertEquals(w,s.load(1));val record=DialogueRecord(1,"user","学业如何？",0,"player");val out=RealtimeEngine.submit(w,Command("CONVERSE",target="1",mode="倾听"),0);s.startDialogue(1,0,out,record);assertEquals(w.nation.treasury-2,s.load(1)!!.nation.treasury)
   try{s.startDialogue(1,0,out,record);fail("replay accepted")}catch(_:IllegalArgumentException){}
   assertEquals(1,s.dialogues(1).size);s.copyTo(1,2);s.appendDialogue(1,DialogueRecord(1,"assistant","愿潜心进学。",0,"template"));assertEquals(1,s.dialogues(2).size);s.import(s.export(1),3);assertEquals(s.dialogues(1),s.dialogues(3));assertEquals(s.load(1),s.load(3))
  }
  Store(ctx,name).use {assertEquals(2,it.dialogues(1).size);assertEquals(1,it.dialogues(2).size)}
 }
}

@RunWith(AndroidJUnit4::class)
class HubLiveTest {
 @Test fun realHuggingFaceSearchAndNonQwenFiles(){
  val client=HuggingFaceClient();val page=client.search("SmolLM2", "downloads",true);assertTrue(page.items.isNotEmpty());val repo=client.detail("bartowski/SmolLM2-135M-Instruct-GGUF");val files=client.files(repo);val selected=files.items.first {it.path.contains("Q4_K_M")&&it.blocked.isEmpty()};val spec=HubCatalog.spec(repo,selected);spec.verify();assertTrue(spec.url.contains(repo.revision));println("LIVE_HF_SEARCH repos=${page.items.size} repo=${repo.id} revision=${repo.revision} file=${selected.path} bytes=${selected.bytes} sha256=${selected.sha256}")
 }
}
