'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const M = require('./trials-model.js');

function defaults(resourceDir) {
  const book = {version:1};
  for (const key of ['config','act','opponents','events','rest','boons','shop','charms','rewards']) {
    const file = key === 'opponents' ? 'trials-opponents.json' : path.join('trials',key+'.json');
    book[key] = JSON.parse(fs.readFileSync(path.join(resourceDir,file),'utf8'));
  }
  return book;
}
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
function handler({catalogue,resourceDir,token,send,onSaved=()=>{}}) {
  const file = path.join(path.dirname(catalogue),'trials.json');
  function read() {
    const base = defaults(resourceDir);
    const raw = fs.existsSync(file) ? fs.readFileSync(file,'utf8') : JSON.stringify(base,null,2)+'\n';
    const cards = JSON.parse(fs.readFileSync(catalogue,'utf8')).cards.filter(c=>!c.deleted)
      .map(({id,name,kind,keywords=[],token,rarity,cost,type})=>({id,name,kind,keywords,token,rarity,cost,type}));
    return {raw, base, cards};
  }
  return function(req,res) {
    if(req.headers['x-workshop-token']!==token) return send(res,403,{error:'Open the Workshop using its launcher.'});
    if(req.method==='GET') {
      try {const {raw,base,cards}=read();return send(res,200,{data:JSON.parse(raw),defaults:base,cards,revision:hash(raw),path:file});}
      catch(e){return send(res,500,{error:'Could not read Trials. '+e.message});}
    }
    if(req.method!=='POST'||!String(req.headers['content-type']).startsWith('application/json'))return send(res,405,{error:'Expected a JSON save.'});
    let bytes=0, chunks=[];
    req.on('data',chunk=>{bytes+=chunk.length;if(bytes<=1_000_000)chunks.push(chunk);else if(chunks){chunks=null;send(res,413,{error:'Trials must fit in 1 MB.'});}});
    req.on('end',()=>{
      if(!chunks)return;
      let input;
      try {input=JSON.parse(Buffer.concat(chunks).toString('utf8'));}
      catch {return send(res,400,{error:'Invalid JSON.'});}
      let temp;
      try {
        const {raw,cards}=read();
        if(input?.revision!==hash(raw))return send(res,409,{error:'Another tab or Minecraft changed this file. Export your draft, then reload the saved version.'});
        const errors=M.validate(input.data,cards);
        if(errors.length)return send(res,422,{error:errors[0].path+' — '+errors[0].message,errors});
        const next=JSON.stringify(input.data,null,2)+'\n';
        if(Buffer.byteLength(next)>1_000_000)return send(res,413,{error:'The formatted Trials file exceeds 1 MB.'});
        fs.mkdirSync(path.dirname(file),{recursive:true});
        temp=file+'.'+crypto.randomUUID()+'.tmp';
        fs.writeFileSync(temp,next,{flag:'wx'});
        if(fs.existsSync(file))fs.copyFileSync(file,file+'.workshop-backup.json');
        fs.renameSync(temp,file);temp=null;
        send(res,200,{revision:hash(next),path:file});
        onSaved();
      } catch(e){send(res,500,{error:'Trials save failed. '+e.message});}
      finally{if(temp&&fs.existsSync(temp))fs.unlinkSync(temp);}
    });
  };
}
module.exports={handler,defaults};
