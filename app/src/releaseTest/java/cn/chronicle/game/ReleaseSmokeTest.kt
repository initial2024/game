package cn.chronicle.game

import android.app.Activity
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Black-box release tests intentionally never reference obfuscated game/core classes. */
class ReleaseSmokeTest {
 private val ins get()=InstrumentationRegistry.getInstrumentation()
 private val ctx get()=ins.targetContext
 private val device get()=UiDevice.getInstance(ins)
 internal fun until(timeout:Long=20000,test:()->Boolean){val end=android.os.SystemClock.uptimeMillis()+timeout;while(android.os.SystemClock.uptimeMillis()<end){if(runCatching(test).getOrDefault(false))return;android.os.SystemClock.sleep(150)};error("Release condition timed out")}
 internal fun click(selector:BySelector,scroll:Boolean=true){
  repeat(if(scroll)14 else 1){attempt->
   // Recover only a verified emulator launcher ANR. All actual app controls remain mandatory.
   if(device.findObject(By.textContains("Quickstep isn't responding"))!=null){
    val waitAction=device.findObject(By.text("Wait"))?:error("Quickstep ANR lacks its Wait control")
    println("RELEASE_MIDRUN_QUICKSTEP_RECOVERY attempt=${attempt+1} selector=$selector")
    waitAction.click()
    device.waitForIdle(700)
   }
   val node=device.wait(Until.findObject(selector),1000)
   if(node!=null){
    // Compose may replace a UiObject2 between lookup and bounds/click.
    // Recover this specific stale node by finding the SAME required selector again.
    // Do not ignore a missing control or relax the downstream save/map/GGUF assertions.
    val clicked=try {
     if(node.visibleBounds.height()>8){node.click();true}else false
    }catch(e:androidx.test.uiautomator.StaleObjectException){
     println("RELEASE_STALE_NODE_REQUERY attempt=${attempt+1} selector=$selector")
     return@repeat
    }
    if(clicked){device.waitForIdle(500);return}
   }
   if(scroll)device.swipe(device.displayWidth/2,device.displayHeight-140,device.displayWidth/2,230,12)
  }
  device.dumpWindowHierarchy(File(ctx.getExternalFilesDir(null),"release-failure.xml"));error("Missing release control: $selector package=${device.currentPackageName}")
 }
 internal fun click(text:String)=click(By.text(text))
 /**
  * Retry only a known Android emulator launcher ANR, never conceal application failures.
  * Continue only after the real game welcome control is visible.
  */
 internal fun waitForWelcome() {
  repeat(3) { attempt ->
   val quickstep=device.findObject(By.textContains("Quickstep isn't responding"))
   if(quickstep!=null){
    val waitButton=device.findObject(By.text("Wait"))?:error("Quickstep ANR without a Wait action")
    println("RELEASE_QUICKSTEP_ANR_RECOVERY=${attempt+1}")
    waitButton.click()
    device.waitForIdle(1000)
   }
   val command=device.executeShellCommand("am start -W -n cn.chronicle.shanhe/cn.chronicle.game.MainActivity")
   println("RELEASE_START_ATTEMPT=${attempt+1} status=${command.take(400)}")
   if(device.wait(Until.hasObject(By.text("开启纪年")),15000)) {
    println("RELEASE_WELCOME_VISIBLE=true attempt=${attempt+1}")
    return
   }
   println("RELEASE_WELCOME_MISSING attempt=${attempt+1} currentPackage=${device.currentPackageName}")
  }
  device.dumpWindowHierarchy(File(ctx.getExternalFilesDir(null),"release-failure.xml"))
  val logs=device.executeShellCommand("logcat -d -t 150 -s AndroidRuntime:E ActivityTaskManager:E")
  println("RELEASE_STARTUP_DIAGNOSTICS=\n${logs.takeLast(6000)}")
  error("Game welcome not visible after bounded launcher recovery; package=${device.currentPackageName}")
 }

