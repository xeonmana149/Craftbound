"use strict";
const assert=require("node:assert/strict"),fs=require("node:fs"),vm=require("node:vm"),path=require("node:path");
const source=fs.readFileSync(path.join(__dirname,"editor.js"),"utf8");
const saveSource=source.slice(source.indexOf("async function saveFile("),source.indexOf('$("curveNeutral").onchange='));
(async()=>{
 const posts=[],messages=[],node={textContent:""};
 const context={
  M:require("./model.js"),data:{cards:[{id:"craftbound:test",cost:1}]},baseline:{},dirty:true,
  localWorkshop:true,activePath:"",saving:false,activeSaveHandle:null,recovery:false,autoIds:new Set(["craftbound:test"]),
  handle:{createWritable(){throw Error("Normal Save must not use the imported file handle")}},
  errors:()=>[],status:(text,error)=>messages.push({text,error}),$:()=>node,
  renderCard(){},remember(){},clearTimeout(){},draftTimer:0,key:"draft",localStorage:{removeItem(){}},
  window:{},pickerTypes:[],workshopRequest:async(method,json)=>{posts.push({method,json});return {path:"run/config/craftbound/cards.json"}}
 };
 vm.runInNewContext(saveSource+"\nglobalThis.runSave=saveFile;",context);
 await context.runSave();assert.equal(posts.length,1);assert.equal(posts[0].method,"POST");assert.equal(context.dirty,false);
 assert.equal(context.baseline.cards[0].cost,1);assert.equal(context.autoIds.size,0);
 context.data.cards[0].cost=2;context.dirty=true;
 let exported;
 context.window.showSaveFilePicker=true;context.showSaveFilePicker=async()=>({name:"export.json",createWritable:async()=>({write:async json=>exported=json,close:async()=>{}})});
 await context.runSave(true);assert.equal(JSON.parse(exported).cards[0].cost,2);assert.equal(posts.length,1);assert.equal(context.dirty,true);
 await context.runSave();assert.equal(posts.length,2);assert.equal(JSON.parse(posts[1].json).cards[0].cost,2);
 context.dirty=true;context.workshopRequest=async()=>{throw Error("Disk unavailable")};
 await context.runSave();assert.equal(context.dirty,true);assert.equal(context.saving,false);assert.equal(messages.at(-1).error,true);
 context.workshopRequest=async()=>{context.data.cards[0].cost=4;return {path:"run/config/craftbound/cards.json"}};
 context.data.cards[0].cost=3;await context.runSave();assert.equal(context.dirty,true);assert.equal(context.baseline.cards[0].cost,3);assert.equal(context.data.cards[0].cost,4);
 context.localWorkshop=false;await context.runSave();assert.ok(messages.at(-1).text.includes("Nothing has been saved"));
 // The reported bug: a standalone page contains Danny, but previously Save could only reject it.
 const original=JSON.parse(fs.readFileSync(path.resolve(__dirname,"../../src/test/resources/craftbound/test-cards.json"),"utf8"));
 context.data=structuredClone(original);context.baseline=structuredClone(original);
 const danny={...structuredClone(original.cards.find(c=>!c.token)),id:"craftbound:danny",name:"Danny"};
 context.data.cards.push(danny);context.dirty=true;context.window.showOpenFilePicker=true;
 let disk=JSON.stringify(original),picks=0,aborts=0;
 const target={name:"cards.json",getFile:async()=>({size:disk.length,text:async()=>disk}),
  createWritable:async()=>{let staged;return {write:async text=>{staged=text},close:async()=>{disk=staged},abort:async()=>{aborts++}}}};
 context.showOpenFilePicker=async()=>{picks++;return [target]};
 await context.runSave();assert.equal(context.dirty,false);assert.equal(JSON.parse(disk).cards.at(-1).name,"Danny");
 assert.equal(context.activeSaveHandle,target);assert.equal(picks,1);
 context.data.cards.at(-1).cost=4;context.dirty=true;await context.runSave();
 assert.equal(picks,1);assert.equal(JSON.parse(disk).cards.at(-1).cost,4);
 // An export/import handle never becomes the normal Save destination, and cancel preserves the draft.
 context.activeSaveHandle=null;context.dirty=true;
 context.showOpenFilePicker=async()=>{throw Object.assign(Error("cancel"),{name:"AbortError"})};
 await context.runSave();assert.equal(context.dirty,true);assert.match(messages.at(-1).text,/cancelled/);
 context.showOpenFilePicker=async()=>[{...target,name:"export.json"}];
 await context.runSave();assert.equal(context.activeSaveHandle,null);assert.equal(context.dirty,true);
 // Reject deleting a card someone added to the selected file after this draft was opened.
 const updated=JSON.parse(disk);updated.cards.push({...danny,id:"craftbound:other_new_card"});disk=JSON.stringify(updated);
 context.showOpenFilePicker=async()=>[target];await context.runSave();
 assert.equal(context.dirty,true);assert.match(messages.at(-1).text,/not overwritten/);
 disk=JSON.stringify(original);
 context.showOpenFilePicker=async()=>[{...target,createWritable:async()=>({write:async()=>{throw Error("Disk full")},abort:async()=>{aborts++}})}];
 await context.runSave();assert.equal(context.dirty,true);assert.equal(aborts,1);assert.equal(context.saving,false);
 console.log("PASS: connected and standalone saves, Danny append, repeated overwrite, export isolation, cancelled/failed writes, disk identity protection and edits during Save.");
})().catch(e=>{console.error(e);process.exitCode=1});
