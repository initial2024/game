package cn.chronicle.core

import kotlinx.serialization.Serializable

@Serializable data class ProductionRecord(val grain:Long=0,val timber:Long=0,val iron:Long=0,val coal:Long=0,val stone:Long=0,val goods:Long=0,val operation:Int=100)
@Serializable data class ResourceState(val initialized:Boolean=false,val stocks:Map<String,Long> = emptyMap(),val sites:Map<String,SiteResources> = emptyMap(),val landPools:List<Long> = emptyList(),val lastTurn:Int=-1,val yields:Map<String,ProductionRecord> = emptyMap(),val totals:ProductionRecord=ProductionRecord(),val militaryFoodRatio:Int=100,val grainTaxValueWan:Long=0,val plants:List<Int> = listOf(0,0,0,0),val industrialTaxBaseLoss:Long=0,val tradedLots:Map<String,Int> = emptyMap(),val relief:Long=0,val foodAvailable:Long=0)
object ResourceEngine {
 val labels=linkedMapOf("GRAIN" to "粮食","TIMBER" to "木材","STONE" to "石料","IRON" to "铁材","COAL" to "煤炭")
 fun unit(kind:String)=if(kind=="GRAIN")"石"else"吨"
 fun grainCapacity(w:World)=w.towns.sumOf {(it.buildings["GRANARY"]?:0)*20000L}.coerceAtLeast(20000)
 fun capacity(w:World,kind:String)=if(kind=="GRAIN")grainCapacity(w)else 20000000L
 fun available(w:World,kind:String)=w.resources.stocks[kind]?:0L
 fun requirements(w:World,c:Command):Map<String,Long> {
  val level=c.level.coerceAtLeast(1).toLong()
  return when(c.type){
   "URBAN_BUILD"->when(c.objective){"WORKSHOP","MINE"->mapOf("IRON" to 10*level,"TIMBER" to 15*level,"STONE" to 20*level);"ROAD","PORT"->mapOf("STONE" to 25*level,"TIMBER" to 10*level);else->mapOf("TIMBER" to 10*level,"STONE" to 10*level)}
   "FACTORY"->mapOf("IRON" to 40L,"TIMBER" to 50L,"STONE" to 60L)
   "WORKSHOP"->mapOf("IRON" to 15L,"TIMBER" to 40L,"STONE" to 40L)
   "RELIEF"->mapOf("GRAIN" to 50000L)
   "PALACE_BUILD"->mapOf("IRON" to 20*level,"TIMBER" to 100*level,"STONE" to 100*level)
   "FARM","ROAD"->mapOf("STONE" to 25L,"TIMBER" to 15L)
   "CANAL"->mapOf("IRON" to 20L,"TIMBER" to 60L,"STONE" to 80L)
   "ARMY_RECRUIT"->mapOf("IRON" to 20L,"TIMBER" to 20L,"GRAIN" to 1000L)
   "LOCAL_RECRUIT"->mapOf("IRON" to (c.level/50).toLong(),"TIMBER" to (c.level/50).toLong(),"GRAIN" to c.level.toLong())
   "DECLARE_WAR"->{val army=CrownEngine.units(w).find {it.id==c.target};if(army==null)emptyMap()else mapOf("GRAIN" to army.soldiers.toLong(),"IRON" to 10L,"TIMBER" to if(!TerrainEngine.profile(w,"neighbor-city-${c.region}").land)80L else 20L)}
   "WAR_BATTLE"->{val army=w.war?.let {f->CrownEngine.units(w).first {it.id==f.army}};if(army==null)emptyMap()else WarfareEngine.approach(c).let {a->mapOf("GRAIN" to army.soldiers.toLong()*a.grainParts/4,"IRON" to a.iron,"TIMBER" to a.timber)}}
   else->emptyMap()
  }
 }
 val lots=mapOf("GRAIN" to 100000L,"TIMBER" to 100L,"STONE" to 200L,"IRON" to 50L,"COAL" to 150L)
 fun tradeValidate(w:World,c:Command):String?=when {c.objective !in lots||c.target !in listOf("采购","出售")||c.level !in 1..5->"请选择物资、方向和1–5批次。";(w.resources.tradedLots[c.objective]?:0)+c.level>5->"本季该物资商路限额为5批。";w.neighbors.withIndex().none {it.value.relation>=30&&w.war?.neighbor!=it.index}->"没有可通商的邻国，须改善外交或停战。";c.target=="出售"&&available(w,c.objective)<lots.getValue(c.objective)*c.level->"库存不足以出售。";c.target=="采购"&&available(w,c.objective)+lots.getValue(c.objective)*c.level>capacity(w,c.objective)->"采购后超过仓储容量，请减少批次、使用库存或扩建常平仓。";else->null}
 fun trade(w:World,c:Command,events:MutableList<String>):World {val quantity=lots.getValue(c.objective)*c.level;val buying=c.target=="采购";events+="通过商队${c.target}${labels[c.objective]}${quantity}${unit(c.objective)}，${if(buying)"采购含运输费，每批2万两"else"每批收入1万两"}；记入本季商路限额，不改变本国矿藏储量。";return w.copy(nation=if(buying)w.nation else w.nation.copy(treasury=(w.nation.treasury+c.level).coerceAtMost(1000000)),resources=w.resources.copy(stocks=w.resources.stocks+(c.objective to (available(w,c.objective)+if(buying)quantity else -quantity)),tradedLots=w.resources.tradedLots+(c.objective to ((w.resources.tradedLots[c.objective]?:0)+c.level))))}
 fun validate(w:World,c:Command):String? {
  if(!w.resources.initialized)return null
  for((kind,amount)in requirements(w,c))if(available(w,kind)<amount)return "${labels[kind]}不足：需$amount${unit(kind)}，库存${available(w,kind)}。可生产、调配或待恢复后再行动。"
  return null
 }
 fun consume(w:World,c:Command,ledger:MutableList<String>):World {if(!w.resources.initialized)return w;val need=requirements(w,c);if(need.isEmpty())return w;ledger+="${Rules.label(c)}物资支出："+need.entries.joinToString {"${labels[it.key]}-${it.value}${unit(it.key)}"};return w.copy(resources=w.resources.copy(stocks=w.resources.stocks.mapValues {(key,value)->value-(need[key]?:0L)},relief=w.resources.relief+if(c.type=="RELIEF")50000L else 0L))}
 fun quarter(w:World,ledger:MutableList<String>,events:MutableList<String>):World {
  if(!w.resources.initialized||w.resources.lastTurn==w.turn)return w
  val stocks=w.resources.stocks.toMutableMap();val sites=w.resources.sites.toMutableMap();val yields=linkedMapOf<String,ProductionRecord>();val weights=w.towns.associate {t->val p=sites.getValue(t.id).terrain;t.id to ((if(t.rank=="县城")1L else 3L)*(p.fertility+30))}
  val sums=w.regions.indices.associateWith {r->w.towns.filter {it.region==r}.sumOf {weights.getValue(it.id)}}
  var grain=0L;var timber=0L;var iron=0L;var coal=0L;var stone=0L;var goods=0L
  for(t in w.towns){val s=sites.getValue(t.id);val p=s.terrain;val irrig=t.buildings["IRRIGATION"]?:0;val farmEfficiency=(20+MunicipalEngine.stats(t).getValue("农业")+w.nation.agriculture/2+p.water/3+irrig*8+w.nation.technology/10).coerceIn(15,180)
   val land=w.resources.landPools[t.region]*weights.getValue(t.id)/sums.getValue(t.region).coerceAtLeast(1);val harvest=land*farmEfficiency/100
   val logistics=(40+(t.buildings["ROAD"]?:0)*10+(t.buildings["PORT"]?:0)*10+(if(w.canals.any {it.from==t.id||it.to==t.id})15 else 0)+w.nation.infrastructure/5+(t.security-50)/5).coerceIn(30,100)
   val mine=t.buildings["MINE"]?:0;val logging=t.buildings["SAWMILL"]?:0
   val ironYield=minOf(s.iron,mine*100L*p.iron/50*logistics/100);val coalYield=minOf(s.coal,mine*160L*p.coal/50*logistics/100);val stoneYield=minOf(s.stone,mine*300L*p.stone/50*logistics/100)
   val logYield=minOf(s.forest,logging*150L*p.timber/50*logistics/100)
   val extra=MunicipalEngine.extra(t,"WORKSHOP");val desiredIron=extra*5L;val desiredCoal=extra*(if(w.setting==EraRules.ANCIENT)0L else 10L);val desiredWood=extra*(if(w.setting==EraRules.ANCIENT)10L else 0L)
   stocks["IRON"]=stocks.getValue("IRON")+ironYield;stocks["COAL"]=stocks.getValue("COAL")+coalYield;stocks["STONE"]=stocks.getValue("STONE")+stoneYield;stocks["TIMBER"]=stocks.getValue("TIMBER")+logYield
   val ratios=listOf("IRON" to desiredIron,"COAL" to desiredCoal,"TIMBER" to desiredWood).filter {it.second>0}.map {(k,n)->(stocks.getValue(k)*100/n).coerceAtMost(100).toInt()};val ratio=((ratios.minOrNull()?:100)*logistics/100)
   for((k,n)in listOf("IRON" to desiredIron,"COAL" to desiredCoal,"TIMBER" to desiredWood))stocks[k]=stocks.getValue(k)-n*ratio/100
   val products=extra*120L*ratio/100;val regrowth=minOf((s.forestCapacity-(s.forest-logYield)).coerceAtLeast(0),s.forestCapacity/200)
   sites[t.id]=s.copy(iron=s.iron-ironYield,coal=s.coal-coalYield,stone=s.stone-stoneYield,forest=s.forest-logYield+regrowth)
   yields[t.id]=ProductionRecord(harvest,logYield,ironYield,coalYield,stoneYield,products,ratio);grain+=harvest;timber+=logYield;iron+=ironYield;coal+=coalYield;stone+=stoneYield;goods+=products
  }
  var industryLoss=0L
  for(count in w.resources.plants){val ironNeed=count*20L;val fuelKind=if(w.setting==EraRules.ANCIENT)"TIMBER"else"COAL";val fuelNeed=count*30L;val ratios=listOf("IRON" to ironNeed,fuelKind to fuelNeed).filter {it.second>0}.map {(k,n)->(stocks.getValue(k)*100/n).coerceAtMost(100)};val ratio=ratios.minOrNull()?:100;stocks["IRON"]=stocks.getValue("IRON")-ironNeed*ratio/100;stocks[fuelKind]=stocks.getValue(fuelKind)-fuelNeed*ratio/100;goods+=count*240L*ratio/100*(if(w.government.laborSafety)92 else 100)/100;industryLoss+=count*18L*(100-ratio)/100}
  val grainTax=grain*w.nation.tax/200;stocks["GRAIN"]=stocks.getValue("GRAIN")+grainTax
  val foodNeed=LocalDefenseEngine.troops(w)*4L/3;val supplied=minOf(stocks.getValue("GRAIN"),foodNeed);stocks["GRAIN"]=stocks.getValue("GRAIN")-supplied;val foodRatio=if(foodNeed==0L)100 else (supplied*100/foodNeed).toInt()
  if(foodRatio<100)events+="军粮供给仅达${foodRatio}%，中央军和地方军补给、士气受影响。"
  ledger+="季度物资产出：粮${grain}石、木材${timber}吨、铁${iron}吨、煤${coal}吨、石料${stone}吨、扩建作坊制品${goods}箱。"
  ledger+="田赋折粮${grainTax}石（折${grainTax/100000}万两，从现金税中扣除，避免重复收税）；军粮消耗${supplied}石，应需${foodNeed}石。矿藏按产量扣减，森林按容量缓慢再生。"
  val cap=mapOf("GRAIN" to grainCapacity(w),"TIMBER" to 20000000L,"IRON" to 20000000L,"COAL" to 20000000L,"STONE" to 20000000L)
  for((k,v)in stocks)if(v>cap.getValue(k))ledger+="${labels[k]}超过仓储容量，超出${v-cap.getValue(k)}${unit(k)}按损耗处理。"
  return w.copy(resources=w.resources.copy(stocks=stocks.mapValues {(k,v)->v.coerceIn(0,cap.getValue(k))},sites=sites,lastTurn=w.turn,yields=yields,totals=ProductionRecord(grain,timber,iron,coal,stone,goods),militaryFoodRatio=foodRatio,grainTaxValueWan=grainTax/100000,industrialTaxBaseLoss=industryLoss,tradedLots=emptyMap(),foodAvailable=grain-grainTax+supplied+w.resources.relief,relief=0))
 }
 fun verify(w:World){val s=w.resources;if(!s.initialized)return;require(s.landPools.size==4&&s.landPools.all {it in 0..200000000L}&&s.stocks.keys==labels.keys&&s.stocks.all {(k,v)->v in 0..if(k=="GRAIN")500000000L else 20000000L}&&s.sites.size<=200&&s.militaryFoodRatio in 0..100);s.sites.forEach {(id,r)->require(w.towns.any {it.id==id});require(listOf(r.terrain.height,r.terrain.slope,r.terrain.water,r.terrain.fertility,r.terrain.timber,r.terrain.iron,r.terrain.coal,r.terrain.stone).all {it in 0..100});require(listOf(r.iron,r.coal,r.stone,r.forest,r.forestCapacity).all {it in 0..1000000L}&&r.forest<=r.forestCapacity)};require(s.lastTurn in -1..w.turn&&s.yields.size<=200)}
}
