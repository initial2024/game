package cn.chronicle.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.chronicle.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class UiState(val world:World?=null,val slot:Int=1,val slots:List<Slot> = emptyList(),val busy:Boolean=false,val message:String="",val archive:List<Entry> = emptyList(),val offset:Int=0,val aiText:String="",val generating:Boolean=false)
class GameViewModel(app:Application):AndroidViewModel(app) {
 private val store=Store(app)
 private val lock=Mutex()
 private val mutable=MutableStateFlow(UiState())
 val state=mutable.asStateFlow()
 val models=ModelManager(app)
 private var generation:Job?=null
 init { viewModelScope.launch(Dispatchers.IO) { runCatching { val slots=store.slots(); val id=slots.firstOrNull()?.id?:1; mutable.value=UiState(world=store.load(id),slot=id,slots=slots) }.onFailure { message("存档读取失败：${it.message}。请保留数据并导出排查。") } } }
 fun message(text:String) { mutable.value=mutable.value.copy(message=text) }
 fun newGame(name:String,ruler:String,strategy:String,difficulty:String,seed:String) = task {
  val id=(1..8).firstOrNull { n -> store.slots().none { it.id==n } } ?: error("8 个存档槽已满，请导出后保留现有游戏。")
  val number=seed.toLongOrNull() ?: error("世界种子须为 64 位整数。")
  val w=NewGame.create(name,ruler,strategy,difficulty,number);store.create(id,w);mutable.value=mutable.value.copy(world=w,slot=id,slots=store.slots(),aiText="",archive=emptyList(),message="新纪年已建立。每次行动自动保存。")
 }
 fun submit(commands:List<Command>,expected:Long) {
  if(mutable.value.busy || commands.isEmpty()) return
  cancelAi()
  task {
   val start=mutable.value.world ?: error("请先新建游戏")
   require(start.revision==expected) { "局势已改变，请重新预览。" }
   // Preflight the entire bounded plan before any database mutation.
   var preview=start
   commands.take(3).forEach { preview=Rules.apply(preview,it,preview.revision).world }
   for(c in commands.take(3)) {
    val current=mutable.value.world!!; val out=Rules.apply(current,c,current.revision)
    store.commit(mutable.value.slot,current.revision,out)
    mutable.value=mutable.value.copy(world=out.world,slots=store.slots(),aiText="",message="已执行${Rules.label(c)}，自动存档完成。")
   }
   if(models.isLoaded) generate()
  }
 }
 fun load(id:Int)=task { cancelAi(); val w=store.load(id)?:error("存档不存在");mutable.value=mutable.value.copy(world=w,slot=id,aiText="",archive=emptyList(),offset=0,message="存档已读取。") }
 fun manualSave()=task { val id=(1..8).firstOrNull { n->store.slots().none { it.id==n } } ?: error("没有空存档槽");store.copyTo(mutable.value.slot,id);mutable.value=mutable.value.copy(slots=store.slots(),message="已建立独立快照：槽 $id") }
 fun page(offset:Int)=task { val p=offset.coerceAtLeast(0);mutable.value=mutable.value.copy(archive=store.history(mutable.value.slot,p),offset=p) }
 suspend fun export():String=withContext(Dispatchers.IO) { lock.withLock { store.export(mutable.value.slot) } }
 fun import(text:String)=task { val id=(1..8).firstOrNull { n->store.slots().none { it.id==n } }?:error("没有空槽");store.import(text,id);mutable.value=mutable.value.copy(slots=store.slots(),message="导入完成，位于槽 $id。") }
 fun generate() {
  cancelAi();val w=mutable.value.world?:return;val slot=mutable.value.slot
  generation=viewModelScope.launch {
   mutable.value=mutable.value.copy(generating=true)
   try {
    val text=withContext(Dispatchers.IO) { withTimeout(45000) { models.narrate(MemoryEngine.context(w)) } }
    if(mutable.value.slot==slot && mutable.value.world?.revision==w.revision) mutable.value=mutable.value.copy(aiText=text)
   } catch(e:CancellationException) { /* authoritative turn was already saved */ }
   catch(e:Exception) { message("AI 暂不可用，已保留规则叙事：${e.message}") }
   finally { if(mutable.value.slot==slot) mutable.value=mutable.value.copy(generating=false) }
  }
 }
 fun cancelAi() { models.cancel();generation?.cancel();mutable.value=mutable.value.copy(generating=false) }
 private fun task(block:suspend ()->Unit) {
  if(mutable.value.busy) return
  mutable.value=mutable.value.copy(busy=true)
  viewModelScope.launch(Dispatchers.IO) { lock.withLock { try { block() } catch(e:Exception) { message(e.message?:"操作失败") } finally { mutable.value=mutable.value.copy(busy=false) } } }
 }
 override fun onCleared() { models.close();store.close() }
}
