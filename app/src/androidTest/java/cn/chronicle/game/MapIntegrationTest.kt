package cn.chronicle.game

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import cn.chronicle.core.*
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private fun cleanLauncher(){if(InstrumentationRegistry.getArguments().getString("ci_emulator")=="true")android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("am force-stop com.android.launcher3")).use {it.readBytes()}}
private fun captureMap(name:String){
 cleanLauncher();InstrumentationRegistry.getInstrumentation().waitForIdleSync();UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(800);android.os.SystemClock.sleep(500);val context=InstrumentationRegistry.getInstrumentation().targetContext;val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
 val values=android.content.ContentValues().apply {put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChronicleCI")}
 val uri=context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!;context.contentResolver.openOutputStream(uri)!!.use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
}
@RunWith(AndroidJUnit4::class)
class MapIntegrationTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Before fun setup(){cleanLauncher()}
 private fun js(code:String):String {val latch=CountDownLatch(1);var result="";compose.runOnUiThread {compose.activity.mapController!!.evaluateForTest(code){result=it;latch.countDown()}};assertTrue("WebView evaluation timeout",latch.await(15,TimeUnit.SECONDS));return result}
 private fun scene()=JSONObject(JSONTokener(js("JSON.stringify(ChronicleMap.exportScenario())")).nextValue() as String)
 private fun ready(vm:GameViewModel){compose.waitUntil(30000){compose.activity.mapController?.readyRevision==vm.state.value.world?.revision}}
 private fun openMap(){compose.onNodeWithText("世界",useUnmergedTree=true).performClick();compose.onNodeWithText("打开世界舆图 v4").performScrollTo().performClick()}
 private fun touch(selector:String){
  val query="(function(){const e=document.querySelector("+MapBridgePolicy.quoted(selector)+");if(!e)throw Error('missing element');const r=e.getBoundingClientRect();return JSON.stringify({x:r.x+r.width/2,y:r.y+r.height/2,width:innerWidth,height:innerHeight});})()"
  val p=JSONObject(JSONTokener(js(query)).nextValue() as String);assertTrue("DOM control outside viewport: $p",p.getDouble("y") in 0.0..p.getDouble("height"))
  val x=p.getDouble("x");val y=p.getDouble("y");val viewportWidth=p.getDouble("width")
  var nativeBounds="";compose.runOnUiThread {nativeBounds=compose.activity.mapController!!.boundsForTest()};println("MAP_TOUCH=$selector bounds=$p native=$nativeBounds node=${compose.onNodeWithTag("world-map-web").fetchSemanticsNode().boundsInWindow}")
  compose.onNodeWithTag("world-map-web").performTouchInput {val factor=width/viewportWidth;click(androidx.compose.ui.geometry.Offset((x*factor).toFloat(),(y*factor).toFloat()))}

 }
 private fun cityClick(name:String){
  touch("#search");compose.waitUntil(10000){js("!!document.querySelector('input[type=search]')")=="true"};js("(function(){const e=document.querySelector('input[type=search]');e.value="+MapBridgePolicy.quoted(name)+";e.dispatchEvent(new Event('input',{bubbles:true}));return true})()")
  touch("#panel button.listing");println("MAP_AFTER_CITY_TOUCH="+js("JSON.stringify({panel:document.querySelector('#panel').textContent,events:window.__nativeEvents||null,error:window.__mapRenderError||null})"));captureMap("map-after-city-touch");compose.waitUntil(10000){compose.onAllNodesWithText("城邑名称").fetchSemanticsNodes().isNotEmpty()}
 }

 @Test fun mapClickNativeOrderCommitRefreshAndSaveIsolation(){
  val vm=ViewModelProvider(compose.activity)[GameViewModel::class.java];compose.waitUntil(30000){vm.state.value.ready}
  vm.newGame("古代地图国","君主","综合发展","标准","1847",setting=EraRules.ANCIENT);compose.waitUntil(30000){vm.state.value.world?.name=="古代地图国"&&!vm.state.value.busy};if(!vm.state.value.paused)compose.runOnUiThread {vm.toggleClock()};vm.message("")
  val a=vm.state.value.slot;openMap();ready(vm);println("MAP_RENDER_DIAGNOSTICS="+js("JSON.stringify({ua:navigator.userAgent,size:[innerWidth,innerHeight],body:[document.body.scrollWidth,document.body.scrollHeight],canvas:document.querySelector('#map').getBoundingClientRect().toJSON(),map:__tianxiaDiagnostics(),error:window.__mapRenderError||null})"));assertEquals("game-slot-$a",scene().getString("id"));assertEquals("古代地图国",scene().getJSONArray("polities").getJSONObject(0).getString("name"));captureMap("map-native-ancient")
  cityClick("澄江城");compose.onNodeWithText("城邑名称").performTextReplacement("澄江新城");compose.onNodeWithText("申请更名 · 5万两").performScrollTo().performClick();compose.onNodeWithText("确认执行").performClick();compose.waitUntil(10000){!vm.state.value.busy&&vm.state.value.world!!.pending.any {it.command.type=="CITY_RENAME"}}
  compose.onNodeWithTag("map-clock").performClick();compose.waitUntil(15000){vm.state.value.world?.cartography?.places?.get("city-r0")?.name=="澄江新城"};compose.onNodeWithTag("map-clock").performClick();ready(vm)
  assertEquals("澄江新城",scene().getJSONObject("places").getJSONObject("city-r0").getString("name"))
  val before=vm.state.value.world!!;compose.runOnUiThread {compose.activity.mapController!!.receive("{\"type\":\"place-select\",\"sceneId\":\"game-slot-$a\",\"placeId\":\"city-r0\"}",Uri.parse("https://evil.test"),true)}
  assertEquals(before,vm.state.value.world);assertTrue(compose.activity.mapController!!.rejectedMessages>0)
  assertEquals("true",js("(function(){const before=JSON.stringify(ChronicleMap.exportScenario());let caught=0;try{ChronicleMap.patchScenario({meta:{title:'evil'}})}catch(e){caught++}try{ChronicleMap.applyWorldDelta({sceneId:'game-slot-$a',revision:0,changes:{}})}catch(e){caught++}return caught===2&&JSON.stringify(ChronicleMap.exportScenario())===before})()"))
  cityClick("澄江新城");compose.onNodeWithText("营建新邑 · 120万两 / 120日").performScrollTo().performClick();compose.onNodeWithText("确认执行").performClick();compose.waitUntil(15000){!vm.state.value.busy&&vm.state.value.world!!.projects.any {it.kind=="CITY_FOUND"}};ready(vm)
  assertEquals("planned",scene().getJSONObject("placeStates").getJSONObject("settlement-r0").getString("status"));captureMap("map-native-planned")
  while(vm.state.value.speed!=64)compose.onNodeWithTag("map-speed").performClick();compose.onNodeWithTag("map-clock").performClick();compose.waitUntil(30000){vm.state.value.world?.cartography?.placeStates?.get("settlement-r0")?.status=="active"};compose.onNodeWithTag("map-clock").performClick();ready(vm)
  assertEquals("active",scene().getJSONObject("placeStates").getJSONObject("settlement-r0").getString("status"));cityClick("澄江新邑");captureMap("map-native-completed");compose.onNodeWithText("关闭").performClick()
  val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());val portraitController=compose.activity.mapController;device.setOrientationLeft();compose.waitUntil(30000){compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE&&compose.activity.mapController!=null&&compose.activity.mapController!==portraitController};ready(vm);assertEquals("true",js("innerWidth>innerHeight"));assertEquals("active",scene().getJSONObject("placeStates").getJSONObject("settlement-r0").getString("status"));captureMap("map-landscape");val landscapeController=compose.activity.mapController;device.setOrientationNatural();device.unfreezeRotation();compose.waitUntil(30000){compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT&&compose.activity.mapController!=null&&compose.activity.mapController!==landscapeController};ready(vm)
  vm.newGame("另一条世界线","新君","综合发展","标准","0",setting="工业初兴");compose.waitUntil(30000){vm.state.value.world?.name=="另一条世界线"&&!vm.state.value.busy};if(!vm.state.value.paused)compose.runOnUiThread {vm.toggleClock()};ready(vm);assertNotEquals("game-slot-$a",scene().getString("id"));assertEquals("澄江城",scene().getJSONObject("places").getJSONObject("city-r0").getString("name"))
  vm.load(a);compose.waitUntil(15000){vm.state.value.slot==a&&!vm.state.value.busy};ready(vm);assertEquals("澄江新城",scene().getJSONObject("places").getJSONObject("city-r0").getString("name"))
  val saved=vm.state.value.world!!;val marker=JSONObject().put("slot",a).put("revision",saved.revision).put("name",saved.name);java.io.File(compose.activity.getExternalFilesDir(null),"map-checkpoint.json").writeText(marker.toString())
  repeat(4){compose.onNodeWithText("返回地区列表").performClick();compose.waitUntil(5000){compose.activity.mapController==null};assertEquals(0,MapWebController.activeCount);compose.onNodeWithText("打开世界舆图 v4").performScrollTo().performClick();ready(vm)}
  compose.onNodeWithText("地图沙盘").performClick();compose.waitUntil(30000){compose.activity.mapController?.let {it.isEditor&&it.isPageReady}==true}
  js("ChronicleMap.loadDemo()");assertEquals("demo-fiction",scene().getString("id"));assertEquals(saved,vm.state.value.world);captureMap("map-sandbox")
  compose.onNodeWithText("返回游戏地图").performClick();ready(vm);assertEquals("game-slot-$a",scene().getString("id"));assertEquals("澄江新城",scene().getJSONObject("places").getJSONObject("city-r0").getString("name"))
  println("MAP_NATIVE_CLOSED_LOOP=true slot=$a revision=${saved.revision} status=active")

 }
 @Test fun legacySaveSecurityAndRevisionBoundaries(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext;val dbName="map-v4-migration-test.db";context.deleteDatabase(dbName)
  val old=NewGame.create("旧世界","君主","综合发展","标准",1847)
  val raw=kotlinx.serialization.json.JsonObject(SaveJson.parseToJsonElement(SaveJson.encodeToString(old)).let {it as kotlinx.serialization.json.JsonObject}.filterKeys {it!="setting"&&it!="cartography"}).toString()
  var store=Store(context,dbName);store.writableDatabase.execSQL("INSERT INTO saves(slot,revision,name,turn,json) VALUES(1,0,'旧世界',0,?)",arrayOf(raw));store.close();store=Store(context,dbName)
  val w=store.load(1)!!;assertEquals("工业初兴",w.setting);assertNull(w.cartography);assertEquals(raw,store.readableDatabase.rawQuery("SELECT json FROM saves WHERE slot=1",null).use {it.moveToFirst();it.getString(0)})
  val good="""{"type":"place-select","sceneId":"game-slot-1","placeId":"city-r0"}""";assertEquals("city-r0",MapBridgePolicy.selection(good,w,1).placeId)
  for(bad in listOf(good.replace("game-slot-1","game-slot-2"),good.replace("city-r0","missing"),good.dropLast(1)+",\"treasury\":999}")){try{MapBridgePolicy.selection(bad,w,1);fail("unsafe message accepted")}catch(_:IllegalArgumentException){}}
  assertFalse(MapBridgePolicy.trusted("https://evil.test/assets/maps/chronicle_world_map_v4.html"));assertFalse(MapBridgePolicy.trusted("file:///android_asset/map.html"));assertFalse(MapBridgePolicy.trusted(MapBridgePolicy.PAGE+"/../evil"))
  val out=RealtimeEngine.submit(w,Command("CITY_FOUND"),0);store.commit(1,0,out);store.copyTo(1,2);val next=RealtimeEngine.advance(out.world,90);store.commit(1,1,next);store.close();store=Store(context,dbName)
  assertEquals(2L,store.load(1)!!.revision);assertEquals(1L,store.load(2)!!.revision);assertEquals("planned",MapSnapshotMapper.scene(store.load(1)!!,1).placeStates["settlement-r0"]!!.status);store.close()
  val hostile="引号\"\\\n\u2028\u2029');window.evil=true;//";assertEquals(hostile,JSONTokener(MapBridgePolicy.quoted(hostile)).nextValue())
 }
}

