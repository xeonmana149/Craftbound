const assert=require("node:assert/strict"), fs=require("node:fs"), path=require("node:path"), vm=require("node:vm");
const M=require("./model.js");
{
 const a={id:"a",effects:[]},b={id:"b",effects:[]},c={id:"c",deleted:true,effects:[]};
 a.effects=[{action:"SUMMON",card:"b",amount:2},{action:"GIVE",card:"b",amount:1},{action:"GIVE_RANDOM",pool:["b","c","b"]},{action:"TRANSFORM",card:"missing"},{action:"DAMAGE",card:"stale"},{action:"SUMMON",card:"a"}];
 const links=M.associatedCards({cards:[a,b,c]},a);
 assert.deepEqual(links.map(x=>x.id),["b","c","missing","a"]);
 assert.equal(links[0].effects.length,3);assert.equal(links[1].card.deleted,true);assert.equal(links[2].card,null);
 a.effects=[{action:"GIVE_RANDOM_CLASS"}];assert.deepEqual(M.associatedCards({cards:[a,b]},a),[]);
}
const production=JSON.parse(fs.readFileSync(path.join(__dirname,"../../src/main/resources/craftbound/cards.json"),"utf8"));
const source=JSON.parse(fs.readFileSync(path.join(__dirname,"../../src/test/resources/craftbound/test-cards.json"),"utf8"));
const context={window:{}};vm.runInNewContext(fs.readFileSync(path.join(__dirname,"defaults.js"),"utf8"),context);
assert.equal(JSON.stringify(context.window.CRAFTBOUND_DEFAULTS),JSON.stringify(production),"Browser starter copy drifted");
assert.equal(production.cards.length,9);
assert.ok(production.cards.every(c=>c.token));
assert.deepEqual(M.validate(production,production),[]);
const fresh=M.clone(production),first=M.clone(fresh.cards.find(c=>c.id==="craftbound:wolf_pup"));
first.id="craftbound:first_new_card";first.token=false;first.rarity="EPIC";fresh.cards.push(first);
assert.deepEqual(M.validate(fresh,production),[],"An unfinished set can be saved");
delete fresh.workshopEpoch;
assert.ok(M.validate(fresh,production).length,"Reject stale drafts from before the reset");
assert.deepEqual(M.validate(source,source),[]);
assert.equal(source.cards.length,101);
assert.equal(source.cards.filter(c=>!c.token).length,80);
let checks=0;
function bad(edit){let d=M.clone(source);edit(d);assert.ok(M.validate(d,source).length,"Invalid catalogue was accepted");checks++}
bad(d=>d.cards[0].cost=1.5);bad(d=>d.cards[0].cost=-1);bad(d=>d.cards[0].cost=11);
bad(d=>d.cards[0].cost="1");bad(d=>d.cards[0].kind="SPELL");bad(d=>d.cards[0].token=false);
bad(d=>d.cards[0].name="");bad(d=>d.cards[0].id="../escape");bad(d=>d.cards.push(M.clone(d.cards[0])));
bad(d=>d.cards.pop());bad(d=>d.cards.reverse());bad(d=>d.cards[6].health=0);
bad(d=>d.cards[0].effects[0].action="EXECUTE");bad(d=>d.cards[0].effects[0].action="BUFF");
bad(d=>d.cards.find(c=>c.id==="craftbound:evoker").effects[0].card="craftbound:missing");
bad(d=>d.cards.find(c=>c.id==="craftbound:evoker").effects[0].card="craftbound:arrow");
bad(d=>d.cards[0].effects[0].type="AQUATIC");bad(d=>d.cards[0].effects[0].amount=0);
bad(d=>d.cards[0].effects[0].amount=101);bad(d=>d.cards[0].keywords=["RANGED"]);
bad(d=>d.cards.find(c=>c.id==="craftbound:composter").attack=1);
bad(d=>d.cards[0].effects[0].target="SELF");bad(d=>d.cards[0].passive="ARMOR");
const incomplete=M.clone(source);incomplete.cards.forEach(c=>{if(c.rarity==="LEGENDARY")c.rarity="EPIC"});assert.deepEqual(M.validate(incomplete,source),[]);
const d=M.clone(source),custom=M.clone(d.cards.find(c=>c.id==="craftbound:zombie"));
custom.id="craftbound:reef_keeper";custom.name="Reef Keeper";custom.type="AQUATIC";custom.effects=[M.effect()];custom.effects[0].type="AQUATIC";custom.autoText=true;
d.cards.push(custom);assert.deepEqual(M.validate(d,source),[]);
assert.match(M.description(custom,d),/Spawn: Give other friendly Aquatic mobs \+1\/\+1/);
custom.effects[0]={...M.effect(),trigger:"AFTER_ATTACK",action:"SUMMON",target:"SELF",type:"",card:"craftbound:wheat",excludeSelf:false};
custom.kind="TOOL";custom.type="";custom.keywords=[];custom.class="FARMER";
assert.deepEqual(M.validate(d,source),[]);
assert.match(M.description(custom,d),/After your hero attacks: Summon Wheat/);
custom.kind="ITEM";custom.effects=[{...M.effect(),action:"DRAW",target:"SELF",amount:1}];
assert.equal(M.description(custom,d),"Draw 1 card.");
custom.effects[0].amount=2;assert.equal(M.description(custom,d),"Draw 2 cards.");
custom.kind="MOB";custom.effects[0].trigger="END_TURN";
assert.equal(M.description(custom,d),"At the end of your turn: Draw 2 cards.");
custom.effects=[{...M.effect(),trigger:"HEALED",action:"DRAW",target:"SELF",eventScope:"FRIENDLY",eventType:"UNDEAD",amount:1}];
assert.equal(M.description(custom,d),"After a friendly Undead mob is healed: Draw 1 card.");
custom.kind="ENCHANTMENT";custom.effects=[{...M.effect(),trigger:"AFTER_ATTACK",action:"DRAW",target:"SELF",amount:1}];
assert.equal(M.description(custom,d),"Your tool gains: After your hero attacks: Draw 1 card.");
const roundtrip=JSON.parse(JSON.stringify(d));assert.deepEqual(roundtrip,d);
assert.equal(M.idForName("Reef Keeper",[]),"craftbound:reef_keeper");
assert.equal(M.idForName("  Farmer’s Élite Hoe!  ",[]),"craftbound:farmers_elite_hoe");
assert.equal(M.idForName("?!",[]),"craftbound:new_card");
const named={id:"craftbound:reef_keeper",effects:[]};
assert.equal(M.idForName("Reef Keeper",[named],named),named.id);
assert.equal(M.idForName("Reef Keeper",[named,{id:"craftbound:reef_keeper_2"}]),"craftbound:reef_keeper_3");
const linked={id:"craftbound:caller",effects:[{card:named.id},{card:"craftbound:zombie"}]};
M.renameCard(named,"craftbound:reef_guardian",{cards:[named,linked]});
assert.equal(linked.effects[0].card,named.id);assert.equal(linked.effects[1].card,"craftbound:zombie");
console.log("PASS: name-derived IDs, punctuation/accents, empty names, duplicate names and reference updates.");
console.log("PASS: "+source.cards.length+" definitions, "+source.cards.filter(c=>!c.token).length+" collectibles, "+checks+" invalid edits rejected, Aquatic synergy, tool summon, text and JSON round trip.");

