const assert=require('node:assert/strict'),fs=require('node:fs'),M=require('./model.js');
const source=JSON.parse(fs.readFileSync('src/test/resources/craftbound/test-cards.json','utf8'));
const data=M.clone(source),aura=data.cards.find(c=>c.id==='craftbound:zombie');
Object.assign(aura,{autoText:true,effects:[],passive:'AURA_STATS',passiveAmount:1,auraHealth:2,auraType:'UNDEAD'});
assert.deepEqual(M.validate(data,source),[]);
assert.equal(M.description(aura,data),'Your other Undead mobs have +1 Attack and +2 Health.');
aura.passiveAmount=0;assert.equal(M.description(aura,data),'Your other Undead mobs have +2 Health.');
for(const invalid of [-1,101,1.5,'2']){aura.auraHealth=invalid;assert.ok(M.validate(data,source).length)}
aura.auraHealth=2;
const chest=data.cards.find(c=>c.id==='craftbound:furnace');
Object.assign(chest,{autoText:true,effects:[{...M.effect(),trigger:'END_TURN',action:'GIVE_RANDOM_CLASS',target:'SELF',type:'',boardKind:'BOTH',excludeSelf:false,card:'',amount:1}]});
assert.deepEqual(M.validate(data,source),[]);
assert.match(M.description(chest,data),/At the end of your turn: Add a random card from your class to your hand\./);
chest.effects[0].amount=2;assert.match(M.description(chest,data),/Add 2 random cards/);
chest.effects[0].target='CHOSEN_FRIENDLY';assert.ok(M.validate(data,source).length);
console.log('PASS: Attack/Health aura and random class card authoring, wording and validation.');
