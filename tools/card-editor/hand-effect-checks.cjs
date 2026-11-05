const assert = require('node:assert/strict');
const fs = require('node:fs');
const M = require('./model.js');
const baseline = JSON.parse(fs.readFileSync('src/test/resources/craftbound/test-cards.json','utf8'));
const data = M.clone(baseline);
const card = data.cards.find(c => c.id === 'craftbound:skeleton');
card.autoText = true;
card.keywords = [];
card.passive = 'NONE';
const f = {...M.effect(), trigger:'PLAY', amount:1, boardKind:'BOTH', type:'', excludeSelf:false, card:''};
card.effects = [f];
for (const [action,chooser,target,amount,printed] of [
 ['REVEAL_HAND',undefined,'ENEMY_HERO',1,"Look at your opponent's hand"],
 ['DISCARD_FROM_HAND','YOU','ENEMY_HERO',1,"Look at your opponent's hand and discard a card from it"],
 ['DISCARD_FROM_HAND','OPPONENT','ENEMY_HERO',2,'Your opponent discards 2 cards of their choice'],
 ['DISCARD_FROM_HAND','RANDOM','EACH_ENEMY_HERO',1,'Each opponent discards a random card'],
 ['STEAL_FROM_HAND','YOU','ENEMY_HERO',1,"Look at your opponent's hand and steal a card from it"],
 ['STEAL_FROM_HAND','OPPONENT','ENEMY_HERO',1,'Your opponent gives you a card of their choice from their hand'],
 ['STEAL_FROM_HAND','RANDOM','ENEMY_HERO',2,"Steal 2 random cards from your opponent's hand"],
 ['SHUFFLE_FROM_HAND','YOU','ENEMY_HERO',1,"Look at your opponent's hand and shuffle a card from it into their deck"],
 ['SHUFFLE_FROM_HAND','RANDOM','ENEMY_HERO',1,"Shuffle a random card from your opponent's hand into their deck"]
]) {
 Object.assign(f,{action,target,amount});
 if (chooser) f.chooser = chooser; else delete f.chooser;
 assert.deepEqual(M.targetOptions(action),['ENEMY_HERO','EACH_ENEMY_HERO']);
 assert.deepEqual(M.validate(data,baseline),[],action+' '+chooser);
 assert(M.description(card,data).includes(printed),M.description(card,data));
}
const bad = (changes) => { const saved = M.clone(f); Object.assign(f,changes); const issues = M.validate(data,baseline); for (const k of Object.keys(f)) delete f[k]; Object.assign(f,saved); return issues.length > 0; };
Object.assign(f,{action:'STEAL_FROM_HAND',target:'ENEMY_HERO',amount:1,chooser:'YOU'});
assert(bad({target:'ENEMY_MOBS'}),'board target rejected');
assert(bad({type:'UNDEAD'}),'type filter rejected');
assert(bad({chooser:'SOMEONE'}),'bad chooser rejected');
assert(bad({amount:11}),'more than 10 rejected');
assert(bad({action:'REVEAL_HAND',chooser:'YOU'}),'reveal has no chooser');
assert(bad({action:'REVEAL_HAND',chooser:undefined,amount:2}),'reveal amount is 1');
assert(bad({action:'DAMAGE',chooser:'YOU'}),'chooser only on hand picks');
console.log('PASS: hand effects (reveal, discard, steal, shuffle), choosers, printed rules and validation.');