for(const passive of ["GRAZER","BATTLE_HARDENED","SCHOOLING"]) {
 const d=M.clone(source), c=d.cards.find(c=>c.id==="craftbound:cow");
 c.passive=passive;c.passiveAmount=2;c.autoText=true;
 assert.deepEqual(M.validate(d,source),[]);
 assert.ok(M.description(c,d).includes(passive==="GRAZER"?"control a Crop":passive==="SCHOOLING"?"until the end of the turn":"Once per turn"));
}
bad(d=>d.cards.find(c=>c.id==="craftbound:wheat").passive="SCHOOLING");
bad(d=>d.cards.find(c=>c.id==="craftbound:wheat").passive="BATTLE_HARDENED");
{
 const d=M.clone(source), c=d.cards.find(c=>c.id==="craftbound:bone_meal");c.autoText=true;
 c.effects=[{...M.effect(),trigger:"PLAY",action:"CLEANSE",target:"CHOSEN_FRIENDLY",boardKind:"BOTH",type:"",excludeSelf:false}];
 assert.deepEqual(M.validate(d,source),[]);
 assert.ok(M.description(c,d).includes("Cleanse"));
 c.effects[0].target="FRIENDLY_HERO";assert.deepEqual(M.validate(d,source),[]);
}
console.log("PASS: Grazer, Battle-hardened, Schooling and Cleanse authoring/validation.");

