const {chromium}=require('playwright');
const fs=require('node:fs'),http=require('node:http'),assert=require('node:assert/strict'),path=require('node:path');
const baseline=process.argv.includes('--baseline');
const html=fs.readFileSync(baseline?'integration/map-v4/upstream/chronicle_world_map_v4.html':'app/src/main/assets/maps/chronicle_world_map_v4.html','utf8');
const script=html.match(/<script>([\s\S]*?)<\/script>/)[1];
fs.mkdirSync('work/map-browser',{recursive:true});fs.writeFileSync('work/map-browser/extracted.js',script);
require('node:child_process').execFileSync(process.execPath,['--check','work/map-browser/extracted.js']);
const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(html)});
(async()=>{
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const origin=`http://127.0.0.1:${server.address().port}`;
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const results=[];
 try {
 for(const [width,height] of [[390,844],[320,720],[844,390]]){
  const context=await browser.newContext({viewport:{width,height},deviceScaleFactor:1,hasTouch:true});
  const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto(origin);await page.waitForFunction(()=>window.ChronicleMap);assert.equal(await page.evaluate(()=>ChronicleMap.getSchema()),'chronicle-map-scenario/v2');
  const initial=await page.evaluate(()=>ChronicleMap.exportScenario());assert.equal(initial.meta.title,'');assert.equal(initial.polities.length,0);
  await page.locator('#territory-open').click();await page.getByText('加载虚构示例（覆盖当前槽位）',{exact:true}).waitFor();
  page.once('dialog',d=>d.accept());await page.getByText('加载虚构示例（覆盖当前槽位）',{exact:true}).click();
  assert.equal(await page.evaluate(()=>ChronicleMap.listTerritories().length),3);
  await page.locator('#panel details summary').first().click();await page.locator('#panel select').first().selectOption('b');await page.getByText('保存归属',{exact:true}).first().click();
  assert.equal(await page.evaluate(()=>ChronicleMap.listTerritories()[0].polityId),'b');
  const checks=await page.evaluate(()=>{
   const before=JSON.stringify(ChronicleMap.exportScenario());let bad=0;
   for(const polygon of [[[100,30],[104,34],[100,34],[104,30]],[[999,30],[103,30],[102,32]]]){
    try{ChronicleMap.patchScenario({territories:[{id:'bad',polygon}]})}catch{bad++}
    if(before!==JSON.stringify(ChronicleMap.exportScenario()))throw Error('invalid patch changed scene');
   }
   const scene=ChronicleMap.exportScenario();ChronicleMap.applyWorldDelta({sceneId:scene.id,revision:1,changes:{places:{'place-0':{name:'增量城'}}}});
   const after=JSON.stringify(ChronicleMap.exportScenario());
   for(const d of [{sceneId:scene.id,revision:1,changes:{}},{sceneId:'other',revision:2,changes:{}}]){try{ChronicleMap.applyWorldDelta(d)}catch{bad++}if(after!==JSON.stringify(ChronicleMap.exportScenario()))throw Error('replay changed scene')}
   return bad;
  });assert.equal(checks,4);
  await page.locator('#panel .close').click();
  if(height<500){await page.locator('#mode').click();await page.mouse.move(width*.5,height*.35);await page.mouse.down();await page.mouse.move(width*.5,height*.63,{steps:5});await page.mouse.up();}
  await page.locator('#territory-open').click();await page.getByText('✎ 在地图上圈定新疆域',{exact:true}).click();
  const drawBottom=await page.locator('#draw-controls').evaluate(e=>e.getBoundingClientRect().bottom);const barTop=await page.locator('#toolbar').evaluate(e=>e.getBoundingClientRect().top);const y1=Math.max(drawBottom+6,height*.5-10),y2=Math.min(y1+16,barTop-8);assert.ok(y2>y1);
  for(const [x,y] of [[.47,y1],[.53,y1],[.53,y2],[.47,y2]])await page.locator('#map').tap({position:{x:width*x,y}});
  console.log('DRAW',width,height,await page.locator('#draw-count').textContent());await page.waitForFunction(()=>document.querySelector('#draw-count').textContent.includes('4 个顶点'));
  await page.locator('#draw-finish').click();assert.equal(await page.evaluate(()=>ChronicleMap.listTerritories().length),4);
  await page.locator('#search').click();await page.locator('input[type=search]').fill('增量城');await page.locator('#panel button.listing').first().click();
  assert.ok((await page.locator('#panel').innerText()).includes('稳定地点 ID：place-0'));
  await page.locator('#panel .close').click();
  const zoom=await page.evaluate(()=>__tianxiaDiagnostics().cameraZoom);await page.locator('#map').focus();await page.keyboard.press('+');assert.ok(await page.evaluate(()=>__tianxiaDiagnostics().cameraZoom)>zoom);
  await page.mouse.move(width*.4,height*.5);await page.mouse.down();await page.mouse.move(width*.6,height*.5,{steps:8});await page.mouse.up();assert.equal(await page.evaluate(()=>__tianxiaDiagnostics().cameraFinite),true);
  for(let i=0;i<3;i++)await page.locator('#theme').click();
  await page.evaluate(()=>{const a=ChronicleMap.exportScenario();ChronicleMap.createScenario();ChronicleMap.selectScenario(a.id);if(ChronicleMap.exportScenario().places['place-0'].name!=='增量城')throw Error('slot isolation');const b=ChronicleMap.exportScenario();ChronicleMap.applyScenario(JSON.parse(JSON.stringify(b)));});
  await page.screenshot({path:`work/map-browser/${baseline?'baseline':'integrated'}-${width}x${height}.png`});
  assert.deepEqual(errors,[]);results.push({viewport:[width,height],passed:true});await context.close();
 }
 const legacy=await browser.newContext();await legacy.addInitScript(()=>localStorage.setItem('chronicle-map-scene-v1',JSON.stringify({schema:'chronicle-map-scenario/v1',id:'legacy',places:{'place-0':{name:'旧存档城'}}})));
 const p=await legacy.newPage();await p.goto(origin);assert.equal(await p.evaluate(()=>ChronicleMap.exportScenario().schema),'chronicle-map-scenario/v2');assert.equal(await p.evaluate(()=>ChronicleMap.exportScenario().places['place-0'].name),'旧存档城');await legacy.close();results.push({legacyMigration:true});
 if(!baseline){
 const native=await browser.newContext({viewport:{width:390,height:844}});await native.addInitScript(()=>{window.hostEvents=[];window.ChronicleHost={postMessage(text){hostEvents.push(JSON.parse(text))}}});
 const p=await native.newPage();await p.goto(origin+'?native=1');
 const demo=JSON.parse(fs.readFileSync('integration/map-v4/upstream/chronicle_scenario_demo_v4.json','utf8'));await p.evaluate(s=>ChronicleMap.applyScenario(s),demo);
 assert.equal(await p.evaluate(()=>{try{ChronicleMap.patchScenario({meta:{title:'forged'}});return false}catch{return true}}),true);
 await p.locator('#search').click();await p.locator('input[type=search]').fill('月京');await p.locator('#panel button.listing').first().click();
 assert.ok(await p.evaluate(()=>hostEvents.some(e=>e.type==='place-select'&&e.placeId==='place-0')));assert.equal(await p.getByText('保存地点设定',{exact:true}).count(),0);
 await p.evaluate(()=>{const s=ChronicleMap.exportScenario();ChronicleMap.applyWorldDelta({sceneId:s.id,revision:1,changes:{places:{'place-0':{name:'刷新城'}}}})});assert.equal(await p.locator('#panel .panel-head strong').textContent(),'刷新城');assert.equal(await p.evaluate(()=>hostEvents.filter(e=>e.type==='place-select').length),1);
 await p.locator('#panel .close').click();await p.locator('#territory-open').click();await p.getByText('苍岭原 · 玄岚',{exact:true}).click();await p.getByText('登记城邑：2处',{exact:true}).waitFor();assert.ok((await p.locator('#panel').innerText()).includes('行政层级：苍岭原'));
 const custom={...demo,id:'custom-check',geography:{kind:'custom',landPolygons:[[[95,20],[125,20],[125,45],[95,45]]]},places:{...demo.places,'town-new':{name:'架空新城'}},meta:{title:'架空地理',era:'测试'}};
 await p.evaluate(s=>ChronicleMap.applyScenario(s),custom);assert.equal(await p.evaluate(()=>ChronicleMap.listPlaces().some(p=>p.id==='place-0')),false);
 await p.locator('#panel .close').click();await p.screenshot({path:'work/map-browser/integrated-custom.png'});
 const before=await p.evaluate(()=>JSON.stringify(ChronicleMap.exportScenario()));
 const invalids=[{groups:[{id:'loop',parentId:'loop'}]},{places:{missing:{name:'错点'}}},{territories:[{id:'bad',polygon:[[100,30],[104,30],[102,30],[104,34]]}]}];
 for(const changes of invalids){assert.equal(await p.evaluate(ch=>{try{const s=ChronicleMap.exportScenario();ChronicleMap.applyWorldDelta({sceneId:s.id,revision:99,changes:ch});return false}catch{return true}},changes),true);assert.equal(await p.evaluate(()=>JSON.stringify(ChronicleMap.exportScenario())),before)}
 await native.close();results.push({nativeReadOnly:true,selectionBridge:true,customGeography:true,invalidDeltaAtomic:true});
 }
 fs.writeFileSync('work/map-browser/'+(baseline?'baseline':'integrated')+'-results.json',JSON.stringify({browser:await browser.version(),results},null,2));console.log(JSON.stringify(results));
 }finally{await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1;server.close()});
