"use strict";
// Local Workshop host: one fixed catalogue, authenticated writes, no external packages.
const http=require("node:http"),fs=require("node:fs"),path=require("node:path"),crypto=require("node:crypto");
const assets=new Map([["/","index.html"],...["index.html","model.js","editor.js","defaults.js","style.css","card-kinds.css","curve.css","reference.html","foundation-ideas.html","trials.html","trials-ui.js","trials-model.js","trials.css"].map(x=>["/"+x,x])]);
function createWorkshopServer({catalogue,bundled,assetDir=__dirname,token=crypto.randomBytes(32).toString("hex"),release=null}){
 const send=(res,code,body,type="application/json")=>{res.writeHead(code,{"Content-Type":type,"Cache-Control":"no-store","X-Content-Type-Options":"nosniff","Referrer-Policy":"no-referrer"});res.end(type==="application/json"?JSON.stringify(body):body)};
 const ideasFile=path.join(path.dirname(catalogue),'workshop-ideas.json');
 const readIdeas=()=>fs.existsSync(ideasFile)?fs.readFileSync(ideasFile,'utf8'):JSON.stringify({version:1,ideas:[]});
 const revision=text=>crypto.createHash('sha256').update(text).digest('hex');
 const trials=require("./trials-server.cjs").handler({catalogue,resourceDir:path.resolve(__dirname,"../../src/main/resources/craftbound"),token,send,onSaved:()=>release?.schedule()});
 const server=http.createServer((req,res)=>{
  const origin="http://127.0.0.1:"+server.address().port;
  if(req.headers.host!==new URL(origin).host || req.headers.origin && req.headers.origin!==origin){send(res,403,{error:"Only the local Workshop may access this server."});return}
  const route=new URL(req.url,origin).pathname;
  if(route==="/api/trials")return trials(req,res);
  if(route==="/api/release"){
   if(req.headers["x-workshop-token"]!==token){send(res,403,{error:"Open the Workshop using its launcher."});return}
   send(res,200,release?{enabled:true,...release.state()}:{enabled:false});return;
  }
  if(route==="/api/ideas"){
   if(req.headers["x-workshop-token"]!==token){send(res,403,{error:"Open the Workshop using its launcher."});return}
   if(req.method==="GET"){try{const text=readIdeas();send(res,200,{data:JSON.parse(text),revision:revision(text)})}catch(e){send(res,500,{error:"Cannot read ideas: "+e.message})}return}
   if(req.method!=="POST"||!String(req.headers["content-type"]).startsWith("application/json")){send(res,405,{error:"Expected JSON ideas."});return}
   let body="",bytes=0,large=false;
   req.setEncoding('utf8');req.on('data',chunk=>{bytes+=Buffer.byteLength(chunk);if(bytes>1000000){if(!large)send(res,413,{error:"Ideas exceed 1 MB. Export an archive first."});large=true}else body+=chunk});
   req.on('end',()=>{
    if(large)return;let temp;
    try{
     const input=JSON.parse(body),old=readIdeas(),d=input.data;
     if(input.revision!==revision(old)){send(res,409,{error:"Ideas changed in another tab. Export these edits, then reopen Ideas to load the saved version."});return}
     const fields=['id','title','class','kind','stage','tags','concept','synergy','balance','updated'];
     if(d?.version!==1||!Array.isArray(d.ideas)||d.ideas.length>1000||d.ideas.some(i=>!i||fields.some(k=>typeof i[k]!=="string"||i[k].length>20000)||!i.id)||new Set(d.ideas.map(i=>i.id)).size!==d.ideas.length){send(res,422,{error:"Invalid ideas document."});return}
     const clean={version:1,ideas:d.ideas.map(i=>Object.fromEntries(fields.map(k=>[k,i[k]])))},text=JSON.stringify(clean,null,2)+'\n';
     fs.mkdirSync(path.dirname(ideasFile),{recursive:true});if(fs.existsSync(ideasFile))fs.copyFileSync(ideasFile,ideasFile+'.backup.json');
     temp=ideasFile+'.'+crypto.randomBytes(6).toString('hex')+'.tmp';fs.writeFileSync(temp,text,{flag:'wx'});fs.renameSync(temp,ideasFile);temp=null;
     send(res,200,{revision:revision(text)});
    }catch(e){send(res,500,{error:"Ideas save failed: "+e.message})}finally{if(temp&&fs.existsSync(temp))fs.unlinkSync(temp)}
   });return;
  }
  if(route==="/api/cards"){
   if(req.headers["x-workshop-token"]!==token){send(res,403,{error:"Open the Workshop using its launcher."});return}
   if(req.method==="GET"){
    try {const modelPath=require.resolve(path.join(assetDir,"model.js"));delete require.cache[modelPath];send(res,200,{data:require(modelPath).prepareCatalog(JSON.parse(fs.readFileSync(catalogue,"utf8"))),path:catalogue})}
    catch(e){send(res,500,{error:"Cannot read active cards.json: "+e.message})}
    return;
   }
   if(req.method!=="POST" || !String(req.headers["content-type"]).startsWith("application/json")){send(res,405,{error:"Expected a JSON save."});return}
   let body="",bytes=0,large=false;
   req.setEncoding("utf8");req.on("data",chunk=>{bytes+=Buffer.byteLength(chunk);if(bytes>1000000){if(!large)send(res,413,{error:"Catalogue exceeds 1 MB"});large=true}else body+=chunk});
   req.on("end",()=>{
    if(large)return;
    let temp;
    try{
     const incoming=JSON.parse(body),previous=fs.readFileSync(catalogue,"utf8");
     // Browser assets are served fresh. Use the same current rules for each save,
     // even when this host was started before a Workshop update.
     const modelPath=require.resolve(path.join(assetDir,"model.js"));
     delete require.cache[modelPath];
     const M=require(modelPath);
     M.prepareCatalog(incoming);
     const problems=[...M.validate(incoming,JSON.parse(previous)),...M.validate(incoming,JSON.parse(fs.readFileSync(bundled,"utf8")))];
     if(problems.length){send(res,422,{error:[...new Set(problems)].slice(0,8).join("\n")});return}
     // Keep the last saved version before replacing the live file. A failed write leaves it intact.
     fs.copyFileSync(catalogue,catalogue+".workshop-backup.json");
     temp=catalogue+".workshop-"+crypto.randomBytes(6).toString("hex")+".tmp";
     fs.writeFileSync(temp,JSON.stringify(incoming,null,2)+"\n",{flag:"wx"});
     fs.renameSync(temp,catalogue);temp=null;
     send(res,200,{path:catalogue});
     release?.schedule();
    }catch(e){send(res,500,{error:"Save failed; your draft is still open. "+e.message})}
    finally{if(temp && fs.existsSync(temp))fs.unlinkSync(temp)}
   });
   return;
  }
  if(req.method!=="GET" || !assets.has(route)){send(res,404,{error:"Not found"});return}
  try{
   const filename=assets.get(route),type=filename.endsWith(".css")?"text/css":filename.endsWith(".js")?"text/javascript":"text/html; charset=utf-8";
   send(res,200,fs.readFileSync(path.join(assetDir,filename)),type);
  }catch(e){send(res,500,{error:e.message})}
 });
 server.requestTimeout=15000;
 return {server,token};
}
module.exports={createWorkshopServer};
if(require.main===module){
 const project=path.resolve(__dirname,"../..");
 const catalogue=path.join(project,"run/config/craftbound/cards.json");
 const bundled=path.join(project,"src/main/resources/craftbound/cards.json");
 if(!fs.existsSync(catalogue)){fs.mkdirSync(path.dirname(catalogue),{recursive:true});fs.copyFileSync(bundled,catalogue)}
 // A fixed port and a token saved beside the catalogue keep the same link across restarts, so it can be bookmarked.
 // Set WORKSHOP_PORT to move it. Delete run/config/craftbound/workshop-token.txt to get a new token.
 const port=Number(process.env.WORKSHOP_PORT)||47390;
 const tokenFile=path.join(path.dirname(catalogue),"workshop-token.txt");
 let token=fs.existsSync(tokenFile)?fs.readFileSync(tokenFile,"utf8").trim():"";
 if(!/^[0-9a-f]{64}$/.test(token)){token=crypto.randomBytes(32).toString("hex");fs.writeFileSync(tokenFile,token)}
 const url="http://127.0.0.1:"+port+"/#"+token;
 const open=()=>{
  const child=require("node:child_process").spawn("powershell.exe",["-NoProfile","-Command","Start-Process '"+url+"'"],{windowsHide:true,stdio:"ignore"});
  child.on("error",e=>console.error("Cannot open browser:",e.message));
 };
 // Each save rebuilds the player jar (build/release) in the background. Set WORKSHOP_AUTO_RELEASE=0 to turn it off.
 const release=process.env.WORKSHOP_AUTO_RELEASE==="0"?null:require("./release-builder.cjs").createReleaseBuilder({project});
 const {server}=createWorkshopServer({catalogue,bundled,token,release});
 server.on("error",e=>{
  // Already running (from the launcher or another window): reuse it instead of failing.
  if(e.code==="EADDRINUSE"){console.log(url+"  (already running)");if(process.argv.includes("--open"))open();return}
  console.error("Workshop could not start:",e.message);process.exitCode=1;
 });
 server.listen(port,"127.0.0.1",()=>{console.log(url);if(process.argv.includes("--open"))open()});
}
