const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const M = require("./model.js");
const source = JSON.parse(fs.readFileSync(path.join(__dirname, "../../src/test/resources/craftbound/test-cards.json"), "utf8"));
const data = M.clone(source);
let sequence = 0;
function card(base, edits, effects) {
 const c = {...M.clone(source.cards.find(c => c.id === "craftbound:" + base)), ...edits,
  id: "craftbound:mechanic_check_" + sequence++, autoText: true, effects};
 data.cards.push(c); return c;
}
function effect(edits) {
 return {...M.effect(), trigger:"PLAY", target:"SELF", type:"", boardKind:"BOTH",
  excludeSelf:false, attack:0, health:0, card:"", amount:1, ...edits};
}
const crop=card("wheat", {}, [effect({trigger:"HARVEST",action:"DRAW"})]);
card("dispenser", {}, [effect({trigger:"DISMANTLE",action:"DAMAGE",target:"ENEMY_HERO"})]);
card("iron_sword", {}, [effect({trigger:"ON_KILL",action:"GIVE",card:"craftbound:wheat"})]);
for(const trigger of ["CARD_ADDED","ITEM_PLAYED","MINED","HEALED"]) {
 card("zombie", {}, [effect({trigger,action:"DRAW",eventScope:"FRIENDLY",eventType: trigger==="CARD_ADDED"?"AQUATIC":""})]);
}
card("zombie", {passive:"AURA_ATTACK",passiveAmount:1,auraType:"ILLAGER"}, []);
const buff=card("bone_meal", {}, [effect({action:"BUFF",target:"CHOSEN_ENEMY",boardKind:"MOB",attack:-2})]);
const grant=card("bone_meal", {}, [effect({action:"GRANT_KEYWORD",target:"CHOSEN_FRIENDLY",boardKind:"MOB",keyword:"SPRINT"})]);
const random=card("bone_meal", {}, [effect({action:"GIVE_RANDOM",pool:["craftbound:wheat","craftbound:arrow"]})]);
card("bone_meal", {}, [effect({action:"DAMAGE",target:"ENEMY_HERO",countScope:"FRIENDLY",countKind:"BLOCK",countType:"ORE"})]);
card("bone_meal", {}, [effect({action:"WELL_FED",target:"CHOSEN_CHARACTER",amount:3})]);
const transform=card("bone_meal", {}, [effect({action:"TRANSFORM",target:"CHOSEN_FRIENDLY",boardKind:"MOB",card:"craftbound:zombie"})]);
const enchant=card("sharpness", {}, [effect({action:"ENCHANT_TOOL"}),effect({trigger:"ON_KILL",action:"DRAW"})]);
card("zombie", {}, [effect({trigger:"HEALED",action:"BUFF",target:"EVENT",boardKind:"MOB",attack:1,eventScope:"FRIENDLY"})]);
assert.deepEqual(M.validate(data,source), []);
assert.match(M.description(crop,data), /Harvest/);
assert.match(M.description(buff,data), /-2/);
assert.doesNotMatch(M.description(buff,data), /\+-/);
assert.equal(M.description(enchant,data), "Your tool gains: After this destroys a mob or block, draw 1 card.");
enchant.effects[0].attack=2;
assert.doesNotMatch(M.description(enchant,data), /\+0|Durability/);
enchant.effects[0].attack=0;enchant.effects[0].health=2;
assert.doesNotMatch(M.description(enchant,data), /\+0|Attack/);
function bad(obj,key,value) {
 const prior=obj[key];obj[key]=value;
 assert.ok(M.validate(data,source).length, key+" accepted invalid value");
 obj[key]=prior;
}
bad(crop,"type","UNDEAD");
bad(grant.effects[0],"keyword","BLOCK");
bad(random.effects[0],"pool",["craftbound:not_real"]);
bad(transform.effects[0],"card","craftbound:arrow");
bad(buff.effects[0],"boardKind","BLOCK");
bad(enchant.effects[1],"countScope","INVALID");
const target=card("zombie",{},[]);
random.effects[0].pool.push(target.id);
M.renameCard(target,"craftbound:renamed_pool_target",data);
assert.equal(random.effects[0].pool.at(-1),target.id);
assert.ok(M.cardsUsing(data,target.id).length);
assert.deepEqual(M.validate(JSON.parse(JSON.stringify(data)),source),[]);
console.log("PASS: new triggers/actions, typed auras, negative buffs, attached enchantments, random pools, for-each, Well Fed, transform, generated text, validation, rename links and JSON round trip.");
