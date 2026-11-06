"use strict";
const M=CardModel, $=id=>document.getElementById(id), esc=s=>String(s??"").replace(/[&<>"']/g,x=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[x]));
const classColors={"NEUTRAL":"#756B62","FIGHTER":"#A83749","BUILDER":"#3C63B5","FARMER":"#528D32","FISHERMAN":"#208E9E","ALCHEMIST":"#8051B5","MINER":"#B18B22","CHEF":"#CF713A","TAMER":"#AD558A","REDSTONER":"#B14428"};
const colors={COMMON:"#a9b3aa",RARE:"#68a1ee",EPIC:"#c77ce2",LEGENDARY:"#f2bd60"};
const rulesHtml=text=>esc(text).replace(/\b((?:After|When|At the end of|At the start of)[^.:\n]+(?=:)|At the end of your turn|After your hero attacks|After your hero deals attack damage|After your tool kills a mob or block|After this destroys a mob or block|After this kills a mob or block|After this deals damage|After attacking|When attacked|Your tool gains|After attack|End turn|Activate|Death|Activate|Play|Ignore Shield|Shield|Sprint|Ranged|Spawn|Drop|Cleanse|Repair|Harvest|On kill|Card added|Action played|Mined|Healed|Well Fed)\b/g,word=>word==="Cleanse"?'<strong title="Removes all buffs and debuffs. Printed abilities remain.">Cleanse</strong>':'<strong>'+word+'</strong>');
const key="craftbound-card-workshop-v1:"+ (CRAFTBOUND_DEFAULTS.workshopEpoch||"legacy");
let data=M.clone(CRAFTBOUND_DEFAULTS), baseline=M.clone(CRAFTBOUND_DEFAULTS), selected=data.cards.find(c=>!c.deleted&&!c.token)?.id||"";
let handle=null, fileName="", dirty=false, view="cards", recovery=false, draftTimer;
const autoIds=new Set();
const localWorkshop=location.hostname==="127.0.0.1" && location.hash.length>1;
const workshopToken=localWorkshop?location.hash.slice(1):"";
let activePath="",saving=false,activeSaveHandle=null;
async function workshopRequest(method="GET",body){
 const response=await fetch("/api/cards",{method,headers:{"X-Workshop-Token":workshopToken,"Content-Type":"application/json"},body});
 const result=await response.json();if(!response.ok)throw Error(result.error||"Workshop connection failed");return result;
}
try { const saved=JSON.parse(localStorage.getItem(key)||"null"); if(saved?.data?.version===1&&saved.data.cards?.length&&saved.data.cards.every(c=>Array.isArray(c.effects)&&Array.isArray(c.keywords))) {data=saved.data;baseline=saved.baseline||baseline;selected=data.cards.find(c=>!c.deleted&&!c.token)?.id||"";dirty=true;recovery=true;} } catch {}
let focusedIssue=null,cardOverride=null;
const card=()=>cardOverride?.id===selected&&data.cards.includes(cardOverride)?cardOverride:data.cards.find(c=>c.id===selected);
const locked=c=>baseline.cards.some(x=>x.id===c.id);
try { const saved=JSON.parse(localStorage.getItem(key)||"null");for(const id of saved?.autoIds||[])if(data.cards.some(c=>c.id===id&&!locked(c)))autoIds.add(id); } catch {}
function status(s,error=false){$("status").textContent=s;$("status").classList.toggle("error",error)}
// After a save the Workshop host rebuilds the player jar; show its progress after the save message.
let releaseTimer=null;
function watchRelease(){
 clearTimeout(releaseTimer);
 const base=$("status").textContent.replace(/ · Release jar.*$/,"");
 const poll=async()=>{
  try{
   const r=await (await fetch("/api/release",{headers:{"X-Workshop-Token":workshopToken}})).json();
   if(!r.enabled)return;
   const tail=r.status==="ready"?" · Release jar ready: "+r.jar:r.status==="failed"?" · "+r.message:" · Release jar building…";
   status(base+tail,r.status==="failed");
   if(r.status==="queued"||r.status==="building")releaseTimer=setTimeout(poll,2000);
  }catch{}
 };
 releaseTimer=setTimeout(poll,500);
}
function options(values,value,labels={}){return values.map(v=>'<option value="'+esc(v)+'"'+(v===value?' selected':'')+'>'+esc(labels[v]??M.title(v))+'</option>').join("")}
function field(label,key,value,type="text",extra=""){return '<label>'+esc(label)+'<input data-field="'+key+'" type="'+type+'" value="'+esc(value)+'" '+extra+'></label>'}
// The optional "If ..." boost on number effects. Its inputs use data-boost, not data-field, because they edit f.boost.
function boostFields(f){
 const b=f.boost||{},whens=M.BOOST_WHEN.filter(w=>!(w.startsWith("TARGET_")&&["DRAW","GAIN_XP"].includes(f.action)));
 const pickBox=(label,key,values,value,labels={})=>'<label>'+esc(label)+'<select data-boost="'+key+'">'+options(values,value,labels)+'</select>'+(key==="when"&&b.when?'<span class="hint">'+esc(M.GUIDE.boost[b.when]||"")+'</span>':"")+'</label>';
 let out='<div class="fields two">'+pickBox("Boost (optional)","when",[""].concat(whens),b.when||"",{"":"No boost",YOU_CONTROL:"If you control…",ENEMY_CONTROLS:"If an enemy controls…",ANYONE_CONTROLS:"If either side controls…",TARGET_IS:"Against targets that are…",TARGET_FROZEN:"Against Frozen targets",TARGET_WITHERED:"Against Withered targets",TARGET_DAMAGED:"Against damaged targets"});
 if(!b.when)return out+'</div>';
 out+=pickBox("Boost by","how",M.BOOST_HOW,b.how||"DOUBLE",{DOUBLE:"Double it",TRIPLE:"Triple it",PLUS:"Add a bonus"})+'</div>';
 if(b.how==="PLUS")out+='<div class="fields two"><label>Bonus<input data-boost="amount" type="number" min="1" max="20" value="'+esc(b.amount??1)+'"></label></div>';
 if(["YOU_CONTROL","ENEMY_CONTROLS","ANYONE_CONTROLS","TARGET_IS"].includes(b.when)){
  const cardsOnBoard=data.cards.filter(x=>x.kind==="MOB"&&(!x.deleted||x.id===b.card));
  out+='<div class="fields two">'+pickBox("Specific card","card",[""].concat(cardsOnBoard.map(x=>x.id)),b.card||"",Object.assign({"":"Any matching card"},Object.fromEntries(cardsOnBoard.map(x=>[x.id,x.name]))))
   +(b.card?"":pickBox("Card kind","kind",["MOB","BLOCK","BOTH"],b.kind||"BOTH",{MOB:"Mob",BLOCK:"Block",BOTH:"Mob or block"})+pickBox("Mob type","type",M.TYPES,b.type||"",{"":"Any type"}))+'</div>';
 }
 return out+'<p class="muted">Boosts change this effect\'s number when the condition is met; card text shows it in brackets.</p>';
}
function select(label,key,values,value,extra="",labels={}){return '<label>'+esc(label)+'<select data-field="'+key+'" '+extra+'>'+options(values,value,labels)+'</select>'+(M.GUIDE[key]?'<span class="hint">'+esc(M.GUIDE[key][value]||"")+'</span>':'')+'</label>'}
function remember(){clearTimeout(draftTimer);draftTimer=setTimeout(()=>{try{localStorage.setItem(key,JSON.stringify({data,baseline,autoIds:[...autoIds]}))}catch{status("Browser draft could not be saved. Save your JSON file.",true)}},250)}
function changed(){refreshAnimations();dirty=true;remember();renderList();renderPreview();renderValidation();$("heading").textContent=card()?.name||"Card details";$("dirty").textContent="Unsaved changes";if(view==="balance")renderCharts()}
const issueKey=issue=>JSON.stringify([issue.message,issue.cardIndex,issue.effectIndex,issue.field]);
function validationIssues(){
 M.prepareCatalog(data);
 const found=[...M.validationIssues(data,CRAFTBOUND_DEFAULTS),...M.validationIssues(data,baseline)];
 return [...new Map(found.map(issue=>[issueKey(issue),issue])).values()];
}
function errors(){return [...new Set(validationIssues().map(issue=>issue.message))]}
function expectedCards(){
 const ids=new Set(CRAFTBOUND_DEFAULTS.cards.map(c=>c.id));
 return [...CRAFTBOUND_DEFAULTS.cards.map(c=>baseline.cards.find(b=>b.id===c.id)||c),...baseline.cards.filter(c=>!ids.has(c.id))];
}
function jumpToIssue(issue){
 focusedIssue=issue;
 const c=issue.cardIndex>=0?data.cards[issue.cardIndex]:null;
 if(issue.cardId){selected=c?.id||issue.cardId;cardOverride=c}
 $("search").value="";$("classFilter").value="";$("kindFilter").value="";
 if(c?.token||issue.expected?.token)$("showTokens").checked=true;
 if(c?.deleted)$("showDeleted").checked=true;
 showView("cards");highlightValidation(true);
}
function highlightValidation(scroll=false){
 document.querySelectorAll(".validation-focus").forEach(el=>el.classList.remove("validation-focus"));
 document.querySelectorAll('[aria-invalid="true"]').forEach(el=>el.removeAttribute("aria-invalid"));
 const panel=$("errorDetail");if(!panel)return;
 panel.hidden=!focusedIssue;if(!focusedIssue)return;
 const issue=focusedIssue;
 let explanation=issue.message;
 if(issue.code==="missing")explanation=issue.cardId+" is missing from this draft. It exists in the saved/bundled catalogue. An older recovered draft or a changed ID can cause this.";
 if(issue.code==="identity")explanation+=". Required: "+issue.expected.kind+", "+(issue.expected.token?"generated token":"collectible card")+".";
 if(issue.code==="order")explanation+=" Collector #"+issue.collectorNumber+" must be "+issue.expectedId+". Sorting the library is fine; changing saved collector order is not.";
 panel.innerHTML='<strong>'+esc(issue.code==="missing"?"Missing card":issue.code==="order"?"Collector order":"Fix this error")+'</strong><p>'+esc(explanation)+'</p>'+
  (issue.code==="missing"?'<button type="button" id="repairMissingAll">Add all missing original cards</button> ':"")+
  (["missing","identity","order"].includes(issue.code)?'<button type="button" id="repairIssue">'+(issue.code==="missing"?"Add original card to draft":issue.code==="order"?"Restore collector order":"Restore required kind and token flag")+'</button>':"");
 const restoreAll=$("repairMissingAll");if(restoreAll)restoreAll.onclick=()=>{
  const known=new Set(data.cards.map(c=>c.id)),reference=expectedCards();
  for(const original of reference)if(!known.has(original.id)){data.cards.push(M.clone(original));known.add(original.id)}
  const rank=new Map(reference.map((c,i)=>[c.id,i]));
  data.cards.sort((a,b)=>(rank.get(a.id)??Infinity)-(rank.get(b.id)??Infinity));
  focusedIssue=null;cardOverride=null;changed();renderCard();renderList();
 };
 const repair=$("repairIssue");if(repair)repair.onclick=()=>{
  if(issue.code==="missing"&&!data.cards.some(c=>c.id===issue.cardId)){data.cards.push(M.clone(issue.expected));selected=issue.cardId}
  if(issue.code==="identity"){const c=data.cards.find(c=>c.id===issue.cardId);if(c){c.kind=issue.expected.kind;c.token=issue.expected.token}}
  if(issue.code==="order"){
   const rank=new Map(expectedCards().map((c,i)=>[c.id,i]));
   data.cards.sort((a,b)=>(rank.get(a.id)??Infinity)-(rank.get(b.id)??Infinity));
  }
  focusedIssue=null;cardOverride=null;changed();renderCard();renderList();
 };
 let target=null;
 if(issue.cardId===selected&&card()){
  const scope=issue.effectIndex===null?$("cardForm"):$("effects")?.querySelector('[data-effect="'+issue.effectIndex+'"]');
  const field=issue.field==="kind"?"displayKind":issue.field;
  if(field==="collectorOrder")target=$("cardNumber");
  else if(field==="effects")target=$("effects");
  else if(field==="keywords")target=$("cardForm").querySelector(".keywords");
  else if(scope)target=scope.querySelector('[data-field="'+field+'"]')||(field==="excludeSelf"?scope.querySelector("[data-exclude]"):null)||scope;
 }
 target=target||panel;
 target.classList.add("validation-focus");
 if(target.matches("input,select,textarea"))target.setAttribute("aria-invalid","true");
 if(scroll){
  target.scrollIntoView({block:"center",behavior:"smooth"});
  const focus=target.disabled?target.closest("label")||panel:target;
  if(!focus.matches("input,select,textarea,button"))focus.tabIndex=-1;
  focus.focus({preventScroll:true});
 }
}
function renderValidation(){
 const problems=validationIssues();$("save").disabled=$("saveAs").disabled=problems.length>0;
 if(focusedIssue)focusedIssue=problems.find(issue=>issueKey(issue)===issueKey(focusedIssue))||null;
 $("warnings").replaceChildren();
 if(problems.length){
  const heading=document.createElement("strong");heading.textContent=problems.length+" issues · click to locate";$("warnings").append(heading);
  for(const issue of problems){
   const button=document.createElement("button");button.type="button";button.className="warning validation-link";
   button.textContent=issue.message+(issue.effectIndex!==null?" (effect "+(issue.effectIndex+1)+")":"");
   button.onclick=()=>jumpToIssue(issue);$("warnings").append(button);
  }
  status(problems.length+" validation issue(s). Click an error to locate it.",true);
 }else{
  const p=document.createElement("p");p.className="good";p.textContent="✓ Catalogue is valid";$("warnings").append(p);
  if(dirty)status("Unsaved edits. Save the catalogue, then reload cards in Minecraft.");
 }
 requestAnimationFrame(()=>highlightValidation());
}
function renderList(){
 const search=$("search").value.toLowerCase(),cls=$("classFilter").value,kind=$("kindFilter").value,showTokens=$("showTokens").checked;
 const filtered=data.cards.filter(c=>($("showDeleted").checked||!c.deleted)&&(showTokens||!c.token)&&(!cls||c.class===cls)&&(!kind||M.kind(c)===kind)&&[c.name,c.id,c.type,c.text].join(" ").toLowerCase().includes(search));
 $("total").textContent=data.cards.filter(c=>!c.deleted&&!c.token).length;
 $("cardList").innerHTML=filtered.map(c=>'<button class="card-row '+(selected===c.id?'selected':'')+'" data-id="'+esc(c.id)+'" role="listitem" style="--rarity:'+colors[c.rarity]+'"><span class="cost-pill">'+esc(c.cost)+'</span><span class="row-text"><strong>'+esc(c.name)+'</strong><small>'+esc(M.title(c.class))+' · '+esc((c.deleted?"Deleted · ":"")+(c.token?"Token":M.title(M.kind(c))))+'</small></span><span class="rarity-dot" title="'+esc(M.title(c.rarity))+'"></span></button>').join("")||'<p class="empty">No cards match these filters.</p>';
 $("cardList").querySelectorAll("button").forEach(b=>b.onclick=()=>{selected=b.dataset.id;renderCard();renderList()});
}
function browseMechanics(){
 const host=$("mechanicGuide");if(!host)return;
 const picker=host.querySelector("select"),query=host.querySelector("input"),out=host.querySelector("dl");
 const draw=()=>{const term=query.value.trim().toLowerCase();const rows=Object.entries(M.GUIDE[picker.value]).filter(([k,v])=>k!=="DISMANTLE"&&(M.title(k)+" "+v).toLowerCase().includes(term));
 out.innerHTML=rows.map(([k,v])=>'<dt>'+esc(M.title(k))+'</dt><dd>'+esc(v)+'</dd>').join("")||'<dd>No matching rules.</dd>';};
 picker.onchange=draw;query.oninput=draw;host.ontoggle=()=>{if(host.open)draw()};
}
function ruleLibrary(c){
 const host=$("ruleLibrary");if(!host)return;
 const list=Array.isArray(data.workshopRecipes)?data.workshopRecipes:[];
 const steps=c.effects.map((f,i)=>'<option value="'+i+'">'+esc((i+1)+'. '+M.title(f.trigger)+' → '+M.title(f.action))+'</option>').join('');
 host.innerHTML='<summary>My mechanics · save and reuse rules</summary><p>Build an effect above, then name the part you want to reuse. Recipes travel with this catalogue. Applying one makes an independent, editable copy.</p><div class="fields two"><label>Recipe type<select class="recipeKind"><option value="when">When</option><option value="action">What happens</option><option value="passive">Passive ability</option></select></label><label>Source / destination effect<select class="recipeStep">'+(steps||'<option value="-1">No effects yet</option>')+'</select></label></div><p class="recipeHint muted"></p><div class="fields two"><label>New recipe name<input class="recipeName" maxlength="60" placeholder="e.g. Lit fuse"></label><button class="recipeSave">Save as recipe</button></div><div class="fields two"><label>Saved recipes<select class="recipeList"></select></label><div><button class="recipeApply">Apply</button> <button class="recipeDelete">Delete recipe</button></div></div><p class="recipeStatus" role="status"></p>';
 const q=n=>host.querySelector('.recipe'+n),say=t=>q('Status').textContent=t;
 function refresh(){const k=q("Kind").value;
 q("Hint").textContent=({when:"Saves the selected timing, event filters and condition. Apply replaces only the timing of the destination effect.",action:"Saves the selected action and following actions with the same timing. Apply inserts them after the destination, using its timing; on an empty card they use Play.",passive:"Saves the built-in passive and all non-Play effects on this card. Apply adds those ongoing rules to a mob or block."})[k];
 q("Step").disabled=k==="passive";q("List").innerHTML=list.map((r,i)=>r?.kind===k?'<option value="'+i+'">'+esc(r.name)+'</option>':'').join("")||'<option value="">No saved recipes</option>';
 q("Apply").disabled=q("Delete").disabled=q("List").value==="";}
 q("Kind").onchange=refresh;refresh();
 q("Save").onclick=()=>{try{const name=q("Name").value.trim(),kind=q("Kind").value;if(!name)throw Error("Give the recipe a name.");if(list.some(r=>r?.kind===kind&&r.name.toLowerCase()===name.toLowerCase()))throw Error("That name is already in use.");const value=M.recipe(c,kind,Number(q("Step").value));list.push({name,kind,value});data.workshopRecipes=list;changed();refresh();q("List").value=String(list.length-1);say("Recipe added. Save the catalogue to keep it.");}catch(err){say(err.message)}};
 q("Apply").onclick=()=>{try{const next=M.applyRecipe(c,list[Number(q("List").value)],Number(q("Step").value));const test={...data,cards:data.cards.map(x=>x===c?next:x)};
 const before=new Set(M.validationIssues(data,baseline).filter(x=>x.cardId===c.id).map(x=>x.message));const issues=M.validationIssues(test,baseline).filter(x=>x.cardId===c.id&&!before.has(x.message));if(issues.length)throw Error(issues[0].message);
 Object.assign(c,next);changed();renderCard();$("ruleLibrary").open=true;status("Recipe applied. Review the new rules above.");}catch(err){say(err.message)}};
 q("Delete").onclick=()=>{list.splice(Number(q("List").value),1);data.workshopRecipes=list;changed();refresh();say("Recipe removed. Cards that used it keep their rules.");};
}
function animationPanel(c){
 const picker=(key,label)=>select(label,key,Object.keys(M.FX[key]),c[key]||"AUTO","",M.FX[key]);
 return '<section class="form-section"><h2>Match animations</h2><p class="hint">Every animation can be selected for any card. Automatic uses the card’s artwork and rules.</p><div class="fields two">'+picker("castFx","Play animation")+picker("arrivalFx","Board arrival")+picker("deathFx","Board death")+'</div><ul id="animNotes"></ul><small class="hint">Visuals only: animations never add damage, keywords or other rules. Arrival and death choices run when a card enters or dies on the board. A hand effect still needs its own rule; a fox dash only steals a tool if a rule destroys that tool.</small></section>';
}
function refreshAnimations(){const el=$("animNotes");if(el&&card())el.innerHTML=M.animationNotes(card()).map(t=>'<li>'+esc(t)+'</li>').join("")}
function renderCard(){
 if(focusedIssue?.cardId&&focusedIssue.cardId!==selected)focusedIssue=null;
 const c=card();$("duplicate").disabled=!c;$("deleteCard").disabled=!c;$("deleteCard").textContent=c?.deleted?"Restore card":"Delete card";
 if(!c){
  $("heading").textContent=focusedIssue?.expected?.name||selected||"Start your Foundation set";
  $("cardNumber").textContent=selected?"MISSING FROM THIS DRAFT":"No collectible cards yet — use New card to begin. System tokens are available with Show tokens.";$("cardForm").replaceChildren();$("preview").replaceChildren();$("classCurve").replaceChildren();
  renderValidation();return;
 }
 $("heading").textContent=c.name+(c.deleted?" (deleted)":"");
 $("cardNumber").textContent=c.token?"GENERATED TOKEN":"FOUNDATION / #"+String(data.cards.filter(x=>!x.token).findIndex(x=>x.id===c.id)+1).padStart(3,"0");
 const old=locked(c),displayKind=M.kind(c);
 $("cardForm").innerHTML=
 '<div class="fields two">'+field("Card name","name",c.name,"text",'maxlength="80"')+field("Stable card ID","id",c.id,"text",old?'disabled':'maxlength="120"')+'</div>'+
 '<div class="fields">'+select("Class","class",M.CLASSES,c.class)+select("Rarity","rarity",M.RARITIES,c.rarity)+select("Card kind","displayKind",old?(c.kind==="MOB"?["MOB","BLOCK"]:[c.kind]):["MOB","BLOCK","ITEM","TOOL","ENCHANTMENT"],displayKind)+'</div>'+
 '<div class="fields">'+field("XP cost","cost",c.cost,"number",'min="0" max="10" step="1"')+field("Attack","attack",c.attack,"number",'min="0" max="100" step="1" '+(["ITEM","ENCHANTMENT"].includes(c.kind)||displayKind==="BLOCK"?"disabled":""))+field(c.kind==="TOOL"?"Durability":"Health","health",c.health,"number",'min="'+(["ITEM","ENCHANTMENT"].includes(c.kind)?0:1)+'" max="100" step="1" '+(["ITEM","ENCHANTMENT"].includes(c.kind)?"disabled":""))+'</div>'+
 '<div class="fields two">'+select("Mob type","type",M.TYPES,c.type,c.kind!=="MOB"?"disabled":"",{"ANIMAL":"Animal / passive mobs"})+field("Minecraft artwork item or mob ID","art",c.art,"text",'maxlength="120" placeholder="minecraft:zombie_spawn_egg or minecraft:zombie"')+'</div>'+
 '<div class="fields two">'+select("Element","element",[""].concat(M.ELEMENTS),c.element||"","",{"":"None"})+'</div>'+
 (c.kind==="ITEM"?'<label class="check" title="'+esc(M.GUIDE.brew.POTION)+'"><input data-field="potion" type="checkbox" '+(c.potion?"checked":"")+'>Potion — a Brewing Stand can upgrade it in your hand</label>':'')+
 (c.kind==="MOB"?'<div class="fields two"><label title="'+esc(M.GUIDE.card.USES)+'">Uses before destroyed (0 = no limit)<input data-field="uses" type="number" min="0" max="20" value="'+(c.uses||0)+'"></label></div>':'')+
 '<label class="check"><input data-field="autoArt" type="checkbox" '+(c.autoArt===true?"checked":"")+'>Match artwork to card name automatically</label>'+
 '<label class="check"><input data-field="token" type="checkbox" '+(c.token?"checked ":"")+(old?"disabled":"")+'>Generated token — excluded from boosters and collection totals</label><section id="tokenUsage" class="token-usage" hidden></section>'+
 '<section class="form-section"><h2>Keywords</h2><div class="keywords">'+M.KEYWORDS.filter(k=>k!=="BLOCK").map(k=>'<label class="check" title="'+esc(M.GUIDE.keyword[k]||"")+'"><input data-keyword="'+k+'" type="checkbox" '+(c.keywords.includes(k)?"checked ":"")+(M.keywordAllowed(c.kind,k)?"":"disabled")+'>'+M.title(k)+'</label>').join("")+'</div></section>'+
 '<section class="form-section"><div class="section-line"><h2>Effects</h2><button id="addEffect" '+(c.effects.length>=16?"disabled":"")+'>＋ Add effect</button></div><p class="muted">Pick when the rule fires, what it does, and who it affects. Steps resolve from top to bottom. Save useful combinations in My mechanics below.</p><details id="mechanicGuide"><summary>Browse rule explanations</summary><div class="fields two"><label>Section<select><option value="trigger">When</option><option value="action">What happens</option><option value="target">Affects</option><option value="passive">Passive abilities</option><option value="keyword">Keywords</option><option value="chooser">Who picks (hand effects)</option><option value="element">Elements</option><option value="boost">Boosts (If…)</option><option value="brew">Potions and brewing</option><option value="card">Card limits</option></select></label><label>Find a rule<input type="search" placeholder="e.g. heal, damage or block"></label></div><dl></dl></details><div id="effects"></div></section>'+
 '<section class="form-section"><div class="fields two">'+select("Passive ability","passive",c.kind==="MOB"?M.PASSIVES:["NONE"],c.passive,"",{AURA_ATTACK:"Aura: Attack",AURA_STATS:"Aura: Attack and Health",NEGATE_KEYWORD:"Negate keyword"})+field(["AURA_ATTACK","AURA_STATS"].includes(c.passive)?"Aura Attack bonus":"Passive amount","passiveAmount",c.passiveAmount,"number",'min="0" max="100" '+(["NONE","NEGATE_KEYWORD"].includes(c.passive)||c.passive==="AMPLIFY_ELEMENT"&&(c.ampHow||"DOUBLE")!=="PLUS"?"disabled":""))+'</div>'+
 (c.passive==="AMPLIFY_ELEMENT"?'<div class="fields two">'+select("Element boosted","ampElement",M.ELEMENTS,c.ampElement||"FIRE")+select("Boost by","ampHow",M.BOOST_HOW,c.ampHow||"DOUBLE","",{DOUBLE:"Double damage",TRIPLE:"Triple damage",PLUS:"+Passive amount damage"})+'</div>':"")+
 (c.passive==="NEGATE_KEYWORD"?'<div class="fields two">'+select("Keyword enemies lose","negateKeyword",M.NEGATABLE,c.negateKeyword||"SPRINT")+'</div><p class="muted">While this is on the board, the enemies picked below lose this keyword, even if it is printed, trained or granted. It comes back when this leaves play.</p>':"")+
 '<div class="fields two">'+(c.passive==="AURA_STATS"?field("Aura Health bonus","auraHealth",c.auraHealth??0,"number",'min="0" max="100"'):"")+(c.passive==="NEGATE_KEYWORD"?select("Negate affects","auraKind",["MOB","BLOCK","BOTH"],c.auraKind||"BOTH","",{MOB:"Enemy mobs",BLOCK:"Enemy blocks",BOTH:"Enemy mobs and blocks"})+select("Negate affects this mob type","auraType",M.TYPES,c.auraType||"","",{"":"All mob types"}):select("Aura affects","auraKind",["MOB","BLOCK","BOTH"],c.auraKind||"MOB",!["AURA_ATTACK","AURA_STATS"].includes(c.passive)?"disabled":"",{MOB:"Other friendly mobs",BLOCK:"Other friendly blocks",BOTH:"Other friendly mobs and blocks"})+select("Aura affects this mob type","auraType",M.TYPES,c.auraType||"",!["AURA_ATTACK","AURA_STATS"].includes(c.passive)?"disabled":"",{"":"All mob types"}))+'</div>'+
 (["AURA_ATTACK","AURA_STATS"].includes(c.passive)?'<p class="muted">Affects your other matching cards while this source is on the board. Choose mobs, blocks, or both. Blocks can receive Health only: set Attack to 0. The source never buffs itself, and the bonus ends when it leaves play.</p>':"")+
 '<label class="check"><input data-field="autoText" type="checkbox" '+(c.autoText?"checked":"")+'>Automatic description from effects</label><label style="margin-top:12px">Card description<textarea data-field="text" maxlength="1200" '+(c.autoText?"disabled":"")+'>'+esc(M.description(c,data))+'</textarea><span class="hint">'+(c.autoText?"Generated from the actual rules.":"Custom wording only. Enable automatic text to keep it in sync with rules.")+'</span></label></section><details id="ruleLibrary" class="form-section"></details>'+animationPanel(c);
 $("cardForm").querySelectorAll("[data-field]").forEach(el=>{
  const event=el.tagName==="SELECT"||el.type==="checkbox"?"change":"input";
  el.addEventListener(event,()=>{
   const key=el.dataset.field;
   let v=el.type==="checkbox"?el.checked:el.type==="number"?(el.value===""?null:Number(el.value)):el.value;
   if(key==="id"){autoIds.delete(c.id);M.renameCard(c,v,data);selected=v}
   else if(key==="name"){
    c.name=v;M.autoArtwork(c);
    $("cardForm").querySelector('[data-field="art"]').value=c.art;
    if(!locked(c)&&autoIds.has(c.id)){
     autoIds.delete(c.id);M.renameCard(c,M.idForName(v,data.cards,c),data);autoIds.add(c.id);selected=c.id;
     $("cardForm").querySelector('[data-field="id"]').value=c.id;
     renderEffects();
    }
   }
   else if(key==="displayKind"){
    c.kind=v==="BLOCK"?"MOB":v;
    c.keywords=c.keywords.filter(k=>M.keywordAllowed(c.kind,k));
    if(v==="BLOCK"){c.keywords.push("BLOCK");c.attack=0}
    if(["ITEM","ENCHANTMENT"].includes(c.kind)){c.attack=0;c.health=0}else if(!c.health)c.health=1;
    if(c.kind!=="MOB"){c.type="";c.passive="NONE"}
    if(c.kind!=="ITEM")delete c.potion;
    if(c.kind!=="MOB")delete c.uses;
   }else if(key==="art"){c.art=v;c.autoArt=false;$("cardForm").querySelector('[data-field="autoArt"]').checked=false;}else c[key]=v;
   if(key==="passive"&&v==="AURA_STATS"&&c.auraHealth===undefined)c.auraHealth=1;
   if(key==="passive"){if(v==="NEGATE_KEYWORD"){c.negateKeyword=c.negateKeyword||"SPRINT";c.auraKind="BOTH"}else delete c.negateKeyword}
   if(key==="passive"){if(v==="AMPLIFY_ELEMENT"){c.ampElement=c.ampElement||"FIRE";c.ampHow=c.ampHow||"DOUBLE"}else{delete c.ampElement;delete c.ampHow}}
   if(key==="element"&&!v)delete c.element;
   if(key==="potion"&&!v)delete c.potion;
   if(key==="uses"){c.uses=Number(v)||0;if(!c.uses)delete c.uses;c.autoText=true;updateDescription()}
   if(["passive","passiveAmount","auraHealth","auraType","auraKind","negateKeyword","element","ampElement","ampHow"].includes(key)){c.autoText=true;updateDescription()}
   if(key==="autoArt"){M.autoArtwork(c);$("cardForm").querySelector('[data-field="art"]').value=c.art;}
   changed();
   if(["displayKind","autoText","passive","token","ampHow","potion"].includes(key))renderCard();
  });
 });
 $("cardForm").querySelectorAll("[data-keyword]").forEach(el=>el.onchange=()=>{c.keywords=c.keywords.filter(k=>k!==el.dataset.keyword);if(el.checked)c.keywords.push(el.dataset.keyword);c.autoText=true;changed();updateDescription();renderEffects()});
 $("addEffect").onclick=()=>{c.effects.push(M.effect());c.autoText=true;changed();renderCard()};
 browseMechanics();ruleLibrary(c);renderEffects();renderPreview();renderValidation();refreshAnimations();
}
function renderEffects(){
 const c=card();
 $("effects").innerHTML=c.effects.map((f,i)=>{
  const generator=["DRAW","GAIN_XP","GAIN_MAX_XP","GIVE","GIVE_RANDOM","GIVE_RANDOM_CLASS","SUMMON","ENCHANT_TOOL","UPGRADE_TOOL","PICK_ONE","BREW_POTION"].includes(f.action);
  const deckChoice=f.action==="CHOOSE_FROM_DECK";
  const heroGroup=M.TEAM_HERO_TARGETS.includes(f.target);
  const aims=M.targetOptions(f.action);
  const reference=["GIVE","SUMMON","TRANSFORM","BAD_OMEN"].includes(f.action);
  const labels=Object.fromEntries(data.cards.filter(x=>(!x.deleted||x.id===f.card)&&(!["SUMMON","TRANSFORM","BAD_OMEN"].includes(f.action)||x.kind==="MOB"&&(f.action!=="BAD_OMEN"||!x.keywords.includes("BLOCK")))).map(x=>[x.id,x.name+" ("+x.id.split(":")[1]+")"]));
  const triggers=c.kind==="ITEM"?["PLAY"]:["TOOL","ENCHANTMENT"].includes(c.kind)?M.TRIGGERS.filter(t=>!["HARVEST","DISMANTLE","DROP","ATTACKED","ACTIVATE","AFTER_SURVIVING_KILL"].includes(t)):M.TRIGGERS.filter(t=>t!=="DISMANTLE"&&(t!=="ACTIVATE"||c.keywords.includes("BLOCK"))&&(t!=="AFTER_SURVIVING_KILL"||!c.keywords.includes("BLOCK")));
  const hand=M.HAND_ACTIONS.includes(f.action),pick=M.HAND_PICKS.includes(f.action);
  const counted=["BUFF","DAMAGE","HEAL","WELL_FED","DRAW","GIVE","GIVE_RANDOM","GIVE_RANDOM_CLASS","SUMMON","GAIN_XP"].includes(f.action);
  return '<article class="effect" data-effect="'+i+'"><div class="effect-heading"><strong>EFFECT '+(i+1)+'</strong><button data-remove="'+i+'" title="Remove effect">Remove</button></div><div class="fields two">'+select(c.kind==="ENCHANTMENT"?"When (non-Play attaches to tool)":"When","trigger",triggers,f.trigger,"",{AFTER_SURVIVING_KILL:"After attacking, killing and surviving"})+select("What happens","action",M.ACTIONS,f.action,"",{GIVE_RANDOM_CLASS:"Add random card from your class",UPGRADE_TOOL:"Improve equipped tool",REVEAL_HAND:"Look at opponent's hand",DISCARD_FROM_HAND:"Discard from opponent's hand",STEAL_FROM_HAND:"Steal from opponent's hand",SHUFFLE_FROM_HAND:"Shuffle from opponent's hand into their deck"})+'</div>'+
  (f.trigger!=="PLAY"?select("Only if (checked as this action resolves)","gate",Object.keys(M.GATES),f.gate||"ALWAYS","",M.GATES):"")+
  (f.trigger==="AFTER_SURVIVING_KILL"?'<p class="muted">This mob must kill its target with its own attack and remain alive on the board after retaliation, thorns and Death effects. Does not trigger while defending, hitting a hero, or killing through another ability.</p>':"")+
  (f.action==="BAD_OMEN"?'<p class="muted">After the marked hero plays a mob from hand, summon one copy of the chosen mob for the opposing side. Cleanse removes Bad Omen. It cannot stack.</p>':"")+
  (f.action==="GIVE_RANDOM_CLASS"?'<p class="muted">Creates a normal collectible from the controlling player’s class. Excludes Neutral, tokens and deleted cards; does nothing if that class has no cards yet. Does not draw from the deck.</p>':"")+
  (c.kind==="ENCHANTMENT"&&f.trigger!=="PLAY"?'<p class="muted">Automatically attaches this ability to your equipped tool. No separate Enchant tool effect or stat bonus is needed. On kill triggers after the tool destroys a mob or block, including on its last durability.</p>':"")+
  '<div class="fields">'+select("Affects","target",aims,f.target,generator||deckChoice?"disabled":"",{EACH_ENEMY_HERO:"Each enemy hero",EACH_HERO:"Each hero (both teams)",FRIENDLY_MOBS:"Friendly board",ENEMY_MOBS:"Enemy board",ALL_MOBS:"Both boards",RANDOM_FRIENDLY_MOB:"Random friendly mob / block",RANDOM_ENEMY:"Random enemy",RANDOM_FRIENDLY:"Random friendly",RANDOM_BLOCK:"Random block",RANDOM_MOB:"Random mob",RANDOM_HERO:"Random hero",CHOSEN_FRIENDLY:"Chosen friendly",CHOSEN_ENEMY:"Chosen enemy",CHOSEN_ANY:f.trigger==="DAMAGE_DEALT"?"Damaged mob / block (no heroes)":"Chosen on either side",CHOSEN_CHARACTER:f.trigger==="DAMAGE_DEALT"?"Damaged mob or hero":"Chosen board card or hero (either side)",EVENT:f.trigger==="AFTER_ATTACK"?"Attack target (mob, block or hero)":"Character that caused this trigger",KILLER:"Enemy mob that killed this (Death only)"})+select("Eligible board cards","boardKind",["MOB","BLOCK","BOTH"],f.boardKind,generator||heroGroup||hand||f.action==="BAD_OMEN"||["RANDOM_MOB","RANDOM_BLOCK","RANDOM_HERO"].includes(f.target)?"disabled":"",{MOB:"Mobs only",BLOCK:"Blocks only",BOTH:"Mobs and blocks"})+select("Only this type","type",M.TYPES,f.type,generator||heroGroup||hand||f.action==="BAD_OMEN"||["CHOSEN_CHARACTER","RANDOM_HERO"].includes(f.target)?"disabled":"")+'</div>'+
  (f.target.startsWith("RANDOM_")?'<p class="muted">Selects one eligible target at random. Enemy/Friendly stay on that side and include its hero when the effect supports heroes. Random mob/block/hero can choose either side. Mob-type filters exclude heroes. No eligible targets means no effect.</p>':"")+
  (["UPGRADE_TOOL","ENCHANT_TOOL"].includes(f.action)?'<p class="muted">Set either bonus to 0 to leave that stat unchanged. For an Anvil or other block, use Improve equipped tool. Enchant tool is for Enchantment cards.</p>':"")+
  (f.trigger==="DAMAGE_DEALT"?'<p class="muted">Affects the character actually damaged; no extra targeting click. Choose Damaged mob or hero to include hero hits. Prevented damage does not trigger this. Non-Ranged mobs also deal retaliation damage while defending.</p>':"")+
  (reference?'<div class="fields two">'+select("Card to create","card",["",...Object.keys(labels)],f.card,"",{"":"Choose a card…",...labels})+(["TRANSFORM","BAD_OMEN"].includes(f.action)?"":field("Copies","amount",f.amount,"number",'min="1" max="10"'))+'</div>':
  f.action==="PICK_ONE"?'<label>Cards to choose from (Ctrl-click 2 or 3)<select data-field="pool" multiple size="6">'+data.cards.filter(x=>!x.deleted||(f.pool||[]).includes(x.id)).map(x=>'<option value="'+esc(x.id)+'" '+((f.pool||[]).includes(x.id)?"selected":"")+'>'+esc(x.name+" · "+x.id)+'</option>').join("")+'</select></label><p class="muted">When this triggers you see these cards and pick one; it goes into your hand. Make the options as tokens so they never show up in packs.</p>':
  f.action==="GIVE_RANDOM"?'<label>Catch / loot table (Ctrl-click to select several)<select data-field="pool" multiple size="6">'+data.cards.filter(x=>!x.deleted||(f.pool||[]).includes(x.id)).map(x=>'<option value="'+esc(x.id)+'" '+((f.pool||[]).includes(x.id)?"selected":"")+'>'+esc(x.name+" · "+x.id)+'</option>').join("")+'</select></label>'+field("Random cards to give","amount",f.amount,"number",'min="1" max="10"'):
  pick?'<div class="fields two">'+select("Who picks the card","chooser",M.HAND_CHOOSERS,f.chooser||"YOU","",{YOU:"You choose (you see their hand)",OPPONENT:"Opponent chooses",RANDOM:"Random"})+field("Cards","amount",f.amount,"number",'min="1" max="10"')+'</div><p class="muted">The picker gets 30 seconds, then a random card is taken. In Commander and team matches picks are always random. Stolen cards are lost if your hand is full. Nothing happens if their hand is empty.</p>':
  deckChoice?field("Different choices to offer","amount",f.amount,"number",'min="1" max="3"')+'<p class="muted">Choose and draw one real card from your deck. Set Eligible board cards to Blocks only for Blueprint Chest. Repeated copies of a card offer one choice. Up to 3 options; fewer if fewer match. Unchosen cards stay in the deck. The choice is private and lasts 30 seconds.</p>':
  f.action==="RETURN_AND_COPY"?field("Extra copies (besides the returned card)","amount",f.amount,"number",'min="1" max="10"')+'<p class="muted">Choose a target on the board; no fixed card ID is needed. One extra copy makes two cards for a normal friendly target. Keeps Enchanted and Trials upgrades, but resets combat buffs, damage and statuses. Normal token/commander return rules apply. Excess cards are lost when your hand is full.</p>':
  f.action==="REVEAL_HAND"?'<p class="muted">Shows you the opponent’s hand for a few seconds. Only you see it.</p>':
  f.action==="GRANT_KEYWORD"?select("Keyword to grant","keyword",["SHIELD","SPRINT","RANGED","IGNORE_SHIELD","RANDOM"],f.keyword||"SHIELD","",{RANDOM:"Random (one it doesn't have)"}):
  ["BUFF","ENCHANT_TOOL","UPGRADE_TOOL"].includes(f.action)?'<div class="fields two">'+field(f.action==="BUFF"?"Attack change (negative weakens)":"Bonus attack","attack",f.attack,"number",'min="'+(f.action==="BUFF"?-100:0)+'" max="100"')+field(f.action!=="BUFF"?"Bonus durability":"Bonus health","health",f.health,"number",'min="0" max="100"')+'</div>':
  ["DAMAGE","HEAL","WELL_FED","DRAW","GAIN_XP","GAIN_MAX_XP","GIVE_RANDOM_CLASS","WEAKEN_UNTIL_NEXT_TURN"].includes(f.action)?'<div class="fields two">'+field("Amount","amount",f.amount,"number",'min="1" max="'+(generator?10:100)+'"')+'</div>':"")+
  (f.action==="BUFF"?select("Also grant keyword","keyword",["","SHIELD","SPRINT","RANGED","IGNORE_SHIELD"],f.keyword||"","",{"":"None"})+'<p class="muted">The stat bonus and keyword affect the same target in this effect. Random targets are selected once. The keyword lasts while that card stays on the board.</p>':"")+
  (counted?'<div class="fields">'+select("Multiply for each","countScope",M.COUNT_SCOPES,f.countScope||"NONE","",{NONE:"Fixed amount",FRIENDLY:"Friendly board",ENEMY:"Enemy board",ALL:"Both boards"})+select("Count card kind","countKind",["MOB","BLOCK","BOTH"],f.countKind||"BOTH")+select("Count only this type","countType",M.TYPES,f.countType||"")+'</div><label class="check"><input data-field="countExcludeSelf" type="checkbox" '+(f.countExcludeSelf?"checked":"")+'>Do not count this card</label>':"")+
  (M.OBSERVED.includes(f.trigger)?'<div class="fields two">'+select("Watch whose event?","eventScope",M.EVENT_SCOPES,f.eventScope||(f.trigger==="HEALED"?"SELF":"FRIENDLY"))+select("Triggering card type","eventType",M.TYPES,f.eventType||"")+'</div><p class="muted">'+({CARD_ADDED:"Includes draws and generated/returned cards that enter hand; burned cards do not count.",ITEM_PLAYED:"Fires after an Action resolves (not Tools or Enchantments).",MINED:"Fires after friendly Ore is mined and its Drop pays out.",HEALED:"Fires only when Health actually increases. Self watches this mob (or your hero for a tool)."})[f.trigger]+'</p>':"")+
  (M.BOOSTABLE.includes(f.action)?boostFields(f):"")+
  '<label class="check"><input data-exclude type="checkbox" '+(f.excludeSelf?"checked ":"")+(generator||deckChoice||heroGroup||hand||f.action==="BAD_OMEN"?"disabled":"")+'>Exclude this card itself</label></article>';
 }).join("")||'<p class="muted">No triggered effects. This card uses its stats and keywords.</p>';
 $("effects").querySelectorAll("[data-effect]").forEach(article=>{
  const i=Number(article.dataset.effect),f=c.effects[i];
  article.querySelectorAll("[data-field]").forEach(el=>{
   // Effect inputs are added after the parent card form listeners.
   el.addEventListener(el.tagName==="SELECT"||el.type==="checkbox"?"change":"input",()=>{
    const key=el.dataset.field;f[key]=key==="pool"?Array.from(el.selectedOptions,o=>o.value):el.type==="checkbox"?el.checked:el.type==="number"?(el.value===""?null:Number(el.value)):el.value;
    if(key==="action"){
     if(!M.BOOSTABLE.includes(f.action)||["DRAW","GAIN_XP"].includes(f.action)&&f.boost?.when?.startsWith("TARGET_"))delete f.boost;
     f.card="";f.type="";f.excludeSelf=false;delete f.pool;delete f.keyword;delete f.countScope;delete f.countKind;delete f.countType;delete f.countExcludeSelf;
     f.boardKind=f.action==="BUFF"&&f.attack!==0?"MOB":"BOTH";
     if(M.TEAM_HERO_TARGETS.includes(f.target)&&!M.targetOptions(f.action).includes(f.target))f.target="ENEMY_MOBS";
     if(f.action==="GRANT_KEYWORD"){f.keyword="SHIELD";f.boardKind="MOB";f.target="CHOSEN_FRIENDLY"}
     if(f.action==="BAD_OMEN"){f.target="ENEMY_HERO";f.amount=1;}
     if(f.action==="WEAKEN_UNTIL_NEXT_TURN"){f.boardKind="MOB";f.attack=0;}
     if(f.action==="CHOOSE_FROM_DECK"){f.target="SELF";f.boardKind="BLOCK";f.amount=3;f.attack=0;f.health=0;}
     if(f.action==="RETURN_AND_COPY"){f.target="CHOSEN_FRIENDLY";f.boardKind="BLOCK";f.amount=1;f.attack=0;f.health=0;}
     if(f.action==="GIVE_RANDOM"||f.action==="PICK_ONE")f.pool=[];
     if(f.action==="PICK_ONE")f.amount=1;
     if(f.action==="MINE"){f.target="CHOSEN_FRIENDLY";f.boardKind="BLOCK";f.type="ORE"}
     if(["DRAW","GAIN_XP","GAIN_MAX_XP","GIVE","GIVE_RANDOM","GIVE_RANDOM_CLASS","SUMMON","ENCHANT_TOOL","UPGRADE_TOOL","PICK_ONE","BREW_POTION"].includes(f.action)){f.target="SELF";f.amount=Math.min(f.amount||1,10)}
     else if(f.action==="DESTROY_TOOL")f.target="ENEMY_HERO";
     else if(f.target==="SELF"&&c.kind!=="MOB"&&f.action!=="CHOOSE_FROM_DECK")f.target="CHOSEN_FRIENDLY";
     if(M.HAND_ACTIONS.includes(f.action)){f.target="ENEMY_HERO";f.boardKind="BOTH";f.amount=1;if(M.HAND_PICKS.includes(f.action))f.chooser=f.chooser||"YOU";else delete f.chooser}
     else delete f.chooser;
     if(["BUFF","BOUNCE","RETURN_AND_COPY","TOTEM","INVULNERABLE","WITHER","TOOL_DAMAGE","GRANT_KEYWORD","TRANSFORM"].includes(f.action)&&["CHOSEN_CHARACTER","FRIENDLY_HERO","ENEMY_HERO","RANDOM_HERO"].includes(f.target))f.target="ENEMY_MOBS";
    }
    if(key==="target"&&f.trigger==="PLAY"&&f.target.startsWith("CHOSEN")){
     // A single play selects one target; keep all chosen Play effects on that same target side.
     for(const other of c.effects)if(other.trigger==="PLAY"&&other.target.startsWith("CHOSEN")){
      other.target=f.target;if(f.target==="CHOSEN_CHARACTER")other.type="";
     }
    }
    if(key==="keyword"&&f.keyword&&!["SHIELD","RANDOM"].includes(f.keyword))f.boardKind="MOB";
    if(key==="target"&&f.target==="CHOSEN_CHARACTER")f.type="";
    if(f.target==="RANDOM_MOB")f.boardKind="MOB";
    if(f.target==="RANDOM_BLOCK")f.boardKind="BLOCK";
    if(["RANDOM_HERO","EACH_ENEMY_HERO","EACH_HERO"].includes(f.target)){f.boardKind="BOTH";f.type="";f.excludeSelf=false}
    if(key==="trigger"){if(f.trigger==="PLAY")delete f.gate;delete f.eventScope;delete f.eventType;if(f.target==="EVENT")f.target="SELF"}
    if(key==="trigger"&&["DAMAGE_DEALT","ATTACKED"].includes(f.trigger)){f.target="CHOSEN_ANY";f.type="";f.excludeSelf=false;f.boardKind="BOTH"}
    if(key==="trigger"&&!["PLAY","DAMAGE_DEALT","ATTACKED"].includes(f.trigger)&&f.target.startsWith("CHOSEN"))f.target="FRIENDLY_MOBS";
    c.autoText=true;
    changed();updateDescription();
    if(["action","trigger","target","keyword","countScope"].includes(key))renderEffects();
   });
  });
  article.querySelectorAll("[data-boost]").forEach(el=>el.addEventListener(el.tagName==="SELECT"?"change":"input",()=>{
   const k=el.dataset.boost;
   if(k==="when"){if(!el.value)delete f.boost;else f.boost=Object.assign({card:"",type:"",kind:"BOTH",how:"DOUBLE"},f.boost||{},{when:el.value})}
   else if(f.boost){f.boost[k]=k==="amount"?(el.value===""?null:Number(el.value)):el.value;if(k==="how"&&el.value==="PLUS"&&!f.boost.amount)f.boost.amount=1;if(k==="how"&&el.value!=="PLUS")delete f.boost.amount}
   c.autoText=true;changed();updateDescription();
   if(k!=="amount")renderEffects();
  }));
  article.querySelector("[data-exclude]").onchange=e=>{f.excludeSelf=e.target.checked;c.autoText=true;changed();updateDescription()};
  article.querySelector("[data-remove]").onclick=()=>{c.effects.splice(i,1);c.autoText=true;changed();renderCard()};
 });
}
function updateDescription(){const c=card();const auto=$("cardForm").querySelector('[data-field="autoText"]'),text=$("cardForm").querySelector('textarea');if(auto)auto.checked=c.autoText;if(text){text.disabled=c.autoText;text.value=M.description(c,data)}}
function renderClassCurve(){
 const c=card();if(!c)return;
 $("curveNeutral").disabled=c.class==="NEUTRAL";
 const include=$("curveNeutral").checked&&c.class!=="NEUTRAL";
 const curve=M.xpCurve(data.cards,c.class,include),max=Math.max(1,...curve.counts);
 $("classCurve").innerHTML='<h2>'+esc(M.title(c.class))+(include?' + Neutral':'')+' XP curve</h2><p class="muted">'+curve.total+' unique cards · Average '+(curve.total?curve.average.toFixed(1):'—')+' XP</p><div class="xp-chart" role="img" aria-label="'+esc(curve.counts.map((n,i)=>i+' XP: '+n+' cards').join(', '))+'">'+curve.counts.map((n,i)=>'<div class="xp-column" title="'+i+' XP: '+n+' cards"><div class="xp-track"><i style="height:'+n/max*100+'%"><b>'+n+'</b></i></div><span>'+i+'</span></div>').join('')+'</div><p class="curve-caption">Unique collectible cards in this class pool. Tokens excluded.</p>';
}
function renderTokenUsage(){
 const c=card(),section=$("tokenUsage");if(!section)return;
 section.hidden=!c?.token;if(!c?.token){section.replaceChildren();return}
 const links=M.cardsUsing(data,c.id),sources=M.TOKEN_SOURCES[c.id]||[];
 section.innerHTML='<h2>Used by</h2><p class="muted">Cards that create or reference this token. Click a card to edit it.</p>'+
  (links.length?'<div class="token-links">'+links.map(({card:source,effects})=>{
   const details=effects.map(f=>(f.trigger==="PLAY"&&source.kind==="MOB"?"Spawn":M.title(f.trigger))+' → '+M.title(f.action)+(Number.isInteger(f.amount)?' ×'+f.amount:''));
   return '<button type="button" class="token-link" data-source="'+esc(source.id)+'" title="'+esc(source.id)+'"><strong>'+esc(source.name)+'</strong><span>'+esc([...new Set(details)].join(' · '))+'</span></button>';
  }).join('')+'</div>':'<p class="muted">No cards in this catalogue currently reference this token.</p>')+
  (sources.length?'<h3>Other sources</h3>'+sources.map(source=>'<p class="token-source"><strong>'+esc(source.name)+'</strong><span>'+esc(source.detail)+'</span></p>').join(''):'');
 section.querySelectorAll("[data-source]").forEach(button=>button.onclick=()=>{
  const source=data.cards.find(c=>c.id===button.dataset.source);if(!source)return;
  selected=source.id;$("search").value="";$("classFilter").value="";$("kindFilter").value="";
  if(source.token)$("showTokens").checked=true;
  renderCard();renderList();$("heading").scrollIntoView({block:"start",behavior:"smooth"});
 });
}
function renderCardPreview(c,container){
 container.innerHTML='<div class="preview-card kind-'+M.kind(c).toLowerCase()+' '+(c.rarity==='LEGENDARY'?'legendary':'')+'" style="--rarity:'+colors[c.rarity]+';--class-color:'+classColors[c.class]+'"><div class="preview-cost">'+esc(c.cost)+'</div><div class="preview-name"><span>'+esc(c.name)+'</span></div><div class="preview-art"><div class="preview-glyph">'+(c.kind==="TOOL"?"⚒":["ITEM","ENCHANTMENT"].includes(c.kind)?"✦":c.keywords.includes("BLOCK")?"▣":"◆")+'</div><small>'+esc(c.art||"Minecraft artwork")+'</small></div><div class="preview-gem"></div><div class="preview-text">'+rulesHtml(M.description(c,data))+'</div><div class="preview-bottom">'+(M.kind(c)==="BLOCK"||["ITEM","ENCHANTMENT"].includes(c.kind)?"":'<span class="stat">'+esc(c.attack)+'</span>')+'<span class="family">'+esc(M.title(c.class))+'<br>'+esc(M.kind(c)==="BLOCK"?"Block"+(c.type?" · "+M.title(c.type):""):c.kind==="MOB"&&c.type?M.title(c.type):c.kind==="ITEM"&&c.potion?(c.element?M.title(c.element)+" Potion":"Potion"):c.kind==="ITEM"&&c.element?M.title(c.element):M.title(M.kind(c)))+'</span>'+(["ITEM","ENCHANTMENT"].includes(c.kind)?"":'<span class="stat health">'+esc(c.health)+'</span>')+'</div></div>';
 const banner=container.querySelector(".preview-name");
 if(banner.clientWidth){
  const size=parseFloat(getComputedStyle(banner).fontSize);
  const style=getComputedStyle(banner), available=banner.clientWidth-parseFloat(style.paddingLeft)-parseFloat(style.paddingRight);
  const titleWidth=banner.firstElementChild.offsetWidth;
  if(titleWidth>available && available>0)banner.style.fontSize=(size*available/titleWidth)+"px";
 }
 const shortRules=M.description(c,data).trim();
 if(/^(?:Ignore Shield|Shield|Sprint|Ranged|Cleanse|Repair)\.?(?:\s+(?:Ignore Shield|Shield|Sprint|Ranged|Cleanse|Repair)\.?)?$/.test(shortRules)){
  const box=container.querySelector(".preview-text");
  box.classList.add("keyword-only");
  box.innerHTML=shortRules.match(/Ignore Shield|Shield|Sprint|Ranged|Cleanse|Repair/g).map(word=>'<span>'+rulesHtml(word+".")+'</span>').join("");
 }
}
let associatedSource="",associatedIndex=0;
function renderAssociatedCards(c){
 const panel=$("associatedCards");
 const links=M.associatedCards(data,c),dynamic=c.effects.some(f=>f.action==="GIVE_RANDOM_CLASS");
 panel.hidden=!links.length&&!dynamic;
 if(panel.hidden){panel.replaceChildren();return}
 if(associatedSource!==c.id){associatedSource=c.id;associatedIndex=0}
 associatedIndex=Math.min(associatedIndex,Math.max(0,links.length-1));
 const link=links[associatedIndex];
 const details=link?link.effects.map(f=>{
  const when=f.trigger==="PLAY"&&c.kind==="MOB"?"Spawn":f.trigger==="DROP"?"Death":M.title(f.trigger);
  const action={SUMMON:"Summons",GIVE:"Adds to hand",TRANSFORM:"Transforms into",GIVE_RANDOM:"Possible random card",PICK_ONE:"Can be picked"}[f.action];
  return when+' · '+action+(f.action!=="TRANSFORM"?' ×'+f.amount:'');
 }):[];
 panel.innerHTML='<h2>Associated cards</h2>'+(dynamic?'<p class="muted">Random class card: the pool depends on the hero playing this card. No fixed result; excludes Neutral cards and tokens.</p>':'')+
  (link?'<div class="associated-nav"><button type="button" data-step="-1" aria-label="Previous associated card" '+(associatedIndex===0?'disabled':'')+'>‹</button><span>'+(associatedIndex+1)+' / '+links.length+'</span><button type="button" data-step="1" aria-label="Next associated card" '+(associatedIndex===links.length-1?'disabled':'')+'>›</button></div><p class="associated-reason">'+esc([...new Set(details)].join(' · '))+'</p>'+(link.card?'<div class="associated-face"></div><p class="muted">'+(link.card.deleted?'Deleted card':link.card.token?'Generated token':'Collectible card')+'</p><button type="button" data-edit-associated>Edit this card</button>':'<p class="bad">Missing card: '+esc(link.id)+'</p>'):'');
 if(link?.card)renderCardPreview(link.card,panel.querySelector('.associated-face'));
 panel.querySelectorAll('[data-step]').forEach(b=>b.onclick=()=>{associatedIndex+=Number(b.dataset.step);renderAssociatedCards(c)});
 const edit=panel.querySelector('[data-edit-associated]');
 if(edit)edit.onclick=()=>{
  selected=link.id;$("search").value="";$("classFilter").value="";$("kindFilter").value="";
  if(link.card.token)$("showTokens").checked=true;if(link.card.deleted)$("showDeleted").checked=true;
  renderCard();renderList();$("heading").scrollIntoView({block:"start",behavior:"smooth"});
 };
}
function renderPreview(){
 const c=card();if(!c)return;
 renderClassCurve();renderTokenUsage();renderWordingAdvice();
 renderCardPreview(c,$("preview"));renderAssociatedCards(c);
 $("giveCommand").textContent="/craftbound cards give "+c.id;
}
function openReference(anchor="start-here") {
 $("referenceFrame").src="reference.html#"+encodeURIComponent(anchor);showView("reference");
}
function renderWordingAdvice(){
 const c=card(),panel=$("wordingAdvice");if(!c||!panel)return;
 const advice=M.wordingAdvice(c,data),words=M.description(c,data).trim().split(/\s+/).filter(Boolean).length;
 panel.innerHTML='<h2>Wording check <small>'+words+' words</small></h2><p class="muted">Check target, timing, amount and duration. These are suggestions, not save errors.</p>'+(advice.length?'<ul>'+advice.map(x=>'<li>'+esc(x)+'</li>').join('')+'</ul>':'<p class="good">No common wording issues detected. Verify the targets in a match.</p>')+'<div class="reference-links"><button data-guide="wording-standard">Wording examples</button><button data-guide="'+(c.class==='NEUTRAL'?'neutral-foundation-ideas':c.class.toLowerCase())+'">'+esc(M.title(c.class))+' design guide</button><button data-guide="effect-maker-capability-map">Effects &amp; limits</button></div>';
 panel.querySelectorAll('[data-guide]').forEach(b=>b.onclick=()=>openReference(b.dataset.guide));
}
function showView(next){
 view=next;
 window.scrollTo({top:0,behavior:"instant"});
 for(const name of ["cards","balance","reference","ideas","trials"]){$(name+"View").hidden=next!==name;$(name+"Tab").classList.toggle("active",next===name)}
 document.body.classList.toggle('reference-open',next==='reference');
 document.body.classList.toggle("trials-open",next==="trials");
 if(next==="trials"){
   const frame=$("trialsFrame");
   if(!frame.hasAttribute("src"))frame.src="trials.html"+location.hash;
   return;
 }
 if(next==='ideas'){openIdeas();return}
 if(next==="balance")renderBalance();else if(next==="cards"){renderCard();renderList()}
 else if(!$("referenceFrame").getAttribute("src"))$("referenceFrame").src="reference.html";
}
function renderCharts(){
 const cards=data.cards.filter(c=>!c.deleted&&!c.token),avg=cards.length?cards.reduce((s,c)=>s+(Number(c.cost)||0),0)/cards.length:0;
 const neutral=cards.filter(c=>c.class==="NEUTRAL").length;
 $("setTarget").value=data.foundationTarget||180;
 $("metrics").innerHTML=[[cards.length,"of "+(data.foundationTarget||180)+" planned cards"],[neutral,"Neutral cards"],[cards.length-neutral,"Class cards"],[avg.toFixed(1),"Average XP cost"]].map(([v,l])=>'<div class="metric"><strong>'+v+'</strong><span>'+esc(l)+'</span></div>').join("");
 const count=(values,get)=>values.map(v=>[M.title(v),cards.filter(c=>get(c)===v).length]);
 const groups=[["Classes",count(M.CLASSES,c=>c.class)],["Rarities",count(M.RARITIES,c=>c.rarity)],["Kinds",count(["MOB","BLOCK","ITEM","TOOL","ENCHANTMENT"],M.kind)],["XP curve",Array.from({length:11},(_,i)=>[String(i),cards.filter(c=>c.cost===i).length])],["Mob types",count(M.TYPES.filter(Boolean),c=>c.type)]];
 $("distributions").innerHTML=groups.map(([name,rows])=>{const max=Math.max(1,...rows.map(r=>r[1]));return '<section class="distribution"><h2>'+name+'</h2>'+rows.map(([l,n])=>'<div class="bar-row"><span>'+esc(l)+'</span><span class="bar"><i style="width:'+n/max*100+'%"></i></span><b>'+n+'</b></div>').join("")+'</section>'}).join("");
}
function renderBalance(){
 renderCharts();const query=$("tableSearch").value.toLowerCase(),sort=$("sort").value;
 let cards=data.cards.filter(c=>!c.deleted&&!c.token&&[c.name,c.class,c.type,c.rarity,M.title(M.kind(c))].join(" ").toLowerCase().includes(query));
 if(sort!=="order")cards.sort((a,b)=>sort==="cost"?a.cost-b.cost:sort==="rarity"?M.RARITIES.indexOf(a.rarity)-M.RARITIES.indexOf(b.rarity):String(a[sort]).localeCompare(String(b[sort])));
 const number=c=>data.cards.filter(x=>!x.token).findIndex(x=>x.id===c.id)+1;
 $("balanceRows").innerHTML=cards.map(c=>'<tr data-id="'+esc(c.id)+'"><td>'+number(c)+'</td><td><button data-edit>'+esc(c.name)+'</button></td><td><select data-col="class" aria-label="Class for '+esc(c.name)+'">'+options(M.CLASSES,c.class)+'</select></td><td>'+M.title(M.kind(c))+'</td><td><select data-col="type" aria-label="Type for '+esc(c.name)+'" '+(c.kind!=="MOB"?"disabled":"")+'>'+options(M.TYPES,c.type)+'</select></td><td><select data-col="rarity" aria-label="Rarity for '+esc(c.name)+'">'+options(M.RARITIES,c.rarity)+'</select></td>'+["cost","attack","health"].map(k=>'<td><input data-col="'+k+'" aria-label="'+k+' for '+esc(c.name)+'" type="number" value="'+c[k]+'" min="'+(k==="health"&&c.kind!=="ITEM"?1:0)+'" max="'+(k==="cost"?10:100)+'" '+((k!=="cost"&&["ITEM","ENCHANTMENT"].includes(c.kind))||(k==="attack"&&M.kind(c)==="BLOCK")?"disabled":"")+'></td>').join("")+'</tr>').join("");
 $("balanceRows").querySelectorAll("tr").forEach(row=>{
  const c=data.cards.find(c=>c.id===row.dataset.id);
  row.querySelector("[data-edit]").onclick=()=>{selected=c.id;showView("cards")};
  row.querySelectorAll("[data-col]").forEach(el=>el.onchange=()=>{c[el.dataset.col]=el.type==="number"?(el.value===""?null:Number(el.value)):el.value;changed()});
 });
}
function newCard(duplicate=false,template=null){
 const old=card(),c=template?M.clone(M.TEMPLATES[template]):duplicate?M.clone(old):{id:"craftbound:new_card",name:"New card",text:"",autoText:true,kind:"MOB",class:"NEUTRAL",rarity:"COMMON",cost:1,attack:1,health:2,type:"",keywords:[],token:false,autoArt:true,art:"minecraft:paper",passive:"NONE",passiveAmount:1,effects:[]};
 delete c.deleted;
 if(duplicate)c.name=old.name+" copy";
 const id=M.idForName(c.name,data.cards);c.id=id;autoIds.add(id);
 data.cards.push(c);selected=id;$("search").value="";$("classFilter").value="";$("kindFilter").value="";if(c.token)$("showTokens").checked=true;
 changed();showView("cards");$("cardForm").querySelector('[data-field="name"]').focus();
}
function deleteSelected(){
 const c=card();if(!c)return;
 if(c.deleted){delete c.deleted;changed();renderCard();return;}
 const problem=M.deletionProblem(data,c.id);if(problem){status(problem,true);return;}
 const saved=baseline.cards.some(x=>x.id===c.id)||CRAFTBOUND_DEFAULTS.cards.some(x=>x.id===c.id);
 if(!confirm('Delete "'+c.name+'"? '+(saved?'It will leave active collections and card pools. Its ID and collector number stay reserved; use Show deleted cards to restore it.':'This unsaved card will be removed from the draft.')+' Save to apply.'))return;
 M.deleteCard(data,c.id,saved?{cards:[c]}:{cards:[]});autoIds.delete(c.id);cardOverride=null;focusedIssue=null;
 selected=data.cards.find(x=>!x.deleted&&!x.token)?.id||data.cards.find(x=>!x.deleted)?.id||"";
 changed();renderCard();
}
function acceptFile(text,name,newHandle){
 let incoming;try{incoming=M.prepareCatalog(JSON.parse(text));const problems=M.validate(incoming,CRAFTBOUND_DEFAULTS);if(problems.length)throw Error(problems.slice(0,5).join("\n"))}catch(e){status("Could not open catalogue: "+e.message,true);return}
 data=incoming;baseline=M.clone(data);autoIds.clear();handle=newHandle;fileName=name;selected=data.cards.find(c=>!c.deleted&&!c.token)?.id||"";dirty=false;recovery=false;
 try{localStorage.removeItem(key)}catch{}clearTimeout(draftTimer);
 $("dirty").textContent=name;renderList();showView(view);renderValidation();status("Imported "+name+". Save writes this catalogue to run/config/craftbound/cards.json; Save as… exports a copy.");return true;
}
const pickerTypes=[{description:"Craftbound card catalogue",accept:{"application/json":[".json"]}}];
async function openFile(){
 if(dirty&&!confirm("Open another catalogue and discard the current unsaved draft?"))return;
 try{
  if(window.showOpenFilePicker){const [h]=await showOpenFilePicker({multiple:false,types:pickerTypes});const f=await h.getFile();if(f.size>1000000)throw Error("File exceeds 1 MB");acceptFile(await f.text(),f.name,h)}
  else $("fileInput").click();
 }catch(e){if(e.name!=="AbortError")status("Open failed: "+e.message,true)}
}
async function saveFile(as=false){
 if(saving)return;
 const problems=errors();if(problems.length){status(problems[0],true);return}
 const snapshot=M.clone(data),json=JSON.stringify(snapshot,null,2)+"\n";
 if(!as){
  if(!localWorkshop && !window.showOpenFilePicker){
   status("This browser cannot overwrite a local file from this page. Use Save as… to keep your draft, launch Open Card Workshop.cmd, then Open catalogue to import that draft and Save. Nothing has been saved to Minecraft yet.",true);return;
  }
  saving=true;
  try{
   if(localWorkshop){
    const result=await workshopRequest("POST",json);activePath=result.path;
   }else{
    // A standalone/file preview has no local service. Let the user explicitly connect the
    // existing active file, preserving this page's unsaved draft (including newly made cards).
    if(!activeSaveHandle){
     status("Select your active run/config/craftbound/cards.json. This saves the current draft directly into that file.");
     const [target]=await showOpenFilePicker({multiple:false,types:pickerTypes});
     if(target.name.toLowerCase()!=="cards.json")throw Error("Choose the existing cards.json in run/config/craftbound, not an export or another file.");
     activeSaveHandle=target;
    }
    const current=await activeSaveHandle.getFile();
    if(current.size>1000000)throw Error("The selected catalogue exceeds 1 MB.");
    const previous=JSON.parse(await current.text());
    const issues=M.validate(snapshot,previous);
    if(issues.length)throw Error("Selected file was not overwritten: "+issues.slice(0,5).join("\n"));
    const writer=await activeSaveHandle.createWritable();
    try{await writer.write(json);await writer.close()}catch(e){try{await writer.abort()}catch{}throw e}
    activePath="the connected cards.json ("+snapshot.cards.filter(c=>!c.deleted&&!c.token).length+" collectible cards)";
   }
   baseline=snapshot;dirty=JSON.stringify(data)!==JSON.stringify(snapshot);
   recovery=false;
   for(const c of snapshot.cards)autoIds.delete(c.id);
   clearTimeout(draftTimer);
   if(dirty)remember();else try{localStorage.removeItem(key)}catch{}
   $("dirty").textContent=dirty?"Unsaved changes":"Saved · cards.json";
   renderCard();status("Saved directly to "+activePath+". Run /craftbound cards reload between matches.");
   if(localWorkshop)watchRelease();
  }catch(e){
   if(!localWorkshop)activeSaveHandle=null;
   status(e.name==="AbortError"?"Save cancelled. Your draft is still here; Minecraft's cards.json was not changed.":e.message,true);
  }finally{saving=false}
  return;
 }
 try{
  let target=as?null:handle;
  if(!target&&window.showSaveFilePicker)target=await showSaveFilePicker({suggestedName:"cards.json",types:pickerTypes});
  if(target){
   const writer=await target.createWritable();await writer.write(json);await writer.close();
   status("Exported "+target.name+". Normal Save still writes to the active run/config/craftbound/cards.json.");
  }else{
   const url=URL.createObjectURL(new Blob([json],{type:"application/json"})),a=document.createElement("a");a.href=url;a.download="cards.json";a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
   status("Downloaded cards.json. Replace config/craftbound/cards.json with it, then reload. The workshop cannot confirm the file was replaced.");
  }
 }catch(e){if(e.name!=="AbortError")status("Save failed: "+e.message,true)}
}
$("curveNeutral").onchange=renderClassCurve;
$("classFilter").innerHTML='<option value="">All classes</option>'+options(M.CLASSES,"");
$("kindFilter").innerHTML='<option value="">All kinds</option>'+options(["MOB","BLOCK","ITEM","TOOL","ENCHANTMENT"],"");
for(const id of ["search","classFilter","kindFilter","showTokens","showDeleted"])$(id).addEventListener(id==="search"?"input":"change",renderList);
$("referenceTab").onclick=()=>showView("reference");$("referenceBack").onclick=()=>showView("cards");$("cardsTab").onclick=()=>showView("cards");$("balanceTab").onclick=()=>showView("balance");
$("deleteCard").onclick=deleteSelected;
$("new").onclick=()=>newCard();$("duplicate").onclick=()=>newCard(true);
$("template").onchange=e=>{if(e.target.value)newCard(false,e.target.value);e.target.value=""};
$("open").onclick=openFile;$("save").onclick=()=>saveFile();$("saveAs").onclick=()=>saveFile(true);
$("fileInput").onchange=async e=>{const f=e.target.files[0];if(f){if(f.size>1000000)status("File exceeds 1 MB",true);else acceptFile(await f.text(),f.name,null)}e.target.value=""};
$("help").onclick=()=>$("helpDialog").showModal();$("closeHelp").onclick=()=>$("helpDialog").close();
$("tableSearch").oninput=renderBalance;$("sort").onchange=renderBalance;
$("setTarget").onchange=()=>{const n=Number($("setTarget").value);if(Number.isInteger(n)&&n>0&&n<=1000){data.foundationTarget=n;changed()}else status("Set target must be 1–1000",true)};
$("copyCommand").onclick=async()=>{try{await navigator.clipboard.writeText($("giveCommand").textContent);status("Give command copied.")}catch{status("Select and copy the give command shown above.",true)}};
window.addEventListener("beforeunload",e=>{if(dirty){try{localStorage.setItem(key,JSON.stringify({data,baseline,autoIds:[...autoIds]}))}catch{}e.preventDefault();e.returnValue=""}});
document.addEventListener("keydown",e=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==="s"){e.preventDefault();saveFile(e.shiftKey)}});
renderList();renderCard();
if(localWorkshop){
 $("save").disabled=true;
 workshopRequest().then(result=>{
  activePath=result.path;
  if(recovery){
   M.mergeSavedDraft(data,baseline,result.data);
   baseline=M.clone(result.data);
   renderCard();renderList();renderValidation();status("Recovered your unsaved draft. Save overwrites "+activePath+".");
  }else if(!acceptFile(JSON.stringify(result.data),"cards.json",null))return;
  status(recovery?"Recovered draft. Save overwrites "+activePath+".":"Connected to "+activePath+". Save overwrites this file.");
 }).catch(e=>status("Could not connect to active catalogue: "+e.message,true));
}else status(window.showOpenFilePicker?"Save will ask you to select the active run/config/craftbound/cards.json once, then overwrite it. Your draft stays on this page.":"Use Save as… to keep your draft, then open it in the launched Workshop for direct saving.");

