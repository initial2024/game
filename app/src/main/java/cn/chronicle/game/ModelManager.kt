package cn.chronicle.game

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.chronicle.core.*
import kotlinx.coroutines.*
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

object NativeBridge {
 init { System.loadLibrary("chronicle") }
 external fun load(path:String,threads:Int,context:Int):Boolean
 external fun generate(prompt:ByteArray,tokens:Int,timeout:Int):ByteArray
 external fun cancel()
 external fun unload()
}
data class ModelState(val status:String="未加载模型 · 使用规则叙事",val progress:Float=0f,val working:Boolean=false,val loaded:Boolean=false,val exists:Boolean=false,val paused:Boolean=false)
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
 var threads:Int get()=prefs.getInt("threads",minOf(4,Runtime.getRuntime().availableProcessors()).coerceAtLeast(1)); set(v){prefs.edit().putInt("threads",v.coerceIn(1,8)).apply()}
 var contextLimit:Int get()=prefs.getInt("context",1024); set(v){prefs.edit().putInt("context",v.coerceIn(512,2048)).apply()}
 private fun update(s:String,work:Boolean=false,progress:Float=mutable.value.progress) {mutable.value=mutable.value.copy(status=s,working=work,progress=progress,exists=model.exists(),paused=partial.exists())}
 fun download() {
  if(mutable.value.working || isLoaded) return
  pause.set(false);update("正在连接官方模型库",true)
  scope.launch {
   try { gate.withLock {
    val start=partial.takeIf {it.exists()}?.length()?:0L
    val connection=URL(URL_MODEL).openConnection() as HttpURLConnection
    connection.connectTimeout=20000;connection.readTimeout=20000
    if(start>0) connection.setRequestProperty("Range","bytes=$start-")
    try {
     val code=connection.responseCode;require(code==200||code==206){"下载服务器返回 $code"}
     var offset=if(code==206) start else 0L
     if(code==206) require(connection.getHeaderField("Content-Range")?.startsWith("bytes $start-")==true){"续传范围不匹配"}
     RandomAccessFile(partial,"rw").use { out ->
      if(offset==0L) out.setLength(0);out.seek(offset)
      connection.inputStream.use { input -> val buffer=ByteArray(65536);var tick=0
       while(!pause.get()) {val n=input.read(buffer);if(n<0)break;offset+=n;require(offset<=SIZE){"下载文件超出目录大小"};out.write(buffer,0,n);if(tick++%8==0) update("下载 ${offset/1048576} / ${SIZE/1048576} MiB",true,offset.toFloat()/SIZE)}
      }
     }
     if(pause.get()) {update("已暂停，可继续下载");return@withLock}
     require(offset==SIZE){"下载未完整，请重试续传"};update("正在校验 SHA-256",true)
     require(hash(partial)==SHA){partial.delete();"模型校验失败，已移除损坏下载"}
     checkHeader(partial);require(partial.renameTo(model)){"无法保存模型"};prefs.edit().putString("sha",SHA).apply();update("模型下载并校验完成",false,1f)
    } finally {connection.disconnect()}
   } } catch(e:Exception) {update("下载失败：${e.message}；可重试")}
  }
 }
 fun pauseDownload() {pause.set(true)}
 fun import(uri:Uri) {
  if(mutable.value.working||isLoaded) return
  update("正在导入并校验 GGUF",true)
  scope.launch {try {gate.withLock {
   val temp=File(root,"import.part")
   try {
    app.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use {out->val buffer=ByteArray(65536);var total=0L;while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=MAX_SIZE){"模型超过 1.5 GiB 上限"};out.write(buffer,0,n)}} }?:error("无法打开所选文件")
    checkHeader(temp);val digest=hash(temp);require(temp.renameTo(model)){"无法保存模型"};prefs.edit().putString("sha",digest).apply();update("导入完成；兼容性以实际加载为准")
   } finally {temp.delete()}
  }} catch(e:Exception){update("导入失败：${e.message}")}}
 }
 fun load() {
  if(mutable.value.working||isLoaded) return
  update("校验文件并加载模型…",true)
  scope.launch {try {gate.withLock {
   checkHeader(model);require(hash(model)==prefs.getString("sha","")){"模型已被修改或损坏，拒绝加载"}
   val info=ActivityManager.MemoryInfo();(app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
   require(!info.lowMemory && info.availMem>model.length()+350L*1024*1024){"可用内存不足，继续使用规则模式"}
   val ctx=minOf(contextLimit,if(info.availMem<2L*1024*1024*1024) 768 else 2048)
   val start=System.nanoTime();require(NativeBridge.load(model.path,threads.coerceAtMost(Runtime.getRuntime().availableProcessors().coerceAtLeast(1)),ctx))
   activeContext=ctx;mutable.value=mutable.value.copy(loaded=true);update("已加载 · 上下文 $ctx · ${"%.1f".format((System.nanoTime()-start)/1e9)} 秒")
  }} catch(e:Throwable){mutable.value=mutable.value.copy(loaded=false);update("加载失败：${e.message}；规则模式仍可游玩")}}
 }
 private var activeContext=1024
 suspend fun narrate(context:String):String=gate.withLock {
  require(isLoaded)
  val visible=context.take(((activeContext-200)*.55).toInt())
  val prompt="<|im_start|>system\n用中文写一段80字以内的历史叙述。只描述给定的可见事实。不要更改数值、编造证据或输出建议。<|im_end|>\n<|im_start|>user\n$visible<|im_end|>\n<|im_start|>assistant\n"
  val result=NativeBridge.generate(prompt.toByteArray(),160,40000).toString(Charsets.UTF_8).trim()
  require(result.isNotBlank()) {"模型返回空文本"};result.take(1400)
 }
 suspend fun interpret(text:String,w:World):Preview=gate.withLock {
  require(isLoaded)
  val prompt="<|im_start|>system\n把玩家命令映射到政策。只输出JSON，不要Markdown。格式：{\"commands\":[{\"type\":\"FARM\",\"region\":0}]}。type只允许FACTORY,FARM,RESEARCH,ROAD,TAX_DOWN,TAX_UP,DIPLOMACY,DRILL,RELIEF,REPAY,REFORM,REST。region为0至3。最多3项。不理解就输出{\"commands\":[]}。不要承诺成功，不要凭空加资源。<|im_end|>\n<|im_start|>user\n${text.take(400)}<|im_end|>\n<|im_start|>assistant\n"
  var last:Exception?=null
  repeat(2) {
   try {val output=NativeBridge.generate(prompt.toByteArray(),160,18000).toString(Charsets.UTF_8);return@withLock AiValidation.parse(output,w)}catch(e:Exception){last=e}
  }
  val fallback=IntentParser.parse(text);fallback.copy(description="模型输出未通过严格校验（${last?.message?.take(70)}），已退回规则解析。\n${fallback.description}",uncertain=true)
 }
 fun cancel() {if(isLoaded || mutable.value.working) runCatching {NativeBridge.cancel()}}
 fun unloadAsync() {cancel();scope.launch {gate.withLock {runCatching {NativeBridge.unload()};mutable.value=mutable.value.copy(loaded=false);update("模型已卸载，内存已释放")}}}
 fun delete() {if(mutable.value.working)return;cancel();scope.launch {gate.withLock {runCatching{NativeBridge.unload()};model.delete();partial.delete();prefs.edit().remove("sha").apply();mutable.value=ModelState();update("已移除本地模型文件")}}}
 fun close() {pause.set(true);cancel();scope.launch {gate.withLock {runCatching {NativeBridge.unload()}};scope.cancel()}}
 companion object {
  const val URL_MODEL="https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/9217f5db79a29953eb74d5343926648285ec7e67/qwen2.5-0.5b-instruct-q4_k_m.gguf"
  const val SHA="74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
  const val SIZE=491400032L
  const val MAX_SIZE=1610612736L
  fun checkHeader(f:File) {
   require(f.exists() && f.length() in 24..MAX_SIZE){"模型不存在或大小不受支持"}
   RandomAccessFile(f,"r").use {r->val header=ByteArray(24);r.readFully(header);val b=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);require(b.int==0x46554747){"不是 GGUF 文件"};require(b.int in 2..3){"不支持的 GGUF 版本"};require(b.long in 1..10000 && b.long in 1..10000){"GGUF 元数据无效"}}
  }
  fun hash(file:File):String {val digest=MessageDigest.getInstance("SHA-256");file.inputStream().use {input->val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}};return digest.digest().joinToString(""){"%02x".format(it)}}
 }
}
@Composable fun ModelPanel(vm:GameViewModel) {
 val manager=vm.models;val state by manager.state.collectAsState();var open by rememberSaveable {mutableStateOf(false)};var threads by rememberSaveable {mutableFloatStateOf(manager.threads.toFloat())};var context by rememberSaveable {mutableFloatStateOf(manager.contextLimit.toFloat())}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)manager.import(uri)}
 Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Text("本地 AI 模型",style=MaterialTheme.typography.titleMedium);Text(state.status,style=MaterialTheme.typography.bodySmall)
  TextButton(onClick={open=!open}){Text(if(open)"收起管理" else "模型与推理设置")}
  if(open) {
   Text("Qwen2.5 0.5B Instruct · Q4_K_M\n469 MiB · Apache 2.0 · 官方 GGUF",style=MaterialTheme.typography.bodyMedium)
   Text("模型与 APK 分离，需自行下载。此版本尚未完成 vivo ARM64 性能验收；中文生成质量依场景而异。",style=MaterialTheme.typography.bodySmall)
   if(state.working) {LinearProgressIndicator(progress={state.progress},modifier=Modifier.fillMaxWidth());TextButton(onClick={manager.pauseDownload()}){Text("暂停下载")}}
   Row {TextButton(onClick={manager.download()},enabled=!state.working&&!state.loaded){Text(if(state.paused)"继续 / 重试下载" else "下载模型")};TextButton(onClick={picker.launch(arrayOf("*/*"))},enabled=!state.working&&!state.loaded){Text("本地导入")}}
   Row {TextButton(onClick={manager.load()},enabled=state.exists&&!state.working&&!state.loaded){Text("加载")};TextButton(onClick={vm.cancelAi();manager.unloadAsync()},enabled=state.loaded){Text("卸载内存")};TextButton(onClick={manager.delete()},enabled=!state.working){Text("移除文件")}}
   Text("线程 ${threads.toInt()} · 修改后重新加载生效");Slider(value=threads,onValueChange={threads=it;manager.threads=it.toInt()},valueRange=1f..8f,steps=6)
   Text("上下文 ${context.toInt()} tokens");Slider(value=context,onValueChange={context=it;manager.contextLimit=it.toInt()},valueRange=512f..2048f,steps=5)
   Text("默认不上载游戏内容。文件校验失败、内存不足或推理超时会保留规则模式。",style=MaterialTheme.typography.bodySmall)
  }
 }}
}
