package cn.chronicle.core

import kotlinx.serialization.Serializable

@Serializable data class Exhibit(val id:String,val kind:String,val title:String,val detail:String,val source:Int,val authentic:Boolean,val verified:Boolean=false,val coerced:Boolean=false) {
 val points:Int get()=if(!verified||!authentic||coerced)0 else when(kind){"账册"->40;"实物"->35;else->25}
}
@Serializable data class Investigation(val id:String,val blueprint:String,val town:String,val openedDay:Int,val venue:String,val accused:Int,val culprit:Int,val witness:Int,val truth:String,val motive:String,val accepted:Boolean=false,val exhibits:List<Exhibit> = emptyList(),val contradictions:Int=0,val protectedWitness:Boolean=false,val revealedCulprit:Boolean=false,val resolved:Boolean=false,val verdict:String="",val closedDay:Int=-1,val revisitDay:Int=-1,val followup:String="",val grievancePerson:Int=-1)
@Serializable data class CaseBook(val enabled:Boolean=false,val serial:Int=0,val cases:List<Investigation> = emptyList(),val lastSpawn:Int=-1,val witnessedRulerAbuse:Int=0,val lastRequest:Int=-30)
object Seeded {
 fun pick(w:World,key:String,n:Int):Int {require(n>0);return Math.floorMod(w.seed*6364136223846793005L+key.hashCode()*1442695040888963407L,n.toLong()).toInt()}
}
object InvestigationEngine {
 val labels=linkedMapOf("CASE_REQUEST" to "征询地方陈情","CASE_OPEN" to "受理线索","CASE_VISIT" to "登记走访","CASE_RECORD" to "调取账册","CASE_PHYSICAL" to "采验实物","CASE_VERIFY" to "独立复核","CASE_CROSS" to "当面对质","CASE_PROTECT" to "保护证人","CASE_DECIDE" to "裁处案件","CASE_PRESSURE" to "强令作证")
 val types=labels.keys
 val venues=listOf("监察转呈","联名诉状","商会申冤","军士呈情","驿站投帖","叩阍鸣冤","巡幸拦驾","街坊诉苦")
 fun covert(w:World)=w.journey?.revealed==false
 fun authority(w:World)=w.journey==null||w.journey.revealed
 fun cost(c:Command):Long=when(c.type){"CASE_OPEN"->if(c.mode=="暗访")5 else 0;"CASE_VISIT"->if(c.mode=="委派")25 else 3;"CASE_RECORD"->15;"CASE_PHYSICAL"->8;"CASE_VERIFY"->12;"CASE_CROSS"->8;"CASE_PROTECT"->25;"CASE_PRESSURE"->5;"CASE_REQUEST"->10;"CASE_DECIDE"->when(c.mode){"纠正王命"->50;"依法追究"->25;"行政撤换"->15;"制度改革"->35;"强制处置"->45;else->0};else->0}
 fun duration(c:Command)=when(c.type){"CASE_REQUEST"->5;"CASE_OPEN"->2;"CASE_VISIT"->if(c.mode=="委派")12 else 3;"CASE_RECORD"->5;"CASE_PHYSICAL","CASE_VERIFY"->4;"CASE_CROSS","CASE_PROTECT","CASE_PRESSURE","CASE_DECIDE"->3;else->0}
 fun qualified(w:World,b:CaseBlueprint,t:Town):Boolean=when(b.domain){"军务"->w.localDefense.units.any {it.home==t.id&&it.soldiers>0};"工造"->(t.buildings["WORKSHOP"]?:0)>0||t.work!=null;"宫务"->t.region==0;else->true}
 fun spawn(w:World,townId:String?=null,blueprintId:String?=null,venue:String?=null):World {
  if(!w.caseBook.enabled||w.caseBook.cases.count {!it.resolved}>=8)return w
  val towns=w.towns.filter {t->MunicipalEngine.officer(w,t)!=null&&w.caseBook.cases.none {!it.resolved&&it.town==t.id}&&(blueprintId==null||CaseLibrary.all.any {it.id==blueprintId&&qualified(w,it,t)&&w.caseBook.cases.none {c->c.town==t.id&&c.blueprint==it.id&&w.day-c.openedDay<720}})}
  val t=(if(townId!=null)towns.find {it.id==townId}else towns.getOrNull(Seeded.pick(w,"town:${w.turn}:${w.caseBook.serial}",towns.size.coerceAtLeast(1))))?:return w
  val eligible=CaseLibrary.all.filter {qualified(w,it,t)&&w.caseBook.cases.none {c->c.town==t.id&&c.blueprint==it.id&&w.day-c.openedDay<720}}
  val b=(if(blueprintId!=null)eligible.find {it.id==blueprintId}else eligible.getOrNull(Seeded.pick(w,"blueprint:${w.turn}:${w.caseBook.serial}",eligible.size.coerceAtLeast(1))))?:return w
  val serial=w.caseBook.serial+1;val id="docket-$serial-${t.id}";val branch=Seeded.pick(w,id,6)
  val chief=MunicipalEngine.officer(w,t)!!.id;val deputy=w.people.firstOrNull {it.id==t.deputy&&it.alive}?.id?:chief
  val truth=listOf("经办侵吞","替罪指控","上下串通","合法应急","恶意构陷","误会与疏漏")[branch]
  val culprit=when(branch){0,2->chief;1,4->deputy;else->-1}
  val newId=(w.people.maxOfOrNull {it.id}?:0)+1;val canAdd=w.people.size<1000
  val person=if(canAdd)Person(newId,"${t.name.take(3)}·${listOf("闻溪","明珂","望舒","安禾","行舟","青芜","守朴","秋白")[serial%8]}$serial",t.name+b.witness,serial%3,80+Seeded.pick(w,id+"age",200),30+Seeded.pick(w,id+"skill",45),35+Seeded.pick(w,id+"loyalty",40),if(branch==4)"利害优先"else"求得公道",b.rumor,trust=30+Seeded.pick(w,id+"trust",30),fear=if(branch in listOf(1,2))75 else 20) else w.people.first {it.id==(if(t.clerk>=0)t.clerk else chief)}
  val petition=venue?:when {covert(w)&&w.journey?.region==t.region->"街坊诉苦";w.journey?.revealed==true&&w.journey.region==t.region->"巡幸拦驾";b.domain=="军务"->"军士呈情";b.domain=="工造"->"商会申冤";t.morale<45->"联名诉状";else->venues[Seeded.pick(w,id+"venue",6)]}
  val c=Investigation(id,b.id,t.id,w.day,petition,chief,culprit,person.id,truth,b.motive)
  return w.copy(people=if(canAdd)w.people+person else w.people,caseBook=w.caseBook.copy(serial=serial,cases=(w.caseBook.cases+c).let {cs->if(cs.size<=128)cs else cs.filter {!it.resolved}+cs.filter {it.resolved}.takeLast(120)}))
 }
 fun quarter(w:World,events:MutableList<String>):World {
  if(!w.caseBook.enabled||w.caseBook.lastSpawn==w.turn)return w
  var x=w.copy(caseBook=w.caseBook.copy(lastSpawn=w.turn))
  val need=w.nation.corruption+w.nation.tax+(if(w.payroll.arrears.isNotEmpty())30 else 0)+(if(w.war!=null)20 else 0)
  if(Seeded.pick(w,"petition:${w.turn}",100)<need.coerceIn(30,85)){
   val before=x.caseBook.serial;x=spawn(x);if(x.caseBook.serial>before){val c=x.caseBook.cases.last();events+="【新线索】${c.venue}：${w.towns.first {it.id==c.town}.name}${CaseLibrary.get(c.blueprint).title}，仅属待核陈情，未认定有罪。"}
  }
  val complainant=x.adventure.encounters.firstOrNull {it.forced&&!it.released&&w.day-it.day>=30&&x.caseBook.cases.none {c->c.grievancePerson==it.person}}
  if(complainant!=null&&x.caseBook.cases.count {!it.resolved}<8){
   val before=x.caseBook.serial;x=spawn(x,blueprintId="case-62",venue=if(covert(x))"驿站投帖"else"叩阍鸣冤")
   if(x.caseBook.serial>before){val id=x.caseBook.cases.last().id;x=x.copy(caseBook=x.caseBook.copy(cases=x.caseBook.cases.map {if(it.id==id)it.copy(truth="王命强制",culprit=-1,witness=complainant.person,grievancePerson=complainant.person)else it}));events+="【陈情】有人对王命强制安排提出异议。须核实当事人、见证与登记，不可因触犯君威自动判作诬告。"}
  }
  val revised=x.caseBook.cases.map {c->if(c.resolved&&c.followup.isBlank()&&w.day-c.closedDay>=90){val text=when(c.verdict){"制度改革"->"经费、办理期限与回避记录已公开，等待后续回访；改革不保证立即消除积弊。";"强制处置"->if(c.culprit==c.accused)"后续材料证实存在问题，但强制程序仍留下怨怼。"else"后续材料与原指控不合，民间质疑强制处置；当事人并未因此自动恢复信任。";"保留待查"->"部分线索仍未闭合，案卷保留，新的可核材料出现后可以重新受理。";else->"地方保存原始案卷、岗位变动与补偿记录，证人可以回访。"};events+="${CaseLibrary.get(c.blueprint).title}后续：$text";c.copy(followup=text,revisitDay=w.day)}else c}
  return x.copy(caseBook=x.caseBook.copy(cases=revised))
 }
 fun get(w:World,id:String)=w.caseBook.cases.find {it.id==id}
 fun score(c:Investigation)=c.exhibits.sumOf {it.points}
 fun validate(w:World,c:Command):String? {
  if(c.type=="CASE_REQUEST")return when { !w.caseBook.enabled->"请先开启新章。";covert(w)->"伪装时不能以君主名义征集御状，可在当地自然接触陈情者。";w.caseBook.cases.count {!it.resolved}>=8->"待核案卷已达8件，请先办理现有陈情。";w.day-w.caseBook.lastRequest<30->"公开征询须间隔30日，日常御状仍可能自然出现。";else->null}
  val file=get(w,c.target)?:return "案卷不存在。";val b=CaseLibrary.get(file.blueprint);val town=w.towns.first {it.id==file.town}
  if(file.resolved)return "案卷已结，后续影响会继续记录，不能重复处分。"
  val inTown=w.journey?.let {it.remainingTravel==0&&it.region==town.region}==true
  if(c.type=="CASE_OPEN")return when {file.accepted->"本案已受理。";c.mode !in listOf("御前受理","暗访")->"请选择御前受理或暗访登记。";c.mode=="御前受理"&&!authority(w)->"伪装身份不能当众受御状。可在当地暗访登记，或公开验证身份。";c.mode=="暗访"&&!inTown->"暗访须亲至当地。";else->null}
  if(!file.accepted)return "请先受理线索，明确核查权限。"
  if(c.type=="CASE_VISIT"&&c.mode !in listOf("委派","亲访"))return "请选择亲访或委派。"
  if(c.type=="CASE_VISIT"&&c.mode=="委派"&&!authority(w))return "普通旅客不能委派公职监察，须先验明身份或亲访。"
  if(c.type=="CASE_VISIT"&&c.mode=="亲访"&&!inTown)return "亲访须抵达案发大区；在朝可以委派核查。"
  if(c.type in listOf("CASE_RECORD","CASE_VERIFY","CASE_CROSS","CASE_DECIDE","CASE_PRESSURE","CASE_PROTECT")&&!authority(w))return "当前仅以${w.journey!!.disguise}行事，不能直接调公簿、传讯或裁案。请出示印信验证身份，或返回朝廷后委派。"
  if(c.type=="CASE_PHYSICAL"&&covert(w)&&b.site=="军营")return "军营不向普通旅客开放；须有合法军令或委派核查，不能自称君主闯入。"
  if(c.type=="CASE_PHYSICAL"&&!authority(w)&&!inTown)return "采样须在当地亲访，或以已验证的王命委派。"
  val kind=when(c.type){"CASE_VISIT"->"证言";"CASE_RECORD"->"账册";"CASE_PHYSICAL"->"实物";else->null}
  if(kind!=null&&file.exhibits.any {it.kind==kind&&!it.coerced})return "此类材料已登记，请复核或质证。"
  if(c.type=="CASE_VERIFY"&&file.exhibits.none {it.id==c.objective&&!it.verified})return "请选择尚未复核的材料。"
  if(c.type=="CASE_CROSS"){
   if(file.exhibits.map {it.kind}.distinct().size<2||file.exhibits.none {it.verified})return "质证至少需要两类材料，且一类经过独立复核。"
   if(file.revealedCulprit||file.contradictions>0)return "关键矛盾已经质证，不能重复刷取线索。"
   if(w.people.first {it.id==file.witness}.fear>=70&&!file.protectedWitness)return "证人畏惧报复，拒绝公开对质。可先保护证人；强令作证不能补足证据。"
  }
  if(c.type=="CASE_PROTECT"&&file.protectedWitness)return "本案证人已列入保护。"
  if(c.type=="CASE_PRESSURE"&&file.exhibits.any {it.coerced})return "已记录受压笔录，不能重复施压刷结果。"
  if(c.type=="CASE_DECIDE"){
   if(c.mode !in listOf("依法追究","行政撤换","制度改革","保留待查","撤销指控","强制处置","纠正王命"))return "处置路线无效。"
   if(file.grievancePerson>=0&&c.mode in listOf("依法追究","行政撤换","强制处置"))return "本案涉及王命安排，不可把责任转嫁给无独立犯罪证据的地方官。请核查后纠正、改革或保留待查。"
   if(c.mode=="纠正王命"&&(file.grievancePerson<0||file.exhibits.none {it.verified&&it.authentic}))return "须核实当事人与王命强制的关联后才能纠正、赔偿。"
   if(c.mode=="依法追究"&&(score(file)<75||file.contradictions==0||file.culprit<0||(!file.revealedCulprit&&file.culprit!=file.accused)))return "证据链与拟追究对象尚未闭合。须核验账册、实物并独立质证；传闻和受压笔录不计入合法证据。"
   if(c.mode=="撤销指控"&&file.exhibits.none {it.verified})return "未独立核查不能断言指控失实，可以先保留待查。"
   if(c.mode=="强制处置"&&(w.nation.stability<20||town.security<20))return "行政与治安已难以执行强制命令，请先恢复秩序。"
  }
  return null
 }
 fun execute(w:World,c:Command,events:MutableList<String>):World {
  if(c.type=="CASE_REQUEST"){val x=spawn(w);events+="公开征询已经登记：新线索仍须受理、取证，不预先认定任何人有罪。";return x.copy(caseBook=x.caseBook.copy(lastRequest=w.day))}
  val file=get(w,c.target)!!;val b=CaseLibrary.get(file.blueprint);val town=w.towns.first {it.id==file.town};var next=file;var x=w
  fun exhibit(kind:String,title:String,detail:String,authentic:Boolean){val id="${file.id}-${file.exhibits.size}";next=next.copy(exhibits=next.exhibits+Exhibit(id,kind,title,detail,file.witness,authentic))}
  when(c.type){
   "CASE_OPEN"->{next=next.copy(accepted=true);events+="${file.venue}材料列入待核案簿：${if(file.grievancePerson>=0)"当事人请求核对强制安排、去留意愿与补偿"else b.rumor}。受理不等于认定犯罪。"}
   "CASE_VISIT"->{exhibit("证言",b.witness+"的陈述",if(file.grievancePerson>=0)"当事人请求复核已登记的非自愿安排，不把畏惧等同同意。"else b.rumor,file.truth!="恶意构陷");events+="完成${if(c.mode=="委派")"委派走访"else"当地走访"}，实名陈述已登记，尚未核真。"}
   "CASE_RECORD"->{exhibit("账册",b.record,"取得原始登记与对照副本；须核验编号、签押和形成时序。",file.truth!="恶意构陷");events+="原始账册登记入卷；账簿存在并不等于内容真实。"}
   "CASE_PHYSICAL"->{exhibit("实物",if(file.grievancePerson>=0)"去留与强制安排的在册记录"else b.physical,if(file.grievancePerson>=0)"核对实际登记的强制安排与本人意愿；没有凭空生成被拦截的文书。"else "封存采样并登记交接；尚未独立鉴定。",file.truth !in listOf("合法应急","误会与疏漏"));events+="样本按来源封存，未凭外观直接定罪。"}
   "CASE_VERIFY"->{next=next.copy(exhibits=next.exhibits.map {if(it.id==c.objective)it.copy(verified=true)else it});val e=next.exhibits.first {it.id==c.objective};events+="${e.title}复核：${if(e.authentic)"来源与时序得到相互印证，仍须联系责任对象"else"出现不符，不能作为定罪材料"}。"}
   "CASE_PROTECT"->{next=next.copy(protectedWitness=true);x=x.copy(people=x.people.map {if(it.id==file.witness)it.copy(fear=(it.fear-25).coerceAtLeast(0),trust=(it.trust+6).coerceAtMost(100),memories=(it.memories+"${w.day}日列入证人保护，费用由国库登记").takeLast(8))else it});events+="证人保护已登记，不能用保护换取指定口供。"}
   "CASE_CROSS"->{next=next.copy(contradictions=1,revealedCulprit=true);val reveal=when(file.truth){"替罪指控"->"经办时序指向另一名在册经办人，最初被指控者可能是替罪对象。";"上下串通"->"账册与交接物证形成上下游关联，责任不能只落在末端经办人。";"合法应急"->"查到合法应急调拨的登记，但通知与公示存在缺失。";"恶意构陷"->"原始副本与呈状材料不符，出现构陷线索；另案追究仍须完整证据。";"误会与疏漏"->"时限、计量或登记口径造成误解，仍有事项需补查，不宜凭猜测结罪。";else->"材料时序与责任对象吻合，尚须核足其他证据后裁处。"};if(file.truth=="恶意构陷")next=next.copy(exhibits=next.exhibits+Exhibit("${file.id}-original","账册","原始收发簿与伪件对照","独立原件与呈状伪件形成时序不符，责任来源已核实。",file.witness,true,true));events+="质证：${b.contradiction}。$reveal"}
   "CASE_PRESSURE"->{next=next.copy(exhibits=next.exhibits+Exhibit("${file.id}-pressure","证言","受压形成的笔录","非自愿应答，不能作为独立合法证据。",file.witness,false,true,true));x=x.copy(people=x.people.map {if(it.id==file.witness)it.copy(trust=(it.trust-15).coerceAtLeast(0),fear=(it.fear+25).coerceAtMost(100),hostility=(it.hostility+15).coerceAtMost(100))else it},nation=x.nation.copy(morale=x.nation.morale-3,stability=x.nation.stability-2));events+="记录强令作证及政治后果；笔录不计入合法证据分。"}
   "CASE_DECIDE"->{
   if(c.mode=="纠正王命"){
     val encounter=x.adventure.encounters.find {it.person==file.grievancePerson&&it.forced&&!it.released}
     if(encounter!=null)x=EncounterEngine.execute(x,Command("ENCOUNTER_RELEASE",target=encounter.person.toString()),events)
     events+="王命强制经复核后纠正，50万两用于赔偿、迁居与复核；不将责任转嫁给无证据的地方官。"
    }
    val actual=if(file.revealedCulprit&&file.culprit>=0)file.culprit else file.accused
    val removes=c.mode in listOf("依法追究","行政撤换","强制处置")
    if(removes){x=x.copy(towns=x.towns.map {it.copy(official=if(it.official==actual)-1 else it.official,deputy=if(it.deputy==actual)-1 else it.deputy,clerk=if(it.clerk==actual)-1 else it.clerk)},regions=x.regions.map {if(it.official==actual)it.copy(official=-1)else it});x=x.copy(localDefense=x.localDefense.copy(units=x.localDefense.units.map {it.copy(commander=if(it.commander==actual)-1 else it.commander,governor=if(it.governor==actual)-1 else it.governor)}),people=x.people.map {if(it.id==actual&&it.alive)it.copy(role=if(c.mode=="依法追究")"依法免职候审"else if(c.mode=="强制处置")"强制免职，未认定犯罪"else"免职待核",loyalty=(it.loyalty-if(c.mode=="依法追究")15 else 25).coerceAtLeast(0),memories=(it.memories+"${w.day}日${b.title}：${c.mode}").takeLast(8))else it})}
    val forced=c.mode=="强制处置";val wrongful=forced&&file.culprit!=actual
    x=x.copy(towns=x.towns.map {if(it.id==town.id)it.copy(corruption=(it.corruption-if(c.mode=="制度改革")6 else if(c.mode=="依法追究")4 else 0).coerceAtLeast(0),morale=(it.morale+if(forced)-8 else if(c.mode in listOf("依法追究","制度改革"))3 else 0).coerceIn(0,100))else it},nation=x.nation.copy(morale=x.nation.morale+if(forced)-6 else 1,stability=x.nation.stability+if(forced)-5 else 1,administration=x.nation.administration+if(c.mode=="制度改革")1 else 0),caseBook=x.caseBook.copy(witnessedRulerAbuse=x.caseBook.witnessedRulerAbuse+if(wrongful)1 else 0))
    next=next.copy(resolved=true,verdict=c.mode,closedDay=w.day)
    events+="${b.title}以“${c.mode}”登记结案。${if(c.mode=="制度改革")b.reform else if(c.mode=="保留待查")"证据仍有留白，保留未决线索；没有认定任何人有罪。"else if(forced)"行政强制不代替事实认定，民怨与责任持续保留。"else"免职、定罪和证据不足分别记录，不抹去既有历史。"}"
   }
  }
  return x.copy(caseBook=x.caseBook.copy(cases=x.caseBook.cases.map {if(it.id==file.id)next else it}))
 }
 fun visible(w:World,c:Investigation):String {val b=CaseLibrary.get(c.blueprint);if(c.grievancePerson>=0)return "王命强制陈情：请求核对强制安排、当事人意愿与补偿责任；尚待独立核查。";return "${b.title}：${b.rumor}；${c.exhibits.size}项已登记材料，${c.exhibits.count {it.verified}}项复核；${if(c.resolved)c.verdict else "待核查，不能凭传闻定罪"}。"}
 fun verify(w:World){val b=w.caseBook;require(b.cases.size<=128&&b.serial>=0&&b.cases.map {it.id}.distinct().size==b.cases.size);b.cases.forEach {c->require(CaseLibrary.all.any {it.id==c.blueprint}&&w.towns.any {it.id==c.town}&&w.people.any {it.id==c.accused}&&w.people.any {it.id==c.witness}&&c.exhibits.size<=5&&c.contradictions in 0..1&&c.openedDay in 0..w.day);require(c.exhibits.map {it.id}.distinct().size==c.exhibits.size)}}
}
