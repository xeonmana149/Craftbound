const assert=require('node:assert/strict'),fs=require('node:fs'),M=require('./model.js');
const source=JSON.parse(fs.readFileSync('src/test/resources/craftbound/test-cards.json','utf8'));
const data=M.clone(source),c=data.cards.find(c=>c.id==='craftbound:ravager');
c.autoText=true;c.keywords=[];c.passive='NONE';
c.effects=[{...M.effect(),trigger:'AFTER_SURVIVING_KILL',action:'DAMAGE',target:'ENEMY_HERO',amount:2,boardKind:'BOTH',type:'',excludeSelf:false,card:''}];
assert.deepEqual(M.validate(data,source),[]);
assert.equal(M.description(c,data),'After this attacks and destroys a mob or block, if it survives: Deal 2 damage to the enemy hero.');
c.keywords=['BLOCK'];c.attack=0;assert.ok(M.validate(data,source).some(e=>String(e).includes('non-block mob')));
console.log('PASS: surviving attack kill authoring, wording and Block rejection.');
