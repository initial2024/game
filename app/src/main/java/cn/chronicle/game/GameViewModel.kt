package cn.chronicle.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.chronicle.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class UiState(val world:World?=null,val slot:Int=1,val slots:List<Slot> = emptyList(),val busy:Boolean=false,val message:String="",val archive:List<Entry> = emptyList(),val offset:Int=0,val aiText:String="",val generating:Boolean=false,val paused:Boolean=true,val speed:Int=1,val ready:Boolean=false,val cityId:String?=null)
data class ChatState(val person:Int?=null,val records:List<DialogueRecord> = emptyList(),val working:Boolean=false,val partial:String="",val note:String="")
class GameViewModel(app:Application):AndroidViewModel(app) {
 private val store=Store(app)
 private val resumePrefs=app.getSharedPreferences("resume-world",0)
 private val lock=Mutex()
 private val mutable=MutableStateFlow(UiState())
 val state=mutable.asStateFlow()
 val models=ModelManager(app)
 private var generation:Job?=null
 private val chatMutable=MutableStateFlow(ChatState())
 val chat=chatMutable.asStateFlow()
 private var chatJob:Job?=null
 private var chatEpoch=0L
 @Volatile private var foreground=true
 fun foreground(active:Boolean) {foreground=active}
 fun toggleClock() {mutable.update { currentUi -> currentUi.copy(paused=!currentUi.paused) }}
 fun changeSpeed() {val values=listOf(1,4,16,64);mutable.update { currentUi -> currentUi.copy(speed=values[(values.indexOf(currentUi.speed)+1)%values.size]) }}
 init {
  viewModelScope.launch(Dispatchers.IO) {
   while(isActive) {
    delay(2000)
    if(!foreground || mutable.value.paused || mutable.value.busy || mutable.value.world==null || lock.isLocked)continue
    lock.withLock {
     if(!foreground || mutable.value.paused || mutable.value.busy)return@withLock
     runCatching {
      val current=mutable.value.world?:return@withLock
      val out=RealtimeEngine.advance(current,mutable.value.speed)
      val meaningful=out.entry.events.isNotEmpty()||out.entry.ledger.isNotEmpty()
      store.commit(mutable.value.slot,current.revision,out,meaningful)
      mutable.update { currentUi -> currentUi.copy(world=out.world,slots=if(meaningful)store.slots() else mutable.value.slots) }
     }.onFailure {mutable.update { currentUi -> currentUi.copy(paused=true,message="时间已暂停：${it.message}") }}
    }
   }
  }
 }
 init { viewModelScope.launch(Dispatchers.IO) { runCatching { val slots=store.slots(); val remembered=resumePrefs.getInt("slot",1); val id=slots.firstOrNull {it.id==remembered}?.id?:slots.firstOrNull()?.id?:1; mutable.value=UiState(world=loadWorld(id),slot=id,slots=slots,ready=true) }.onFailure { mutable.update {currentUi->currentUi.copy(ready=true)};message("存档读取失败：${it.message}。请保留数据并导出排查。") } } }
 private fun loadWorld(id:Int):World? {
  val world=store.load(id)?:return null;val upgraded=RealtimeEngine.refundLegacyChat(world)?:return world
  store.commit(id,world.revision,upgraded);return upgraded.world
 }
 fun openCity(id:String){val w=mutable.value.world?:return;if(MunicipalEngine.all(w).any {it.id==id})mutable.update {it.copy(cityId=id)}}
 fun closeCity(){mutable.update {it.copy(cityId=null)}}
 fun message(text:String) { mutable.update { currentUi -> currentUi.copy(message=text) } }
 fun newGame(name:String,ruler:String,strategy:String,difficulty:String,seed:String,era:String="启元",scale:String="中等国家",atmosphere:String="均势博弈",setting:String="工业初兴") = task {
  val id=(1..8).firstOrNull { n -> store.slots().none { it.id==n } } ?: error("8 个存档槽已满，请导出后保留现有游戏。")
  val number=seed.toLongOrNull() ?: error("世界种子须为 64 位整数。")
  cancelAi();chatMutable.value=ChatState();val w=NewGame.create(name,ruler,strategy,difficulty,number,era,scale,atmosphere,setting);store.create(id,w);resumePrefs.edit().putInt("slot",id).commit();mutable.update { currentUi -> currentUi.copy(world=w,slot=id,cityId=null,slots=store.slots(),paused=false,aiText="",archive=emptyList(),message="新纪年已建立。每次行动自动保存。") }
 }
 fun submit(commands:List<Command>,expected:Long) {
  if(mutable.value.busy || commands.isEmpty()) return
  cancelAi()
  task {
   val start=mutable.value.world ?: error("请先新建游戏")
   require(expected<=start.revision) { "局势已改变，请重新预览。" }
   // Preflight the entire bounded plan before any database mutation.
   var preview=start
   commands.take(3).forEach { preview=RealtimeEngine.submit(preview,it,preview.revision).world }
   for(c in commands.take(3)) {
    val current=mutable.value.world!!; val out=RealtimeEngine.submit(current,c,current.revision)
    store.commit(mutable.value.slot,current.revision,out)
    mutable.update { currentUi -> currentUi.copy(world=out.world,slots=store.slots(),aiText="",message="已执行${Rules.label(c)}，自动存档完成。") }
   }
   if(models.isLoaded) generate()
  }
 }
 fun load(id:Int)=task { cancelAi();chatMutable.value=ChatState(); val w=loadWorld(id)?:error("存档不存在");resumePrefs.edit().putInt("slot",id).commit();mutable.update { currentUi -> currentUi.copy(world=w,slot=id,cityId=null,paused=true,aiText="",archive=emptyList(),offset=0,message="存档已读取。") } }
 fun manualSave()=task { val id=(1..8).firstOrNull { n->store.slots().none { it.id==n } } ?: error("没有空存档槽");store.copyTo(mutable.value.slot,id);mutable.update { currentUi -> currentUi.copy(slots=store.slots(),message="已建立独立快照：槽 $id") } }
 fun page(offset:Int)=task { val p=offset.coerceAtLeast(0);mutable.update { currentUi -> currentUi.copy(archive=store.history(mutable.value.slot,p,5),offset=p) } }
 suspend fun exportDialogues(uri:android.net.Uri)=withContext(Dispatchers.IO){lock.withLock {getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {store.exportDialogues(mutable.value.slot,it)}?:error("文件无法打开")}}
 suspend fun export():String=withContext(Dispatchers.IO) { lock.withLock { store.export(mutable.value.slot) } }
 fun import(text:String)=task { val id=(1..8).firstOrNull { n->store.slots().none { it.id==n } }?:error("没有空槽");store.import(text,id);mutable.update { currentUi -> currentUi.copy(slots=store.slots(),message="导入完成，位于槽 $id。") } }
 fun parse(text:String,ready:(Preview)->Unit) {
  cancelAi();val w=mutable.value.world?:return
  if(!models.isLoaded) {ready(EraRules.parse(text,w));return}
  generation=viewModelScope.launch {
   mutable.update { currentUi -> currentUi.copy(generating=true) }
   try {val result=withContext(Dispatchers.IO){models.interpret(text,w)};if(mutable.value.world?.recent?.lastOrNull()?.revision==w.recent.lastOrNull()?.revision)ready(result)}
   catch(e:CancellationException) { }
   catch(e:Exception) {ready(EraRules.parse(text,w).copy(description="本地 AI 未能解析，已退回规则选项。"))}
   finally {mutable.update { currentUi -> currentUi.copy(generating=false) }}
  }
 }
 fun generate() {
  cancelAi();val w=mutable.value.world?:return;val slot=mutable.value.slot
  generation=viewModelScope.launch {
   mutable.update { currentUi -> currentUi.copy(generating=true) }
   try {
    val text=withContext(Dispatchers.IO) { withTimeout(45000) { models.narrate(MemoryEngine.context(w)) } }
    if(mutable.value.slot==slot && mutable.value.world?.recent?.lastOrNull()?.revision==w.recent.lastOrNull()?.revision) mutable.update { currentUi -> currentUi.copy(aiText=if(w.setting==EraRules.ANCIENT&&listOf("蒸汽","电力","火车","机枪","飞机","铁路").any(text::contains)) "本次演绎超出古代时代范围，已保留规则叙事。" else if(w.journey?.revealed==false && listOf("陛下","皇帝","君主",w.ruler).any {text.contains(it)}) "本次演绎可能泄露隐藏身份，已丢弃。请参考规则叙事。" else text) }
   } catch(e:CancellationException) { /* authoritative turn was already saved */ }
   catch(e:Exception) { message("AI 暂不可用，已保留规则叙事：${e.message}") }
   finally { if(mutable.value.slot==slot) mutable.update { currentUi -> currentUi.copy(generating=false) } }
  }
 }
 fun openChat(id:Int)=task {
  cancelAi();val w=mutable.value.world?:error("请先新建游戏");require(w.people.any {it.id==id})
  chatMutable.value=ChatState(id,store.dialogues(mutable.value.slot,id,100),note=DialogueEngine.access(w,id)?:"对话仅供交流；实际政策须通过行动确认。")
 }
 fun closeChat(){cancelAi();chatMutable.value=ChatState()}
 fun sendChat(text:String,approach:String){
  if(chatMutable.value.working||mutable.value.busy||text.isBlank())return
  cancelAi();val token=++chatEpoch
  chatMutable.value=chatMutable.value.copy(working=true,partial="",note="正在准备对话…")
  chatJob=viewModelScope.launch(Dispatchers.IO){
   var world:World?=null;var person:Person?=null;var slot=0;var previous=emptyList<DialogueRecord>()
   try {
    lock.withLock {
     val w=mutable.value.world?:error("请先新建游戏");val id=chatMutable.value.person?:error("人物已关闭");val p=w.people.first {it.id==id};require(DialogueEngine.access(w,id)==null){DialogueEngine.access(w,id)?:"无法交谈"}
     val clean=text.trim().take(400);val cmd=Command("CONVERSE",target=id.toString(),mode=DialogueEngine.approach(approach,clean),objective=DialogueEngine.topic(clean))
     val out=RealtimeEngine.submit(w,cmd,w.revision)
     slot=mutable.value.slot;previous=store.dialogues(slot,id,6)
     val record=DialogueRecord(id,"user",clean,w.day,"player");store.startDialogue(slot,w.revision,out,record)
     val x=out.world;world=x;person=x.people.first {it.id==p.id}
     mutable.update {it.copy(world=x,message="交谈已保存；无需支付资金或等待冷却。")}
     chatMutable.value=chatMutable.value.copy(records=(chatMutable.value.records+record).takeLast(100),note=if(models.isLoaded)"本地模型生成中…"else"未加载模型，使用规则模板。")
    }
    val w=world!!;val p=person!!
    val reply=if(models.isLoaded)try {
     models.chat(w,p,previous,text){part->if(token==chatEpoch)chatMutable.value=chatMutable.value.copy(partial=part)}
    }catch(e:Exception){currentCoroutineContext().ensureActive();chatMutable.value=chatMutable.value.copy(note="模型未完成：${e.message?.take(120)}，已切换模板。");SpokenReply(DialogueEngine.fallback(w,p,text),"template")}
    else SpokenReply(DialogueEngine.fallback(w,p,text),"template")
    currentCoroutineContext().ensureActive()
    lock.withLock {
     if(token!=chatEpoch||mutable.value.slot!=slot)return@withLock
     val current=mutable.value.world?:return@withLock
     val currentPerson=current.people.find {it.id==p.id}?:return@withLock
     if(!currentPerson.alive){chatMutable.value=chatMutable.value.copy(partial="",note="对方已故，未完成的回复已停止，旧记录仍可查看。");return@withLock}
     val safe=DialogueEngine.sanitize(current,currentPerson,reply.text)?:DialogueEngine.fallback(current,currentPerson,text)
     val record=DialogueRecord(p.id,"assistant",safe,current.day,if(safe==reply.text)reply.source else "template")
     store.appendDialogue(slot,record);chatMutable.value=chatMutable.value.copy(records=(chatMutable.value.records+record).takeLast(100),partial="",note=if(record.source=="model")"本地模型回复 · 不直接改变世界状态"else"规则模板回复 · 可继续游戏")
    }
   }catch(e:CancellationException){}catch(e:Exception){if(token==chatEpoch)chatMutable.value=chatMutable.value.copy(note=e.message?:"对话未完成",partial="")}
   finally {if(token==chatEpoch)chatMutable.value=chatMutable.value.copy(working=false)}
  }
 }
 fun cancelAi() {chatEpoch++;models.cancel();generation?.cancel();chatJob?.cancel();chatMutable.value=chatMutable.value.copy(working=false,partial="");mutable.update {currentUi->currentUi.copy(generating=false)}}
 private fun task(block:suspend ()->Unit) {
  if(mutable.value.busy) return
  mutable.update { currentUi -> currentUi.copy(busy=true) }
  viewModelScope.launch(Dispatchers.IO) { lock.withLock { try { block() } catch(e:Exception) { message(e.message?:"操作失败") } finally { mutable.update { currentUi -> currentUi.copy(busy=false) } } } }
 }
 override fun onCleared() { models.close();store.close() }
}
