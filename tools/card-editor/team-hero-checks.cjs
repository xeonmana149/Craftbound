const assert = require('node:assert/strict');
const fs = require('node:fs');
const M = require('./model.js');
const baseline = JSON.parse(fs.readFileSync('src/test/resources/craftbound/test-cards.json','utf8'));
const data = M.clone(baseline);
const card = data.cards.find(c => c.id === 'craftbound:skeleton');
card.autoText = true;
card.keywords = [];
card.passive = 'NONE';
const f = {...M.effect(), trigger:'PLAY', amount:2, boardKind:'BOTH', type:'', excludeSelf:false, card:''};
card.effects = [f];
for (const [action,target,printed] of [
 ['DAMAGE','EACH_ENEMY_HERO','Deal 2 damage to each enemy hero'],
 ['FREEZE','EACH_HERO','Freeze each hero'],
 ['DESTROY_TOOL','EACH_ENEMY_HERO', "Destroy each enemy hero's tool"],
 ['TOOL_DAMAGE','EACH_HERO', "Deal your tool's attack as damage to each hero"],
 ['BAD_OMEN','EACH_ENEMY_HERO','Give each enemy hero Bad Omen']
]) {
 Object.assign(f,{action,target,amount:action==='BAD_OMEN'?1:2,card:action==='BAD_OMEN'?card.id:''});
 assert(M.targetOptions(action).includes(target));
 assert.deepEqual(M.validate(data,baseline),[]);
 assert(M.description(card,data).includes(printed),M.description(card,data));
}
for (const [action,target] of [['BAD_OMEN','EACH_HERO'],['WITHER','EACH_HERO'],['BUFF','EACH_ENEMY_HERO']]) {
 Object.assign(f,{action,target,card:action==='BAD_OMEN'?card.id:''});
 assert(!M.targetOptions(action).includes(target));
 assert(M.validate(data,baseline).length);
}
Object.assign(f,{action:'DAMAGE',target:'EACH_HERO',type:'UNDEAD'});
assert(M.validate(data,baseline).some(m=>m.includes('Type filters')));
console.log('PASS: team hero targets, printed rules and incompatible actions.');
