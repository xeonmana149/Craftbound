"use strict";
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const M=require("./model.js"),base=JSON.parse(fs.readFileSync(path.join(__dirname,"../../src/test/resources/craftbound/test-cards.json"),"utf8"));
const source=fs.readFileSync(path.join(__dirname,"editor.js"),"utf8");
const nodes=[];
function element(tag="div"){
 const classes=new Set(),attributes=new Map();
 const el={tag,children:[],hidden:false,value:"filtered",checked:false,innerHTML:"",
 classList:{add:x=>classes.add(x),remove:x=>classes.delete(x),contains:x=>classes.has(x)},
 setAttribute:(k,v)=>attributes.set(k,v),removeAttribute:k=>attributes.delete(k),getAttribute:k=>attributes.get(k),
 append(child){this.children.push(child)},replaceChildren(){this.children=[]},scrollIntoView(){this.scrolled=true},
 focus(){this.focused=true},matches(selector){return selector.split(",").includes(this.tag)},closest(){return this},
 querySelector(){return null}};nodes.push(el);return el;
}
const ids=Object.fromEntries(["save","saveAs","warnings","errorDetail","cardForm","effects","cardNumber","search","classFilter","kindFilter","showTokens"].map(id=>[id,element()]));
const restoreAll=element("button"),repair=element("button"),fields=[element("input"),element("input")],effects=[element(),element()];
effects.forEach((el,i)=>el.querySelector=selector=>selector==='[data-field="amount"]'?fields[i]:null);
ids.effects.querySelector=selector=>effects[Number(selector.match(/\d+/)?.[0])];
ids.cardForm.querySelector=()=>null;
const context={
 M,data:M.clone(base),baseline:M.clone(base),CRAFTBOUND_DEFAULTS:M.clone(base),selected:base.cards[0].id,focusedIssue:null,cardOverride:null,
  $:id=>id==="repairMissingAll"?(ids.errorDetail.innerHTML.includes('id="repairMissingAll"')?restoreAll:null):id==="repairIssue"?(ids.errorDetail.innerHTML.includes('id="repairIssue"')?repair:null):ids[id],
 document:{createElement:element,querySelectorAll:selector=>nodes.filter(n=>selector===".validation-focus"?n.classList.contains("validation-focus"):n.getAttribute("aria-invalid")==="true")},
 requestAnimationFrame:fn=>fn(),status(){},dirty:true,esc:text=>String(text).replaceAll("<","&lt;"),changed(){},renderCard(){},renderList(){},
 showView(view){context.view=view},card:()=>context.data.cards.find(c=>c.id===context.selected)
};
vm.createContext(context);
vm.runInContext(source.slice(source.indexOf("const issueKey="),source.indexOf("function renderList()")),context);
const witch=context.data.cards.find(c=>c.id==="craftbound:witch");witch.name="My edited Witch";witch.effects[0].amount=0;
witch.effects.push({...witch.effects[0],action:"DRAW",card:""});
context.renderValidation();assert.ok(ids.warnings.children.slice(1).every(b=>typeof b.onclick==="function"));
let issue=context.validationIssues().find(e=>e.cardId===witch.id&&e.effectIndex===1&&e.field==="amount");
context.jumpToIssue(issue);assert.equal(context.selected,witch.id);assert.equal(context.view,"cards");
assert.equal(ids.search.value,"");assert.ok(fields[1].scrolled&&fields[1].focused&&fields[1].classList.contains("validation-focus"));assert.equal(fields[1].getAttribute("aria-invalid"),"true");
witch.effects[1].amount=1;context.renderValidation();assert.ok(!fields[1].classList.contains("validation-focus"));assert.equal(context.focusedIssue,null);
context.data.cards=context.data.cards.filter(c=>c.id!=="craftbound:minor_harming_potion");
issue=context.validationIssues().find(e=>e.code==="missing"&&e.cardId==="craftbound:minor_harming_potion");
context.jumpToIssue(issue);assert.equal(ids.showTokens.checked,true);assert.ok(ids.errorDetail.innerHTML.includes("missing from this draft"));
repair.onclick();assert.ok(context.data.cards.some(c=>c.id==="craftbound:minor_harming_potion"));assert.equal(witch.name,"My edited Witch");
const a=context.data.cards.findIndex(c=>!c.token),b=context.data.cards.findIndex((c,i)=>i>a&&!c.token);
[context.data.cards[a],context.data.cards[b]]=[context.data.cards[b],context.data.cards[a]];
issue=context.validationIssues().find(e=>e.code==="order");context.jumpToIssue(issue);repair.onclick();
assert.deepEqual(context.data.cards.filter(c=>!c.token).map(c=>c.id),base.cards.filter(c=>!c.token).map(c=>c.id));
assert.equal(witch.name,"My edited Witch");
context.data.cards=context.data.cards.filter(c=>!["craftbound:minor_harming_potion","craftbound:splash_potion_of_harming"].includes(c.id));
issue=context.validationIssues().find(e=>e.code==="missing");context.jumpToIssue(issue);restoreAll.onclick();
assert.ok(context.data.cards.some(c=>c.id==="craftbound:minor_harming_potion"));
assert.ok(context.data.cards.some(c=>c.id==="craftbound:splash_potion_of_harming"));
assert.equal(witch.name,"My edited Witch");
assert.ok(!context.validationIssues().some(e=>e.code==="missing"||e.code==="order"));
console.log("PASS: clickable errors navigate, clear filters, scroll/focus/highlight exact effect fields, clear resolved highlights, and repair missing cards/order without losing edits.");
