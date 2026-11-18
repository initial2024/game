package cn.chronicle.game

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebSettingsCompat
import cn.chronicle.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import java.io.ByteArrayInputStream

@Serializable data class MapHostMessage(val type:String,val sceneId:String="",val placeId:String="",val territoryId:String="",val polityId:String="")
object MapBridgePolicy {
 const val ORIGIN="https://appassets.androidplatform.net"
 const val PAGE="$ORIGIN/assets/maps/chronicle_world_map.html"
 fun trusted(url:String?)=url?.substringBefore('?')==PAGE
 fun selection(text:String,world:World,slot:Int):MapHostMessage {
  require(text.length<=1024)
  val m=SaveJson.decodeFromString<MapHostMessage>(text);require(m.sceneId=="game-slot-$slot")
  val s=MapSnapshotMapper.scene(world,slot)
  when(m.type){
   "place-select" -> require(m.territoryId.isEmpty()&&m.polityId.isEmpty()&&s.places.containsKey(m.placeId)&&s.placeStates[m.placeId]?.status!="hidden")
   "territory-select" -> require(m.placeId.isEmpty()&&m.polityId.isEmpty()&&s.territories.any {it.id==m.territoryId})
   "polity-select" -> require(m.placeId.isEmpty()&&m.territoryId.isEmpty()&&s.polities.any {it.id==m.polityId})
   else -> error("地图没有业务命令权限")
  }
  return m
 }
 fun quoted(text:String)=JSONObject.quote(text).replace("\u2028","\\u2028").replace("\u2029","\\u2029")
}

