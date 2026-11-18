package cn.chronicle.game

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.chronicle.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.URLEncoder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

fun interface NativeTokenSink {fun onProgress(bytes:ByteArray)}
object NativeBridge {
 init { System.loadLibrary("chronicle") }
 external fun load(path:String,threads:Int,context:Int):Boolean
 external fun generate(prompt:ByteArray,tokens:Int,timeout:Int):ByteArray
 external fun formatChat(roles:Array<String>,content:Array<ByteArray>,template:String):ByteArray
 external fun countTokens(prompt:ByteArray):Int
 external fun generateStreaming(prompt:ByteArray,tokens:Int,timeout:Int,sink:NativeTokenSink):ByteArray
 external fun cancel()
 external fun unload()
}
data class HubState(val query:String="",val sort:String="downloads",val ggufOnly:Boolean=true,val repositories:List<HubRepository> = emptyList(),val next:String?=null,val selected:HubRepository?=null,val files:List<HubFile> = emptyList(),val fileNext:String?=null,val busy:Boolean=false,val error:String="")
data class ModelState(val status:String="未加载模型 · 使用规则叙事",val progress:Float=0f,val working:Boolean=false,val loaded:Boolean=false,val exists:Boolean=false,val paused:Boolean=false,val loading:Boolean=false)
class ModelManager(private val app:Context) {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private val gate=Mutex()
 private val pause=AtomicBoolean(false)
 private val root=File(app.filesDir,"models").apply {mkdirs()}
 private val model=File(root,"active.gguf")
 private val partial=File(root,"qwen.part")
 private val prefs=app.getSharedPreferences("inference",0)
 private val mutable=MutableStateFlow(ModelState(exists=model.exists(),paused=partial.exists()))
 val state=mutable.asStateFlow()
 val isLoaded get()=mutable.value.loaded
 /** Reconcile a GGUF added after ViewModel creation without starting inference. */
 fun refreshInstalled() {
  val existsNow=model.isFile
  val pausedNow=partial.isFile
  mutable.value=mutable.value.let {s->
   if(s.exists==existsNow&&s.paused==pausedNow)s
   else s.copy(exists=existsNow,paused=pausedNow)
  }
 }
 @Volatile private var loadingJob:Job?=null
 private val catalogMutable=MutableStateFlow(ModelCatalog.builtIn)
 val catalog=catalogMutable.asStateFlow()
 private val searchMutable=MutableStateFlow("")
 val searchStatus=searchMutable.asStateFlow()
 fun advice():ModelCatalog.Advice {
  val info=ActivityManager.MemoryInfo();(app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
  return ModelCatalog.recommend(info.availMem,info.totalMem,Runtime.getRuntime().availableProcessors(),Build.SUPPORTED_ABIS.any {it=="arm64-v8a"})
 }
 fun deviceText():String {
  val info=ActivityManager.MemoryInfo();(app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
  return "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n总内存 ${info.totalMem/1048576} MiB · 可用 ${info.availMem/1048576} MiB · ${Runtime.getRuntime().availableProcessors()} CPU 线程"
 }
 private val hub=HuggingFaceClient()
 private var hubJob:Job?=null
 private var hubGeneration=0
 private val hubMutable=MutableStateFlow(HubState())
 val hubState=hubMutable.asStateFlow()
 fun search(query:String,sort:String="downloads",ggufOnly:Boolean=true,more:Boolean=false) {
  if(query.isBlank()||query.length>120)return
  val old=hubMutable.value;hub.cancel();hubJob?.cancel();val token=++hubGeneration
  hubMutable.value=if(more)old.copy(busy=true,error="")else HubState(query=query,sort=sort,ggufOnly=ggufOnly,busy=true)
  hubJob=scope.launch {try {
   val page=hub.search(query,sort,ggufOnly,if(more)old.next else null);ensureActive()
   if(token==hubGeneration)hubMutable.value=hubMutable.value.copy(repositories=((if(more)old.repositories else emptyList())+page.items).distinctBy {it.id}.take(120),next=page.next,busy=false)
  }catch(e:CancellationException){}catch(e:Exception){if(token==hubGeneration)hubMutable.value=hubMutable.value.copy(busy=false,error="搜索失败：${e.message}")}}
 }
 fun openRepository(id:String,more:Boolean=false){
  val old=hubMutable.value;hub.cancel();hubJob?.cancel();val token=++hubGeneration;hubMutable.value=old.copy(busy=true,error="",selected=if(more)old.selected else null,files=if(more)old.files else emptyList(),fileNext=if(more)old.fileNext else null)
  hubJob=scope.launch {try {val repo=if(more)old.selected!! else hub.detail(id);val page=hub.files(repo,if(more)old.fileNext else null);ensureActive();if(token==hubGeneration)hubMutable.value=hubMutable.value.copy(selected=repo,files=((if(more)old.files else emptyList())+page.items).distinctBy {it.path}.take(1000),fileNext=page.next,busy=false)}catch(e:CancellationException){}catch(e:Exception){if(token==hubGeneration)hubMutable.value=hubMutable.value.copy(busy=false,error="目录读取失败：${e.message}")}}
 }
 fun backToSearch(){hub.cancel();hubJob?.cancel();hubGeneration++;hubMutable.value=hubMutable.value.copy(selected=null,files=emptyList(),fileNext=null,busy=false)}
 fun cancelSearch(){hub.cancel();hubJob?.cancel();hubGeneration++;hubMutable.value=hubMutable.value.copy(busy=false)}

 var chatTemplate:String get()=prefs.getString("chat-template","auto")?:"auto";set(v){require(v in listOf("auto","chatml","llama3","gemma","zephyr"));prefs.edit().putString("chat-template",v).apply()}
 var threads:Int get()=prefs.getInt("threads",minOf(4,Runtime.getRuntime().availableProcessors()).coerceAtLeast(1)); set(v){prefs.edit().putInt("threads",v.coerceIn(1,8)).apply()}
 var contextLimit:Int get()=prefs.getInt("context",1024); set(v){prefs.edit().putInt("context",v.coerceIn(512,2048)).apply()}
 var autoLoadOnChat:Boolean get()=prefs.getBoolean("auto-load-chat",true);set(value){prefs.edit().putBoolean("auto-load-chat",value).apply()}
 private fun update(s:String,work:Boolean=false,progress:Float=mutable.value.progress) {mutable.value=mutable.value.copy(status=s,working=work,progress=progress,exists=model.exists(),paused=partial.exists())}
 var downloadSource:String get()=prefs.getString("download-source","auto")?:"auto";set(value){require(value in ModelTransfer.sources);prefs.edit().putString("download-source",value).apply()}
 @Volatile var foreground=true
 private var pendingAutoLoad=false
 init {scope.launch {ModelTransfer.state.collect {transfer->if(transfer.serial!=0L){mutable.value=mutable.value.copy(status=transfer.message,working=transfer.working,progress=transfer.progress,exists=model.exists(),paused=partial.exists());if(transfer.completed&&transfer.autoLoad&&!isLoaded){pendingAutoLoad=true;if(foreground){pendingAutoLoad=false;load()}}}}}}
 fun resumed(){foreground=true;if(pendingAutoLoad){pendingAutoLoad=false;load()}}
 fun download(spec:ModelSpec=ModelCatalog.builtIn.first(),autoLoad:Boolean=true){if(mutable.value.working||isLoaded||ModelTransfer.state.value.working)return;runCatching {ModelTransfer.start(app,spec,downloadSource,autoLoad)}.onFailure {update("下载未启动：${it.message}")}}
 fun pauseDownload(){ModelTransfer.pause()}
 fun import(uri:Uri) {
  if(mutable.value.working||isLoaded||ModelTransfer.state.value.working) return
  update("正在导入并校验 GGUF",true)
  scope.launch {try {gate.withLock {
   val temp=File(root,"import.part")
   try {
    app.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use {out->val buffer=ByteArray(65536);var total=0L;while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=MAX_SIZE){"模型超过 8 GiB 上限"};out.write(buffer,0,n)}} }?:error("无法打开所选文件")
    checkHeader(temp);val digest=hash(temp);require(temp.renameTo(model)){"无法保存模型"};prefs.edit().putString("sha",digest).putString("active","本地导入模型").apply();update("导入完成；兼容性以实际加载为准")
   } finally {temp.delete()}
  }} catch(e:Exception){update("导入失败：${e.message}")}}
 }
 fun load() {
  if(mutable.value.working||isLoaded||ModelTransfer.state.value.working) return
  update("校验文件并加载模型…",true)
  scope.launch {try {loadInstalled()}catch(_:CancellationException){}catch(_:Exception){/* The shared loader retains the actionable error in state. */}}
 }
 suspend fun ensureLoadedForChat() {
  if(isLoaded)return
  check(autoLoadOnChat){"已关闭发送时自动加载；可在模型设置开启，或手动加载"}
  check(!mutable.value.working||mutable.value.loading){"模型正在导入、下载或准备加载，请完成后再发送"}
  loadInstalled()
 }
 private suspend fun loadInstalled()=gate.withLock {
  currentCoroutineContext().ensureActive()
  if(isLoaded)return@withLock
  check(!ModelTransfer.state.value.working){"模型仍在下载，请完成安装后再发送"}
  val caller=currentCoroutineContext()
  var nativeStarted=false
  loadingJob=caller[Job]
  mutable.value=mutable.value.copy(loading=true)
  update("正在校验已安装模型…",true)
  try {
   require(model.isFile){"尚未安装模型，请打开模型设置下载或导入 GGUF"}
   checkHeader(model);require(hash(model){caller.ensureActive()}==prefs.getString("sha","")){"模型已被修改或损坏，拒绝加载；请重新下载或导入"}
   val info=ActivityManager.MemoryInfo();(app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
   require(!info.lowMemory && info.availMem>model.length()+350L*1024*1024){"可用内存不足，继续使用规则模式"}
   val ctx=minOf(contextLimit,if(info.availMem<2L*1024*1024*1024) 768 else 2048)
   caller.ensureActive();update("正在自动加载本地模型…",true)
   val start=System.nanoTime()
   // JNI can block while mmap/context allocation runs. Keep cancellation asserted even
   // if it arrives just before native load resets its stop flag.
   val watcher=scope.launch(Dispatchers.Default){while(isActive){if(!caller.isActive)runCatching{NativeBridge.cancel()};delay(25)}}
   try {
    caller.ensureActive();nativeStarted=true
    require(NativeBridge.load(model.path,threads.coerceAtMost(Runtime.getRuntime().availableProcessors().coerceAtLeast(1)),ctx)){"模型架构不兼容或加载失败"}
    caller.ensureActive()
   }finally {watcher.cancel()}
   activeContext=ctx;mutable.value=mutable.value.copy(loaded=true);update("已加载 · 上下文 $ctx · ${"%.1f".format((System.nanoTime()-start)/1e9)} 秒")
  }catch(e:Throwable){
   if(nativeStarted)runCatching{NativeBridge.unload()}
   mutable.value=mutable.value.copy(loaded=false)
   update(if(e is CancellationException)"加载已取消；模型文件与存档保留"else"加载失败：${e.message}；规则模式仍可游玩")
   throw e
  }finally {loadingJob=null;mutable.value=mutable.value.copy(loading=false,working=false)}
 }
 private var activeContext=1024
 fun pendingDownload():ModelSpec?=runCatching {SaveJson.decodeFromString<ModelSpec>(prefs.getString("pending-spec","")!!).also {it.verify()} }.getOrNull()
 private fun format(messages:List<Pair<String,String>>)=NativeBridge.formatChat(messages.map {it.first}.toTypedArray(),messages.map {it.second.toByteArray()}.toTypedArray(),chatTemplate)
 private fun fits(prompt:ByteArray,tokens:Int)=NativeBridge.countTokens(prompt)+tokens+8<=activeContext
 suspend fun chat(w:World,p:Person,history:List<DialogueRecord>,text:String,progress:(String)->Unit):SpokenReply=gate.withLock {
  require(isLoaded)
  val system=DialogueEngine.context(w,p,true)
  var messages=listOf("system" to system)+history.takeLast(4).map {it.role to DialogueEngine.safeText(it.text).take(300)}+("user" to DialogueEngine.safeText(text).take(400))
  var prompt=format(messages)
  while(!fits(prompt,96)&&messages.size>2){messages=listOf(messages.first())+messages.drop(if(messages.size>=4)3 else 2);prompt=format(messages)}
  require(fits(prompt,96)){"对话超出上下文预算，请缩短消息或提高上下文上限。"}
  val coroutine=currentCoroutineContext();coroutine.ensureActive()
  val raw=NativeBridge.generateStreaming(prompt,96,180000,NativeTokenSink {bytes->
   coroutine.ensureActive()
   val partial=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.IGNORE).onUnmappableCharacter(java.nio.charset.CodingErrorAction.IGNORE).decode(ByteBuffer.wrap(bytes)).toString()
   // Never stream an unfiltered royal identity or fabricated judgment to the player.
   DialogueEngine.sanitize(w,p,partial)?.let(progress)
  }).toString(Charsets.UTF_8)
  val safe=DialogueEngine.sanitize(w,p,raw)?:error("模型回复超出角色可知信息，已回退模板。")
  SpokenReply(safe,"model")
 }
 suspend fun narrate(context:String):String=gate.withLock {
  require(isLoaded)
  var visible=context.take(600)
  var prompt=format(listOf("system" to "用中文写80字以内历史叙述，只描述给定可见事实，不更改数值或编造证据。","user" to visible))
  while(!fits(prompt,160)&&visible.length>80){visible=visible.take(visible.length*3/4);prompt=format(listOf("system" to "用中文写80字以内历史叙述，只描述给定可见事实，不更改数值或编造证据。","user" to visible))}
  val result=NativeBridge.generate(prompt,160,40000).toString(Charsets.UTF_8).trim();require(result.isNotBlank());result.take(1400)
 }
 suspend fun interpret(text:String,w:World):Preview=gate.withLock {
  if(text.contains("运河"))return@withLock EraRules.parse(text,w)
  require(isLoaded)
  val instruction="把命令映射为JSON：{\"commands\":[{\"type\":\"FARM\",\"region\":0}]}。只输出JSON。时代${w.setting}。古代用WORKSHOP禁FACTORY，工业用FACTORY禁WORKSHOP。type仅WORKSHOP,FACTORY,FARM,RESEARCH,ROAD,TAX_DOWN,TAX_UP,DIPLOMACY,DRILL,RELIEF,REPAY,REFORM,REST。region 0至3，最多3项。不理解输出{\"commands\":[]}，不承诺成功。"
  val prompt=format(listOf("system" to instruction,"user" to DialogueEngine.safeText(text).take(240)))
  require(fits(prompt,160)){"命令超过上下文预算"}
  var last:Exception?=null
  repeat(2){try{return@withLock AiValidation.parse(NativeBridge.generate(prompt,160,18000).toString(Charsets.UTF_8),w)}catch(e:Exception){last=e}}
  EraRules.parse(text,w).let {it.copy(description="模型输出未通过校验（${last?.message?.take(70)}），已退回规则解析。\n${it.description}",uncertain=true)}
 }
 fun cancel() {loadingJob?.cancel();if(isLoaded || mutable.value.working) runCatching {NativeBridge.cancel()}}
 fun unloadAsync() {cancel();scope.launch {gate.withLock {runCatching {NativeBridge.unload()};mutable.value=mutable.value.copy(loaded=false);update("模型已卸载，内存已释放")}}}
 fun delete() {if(mutable.value.working)return;cancel();scope.launch {gate.withLock {runCatching{NativeBridge.unload()};model.delete();partial.delete();prefs.edit().remove("sha").apply();mutable.value=ModelState();update("已移除本地模型文件")}}}
 fun close() {cancelSearch();pause.set(true);cancel();scope.launch {gate.withLock {runCatching {NativeBridge.unload()}};scope.cancel()}}
 companion object {
  const val URL_MODEL="https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/9217f5db79a29953eb74d5343926648285ec7e67/qwen2.5-0.5b-instruct-q4_k_m.gguf"
  const val SHA="74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
  const val SIZE=491400032L
  const val MAX_SIZE=8589934592L
  fun checkHeader(f:File) {
   require(f.exists() && f.length() in 24..MAX_SIZE){"模型不存在或大小不受支持"}
   RandomAccessFile(f,"r").use {r->val header=ByteArray(24);r.readFully(header);val b=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);require(b.int==0x46554747){"不是 GGUF 文件"};require(b.int in 2..3){"不支持的 GGUF 版本"};require(b.long in 1..10000 && b.long in 1..10000){"GGUF 元数据无效"}}
  }
  fun hash(file:File,checkpoint:()->Unit={}):String {val digest=MessageDigest.getInstance("SHA-256");file.inputStream().use {input->val buffer=ByteArray(65536);while(true){checkpoint();val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}};return digest.digest().joinToString(""){"%02x".format(it)}}
 }
}
@Composable fun ModelPanel(vm:GameViewModel,compact:Boolean=false) {
 val manager=vm.models;val state by manager.state.collectAsState();val hub by manager.hubState.collectAsState()
 var open by rememberSaveable {mutableStateOf(false)};var section by rememberSaveable {mutableIntStateOf(0)}
 var query by rememberSaveable {mutableStateOf("")};var sort by rememberSaveable {mutableStateOf("downloads")};var gguf by rememberSaveable {mutableStateOf(true)}
 var install by remember {mutableStateOf<ModelSpec?>(null)};var accepted by remember {mutableStateOf(false)};var autoLoad by rememberSaveable {mutableStateOf(true)}
 var source by rememberSaveable {mutableStateOf(manager.downloadSource)}
 var autoChat by rememberSaveable {mutableStateOf(manager.autoLoadOnChat)}
 var advice by remember {mutableStateOf(manager.advice())};var page by rememberSaveable {mutableIntStateOf(0)}
 var threads by rememberSaveable {mutableFloatStateOf(manager.threads.toFloat())};var context by rememberSaveable {mutableFloatStateOf(manager.contextLimit.toFloat())}
 val ctx=androidx.compose.ui.platform.LocalContext.current
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)manager.import(uri)}
 fun propose(spec:ModelSpec){accepted=false;install=spec}
 if(compact)TextButton(onClick={open=true},enabled=!state.loading){Text("模型设置")}
 else Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("本地 AI 模型",style=MaterialTheme.typography.titleMedium);Text(state.status,style=MaterialTheme.typography.bodySmall);Button(onClick={open=true},modifier=Modifier.fillMaxWidth()){Text("搜索、下载与管理模型")}}}
 if(open) androidx.compose.ui.window.Dialog(onDismissRequest={open=false;manager.cancelSearch()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=true)){
  Surface(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().imePadding().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Row{Text("离线模型中心",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick={open=false;manager.cancelSearch()}){Text("完成")}}
   Text(state.status,style=MaterialTheme.typography.labelSmall,maxLines=3)
   if(state.working){if(state.loading){LinearProgressIndicator(modifier=Modifier.fillMaxWidth());TextButton(onClick={vm.cancelAi();manager.cancel()}){Text("取消加载")}}else{LinearProgressIndicator(progress={state.progress},modifier=Modifier.fillMaxWidth());TextButton(onClick={manager.pauseDownload()}){Text("暂停下载")}}}
   SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){listOf("推荐","模型库","设置").forEachIndexed {i,t->SegmentedButton(selected=section==i,onClick={section=i},shape=SegmentedButtonDefaults.itemShape(i,3)){Text(t)}}}
   Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
    when(section){
     0->{Text(manager.deviceText());Text(advice.text)
      advice.model?.let {spec->Button(onClick={propose(spec)},enabled=!state.working&&!state.loaded){Text("推荐：${spec.label} · ${spec.bytes/1048576} MiB")}}
      TextButton(onClick={advice=manager.advice()}){Text("重新检测手机内存")}
      TextButton(onClick={manager.threads=advice.threads;manager.contextLimit=advice.context;threads=advice.threads.toFloat();context=advice.context.toFloat()}){Text("采用建议线程与上下文")}
      Text("推荐来自内存预算估算，不代表手机性能实测。GGUF 的架构与聊天模板须经实际加载验证。搜索不限制模型家族。",style=MaterialTheme.typography.bodySmall)
      ModelCatalog.builtIn.forEach {spec->OutlinedButton(onClick={propose(spec)},enabled=!state.working&&!state.loaded,modifier=Modifier.fillMaxWidth()){Text("${spec.label} · ${spec.bytes/1048576} MiB")}}
     }
     1->{if(hub.selected==null){
       OutlinedTextField(query,{query=it.take(120)},label={Text("Hugging Face 关键词 / 作者 / 模型名")},singleLine=true,modifier=Modifier.fillMaxWidth())
       Row{Checkbox(gguf,{gguf=it});Text("仅看GGUF",Modifier.padding(top=12.dp));TextButton(onClick={sort=if(sort=="downloads")"lastModified"else"downloads"}){Text(if(sort=="downloads")"按下载量"else"按更新")}}
       Row{Button(onClick={manager.search(query,sort,gguf);page=0},enabled=query.isNotBlank()&&!hub.busy){Text("在线搜索")};if(hub.busy)TextButton(onClick={manager.cancelSearch()}){Text("取消搜索")}}
       if(hub.repositories.isEmpty()&&!hub.busy)Text("直接查询 Hugging Face 公开目录；搜索只发送关键词，不发送人物或存档。")
       hub.repositories.drop(page*4).take(4).forEach {r->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(r.id,style=MaterialTheme.typography.titleSmall);Text("${r.license} · 下载${r.downloads}${if(r.gated)" · 需授权"else""}",style=MaterialTheme.typography.labelSmall);TextButton(onClick={manager.openRepository(r.id);page=0},enabled=!hub.busy){Text("查看GGUF文件")}}}}
       Row{TextButton(onClick={page--},enabled=page>0){Text("上页")};TextButton(onClick={page++},enabled=(page+1)*4<hub.repositories.size){Text("下页")};if(hub.next!=null&&hub.repositories.size<120)TextButton(onClick={manager.search(hub.query,hub.sort,hub.ggufOnly,true)},enabled=!hub.busy){Text("加载更多")}}
      }else{val r=hub.selected!!
       TextButton(onClick={manager.backToSearch();page=0}){Text("返回搜索")};Text(r.id,style=MaterialTheme.typography.titleMedium);Text("许可：${r.license} · 修订${r.revision.take(8)}",style=MaterialTheme.typography.labelSmall)
       if(hub.files.isEmpty()&&!hub.busy)Text("当前目录页未找到GGUF；可以继续加载目录页。Safetensors等文件不能直接安装。")
       hub.files.drop(page*4).take(4).forEach {f->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(f.path);Text("${f.bytes/1048576} MiB · SHA256 ${f.sha256.take(12)}…",style=MaterialTheme.typography.labelSmall)
        val spec=runCatching {HubCatalog.spec(r,f)}.getOrNull();if(spec==null)Text(f.blocked.ifBlank {if(r.gated)"需先在官网授权并手动导入"else"许可或目录信息不足，请核对官网后导入"},style=MaterialTheme.typography.labelSmall)
        Button(onClick={spec?.let(::propose)},enabled=spec!=null&&!state.working&&!state.loaded){Text("下载并安装")}
       }}}
       Row{TextButton(onClick={page--},enabled=page>0){Text("上页")};TextButton(onClick={page++},enabled=(page+1)*4<hub.files.size){Text("下页")};if(hub.fileNext!=null&&hub.files.size<1000)TextButton(onClick={manager.openRepository(r.id,true)},enabled=!hub.busy){Text("加载目录")}}
      }
      if(hub.busy)LinearProgressIndicator(modifier=Modifier.fillMaxWidth());if(hub.error.isNotBlank())Text(hub.error,color=MaterialTheme.colorScheme.error)
      TextButton(onClick={picker.launch(arrayOf("*/*"))},enabled=!state.loaded&&!state.working){Text("从本地导入GGUF")}
      manager.pendingDownload()?.takeIf {state.paused}?.let {spec->TextButton(onClick={manager.download(spec,autoLoad)},enabled=!state.working&&!state.loaded){Text("继续下载：${spec.label}")}}
     }
     else->{Text("推理线程 ${threads.toInt()}");Slider(threads,{threads=it;manager.threads=it.toInt()},valueRange=1f..8f,steps=6)
      Text("上下文 ${context.toInt()} tokens");Slider(context,{context=it;manager.contextLimit=it.toInt()},valueRange=512f..2048f,steps=5)
      Text("聊天模板：${manager.chatTemplate}（重新生成时生效）");listOf("auto","chatml","llama3","gemma","zephyr").forEach {t->TextButton(onClick={manager.chatTemplate=t;vm.message("已选聊天模板：$t")}){Text(if(t=="auto")"自动读取GGUF模板（推荐）"else t)}}
      Row{Checkbox(autoLoad,{autoLoad=it});Text("安装后尝试加载",Modifier.padding(top=12.dp))}
      Row{Checkbox(autoChat,{autoChat=it;manager.autoLoadOnChat=it});Text("发送消息时自动加载已安装模型",Modifier.padding(top=12.dp))}
      Text("线程与上下文在下次加载生效。内存不足或模型不兼容时保留规则模式。人物对话使用模型自带聊天模板。",style=MaterialTheme.typography.bodySmall)
     }
    }
   }
   Row{TextButton(onClick={manager.load()},enabled=state.exists&&!state.working&&!state.loaded){Text("加载已安装")};TextButton(onClick={vm.cancelAi();manager.unloadAsync()},enabled=state.loaded){Text("卸载内存")};TextButton(onClick={manager.delete()},enabled=!state.working){Text("移除文件")}}
  }}
 }
 install?.let {spec->AlertDialog(onDismissRequest={install=null},title={Text("安装模型")},text={Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text("${spec.label}\n${spec.bytes/1048576} MiB · 许可 ${spec.license}\n安装后校验 SHA-256，实际兼容性以加载为准。仅替换模型文件，不更改存档。")
  Text("下载源：切换镜像仍使用清单中的固定SHA-256，校验失败不安装。ModelScope仅映射官方Qwen；其他仓库可用HF Mirror或原站。",style=MaterialTheme.typography.bodySmall)
  ModelTransfer.sources.forEach {(id,label)->FilterChip(selected=source==id,onClick={source=id;manager.downloadSource=id},label={Text(label)})}
  TextButton(onClick={ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,Uri.parse("https://huggingface.co/${spec.repo}")))}){Text("查看仓库与许可")}
  Row{Checkbox(accepted,{accepted=it});Text("我已核对许可并同意下载",Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodySmall)}
 }},confirmButton={TextButton(onClick={manager.download(spec,autoLoad);install=null},enabled=accepted){Text("下载并安装")}},dismissButton={TextButton(onClick={install=null}){Text("取消")}})}
}
