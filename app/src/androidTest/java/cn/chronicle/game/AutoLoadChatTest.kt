package cn.chronicle.game

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** Isolated model directories: never modify an installed model or another save. */
class AutoLoadChatTest {
 private fun isolated(block:(ModelManager,File,Context)->Unit) {
  val base=InstrumentationRegistry.getInstrumentation().targetContext
  val id="autoload-test-${UUID.randomUUID()}"
  val root=File(base.cacheDir,id).apply{mkdirs()}
  val context=object:ContextWrapper(base){
   override fun getFilesDir()=root
   override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("$id-$name",mode)
  }
  val manager=ModelManager(context)
  try {block(manager,root,context)}finally{manager.close();root.deleteRecursively();base.deleteSharedPreferences("$id-inference")}
 }
 @Test fun missingInstalledModelExplainsTemplateAndNeverDownloads()=isolated {manager,_,_->
  assertTrue(manager.autoLoadOnChat)
  val error=runBlocking {runCatching{manager.ensureLoadedForChat()}.exceptionOrNull()}
  assertTrue(error?.message.orEmpty().contains("尚未安装模型"))
  assertFalse(manager.state.value.loaded);assertFalse(manager.state.value.working)
  assertFalse(manager.state.value.loading);assertFalse(manager.state.value.exists)
 }
 @Test fun disabledAutomaticLoadIsExplicitAndPersisted()=isolated {manager,_,context->
  manager.autoLoadOnChat=false
  assertFalse(context.getSharedPreferences("inference",0).getBoolean("auto-load-chat",true))
  val error=runBlocking {runCatching{manager.ensureLoadedForChat()}.exceptionOrNull()}
  assertTrue(error?.message.orEmpty().contains("已关闭发送时自动加载"))
  assertFalse(manager.state.value.working)
 }
 @Test fun modifiedGgufRejectedBeforeNativeLoadAndErrorRetained()=isolated {manager,root,context->
  val file=File(root,"models/active.gguf")
  file.writeBytes(ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(0x46554747).putInt(3).putLong(1).putLong(1).array())
  context.getSharedPreferences("inference",0).edit().putString("sha","invalid").commit()
  val error=runBlocking {runCatching{manager.ensureLoadedForChat()}.exceptionOrNull()}
  assertTrue(error?.message.orEmpty().contains("模型已被修改或损坏"))
  assertTrue(manager.state.value.status.contains("重新下载或导入"))
  assertTrue(file.isFile);assertFalse(manager.isLoaded);assertFalse(manager.state.value.loading)
 }
 @Test fun cancellationInterruptsChecksumAndKeepsInstalledFile()=isolated {manager,root,context->
  val file=File(root,"models/active.gguf")
  RandomAccessFile(file,"rw").use {it.write(ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(0x46554747).putInt(3).putLong(1).putLong(1).array());it.setLength(64L*1024*1024)}
  context.getSharedPreferences("inference",0).edit().putString("sha","deliberately invalid; cancelled before validation").commit()
  runBlocking {
   val job=launch(Dispatchers.Default){manager.ensureLoadedForChat()}
   withTimeout(10000){manager.state.first {it.loading}}
   manager.cancel();withTimeout(10000){job.join()};assertTrue(job.isCancelled)
  }
  assertTrue(file.isFile);assertEquals(64L*1024*1024,file.length())
  assertFalse(manager.state.value.working);assertFalse(manager.state.value.loading);assertFalse(manager.isLoaded)
  assertTrue(manager.state.value.status.contains("加载已取消"))
 }
}
