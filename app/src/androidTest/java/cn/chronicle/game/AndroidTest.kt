package cn.chronicle.game

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.chronicle.core.*
import org.junit.Test
import org.junit.Before
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule

@RunWith(AndroidJUnit4::class)
class StorageTest {
 @Test fun largeWorldBeyondCursorWindowRestartsWithoutTruncation(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext;val dbName="large-world-verification.db";context.deleteDatabase(dbName)
  val original=NewGame.create("大档验证","景衡","综合发展","标准",1847)
  val world=original.copy(people=original.people.map {p->p.copy(memories=List(8){i->"${p.id}号人物第${i}项经历："+"地方治安粮仓巡检".repeat(20)})})
  val encoded=SaveJson.encodeToString(world);assertTrue("Must exceed SQLite's common 2 MiB CursorWindow",encoded.toByteArray().size>2*1024*1024)
  Store(context,dbName).use {it.create(1,world)}
  Store(context,dbName).use {store->
   val loaded=requireNotNull(store.load(1));assertEquals(Store.sha(encoded),Store.sha(SaveJson.encodeToString(loaded)));assertEquals(778,loaded.people.size)
   store.copyTo(1,2);store.import(store.export(1),3);assertEquals(Store.sha(encoded),Store.sha(SaveJson.encodeToString(store.load(3)!!)))
   val changed=RealtimeEngine.submit(loaded,Command("CONVERSE",target=loaded.localDefense.units.first().commander.toString(),mode="倾听"),loaded.revision)
   store.commit(1,loaded.revision,changed);assertEquals(0L,store.load(2)!!.revision)
  }
  Store(context,dbName).use {assertEquals(1L,it.load(1)!!.revision);assertEquals(0L,it.load(3)!!.revision)}
  println("LARGE_WORLD_RESTART bytes=${encoded.toByteArray().size} people=${world.people.size} slots=3")
 }
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
 @Test fun legacyExportChecksumSurvivesCommandSchemaExtension(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext;context.deleteDatabase("old-command-import-test.db")
  val start=NewGame.create("旧格式","君主","综合发展","标准",1847);val out=RealtimeEngine.submit(start,Command("FARM"),0)
  fun strip(e:JsonElement):JsonElement=when(e){is JsonObject->JsonObject(e.filterKeys {it!="level"}.mapValues {strip(it.value)});is JsonArray->JsonArray(e.map {strip(it)});else->e}
  val original=SaveJson.parseToJsonElement(SaveJson.encodeToString(out.world)).jsonObject
  val world=strip(JsonObject(original.filterKeys {it !in listOf("towns","socialTopics","resources","localDefense","payroll")}.toMutableMap().apply {put("people",SaveJson.parseToJsonElement(SaveJson.encodeToString(out.world.people.take(10))))})).toString()
  val history=strip(SaveJson.parseToJsonElement(SaveJson.encodeToString(listOf(out.entry))));val digest=Store.sha(world+history.toString()+"[]")
  val snapshot=buildJsonObject {put("format",2);put("world",world);put("history",history);put("dialogues",JsonArray(emptyList()));put("sha256",digest)}.toString()
  Store(context,"old-command-import-test.db").use {store->store.import(snapshot,1);assertEquals(out.world.nation,store.load(1)!!.nation);assertEquals(171,store.load(1)!!.towns.count {it.rank=="县城"});assertEquals("FARM",store.history(1).single().commands.single().type)}
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
 private fun clearEmulatorLauncher() {
  if(InstrumentationRegistry.getArguments().getString("ci_emulator")=="true") {
   val descriptor=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("am force-stop com.android.launcher3")
   android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {it.readBytes()}
  }
 }
 @Before fun prepareEmulatorWindow() {clearEmulatorLauncher()}
 private fun screenshot(name:String) {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  clearEmulatorLauncher()
  compose.waitForIdle()
  compose.waitUntil(5000){InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.packageName?.toString()=="cn.chronicle.shanhe"}
  val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
  if(android.os.Build.VERSION.SDK_INT>=29) {
   val values=android.content.ContentValues().apply {put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChronicleCI")}
   val uri=context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
   context.contentResolver.openOutputStream(uri)!!.use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  }
 }
 @Test fun continuousJourneyEvidenceJudgmentAndReturn() {
  val vm=ViewModelProvider(compose.activity)[GameViewModel::class.java]
  compose.waitUntil(30000){vm.state.value.ready}
  compose.waitForIdle()
  val previousSlot=vm.state.value.slot;val hadWorld=vm.state.value.world!=null
  if(compose.onAllNodesWithText("开启纪年").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithText("开启纪年").performClick()
  else {compose.onNodeWithText("档案",useUnmergedTree=true).performClick();compose.onNodeWithText("新建").performScrollTo().performClick()}
  compose.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick() // Exercise free play; guided auto-pause is tested separately.
  compose.onNodeWithText("建立国家").performClick()
  compose.waitUntil(30000){vm.state.value.world!=null && !vm.state.value.busy && (!hadWorld || vm.state.value.slot!=previousSlot)}
  fun pause() {if(!vm.state.value.paused)compose.onNodeWithTag("clock-toggle").performClick()}
  fun choose(label:String) {compose.onNodeWithText(label).performScrollTo().performClick();compose.waitUntil(10000){compose.onAllNodesWithText("确认执行").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("确认执行").performClick();compose.waitUntil(10000){!vm.state.value.busy}}
  fun until(test:(World)->Boolean) {if(vm.state.value.paused)compose.onNodeWithTag("clock-toggle").performClick();compose.waitUntil(60000){vm.state.value.world?.let(test)==true};pause()}
  pause();while(vm.state.value.speed!=4)compose.onNodeWithTag("clock-speed").performClick()
  compose.onNodeWithText("世界",useUnmergedTree=true).performClick();compose.onNodeWithText("地区",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithText("行旅",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress){it(90f)}
  choose("出发巡游");until {it.journey?.remainingTravel==0};screenshot("tour-arrived")
  choose("探访场景");until {it.pending.none {p->p.command.type=="VISIT"}}
  choose("暗访取证");until {it.caseFile.evidence.any {e->e.id=="receipt"}}
  assertFalse(vm.state.value.world!!.journey!!.revealed)
  choose("公开身份");until {it.journey?.revealed==true}
  choose("核验账册");until {it.caseFile.evidence.any {e->e.id=="ledger"}};screenshot("tour-evidence")
  compose.onNodeWithText("案卷",useUnmergedTree=true).performScrollTo().performClick()
  choose("依法审理");until {it.caseFile.resolved}
  compose.onNodeWithText("行旅",useUnmergedTree=true).performScrollTo().performClick()
  choose("返回朝廷");until {it.journey==null}
  while(vm.state.value.speed!=64)compose.onNodeWithTag("clock-speed").performClick()
  until {it.regions[0].official>=0 && it.regions[0].official!=3 && it.people.any {p->p.id==it.regions[0].official && p.alive}}
  val world=vm.state.value.world!!;assertTrue(world.day>=90);assertEquals("免职候审",world.people[3].role);assertTrue(world.regions[0].corruption<58)
  val store=Store(compose.activity);assertEquals(world,store.load(vm.state.value.slot));store.close();compose.onNodeWithText("概览",useUnmergedTree=true).performScrollTo().performClick();compose.onNodeWithText("地方官：",substring=true).performScrollTo();screenshot("tour-long-term-result")
 }
 @Test fun newGamePolicyConfirmationAndNavigation() {
  val vm=ViewModelProvider(compose.activity)[GameViewModel::class.java]
  compose.waitUntil(30000){vm.state.value.ready}
  compose.waitForIdle()
  if(compose.onAllNodesWithText("开启纪年").fetchSemanticsNodes().isNotEmpty()) {
   compose.onNodeWithText("开启纪年").performClick();compose.onNodeWithText("建立国家").performClick()
  }
  compose.onNodeWithText("纪事",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNode(hasText("御前纪事") or hasText("行旅札记")).assertIsDisplayed();screenshot("story")
  compose.onNodeWithText("行动",useUnmergedTree=true).performScrollTo().performClick()
  compose.waitUntil(30000){compose.onAllNodesWithText("亲拟诏令").fetchSemanticsNodes().isNotEmpty()}
  if(!vm.state.value.paused)compose.onNodeWithTag("clock-toggle").performClick()
  val beforeRestRevision=requireNotNull(vm.state.value.world).revision
  compose.onNodeWithText("休养生息").performScrollTo().performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("确认执行").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("确认执行").assertIsDisplayed().performClick()
  // Dialog dismissal is not the same as a committed rule-engine transaction.
  compose.waitUntil(30000){!vm.state.value.busy && (vm.state.value.world?.revision ?: -1L)>beforeRestRevision}
  val decreeFold=compose.onNodeWithTag("fold-toggle-亲拟诏令").performScrollTo()
  val foldState=decreeFold.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
  assertTrue("Unknown decree fold state: $foldState",foldState=="已折叠"||foldState=="已展开")
  if(foldState=="已折叠")decreeFold.performClick()
  val inputFound=runCatching {compose.waitUntil(15000){compose.onAllNodesWithTag("custom-decree-input",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}}.isSuccess
  if(!inputFound) compose.onRoot(useUnmergedTree=true).printToLog("CHRONICLE_DECREE_SEMANTICS")
  assertTrue("Decree input absent after transaction; fold=$foldState revision=${vm.state.value.world?.revision} busy=${vm.state.value.busy}",inputFound)
  compose.onNodeWithTag("custom-decree-input",useUnmergedTree=true).performScrollTo().performTextInput("兴修水渠")
  compose.onNodeWithText("解析并预览").performScrollTo().performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("确认执行").fetchSemanticsNodes().isNotEmpty()};screenshot("custom-action")
  compose.onNodeWithText("确认执行").assertIsDisplayed().performClick()
  compose.waitUntil(30000){vm.state.value.world?.projects?.any {it.kind=="FARM"}==true}
  compose.onNodeWithText("朝政",useUnmergedTree=true).performClick();compose.onNodeWithText("国库",useUnmergedTree=true).assertIsDisplayed();screenshot("court")
  compose.onNodeWithText("世界",useUnmergedTree=true).performClick();compose.onNodeWithText("地区",useUnmergedTree=true).assertIsDisplayed();screenshot("world")
  compose.onNodeWithText("宗室",useUnmergedTree=true).performClick();compose.onNodeWithText("宗室与家族").assertIsDisplayed();screenshot("family")
  compose.onNodeWithText("档案",useUnmergedTree=true).performClick();compose.onNodeWithText("纪年存档").assertIsDisplayed();screenshot("archive")
  compose.onNodeWithContentDescription("关闭反馈").performClick();compose.onNodeWithText("模型",useUnmergedTree=true).performScrollTo().performClick();compose.onNodeWithText("搜索、下载与管理模型").performScrollTo().performClick();compose.waitUntil(15000){runCatching {compose.onNodeWithText("离线模型中心").assertIsDisplayed();true}.getOrDefault(false)};compose.onNodeWithText("模型库",useUnmergedTree=true).performClick();screenshot("models");compose.onNodeWithText("完成").performClick()
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
  val commandPrompt="<|im_start|>system\n只输出JSON：{\"commands\":[{\"type\":\"FARM\",\"region\":0}]}。将兴修水渠映射为FARM，最多1条，不要Markdown。<|im_end|>\n<|im_start|>user\n兴修水渠<|im_end|>\n<|im_start|>assistant\n".toByteArray()
  val structured=NativeBridge.generate(commandPrompt,64,300000).toString(Charsets.UTF_8)
  val parsed=runCatching {AiValidation.parse(structured,NewGame.create("测试","君主","综合发展","标准",1847))}.getOrNull()
  println("REAL_INTENT_STRUCTURED_VALID=${parsed!=null} text=$structured")
  val playable=parsed?:IntentParser.parse("兴修水渠");assertEquals("FARM",playable.commands.single().type)
  val world=NewGame.create("承熙","景衡","综合发展","标准",1847);val person=world.people[1]
  val formatted=NativeBridge.formatChat(arrayOf("system","user"),arrayOf(DialogueEngine.context(world,person,true).toByteArray(),"请用一句话谈谈你的目标。".toByteArray()),"auto")
  assertTrue(NativeBridge.countTokens(formatted)+104<512)
  var chunks=0
  val dialogue=NativeBridge.generateStreaming(formatted,96,300000,NativeTokenSink {chunks++}).toString(Charsets.UTF_8)
  assertTrue(dialogue.isNotBlank());assertTrue(chunks>0);assertNotNull(DialogueEngine.sanitize(world,person,dialogue));println("REAL_CHARACTER_CHAT chunks=$chunks text=$dialogue")
  val memory=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(memory);println("ANDROID_OBSERVED_PSS_KIB=${memory.totalPss}")
  repeat(3) {assertTrue(NativeBridge.generate(prompt,6,120000).isNotEmpty())}
  val executor=java.util.concurrent.Executors.newSingleThreadExecutor()
  val attempt=executor.submit<Boolean> {try {NativeBridge.generate(narrativePrompt,256,300000);false}catch(_:IllegalStateException){true}}
  Thread.sleep(100);NativeBridge.cancel();assertTrue("cancel must interrupt inference",attempt.get(30,java.util.concurrent.TimeUnit.SECONDS));executor.shutdownNow()
  assertTrue(NativeBridge.generate(prompt,6,120000).isNotEmpty())
  val resident=File("/proc/self/status").readLines().filter {it.startsWith("VmHWM:")||it.startsWith("VmRSS:")}.joinToString("; ")
  println("ANDROID_PROCESS_RESIDENT $resident")
  NativeBridge.unload()
  // Verify the exact application adapter, not only the raw JNI entry point.
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val manager=ModelManager(context);manager.threads=2;manager.contextLimit=512
  try {
   manager.import(android.net.Uri.fromFile(model))
   runBlocking {withTimeout(120000){while(manager.state.value.working)delay(100)}}
   assertTrue(manager.state.value.status,manager.state.value.exists)
   assertFalse(manager.isLoaded)
   runBlocking {withTimeout(120000){manager.ensureLoadedForChat()}}
   assertTrue(manager.state.value.status,manager.isLoaded)
   val county=world.towns.first {it.rank=="县城"};val magistrate=world.people.first {it.id==county.official}
   assertTrue(DialogueEngine.context(world,magistrate,true).contains(county.name))
   var appChunks=0;val appStart=System.nanoTime()
   val reply=runBlocking {withTimeout(210000){manager.chat(world,magistrate,emptyList(),"请用一句话介绍你的任所和职责。",{appChunks++})}}
   assertEquals("model",reply.source);assertTrue(reply.text.any {it in '\u4e00'..'\u9fff'});assertTrue(appChunks>0)
   println("REAL_APP_CHARACTER_CHAT elapsed_ms=${(System.nanoTime()-appStart)/1000000} chunks=$appChunks person=${magistrate.id} role=${magistrate.role} town=${county.id} text=${reply.text}")
   val localUnit=world.localDefense.units.first {it.home==county.id&&it.kind=="县兵"};val captain=world.people.first {it.id==localUnit.commander}
   assertTrue(DialogueEngine.context(world,captain,true).contains("俸禄"));assertTrue(DialogueEngine.context(world,captain,true).contains(localUnit.name))
   var militaryChunks=0;val militaryStart=System.nanoTime()
   val militaryReply=runBlocking {withTimeout(210000){manager.chat(world,captain,emptyList(),"请用一句话说明你的驻地与军务职责。",{militaryChunks++})}}
   assertEquals("model",militaryReply.source);assertTrue(militaryReply.text.any {it in '\u4e00'..'\u9fff'});assertTrue(militaryChunks>0)
   println("REAL_APP_LOCAL_OFFICER_CHAT elapsed_ms=${(System.nanoTime()-militaryStart)/1000000} chunks=$militaryChunks person=${captain.id} role=${captain.role} salary_liang=${PayrollEngine.salary(world,captain.id)} text=${militaryReply.text}")
  } finally {manager.unloadAsync();runBlocking {withTimeout(60000){while(manager.isLoaded)delay(100)}};manager.close()}
 }
}
