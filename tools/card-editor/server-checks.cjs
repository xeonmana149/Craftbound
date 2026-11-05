"use strict";
const assert=require("node:assert/strict"),fs=require("node:fs"),os=require("node:os"),path=require("node:path");
const {createWorkshopServer}=require("./server.cjs");
(async()=>{
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),"craftbound-workshop-check-"));
 const catalogue=path.join(directory,"cards.json"),bundled=path.resolve(__dirname,"../../src/test/resources/craftbound/test-cards.json");
 const original=fs.readFileSync(bundled,"utf8");fs.writeFileSync(catalogue,original);
 const modelFile=path.join(directory,"model.js"),modelSource=fs.readFileSync(path.join(__dirname,"model.js"),"utf8");
 fs.writeFileSync(modelFile,modelSource);
 fs.copyFileSync(path.join(__dirname,"reference.html"),path.join(directory,"reference.html"));
 const {server,token}=createWorkshopServer({catalogue,bundled,assetDir:directory});
 await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
 const url="http://127.0.0.1:"+server.address().port;
 const headers={"X-Workshop-Token":token,"Content-Type":"application/json"};
 try{
  assert.equal((await fetch(url+"/api/cards")).status,403);
  assert.equal((await fetch(url+"/api/cards",{headers:{...headers,Origin:"https://example.com"}})).status,403);
  const forgedHost=await new Promise((resolve,reject)=>{
   const req=require("node:http").get(url+"/api/cards",{headers:{...headers,Host:"example.com"}},res=>{res.resume();resolve(res.statusCode)});req.on("error",reject);
  });
  assert.equal(forgedHost,403);
  assert.equal((await fetch(url+"/server.cjs")).status,404);
  const reference=await fetch(url+"/reference.html");
  assert.equal(reference.status,200);
  assert.match(reference.headers.get("content-type"),/^text\/html/);
  assert.match(await reference.text(),/Craftbound Design Reference/);
  const loaded=await(await fetch(url+"/api/cards",{headers})).json();assert.equal(loaded.path,catalogue);
  const data=loaded.data;data.cards.find(c=>c.id==="craftbound:zombie").cost=3;
  let response=await fetch(url+"/api/cards",{method:"POST",headers,body:JSON.stringify(data)});
  assert.equal(response.status,200);assert.equal(JSON.parse(fs.readFileSync(catalogue)).cards.find(c=>c.id==="craftbound:zombie").cost,3);
  assert.equal(fs.readFileSync(catalogue+".workshop-backup.json","utf8"),original);
  const first=fs.readFileSync(catalogue,"utf8");data.cards.find(c=>c.id==="craftbound:zombie").cost=4;
  response=await fetch(url+"/api/cards",{method:"POST",headers,body:JSON.stringify(data)});assert.equal(response.status,200);
  assert.equal(JSON.parse(fs.readFileSync(catalogue)).cards.find(c=>c.id==="craftbound:zombie").cost,4);
  assert.equal(fs.readFileSync(catalogue+".workshop-backup.json","utf8"),first);
  const second=fs.readFileSync(catalogue,"utf8");
  // Simulate an update while the host stays alive: old rules reject, then new
  // rules must take effect without restarting or weakening validation.
  fs.writeFileSync(modelFile,'module.exports={prepareCatalog:d=>d,validate:()=>["Old validator"]};');
  response=await fetch(url+"/api/cards",{method:"POST",headers,body:JSON.stringify(data)});
  assert.equal(response.status,422);assert.match((await response.json()).error,/Old validator/);
  assert.equal(fs.readFileSync(catalogue,"utf8"),second);
  fs.writeFileSync(modelFile,modelSource);
  response=await fetch(url+"/api/cards",{method:"POST",headers,body:JSON.stringify(data)});
  assert.equal(response.status,200);
  data.cards[0].id="craftbound:renamed_old_card";
  assert.equal((await fetch(url+"/api/cards",{method:"POST",headers,body:JSON.stringify(data)})).status,422);
  assert.equal(fs.readFileSync(catalogue,"utf8"),second);
  assert.equal((await fetch(url+"/api/cards",{method:"POST",headers,body:"invalid"})).status,500);
  assert.equal(fs.readFileSync(catalogue,"utf8"),second);
  assert.equal((await fetch(url+"/api/cards",{method:"POST",headers,body:" ".repeat(1000001)})).status,413);
  assert.equal(fs.readFileSync(catalogue,"utf8"),second);
  console.log("PASS: direct repeated overwrite, backups, invalid-save preservation, fixed routes, token/origin/host checks and size limit. Tested only temporary files.");
 }finally{
  server.closeAllConnections();await new Promise(resolve=>server.close(resolve));
  // Remove only files created by this test, then its uniquely created empty directory.
  for(const file of fs.readdirSync(directory))fs.unlinkSync(path.join(directory,file));
  fs.rmdirSync(directory);
 }
})().catch(e=>{console.error(e);process.exitCode=1});
