const assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {createWorkshopServer}=require('./server.cjs');
(async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'craftbound-ideas-')),catalogue=path.join(dir,'cards.json');fs.writeFileSync(catalogue,'{"unchanged":true}');
 const {server,token}=createWorkshopServer({catalogue,bundled:catalogue});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const url='http://127.0.0.1:'+server.address().port+'/api/ideas';
 const call=(method='GET',body)=>fetch(url,{method,headers:{'X-Workshop-Token':token,'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})});
 try{
  assert.equal((await fetch(url)).status,403);
  const initial=await (await call()).json();assert.deepEqual(initial.data,{version:1,ideas:[]});
  const idea=Object.fromEntries(['id','title','class','kind','stage','tags','concept','synergy','balance','updated'].map(k=>[k,k]));
  const data={version:1,ideas:[idea]};let r=await call('POST',{data,revision:initial.revision});assert.equal(r.status,200);let saved=await r.json();
  assert.deepEqual((await (await call()).json()).data,data);
  assert.equal((await call('POST',{data,revision:initial.revision})).status,409,'Stale tab must not overwrite notes');
  assert.equal((await call('POST',{data:{version:1,ideas:[{}]},revision:saved.revision})).status,422);
  data.ideas[0].synergy='Undead enabler → payoff';assert.equal((await call('POST',{data,revision:saved.revision})).status,200);
  assert.equal(JSON.parse(fs.readFileSync(path.join(dir,'workshop-ideas.json.backup.json'))).ideas[0].synergy,'synergy');
  assert.equal(fs.readFileSync(catalogue,'utf8'),'{"unchanged":true}');
  console.log('PASS: ideas authentication, persistence, backup, validation, stale-tab protection and catalogue isolation.');
 }finally{await new Promise(r=>server.close(r));fs.rmSync(dir,{recursive:true,force:true})}
})().catch(e=>{console.error(e);process.exitCode=1});
