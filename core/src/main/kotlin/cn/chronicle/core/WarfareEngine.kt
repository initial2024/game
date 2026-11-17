package cn.chronicle.core

import kotlinx.serialization.Serializable

@Serializable data class WarFront(val neighbor:Int,val army:String,val days:Int=0,val battles:Int=0,val progress:Int=0,val enemy:Int,val weariness:Int=0)
object WarfareEngine {
 val types=setOf("DECLARE_WAR","WAR_BATTLE","WAR_PEACE","WAR_WITHDRAW")
 fun validate(w:World,c:Command):String? {
  val war=w.war
  if(c.type=="DECLARE_WAR"){
   val army=CrownEngine.units(w).find {it.id==c.target}?:return "请选择出征军队。"
   return when {war!=null->"已有战争，须先完成停战。";c.region !in w.neighbors.indices->"请选择目标国家。";!TerrainEngine.profile(w,"neighbor-city-${c.region}").land&&w.towns.sumOf {it.buildings["PORT"]?:0}*20000<army.soldiers->"渡海动员需要足够码头运力，每级码头支持两万人海运。";army.soldiers<1000||army.supply<30||w.people.none {it.id==army.commander&&it.alive&&!EducationEngine.busy(w,it.id)}->"须有在岗主将、至少千人及30补给。";MapSnapshotMapper.state(w).territories.none {it.id=="frontier-${c.region}"&&it.polityId=="neighbor-${c.region}"}->"当前剧情没有该国可争夺的边地。";else->null}
  }
  if(war==null)return "当前没有战争。"
  if(c.type=="WAR_BATTLE"){
   val army=CrownEngine.units(w).first {it.id==war.army}
   if(war.days<14)return "军队尚在动员，须经过14日准备。"
   if(army.soldiers<500||army.supply<15||w.people.none {it.id==army.commander&&it.alive&&!EducationEngine.busy(w,it.id)})return "军队缺乏可用兵员、补给或在岗主将，请整补或停战。"
  }
  return null
 }
 fun execute(w:World,c:Command,events:MutableList<String>):World {
  if(c.type=="DECLARE_WAR"){
   val enemy=w.neighbors[c.region];events+="对${enemy.name}宣战，${CrownEngine.units(w).first {it.id==c.target}.name}动员；14日后方能交战。民心-8、稳定-4、对方关系归零，其余国家关系-5。每30日战费40万两加每千人1万两。"
   return w.copy(war=WarFront(c.region,c.target,enemy=enemy.strength*70),nation=w.nation.copy(morale=(w.nation.morale-8).coerceAtLeast(0),stability=(w.nation.stability-4).coerceAtLeast(0)),neighbors=w.neighbors.mapIndexed {i,n->n.copy(relation=if(i==c.region)0 else (n.relation-5).coerceAtLeast(0))})
  }
  val war=w.war!!;val army=CrownEngine.units(w).first {it.id==war.army}
  if(c.type=="WAR_WITHDRAW"){events+="无条件撤军完成：停止战争经费、不获得领土，双方仍保持敌对；民心-3、稳定-3，不退还已发生支出。";return w.copy(war=null,nation=w.nation.copy(morale=(w.nation.morale-3).coerceAtLeast(0),stability=(w.nation.stability-3).coerceAtLeast(0)))}
  if(c.type=="WAR_PEACE"){
   var scene=MapSnapshotMapper.state(w);val won=war.progress>=60||war.enemy<=0
   if(won)scene=scene.copy(territories=scene.territories.map {if(it.id=="frontier-${war.neighbor}")it.copy(polityId="nation",status="controlled",description="${w.day}日战争停战协议登记移交。")else it},places=scene.places.mapValues {(id,p)->if(id=="neighbor-city-${war.neighbor}")p.copy(polityId="nation")else p})
   events+="与${w.neighbors[war.neighbor].name}达成停战：${if(won)"优势达到边地移交门槛，地图归属已登记变化"else"未达到领土移交门槛，维持原有疆界"}。民心+3、对方关系恢复至20，停止战争经费。"
   return w.copy(war=null,cartography=scene,nation=w.nation.copy(morale=(w.nation.morale+3).coerceAtMost(100)),neighbors=w.neighbors.mapIndexed {i,n->if(i==war.neighbor)n.copy(relation=20)else n})
  }
  val network=w.intelligence.networks[war.neighbor];val terrain=TerrainEngine.defense(w,war.neighbor);val winter=if(w.day/90%4==3&&(TerrainEngine.reference("neighbor-city-${war.neighbor}")?.lat?:30.0)>=38)10 else 0;val power=CrownEngine.power(w,army)*(100-terrain-winter)/100;val roll=Math.floorMod(w.seed+war.battles*97L+w.day*11L,41L)-20
  val enemy=(war.enemy*(100-network/3)/100).coerceAtLeast(100);val won=power*(100+roll)/100>=enemy
  val losses=minOf(army.soldiers,(army.soldiers*(if(won)7 else 14)/100).coerceAtLeast(100));val enemyLoss=if(won)war.enemy/4 else war.enemy/10
  val progress=(war.progress+if(won)25 else -25).coerceIn(-100,100)
  val updated=army.copy(soldiers=army.soldiers-losses,supply=(army.supply-6).coerceAtLeast(0),morale=(army.morale+if(won)3 else -6).coerceIn(0,100))
  events+="与${w.neighbors[war.neighbor].name}第${war.battles+1}次交战${if(won)"获胜"else"失利"}：兵员-$losses、补给-6、战线优势${if(won)"+25"else"-25"}；情报网络${network}参与信息优势计算，守方地形加成${terrain}%、严寒影响${winter}%。敌方战力损耗$enemyLoss。"
  val population=(w.nation.population-losses).coerceAtLeast(100000);val total=w.regions.sumOf {it.population}.coerceAtLeast(1);val shares=w.regions.map {it.population*population/total};val regions=w.regions.mapIndexed {i,r->r.copy(population=shares[i]+if(i==3)population-shares.sum()else 0)}
  var next=w.copy(regions=regions,war=war.copy(battles=war.battles+1,progress=progress,enemy=(war.enemy-enemyLoss).coerceAtLeast(0)),armies=CrownEngine.units(w).map {if(it.id==army.id)updated else it},nation=w.nation.copy(population=population,morale=(w.nation.morale+if(won)1 else -3).coerceIn(0,100)),neighbors=w.neighbors.mapIndexed {i,n->if(i==war.neighbor)n.copy(strength=(n.strength-if(won)3 else 1).coerceAtLeast(0))else n})
  if(progress<=-75||updated.soldiers<500){events+="军队无法继续战役，被迫撤回并停战；稳定-6、民心-5，原疆界不变。";next=next.copy(war=null,nation=next.nation.copy(stability=(next.nation.stability-6).coerceAtLeast(0),morale=(next.nation.morale-5).coerceAtLeast(0)))}
  return next
 }
 fun advance(w:World,ledger:MutableList<String>):World {
  val war=w.war?:return w;var next=w.copy(war=war.copy(days=war.days+1));if((war.days+1)%30!=0)return next
  val army=CrownEngine.units(w).first {it.id==war.army};val cost=40+army.soldiers/1000;val cash=w.nation.treasury-cost;val credit=minOf((-cash).coerceAtLeast(0),(2000-w.nation.debt).coerceAtLeast(0));val shortage=cash+credit<0;val moraleLoss=if(shortage)4 else 1+war.weariness/25
  next=next.copy(war=next.war!!.copy(weariness=(war.weariness+5).coerceAtMost(100)),nation=w.nation.copy(treasury=(cash+credit).coerceAtLeast(0),debt=w.nation.debt+credit,morale=(w.nation.morale-moraleLoss).coerceAtLeast(0)),armies=CrownEngine.units(w).map {if(it.id==army.id)it.copy(supply=(it.supply-if(shortage)10 else 2).coerceAtLeast(0))else it})
  ledger+="战争月费-${cost}万两、借款$credit，民心-${moraleLoss}、前线补给-${if(shortage)10 else 2}；厌战${next.war!!.weariness}。"
  return next
 }
 fun verify(w:World){w.war?.let {require(it.neighbor in 0..2&&CrownEngine.units(w).any {u->u.id==it.army}&&it.days in 0..900090&&it.battles in 0..100000&&it.progress in -100..100&&it.enemy in 0..10000&&it.weariness in 0..100)}}
}
