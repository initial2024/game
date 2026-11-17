package cn.chronicle.game

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.chronicle.core.ChronicleEngine
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChronicleClockUiTest {
 @get:Rule val compose = createAndroidComposeRule<MainActivity>()

 @Test fun visibleClockReflectsCommittedGameDays() {
  val vm = ViewModelProvider(compose.activity)[GameViewModel::class.java]
  compose.waitUntil(30000) { vm.state.value.ready && !vm.state.value.busy }
  if(vm.state.value.world == null) {
   vm.newGame("时间显示测试","景衡","综合发展","标准","1847", guided=false)
   compose.waitUntil(15000) { vm.state.value.world != null && !vm.state.value.busy }
  }
  val w = requireNotNull(vm.state.value.world)
  compose.onNodeWithTag("clock-date").assertExists()
  compose.onNodeWithTag("clock-day").assertExists()
  if(!ChronicleEngine.needsPause(w) && !w.ended) {
   if(vm.state.value.paused) vm.toggleClock()
   val before = requireNotNull(vm.state.value.world).day
   compose.waitUntil(15000) { (vm.state.value.world?.day ?: before) > before }
   if(!vm.state.value.paused) vm.toggleClock()
   compose.waitForIdle()
   val latest = requireNotNull(vm.state.value.world)
   assertTrue(latest.day > before)
   compose.onNodeWithTag("clock-date").assertTextEquals(latest.date)
   compose.onNodeWithTag("clock-day").assertTextEquals("总第${latest.day+1}日")
  } else {
   // The clock must visibly explain mandatory stops instead of pretending to advance.
   compose.onNodeWithTag("clock-status").assertExists()
  }
 }
}
