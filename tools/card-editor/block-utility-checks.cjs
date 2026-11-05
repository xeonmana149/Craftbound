const assert=require('node:assert/strict'),fs=require('node:fs'),M=require('./model.js');
const source=JSON.parse(fs.readFileSync('src/test/resources/craftbound/test-cards.json','utf8'));
const data=M.clone(source);
function clone(base,id){
 const c=M.clone(data.cards.find(c=>c.id==='craftbound:'+base));
 Object.assign(c,{id:'craftbound:'+id,autoText:true,text:'',effects:[],passive:'NONE',passiveAmount:0,auraType:''});
 data.cards.push(c);return c;
}
const arch=clone('furnace','test_arch');
Object.assign(arch,{passive:'AURA_STATS',passiveAmount:0,auraHealth:3,auraKind:'BLOCK'});
const press=clone('bone_meal','test_press');
press.effects=[{...M.effect(),excludeSelf:false,action:'RETURN_AND_COPY',target:'CHOSEN_FRIENDLY',boardKind:'BLOCK',amount:1}];
const chest=clone('furnace','test_blueprint');
chest.effects=[{...M.effect(),excludeSelf:false,action:'CHOOSE_FROM_DECK',target:'SELF',boardKind:'BLOCK',amount:3},
 {...M.effect(),excludeSelf:false,trigger:'DROP',action:'DRAW',target:'SELF',amount:1}];
assert.deepEqual(M.validate(data,source),[]);
assert.equal(M.description(arch,data),'Your other blocks have +3 Health.');
assert.match(M.description(press,data),/Return a friendly block to its owner's hand, then add a copy of it to your hand/);
assert.match(M.description(chest,data),/Choose one of up to 3 different blocks from your deck and draw it/);
assert.match(M.description(chest,data),/Drop: Draw 1 card/);
assert.deepEqual(M.validate(JSON.parse(JSON.stringify(data)),source),[]);
arch.passiveAmount=1;assert.ok(M.validate(data,source).length);arch.passiveAmount=0;
arch.auraKind='INVALID';assert.ok(M.validate(data,source).length);arch.auraKind='BLOCK';
press.effects[0].amount=11;assert.ok(M.validate(data,source).length);press.effects[0].amount=1;
for(const [key,value] of [['target','CHOSEN_FRIENDLY'],['amount',4],['amount',0],['excludeSelf',true]]) {
 const before=chest.effects[0][key];chest.effects[0][key]=value;
 assert.ok(M.validate(data,source).length,key+' must be rejected');chest.effects[0][key]=before;
}
chest.effects[0].boardKind='MOB';chest.effects[0].type='UNDEAD';
assert.deepEqual(M.validate(data,source),[]);
assert.match(M.description(chest,data),/different Undead mobs from your deck/);
assert.ok(M.GUIDE.action.RETURN_AND_COPY);
assert.ok(M.GUIDE.action.CHOOSE_FROM_DECK);
console.log('PASS: block auras, return-and-copy, deck choices, wording, save roundtrip and validation.');
