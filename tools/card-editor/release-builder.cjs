"use strict";
// Rebuilds the player jar (./gradlew releaseJar) in the background after Workshop saves.
// Saves close together share one build; a save during a build queues exactly one more.
const fs=require("node:fs"),path=require("node:path"),{spawn}=require("node:child_process");

function createReleaseBuilder({project,delay=3000,run=defaultRun}){
 const state={status:"idle",message:"",jar:"",at:0};
 let timer=null,running=false,again=false;
 const logFile=path.join(project,"build","release-build.log");
 function newestJar(){
  const dir=path.join(project,"build","release");
  if(!fs.existsSync(dir))return "";
  const jars=fs.readdirSync(dir).filter(f=>f.endsWith(".jar")).map(f=>path.join(dir,f));
  jars.sort((a,b)=>fs.statSync(b).mtimeMs-fs.statSync(a).mtimeMs);
  return jars[0]||"";
 }
 function start(){
  running=true;again=false;
  Object.assign(state,{status:"building",message:"Building the release jar…",at:Date.now()});
  run(project,logFile,code=>{
   running=false;
   if(code===0)Object.assign(state,{status:"ready",message:"Release jar ready",jar:newestJar(),at:Date.now()});
   else Object.assign(state,{status:"failed",message:"Release build failed. See build/release-build.log",at:Date.now()});
   if(again)schedule();
  });
 }
 function schedule(){
  if(running){again=true;return}
  clearTimeout(timer);
  Object.assign(state,{status:"queued",message:"Release jar will rebuild in a moment…",at:Date.now()});
  timer=setTimeout(start,delay);
 }
 return {schedule,state:()=>({...state})};
}

function defaultRun(project,logFile,done){
 fs.mkdirSync(path.dirname(logFile),{recursive:true});
 const log=fs.openSync(logFile,"w");
 const windows=process.platform==="win32";
 const child=windows
  ?spawn('"'+path.join(project,"gradlew.bat")+'" releaseJar --offline -q',{cwd:project,windowsHide:true,shell:true,stdio:["ignore",log,log]})
  :spawn("./gradlew",["releaseJar","--offline","-q"],{cwd:project,stdio:["ignore",log,log]});
 child.on("error",e=>{fs.writeSync(log,"Could not start Gradle: "+e.message+"\n");fs.closeSync(log);done(1)});
 child.on("exit",code=>{fs.closeSync(log);done(code)});
}

module.exports={createReleaseBuilder};
