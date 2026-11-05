(() => {
"use strict";
const $ = s => document.querySelector(s), copy = o => structuredClone(o);
const E = (tag, text, cls) => { const e=document.createElement(tag); if(text!=null)e.textContent=text;if(cls)e.className=cls;return e; };
const btn=(label,fn,cls="")=>{const n=E("button",label,cls);n.type="button";n.addEventListener("click",fn);return n;};
const input=(type="text")=>{const n=E("input");n.type=type;return n;};
const navSections={
 act:["⌁","Route map","Choose the encounters along the climb. Use generated branches or draw your own connections."],
 opponents:["⚔","Opponents","Give each rival a playstyle, deck preferences, starting strength and a signature ability."],
 events:["✧","Events","Write encounters and the choices a player can make when visiting them."],
 shop:["◈","Villager shop","Set what the villager sells, how much it costs and how often each service is available."],
 rest:["♨","Camp choices","Choose what players can do when they reach a campfire."],
 boons:["☀","Boons","Make lasting benefits that carry through one expedition."],
 charms:["✥","Charms","Pair a trigger with an effect to reward particular deck strategies."],
 rewards:["✦","First clears","Assign the card back and board design awarded for a class’s first boss clear."],
 config:["⚙","Run settings","Tune starting resources, deck limits, draft size, offer counts and victory rewards."]
};
let state=null, defaults={}, cards=[], revision="", localPath="", section="act", active={}, floor=0,lane=0, dirty=false,busy=false, filter="", validation=[], saveStamp=0;
const token=location.hash.slice(1), tmpKey="craftbound.trials.draft.2";
const enums=k=>(window.TrialsModel?.enums?.[k]||[]).map(e=>typeof e==="string"?e:e.value??e.id);
const parts=p=>String(p).split("."), get=p=>parts(p).reduce((v,k)=>v?.[k],state);
function set(p,v){const ks=parts(p);let parent=state;for(let i=0;i<ks.length-1;i++){const k=ks[i];parent=parent[k]??=(/^\d+$/.test(ks[i+1])?[]:{});}if(v==null||(p==="act.topology"&&v===""))delete parent[ks.at(-1)];else parent[ks.at(-1)]=v;changed();}
const title=s=>s==="MARKS"?"Emeralds":String(s??"").replace(/([a-z])([A-Z])/g,"$1 $2").replace(/[_-]/g," ").toLowerCase().replace(/^\w/,c=>c.toUpperCase());
const indexFor=k=>Math.max(0,Math.min(active[k]||0,(get(k)?.length||1)-1));
function serial(){return JSON.stringify(state);}
function currencyLabels(value){
 let changed=false;
 if(value&&typeof value==="object")for(const [key,entry] of Object.entries(value)){
   if((key==="title"||key==="description")&&typeof entry==="string"){
     const next=entry.replace(/\bMarks\b/gi,"Emeralds").replace(/\bMark\b/gi,"Emerald");
     if(next!==entry){value[key]=next;changed=true;}
   }else if(entry&&typeof entry==="object")changed=currencyLabels(entry)||changed;
 }
 return changed;
}
function status(t){$("#saved").textContent=t;}
let persistTimer;
function changed(){dirty=true;saveStamp++;status("Unsaved changes");$("#save").disabled=busy; clearTimeout(persistTimer);persistTimer=setTimeout(remember,300);}
function remember(){if(state&&dirty)try{sessionStorage.setItem(tmpKey,JSON.stringify({revision,data:state}));}catch{}}
function clearDraft(){clearTimeout(persistTimer);try{sessionStorage.removeItem(tmpKey);}catch{}}
function mutate(fn){fn();changed();render();}
function selectSection(k){section=k;filter="";render();$("#main").scrollIntoView({block:"start"});}
function elAppend(parent,...nodes){for(const n of nodes)if(n)parent.append(n);return parent;}
function hr(){return E("hr");}
function sheet(parent,heading,description=""){const s=E("section",null,"sheet");if(heading)s.append(E("h2",heading));if(description)s.append(E("p",description,"hint"));parent.append(s);return s;}
function fields(parent){const g=E("div",null,"grid");parent.append(g);return g;}
function actions(parent,list){const n=E("div",null,"tools");for(const [label,fn,cls,disabled] of list){const b=btn(label,fn,cls||"small");b.disabled=!!disabled;n.append(b);}parent.append(n);return n;}
function mkLabel(parent,p,label,hint,wide){const l=E("label",null,"field"+(wide?" wide":""));l.append(E("span",label,"label"));parent.append(l);return l;}
function addField(parent,p,label,opts={}){
 const val=get(p), inline=opts.type==="check";
 const l=mkLabel(parent,p,label,opts.hint,opts.wide);
 let f;
 if(opts.options){f=E("select");const optsList=opts.options.map(v=>typeof v==="object"?v:{id:v,label:title(v)});
   if(val!=null&&val!==""&&!optsList.some(o=>o.id===val))optsList.push({id:val,label:String(val)+" (current)"});
   for(const o of optsList){const op=E("option",o.label||o.id);op.value=o.id;f.append(op);}f.value=val??"";}
 else if(opts.type==="long"){f=E("textarea"); f.value=val??"";f.rows=2;}
 else{f=input(inline?"checkbox":opts.type==="number"?"number":"text");if(inline)f.checked=!!val;else f.value=val??"";
   if(opts.min!=null)f.min=opts.min;if(opts.max!=null)f.max=opts.max;if(opts.list)f.setAttribute("list",opts.list);}
 f.dataset.path=p;if(opts.placeholder)f.placeholder=opts.placeholder;l.append(f);if(opts.hint)l.append(E("small",opts.hint,"hint"));
 const apply=()=>{let v=inline?f.checked:opts.type==="number"?(f.value===""?null:Number(f.value)):f.value;set(p,v);if(opts.refresh)render();};
 f.addEventListener(opts.options||inline?"change":"input",apply);
 if(!opts.refresh)f.addEventListener("change",()=>{syncTitle(p);});
 return f;
}
function syncTitle(p){
 if(/\.(name|title|id|heroClass)$/.test(p)){
  document.querySelectorAll("[data-record-path]").forEach(n=>{const path=n.dataset.recordPath,entry=get(path);if(entry&&p.startsWith(path+"."))n.textContent=recordName(entry);});
 }
}
function recordName(x){return x?.name||x?.title||title(x?.heroClass)||x?.id||"Untitled";}
function unique(arr,stem){let base=stem||"new_entry",id=base,i=2;while(arr.some(x=>x.id===id))id=base+"_"+i++;return id;}
function pickNewId(entry,pool){
 if("id"in entry)entry.id=unique(pool,entry.id.toLowerCase()===entry.id?entry.id+"_copy":entry.id+"_COPY");
 for(const k of ["name","title"])if(k in entry)entry[k]="Copy of "+entry[k];
 return entry;
}
function msg(t){status(t);}
const specs={
 opponents:{name:"opponent",new:()=>({id:"new_rival",name:"New rival",tier:"REGULAR",heroClass:"FIGHTER",board:"CLASSIC",style:"Cheap mobs contest the early board.",health:10,healthStep:0,deckSize:10,deckStep:0,bonusXp:0,extraCards:0,bounty:2,anchors:[],tribes:[],tags:[],ability:{name:"Opening move",kind:enums("abilities")[0]||"MOB_HEALTH",amount:1,every:1,card:""}})},
 events:{name:"event",new:()=>({id:"new_event",title:"New event",description:"",options:[{id:"PASS",title:"Move on",description:"Continue on your route.",operations:[{type:"SKIP"}]}]})},
 rest:{name:"camp choice",new:()=>({id:"REST_CHOICE",title:"Camp choice",description:"",operations:[{type:"HEAL",amount:2}]})},
 boons:{name:"boon",new:()=>({id:"NEW_BOON",title:"New boon",description:"",operations:[{type:"MAX_HEALTH",amount:1}],limit:3})},
 charms:{name:"charm",new:()=>({id:"NEW_CHARM",title:"New charm",iconItem:"minecraft:emerald",trigger:"MOB",action:"BUFF_ATTACK",amount:1,cost:0})},
 "shop.items":{name:"shop service",new:()=>({id:"NEW_SERVICE",title:"New service",description:"",service:"CARD",price:2,stock:1})},
 rewards:{name:"class reward",new:()=>({heroClass:enums("classes").find(c=>c!=="NEUTRAL"&&!state.rewards.some(x=>x.heroClass===c))||"FIGHTER",cardBack:"",board:""})}
};
function collection(root,path,edit){
 const list=get(path),spec=specs[path],sel=indexFor(path);
 const wrap=E("div",null,"split");root.append(wrap);
 const rail=sheet(wrap,"Library"), detail=sheet(wrap,"");
 const search=input("search"); search.className="picker";search.placeholder="Find "+spec.name+"…";search.setAttribute("aria-label","Filter "+spec.name+" list");search.value=filter;rail.append(search);
 const shelf=E("div",null,"collection-list");rail.append(shelf);
 function showEntries(){
  shelf.replaceChildren();list.forEach((o,i)=>{if(filter&&!JSON.stringify(o).toLowerCase().includes(filter.toLowerCase()))return;
   const b=btn(recordName(o),()=>{active[path]=i;render();});b.dataset.recordPath=path+"."+i;b.setAttribute("aria-pressed",i===sel?"true":"false");shelf.append(b);
  });
  if(!shelf.childElementCount)shelf.append(E("span","No matching entries","empty"));
 }
 search.oninput=()=>{filter=search.value;showEntries();};showEntries();
 actions(rail,[[ "+ New "+spec.name,()=>mutate(()=>{const q=spec.new();if(q.id)q.id=unique(list,q.id);list.push(q);active[path]=list.length-1;filter="";}),"small"]]);
 if(!list.length){detail.append(E("p","Create the first "+spec.name+" to begin.","empty"));return;}
 const e=list[sel],p=path+"."+sel,hd=E("div",null,"record-head"),name=E("h2",recordName(e));name.dataset.recordPath=p;hd.append(name);detail.append(hd);
 actions(hd,[["Duplicate",()=>mutate(()=>{let q=pickNewId(copy(e),list);if(path==="rewards")q.heroClass=spec.new().heroClass;list.splice(sel+1,0,q);active[path]=sel+1;filter="";}),"small"],
 ["Delete",()=>{if(confirm("Delete “"+recordName(e)+"” from this draft?"))mutate(()=>{list.splice(sel,1);active[path]=Math.max(0,sel-1);});},"small danger"]]);
 edit(detail,p,e);
}
function tags(parent,p,options,label){
 const box=E("div",null,"field wide");box.append(E("strong",label,"label"));const wrap=E("div",null,"tags");wrap.dataset.path=p;wrap.tabIndex=-1;box.append(wrap);parent.append(box);
 const arr=get(p)||[];for(const v of [...new Set([...options,...arr])]){const l=E("label"), cb=input("checkbox");cb.checked=arr.includes(v);l.append(cb,document.createTextNode(title(v)));wrap.append(l);cb.onchange=()=>set(p,cb.checked?[...get(p)||[],v]:(get(p)||[]).filter(x=>x!==v));}
}
function anchors(parent,p){const part=sheet(parent,"Preferred cards","These existing cards guide the rival’s deck. Earlier entries take priority.");const rows=get(p)||[];
 rows.forEach((v,i)=>{const r=E("div",null,"inline");part.append(r);const k=p+"."+i, f=input();f.className="picker";f.dataset.path=k;f.setAttribute("list","cards");f.setAttribute("aria-label","Preferred card "+(i+1));f.value=v;r.append(f);f.onchange=()=>set(k,f.value.trim());actions(r,[["↑",()=>mutate(()=>{[rows[i-1],rows[i]]=[rows[i],rows[i-1]];}),"small",i===0],["Remove",()=>mutate(()=>rows.splice(i,1)),"small"]]);});
 const pick=input();pick.className="picker";pick.setAttribute("list","cards");pick.placeholder="Find a card by name or ID";pick.setAttribute("aria-label","Add a preferred card");const row=E("div",null,"inline");row.append(pick,btn("Add",()=>{const v=pick.value.trim();if(!v)return;if(!rows.includes(v))mutate(()=>rows.push(v));}));part.append(row);
}
function numFields(parent,base,descriptions){
 for(const [k,cap,hint] of descriptions)addField(parent,base+"."+k,cap,{type:"number",hint,min:0});
}
const emptyOpt=(xs,label="Automatic")=>[{id:"",label},...xs.map(s=>typeof s==="object"?s:{id:s,label:title(s)})];
function opponent(parent,p,e){
 let g=fields(parent);addField(g,p+".name","Rival name");addField(g,p+".id","Stable ID");addField(g,p+".tier","Room tier",{options:enums("tiers")});
 addField(g,p+".heroClass","Hero class",{options:enums("classes").filter(v=>v!=="NEUTRAL")});addField(g,p+".board","Battle board",{options:enums("boards")});
 addField(g,p+".style","Playstyle and counterplay",{type:"long",wide:true,hint:"Players read this before committing to the encounter."});
 const battle=sheet(parent,"Starting strength");g=fields(battle);numFields(g,p,[["health","Health"],["healthStep","Health growth"],["deckSize","Deck size"],["deckStep","Deck growth"],["extraCards","Extra opening cards"],["bonusXp","Starting XP bonus"],["bounty","Emeralds for winning"]]);
 const ability=sheet(parent,"Signature ability");g=fields(ability);addField(g,p+".ability.name","Ability name");
 addField(g,p+".ability.kind","Behaviour",{options:enums("abilities")});numFields(g,p+".ability",[["amount","Amount"],["every","Every N turns"]]);
 addField(g,p+".ability.card","Card used by the ability",{list:"cards",placeholder:"Choose a card for a split or summon",wide:true});
 anchors(parent,p+".anchors");
 const pref=sheet(parent,"Deck preferences");tags(pref,p+".tribes",enums("tribes"),"Mob families");tags(pref,p+".tags",enums("tags"),"Preferred mechanics");
}
function optionBody(parent,p,e){
 const g=fields(parent);addField(g,p+".title","Choice name");addField(g,p+".id","Stable ID");addField(g,p+".description","What the player sees",{type:"long",wide:true});
 addField(g,p+".iconItem","Minecraft icon",{placeholder:"minecraft:book"});addField(g,p+".costMarks","Emeralds paid",{type:"number",min:0});addField(g,p+".costHealth","Health paid",{type:"number",min:0});
 addField(g,p+".limit","Maximum uses",{type:"number",min:0,hint:"Leave blank for the default."});
 const ops=sheet(parent,"Effects","Effects resolve in order. A card choice can open a follow-up screen.");
 const opList=e.operations||[];
 opList.forEach((op,i)=>{let row=E("div",null,"nested");ops.append(row);let hd=E("div",null,"record-head");hd.append(E("h3","Step "+(i+1)));row.append(hd);
 actions(hd,[["↑",()=>mutate(()=>{[opList[i-1],opList[i]]=[opList[i],opList[i-1]];}),"small",i===0],["Remove",()=>mutate(()=>opList.splice(i,1)),"small"]]);
 const gg=fields(row);addField(gg,p+".operations."+i+".type","Effect",{options:enums("operations")});addField(gg,p+".operations."+i+".amount","Amount",{type:"number"});});
 actions(ops,[["+ Effect",()=>mutate(()=>{(e.operations??=[]).push({type:"HEAL",amount:1});})]]);
}
function eventEditor(parent,p,e){
 const g=fields(parent);addField(g,p+".title","Event title");addField(g,p+".id","Stable ID");addField(g,p+".description","Scene",{type:"long",wide:true});
 const box=sheet(parent,"Player choices");e.options.forEach((o,i)=>{const el=E("details",null,"nested");el.dataset.option=i;el.open=active[p+".option"]===i || i===0;el.append(E("summary",o.title||"Choice "+(i+1)));box.append(el);optionBody(el,p+".options."+i,o);actions(el,[["Duplicate choice",()=>mutate(()=>{e.options.splice(i+1,0,pickNewId(copy(o),e.options));active[p+".option"]=i+1;})],["Delete choice",()=>mutate(()=>e.options.splice(i,1)),"small danger"]]);});
 actions(box,[["+ Player choice",()=>mutate(()=>{e.options.push({id:unique(e.options,"CHOICE"),title:"New choice",description:"",operations:[{type:"SKIP"}]});active[p+".option"]=e.options.length-1;})]]);
}
function charmEditor(parent,p){const g=fields(parent);addField(g,p+".title","Charm name");addField(g,p+".id","Stable ID");addField(g,p+".iconItem","Minecraft item icon");
 const s=sheet(parent,"Once per turn","Choose which card trips the charm, then set what happens.");const gg=fields(s);addField(gg,p+".trigger","When you play…",{options:enums("triggers")});
 addField(gg,p+".cost","XP threshold",{type:"number",hint:"Used by cost-based triggers."});addField(gg,p+".action","Effect",{options:enums("actions")});addField(gg,p+".amount","Amount",{type:"number"});}
function shopEditor(parent,p){const g=fields(parent);addField(g,p+".title","Service name");addField(g,p+".id","Stable ID");addField(g,p+".description","Description",{type:"long",wide:true});addField(g,p+".service","Service",{options:enums("services")});
 addField(g,p+".price","Price in Emeralds",{type:"number",min:0,hint:"Leave blank to use the run setting for this service."});addField(g,p+".stock","Uses per visit",{type:"number",min:0});addField(g,p+".iconItem","Minecraft item icon");}
function rewardEditor(parent,p){const g=fields(parent);addField(g,p+".heroClass","Winning class",{options:enums("classes").filter(c=>c!=="NEUTRAL")});
 addField(g,p+".cardBack","Card back",{options:emptyOpt(enums("backs"),"No card back")});addField(g,p+".board","Board design",{options:emptyOpt(enums("rewardBoards"),"No board design")});
 parent.append(E("p","The first time a player clears the boss with this class, they earn these cosmetics.","note"));}
function configEditor(parent){
 const groups=[
 ["Starting your run",[["draftSize","Opening draft size"],["offerCount","Options per offer"],["startLives","Starting lives"],["startHealth","Starting Health"],["startMarks","Starting Emeralds"]]],
 ["Deck and kit",[["minDeck","Minimum deck size"],["maxDeck","Maximum deck size"],["charmSlots","Charm slots"],["bossPacks","Packs from boss victory"]]],
 ["Shop defaults",[["cardPrice","Card price"],["charmPrice","Charm price"],["removePrice","Remove price"],["trainPrice","Training price"],["shopStock","Card options per shelf"]]]
 ];for(const [label,items] of groups)numFields(fields(sheet(parent,label)),"config",items);
}
const laneArrays=["titles","descriptions","opponents","events"];
const kindShort={FIGHT:"Battle",ELITE:"Elite",BOSS:"Boss",EVENT:"Event",REST:"Camp",SHOP:"Shop"};
function route(root){
 const varied=state.act.topology==="BRANCHING_V2",generated=varied||state.act.topology==="BRANCHING_V1",fs=state.act.floors;if(!fs.length){root.append(E("p","Add at least two floors.","empty"));return;}
 floor=Math.max(0,Math.min(floor,fs.length-1)); const row=fs[floor];lane=Math.max(0,Math.min(lane,row.kinds.length-1));
 const at="act.floors."+floor, last=floor===fs.length-1, count=Math.max(1,...fs.map(f=>f.kinds.length));
 const top=sheet(root,"Act map");const names=fields(top);addField(names,"act.name","Act name");
 addField(names,"act.topology","Path layout",{options:[{id:"",label:"Authored paths (legacy)"},{id:"BRANCHING_V2",label:"Varied branches (up to four)"},{id:"BRANCHING_V1",label:"Original generated branches"},{id:"AUTHORED",label:"Draw paths yourself"}],refresh:true});
 top.append(E("p",generated?(varied?"Each seed creates two to four routes through these encounter templates. Routes have different numbers of rooms and meet at crossroads. Shorter paths reach tougher opponents sooner, with fewer chances to recover or earn rewards. All routes lead to the final boss.":"Each seed creates two or three committed paths through these encounter templates, meeting at crossroads after a few rooms. All paths end at the final boss. Unvisited events appear as mysteries."):"Select a room to edit it. Lines show the next rooms it can reach. A seed can shuffle the room types within each floor.","hint"));
 const scroll=E("div",null,"graph-wrap");scroll.id="routeGraph";top.append(scroll);
 const ns="http://www.w3.org/2000/svg",svg=(t,att={},txt="")=>{const e=document.createElementNS(ns,t);for(const [k,v] of Object.entries(att))e.setAttribute(k,v);if(txt)e.textContent=txt;return e;};
 const ww=fs.length*90+28,hh=39+count*42, graph=svg("svg",{width:ww,height:hh,viewBox:"0 0 "+ww+" "+hh,class:"graph",role:"group","aria-label":"Editable act route"});scroll.append(graph);
 const px=n=>58+n*90,py=(fi,idx)=>44+(count-fs[fi].kinds.length)*21+idx*42;
 fs.forEach((f,fi)=>{if(generated||fi+1>=fs.length)return;(f.edges||[]).forEach((targets,li)=>{for(const dest of targets||[]){if(dest<0||dest>=fs[fi+1].kinds.length)continue;
   graph.append(svg("path",{d:"M"+px(fi)+" "+py(fi,li)+" L"+px(fi+1)+" "+py(fi+1,dest),class:"link"+(fi===floor&&li===lane||fi+1===floor&&dest===lane?" active":"")}));}});});
 fs.forEach((f,fi)=>{graph.append(svg("text",{x:px(fi),y:17,class:"col"},"FLOOR "+(fi+1)));f.kinds.forEach((kind,li)=>{
  const sel=fi===floor&&li===lane,n=svg("g",{class:"node"+(sel?" active":""),tabindex:0,role:"button","aria-label":"Floor "+(fi+1)+", lane "+(li+1)+", "+(kindShort[kind]||kind),"aria-pressed":sel,"data-floor":fi,"data-lane":li});
  n.append(svg("title",{},f.titles?.[li]||kindShort[kind]||kind),svg("rect",{x:px(fi)-30,y:py(fi,li)-13,width:60,height:26,rx:5}),svg("text",{x:px(fi),y:py(fi,li)+4},kindShort[kind]||kind));
  const jump=()=>{floor=fi;lane=li;render();};n.onclick=jump;n.onkeydown=e=>{if(["Enter"," "].includes(e.key)){e.preventDefault();jump();}};graph.append(n);
 });});
 const info=E("div",null,"route-info");top.append(info);info.append(E("span",fs.length+" floors · "+fs.reduce((n,f)=>n+f.kinds.length,0)+(generated?" templates":" rooms"),"badge"));
 const ctl=E("div");info.append(ctl);actions(ctl,[
 ["+ Floor",()=>mutate(()=>insertFloor(false)),"small",fs.length>=30],
 ["Duplicate floor",()=>mutate(()=>insertFloor(true)),"small",fs.length>=30||last],
 ["Delete floor",()=>{if(confirm("Remove floor "+(floor+1)+" and reconnect the route?"))mutate(()=>removeFloor());},"small danger",fs.length<=(generated?7:2)||last]
 ]);
 const edit=sheet(root,"Floor "+(floor+1)+" · Lane "+(lane+1));
 const lanes=E("div",null,"chips");edit.append(lanes);row.kinds.forEach((r,i)=>lanes.append(btn("Lane "+(i+1)+" · "+(kindShort[r]||r),()=>{lane=i;render();},"chip"+(i===lane?" active":""))));
 actions(edit,[["+ Lane",()=>mutate(addLane),"small",row.kinds.length>=(generated?(varied?4:3):5)||(generated&&last)],["Delete lane",()=>mutate(removeLane),"small danger",row.kinds.length<2]]);
 const g=fields(edit);const kind=row.kinds[lane], choices=last?["BOSS"]:enums("kinds").filter(k=>k!=="BOSS");
 const kindControl=addField(g,at+".kinds."+lane,"Room kind",{options:choices});kindControl.addEventListener("change",()=>{
  if(row.events)row.events[lane]="";if(row.opponents)row.opponents[lane]="";render();
 });
 addField(g,at+".shuffle","Shuffle room types across this floor",{type:"check"});
 // Override arrays remain absent until actually edited. Once present, all lanes have a string.
 function override(key,label,opts){const k=at+"."+key+"."+lane;const field=addField(g,k,label,opts);field.addEventListener("input",fix);field.addEventListener("change",fix);
  function fix(){if(row[key])row[key]=Array.from({length:row.kinds.length},(_,i)=>row[key][i]??"");}return field;}
 override("titles","Room title override",{placeholder:"Use the generated name"});
 if(kind==="EVENT")override("events","Event for this room",{options:emptyOpt(state.events.map(o=>({id:o.id,label:o.title})),"Seeded event")});
 const tier={FIGHT:"REGULAR",ELITE:"ELITE",BOSS:"BOSS"}[kind];
 if(tier)override("opponents","Encounter for this room",{options:emptyOpt(state.opponents.filter(o=>o.tier===tier).map(o=>({id:o.id,label:o.name})),"Seeded "+title(tier)+" encounter")});
 override("descriptions","Map description override",{type:"long",wide:true,placeholder:"Use the room’s description"});
 const edgeBox=sheet(edit,generated?"Generated connections":"Paths forward",last?"The boss ends the expedition.":generated?"Connections are generated from the run seed. Switch to “Draw paths yourself” to edit the stored paths.":"Choose every lane players may reach from this position.");edgeBox.dataset.path=at+".edges."+lane;edgeBox.tabIndex=-1;
 if(!last&&!generated) {
  const links=E("div",null,"links");edgeBox.append(links);fs[floor+1].kinds.forEach((k,i)=>{const l=E("label",null,"link-check"),cb=input("checkbox");cb.checked=row.edges[lane].includes(i);cb.onchange=()=>mutate(()=>{row.edges[lane]=cb.checked?[...row.edges[lane],i].sort((a,b)=>a-b):row.edges[lane].filter(n=>n!==i);});l.append(cb,document.createTextNode("Lane "+(i+1)+" · "+(kindShort[k]||k)));links.append(l);});
 }
 const chosen=graph.querySelector(".node.active"); if(chosen){const near=px(floor)-Math.max(30,Math.round(scroll.clientWidth*.5));scroll.scrollLeft=Math.max(0,near);}
}
function insertFloor(duplicate){
 const fs=state.act.floors;const at=Math.min(floor+1,fs.length-1),prev=fs[at-1],next=fs[at];
 if(duplicate&&prev){const clone=copy(prev);prev.edges=prev.kinds.map((_,i)=>[i]);fs.splice(at,0,clone);}
 else {const row={kinds:["FIGHT"],shuffle:false,edges:[next.kinds.map((_,i)=>i)]};if(prev)prev.edges=prev.kinds.map(()=>[0]);fs.splice(at,0,row);}
 floor=at;lane=0;
}
function removeFloor(){const fs=state.act.floors,mid=fs[floor],prev=fs[floor-1];
 if(prev)prev.edges=prev.edges.map(e=>[...new Set(e.flatMap(n=>mid.edges[n]||[]))].sort((a,b)=>a-b));
 fs.splice(floor,1);floor=Math.max(0,floor-1);lane=0;
}
function addLane(){const fs=state.act.floors,f=fs[floor],i=f.kinds.length;f.kinds.push(f.kinds[lane]);f.edges.push([...f.edges[lane]]);for(const key of laneArrays)if(f[key])f[key].push(f[key][lane]??"");
 if(floor>0)for(const edges of fs[floor-1].edges)if(edges.includes(lane))edges.push(i);
 lane=i;
}
function removeLane(){const fs=state.act.floors,f=fs[floor],old=lane; const removedOut=f.edges[old];
 f.kinds.splice(old,1);f.edges.splice(old,1);for(const key of laneArrays)if(f[key])f[key].splice(old,1);
 lane=Math.min(old,f.kinds.length-1);f.edges[lane]=[...new Set([...f.edges[lane],...removedOut])].sort((a,b)=>a-b);
 if(floor>0)fs[floor-1].edges=fs[floor-1].edges.map(path=>[...new Set(path.map(dest=>dest===old?lane:dest>old?dest-1:dest))].sort((a,b)=>a-b));
}
function render(){
 const main=$("#editor"), nav=$("#sections"); main.replaceChildren();nav.replaceChildren();
 for(const [k,[icon,label]] of Object.entries(navSections)){const b=btn("",()=>selectSection(k));const g=E("span",icon,"glyph");g.setAttribute("aria-hidden","true");b.append(g,document.createTextNode(label));b.dataset.section=k;if(k===section)b.setAttribute("aria-current","page");nav.append(b);}
 if(!state){main.append(E("p","Open the local Workshop to edit your campaign.","note"));return}
 const heading=E("div",null,"heading"), meta=navSections[section]; heading.append(E("small",state.act.name||"Expedition", "eyebrow"),E("h1",meta[1]),E("p",meta[2]));main.append(heading);
 switch(section){
 case "act":route(main);break;
 case "opponents":collection(main,"opponents",opponent);break;
 case "events":collection(main,"events",eventEditor);break;
 case "shop":{const g=fields(sheet(main,"The stall"));addField(g,"shop.title","Shop name");addField(g,"shop.description","Introduction",{type:"long"});collection(main,"shop.items",shopEditor);break}
 case "rest":case "boons":collection(main,section,optionBody);break;
 case "charms":collection(main,"charms",charmEditor);break;
 case "rewards":collection(main,"rewards",rewardEditor);break;
 case "config":configEditor(main);break;
 }
 renderIssues();
}
function renderIssues(){
 const box=$("#issues");box.replaceChildren();if(!validation.length)return;
 const err=E("details",null,"alert");err.open=true;err.append(E("summary",validation.length+" issue"+(validation.length===1?"":"s")+" to fix"));box.append(err);
 validation.slice(0,25).forEach(issue=>{const b=btn((issue.path||"Campaign")+" — "+issue.message,()=>goIssue(issue.path));err.append(b);});
 if(validation.length>25)err.append(E("small","Showing the first 25 issues. Save again after fixing them."));
}
function goIssue(path){
 const ks=parts(path);section=ks[0] in navSections?ks[0]:"config";filter="";
 if(section==="act"){floor=Math.max(0,Number(ks[2])||0);lane=Math.max(0,Number(ks[4])||0);}
 else if(section==="shop"){if(ks[1]==="items"&&ks[2]!=null)active["shop.items"]=+ks[2];}
 else if(ks[1]!=null && /^\d+$/.test(ks[1])){active[section]=+ks[1];if(section==="events"&&ks[2]==="options")active["events."+ks[1]+".option"]=+ks[3];}
 render();
 let focus=path;const fields=[...document.querySelectorAll("[data-path]")];let target;
 while(focus){target=fields.find(e=>e.dataset.path===focus)||fields.find(e=>e.dataset.path.startsWith(focus+"."));if(target)break;focus=focus.split(".").slice(0,-1).join(".");}
 if(target){for(let n=target.parentElement;n;n=n.parentElement)if(n.tagName==="DETAILS")n.open=true;target.classList.add("bad");target.scrollIntoView({block:"center"});target.focus({preventScroll:true});}
}
async function request(method,body) {
 const res=await fetch("/api/trials",{method,headers:{"X-Workshop-Token":token,...(body?{"Content-Type":"application/json"}:{})},...(body?{body:JSON.stringify(body)}:{})});
 const result=await res.json();
 if(!res.ok){const error=new Error(result.error||"Could not save Trials.");error.status=res.status;error.details=result.issues||result.errors;throw error;}
 return result;
}
async function load(force=false){
 if(busy)return;
 if(force&&dirty&&!confirm("Discard unsaved changes and reload the active Trials file?"))return;
 busy=true;$("#reload").disabled=true;$("#save").disabled=true;
 try{const r=await request("GET");state=r.data;defaults=r.defaults||{};cards=r.cards||[];revision=r.revision;localPath=r.path||"trials.json";dirty=false;validation=[];
 if(!force)try{const raw=JSON.parse(sessionStorage.getItem(tmpKey)||"null");if(raw?.revision===revision&&raw?.data?.act){state=raw.data;dirty=true;}}catch{}
 dirty=currencyLabels(state)||dirty;currencyLabels(defaults);
 if(force)clearDraft();
 const dl=$("#cards");dl.replaceChildren();for(const c of cards){if(!c.id)continue;const o=E("option");o.value=c.id;o.label=c.name||c.id;dl.append(o);}
 status(dirty?"Restored your unsaved draft":"Loaded active campaign");$("#export").disabled=false;render();
 }catch(e){status(e.message);const card=E("div",null,"alert");card.append(E("strong","Could not open the Trials editor"),E("p",e.message),E("p","Use Open Card Workshop.cmd, then choose The Trials."));$("#issues").replaceChildren(card);}
 finally{busy=false;$("#reload").disabled=false;$("#save").disabled=!dirty;}
}
async function save(){
 if(!state||busy||!dirty)return;
 validation=window.TrialsModel.validate(state,cards);
 if(validation.length){renderIssues();goIssue(validation[0].path);status("Fix the highlighted fields before saving");return;}
 busy=true;const snapshot=copy(state), stamp=saveStamp;$("#save").disabled=true;$("#reload").disabled=true;status("Saving…");
 try{const r=await request("POST",{data:snapshot,revision});revision=r.revision;localPath=r.path||localPath;dirty=saveStamp!==stamp;
 if(dirty)remember();else clearDraft();status(dirty?"Earlier edits saved. Newer changes are still unsaved.":"Saved to "+localPath);
 $("#issues").replaceChildren();
 }catch(e){status(e.status===409?"The campaign changed on disk. Export your draft before reloading.":e.message);
 if(Array.isArray(e.details)){validation=e.details.map(x=>typeof x==="string"?{path:"",message:x}:x);renderIssues();}
 }finally{busy=false;$("#reload").disabled=false;$("#save").disabled=!dirty;}
}
$("#export").onclick=()=>{
 if(!state)return;
 const url=URL.createObjectURL(new Blob([JSON.stringify(state,null,2)+"\n"],{type:"application/json"}));
 const a=E("a");a.href=url;a.download="trials.json";a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
 status("Exported a copy of this campaign.");
};
$("#save").onclick=save;$("#reload").onclick=()=>load(true);
window.addEventListener("beforeunload",e=>{if(dirty){remember();e.preventDefault();e.returnValue="";}});
document.addEventListener("keydown",e=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==="s"){e.preventDefault();save();}});
load();
})();
