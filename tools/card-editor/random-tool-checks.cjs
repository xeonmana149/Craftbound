const assert=require('node:assert/strict'),fs=require('node:fs'),M=require('./model.js');
const source=JSON.parse(fs.readFileSync('src/test/resources/craftbound/test-cards.json','utf8'));
const data=M.clone(source),c=data.cards.find(c=>c.id==='craftbound:furnace');c.autoText=true;
c.effects=[{...M.effect(),trigger:'END_TURN',action:'UPGRADE_TOOL',target:'SELF',boardKind:'BOTH',type:'',excludeSelf:false,card:'',attack:2,health:0}];
assert.deepEqual(M.validate(data,source),[]);assert.equal(M.description(c,data),'At the end of your turn: Give your equipped tool +2 Attack.');
c.effects[0].attack=0;c.effects[0].health=3;assert.deepEqual(M.validate(data,source),[]);assert.match(M.description(c,data),/\+3 Durability/);assert.doesNotMatch(M.description(c,data),/Attack/);
c.effects[0].health=0;assert.ok(M.validate(data,source).length);
for(const target of ['RANDOM_ENEMY','RANDOM_FRIENDLY','RANDOM_BLOCK','RANDOM_MOB','RANDOM_HERO']){
 c.effects=[{...M.effect(),trigger:'END_TURN',action:'DAMAGE',target,boardKind:target==='RANDOM_BLOCK'?'BLOCK':target==='RANDOM_MOB'?'MOB':'BOTH',type:'',excludeSelf:false,card:''}];
 assert.deepEqual(M.validate(data,source),[],target);assert.match(M.description(c,data),/random/);
}
c.effects[0].action='WITHER';assert.ok(M.validate(data,source).length);
console.log('PASS: Attack-only/Durability-only tool improvement and all random target options.');
