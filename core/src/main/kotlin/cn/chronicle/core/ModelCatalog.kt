package cn.chronicle.core

import kotlinx.serialization.Serializable
@Serializable data class ModelSpec(val repo:String,val revision:String,val file:String,val bytes:Long,val sha256:String,val license:String,val label:String) {
 val id get()="$repo/$file"
 val url get()="https://huggingface.co/$repo/resolve/$revision/$file"
 fun verify() {
  require(repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")))
  require(revision.matches(Regex("[a-f0-9]{40}")))
  require(file.matches(Regex("[A-Za-z0-9_./-]+\\.gguf")) && !file.contains("..") && !file.startsWith("/") && !file.contains(Regex("-\\d{5}-of-\\d{5}")))
  require(bytes in 50_000_000L..1_610_612_736L && sha256.matches(Regex("[a-f0-9]{64}")))
  require(license in listOf("apache-2.0","mit"))
 }
}
object ModelCatalog {
 val builtIn=listOf(
  ModelSpec("Qwen/Qwen2.5-0.5B-Instruct-GGUF","9217f5db79a29953eb74d5343926648285ec7e67","qwen2.5-0.5b-instruct-q4_k_m.gguf",491400032,"74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db","apache-2.0","Qwen2.5 0.5B · Q4_K_M"),
  ModelSpec("Qwen/Qwen2.5-0.5B-Instruct-GGUF","9217f5db79a29953eb74d5343926648285ec7e67","qwen2.5-0.5b-instruct-q3_k_m.gguf",432041824,"590d2479d401db206fe12a4562294d2de6211e06338a6e34fbad64b32f1469d0","apache-2.0","Qwen2.5 0.5B · Q3_K_M"),
  ModelSpec("Qwen/Qwen2.5-1.5B-Instruct-GGUF","91cad51170dc346986eccefdc2dd33a9da36ead9","qwen2.5-1.5b-instruct-q4_k_m.gguf",1117320736,"6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e","apache-2.0","Qwen2.5 1.5B · Q4_K_M")
 )
 data class Advice(val model:ModelSpec?,val context:Int,val threads:Int,val text:String)
 fun recommend(available:Long,total:Long,cores:Int,arm64:Boolean):Advice {
  val mib=1024L*1024
  if(!arm64 || available<1200*mib) return Advice(null,512,1,"当前可用内存偏低或不是 ARM64，建议规则模式。释放内存后可重新评估。")
  val model=if(available<2000*mib) builtIn[1] else builtIn[0]
  val large=if(total>=8000*mib && available>=4000*mib) "1.5B 也在估算内存预算内，可自行比较。" else "优先控制内存，暂不推荐更大的模型。"
  return Advice(model,if(available>=4000*mib) 2048 else if(available>=2000*mib) 1024 else 512,cores.coerceIn(1,4),"按当前内存建议 ${model.label}。$large 此建议不代表已测得的手机速度或叙事质量。")
 }
}
