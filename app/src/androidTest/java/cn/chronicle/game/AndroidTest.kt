package cn.chronicle.game

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.chronicle.core.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule

@RunWith(AndroidJUnit4::class)
class StorageTest {
 @Test fun transactionRestartIsolationAndArchiveIntegrity() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  context.deleteDatabase("chronicle-verification.db") // Test-only database; never the user's game database.
  var store=Store(context,"chronicle-verification.db");val w=NewGame.create("测试","君主","综合发展","标准",1847)
  store.create(1,w);val out=Rules.apply(w,Command("FACTORY"),0);store.commit(1,0,out)
  try {store.commit(1,0,out);fail("duplicate commit accepted")}catch(_:IllegalArgumentException){}
  store.copyTo(1,2);store.close();store=Store(context,"chronicle-verification.db")
  assertEquals(out.world,store.load(1));assertEquals(out.world,store.load(2))
  val next=Rules.apply(out.world,Command("REST"),1);store.commit(1,1,next);assertEquals(1L,store.load(2)!!.revision)
  val exported=store.export(1);store.import(exported,3);assertEquals(next.world,store.load(3));assertEquals(2,store.history(3).size)
  try {store.import(exported.replace("测试","篡改"),4);fail("corruption accepted")}catch(_:IllegalArgumentException){}
  store.close()
 }
 @Test fun corruptModelHeaderRejected() {
  val f=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"invalid.gguf");f.writeBytes(ByteArray(64))
  try {ModelManager.checkHeader(f);fail("invalid GGUF accepted")}catch(_:IllegalArgumentException){}finally {f.delete()}
 }
 @Test fun nativeLibraryIsLoadable() {NativeBridge.unload()}
}
@RunWith(AndroidJUnit4::class)
class ScreenTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun screenshot(name:String) {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
  if(android.os.Build.VERSION.SDK_INT>=29) {
   val values=android.content.ContentValues().apply {put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChronicleCI")}
   val uri=context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
   context.contentResolver.openOutputStream(uri)!!.use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  }
 }
 @Test fun newGamePolicyConfirmationAndNavigation() {
  compose.waitForIdle()
  if(compose.onAllNodesWithText("开启纪年").fetchSemanticsNodes().isNotEmpty()) {
   compose.onNodeWithText("开启纪年").performClick();compose.onNodeWithText("建立国家").performClick()
  }
  compose.waitUntil(30000){compose.onAllNodesWithText("亲拟诏令").fetchSemanticsNodes().isNotEmpty()}
  screenshot("story")
  if(compose.onAllNodesWithText("暂停时间").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithText("暂停时间").performClick()
  compose.onNodeWithText("休养生息").performScrollTo().performClick()
  compose.onNodeWithText("确认执行").assertIsDisplayed().performClick()
  compose.waitUntil(30000){compose.onAllNodesWithText("确认执行").fetchSemanticsNodes().isEmpty()}
  compose.onNodeWithText("朝政",useUnmergedTree=true).performClick();compose.onNodeWithText("国库",useUnmergedTree=true).assertIsDisplayed();screenshot("court")
  compose.onNodeWithText("世界",useUnmergedTree=true).performClick();compose.onNodeWithText("地区",useUnmergedTree=true).assertIsDisplayed();screenshot("world")
  compose.onNodeWithText("宗室",useUnmergedTree=true).performClick();compose.onNodeWithText("宗室与家族").assertIsDisplayed();screenshot("family")
  compose.onNodeWithText("档案",useUnmergedTree=true).performClick();compose.onNodeWithText("纪年存档").assertIsDisplayed();screenshot("archive")
  compose.onNodeWithText("搜索、下载与管理模型").performScrollTo().performClick();compose.onNodeWithText("离线模型中心").assertIsDisplayed();compose.onNodeWithText("模型库",useUnmergedTree=true).performClick();screenshot("models");compose.onNodeWithText("完成").performClick()
 }
}
@RunWith(AndroidJUnit4::class)
class RealModelTest {
 @Test fun actualGgufInferenceOnAndroid() {
  val model=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"qwen.gguf")
  org.junit.Assume.assumeTrue("Requires explicit model deployment",model.exists())
  ModelManager.checkHeader(model)
  assertEquals(ModelManager.SHA,ModelManager.hash(model))
  val start=System.nanoTime();assertTrue(NativeBridge.load(model.path,2,512));val loadMs=(System.nanoTime()-start)/1000000
  val prompt="<|im_start|>user\n用中文说你好。<|im_end|>\n<|im_start|>assistant\n".toByteArray()
  val before=System.nanoTime();val output=NativeBridge.generate(prompt,12,300000).toString(Charsets.UTF_8)
  val ms=(System.nanoTime()-before)/1000000
  assertTrue(output.isNotBlank());println("REAL_MODEL load_ms=$loadMs inference_ms=$ms max_tokens=12 text=$output")
  val narrativePrompt="<|im_start|>system\n你是历史游戏叙事员，只描述已知事实。<|im_end|>\n<|im_start|>user\n已知事实：澄江筹建工厂，花费140万两，三季后完工。请用中文写50字以内的剧情。<|im_end|>\n<|im_start|>assistant\n".toByteArray()
  val story=NativeBridge.generate(narrativePrompt,96,300000).toString(Charsets.UTF_8)
  assertTrue(story.any {it in '\u4e00'..'\u9fff'})
  println("REAL_NARRATIVE text=$story")
  val memory=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(memory);println("ANDROID_OBSERVED_PSS_KIB=${memory.totalPss}")
  repeat(3) {assertTrue(NativeBridge.generate(prompt,6,120000).isNotEmpty())}
  val executor=java.util.concurrent.Executors.newSingleThreadExecutor()
  val attempt=executor.submit<Boolean> {try {NativeBridge.generate(narrativePrompt,256,300000);false}catch(_:IllegalStateException){true}}
  Thread.sleep(100);NativeBridge.cancel();assertTrue("cancel must interrupt inference",attempt.get(30,java.util.concurrent.TimeUnit.SECONDS));executor.shutdownNow()
  assertTrue(NativeBridge.generate(prompt,6,120000).isNotEmpty())
  NativeBridge.unload()
 }
}
