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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
 fun search(query:String) {
  if(query.length !in 2..80 || searchMutable.value=="正在搜索官方与社区目录…") return
  searchMutable.value="正在搜索官方与社区目录…"
  scope.launch {
   try {
    val repos=jsonGet("https://huggingface.co/api/models?search=${URLEncoder.encode(query,"UTF-8")}&filter=gguf&limit=5").jsonArray
    val found=mutableListOf<ModelSpec>()
    repos.forEach { row ->
     val repo=row.jsonObject["id"]?.jsonPrimitive?.content?:return@forEach
     if(!repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) || !repo.contains("qwen2.5",true) || repo.contains("-vl",true)) return@forEach
     runCatching {
      val detail=jsonGet("https://huggingface.co/api/models/$repo?blobs=true").jsonObject
      val license=detail["cardData"]?.jsonObject?.get("license")?.jsonPrimitive?.content?:return@runCatching
      if(license !in listOf("apache-2.0","mit")) return@runCatching
      val revision=detail["sha"]!!.jsonPrimitive.content
      detail["siblings"]!!.jsonArray.forEach { fileRow ->
       val f=fileRow.jsonObject;val name=f["rfilename"]!!.jsonPrimitive.content
       if(!name.endsWith(".gguf") || !listOf("q3_k_m","q4_k_m","q5_k_m").any {name.contains(it,true)}) return@forEach
       val spec=ModelSpec(repo,revision,name,f["size"]!!.jsonPrimitive.long,f["lfs"]!!.jsonObject["sha256"]!!.jsonPrimitive.content,license,"${repo.substringAfter('/')} · ${name.substringAfterLast('-')}")
       runCatching {spec.verify();found+=spec}
      }
     }
    }
    catalogMutable.value=(ModelCatalog.builtIn+found).distinctBy {it.id}.take(18)
    searchMutable.value="找到 ${found.size} 个满足许可、大小与校验条件的候选。本版在线发现限 Qwen2.5 文本 GGUF；社区文件兼容性须实际加载验证。"
   } catch(e:Exception) {searchMutable.value="在线搜索失败：${e.message}。仍可使用内置目录。"}
  }
 }
 private fun jsonGet(url:String):JsonElement {
  val c=URL(url).openConnection() as HttpURLConnection;c.connectTimeout=15000;c.readTimeout=15000
  try {require(c.responseCode==200){"HTTP ${c.responseCode}"};return c.inputStream.use {input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=2*1024*1024){"目录过大"};out.write(buffer,0,n)};Json.parseToJsonElement(out.toString("UTF-8"))}}finally{c.disconnect()}
 }

 var threads:Int get()=prefs.getInt("threads",minOf(4,Runtime.getRuntime().availableProcessors()).coerceAtLeast(1)); set(v){prefs.edit().putInt("threads",v.coerceIn(1,8)).apply()}
 var contextLimit:Int get()=prefs.getInt("context",1024); set(v){prefs.edit().putInt("context",v.coerceIn(512,2048)).apply()}
 private fun update(s:String,work:Boolean=false,progress:Float=mutable.value.progress) {mutable.value=mutable.value.copy(status=s,working=work,progress=progress,exists=model.exists(),paused=partial.exists())}
 fun download(spec:ModelSpec=ModelCatalog.builtIn.first(),autoLoad:Boolean=true) {
  if(mutable.value.working || isLoaded) return
  spec.verify();pause.set(false);update("正在连接模型库：${spec.label}",true)
  scope.launch {
   try { gate.withLock {
    if(prefs.getString("pending","")!=spec.id) {partial.delete();prefs.edit().putString("pending",spec.id).apply()}
    require(root.usableSpace>spec.bytes-(partial.takeIf {it.exists()}?.length()?:0L)+50L*1048576){"存储空间不足，需保留完整模型和校验空间"}
    val start=partial.takeIf {it.exists()}?.length()?:0L
    val connection=URL(spec.url).openConnection() as HttpURLConnection
    connection.connectTimeout=20000;connection.readTimeout=20000
    if(start>0) connection.setRequestProperty("Range","bytes=$start-")
    try {
     val code=connection.responseCode;require(code==200||code==206){"下载服务器返回 $code"}
     var offset=if(code==206) start else 0L
     if(code==206) require(connection.getHeaderField("Content-Range")?.startsWith("bytes $start-")==true){"续传范围不匹配"}
     RandomAccessFile(partial,"rw").use { out ->
      if(offset==0L) out.setLength(0);out.seek(offset)
      connection.inputStream.use { input -> val buffer=ByteArray(65536);var tick=0
       while(!pause.get()) {val n=input.read(buffer);if(n<0)break;offset+=n;require(offset<=spec.bytes){"下载文件超出目录大小"};out.write(buffer,0,n);if(tick++%8==0) update("下载 ${offset/1048576} / ${spec.bytes/1048576} MiB",true,offset.toFloat()/spec.bytes)}
      }
     }
     if(pause.get()) {update("已暂停，可继续下载");return@withLock}
     require(offset==spec.bytes){"下载未完整，请重试续传"};update("正在校验 SHA-256",true)
     require(hash(partial)==spec.sha256){partial.delete();"模型校验失败，已移除损坏下载"}
     checkHeader(partial);require(partial.renameTo(model)){"无法保存模型"};prefs.edit().putString("sha",spec.sha256).putString("active",spec.label).apply();update("已安装 ${spec.label}",false,1f);if(autoLoad) load()
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
 val manager=vm.models;val state by manager.state.collectAsState();val catalog by manager.catalog.collectAsState();val searchStatus by manager.searchStatus.collectAsState()
 var open by rememberSaveable {mutableStateOf(false)};var section by rememberSaveable {mutableIntStateOf(0)}
 var query by rememberSaveable {mutableStateOf("")};var page by rememberSaveable {mutableIntStateOf(0)}
 var selectedId by rememberSaveable {mutableStateOf(ModelCatalog.builtIn.first().id)}
 var install by remember {mutableStateOf<ModelSpec?>(null)};var autoLoad by rememberSaveable {mutableStateOf(true)}
 var advice by remember {mutableStateOf(manager.advice())};var profile by remember {mutableStateOf(manager.deviceText())}
 var threads by rememberSaveable {mutableFloatStateOf(manager.threads.toFloat())};var context by rememberSaveable {mutableFloatStateOf(manager.contextLimit.toFloat())}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)manager.import(uri)}
 Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("本地 AI 模型",style=MaterialTheme.typography.titleMedium);Text(state.status,style=MaterialTheme.typography.bodySmall)
  Button(onClick={open=true},modifier=Modifier.fillMaxWidth()){Text("搜索、下载与管理模型")}
 }}
 if(open) Dialog(onDismissRequest={open=false},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
  Surface(modifier=Modifier.fillMaxWidth().fillMaxHeight(.95f).padding(12.dp),shape=RoundedCornerShape(20.dp)) {
   Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
    Row {Text("离线模型中心",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick={open=false}){Text("完成")}}
    Text(state.status,style=MaterialTheme.typography.bodySmall)
    if(state.working) {LinearProgressIndicator(progress={state.progress},modifier=Modifier.fillMaxWidth());TextButton(onClick={manager.pauseDownload()}){Text("暂停下载")}}
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {listOf("推荐","模型库","设置").forEachIndexed {i,t->SegmentedButton(selected=section==i,onClick={section=i},shape=SegmentedButtonDefaults.itemShape(i,3)){Text(t)}}}
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
     when(section) {
      0 -> {
       Text(profile,style=MaterialTheme.typography.bodyMedium);Text(advice.text,style=MaterialTheme.typography.bodyMedium)
       Text("推荐是内存预算估算，尚非 vivo 真机性能认证；模型许可、下载完整性与加载兼容性分别检查。",style=MaterialTheme.typography.bodySmall)
       advice.model?.let {spec->Button(onClick={selectedId=spec.id;install=spec},enabled=!state.working&&!state.loaded,modifier=Modifier.fillMaxWidth()){Text("安装推荐模型 · ${spec.bytes/1048576} MiB")}}
       OutlinedButton(onClick={advice=manager.advice();profile=manager.deviceText()},modifier=Modifier.fillMaxWidth()){Text("重新检测手机")}
       OutlinedButton(onClick={manager.threads=advice.threads;manager.contextLimit=advice.context;threads=advice.threads.toFloat();context=advice.context.toFloat()},modifier=Modifier.fillMaxWidth()){Text("应用建议：${advice.threads}线程 / ${advice.context}上下文")}
       Text("没有模型也能完整结算游戏。下载仅获取模型文件，不上传游戏存档或剧情。",style=MaterialTheme.typography.bodySmall)
      }
      1 -> {
       OutlinedTextField(value=query,onValueChange={query=it.take(80);page=0},label={Text("搜索模型，例如 Qwen2.5 0.5B")},singleLine=true,modifier=Modifier.fillMaxWidth())
       Row {TextButton(onClick={manager.search(query);page=0},enabled=query.length>=2){Text("在线搜索")};TextButton(onClick={query="";page=0}){Text("显示全部")}}
       if(searchStatus.isNotBlank())Text(searchStatus,style=MaterialTheme.typography.bodySmall)
       val filtered=catalog.filter {spec->query.isBlank() || query.split(' ').filter {it.isNotBlank()}.all {part->(spec.label+spec.repo+spec.file+" 中文 GGUF").contains(part,true)}}
       val shown=filtered.drop(page*2).take(2)
       if(shown.isEmpty())Text("未找到匹配候选。试试 Qwen2.5，或从本地导入。")
       shown.forEach {spec->OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(spec.label,style=MaterialTheme.typography.titleSmall)
        Text("${spec.bytes/1048576} MiB · ${spec.license}\n${spec.repo}",style=MaterialTheme.typography.bodySmall)
        Text("GGUF 候选 · 安装后仍须加载验证",style=MaterialTheme.typography.labelSmall)
        Button(onClick={selectedId=spec.id;install=spec},enabled=!state.working&&!state.loaded,modifier=Modifier.fillMaxWidth()){Text("下载并安装")}
       }}}
       Row {TextButton(onClick={page--},enabled=page>0){Text("上一页")};Text("${page+1} / ${maxOf(1,(filtered.size+1)/2)}");TextButton(onClick={page++},enabled=(page+1)*2<filtered.size){Text("下一页")}}
       TextButton(onClick={picker.launch(arrayOf("*/*"))},enabled=!state.working&&!state.loaded){Text("从手机文件导入 GGUF")}
       if(state.paused) TextButton(onClick={install=catalog.find {it.id==selectedId}?:ModelCatalog.builtIn.first()},enabled=!state.working&&!state.loaded){Text("继续所选模型的下载")}
      }
      else -> {
       Text("线程 ${threads.toInt()} · 重载后生效");Slider(value=threads,onValueChange={threads=it;manager.threads=it.toInt()},valueRange=1f..8f,steps=6)
       Text("上下文 ${context.toInt()} tokens");Slider(value=context,onValueChange={context=it;manager.contextLimit=it.toInt()},valueRange=512f..2048f,steps=5)
       Row {Checkbox(checked=autoLoad,onCheckedChange={autoLoad=it});Text("安装完成后尝试加载")}
       Text("系统会按实际可用内存限制上下文；内存不足时保留规则模式。卸载内存保留文件，移除文件会释放存储空间。",style=MaterialTheme.typography.bodySmall)
      }
     }
    }
    Row {TextButton(onClick={manager.load()},enabled=state.exists&&!state.working&&!state.loaded){Text("加载已安装")};TextButton(onClick={vm.cancelAi();manager.unloadAsync()},enabled=state.loaded){Text("卸载内存")};TextButton(onClick={manager.delete()},enabled=!state.working){Text("移除文件")}}
   }
  }
 }
 install?.let {spec->AlertDialog(onDismissRequest={install=null},title={Text("安装模型")},text={Text("${spec.label}\n下载 ${spec.bytes/1048576} MiB，许可 ${spec.license}。文件经 SHA-256 校验后自动安装到游戏私有目录。\n\n完成前保留已安装文件；切换模型会替换未完成的下载。${if(autoLoad) "完成后尝试加载，内存不足则继续规则模式。" else "安装后可手动加载。"}")},confirmButton={TextButton(onClick={manager.download(spec,autoLoad);install=null}){Text("下载并安装")}},dismissButton={TextButton(onClick={install=null}){Text("取消")}})}
}
