"use strict";
// The Workshop rebuilds the release jar after saves: bursts share one build, a save mid-build queues one more.
const assert=require("node:assert");
const {createReleaseBuilder}=require("./release-builder.cjs");
let runs=0,finish=null;
const fake=(project,log,done)=>{runs++;finish=done};
const b=createReleaseBuilder({project:__dirname,delay:10,run:fake});
b.schedule();b.schedule();b.schedule();
assert.equal(b.state().status,"queued");
setTimeout(()=>{
 assert.equal(runs,1,"three quick saves start one build");
 assert.equal(b.state().status,"building");
 b.schedule();
 finish(0);
 assert.equal(b.state().status,"queued","a save during the build queues another");
 setTimeout(()=>{
  assert.equal(runs,2);
  finish(1);
  assert.equal(b.state().status,"failed");
  console.log("PASS: release jar rebuilds after saves, batched, with status.");
 },40);
},40);
