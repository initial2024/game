package cn.chronicle.core

data class MapOrderTarget(val id:String,val name:String,val polity:String,val region:Int?=null,val neighbor:Int?=null)

/** Read-only selection resolution. Native confirmation and Rules own every effect. */
object MapOrders {
 fun target(w:World,kind:String,id:String):MapOrderTarget {
  val s=MapSnapshotMapper.state(w)
  val owner:String;val name:String;val region:Int?
  when(kind){
   "place-select"->{val p=s.places[id]?:error("地图城邑已不存在")
    require(s.placeStates[id]?.status!="hidden"){"该地点不可见"}
    owner=p.polityId;name=p.name;region=MapSnapshotMapper.regionId(w,id)
   }
   "territory-select"->{val t=s.territories.firstOrNull {it.id==id}?:error("地图领地已不存在")
    owner=t.polityId;name=t.name;region=MapSnapshotMapper.regionForGroup(w,t.groupId)
   }
   "polity-select"->{val p=s.polities.firstOrNull {it.id==id}?:error("地图势力已不存在")
    owner=p.id;name=p.name;region=null
   }
   else->error("地图只可选择已登记城邑、领地或势力")
  }
  val neighbor=w.neighbors.indices.firstOrNull {owner=="neighbor-$it"}
  return MapOrderTarget(id,name,owner,if(owner=="nation")region else null,neighbor)
 }
 fun military(w:World,t:MapOrderTarget,army:String):Command {
  require(CrownEngine.units(w).any {it.id==army}){"军队已不存在，请重新选择"}
  return when {
   t.neighbor!=null&&w.war?.neighbor==t.neighbor->{require(w.war.army==army){"该战线已有出征军，请选用前线部队"};WarfareEngine.bind(w,Command("WAR_BATTLE",mode="稳战"))}
   t.neighbor!=null->Command("DECLARE_WAR",region=t.neighbor,target=army)
   t.polity=="nation"&&t.region!=null->Command("ARMY_MOVE",region=t.region,target=army)
   else->error("请点选已划入内政区的城邑或外国目标；整个本国与未划区边地不能作为本营驻地")
  }
 }
 fun diplomacy(t:MapOrderTarget):Command {
  val n=t.neighbor?:error("请点选外国势力或仍归该国的城邑、边地")
  return Command("DIPLOMACY_TARGET",region=n,target="neighbor-$n")
 }
}
