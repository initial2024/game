package cn.chronicle.core

import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLEncoder

data class HubRepository(val id:String,val license:String,val downloads:Long,val task:String,val gated:Boolean,val revision:String="")
data class HubFile(val path:String,val bytes:Long,val sha256:String,val blocked:String="")
data class HubPage<T>(val items:List<T>,val next:String?=null)
object HubCatalog {
 fun repo(id:String)=id.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))
 fun searchUrl(query:String,sort:String="downloads",ggufOnly:Boolean=true):String {
  require(query.length in 1..120&&sort in listOf("downloads","lastModified"))
  return "https://huggingface.co/api/models?search=${URLEncoder.encode(query,"UTF-8")}&sort=$sort&direction=-1&limit=12&full=true"+if(ggufOnly)"&filter=gguf" else ""
 }
 private fun obj(e:JsonElement?)=e as? JsonObject
 private fun text(e:JsonElement?)=(e as? JsonPrimitive)?.contentOrNull?:""
 private fun number(e:JsonElement?)=(e as? JsonPrimitive)?.longOrNull?:0L
 fun repository(o:JsonObject):HubRepository {
  val id=text(o["id"]);require(repo(id))
  val tags=(o["tags"] as? JsonArray).orEmpty().map(::text)
  val license=text(obj(o["cardData"])?.get("license")).ifEmpty {tags.firstOrNull {it.startsWith("license:") }?.removePrefix("license:")?:"unknown"}
  return HubRepository(id,license,number(o["downloads"]),text(o["pipeline_tag"]),text(o["gated"]) !in listOf("","false"),text(o["sha"]))
 }
 fun repositories(json:JsonElement)=json.jsonArray.mapNotNull {runCatching {repository(it.jsonObject)}.getOrNull()}
 fun files(json:JsonElement):List<HubFile> =json.jsonArray.mapNotNull {e->
  val o=obj(e)?:return@mapNotNull null;val path=text(o["path"]);if(text(o["type"])!="file"||!path.endsWith(".gguf",true))return@mapNotNull null
  val lfs=obj(o["lfs"]);val sha=text(lfs?.get("oid")).ifEmpty {text(lfs?.get("sha256"))}.removePrefix("sha256:")
  val bytes=number(o["size"])
  val reason=when {
   path.contains(Regex("-\\d{5}-of-\\d{5}"))->"分片GGUF，不能单独安装；请使用完整单文件。"
   path.substringAfterLast('/').contains("mmproj",true)->"视觉投影附件，不是独立聊天模型。"
   bytes !in 16_000_000L..8_589_934_592L->"超出本版16MB–8GiB单文件安装范围。"
   !sha.matches(Regex("[a-f0-9]{64}"))->"缺少可核验的SHA-256，不能自动安装。"
   else->""
  }
  HubFile(path,bytes,sha,reason)
 }
 fun next(link:String?,prefix:String):String? {
  val url=Regex("<([^>]+)>;\\s*rel=\"?next\"?").find(link?:"")?.groupValues?.get(1)?:return null
  val u=URI(url);require(u.scheme=="https"&&u.host=="huggingface.co"&&u.rawUserInfo==null&&u.port in listOf(-1,443)&&u.path==URI(prefix).path){"模型库返回了不受信的分页地址"}
  return url
 }
 fun spec(repo:HubRepository,file:HubFile):ModelSpec {
  require(file.blocked.isEmpty()&&!repo.gated){file.blocked.ifEmpty {"此仓库需登录或接受访问条款；请先从官网取得授权文件再导入。"}}
  return ModelSpec(repo.id,repo.revision,file.path,file.bytes,file.sha256,repo.license,"${repo.id.substringAfter('/')} · ${file.path.substringAfterLast('/')}").also {it.verify()}
 }
}