 internal fun world():JSONObject {
  val db=SQLiteDatabase.openDatabase(ctx.getDatabasePath("chronicle.db").path,null,SQLiteDatabase.OPEN_READONLY)
  return db.use {d->
   val slot=ctx.getSharedPreferences("resume-world",0).getInt("slot",1)
   val length=d.rawQuery("SELECT length(json) FROM saves WHERE slot=?",arrayOf("$slot")).use {it.moveToFirst();it.getInt(0)}
   val out=StringBuilder(length);var offset=1
   while(offset<=length){out.append(d.rawQuery("SELECT substr(json,?,131072) FROM saves WHERE slot=?",arrayOf("$offset","$slot")).use {it.moveToFirst();it.getString(0)});offset+=131072}
   JSONObject(out.toString())
  }
 }
 private fun descendants(v:View):WebView? {if(v is WebView)return v;if(v is ViewGroup)for(i in 0 until v.childCount)descendants(v.getChildAt(i))?.let {return it};return null}
 internal fun js(code:String):String {
  val latch=CountDownLatch(1);var result="";var failure:Throwable?=null
  ins.runOnMainSync {try{val web=android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull {descendants(it)}?:error("No WebView");web.evaluateJavascript(code){result=it;latch.countDown()}}catch(e:Throwable){failure=e;latch.countDown()}}
  assertTrue(latch.await(15,TimeUnit.SECONDS));failure?.let {throw it};return result
 }
 internal fun scene()=JSONObject(JSONTokener(js("JSON.stringify(ChronicleMap.exportScenario())")).nextValue() as String)
 internal fun screenshot(name:String){val bitmap=ins.uiAutomation.takeScreenshot()!!;val values=android.content.ContentValues().apply {put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ChronicleCI")};val uri=ctx.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!;ctx.contentResolver.openOutputStream(uri)!!.use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
 @Test fun minifiedReleaseSavesMapNativeCallbacksAndRealGgufStreaming(){
  val external=File(ctx.getExternalFilesDir(null),"qwen.gguf");assertTrue("CI must stage the real GGUF",external.isFile)
  val root=File(ctx.filesDir,"models").apply {mkdirs()};val local=File(root,"active.gguf");external.copyTo(local,true)
  val digest=MessageDigest.getInstance("SHA-256");local.inputStream().use {i->val buffer=ByteArray(65536);while(true){val n=i.read(buffer);if(n<0)break;digest.update(buffer,0,n)}}
  val sha=digest.digest().joinToString(""){"%02x".format(it)};assertEquals("74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",sha)
  ctx.getSharedPreferences("inference",0).edit().putString("sha",sha).putInt("threads",2).putInt("context",512).commit()

  waitForWelcome()
  click("开启纪年");click("建立国家");until {world().getJSONObject("chronicle").getBoolean("enabled")}
  assertTrue(world().getJSONObject("sovereign").getJSONObject("guard").getInt("allocated")>0)
  screenshot("release-new-game")
  click("世界");click("打开世界舆图")
  until {runCatching {scene().getJSONArray("groups").length()>100}.getOrDefault(false)}
  val map=scene();assertEquals(171,(0 until map.getJSONArray("groups").length()).count {map.getJSONArray("groups").getJSONObject(it).optString("rank")=="县"})
  js("document.querySelector('#search').click();true")
  until {js("!!document.querySelector('input[type=search]')")=="true"}
  js("(function(){const e=document.querySelector('input[type=search]');e.value='长安县';e.dispatchEvent(new Event('input',{bubbles:true}));return true})()")
  until {js("!!document.querySelector('#panel button.listing[title=\"county-jingji-01\"]')")=="true"}
  js("document.querySelector('#panel button.listing[title=\"county-jingji-01\"]').click();true")
  click("官员")
  val field=device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000)!!;field.click();device.waitForIdle(500);field.text="长安新县";device.pressBack()
  click("预览更名 · 5万两");click("确认执行");click("关闭城邑")
  click("▶ 继续")
  until {val ts=world().getJSONArray("towns");(0 until ts.length()).any {ts.getJSONObject(it).optString("name")=="长安新县"}}
  click("Ⅱ 暂停")
  until {scene().getJSONObject("places").getJSONObject("county-jingji-01").getString("name")=="长安新县"}
  screenshot("release-map-saved-city")
  // Stay in the map; never visit the model center or manually load the model.
  click("返回地区列表");click("打开世界舆图")
  until {runCatching {scene().getJSONObject("places").getJSONObject("county-jingji-01").getString("name")=="长安新县"}.getOrDefault(false)}
  js("document.querySelector('#search').click();true");until {js("!!document.querySelector('input[type=search]')")=="true"}
  js("(function(){const e=document.querySelector('input[type=search]');e.value='长安新县';e.dispatchEvent(new Event('input',{bubbles:true}));return true})()")
  until {js("!!document.querySelector('#panel button.listing[title=\"county-jingji-01\"]')")=="true"}
  js("document.querySelector('#panel button.listing[title=\"county-jingji-01\"]').click();true")
  click("官员");click("与地方官交谈")
  assertTrue("Model must start unloaded and be offered on send",device.wait(Until.hasObject(By.textContains("发送时自动加载本地 AI")),10000))
  val chat=device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000)!!;chat.click();device.waitForIdle(500);chat.text="你近来是否安好";device.pressBack();click("发送对话")
  until(210000){device.hasObject(By.textContains("本地模型回复 ·"))}
  screenshot("release-real-county-chat")
  val db=SQLiteDatabase.openDatabase(ctx.getDatabasePath("chronicle.db").path,null,SQLiteDatabase.OPEN_READONLY)
  db.use {d->assertTrue(d.rawQuery("SELECT content FROM dialogues WHERE role='assistant' AND source='model'",null).use {it.moveToFirst()&&it.getString(0).isNotBlank()})}
  println("RELEASE_MINIFIED_MAP_DATABASE_AND_REAL_MODEL_CHAT=true AUTO_LOAD_ON_FIRST_SEND=true sha=$sha")
 }
}

class ReleaseRestoreTest {
 @Test fun savedCountyAndMapSurviveFullProcessRestart(){
  val h=ReleaseSmokeTest();val ctx=InstrumentationRegistry.getInstrumentation().targetContext
  val towns=h.world().getJSONArray("towns");assertTrue((0 until towns.length()).any {towns.getJSONObject(it).optString("name")=="长安新县"})
  println(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand("am start -W -n cn.chronicle.shanhe/cn.chronicle.game.MainActivity"))
  h.click("世界");h.click("打开世界舆图")
  h.until {h.scene().getJSONObject("places").getJSONObject("county-jingji-01").getString("name")=="长安新县"}
  h.screenshot("release-restarted-map")
  println("RELEASE_FULL_PROCESS_RESTART_MAP_PERSISTENCE=true")
 }
}
