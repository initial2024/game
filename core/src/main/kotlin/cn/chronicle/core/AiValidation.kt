package cn.chronicle.core
import kotlinx.serialization.Serializable
@Serializable data class AiPlan(val commands:List<AiPolicy>)
@Serializable data class AiPolicy(val type:String,val region:Int=0)
object AiValidation {
 fun parse(text:String,w:World):Preview {
  require(text.length<=6000)
  val p=SaveJson.decodeFromString<AiPlan>(text.trim())
  require(p.commands.size in 1..3)
  val commands=p.commands.map {require(it.type in Rules.policies);require(it.region in 0..3);Command(it.type,region=it.region)}
  var future=w
  commands.forEach {future=Rules.apply(future,it,future.revision).world}
  return Preview(commands,"本地模型建议映射为标准政策：${commands.joinToString {Rules.label(it)}}。仅执行清单；未列出的金额、比例及附加条件均不执行。请确认。",true)
 }
}
