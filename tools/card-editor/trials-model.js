// Shared by the Workshop form and its local save endpoint.
(function(root) {
"use strict";
const words = text => text.split(" ");
const classes = words("FIGHTER BUILDER FARMER FISHERMAN ALCHEMIST MINER CHEF TAMER REDSTONER");
const boards = [...words("CLASSIC SPRUCE AMETHYST PRISMARINE"), ...classes.map(c=>"TRIAL_"+c)];
const enums = {
 classes, boards, tiers: words("REGULAR ELITE BOSS"), rewardBoards:boards.map(c=>c.toLowerCase()),
 backs:[...words("classic emerald redstone ender"), ...classes.map(c=>"trial_"+c.toLowerCase())].map(c=>"craftbound:"+c),
 kinds:words("FIGHT ELITE BOSS SHOP REST EVENT"), services:words("CARD CHARM REMOVE TRAIN"),
 operations:words("MARKS HEAL MAX_HEALTH OPENING_DRAW START_XP ADD_CARD REMOVE_CARD COPY_CARD SWAP_CARD TRAIN_CARD CHARM BOON SKIP"),
 triggers:words("MOB BLOCK ACTION TOOL ENCHANTMENT COST_AT_LEAST COST_AT_MOST ANY_CARD"),
 actions:words("BUFF_ATTACK BUFF_HEALTH DAMAGE_ENEMY TOOL_ATTACK DRAW HEAL_SELF"),
 abilities:words("NONE MOB_HEALTH PACK_ATTACK MOB_SPRINT BLOCK_HEALTH TOOL_DURABILITY HEAL_WOUNDED HEAVY_DRAW WAR_HORN DEATH_BLAST DEATH_SPLIT"),
 tribes:words("ANIMAL UNDEAD ARTHROPOD VILLAGER ILLAGER ENDER NETHER GOLEM SLIME CROP AQUATIC ORE TAMEABLE"),
 tags:words("MOB BLOCK ACTION TOOL ENCHANTMENT RANGED SPRINT SHIELD DROP DAMAGE HEAL SUMMON BUFF FREEZE")
};
const configBounds={draftSize:[1,50],minDeck:[6,50],maxDeck:[6,50],startHealth:[1,500],startLives:[1,9],startMarks:[0,100],offerCount:[1,6],shopStock:[1,6],charmSlots:[1,6],bossPacks:[1,16],cardPrice:[0,99],charmPrice:[0,99],removePrice:[0,99],trainPrice:[0,99]};
const passiveOps = words("MARKS HEAL MAX_HEALTH OPENING_DRAW START_XP");
const resourceId = /^[a-z0-9_.-]+:[a-z0-9_/.-]+$/;
function validate(d,cards=[]) {
 const errors=[];
 const issue=(path,message)=>errors.push({path,message});
 const isObject=o=>o!=null&&typeof o==="object"&&!Array.isArray(o);
 const object=(x,p)=>{if(!isObject(x))issue(p,"Expected an object.");return isObject(x)?x:{};};
 const rows=(x,p,min=0,max=100)=>{if(!Array.isArray(x)||x.length<min||x.length>max){issue(p,"Choose "+min+" to "+max+" entries.");return []}return x;};
 const str=(x,p,max,allowEmpty=false)=>{if(typeof x!=="string"||x.length>max||!allowEmpty&&!x.trim())issue(p,"Enter "+(allowEmpty?"up to ":"1 to ")+max+" characters.");};
 const one=(x,p,options)=>{if(!options.includes(x))issue(p,"Choose one of "+options.join(", ")+".");};
 const num=(x,p,min,max)=>{if(!Number.isInteger(x)||x<min||x>max)issue(p,"Enter a whole number from "+min+" to "+max+".");};
 const visit=(arr,p,min,max,f)=>rows(arr,p,min,max).forEach((x,i)=>f(object(x,p+"."+i),p+"."+i,i));
 function ids(arr,p,pattern) {
   const seen=new Set();
   for(const [i,e] of (Array.isArray(arr)?arr:[]).entries()) {
     if(typeof e?.id!=="string"||!pattern.test(e.id)||seen.has(e.id))issue(p+"."+i+".id","Give this entry a unique, stable ID.");
     seen.add(e?.id);
   }
 }
 function labelled(e,p){str(e.title,p+".title",80);str(e.description??"",p+".description",320,true);if(e.iconItem!=null&&String(e.iconItem).trim()!==""&&!resourceId.test(e.iconItem))issue(p+".iconItem","Use a Minecraft resource ID, such as minecraft:apple.");}
 function options(arr,p,boon) {
   ids(arr,p,/^[\w-]{1,48}$/);
   visit(arr,p,1,50,(o,k)=>{
     labelled(o,k);
     for(const key of ["costMarks","costHealth","limit"])if(key in o)num(o[key],k+"."+key,key==="limit"?1:0,key==="costMarks"?99:100);
     visit(o.operations,k+".operations",1,8,(op,q,i)=>{
       one(op.type,q+".type",enums.operations);
       if(passiveOps.includes(op.type))num(op.amount,q+".amount",1,100);
       else if(op.type!=="SKIP"&&i!==o.operations.length-1)issue(q,"Put a choice of cards, charms or boons last.");
       if(boon&&op.type==="BOON")issue(q,"A boon cannot offer another boon.");
     });
   });
 }
 if(!isObject(d))return [{path:"",message:"Expected a Trials book."}];
 if(d.version!==1)issue("version","Use Trials version 1.");
 const cfg=object(d.config,"config");
 for(const [key,[min,max]] of Object.entries(configBounds))num(cfg[key],"config."+key,min,max);
 if(cfg.minDeck>cfg.draftSize||cfg.draftSize>cfg.maxDeck)issue("config.draftSize","Starter draft size must fit between the minimum and maximum deck sizes.");
 const cardMap=new Map(cards.map(c=>[c.id,c]));
 const profiles=new Map(),tiers=new Set();
 ids(d.opponents,"opponents",/^[a-z0-9_]{1,48}$/);
 visit(d.opponents,"opponents",3,128,(o,p)=>{
   profiles.set(o.id,o);tiers.add(o.tier);
   str(o.name,p+".name",64);str(o.style,p+".style",200);
   for(const [key,list] of [["tier",enums.tiers],["heroClass",classes],["board",boards]])one(o[key],p+"."+key,list);
   for(const [key,min,max] of [["health",1,100],["healthStep",0,5],["deckSize",6,40],["deckStep",0,5],["bonusXp",0,3],["extraCards",0,3],["bounty",0,20]])num(o[key],p+"."+key,min,max);
   for(const [key,list,max] of [["anchors",null,80],["tags",enums.tags,enums.tags.length],["tribes",enums.tribes,enums.tribes.length]]) {
     const values=rows(o[key],p+"."+key,0,max);
     if(new Set(values).size!==values.length)issue(p+"."+key,"Remove duplicate entries.");
     for(const [i,value] of values.entries()) {
       if(list)one(value,p+"."+key+"."+i,list);
       else if(!resourceId.test(value))issue(p+"."+key+"."+i,"Use namespace:card_id.");
     }
   }
   const a=object(o.ability,p+".ability");
   one(a.kind,p+".ability.kind",enums.abilities);str(a.name,p+".ability.name",48);
   num(a.amount,p+".ability.amount",1,5);num(a.every,p+".ability.every",1,8);
   const c=cardMap.get(a.card);
   if(typeof a.card!=="string"||a.card&&(a.kind!=="DEATH_SPLIT"||!resourceId.test(a.card)||c&&(c.kind!=="MOB"||c.keywords?.includes("BLOCK"))))
      issue(p+".ability.card","Only splitting may reference a mob here. Leave blank for its own summoned child.");
 });
 for(const tier of enums.tiers)if(!tiers.has(tier))issue("opponents","Include a "+tier.toLowerCase()+" opponent.");
 const shop=object(d.shop,"shop");labelled(shop,"shop");ids(shop.items,"shop.items",/^[\w-]{1,48}$/);
 visit(shop.items,"shop.items",1,50,(o,p)=>{labelled(o,p);one(o.service,p+".service",enums.services);num(o.stock,p+".stock",1,20);if("price" in o)num(o.price,p+".price",0,99)});
 ids(d.events,"events",/^[\w-]{1,48}$/);
 visit(d.events,"events",1,50,(e,p)=>{labelled(e,p);options(e.options,p+".options",false)});
 options(d.rest,"rest",false);options(d.boons,"boons",true);
 ids(d.charms,"charms",/^[A-Za-z0-9_]{1,48}$/);
 visit(d.charms,"charms",0,100,(c,p)=>{
   str(c.title,p+".title",80);if(!resourceId.test(c.iconItem))issue(p+".iconItem","Enter a Minecraft item ID.");
   one(c.trigger,p+".trigger",enums.triggers);one(c.action,p+".action",enums.actions);
   num(c.amount,p+".amount",1,10); num(c.cost,p+".cost",0,20);
   if(c.action==="BUFF_ATTACK"&&c.trigger!=="MOB"||c.action==="BUFF_HEALTH"&&!["BLOCK","MOB"].includes(c.trigger)||c.action==="TOOL_ATTACK"&&c.trigger!=="TOOL")issue(p+".action","This buff requires a matching card trigger.");
 });
 const rewards=new Set();
 visit(d.rewards,"rewards",0,classes.length,(r,p)=>{
   one(r.heroClass,p+".heroClass",classes);
   if(rewards.has(r.heroClass))issue(p+".heroClass","Only one first-clear reward per class.");
   rewards.add(r.heroClass);one(r.cardBack,p+".cardBack",["",...enums.backs]);one(r.board,p+".board",["",...enums.rewardBoards]);
 });
 const act=object(d.act,"act");str(act.name,"act.name",80);
 if("topology" in act)one(act.topology,"act.topology",["AUTHORED","BRANCHING_V1","BRANCHING_V2"]);
 const generated=["BRANCHING_V1","BRANCHING_V2"].includes(act.topology),maxTemplates=act.topology==="BRANCHING_V2"?4:3;
 const floors=rows(act.floors,"act.floors",generated?7:2,30);let reachable=null;
 const eventIds=new Set((Array.isArray(d.events)?d.events:[]).map(e=>e?.id));
 floors.forEach((row,i)=>{
   const p="act.floors."+i,f=object(row,p),kinds=rows(f.kinds,p+".kinds",1,generated?maxTemplates:5),n=kinds.length;
   const last=i===floors.length-1;
   if(generated&&last&&n!==1)issue(p+".kinds","Generated routes converge at one final boss.");
   if(reachable&&kinds.some((_,lane)=>!reachable.has(lane)))issue(p+".kinds","Some lanes cannot be reached from the previous floor.");
   reachable=new Set();
   if("shuffle" in f&&typeof f.shuffle!=="boolean")issue(p+".shuffle","Choose on or off.");
   for(const key of ["titles","descriptions","events","opponents"])if(key in f)rows(f[key],p+"."+key,n,n).forEach((v,j)=>str(v,p+"."+key+"."+j,320,true));
   kinds.forEach((kind,lane)=>{
     one(kind,p+".kinds."+lane,enums.kinds); if((kind==="BOSS")!==last)issue(p+".kinds."+lane,"Every path must finish with a boss, on the last floor only.");
     const event=f.events?.[lane],enemy=f.opponents?.[lane];
     if(event&&(kind!=="EVENT"||!eventIds.has(event)))issue(p+".events."+lane,"Choose an existing event for an Event room.");
     if(enemy&&profiles.get(enemy)?.tier!==({FIGHT:"REGULAR",ELITE:"ELITE",BOSS:"BOSS"}[kind])||enemy&&!profiles.has(enemy))issue(p+".opponents."+lane,"Choose an existing opponent that matches this room's tier.");
   });
   rows(f.edges,p+".edges",n,n).forEach((edges,lane)=>{
     const dest=rows(edges,p+".edges."+lane,last?0:1,last?0:5);
     if(new Set(dest).size!==dest.length)issue(p+".edges."+lane,"List each next lane once.");
     for(const end of dest){num(end,p+".edges."+lane,0,(floors[i+1]?.kinds?.length||1)-1);reachable.add(end)}
   });
 });
 return errors;
}
const api={enums,configBounds,validate};
if(typeof module!=="undefined"&&module.exports)module.exports=api;
else root.TrialsModel=api;
})(globalThis);
