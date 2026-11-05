"use strict";
// Exercises the production forms and local save route with an isolated catalogue and browser profile.
const fs=require("node:fs"),os=require("node:os"),path=require("node:path"),assert=require("node:assert/strict");
const {spawn}=require("node:child_process");
const {createWorkshopServer}=require("./server.cjs");
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function run(){
 const temp=fs.mkdtempSync(path.join(os.tmpdir(),"craftbound-trials-browser-")),profile=path.join(temp,"profile");
 const root=path.resolve(__dirname,"../.."),catalogue=path.join(temp,"cards.json"),bundled=path.join(root,"src/test/resources/craftbound/test-cards.json");
 fs.copyFileSync(bundled,catalogue);fs.mkdirSync(profile);const {server,token}=createWorkshopServer({catalogue,bundled}); let chrome=null,ws=null;
 try{
  await new Promise((ok,fail)=>{server.once("error",fail);server.listen(0,"127.0.0.1",ok);});
  const exe=[process.env.CHROME_PATH,path.join(process.env.ProgramFiles||"","Google/Chrome/Application/chrome.exe"),path.join(process.env["ProgramFiles(x86)"]||"","Microsoft/Edge/Application/msedge.exe"),"/usr/bin/chromium","/usr/bin/google-chrome"].find(p=>p&&fs.existsSync(p));
  assert.ok(exe,"Set CHROME_PATH to Chromium, Chrome, or Edge");
  chrome=spawn(exe,["--headless=new","--no-first-run","--no-default-browser-check","--disable-background-networking","--disable-component-update","--disable-sync","--disable-extensions","--remote-debugging-port=0","--user-data-dir="+profile,"about:blank"],{stdio:"ignore",windowsHide:true});
  const marker=path.join(profile,"DevToolsActivePort");for(let i=0;i<160&&!fs.existsSync(marker);i++)await sleep(100);
  const port=fs.readFileSync(marker,"utf8").split(/\r?\n/)[0];
  const pages=await(await fetch("http://127.0.0.1:"+port+"/json/list")).json();
  ws=new WebSocket(pages.find(t=>t.type==="page").webSocketDebuggerUrl);
  await new Promise((ok,fail)=>{ws.onopen=ok;ws.onerror=fail;});
  let sequence=0;const outstanding=new Map();
  ws.onmessage=e=>{const m=JSON.parse(e.data);if(!m.id)return;const q=outstanding.get(m.id);if(q){outstanding.delete(m.id);m.error?q[1](Error(m.error.message)):q[0](m.result);}};
  function rpc(method,params={}){return new Promise((ok,fail)=>{const id=++sequence;outstanding.set(id,[ok,fail]);ws.send(JSON.stringify({id,method,params}));});}
  const ev=async expr=>{const r=await rpc("Runtime.evaluate",{expression:expr,returnByValue:true,awaitPromise:true,userGesture:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result.value;};
  async function wait(expr){for(let i=0;i<150;i++){if(await ev(expr))return;await sleep(40);}throw Error("Timed out: "+expr+" · "+await ev('document.querySelector("#saved")?.textContent+" "+document.querySelector("#issues")?.textContent'));}
  // Exercise display-only migration against an old authored label; IDs and operation names stay stable.
  const api='http://127.0.0.1:'+server.address().port+'/api/trials';
  const headers={'X-Workshop-Token':token,'Content-Type':'application/json'};
  const legacy=await(await fetch(api,{headers})).json();
  const oldId=legacy.data.boons[0].id;
  legacy.data.boons[0].title='Gather Marks';legacy.data.boons[0].description='Gain 5 Marks.';
  assert.equal((await fetch(api,{method:'POST',headers,body:JSON.stringify({data:legacy.data,revision:legacy.revision})})).status,200);
  await rpc("Page.enable");await rpc("Emulation.setDeviceMetricsOverride",{width:1280,height:850,deviceScaleFactor:1,mobile:false});
  await rpc("Page.navigate",{url:"http://127.0.0.1:"+server.address().port+"/trials.html#"+token});
  await wait('document.querySelector("#saved")?.textContent.includes("Restored")');
  await ev('window.press=t=>{const b=[...document.querySelectorAll("button")].find(x=>x.textContent.trim()===t&&!x.disabled);if(!b)throw Error("Missing "+t);b.click();};window.field=(p,v)=>{const e=document.querySelector("[data-path=\\""+p+"\\"]");if(!e)throw Error("Missing "+p);e.value=v;e.dispatchEvent(new Event(e.tagName==="SELECT"?"change":"input",{bubbles:true}));};window.nav=k=>document.querySelector("[data-section=\\""+k+"\\"]").click()');
  await ev('nav("boons")');
  assert.equal(await ev('document.querySelector("[data-path=\\"boons.0.title\\"]").value'),'Gather Emeralds');
  assert.equal(await ev('document.querySelector("[data-path=\\"boons.0.description\\"]").value'),'Gain 5 Emeralds.');
  assert.equal(await ev('document.querySelector("[data-path=\\"boons.0.id\\"]").value'),oldId);
  assert.equal(await ev('[...document.querySelectorAll("option")].find(o=>o.value==="MARKS").textContent'),'Emeralds');
  await ev('nav("config")');
  assert(await ev('document.querySelector("main").textContent.includes("Starting Emeralds")'));
  await ev('nav("act")');
  assert.equal(await ev('document.querySelectorAll(".graph .node").length>20'),true);
  assert.equal(await ev('document.querySelector("[data-path=\\"act.topology\\"]").value'),"BRANCHING_V2");
  assert.equal(await ev('document.querySelectorAll(".link-check").length'),0);
  assert.equal(await ev('[...document.querySelectorAll("button")].find(b=>b.textContent==="+ Lane").disabled'),true);
  if(process.argv.includes("--screenshot")){
   const shot=await rpc("Page.captureScreenshot",{format:"png"}),dir=path.join(root,"build/workshop-preview");fs.mkdirSync(dir,{recursive:true});fs.writeFileSync(path.join(dir,"trials-map.png"),Buffer.from(shot.data,"base64"));
  }
  await ev('field("act.topology","AUTHORED");press("+ Floor");press("+ Lane");field("act.name","Graph test expedition");nav("opponents");press("+ New opponent");field("opponents."+([ ...document.querySelectorAll(".collection-list button")].length-1)+".name","Ember Surveyor")');
  assert.equal(await ev('document.querySelectorAll("#cards option").length>10'),true);
  await ev('document.querySelector("[aria-label=\\"Add a preferred card\\"]").value="craftbound:zombie";press("Add");nav("events");press("+ New event");press("+ Player choice");nav("charms");press("Duplicate");window.confirm=()=>true;press("Delete");press("Duplicate");nav("rewards");field("rewards.0.cardBack","craftbound:ender");nav("config");field("config.startHealth","0");press("Save Trials")');
  await wait('document.querySelector("[data-path=\\"config.startHealth\\"]")?.classList.contains("bad")');
  await ev('field("config.startHealth","31");press("Save Trials")');
  await wait('document.querySelector("#saved").textContent.startsWith("Saved to")');
  const saved=JSON.parse(fs.readFileSync(path.join(temp,"trials.json"),"utf8"));
  assert.equal(saved.act.name,"Graph test expedition");assert.equal(saved.act.floors.length,14);assert.equal(saved.config.startHealth,31);
  assert.equal(saved.events.at(-1).options.length,2);assert.equal(saved.opponents.at(-1).name,"Ember Surveyor");assert.deepEqual(saved.opponents.at(-1).anchors,["craftbound:zombie"]);assert.equal(saved.rewards[0].cardBack,"craftbound:ender");
  await ev('nav("rest");nav("boons");nav("shop");nav("act");');
  await rpc("Emulation.setDeviceMetricsOverride",{width:425,height:800,deviceScaleFactor:1,mobile:false});
  assert.ok(await ev('document.documentElement.scrollWidth<=document.documentElement.clientWidth+1'),"Graph overflowed the mobile page");
  console.log("PASS: real Trials browser created floors, lanes, rivals, events and charms, changed a first-clear reward, located a validation error, and saved its campaign.");
 }finally{
  if(ws)ws.close();if(chrome&&chrome.exitCode===null){chrome.kill();await Promise.race([new Promise(r=>chrome.once("exit",r)),sleep(3000)]);}
  server.closeAllConnections();server.close();const target=fs.realpathSync(temp),base=fs.realpathSync(os.tmpdir());
  if(path.dirname(target)===base&&path.basename(target).startsWith("craftbound-trials-browser-")){try{fs.rmSync(target,{recursive:true,force:true,maxRetries:3,retryDelay:100});}catch{/* Browser subprocess may briefly retain its temp profile. */}}
 }
}
run().catch(e=>{console.error(e);process.exitCode=1;});