for(const [name,template] of Object.entries(M.TEMPLATES)){
 const d=M.clone(source),c=M.clone(template);c.id=M.idForName(c.name,d.cards);d.cards.push(c);
 assert.deepEqual(M.validate(d,source),[],name+" template validates");
 assert.ok(M.description(c,d).length>10,name+" template has generated rules");
}
console.log("PASS: all new-card templates preserve existing IDs and validate.");

{
 const d=M.clone(source),c=M.clone(M.TEMPLATES.porkchop);d.cards.push(c);
 assert.deepEqual(M.validate(d,source),[]);
 assert.ok(M.description(c,d).includes("mob or hero"));
 c.effects[0].action="WITHER";assert.ok(M.validate(d,source).length);
 c.effects[0].action="HEAL";c.effects[0].type="UNDEAD";assert.ok(M.validate(d,source).length);
}
console.log("PASS: chosen mob/hero healing, generated text and invalid status/type restrictions.");

{
 const cards=[
  {class:"FARMER",cost:0,token:false},{class:"FARMER",cost:3,token:false},
  {class:"FARMER",cost:10,token:false},{class:"FARMER",cost:1,token:true},
  {class:"NEUTRAL",cost:7,token:false},{class:"FIGHTER",cost:2,token:false}
 ];
 let curve=M.xpCurve(cards,"FARMER");assert.equal(curve.total,3);assert.equal(curve.average,13/3);assert.equal(curve.counts[10],1);assert.equal(curve.counts[1],0);
 curve=M.xpCurve(cards,"FARMER",true);assert.equal(curve.total,4);assert.equal(curve.average,5);
 assert.equal(M.xpCurve(cards,"NEUTRAL",true).total,1);
 assert.equal(M.xpCurve(cards,"ALCHEMIST").average,0);
 cards[0].cost=4;assert.equal(M.xpCurve(cards,"FARMER").counts[0],0);assert.equal(M.xpCurve(cards,"FARMER").counts[4],1);
}
console.log("PASS: class XP curves exclude tokens/other classes, optionally include Neutral, and update after edits.");

