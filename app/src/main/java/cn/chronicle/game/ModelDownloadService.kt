package cn.chronicle.game

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cn.chronicle.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

object ModelTransfer {
 data class State(val serial:Long=0,val message:String="",val progress:Float=0f,val working:Boolean=false,val paused:Boolean=false,val completed:Boolean=false,val autoLoad:Boolean=false)
 val state=MutableStateFlow(State())
 val paused=AtomicBoolean(false)
 @Volatile var connection:HttpURLConnection?=null
 val sources=linkedMapOf("auto" to "自动择源（镜像优先）","modelscope" to "ModelScope（官方Qwen）","hf-mirror" to "HF Mirror","huggingface" to "Hugging Face原站")
 fun urls(spec:ModelSpec,source:String):List<String>{
  val mirror=spec.url.replace("https://huggingface.co/","https://hf-mirror.com/")
  val scope=if(spec.repo=="Qwen/Qwen2.5-0.5B-Instruct-GGUF"||spec.repo=="Qwen/Qwen2.5-1.5B-Instruct-GGUF")"https://modelscope.cn/models/${spec.repo}/resolve/master/${spec.file}" else null
  return when(source){"modelscope"->listOfNotNull(scope);"hf-mirror"->listOf(mirror);"huggingface"->listOf(spec.url);else->listOfNotNull(scope,mirror,spec.url)}
 }
 fun start(context:Context,spec:ModelSpec,source:String,autoLoad:Boolean){
  spec.verify();require(source in sources);require(urls(spec,source).isNotEmpty()){"该仓库未映射到ModelScope，请选HF Mirror或原站"}
  if(state.value.working)return
  paused.set(false);state.value=State(serial=System.currentTimeMillis(),message="正在启动后台模型传输",working=true,autoLoad=autoLoad)
  val intent=Intent(context,ModelDownloadService::class.java).putExtra("spec",SaveJson.encodeToString(spec)).putExtra("source",source).putExtra("autoLoad",autoLoad)
  try{if(Build.VERSION.SDK_INT>=26)context.startForegroundService(intent)else context.startService(intent)}catch(e:Exception){state.value=state.value.copy(working=false,message="无法启动下载：${e.message}；可返回前台重试")}
 }
 fun pause(){paused.set(true);connection?.disconnect()}
}
class ModelDownloadService:Service(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private var task:Job?=null
 private val channel="chronicle-model-transfer"
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,"模型文件传输",NotificationManager.IMPORTANCE_LOW))}
 private fun notification(text:String,progress:Float,working:Boolean):android.app.Notification {
  val open=PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val pause=PendingIntent.getService(this,2,Intent(this,ModelDownloadService::class.java).setAction("PAUSE"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  return NotificationCompat.Builder(this,channel).setSmallIcon(cn.chronicle.game.R.drawable.ic_shanhe).setContentTitle("山河纪年 · 模型下载").setContentText(text).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(working).setProgress(100,(progress*100).toInt(),working&&progress<=0).apply {if(working)addAction(0,"暂停",pause)}.build()
 }
 private fun update(message:String,working:Boolean=true,progress:Float=ModelTransfer.state.value.progress,completed:Boolean=false){
  val old=ModelTransfer.state.value;ModelTransfer.state.value=old.copy(message=message,working=working,progress=progress,paused=ModelTransfer.paused.get(),completed=completed)
  getSystemService(NotificationManager::class.java).notify(1701,notification(message,progress,working))
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action=="PAUSE"){ModelTransfer.pause();return START_NOT_STICKY}
  if(task?.isActive==true)return START_NOT_STICKY
  val spec=runCatching {SaveJson.decodeFromString<ModelSpec>(intent?.getStringExtra("spec")?:"").also {it.verify()}}.getOrNull()?:run {stopSelf();return START_NOT_STICKY}
  val source=intent!!.getStringExtra("source")?:"auto"
  val foreground=notification("连接并核对下载源",0f,true)
  if(Build.VERSION.SDK_INT>=29)startForeground(1701,foreground,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)else startForeground(1701,foreground)
  task=scope.launch {
   val root=File(filesDir,"models").apply {mkdirs()};val part=File(root,"qwen.part");val model=File(root,"active.gguf");val prefs=getSharedPreferences("inference",0)
   try{
    val previous=runCatching {SaveJson.decodeFromString<ModelSpec>(prefs.getString("pending-spec",null)?:"")}.getOrNull()
    if(previous?.sha256!=spec.sha256||previous?.bytes!=spec.bytes||part.length()>spec.bytes)part.delete()
    prefs.edit().putString("pending",spec.id).putString("pending-spec",SaveJson.encodeToString(spec)).putString("download-source",source).commit()
    require(root.usableSpace>spec.bytes-(if(part.exists())part.length()else 0)+50L*1048576){"空间不足，需预留完整模型与校验空间"}
    var failure:Throwable?=null;var installed=false
    for(url in ModelTransfer.urls(spec,source)){
     if(ModelTransfer.paused.get())break
     try{
      val host=URL(url).host;var offset=if(part.exists())part.length()else 0L
      if(offset<spec.bytes){
       val connection=URL(url).openConnection() as HttpURLConnection;ModelTransfer.connection=connection;connection.connectTimeout=20000;connection.readTimeout=20000
       connection.setRequestProperty("Cache-Control","no-cache");if(offset>0)connection.setRequestProperty("Range","bytes=$offset-")
       try{
        val code=connection.responseCode;require(code==200||code==206){"$host 返回$code"}
        if(code==200)offset=0 else require(connection.getHeaderField("Content-Range")?.startsWith("bytes $offset-")==true){"续传范围不匹配"}
        RandomAccessFile(part,"rw").use {out->if(offset==0L)out.setLength(0);out.seek(offset)
         connection.inputStream.use {input->val buffer=ByteArray(65536);var chunks=0
          while(!ModelTransfer.paused.get()){ensureActive();val n=input.read(buffer);if(n<0)break;offset+=n;require(offset<=spec.bytes){"文件超出清单大小"};out.write(buffer,0,n);if(chunks++%16==0)update("$host · ${offset/1048576}/${spec.bytes/1048576}MiB",progress=offset.toFloat()/spec.bytes)}
         };out.fd.sync()
        }
       }finally{connection.disconnect();ModelTransfer.connection=null}
      }
      if(ModelTransfer.paused.get())break
      require(offset==spec.bytes){"下载未完整，保留断点"};update("正在校验SHA-256",progress=1f)
      if(ModelManager.hash(part)!=spec.sha256){part.delete();error("完整性校验失败，拒绝安装")}
      if(ModelTransfer.paused.get())break
      ModelManager.checkHeader(part);check(part.renameTo(model)){"无法安装已校验文件"}
      prefs.edit().putString("sha",spec.sha256).putString("active",spec.label).commit();update("${spec.label}下载完成，校验通过",false,1f,true);installed=true;break
     }catch(e:Exception){failure=e;if(!ModelTransfer.paused.get())update("当前源失败，保留断点并尝试下一源：${e.message?.take(80)}")}
    }
    if(!installed)update(if(ModelTransfer.paused.get())"已暂停；返回模型中心可继续"else"下载失败：${failure?.message?.take(160)}；可切源继续",false)
   }catch(e:Exception){update(if(ModelTransfer.paused.get())"已暂停，断点已保留"else"传输未完成：${e.message}",false)}finally{if(Build.VERSION.SDK_INT>=24)stopForeground(STOP_FOREGROUND_DETACH)else @Suppress("DEPRECATION")stopForeground(false);stopSelf()}
  }
  return START_NOT_STICKY
 }
 override fun onTimeout(startId:Int,fgsType:Int){ModelTransfer.pause();stopSelf()}
 override fun onDestroy(){if(ModelTransfer.state.value.working)ModelTransfer.pause();task?.cancel();scope.cancel();super.onDestroy()}
}
