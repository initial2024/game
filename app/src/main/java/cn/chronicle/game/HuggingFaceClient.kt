package cn.chronicle.game

import cn.chronicle.core.*
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

class HuggingFaceClient {
 private val active=AtomicReference<HttpURLConnection?>()
 private fun get(url:String):Pair<JsonElement,String?> {
  val connection=URL(url).openConnection() as HttpURLConnection;active.set(connection);connection.connectTimeout=15000;connection.readTimeout=20000;connection.setRequestProperty("Accept","application/json")
  try {val status=connection.responseCode;require(status==200){when(status){401,403->"该仓库需登录/授权；公开仓库无需登录，已授权文件可从本地导入。";429->"Hugging Face请求频繁，请稍后重试。";else->"Hugging Face HTTP $status"}}
   val text=connection.inputStream.use {input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=4*1024*1024){"目录响应超过4MiB"};out.write(buffer,0,n)};out.toString("UTF-8")}
   return Json.parseToJsonElement(text) to connection.getHeaderField("Link")
  }finally{active.compareAndSet(connection,null);connection.disconnect()}
 }
 fun cancel(){active.getAndSet(null)?.disconnect()}
 fun search(query:String,sort:String,ggufOnly:Boolean,next:String?=null):HubPage<HubRepository>{val initial=HubCatalog.searchUrl(query,sort,ggufOnly);val (json,link)=get(next?:initial);return HubPage(HubCatalog.repositories(json),HubCatalog.next(link,initial))}
 fun detail(id:String):HubRepository {require(HubCatalog.repo(id));return HubCatalog.repository(get("https://huggingface.co/api/models/$id").first.jsonObject)}
 fun files(repo:HubRepository,next:String?=null):HubPage<HubFile>{require(HubCatalog.repo(repo.id)&&repo.revision.matches(Regex("[a-f0-9]{40}")));val initial="https://huggingface.co/api/models/${repo.id}/tree/${repo.revision}?recursive=true&limit=100";val (json,link)=get(next?:initial);return HubPage(HubCatalog.files(json),HubCatalog.next(link,initial))}
}