{
 const d=M.clone(source),index=d.cards.findIndex(c=>c.id==="craftbound:witch");
 d.cards[index].effects[0].amount=0;
 let issue=M.validationIssues(d,source).find(e=>e.cardId==="craftbound:witch"&&e.field==="amount");
 assert.equal(issue.cardIndex,index);assert.equal(issue.effectIndex,0);
 d.cards[index].effects.push({...M.effect(),amount:0});
 assert.equal(M.validationIssues(d,source).filter(e=>e.cardId==="craftbound:witch"&&e.field==="amount").length,2);
 d.cards=d.cards.filter(c=>c.id!=="craftbound:minor_harming_potion");
 issue=M.validationIssues(d,source).find(e=>e.code==="missing"&&e.cardId==="craftbound:minor_harming_potion");
 assert.equal(issue.cardIndex,-1);assert.equal(issue.expected.name,"Minor Harming Potion");
 const reordered=M.clone(source),a=reordered.cards.findIndex(c=>!c.token),b=reordered.cards.findIndex((c,i)=>i>a&&!c.token);
 [reordered.cards[a],reordered.cards[b]]=[reordered.cards[b],reordered.cards[a]];
 issue=M.validationIssues(reordered,source).find(e=>e.code==="order");
 assert.equal(issue.collectorNumber,1);assert.equal(issue.expectedId,source.cards[a].id);
 const identity=M.clone(source);identity.cards[0].token=false;
 issue=M.validationIssues(identity,source).find(e=>e.code==="identity");assert.equal(issue.field,"token");
 assert.deepEqual([...new Set(M.validationIssues(d,source).map(e=>e.message))],M.validate(d,source),"Detailed errors retain save/server validation");
 for(const field of ["target","boardKind","type","trigger"]){
  const invalid=M.clone(source),e=invalid.cards.find(c=>c.id==="craftbound:mine").effects[0];
  e[field]={target:"CHOSEN_ENEMY",boardKind:"MOB",type:"CROP",trigger:"DROP"}[field];
  assert.ok(M.validate(invalid,source).some(e=>e.includes("Mine requires")));
 }
 assert.equal(M.cardsUsing(source,"craftbound:mined_coal").length,2);
 assert.ok(M.TOKEN_SOURCES["craftbound:iron_ore_token"]);
}
console.log("PASS: exact validation card/effect/field locations, missing-card diagnostics, order/identity errors and Miner rules.");

{
 const d=M.clone(source),milk=M.clone(M.TEMPLATES.milk);d.cards.push(milk);
 assert.deepEqual(M.validate(d,source),[]);
 assert.ok(M.description(milk,d).includes("Cleanse")&&!M.description(milk,d).includes("Frozen and Withered"));
 assert.equal(milk.effects[0].target,"CHOSEN_CHARACTER");
 milk.effects[0].action="FREEZE";assert.deepEqual(M.validate(d,source),[]);
 milk.effects[0].action="WITHER";assert.ok(M.validate(d,source).length);
 assert.ok(M.CLASSES.includes("CHEF")&&M.CLASSES.includes("TAMER")&&M.TYPES.includes("TAMEABLE"));
}
console.log("PASS: hero Cleanse/Freeze authoring, no hero Withered, and Chef/Tamer starter catalogue.");

