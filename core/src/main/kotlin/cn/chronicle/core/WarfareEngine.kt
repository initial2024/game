package cn.chronicle.core

import kotlinx.serialization.Serializable

@Serializable data class WarFront(val neighbor:Int,val army:String,val days:Int=0,val battles:Int=0,val progress:Int=0,val enemy:Int,val weariness:Int=0,val campaign:String="legacy",val lastParley:Int=-360,val enemyWeariness:Int=0,val lastApproach:String="稳战")
data class CampaignApproach(val mode:String,val label:String,val days:Int,val cost:Long,val supply:Int,val powerPercent:Int,val victory:Int,val defeat:Int,val wonLossPercent:Int,val lostLossPercent:Int,val grainParts:Int,val iron:Long,val timber:Long)
object WarfareEngine {
 val types=setOf("DECLARE_WAR","WAR_BATTLE","WAR_ENVOY","WAR_PEACE","WAR_WITHDRAW")
 val approaches=listOf(
  CampaignApproach("稳战","稳步交战",14,35,6,100,25,25,7,14,2,8,5),
  CampaignApproach("速攻","速攻突击",7,75,18,130,35,30,12,23,3,15,4),
  CampaignApproach("围困","围困断粮",28,55,10,110,20,12,3,8,4,4,18))
 fun approach(c:Command)=approaches.firstOrNull {it.mode==c.mode}?:approaches.first()
 fun cost(c:Command)=when(c.type){"DECLARE_WAR"->120L;"WAR_BATTLE"->approach(c).cost;"WAR_ENVOY"->60L;"WAR_PEACE"->40L;else->0L}
 fun duration(c:Command)=when(c.type){"WAR_BATTLE"->approach(c).days;"WAR_ENVOY"->21;else->7}
 // Commands queued on an old front must never execute against a later war.
 fun bind(w:World,c:Command)=if(w.war!=null&&c.type in types&&c.type!="DECLARE_WAR"&&c.objective in listOf("财政审计",""))c.copy(objective="campaign:${w.war.campaign}")else c
 fun envoyAvailable(w:World,p:Person)=p.alive&&p.ageQuarters>=72&&p.ability>=40&&!EducationEngine.busy(w,p.id)&&p.id !in w.sovereign.captives&&w.sovereign.foreign.none {it.person==p.id}&&w.familyPeople[w.sovereign.monarchMember]!=p.id&&w.journey?.companion!=p.id&&w.journey?.regent!=p.id&&w.war?.let {f->CrownEngine.units(w).none {it.id==f.army&&it.commander==p.id}}!=false
 fun influence(w:World,p:Person):Int {
  val f=w.war?:return 0;val a=CrownEngine.units(w).first {it.id==f.army}
  return (p.ability/2+f.progress.coerceIn(-40,60)/2+w.intelligence.networks[f.neighbor]/4+f.enemyWeariness/4+if(CrownEngine.power(w,a)>=f.enemy*14L/10)15 else 0).coerceIn(0,100)
 }
 fun describe(w:World,c:Command):String=when(c.type){
  "WAR_BATTLE"->approach(c).let {"${it.label}：完成时消耗${it.supply}补给，胜/负预计损失兵员${it.wonLossPercent}%/${it.lostLossPercent}%；胜+ ${it.victory}优势、负-${it.defeat}。围困较慢但减少正面伤亡；速攻损失与后勤压力更高，不保证获胜。月费另计。"}
  "WAR_ENVOY"->w.people.find {it.id==c.target.toIntOrNull()}?.let {"使者${it.name}，当前议和筹码${influence(w,it)}/100，执行时重算并受已保存种子影响；阈值60。拒绝亦消耗经费并降低优势3；60日内不能再派，出使期间不能另行任用。成功可按现状停火，优势≥60方有边地条件。"}?:"须点选有资格的在世使者；议和不保证成功。"
  "WAR_PEACE"->"须优势≥60或敌方战力耗尽才可要求边地停战。未达条件可出使议和或无条件撤军。"
  else->""
 }
 fun validate(w:World,c:Command):String? {
  val war=w.war
  if(c.type=="DECLARE_WAR"){
   val army=CrownEngine.units(w).find {it.id==c.target}?:return "请选择出征军队。"
   return when {war!=null->"已有战争，须先完成停战。";c.region !in w.neighbors.indices->"请选择目标国家。";!TerrainEngine.profile(w,"neighbor-city-${c.region}").land&&w.towns.sumOf {it.buildings["PORT"]?:0}*20000<army.soldiers->"渡海动员需要足够码头运力，每级码头支持两万人海运。";army.soldiers<1000||army.supply<30||w.people.none {it.id==army.commander&&it.alive&&!EducationEngine.busy(w,it.id)}->"须有在岗主将、至少千人及30补给。";MapSnapshotMapper.state(w).territories.none {it.id=="frontier-${c.region}"&&it.polityId=="neighbor-${c.region}"}->"当前剧情没有该国可争夺的边地。";else->null}
  }
  if(war==null)return "当前没有战争。"
  if(c.objective.startsWith("campaign:")&&c.objective!="campaign:${war.campaign}")return "该命令属于已经结束的战役，不能移用于新战争。"
  if(c.type in setOf("WAR_BATTLE","WAR_ENVOY")&&w.pending.any {it.command.type in setOf("WAR_BATTLE","WAR_ENVOY")})return "已有前线行动或使团在途，请完成或撤销后再下令。"
  if(c.type=="WAR_PEACE"&&war.progress<60&&war.enemy>0)return "尚无强制边地停战条件，请选择出使议和或无条件撤军。"
  if(c.type=="WAR_ENVOY"){
   val p=w.people.find {it.id==c.target.toIntOrNull()}?:return "请选择登记在册的出使人物。"
   return when {war.days<14->"仍在动员，请在14日后议和。";!envoyAvailable(w,p)->"使者须成年在世、能力至少40、未被拘押或外出求学，且不是在位君主、随驾人或前线主将。";w.day-war.lastParley<60->"上次使团归来不足60日，尚不可重复议和。";else->null}
  }
  if(c.type=="WAR_BATTLE"){
   if(war.enemy<=0)return "敌方战力已耗尽，请办理停战，不再制造无对手的交战。"
   if(c.mode !in approaches.map {it.mode}+listOf("","微服私访"))return "未知的战役策略，请点选稳战、速攻或围困。"
   val spec=approach(c);val army=CrownEngine.units(w).first {it.id==war.army}
   if(war.days<14)return "军队尚在动员，须经过14日准备。"
   if(army.soldiers<500||army.supply<maxOf(15,spec.supply)||w.people.none {it.id==army.commander&&it.alive&&!EducationEngine.busy(w,it.id)})return "军队缺乏可用兵员、策略所需补给或在岗主将，请整补或停战。"
   if(spec.mode=="围困"&&(army.soldiers<1500||army.training<30))return "围困须至少1500名兵员及30训练，以维持封锁线。"
  }
  return null
 }
 private fun close(w:World,events:MutableList<String>):World {
  val obsolete=w.pending.filter {it.command.type in types&&it.command.type!="DECLARE_WAR"}
  val refund=obsolete.sumOf {it.escrow*4/5}
  if(obsolete.isNotEmpty())events+="战役结束，撤销${obsolete.size}项未结前线命令；退还预留${refund}万两，准备费不退，不转用于其他战役。"
  return w.copy(war=null,pending=w.pending-obsolete.toSet(),nation=w.nation.copy(treasury=(w.nation.treasury+refund).coerceAtMost(1000000)))
 }
 private fun peace(w:World,relation:Int,events:MutableList<String>):World {
  val war=w.war!!;var scene=MapSnapshotMapper.state(w);val won=war.progress>=60||war.enemy<=0
  if(won)scene=scene.copy(territories=scene.territories.map {if(it.id=="frontier-${war.neighbor}")it.copy(polityId="nation",status="controlled",description="${w.day}日战争停战协议登记移交。")else it},places=scene.places.mapValues {(id,p)->if(id=="neighbor-city-${war.neighbor}")p.copy(polityId="nation")else p})
  events+="与${w.neighbors[war.neighbor].name}停战：${if(won)"按已取得的优势登记边地移交，季度驻防支出5万两"else"按现状撤除战线，疆界不变"}。停止战争月费，对方关系恢复至${relation}。"
  return close(w.copy(cartography=scene,nation=w.nation.copy(morale=(w.nation.morale+3).coerceAtMost(100)),neighbors=w.neighbors.mapIndexed {i,n->if(i==war.neighbor)n.copy(relation=relation)else n}),events)
 }
 fun execute(w:World,c:Command,events:MutableList<String>):World {
  if(c.type=="DECLARE_WAR"){
   val enemy=w.neighbors[c.region];events+="对${enemy.name}宣战，${CrownEngine.units(w).first {it.id==c.target}.name}动员；14日后方能交战。民心-8、稳定-4、对方关系归零，其余国家关系-5。每30日战费40万两加每千人1万两。"
   return w.copy(war=WarFront(c.region,c.target,enemy=enemy.strength*70,campaign="${w.day}-${w.revision}"),nation=w.nation.copy(morale=(w.nation.morale-8).coerceAtLeast(0),stability=(w.nation.stability-4).coerceAtLeast(0)),neighbors=w.neighbors.mapIndexed {i,n->n.copy(relation=if(i==c.region)0 else (n.relation-5).coerceAtLeast(0))})
  }
  val war=w.war!!;val army=CrownEngine.units(w).first {it.id==war.army}
  if(c.type=="WAR_WITHDRAW"){events+="无条件撤军完成：停止战争经费、不获得领土，双方仍保持敌对；民心-3、稳定-3，不退还已发生支出。";return close(w.copy(nation=w.nation.copy(morale=(w.nation.morale-3).coerceAtLeast(0),stability=(w.nation.stability-3).coerceAtLeast(0))),events)}
  if(c.type=="WAR_PEACE")return peace(w,20,events)
  if(c.type=="WAR_ENVOY"){
   val p=w.people.first {it.id==c.target.toIntOrNull()};val leverage=influence(w,p)
   val roll=(Math.floorMod(w.seed+war.battles*31L+w.day*7L+p.id,17L)-8).toInt();val accepted=leverage+roll>=60
   val memory="第${w.day}日出使${w.neighbors[war.neighbor].name}，${if(accepted)"议和获准"else"和议遭拒"}。"
   val next=w.copy(people=w.people.map {if(it.id==p.id)it.copy(memories=(it.memories+memory).takeLast(8),loyalty=(it.loyalty+if(accepted)1 else -1).coerceIn(0,100))else it})
   events+="${p.name}归报：议和筹码${leverage}、情势修正${roll}，${if(accepted)"对方同意议和"else"对方拒绝和议；优势-3，60日内不可再派使团"}；不是输入旨意即可令别国服从。"
   return if(accepted)peace(next,35,events)else next.copy(war=war.copy(lastParley=w.day,progress=(war.progress-3).coerceAtLeast(-100),weariness=(war.weariness+2).coerceAtMost(100)))
  }
  val spec=approach(c);val network=w.intelligence.networks[war.neighbor];val terrain=TerrainEngine.defense(w,war.neighbor);val winter=if(w.day/90%4==3&&(TerrainEngine.reference("neighbor-city-${war.neighbor}")?.lat?:30.0)>=38)10 else 0
  val defense=if(spec.mode=="围困")terrain/2 else terrain
  val power=CrownEngine.power(w,army)*(100-defense-winter)/100*spec.powerPercent/100
  val roll=Math.floorMod(w.seed+war.battles*97L+w.day*11L,41L)-20
  val enemy=(war.enemy*(100-network/3)/100).coerceAtLeast(100);val won=power*(100+roll)/100>=enemy
  val losses=minOf(army.soldiers,(army.soldiers*(if(won)spec.wonLossPercent else spec.lostLossPercent)/100).coerceAtLeast(100))
  val enemyLoss=if(spec.mode=="围困")war.enemy*(if(won)18 else 6)/100 else if(won)war.enemy/4 else war.enemy/10
  val progress=(war.progress+if(won)spec.victory else -spec.defeat).coerceIn(-100,100)
  val updated=army.copy(soldiers=army.soldiers-losses,supply=(army.supply-spec.supply).coerceAtLeast(0),morale=(army.morale+if(won)3 else -6).coerceIn(0,100))
  events+="${spec.label}第${war.battles+1}次${if(won)"得利"else"受阻"}：兵员-${losses}、补给-${spec.supply}、优势${if(won)"+${spec.victory}"else"-${spec.defeat}"}；情报${network}，守方地形${defense}%、严寒${winter}%，敌战力损耗${enemyLoss}。"
  val population=(w.nation.population-losses).coerceAtLeast(100000);val total=w.regions.sumOf {it.population}.coerceAtLeast(1);val shares=w.regions.map {it.population*population/total};val regions=w.regions.mapIndexed {i,r->r.copy(population=shares[i]+if(i==3)population-shares.sum()else 0)}
  var next=w.copy(regions=regions,war=war.copy(battles=war.battles+1,progress=progress,enemy=(war.enemy-enemyLoss).coerceAtLeast(0),lastApproach=spec.mode,enemyWeariness=(war.enemyWeariness+5).coerceAtMost(100)),armies=CrownEngine.units(w).map {if(it.id==army.id)updated else it},nation=w.nation.copy(population=population,morale=(w.nation.morale+if(won)1 else -3).coerceIn(0,100)),neighbors=w.neighbors.mapIndexed {i,n->if(i==war.neighbor)n.copy(strength=(n.strength-if(won)3 else 1).coerceAtLeast(0))else n})
  if(progress<=-75||updated.soldiers<500){events+="军队无法继续战役，被迫撤回停战；稳定-6、民心-5，疆界不变。";next=close(next.copy(nation=next.nation.copy(stability=(next.nation.stability-6).coerceAtLeast(0),morale=(next.nation.morale-5).coerceAtLeast(0))),events)}
  return next
 }
 fun advance(w:World,ledger:MutableList<String>):World {
  val war=w.war?:return w;var next=w.copy(war=war.copy(days=war.days+1));if((war.days+1)%30!=0)return next
  val army=CrownEngine.units(w).first {it.id==war.army};val cost=40+army.soldiers/1000;val cash=w.nation.treasury-cost;val credit=minOf((-cash).coerceAtLeast(0),(2000-w.nation.debt).coerceAtLeast(0));val shortage=cash+credit<0;val moraleLoss=if(shortage)4 else 1+war.weariness/25
  next=next.copy(war=next.war!!.copy(weariness=(war.weariness+5).coerceAtMost(100),enemyWeariness=(war.enemyWeariness+3).coerceAtMost(100)),nation=w.nation.copy(treasury=(cash+credit).coerceAtLeast(0),debt=w.nation.debt+credit,morale=(w.nation.morale-moraleLoss).coerceAtLeast(0)),armies=CrownEngine.units(w).map {if(it.id==army.id)it.copy(supply=(it.supply-if(shortage)10 else 2).coerceAtLeast(0))else it})
  ledger+="战争月费-${cost}万两、借款${credit}，民心-${moraleLoss}、前线补给-${if(shortage)10 else 2}；本国厌战${next.war!!.weariness}，对方厌战${next.war!!.enemyWeariness}。"
  return next
 }
 fun verify(w:World){w.war?.let {require(it.neighbor in 0..2&&CrownEngine.units(w).any {u->u.id==it.army}&&it.days in 0..900090&&it.battles in 0..100000&&it.progress in -100..100&&it.enemy in 0..10000&&it.weariness in 0..100&&it.enemyWeariness in 0..100&&it.lastParley in -360..900090&&it.campaign.matches(Regex("[a-z0-9-]{1,64}"))&&it.lastApproach in approaches.map {a->a.mode})}}
}
