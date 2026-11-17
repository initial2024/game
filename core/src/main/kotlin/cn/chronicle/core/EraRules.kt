package cn.chronicle.core

object EraRules {
 const val ANCIENT="古代王朝"
 fun industry(w:World)=if(w.setting==ANCIENT)"手工业" else "工业"
 fun factory(w:World)=if(w.setting==ANCIENT)"WORKSHOP" else "FACTORY"
 fun render(text:String,setting:String):String = if(setting!=ANCIENT)text else text
  .replace("工业排污","染坊废水").replace("运输制约煤炭供应","驿运受阻影响炭料供应").replace("工厂吸引新工人","官坊招募匠户")
  .replace("研究院发表成果","太学呈报格物成果").replace("机具改良","农具与织机改良")
  .replace("工业强国","商工兴国").replace("工厂","官营作坊").replace(Regex("(?<!手)工业"),"手工业").replace("科技","格物")
  .replace("资助研究","资助格物").replace("资助科研","资助格物").replace("修筑道路","整修驿道").replace("研究带动","格物带动")
  .replace("铁路","驿道").replace("岚海联邦","岚海国").replace("白榆公国","白榆国")
 fun label(c:Command,setting:String)=render(Rules.label(c),setting)
 fun parse(text:String,w:World):Preview {
  if(text.contains("运河"))return Preview(emptyList(),"请在朝政→建设→运河点选两个城邑并预览工期、费用，不会将水运工程替换为普通道路。",true)
  if(w.setting!=ANCIENT)return IntentParser.parse(text)
  if(listOf("蒸汽","铁路","电力","火车","机枪","飞机").any(text::contains))return Preview(emptyList(),"当前是纯古代王朝，不能直接建设近现代设施。可兴修驿道、营造作坊或资助格物。",true)
  val p=IntentParser.parse(text.replace("作坊","工厂").replace("格物","科研").replace("驿道","基建"))
  return p.copy(commands=p.commands.map {if(it.type=="FACTORY")it.copy(type="WORKSHOP")else it},description=render(p.description,w.setting))
 }
}
