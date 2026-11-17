package cn.chronicle.core

/** A conversation is evidence of what was said, never an instruction to mutate the world. */
object DecreeEngine {
 fun town(w:World,person:Int)=TalentEngine.town(w,person)?:LocalDefenseEngine.office(w,person)?.let {u->w.towns.find {it.id==u.home}}
 fun preview(w:World,person:Int,text:String):Preview {
  fun refuse(reason:String)=Preview(emptyList(),reason,true)
  if(text.isBlank()||text.length>600)return refuse("请将选中的对话整理为1–600字的明确旨意。")
  if(w.people.none {it.id==person&&it.alive})return refuse("对话人物已不在世或不存在，请重新选择。")
  if(listOf("不要","不可","不应","勿","是否","吗","？","?","如果","除非","暂缓","停止","取消").any(text::contains))return refuse("原文包含否定、疑问或条件，不能直接颁行。请编辑为明确肯定的行动；尚未执行任何事项。")
  if(listOf("处死","定罪","证据充分","撤职","统一世界","无限","免费","立即成功").any(text::contains))return refuse("该内容涉及未支持的任免、裁罪或超出规则的结果。请使用案件、人员或其他已有功能；对话不会替代证据与审理。")
  if(text.contains("运河")){
   val found=w.towns.filter {text.contains(MunicipalEngine.name(w,it))};val names=found.filter {a->found.none {b->a.id!=b.id&&MunicipalEngine.name(w,b).contains(MunicipalEngine.name(w,a))}}.sortedBy {text.indexOf(MunicipalEngine.name(w,it))}
   if(names.size!=2)return refuse("请在朝政→建设→运河直接点选起终点，或明确写出两个不同县邑的名称。不会将运河替换成普通道路。")
   val command=CanalEngine.plan(w,names[0].id,names[1].id);val error=Rules.validate(w,command);if(error!=null)return refuse(error)
   return Preview(listOf(command),"${names[0].name}—${names[1].name}运河：${Rules.cost(w,command)}万两，${command.days}日后竣工，维护3万两/季。仅执行这一段标准水运工程，自定比例与额外条款不执行。",true)
  }
  val named=w.towns.filter {text.contains(MunicipalEngine.name(w,it))};val longest=named.maxOfOrNull {MunicipalEngine.name(w,it).length}
  val matches=named.filter {MunicipalEngine.name(w,it).length==longest}
  if(matches.size>1)return refuse("出现同名城邑，请在地方治理界面选择稳定ID后任官。")
  val t=matches.singleOrNull()?:town(w,person);val region=t?.region?:0
  val commands=mutableListOf<Command>()
  val construction=listOf("扩建","修建","建设","升级","增建","营建").any(text::contains)
  if(construction&&t!=null){
   val aliases=mapOf("YAMEN" to listOf("官署","县衙"),"MARKET" to listOf("集市"),"WORKSHOP" to listOf("作坊"),"ROAD" to listOf("驿路"),"GRANARY" to listOf("常平仓","粮仓"),"CLINIC" to listOf("医馆"),"SCHOOL" to listOf("学堂"),"BARRACKS" to listOf("兵营"),"MINE" to listOf("矿场"),"SAWMILL" to listOf("林场"),"PORT" to listOf("码头"),"IRRIGATION" to listOf("灌溉"))
   aliases.filterValues {names->names.any(text::contains)}.keys.forEach {kind->commands+=Command("URBAN_BUILD",region=region,target=t.id,objective=kind,level=t.buildings.getValue(kind)+1,text=text)}
  }
  if(t!=null&&listOf("审计","核查账册","核验账册").any(text::contains))commands+=Command("URBAN_AUDIT",region=region,target=t.id,text=text)
  if(t!=null&&text.contains("赈济"))commands+=Command("URBAN_RELIEF",region=region,target=t.id,text=text)
  if(text.contains("国家学院")&&listOf("入学","派学","学习","研习").any(text::contains))commands+=Command("STUDY_HOME",target=person.toString(),text=text)
  if(construction)CrownEngine.buildings.filter {text.contains(it.name)}.forEach {b->commands+=Command("PALACE_BUILD",objective=b.id,level=w.estate.levels.getValue(b.id)+1,text=text)}
  if(listOf("举办科举","筹办科举","开科取士").any(text::contains))commands+=Command("EXAM",text=text)
  if(text.contains("公开招贤"))commands+=Command("RECRUIT",text=text)
  LocalDefenseEngine.office(w,person)?.takeIf {named.isEmpty()||t?.id==it.home}?.let {u->
   if(listOf("操练","训练县兵","训练州军","训练府卫").any(text::contains))commands+=Command("LOCAL_TRAIN",target=u.id,text=text)
   if(listOf("募兵","招募").any(text::contains)&&listOf("县兵","州军","府卫","士兵").any(text::contains)){
    val count=Regex("(?<![0-9])([0-9]+)(?:名|人)").find(text)?.groupValues?.get(1)?.toIntOrNull()
    if(count !in listOf(50,100,200,500))return refuse("请明确招募50、100、200或500人；也可在兵籍直接点选，不会擅自扩大征募。")
    commands+=Command("LOCAL_RECRUIT",target=u.id,level=count!!,text=text)
   }
  }
  if(text.contains("任命")){
   val namedPeople=w.people.filter {it.alive&&text.contains(it.name)}
   if(namedPeople.size>1)return refuse("涉及多位人物，请拆分任命。")
   val nominee=namedPeople.singleOrNull()?.id?:person
   val armies=CrownEngine.units(w).filter {text.contains(it.name)}
   if(armies.size>1)return refuse("涉及多支军队，请拆分任命。")
   if(armies.size==1)commands+=Command("ARMY_ASSIGN",target=armies.single().id,objective=nominee.toString(),text=text)
   else if(t!=null){val post=when {text.contains("县丞")||text.contains("同知")->"副官";text.contains("主簿")->"主簿";text.contains("县令")||text.contains("知府")||text.contains("总督")->"长官";else->return refuse("请明确写出县令、知府、县丞、主簿或军队名称，避免任错官职。")};commands+=Command("APPOINT",region=t.region,target=nominee.toString(),objective=t.id,mode=post,text=text)}
   else return refuse("请写明现有府县或军队，再拟定任命。")
  }
  if(commands.isEmpty())commands+=EraRules.parse(text,w).commands.map {it.copy(region=region,text=text)}
  if(commands.isEmpty())return refuse("尚未识别出可执行事项。可编辑为“扩建学堂”“审计本县财政”“赈济百姓”“派你去国家学院学习”等。")
  if(commands.size>3)return refuse("一次最多拟定三项行动，请拆分旨意。")
  var future=w
  try{commands.forEach {future=RealtimeEngine.submit(future,it,future.revision).world}}catch(e:IllegalArgumentException){return refuse("当前不能颁行：${e.message}。请修改旨意或处理条件后重拟。")}
  val scope=t?.let {"行动辖区：${MunicipalEngine.name(w,it)}（${it.id}）。"}?:"本次国家政策地区：${w.regions[region].name}；派学对象为当前对话人物。"
  return Preview(commands,"$scope\n仅执行下列${commands.size}项标准行动，总费用${commands.sumOf {Rules.cost(w,it)}}万两。\n${commands.joinToString("\n") {c->if(c.type=="URBAN_BUILD")"${MunicipalEngine.buildings.first {it.id==c.objective}.name} → ${c.level}级：${MunicipalEngine.buildings.first {it.id==c.objective}.effect}" else if(c.type=="APPOINT")"${w.people.first {it.id==c.target.toInt()}.name} → ${w.towns.first {it.id==c.objective}.let {MunicipalEngine.name(w,it)+TalentEngine.role(it,c.mode)}}" else if(c.type=="ARMY_ASSIGN")"${w.people.first {it.id==c.objective.toInt()}.name} → ${CrownEngine.units(w).first {it.id==c.target}.name}主将" else Rules.label(c)}}\n原文中的自定金额、比例、条件及其他未列出事项均不执行。建设须等待工期；审计不等于定罪。确认时重新检查资金、名额与状态。",true)
 }
}