// A single owner for each WebView. No Activity/renderer lives in a singleton.
class MapWebController(private val web:WebView,private val editor:Boolean,private val error:(String)->Unit,private val selected:(String)->Unit,private val fileRequest:(Boolean)->Unit) {
 private val dispatcher=MapDeltaDispatcher()
 private var snapshot:MapScenario?=null
 private var loaded=false
 private var pageLoaded=false
 private var editorPainted=false
 private var requestToken=0L
 private var expectedScene=""
 private var expectedVisual=-1L
 private var visualQueued=false
 var readySceneId:String="";private set
 private var sending=false
 private var destroyed=false
 val isEditor:Boolean get()=editor
 val isPageReady:Boolean get()=loaded&&!destroyed&&(if(editor)editorPainted else readyRevision>=0)
 @Volatile var readyRevision:Long=-1;private set
 var rejectedMessages:Int=0;private set
 fun update(world:World,slot:Int){if(editor||destroyed)return;snapshot=MapSnapshotMapper.scene(world,slot);flush()}
 fun pageReady(){if(destroyed)return;pageLoaded=true;loaded=true;dispatcher.reset();flush()}
 private fun flush(){
  if(editor||!loaded||sending||destroyed)return
  val scene=snapshot?:return;val d=dispatcher.next(scene)?:return;sending=true;visualQueued=false;expectedScene=scene.id;expectedVisual=scene.revision;val token=++requestToken
  val js="(function(){try{if(ChronicleMap.getSchema()!==${MapBridgePolicy.quoted(MAP_SCHEMA)})throw Error('schema');ChronicleMap.${d.method}(JSON.parse(${MapBridgePolicy.quoted(d.json)}));${if(d.method=="applyScenario")"window.dispatchEvent(new Event('chronicle-map:native-scene'));" else ""}return {ok:true,revision:ChronicleMap.exportScenario().revision};}catch(e){return {ok:false,error:String(e)};}})()"
  web.evaluateJavascript(js){raw->
   if(destroyed)return@evaluateJavascript
   val result=runCatching {JSONObject(raw)}.getOrNull()
   if(result?.optBoolean("ok")!=true){sending=false;dispatcher.reset();loaded=false;error("地图未载入：${result?.optString("error")?:"渲染进程不可用"}。可返回后重开。")}
  }
  web.postDelayed({if(!destroyed&&sending&&requestToken==token){sending=false;dispatcher.reset();loaded=false;error("地图绘制尚未完成，请返回后重开；可信存档已保留。")}},12000)
 }
 private fun rendered(id:String,revision:Long){
  if(destroyed||!pageLoaded)return
  if(!editor&&(id!=expectedScene||revision!=expectedVisual||!sending||visualQueued))return
  if(editor&&!MapValidation.id(id))return
  visualQueued=true;val token=requestToken
  web.postVisualStateCallback(revision,object:WebView.VisualStateCallback(){override fun onComplete(requestId:Long){
   if(destroyed||(!editor&&token!=requestToken))return
   web.invalidate();(web.parent as? android.view.View)?.invalidate()
   web.postOnAnimation {web.postOnAnimation {
    if(!destroyed&&(editor||token==requestToken)){editorPainted=editor;readySceneId=id;readyRevision=revision;sending=false;visualQueued=false;flush()}
   }}
  }})
 }
 fun receive(text:String,origin:Uri,main:Boolean){
  android.util.Log.i("ChronicleMap","Bridge origin=$origin main=$main length=${text.length}")
  if(destroyed||!main||origin.toString().trimEnd('/')!=MapBridgePolicy.ORIGIN||!MapBridgePolicy.trusted(web.url)){android.util.Log.w("ChronicleMap","Rejected origin or frame");rejectedMessages++;return}
  if(text.length<=256){val status=runCatching {JSONObject(text)}.getOrNull();if(status?.optString("type")=="frame"){
   if(status.keys().asSequence().toSet()==setOf("type","sceneId","revision"))rendered(status.optString("sceneId"),status.optLong("revision",-1));return
  }}
  if(text=="{\"type\":\"ready\"}"){pageReady();return}
  if(text=="{\"type\":\"request-export\"}"){fileRequest(false);return}
  if(editor&&text=="{\"type\":\"request-import\"}"){fileRequest(true);return}
  if(editor){rejectedMessages++;return}
  if(text.length>1024){rejectedMessages++;return}
  selected(text) // Revalidated against current native world and slot by the screen.
 }
 fun export(ready:(String)->Unit){web.evaluateJavascript("JSON.stringify(ChronicleMap.exportScenario())"){raw->runCatching {val text=org.json.JSONTokener(raw).nextValue() as String;MapValidation.parse(text);text}.onSuccess(ready).onFailure {error("地图导出失败：${it.message}")}}}
 fun importPreview(scene:MapScenario){if(!editor)return;MapValidation.verify(scene);web.evaluateJavascript("ChronicleMap.applyScenario(JSON.parse(${MapBridgePolicy.quoted(SaveJson.encodeToString(scene))}));",null)}
 fun boundsForTest():String {val p=IntArray(2);web.getLocationOnScreen(p);return JSONObject().put("width",web.width).put("height",web.height).put("x",p[0]).put("y",p[1]).toString()}
 fun evaluateForTest(js:String,ready:(String)->Unit){check(!destroyed);web.evaluateJavascript(js,ready)}
 fun pause(){if(!destroyed)web.onPause()}
 fun resume(){if(!destroyed){web.onResume();if(pageLoaded){sending=false;visualQueued=false;loaded=true;dispatcher.reset();flush()}}}
 fun destroy(){if(destroyed)return;destroyed=true;loaded=false;snapshot=null;dispatcher.reset();web.stopLoading();if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))WebViewCompat.removeWebMessageListener(web,"ChronicleHost");web.webChromeClient=null;web.webViewClient=WebViewClient();web.removeAllViews();web.destroy()}
 companion object {var activeCount=0;private set;fun created(){activeCount++};fun released(){activeCount--}}
}

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
private fun createMapWebView(context:android.content.Context,editor:Boolean,error:(String)->Unit,selected:(String)->Unit,file:(Boolean)->Unit):Pair<WebView,MapWebController> {
 val web=WebView(context);web.layoutParams=android.view.ViewGroup.LayoutParams(-1,-1);web.setBackgroundColor(android.graphics.Color.TRANSPARENT);val control=MapWebController(web,editor,error,selected,file)
 val loader=WebViewAssetLoader.Builder().addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(context)).build()
 with(web.settings){javaScriptEnabled=true;domStorageEnabled=true;useWideViewPort=true;loadWithOverviewMode=false;textZoom=100;allowFileAccess=false;allowContentAccess=false;allowFileAccessFromFileURLs=false;allowUniversalAccessFromFileURLs=false;mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW;setSupportMultipleWindows(false);javaScriptCanOpenWindowsAutomatically=false;setGeolocationEnabled(false);cacheMode=WebSettings.LOAD_NO_CACHE;mediaPlaybackRequiresUserGesture=true}
 if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))WebSettingsCompat.setAlgorithmicDarkeningAllowed(web.settings,false)
 if(android.os.Build.VERSION.SDK_INT<33&&WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK))WebSettingsCompat.setForceDark(web.settings,WebSettingsCompat.FORCE_DARK_OFF)
 android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web,false)
 web.webViewClient=object:WebViewClient(){
  override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=true
  override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse {
   if(request.method=="GET"&&MapBridgePolicy.trusted(request.url.toString()))return loader.shouldInterceptRequest(request.url)?:denied()
   return denied()
  }
  override fun onPageFinished(view:WebView,url:String){if(MapBridgePolicy.trusted(url))control.pageReady()}
  override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean {control.destroy();error("地图渲染器已释放，请返回后重新打开。游戏存档仍在数据库中。");return true}
 }
 web.webChromeClient=object:WebChromeClient(){override fun onConsoleMessage(message:ConsoleMessage):Boolean {android.util.Log.i("ChronicleMap",message.message()+" line="+message.lineNumber());return true};override fun onPermissionRequest(request:PermissionRequest){request.deny()};override fun onGeolocationPermissionsShowPrompt(origin:String,callback:GeolocationPermissions.Callback){callback.invoke(origin,false,false)}}
 if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))WebViewCompat.addWebMessageListener(web,"ChronicleHost",setOf(MapBridgePolicy.ORIGIN)){_,message,origin,main,_->control.receive(message.data?:"",origin,main)}
 else error("系统WebView版本过旧，安全选择通道不可用；请更新Android系统WebView。地图可浏览，地区治理仍可从原生列表打开。")
 var requested=false
 web.addOnLayoutChangeListener {_,left,top,right,bottom,_,_,_,_->if(right>left&&bottom>top){if(!requested){requested=true;web.loadUrl(MapBridgePolicy.PAGE+if(editor)"?editor=1" else "?native=1")}else web.post {if(MapBridgePolicy.trusted(web.url))web.evaluateJavascript("window.dispatchEvent(new Event('resize'));",null)}}}

 return web to control
}
private fun denied()=WebResourceResponse("text/plain","UTF-8",403,"Blocked",mapOf("Cache-Control" to "no-store"),ByteArrayInputStream(ByteArray(0)))

