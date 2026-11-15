package cn.chronicle.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val SaveJson = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }
@Serializable data class Nation(
 val treasury: Long = 620, val debt: Long = 0, val population: Long = 2800000,
 val income: Long = 0, val expense: Long = 0, val tax: Int = 20,
 val morale: Int = 62, val stability: Int = 65, val administration: Int = 48,
 val corruption: Int = 32, val agriculture: Int = 42, val commerce: Int = 35,
 val industry: Int = 22, val technology: Int = 18, val infrastructure: Int = 30,
 val military: Int = 38, val research: Int = 0
)
@Serializable data class Region(val id: Int, val name: String, val population: Long, val funds: Long,
 val agriculture: Int, val industry: Int, val security: Int, val corruption: Int, val morale: Int,
 val official: Int, val reform: Int = 0)
@Serializable data class Person(val id: Int, val name: String, val role: String, val faction: Int,
 val ageQuarters: Int, val ability: Int, val loyalty: Int, val stance: String, val goal: String,
 val alive: Boolean = true, val trust: Int = 35, val suspicion: Int = 0, val fear: Int = 0,
 val hostility: Int = 0, val memories: List<String> = emptyList())
@Serializable data class Faction(val name: String, val support: Int)
@Serializable data class Neighbor(val name: String, val relation: Int, val strength: Int)
@Serializable data class Project(val kind: String, val region: Int, val quarters: Int)
@Serializable data class Evidence(val id: String, val label: String, val strength: Int, val authentic: Boolean)
@Serializable data class CaseFile(val region: Int = 0, val accused: Int = 3, val guilty: Boolean,
 val evidence: List<Evidence> = emptyList(), val status: String = "未立案", val resolved: Boolean = false,
 val remoteDays: Int = 0, val resolution: String = "")
@Serializable data class Journey(val region: Int, val mode: String, val disguise: String,
 val companion: Int, val regent: Int, val objective: String, val budgetDays: Int,
 val remainingTravel: Int, val spentDays: Int = 0, val exposure: Int = 0, val revealed: Boolean = false,
 val location: String = "驿道")
@Serializable data class Entry(val revision: Long, val turn: Int, val day: Int, val action: String,
 val narrative: String, val ledger: List<String>, val events: List<String>, val seed: Long)
@Serializable data class World(val schema: Int = 1, val name: String, val ruler: String, val strategy: String,
 val difficulty: String, val seed: Long, val revision: Long = 0, val day: Int = 0,
 val nation: Nation = Nation(), val regions: List<Region>, val people: List<Person>,
 val factions: List<Faction>, val neighbors: List<Neighbor>, val projects: List<Project> = emptyList(),
 val cooldowns: Map<String, Int> = emptyMap(), val journey: Journey? = null, val caseFile: CaseFile,
 val recent: List<Entry> = emptyList(), val summary: String = "", val ended: Boolean = false,
 val era:String="启元",val eraDay:Int=0,val household:Household=Household()) {
 val turn: Int get() = day / 90
 val date: String get() = "${era}${1 + day / 360 - eraDay / 360}年 · ${listOf("春", "夏", "秋", "冬")[(day / 90) % 4]} · 第${day % 90 + 1}日"
}
@Serializable data class Command(val type: String, val region: Int = 0, val mode: String = "微服私访",
 val disguise: String = "商人", val companion: Int = 2, val regent: Int = 0,
 val objective: String = "财政审计", val days: Int = 30, val target: String = "")

data class Preview(val commands: List<Command>, val description: String, val uncertain: Boolean = false)
data class Outcome(val world: World, val entry: Entry)

object NewGame {
 fun create(name: String, ruler: String, strategy: String, difficulty: String, seed: Long, era:String="启元"): World {
  require(FamilyEngine.validName(era))
  require(name.isNotBlank() && name.length <= 24 && ruler.isNotBlank() && ruler.length <= 24)
  val n = Nation(treasury = when(difficulty) { "艰难" -> 380; "宽裕" -> 900; else -> 620 },
   industry = if(strategy == "工业强国") 28 else 22, agriculture = if(strategy == "富民治国") 50 else 42)
  return World(name=name, ruler=ruler, strategy=strategy, difficulty=difficulty, seed=seed, nation=n,era=era,
   regions=listOf(Region(0,"澄江",800000,120,52,28,55,58,54,3),Region(1,"砺山",600000,90,28,40,62,35,58,4),Region(2,"云泽",900000,110,64,12,65,26,66,5),Region(3,"朔原",500000,70,32,18,48,38,51,1)),
   people=listOf(Person(0,"沈知衡","首辅",0,180,76,72,"渐进改革","维持朝局"),Person(1,"韩砺","边军统领",2,164,74,68,"军备优先","整顿军需"),Person(2,"顾清禾","监察使",0,128,82,80,"依法核查","核验地方账目"),Person(3,"陆敬川","澄江知府",1,176,63,52,"保护商绅","维护地方税源"),Person(4,"许闻舟","砺山督办",1,144,71,61,"实业兴邦","扩建矿场"),Person(5,"陶晚晴","云泽农政官",0,116,75,76,"以民为本","修复水渠"),
    Person(6,"孟阿棠","澄江商人",1,108,58,45,"互市公平","取消额外摊派"),Person(7,"周伯","澄江农民",0,220,44,50,"安稳务农","追回赈济欠款"),Person(8,"石远","澄江士兵",2,96,62,63,"军纪严明","足额领取军饷"),Person(9,"贺逢春","澄江乡绅",1,196,68,35,"维护旧制","保住地方特许")),
   factions=listOf(Faction("革新士林",58),Faction("工商议会",52),Faction("勋旧军府",55)),
   neighbors=listOf(Neighbor("岚海联邦",45,50),Neighbor("赤岭汗国",35,58),Neighbor("白榆公国",60,36)),
   caseFile=CaseFile(guilty=Math.floorMod(seed,5L)!=0L))
 }
}

object IntentParser {
 private val rules = listOf("工厂" to "FACTORY", "钢铁" to "FACTORY", "农业" to "FARM", "农田" to "FARM", "水渠" to "FARM", "科研" to "RESEARCH", "研发" to "RESEARCH", "铁路" to "ROAD", "基建" to "ROAD", "减税" to "TAX_DOWN", "增税" to "TAX_UP", "外交" to "DIPLOMACY", "练兵" to "DRILL", "赈济" to "RELIEF", "还债" to "REPAY", "审计" to "REMOTE", "监察" to "REMOTE", "改革" to "REFORM", "休养" to "REST")
 fun parse(text: String): Preview {
  if(text.isBlank() || text.length > 600) return Preview(emptyList(),"请输入 1–600 字的命令。",true)
  if(listOf("统一世界","无限","免费","立即成功","我是皇帝").any { text.contains(it) }) return Preview(emptyList(),"此命令无法直接结算。资源、身份与领土均须经过规则验证，请选用下方合法行动。",true)
  val found=rules.filter { text.contains(it.first) }.map { Command(it.second) }.distinctBy { it.type }.take(3)
  val complex=listOf("三分之一","资本","银行","同时","一半","百分").any { text.contains(it) }
  return Preview(found, if(found.isEmpty()) "规则解析未能确定意图，请选择预设行动。" else "识别为：${found.joinToString("、") { Rules.label(it) }}。" + if(complex) "比例、银行和资本条款暂未实现，仅执行列出的标准政策；其余部分不执行。" else "请核对成本后确认。", complex || found.isEmpty())
 }
}