@RunWith(AndroidJUnit4::class)
class MapRestartTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun persistedWorldAndMapAfterActualProcessRestart(){
  cleanLauncher();val vm=ViewModelProvider(compose.activity)[GameViewModel::class.java];compose.waitUntil(30000){vm.state.value.ready}
  val marker=JSONObject(java.io.File(compose.activity.getExternalFilesDir(null),"map-checkpoint.json").readText());assertEquals(marker.getInt("slot"),vm.state.value.slot);assertEquals(marker.getLong("revision"),vm.state.value.world!!.revision);assertTrue(vm.state.value.paused)
  assertEquals("澄江新城",vm.state.value.world!!.cartography!!.places["city-r0"]!!.name)
  compose.onNodeWithText("世界",useUnmergedTree=true).performClick();compose.onNodeWithText("打开世界舆图 v4").performScrollTo().performClick();compose.waitUntil(30000){compose.activity.mapController?.readyRevision==vm.state.value.world!!.revision}
  val latch=CountDownLatch(1);var raw="";compose.runOnUiThread {compose.activity.mapController!!.evaluateForTest("JSON.stringify(ChronicleMap.exportScenario())"){raw=it;latch.countDown()}};assertTrue(latch.await(15,TimeUnit.SECONDS));val scene=JSONObject(JSONTokener(raw).nextValue() as String)
  assertEquals("active",scene.getJSONObject("placeStates").getJSONObject("settlement-r0").getString("status"));assertEquals("澄江新城",scene.getJSONObject("places").getJSONObject("city-r0").getString("name"));captureMap("map-after-process-restart")
  println("MAP_PROCESS_RESTART_CONSISTENT=true revision=${vm.state.value.world!!.revision}")
 }
}
