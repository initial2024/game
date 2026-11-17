package cn.chronicle.game

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import cn.chronicle.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ChronicleFeatureTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun click(text:String){val node=compose.onNodeWithText(text);node.performScrollTo();node.performClick()}
 @Test fun petitionEvidenceVerdictGuardsAndMirrorsAreReachable(){
  val vm=ViewModelProvider(compose.activity)[GameViewModel::class.java]
  compose.waitUntil(30000){vm.state.value.ready}
  if(vm.state.value.world==null){vm.newGame("新章测试","景衡","综合发展","宽裕","1847");compose.waitUntil(15000){vm.state.value.world!=null&&!vm.state.value.busy}}
  if(!vm.state.value.paused)vm.toggleClock()
  compose.waitUntil(15000){compose.onAllNodes(hasTestTag("world-header") and hasText(vm.state.value.world!!.name,substring=true)).fetchSemanticsNodes().size==1}
  if(!vm.state.value.world!!.chronicle.enabled){val w=vm.state.value.world!!;vm.submit(listOf(Command("CONTENT_ENABLE",mode="自由",level=1)),w.revision);compose.waitUntil(15000){!vm.state.value.busy&&vm.state.value.world!!.chronicle.enabled}}
  click("案簿");compose.onNodeWithText("地方陈情案簿").assertExists()
  val w=vm.state.value.world!!;vm.submit(listOf(Command("CASE_REQUEST")),w.revision);compose.waitUntil(10000){!vm.state.value.busy&&vm.state.value.world!!.pending.any {it.command.type=="CASE_REQUEST"}}
  val ctx=InstrumentationRegistry.getInstrumentation().targetContext
  ctx.deleteDatabase("chronicle-v07-regression.db") // Disposable test-only database, never the player save.
  Store(ctx,"chronicle-v07-regression.db").use {store->
   var x=ChronicleEngine.start(NewGame.create("隔离","纪衡","综合发展","宽裕",1847));store.create(1,x)
   val out=RealtimeEngine.submit(x,Command("CASE_REQUEST"),x.revision);store.commit(1,x.revision,out);x=out.world
   val resolved=RealtimeEngine.advance(x,5);store.commit(1,x.revision,resolved)
   val loaded=store.load(1)!!;assertEquals(1,loaded.caseBook.cases.size);assertEquals(resolved.world,loaded)
   store.copyTo(1,2);val opened=RealtimeEngine.submit(loaded,Command("CASE_OPEN",target=loaded.caseBook.cases.single().id,mode="御前受理"),loaded.revision);store.commit(1,loaded.revision,opened)
   assertEquals(0,store.load(2)!!.pending.size);assertEquals(1,store.load(1)!!.pending.size)
   val exported=store.export(1);store.import(exported,3);assertEquals(store.load(1),store.load(3))
  }
  compose.onNodeWithTag("nav-2").performClick();click("君主");compose.onNodeWithText("${vm.state.value.world!!.sovereign.guard.name} · 第1代君主").assertExists()
  compose.onNodeWithTag("nav-3").performClick();click("模型");click("搜索、下载与管理模型");click("设置")
  assertEquals(3,ModelTransfer.urls(ModelCatalog.builtIn.first(),"auto").size);assertTrue(ModelTransfer.urls(ModelCatalog.builtIn.first(),"hf-mirror").single().startsWith("https://hf-mirror.com/"))
 }
}
