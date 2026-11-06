// Every keyword and mechanic the Workshop offers needs a rule explanation, so new ones can't ship unexplained.
const assert=require('node:assert/strict'),fs=require('node:fs'),M=require('./model.js');
const sections={trigger:M.TRIGGERS,action:M.ACTIONS,target:M.TARGETS,passive:M.PASSIVES,keyword:M.KEYWORDS,chooser:M.HAND_CHOOSERS,element:M.ELEMENTS,boost:M.BOOST_WHEN,brew:["POTION","POTENCY","DURATION","LINGERING"],card:["USES"]};
for(const [section,list] of Object.entries(sections)){
 assert.ok(Array.isArray(list)&&list.length,section+" list is exported");
 const missing=list.filter(x=>!(M.GUIDE[section]&&String(M.GUIDE[section][x]||"").trim()));
 assert.deepEqual(missing,[],"Add a rule explanation in model.js GUIDE."+section+" for: "+missing.join(", "));
}
// Mechanics added to the game's Java enums must also reach the Workshop (and so the guide).
const java=fs.readFileSync('src/main/java/craftbound/card/CardCatalog.java','utf8');
const javaEnum=name=>{const at=java.indexOf('enum '+name+' {');assert.ok(at>=0,'Java enum '+name);const body=java.slice(java.indexOf('{',at)+1,java.indexOf('}',at));return body.split(';')[0].split(',').map(s=>s.trim()).filter(Boolean);};
const retired=new Set(["DISMANTLE"]);
for(const [name,list] of [["Trigger",M.TRIGGERS],["Action",M.ACTIONS],["Aim",M.TARGETS],["Passive",M.PASSIVES]]){
 const missing=javaEnum(name).filter(x=>!retired.has(x)&&!list.includes(x));
 assert.deepEqual(missing,[],"Java "+name+" values missing from the Workshop: "+missing.join(", "));
}
const html=fs.readFileSync('tools/card-editor/editor.js','utf8');
for(const section of Object.keys(sections))assert.ok(html.includes('<option value="'+section+'">'),"Rule explanations browser lists "+section);
console.log('PASS: every keyword, trigger, action, target, passive and hand chooser has a Workshop rule explanation.');
