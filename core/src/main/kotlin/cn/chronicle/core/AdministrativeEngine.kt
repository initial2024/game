package cn.chronicle.core

import kotlin.math.cos
import kotlin.math.sin

/** New fictional counties extend the supplied atlas; the supplied nodes and terrain remain intact. */
object AdministrativeEngine {
 val extraSeeds:List<TownSeed> = townSeeds.filter {it.rank=="府城"}.flatMap {p->(0..7).map {i->val angle=i*Math.PI/4;TownSeed("extra-county-${p.prefecture}-$i",p.prefecture,p.administration.removeSuffix("府")+listOf("东原","清溪","南陵","平川","西亭","永宁","北岑","安丰")[i]+"县",p.region,"县城",p.lon+cos(angle)*(.42+i%2*.12),p.lat+sin(angle)*(.38+i%2*.12),p.administration)}}
 val seeds get()=townSeeds+extraSeeds
 fun state(p:TownSeed):String {val prefs=townSeeds.filter {it.rank=="府城"&&it.region==p.region};return "state-${p.region}-${prefs.indexOfFirst {it.prefecture==p.prefecture}.coerceAtLeast(0)/2}"}
 fun stateName(w:World,id:String):String {val parts=id.split('-');return w.regions[parts[1].toInt()].name+if(parts[2]=="0")"东州"else"西州"}
 fun expand(w:World):World {
  if(w.towns.isEmpty())return w
  val missing=extraSeeds.filter {s->w.towns.none {it.id==s.id}};if(missing.isEmpty())return w
  val people=w.people.toMutableList();var next=(people.maxOfOrNull {it.id}?:-1)+1
  val towns=missing.mapIndexed {i,s->val id=if(people.size<960)next++ else -1
   if(id>=0)people+=Person(id,"${listOf("尹","梁","傅","薛","邵","范","陆")[i%7]}${listOf("知远","守民","文澄","明礼","怀正","子安","绍平","闻溪")[(i/7+i)%8]}","${s.name}县令",i%3,100+i%96,45+i%36,50+i%27,"依法施政","治理${s.name}")
   MunicipalEngine.template(s.id,s.name,s.prefecture,s.region,s.rank,id,w.seed,w.regions[s.region]).copy(staffed=false)
  }
  return TalentEngine.staff(w.copy(towns=w.towns+towns,people=people))
 }
}