{
 const d=M.clone(production),wall=M.clone(d.cards.find(c=>c.id==="craftbound:cobblestone_wall"));
 wall.id="craftbound:test_machine";wall.class="REDSTONER";wall.token=false;wall.autoText=true;
 wall.effects=[{...M.effect(),trigger:"ACTIVATE",action:"DRAW",target:"SELF",excludeSelf:false,amount:1}];d.cards.push(wall);
 assert.deepEqual(M.validate(d,production),[]);
 assert.match(M.description(wall,d),/Activate: Draw 1/);
 wall.keywords=[];assert.ok(M.validate(d,production).some(e=>e.includes("Activate requires a block")));
 wall.keywords=["BLOCK"];
 const puffer=d.cards.find(c=>c.id==="craftbound:caught_pufferfish");
 assert.equal(puffer.text,"Death: The enemy mob that killed this loses 2 Attack until the end of its controller's next turn.");
 assert.ok([...puffer.text].every(c=>c.charCodeAt(0)<128));
 puffer.autoText=true;
 assert.match(M.description(puffer,d),/-2 Attack/);
 assert.ok([...M.description(puffer,d)].every(c=>c.charCodeAt(0)<128));
 assert.match(M.description(puffer,d),/enemy mob that killed this/);
 assert.match(M.description(puffer,d),/controller's next turn/);
 puffer.effects[0].trigger="END_TURN";assert.ok(M.validate(d,production).some(e=>e.includes("Killer target requires")));
 assert.ok(M.CLASSES.includes("REDSTONER"));
}
console.log("PASS: Redstoner Activate authoring, required Block, killer-only Death targeting and temporary weakness wording.");

{
 const d=M.clone(production),c=M.clone(d.cards.find(c=>c.id==="craftbound:wolf_pup"));
 c.id="craftbound:test_ignore_shield";c.keywords=["IGNORE_SHIELD"];d.cards.push(c);
 assert.deepEqual(M.validate(d,production),[]);assert.equal(M.description(c,d),"Ignore Shield.");
 c.kind="TOOL";c.type="";assert.deepEqual(M.validate(d,production),[]);
 c.kind="ITEM";c.attack=0;c.health=0;assert.ok(M.validate(d,production).length);
 c.keywords=[];c.effects=[{...M.effect(),action:"GRANT_KEYWORD",target:"CHOSEN_FRIENDLY",boardKind:"MOB",keyword:"IGNORE_SHIELD",excludeSelf:false}];
 assert.deepEqual(M.validate(d,production),[]);assert.match(M.description(c,d),/Give a friendly mob Ignore Shield/);
 c.effects[0].boardKind="BLOCK";assert.ok(M.validate(d,production).length);
 console.log("PASS: native mob/tool Ignore Shield and grant effect, valid wording, rejects item combat keyword and block grant.");
}

{
 for(const [name,art] of [["Stone Sword","wooden_sword"],["Small Slime","slime"],["Polar Cub","polar_bear"],["Ender Chest","ender_chest"],["Snow Golem","snow_golem"],["Wood Fence","oak_fence"]]) {
  const c={name,art:"minecraft:paper",id:"craftbound:test"};M.autoArtwork(c);
  assert.equal(c.art,"minecraft:"+(name==="Stone Sword"?"stone_sword":art));
 }
 const c={name:"Stone Sword",id:"craftbound:sword",art:"minecraft:stonesword"};
 assert.ok(M.autoArtwork(c));assert.equal(c.art,"minecraft:stone_sword");
 c.name="Diamond Sword";M.autoArtwork(c);assert.equal(c.art,"minecraft:diamond_sword");
 c.autoArt=false;c.art="minecraft:stick";c.name="Iron Sword";M.autoArtwork(c);assert.equal(c.art,"minecraft:stick");
 const custom={name:"Zombie",art:"custom:painted_zombie"};M.autoArtwork(custom);assert.equal(custom.art,"custom:painted_zombie");
 const unknown={name:"Danny",id:"craftbound:danny",art:"minecraft:paper"};assert.equal(M.autoArtwork(unknown),false);
 const old=M.clone(production);old.cards=old.cards.filter(c=>c.id!=="craftbound:crafted_sword");M.prepareCatalog(old);assert.deepEqual(M.validate(old,production),[]);
 const snow=M.clone(production.cards.find(c=>c.id==="craftbound:wolf_pup"));snow.id="craftbound:snow_test";snow.effects=[{...M.effect(),trigger:"AFTER_ATTACK",action:"FREEZE",target:"EVENT",excludeSelf:false}];
 old.cards.push(snow);assert.deepEqual(M.validate(old,production),[]);assert.match(M.description(snow,old),/Freeze its target/);
 console.log("PASS: automatic artwork, aliases, rename updates, manual art protection, old draft token migration and attacked-character Freeze.");
}

{
 const saved=M.clone(production),before=M.clone(saved),draft=M.clone(saved);
 const id="craftbound:caught_cod",local=draft.cards.find(c=>c.id===id),remote=saved.cards.find(c=>c.id===id);
 local.attack=7;remote.art="minecraft:cod_spawn_egg";remote.attack=3;
 M.mergeSavedDraft(draft,before,saved);
 assert.equal(local.attack,7);assert.equal(local.art,"minecraft:cod_spawn_egg");
 console.log("PASS: recovery preserves unsaved edits while accepting external artwork/rules fixes to untouched fields.");
}