@Composable fun WorldMapScreen(world:World,slot:Int,onClose:()->Unit,onAction:(Command)->Unit,onRegion:(Int)->Unit) {
 val gameVm:GameViewModel=viewModel();val clock by gameVm.state.collectAsStateWithLifecycle()
 val context=LocalContext.current;val activity=generateSequence(context){(it as? android.content.ContextWrapper)?.baseContext}.filterIsInstance<MainActivity>().firstOrNull();val scope=rememberCoroutineScope();val lifecycle=LocalLifecycleOwner.current.lifecycle
 var editor by remember {mutableStateOf(false)};var error by remember {mutableStateOf("")};var selection by remember {mutableStateOf<MapHostMessage?>(null)}
 var controller by remember {mutableStateOf<MapWebController?>(null)};var exportData by remember {mutableStateOf("")};var pendingImport by remember {mutableStateOf<MapScenario?>(null)}
 val mapArmyState=rememberSaveable(slot){mutableStateOf("")};var mapArmy by mapArmyState;var chooseArmy by remember {mutableStateOf(false)}
 val currentWorld by rememberUpdatedState(world);val currentSlot by rememberUpdatedState(slot)
 val currentAction by rememberUpdatedState(onAction)
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)scope.launch {runCatching {withContext(Dispatchers.IO){require(exportData.isNotBlank()){"导出状态已重置，请重新导出。"};context.contentResolver.openOutputStream(uri)!!.use {it.write(exportData.toByteArray())}}}.onFailure {error="导出失败：${it.message}"}}}
 val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch {runCatching {withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)!!.use {val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=it.read(buffer);if(n<0)break;require(out.size()+n<=1024*1024);out.write(buffer,0,n)};MapValidation.parse(out.toString("UTF-8"))}}}.onSuccess {pendingImport=it}.onFailure {error="未导入，原沙盘保持不变：${it.message}"}}}
 BackHandler {if(selection!=null)selection=null else if(editor)editor=false else onClose()}
 Column(Modifier.fillMaxSize()) {
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={if(editor)editor=false else onClose()}){Text(if(editor)"返回游戏地图" else "返回地区列表")};if(!editor)TextButton(onClick={chooseArmy=true}){Text("地图调兵")};TextButton(onClick={editor=!editor;selection=null;mapArmy=""}){Text(if(editor)"查看游戏" else "地图沙盘")}}
  if(!editor&&mapArmy.isNotEmpty())Row(Modifier.padding(horizontal=12.dp)){val unit=CrownEngine.units(world).find {it.id==mapArmy};Text("${unit?.name?:"部队"} · 驻${unit?.let {world.regions[it.region].name}?:"未定"} · 点地图预览调防/战令",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall);TextButton(onClick={mapArmy=""}){Text("取消选军")}}
  if(!editor)ChronicleClockHud(clock.world?:world,clock,gameVm,compact=true)
  if(!editor)Row(
   Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=3.dp).testTag("map-strategy-strip"),
   horizontalArrangement=Arrangement.spacedBy(16.dp)
  ){
   Text("国库 ${world.nation.treasury}万两",style=MaterialTheme.typography.labelSmall)
   Text("民心 ${world.nation.morale}",style=MaterialTheme.typography.labelSmall)
   Text("稳定 ${world.nation.stability}",style=MaterialTheme.typography.labelSmall)
   Text("腐败 ${world.nation.corruption}",style=MaterialTheme.typography.labelSmall)
   Text(world.war?.let { front -> "战事持续：${world.neighbors.getOrNull(front.neighbor)?.name ?: "未知前线"}" } ?: "天下无战事",style=MaterialTheme.typography.labelSmall)
  }
  if(clock.message.isNotBlank())Row(Modifier.padding(horizontal=12.dp)){Text(clock.message,Modifier.weight(1f),maxLines=2,style=MaterialTheme.typography.labelSmall);TextButton(onClick={gameVm.message("")}){Text("收起反馈")}}
  if(editor)Text("地图沙盘 · 编辑与导入只保存在地图沙盘，不改变游戏",Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.labelSmall)
  if(error.isNotBlank())Text(error,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
  key(editor) {
   var owned by remember {mutableStateOf<MapWebController?>(null)}
   AndroidView(factory={ctx->
    val (web,c)=createMapWebView(ctx,editor,{error=it},{raw->runCatching {
     val picked=MapBridgePolicy.selection(raw,currentWorld,currentSlot)
     android.util.Log.i("ChronicleMap","Selected type=${picked.type} place=${picked.placeId}")
     // The WebView listener survives recompositions. Read the exact state object mutated by the army picker.
     if(mapArmyState.value.isNotEmpty()){
      val target=MapOrders.target(currentWorld,picked.type,picked.placeId.ifEmpty {picked.territoryId.ifEmpty {picked.polityId}})
      val order=MapOrders.military(currentWorld,target,mapArmyState.value);selection=null;currentAction(order)
     }else if(picked.type=="place-select"&&MunicipalEngine.all(currentWorld).any {t->t.id==picked.placeId&&t.rank in listOf("府城","县城")}){gameVm.openCity(picked.placeId);selection=null}else selection=picked
    }.onFailure {android.util.Log.w("ChronicleMap","Selection validation failed",it);error=it.message?:"已拒绝无效地图选择。"}},{isImport->if(isImport)import.launch(arrayOf("application/json","text/plain"))else controller?.export {text->exportData=text;export.launch("chronicle-map.json")}})
    owned=c;controller=c;activity?.mapController=c;MapWebController.created();web
   },modifier=Modifier.weight(1f).fillMaxWidth().testTag("world-map-web"),update={owned?.update(world,slot)})
   DisposableEffect(owned,lifecycle){val c=owned;val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP)c?.pause()else if(event==Lifecycle.Event.ON_START)c?.resume()};lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}}
   DisposableEffect(Unit){onDispose {owned?.destroy();if(owned!=null)MapWebController.released();if(activity?.mapController===owned)activity?.mapController=null;if(controller===owned)controller=null}}
  }
 }
 pendingImport?.let {data->AlertDialog(onDismissRequest={pendingImport=null},title={Text("导入地图沙盘？")},text={Text("场景 ${data.id} 将导入沙盘；若同ID已存在，会替换该沙盘槽位。游戏存档不受影响。")},confirmButton={TextButton(onClick={controller?.importPreview(data);pendingImport=null}){Text("导入沙盘")}},dismissButton={TextButton(onClick={pendingImport=null}){Text("取消")}})}
 if(chooseArmy)AlertDialog(onDismissRequest={chooseArmy=false},title={Text("选择地图部队")},text={Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState())){
  Text("选择后点击地图目标。国内按大区调防，外国预览动员或当前战线交战；仍须核对费用并确认。",style=MaterialTheme.typography.bodySmall)
  CrownEngine.units(world).forEach {u->TextButton(onClick={mapArmy=u.id;chooseArmy=false;selection=null;error=""}){Text("${u.name} · ${u.soldiers}人 · 驻${world.regions[u.region].name}")}}
 }},confirmButton={TextButton(onClick={chooseArmy=false}){Text("返回")}})
 val picked=selection
 if(picked!=null){
  val scene=MapSnapshotMapper.scene(world,slot)
  val place=scene.places[picked.placeId]
  val region=if(place!=null)MapSnapshotMapper.regionId(world,picked.placeId)else scene.territories.find {it.id==picked.territoryId}?.let {MapSnapshotMapper.regionForGroup(world,it.groupId)}
  val territory=scene.territories.find {it.id==picked.territoryId}
  val polity=scene.polities.find {it.id==picked.polityId}
  val selectedId=picked.placeId.ifEmpty {picked.territoryId.ifEmpty {picked.polityId}}
  val target=runCatching {MapOrders.target(world,picked.type,selectedId)}.getOrNull()
  val foreign=target?.neighbor
  var foreignTab by remember(selectedId){mutableStateOf("外交")}
  var orderError by remember(selectedId){mutableStateOf("")}
  var newName by remember(picked.placeId){mutableStateOf(place?.name?:"")}
  AlertDialog(onDismissRequest={selection=null},title={Text(place?.name?:territory?.name?:polity?.name?:"地图选择")},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text("ID：${picked.placeId.ifEmpty {picked.territoryId.ifEmpty {picked.polityId}}}",style=MaterialTheme.typography.labelSmall)
   if(orderError.isNotBlank())Text(orderError,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
   if(polity?.id=="nation"){Text("国库${world.nation.treasury}万两 · 债务${world.nation.debt}万两");Text("人口${world.nation.population} · 民心${world.nation.morale} · 稳定${world.nation.stability}")}
   if(territory!=null)Text("归属：${scene.polities.find {it.id==territory.polityId}?.name?:"无主"} · 状态：${territory.status}")
   if(place!=null&&MunicipalEngine.all(world).any {it.id==picked.placeId})TextButton(onClick={gameVm.openCity(picked.placeId);selection=null}){Text("城市详情与建筑")}
   if(place!=null)Text("状态：${scene.placeStates[picked.placeId]?.status?:"active"} · 归属：${scene.polities.find {it.id==place.polityId}?.name?:"未定"}")
   if(region!=null&&region in 0..3){val r=world.regions[region];Text("${r.name} · 人口${r.population} · 治安${r.security} · 腐败${r.corruption}");Text("地方官：${world.people.find {it.id==r.official}?.name?:"暂缺"}")
    TextButton(onClick={selection=null;onRegion(region)}){Text("地区治理、巡游与案卷")}
    if(place!=null){OutlinedTextField(value=newName,onValueChange={newName=it.take(16)},label={Text("城邑名称")},singleLine=true);TextButton(onClick={selection=null;onAction(Command("CITY_RENAME",region=region,target=picked.placeId,objective=newName))}){Text("申请更名 · 5万两")}}
    if(picked.placeId=="ruin-r0")OutlinedButton(onClick={selection=null;onAction(Command("CITY_RESTORE",region=0,target="ruin-r0"))}){Text("修复旧城 · 80万两 / 90日")}
    OutlinedButton(onClick={selection=null;onAction(Command("CITY_FOUND",region=region))}){Text("营建新邑 · 120万两 / 120日")}
   }
   if(foreign!=null){val n=world.neighbors[foreign];Text("${n.name} · 邦交${n.relation} · 军力${n.strength}")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){listOf("外交","军务","人物").forEachIndexed {i,t->SegmentedButton(selected=foreignTab==t,onClick={foreignTab=t},shape=SegmentedButtonDefaults.itemShape(i,3)){Text(t)}}}
    when(foreignTab){
     "外交"->{Text("双边使团只影响所选国家；35万两/20日，关系+8，上限100。",style=MaterialTheme.typography.bodySmall);OutlinedButton(onClick={selection=null;onAction(MapOrders.diplomacy(target))}){Text("向${n.name}派使")};OutlinedButton(onClick={selection=null;onAction(Command("INTEL_SCOUT",region=foreign))}){Text("侦察该国 · 35万两 / 30日")}}
     "军务"->{Text("点选出征军队后预览。军力、补给、主将、地形与现有战线均由规则核验。",style=MaterialTheme.typography.bodySmall);CrownEngine.units(world).forEach {u->OutlinedButton(onClick={runCatching {MapOrders.military(world,target,u.id)}.onSuccess {selection=null;onAction(it)}.onFailure {orderError=it.message.orEmpty()}}){Text("${u.name} · ${u.soldiers}人 → ${if(world.war?.neighbor==foreign)"前线交战"else"动员预览"}")}}}
     else->{val ruler=world.sovereign.foreign.getOrNull(foreign);if(ruler!=null){Text("${ruler.name} · ${if(ruler.captive)"被拘"else"在位君主"}");if(ruler.person>=0)OutlinedButton(onClick={selection=null;gameVm.openChat(ruler.person)}){Text("与该国君主交谈")}else OutlinedButton(onClick={selection=null;onAction(Command("RULER_CONTACT",region=foreign))}){Text("登记正式君主会谈")}}else{Text("旧存档尚未登记邻国君主。开启新章会初始化人物与护卫，保留既有世界和历史。");OutlinedButton(onClick={selection=null;onAction(Command("CONTENT_ENABLE"))}){Text("开启人物与护卫档案")}}}
    }
   }
   if(territory?.id=="frontier-0"){Text("两国关系达到80后可议定移交；耗资180万两、90日，后续驻防每季5万两。");OutlinedButton(onClick={selection=null;onAction(Command("BORDER_TREATY",target="frontier-0"))}){Text("议定边地移交")}}
  }},confirmButton={TextButton(onClick={selection=null}){Text("关闭")}})
 }
}