// Ideas are deliberately independent from the playable catalogue and its Save action.
let ideasData={version:1,ideas:[]},ideasRevision='',ideasLoaded=false,ideasLoading=false,ideasDirty=false,ideasSaving=false,ideasTimer,ideaId=null,ideasGeneration=0;
const ideasDraftKey='craftbound-workshop-ideas-draft-v1';
const ideaClasses=['GENERAL',...M.CLASSES];
$('ideasClassFilter').innerHTML='<option value="">All classes</option>'+options(ideaClasses,'');
$("trialsTab").onclick=()=>showView("trials");
$('ideasTab').onclick=()=>showView('ideas');
async function ideasRequest(method='GET',body){
 const response=await fetch('/api/ideas',{method,headers:{'Content-Type':'application/json','X-Workshop-Token':location.hash.slice(1)},...(body?{body:JSON.stringify(body)}:{})});
 const result=await response.json();if(!response.ok)throw Error(result.error||'Cannot reach Ideas storage');return result;
}
async function openIdeas(){
 if(ideasLoaded||ideasLoading)return;ideasLoading=true;$('ideasNew').disabled=true;
 try{
  if(localWorkshop){const result=await ideasRequest();ideasData=result.data;ideasRevision=result.revision}
  else {const cached=localStorage.getItem(ideasDraftKey);if(cached)ideasData=JSON.parse(cached)}
  ideasLoaded=true;ideaId=ideasData.ideas[0]?.id;renderIdeas();renderIdea();
  $('ideasStatus').textContent=localWorkshop?'Notes autosave to workshop-ideas.json beside cards.json.':'Browser-only notes. Export a copy to keep them outside this browser.';
 }catch(e){$('ideasStatus').textContent=e.message+' Reopen using Open Card Workshop.cmd for the updated host.'}
 finally{ideasLoading=false;$('ideasNew').disabled=!ideasLoaded}
}
function renderIdeas(){
 const query=$('ideasSearch').value.trim().toLowerCase(),cls=$('ideasClassFilter').value;
 const list=ideasData.ideas.filter(i=>(!cls||i.class===cls)&&($('ideasArchived').checked||i.stage!=='Archived')&&(!query||Object.values(i).join(' ').toLowerCase().includes(query)));
 $('ideasList').innerHTML=list.length?list.map(i=>'<button data-idea="'+esc(i.id)+'" class="'+(i.id===ideaId?'active':'')+'">'+esc(i.title||'Untitled idea')+'<small>'+esc(M.title(i.class)+' · '+i.kind+' · '+i.stage)+'</small></button>').join(''):'<p class="muted">No ideas here yet.</p>';
 $('ideasList').querySelectorAll('[data-idea]').forEach(b=>b.onclick=()=>{ideaId=b.dataset.idea;renderIdeas();renderIdea()});
}
function renderIdea(){
 const i=ideasData.ideas.find(i=>i.id===ideaId);if(!i){$('ideaEditor').innerHTML='<p>Select an idea or start a new one.</p>';return}
 const input=(key,label,area=false)=>'<label>'+label+(area?'<textarea data-idea-field="'+key+'" maxlength="20000">'+esc(i[key])+'</textarea>':'<input data-idea-field="'+key+'" maxlength="500" value="'+esc(i[key])+'">')+'</label>';
 const select=(key,label,values)=>'<label>'+label+'<select data-idea-field="'+key+'">'+options(values,i[key])+'</select></label>';
 $('ideaEditor').innerHTML=input('title','Idea name')+'<div class="fields">'+select('class','Class',ideaClasses)+select('kind','Plan for',['Card','Synergy','Class identity','Mechanic','Set plan'])+select('stage','Stage',['Idea','Exploring','Ready to build','Built','Archived'])+'</div>'+input('tags','Tags / related cards (free text)')+input('concept','Concept & card wording',true)+input('synergy','Synergies & deck plan — enablers, payoffs, supporting cards',true)+input('balance','Balance questions — XP, stats, counters, weaknesses, playtest notes',true)+'<div class="idea-actions"><span class="muted">Keep rough ideas here until they are ready for the effect maker.</span><button id="ideaArchive">'+(i.stage==='Archived'?'Restore idea':'Archive idea')+'</button></div>';
 $('ideaEditor').querySelectorAll('[data-idea-field]').forEach(el=>el.addEventListener(el.tagName==='SELECT'?'change':'input',()=>{i[el.dataset.ideaField]=el.value;ideaChanged(i)}));
 $('ideaArchive').onclick=()=>{i.stage=i.stage==='Archived'?'Idea':'Archived';ideaChanged(i);renderIdea()};
}
function ideaChanged(i){
 i.updated=new Date().toISOString();ideasDirty=true;ideasGeneration++;renderIdeas();
 try{localStorage.setItem(ideasDraftKey,JSON.stringify(ideasData))}catch{}
 $('ideasStatus').textContent=localWorkshop?'Saving notes…':'Saved in this browser. Export for a portable copy.';
 clearTimeout(ideasTimer);if(localWorkshop)ideasTimer=setTimeout(saveIdeas,650);
}
async function saveIdeas(){
 clearTimeout(ideasTimer);if(!ideasLoaded||ideasSaving)return;
 if(!localWorkshop){$('ideasExport').click();return}
 if(!ideasDirty)return;ideasSaving=true;const generation=ideasGeneration;
 try{const result=await ideasRequest('POST',{data:ideasData,revision:ideasRevision});ideasRevision=result.revision;
  if(generation===ideasGeneration){ideasDirty=false;$('ideasStatus').textContent='All notes saved · workshop-ideas.json'}
  else ideasTimer=setTimeout(saveIdeas,100);
 }catch(e){$('ideasStatus').textContent=e.message+' Your edits remain here; Export notes keeps a copy.'}
 finally{ideasSaving=false}
}
$('ideasNew').onclick=()=>{if(!ideasLoaded)return;const i={id:crypto.randomUUID(),title:'',class:$('ideasClassFilter').value||'GENERAL',kind:'Card',stage:'Idea',tags:'',concept:'',synergy:'',balance:'',updated:''};ideasData.ideas.unshift(i);ideaId=i.id;ideaChanged(i);renderIdea();document.querySelector('#ideaEditor input').focus()};
$('ideasSearch').oninput=renderIdeas;$('ideasClassFilter').onchange=renderIdeas;$('ideasArchived').onchange=renderIdeas;$('ideasSave').onclick=saveIdeas;
$('ideasExport').onclick=()=>{const url=URL.createObjectURL(new Blob([JSON.stringify(ideasData,null,2)],{type:'application/json'})),a=document.createElement('a');a.href=url;a.download='craftbound-ideas.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)};
window.addEventListener('beforeunload',e=>{if(ideasDirty&&localWorkshop){e.preventDefault();e.returnValue=''}});
