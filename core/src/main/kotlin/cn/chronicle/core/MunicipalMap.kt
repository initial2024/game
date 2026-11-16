package cn.chronicle.core

import kotlin.math.abs

object MunicipalMap {
 private val prefectures get()=townSeeds.filter {it.rank=="府城"}
 // Deterministic administrative schematic cells, never presented as surveyed or historical borders.
 private fun cells():List<List<List<Double>>> {
  val sites=prefectures.map {it.lon to it.lat}+listOf(123.0 to 33.0,104.0 to 46.0,119.0 to 40.0)
  return sites.mapIndexed {index,a->
   var ring=listOf(listOf(96.0,18.0),listOf(128.0,18.0),listOf(128.0,51.0),listOf(96.0,51.0))
   sites.forEachIndexed {j,b->if(j!=index){
    val dx=b.first-a.first;val dy=b.second-a.second;val k=(b.first*b.first+b.second*b.second-a.first*a.first-a.second*a.second)/2
    val output=mutableListOf<List<Double>>()
    ring.indices.forEach {i->val p=ring[i];val q=ring[(i+1)%ring.size];val fp=p[0]*dx+p[1]*dy-k;val fq=q[0]*dx+q[1]*dy-k
     if(fp<=1e-9)output+=p
     if((fp<0)!=(fq<0)&&abs(fp-fq)>1e-12){val t=fp/(fp-fq);output+=listOf(p[0]+(q[0]-p[0])*t,p[1]+(q[1]-p[1])*t)}
    }
    ring=output.filterIndexed {i,p->i==0||abs(p[0]-output[i-1][0])+abs(p[1]-output[i-1][1])>1e-8}
    if(ring.size>1&&abs(ring.first()[0]-ring.last()[0])+abs(ring.first()[1]-ring.last()[1])<1e-8)ring=ring.dropLast(1)
   }}
   ring
  }
 }
 private val rings by lazy(::cells)
 fun expand(w:World,s:MapScenario):MapScenario {
  if(s.geography.kind=="custom"||s.extraPlaces.none {it.id=="city-r0"})return s
  val towns=MunicipalEngine.all(w);if(towns.isEmpty())return s
  val placeAdd=towns.associate {t->t.id to MapPlace(t.name,t.rank,"人口、地方官、财政与城内建筑均可从原生档案查看。",if(t.rank=="县城")"admin-${t.id}"else if(t.prefecture.isNotEmpty())"pref-${t.prefecture}"else"region-${t.region}","nation")}
  val groupAdd=prefectures.map {p->MapGroup("pref-${p.prefecture}",p.administration,"府","region-${p.region}","nation",listOf(p.id))}+towns.filter {it.rank=="县城"}.map {p->MapGroup("admin-${p.id}",p.name,"县",if(p.prefecture.isEmpty())"region-${p.region}"else"pref-${p.prefecture}","nation",listOf(p.id))}
  val places=placeAdd+s.places
  val groups=(s.groups+groupAdd.filter {new->s.groups.none {it.id==new.id}}).map {g->if(g.id.startsWith("region-"))g.copy(rank="大区")else g}
  val links=townSeeds.filter {it.rank=="县城"}.map {p->val head=prefectures.first {it.prefecture==p.prefecture};MapRoute("county-route-${p.id}","府县驿路",head.id,p.id)}
  val generated=prefectures.mapIndexed {i,p->MapTerritory("prefecture-${p.prefecture}",p.administration,"府","nation","pref-${p.prefecture}",polygon=rings[i],description="依据原图节点生成的府域治理示意，不是核验过的历史府县边界。") }+w.neighbors.mapIndexed {i,n->MapTerritory("frontier-$i",n.name+"边地",polityId="neighbor-$i",polygon=rings[prefectures.size+i])}
  val old=s.territories.associateBy {it.id}
  val territory=generated.map {t->old[t.id]?.let {previous->if(MapSnapshotMapper.legacyBoundary(previous))t.copy(polityId=previous.polityId,status=previous.status,description=previous.description.ifBlank {t.description})else previous}?:t}+s.territories.filter {t->generated.none {it.id==t.id}&&!MapSnapshotMapper.legacyBoundary(t)}
  return s.copy(places=places,groups=groups,placeStates=towns.associate {it.id to MapPlaceState()}+s.placeStates,territories=territory,routes=s.routes+links.filter {new->s.routes.none {it.id==new.id}})
 }
}
