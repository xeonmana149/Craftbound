const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const M = require("./model.js");
const source = JSON.parse(fs.readFileSync(path.join(__dirname, "../../src/test/resources/craftbound/test-cards.json"), "utf8"));
const java = fs.readFileSync(path.join(__dirname, "../../src/main/java/craftbound/card/CardCatalog.java"), "utf8");
for (const [field, enumName] of [["castFx","CastFx"],["deathFx","DeathFx"],["arrivalFx","ArrivalFx"]]) {
 const values = java.match(new RegExp("enum " + enumName + " \\{([^}]+)\\}"))[1].split(",").map(s=>s.trim());
 assert.deepEqual(Object.keys(M.FX[field]),values,field+" choices must match the server catalogue");
}
const types = ["zombie","dispenser","bone_meal","iron_sword","sharpness"];
for (const type of types) {
 const original=source.cards.find(c=>c.id==="craftbound:"+type);
 for (const [field, choices] of Object.entries(M.FX)) for (const choice of Object.keys(choices)) {
  const data=M.clone(source),card=data.cards.find(c=>c.id===original.id);
  card[field]=choice;
  const prepared=M.prepareCatalog(JSON.parse(JSON.stringify(data)));
  assert.equal(prepared.cards.find(c=>c.id===original.id)[field],choice,"Load must preserve "+type+" "+field+" "+choice);
  assert.deepEqual(M.validate(prepared,source),[],type+" must accept "+field+" "+choice);
  assert.doesNotMatch(M.animationNotes(card).join(" "),/undefined/);
 }
}
const block=M.clone(source.cards.find(c=>c.id==="craftbound:dispenser"));
block.deathFx="SPLIT";
assert.ok(M.animationNotes(block).includes("Death: Split in two"),"Manual death overrides block default in preview");
const tool=M.clone(source.cards.find(c=>c.id==="craftbound:iron_sword"));
tool.castFx="EYE_OF_ENDER";
assert.ok(M.animationNotes(tool).some(s=>s.startsWith("Play: Eye of Ender")),"Tool preview must explain its manual cast");
const eye={...tool,kind:"ITEM",id:"craftbound:renamed_eye",art:"minecraft:ender_eye",effects:[],castFx:"AUTO"};
assert.ok(M.animationNotes(eye).includes("Play: Eye of Ender throw and scan"),"Automatic Eye recognition cannot depend on Reveal Hand");
eye.castFx="PLAIN";
assert.ok(M.animationNotes(eye)[0].startsWith("Play: Standard card play"),"Explicit standard play disables inferred Eye");
// Exercise the real panel builder without a browser: every kind exposes every selector.
const editor=fs.readFileSync(path.join(__dirname,"editor.js"),"utf8");
const panel=editor.slice(editor.indexOf("function animationPanel("),editor.indexOf("function refreshAnimations("));
const context={M,select:(label,key,values,current)=>({label,key,values,current})};
context.select=(label,key,values,current)=>`<select data-field="${key}">${values.map(v=>`<option${v===current?' selected':''}>${v}</option>`).join("")}</select>`;
vm.createContext(context);vm.runInContext(panel,context);
for(const type of types){const markup=context.animationPanel(source.cards.find(c=>c.id==="craftbound:"+type));
 for(const field of Object.keys(M.FX))assert.ok(markup.includes(`data-field="${field}"`),type+" is missing "+field);
}
console.log("PASS: all animation choices on mobs, blocks, actions, tools and enchantments; Java parity, save/load, overrides and unrestricted controls.");
