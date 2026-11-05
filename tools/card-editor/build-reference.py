from pathlib import Path
import json, re, html, subprocess, collections, hashlib, os
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle, KeepTogether, CondPageBreak
from reportlab.platypus.tableofcontents import TableOfContents
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.colors import HexColor, white
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4

PROJECT=Path(__file__).resolve().parents[2]
BASE=PROJECT/'docs/design'
OUT=BASE
REG=json.loads((BASE/'Minecraft-26.3-reference-data.json').read_text(encoding='utf-8'))
def title(s):return s.split(':')[-1].replace('_',' ').title()
def short(s):return s.removeprefix('minecraft:')
node=os.environ.get('CRAFTBOUND_NODE','node')
js='const M=require("./tools/card-editor/model.js"),d=require("./src/main/resources/craftbound/cards.json");process.stdout.write(JSON.stringify(d.cards.map(c=>({...c,generatedDescription:M.description(c,d)}))));'
cards=json.loads(subprocess.check_output([node,'-e',js],cwd=PROJECT))

sections=[]
def slug(s):return re.sub(r'[^a-z0-9]+','-',s.lower()).strip('-')
def section(name,blocks):sections.append({'title':name,'id':slug(name),'blocks':blocks})
def para(t):return {'kind':'p','text':t}
def table(headers,rows):return {'kind':'table','headers':headers,'rows':rows}
lines=(BASE/'Foundation-reference-source.md').read_text(encoding='utf-8').splitlines()
cur=None;i=0
while i<len(lines):
 line=lines[i].strip()
 if line.startswith('# '):
  section(line[2:],[]);cur=sections[-1]['blocks'];i+=1;continue
 if not line:i+=1;continue
 if line.startswith('## '):cur.append({'kind':'h2','text':line[3:]});i+=1;continue
 if line.startswith('|'):
  rows=[]
  while i<len(lines) and lines[i].startswith('|'):
   rows.append([x.strip() for x in lines[i].strip().strip('|').split('|')]);i+=1
  cur.append(table(rows[0],rows[1:]));continue
 if line.startswith('- ') or re.match(r'^\d+\. ',line):
  cur.append({'kind':'li','text':re.sub(r'^(?:- |\d+\. )','',line)});i+=1;continue
 bits=[line];i+=1
 while i<len(lines) and lines[i].strip() and not lines[i].startswith(('#','|','- ')) and not re.match(r'^\d+\. ',lines[i]):bits.append(lines[i].strip());i+=1
 cur.append(para(' '.join(bits)))

effect_hooks={
'speed':'Temporary Attack or Sprint; do not grant an extra attack unless coded.',
'slowness':'Freeze or reduce Attack; choose one exact rule.',
'haste':'Mine payoff or tool Durability; separate from extra hero attacks.',
'mining_fatigue':'Delay or weaken tool use; a direct debuff to tools needs support.',
'strength':'A clear Attack buff to a mob.',
'instant_health':'Restore a fixed amount of Health to named targets.',
'instant_damage':'Deal a fixed amount of damage; specify hero eligibility.',
'jump_boost':'Sprint-themed mobility, not an implied bypass of Shield.',
'nausea':'Avoid randomizing player controls; use a bounded card effect instead.',
'regeneration':'A modest end-turn healing engine.',
'resistance':'Armor or Health; these have different behavior.',
'fire_resistance':'Narrow damage immunity would need damage-type rules.',
'water_breathing':'Aquatic support or a survival buff.',
'invisibility':'Stealth/untargetable needs additional targeting rules.',
'blindness':'Do not conceal essential UI; a precise Attack reduction is clearer.',
'night_vision':'Reveal/search is a future mechanic, not existing effect text.',
'hunger':'A resource tradeoff; alternate costs need explicit code.',
'weakness':'Negative Attack, removable by Cleanse.',
'poison':'Do not call Withered poison if timing and lethality differ.',
'wither':'The current Withered status is the direct analogue; heroes excluded.',
'health_boost':'Increase maximum and current Health through a buff.',
'absorption':'Temporary Health/shields need distinct tracking if they expire.',
'saturation':'Food/healing or XP recovery, with an explicit target/resource.',
'glowing':'Reveal or mark a target; extra marked-damage systems need code.',
'levitation':'Freeze-like lost attack opportunity, if that is the actual rule.',
'luck':'Change a fixed loot/catch pool rather than hidden probability boosts.',
'unluck':'A bounded downside; avoid opaque global RNG penalties.',
'slow_falling':'Protection from a single event is a proposed prevention mechanic.',
'conduit_power':'Aquatic aura from a vulnerable block.',
'dolphins_grace':'Schooling or temporary Aquatic Attack.',
'bad_omen':'A visible delayed challenge; objective counters need code.',
'hero_of_the_village':'Villager aura or generated trade reward.',
'darkness':'An explicit debuff, never a deliberately unreadable interface.',
'trial_omen':'A visible condition leading to a reward; future objective system.',
'raid_omen':'An authored Illager wave; avoid hidden automatic spawns.',
'wind_charged':'A return/transform response, or future positional mechanic.',
'weaving':'Web generation or a small defensive token.',
'oozing':'A finite Slime summon on an appropriate trigger.',
'infested':'Small Arthropod tokens, with board-space limits.',
'breath_of_the_nautilus':'Aquatic sustain or companion support; do not invent a new meter.'}
section('Vanilla status effects',[
 para('VANILLA INDEX: all '+str(len(REG['effects']))+' registered status effects. Category is extracted from the game. The last column is a proposed Craftbound translation, not a full description of the vanilla effect. Status effects are not all obtainable as brewed potions.'),
 table(['Effect / ID','Game category','Card inspiration (proposal)'],[[x['name']+'\n'+short(x['id']),x['category'].title(),effect_hooks.get(short(x['id']),'Choose one supported rule that expresses the theme.')] for x in REG['effects']])])
def potion_effect(x):
 if not x['effects']:return 'No status effect (base liquid/preparation).'
 parts=[]
 for e in x['effects']:
  t=e['ticks'];duration='instant' if short(e['effect']) in ['instant_health','instant_damage'] else (str(t)+' ticks / '+str(round(t/20,2))+' seconds')
  parts.append(title(e['effect'])+' '+str(e['level'])+'; '+duration)
 return '; '.join(parts)
section('Vanilla potion definitions',[para('VANILLA INDEX: all '+str(len(REG['potions']))+' registered potion definitions. Durations are exact base effect ticks with seconds at 20 ticks/second. Delivery by splash, lingering cloud or arrow can change effective application; this table does not claim all bottles, arrows and clouds have identical duration. Minecraft seconds are not Craftbound turns. Define card timing independently.'),table(['Potion ID','Registered contents'],[[short(x['id']),potion_effect(x)] for x in REG['potions']]),para('Potion delivery families: drinkable potion, splash potion, lingering potion and tipped arrow. Their base item IDs appear in the complete item index. One item ID can carry many potion components; those component combinations are not separate item registrations.')])
ench_hooks={
'sharpness':'Attack increase; Fighter.', 'smite':'Undead-specific combat payoff; new damage-type/conditional filtering may be needed.',
'bane_of_arthropods':'Arthropod-specific answer; keep narrow tech playable.', 'efficiency':'Mining payoff or Durability; Miner.',
'fortune':'Extra mining reward; Miner.', 'silk_touch':'Return a resource instead of consuming it; exact rule needs design.',
'looting':'ON_KILL generation; Fighter.', 'fire_aspect':'DAMAGE_DEALT effect attached to a tool; Fighter.',
'luck_of_the_sea':'Fixed catch-table reward; Fisherman.', 'lure':'More frequent finite catches; do not imply extra attacks.',
'unbreaking':'Tool Durability increase.', 'mending':'Resource-triggered repair; verify cost and event support.',
'thorns':'ATTACKED damage; explicit effect differs from retaliation.', 'flame':'Attack/damage trigger for a ranged tool.',
'power':'Ranged tool Attack; price safe trades carefully.', 'multishot':'Extra targets; generic splitting is not yet a single action.',
'piercing':'Do not bypass Shield without explicit targeting code.', 'loyalty':'Return-on-break would need that trigger.',
'channeling':'Lightning-themed damage; weather condition is proposed.', 'riptide':'Mobility theme; no hidden extra attacks.',
'density':'Heavy combat payoff; falling-height calculations are not a card rule.', 'breach':'Armor interaction requires a defined damage rule.',
'wind_burst':'Displacement/return theme; board adjacency is proposed.', 'lunge':'Forward pressure/Sprint theme; use a supported target rule.',
'infinity':'Unlimited resources are dangerous; prefer a finite generation effect.'}
section('Vanilla enchantments',[para('VANILLA INDEX with design suggestions. Max level is the vanilla definition, not a suggested Craftbound card power. The proposed translation does not add the mechanic automatically.'),table(['Name / ID','Vanilla max','Card interpretation (proposal)'],[[x['name']+'\n'+short(x['id']),str(x['data'].get('max_level','')),ench_hooks.get(short(x['id']),'Protection, mobility or utility theme. Use a supported Health/keyword effect, or clearly mark new rules as proposed.')] for x in REG['enchantments']])])
section('Vanilla entities and mobs',[para('VANILLA INDEX: all '+str(len(REG['entities']))+' entity types, including all registered mobs plus projectiles, vehicles, displays and technical entities. The category is Minecraft\'s internal spawn category, not its attitude and not Craftbound\'s type. For example, NPCs and utility creatures may appear under miscellaneous. Unused/command-only types are intentionally retained.'),table(['Entity / ID','Registry category'],[[x['name']+'\n'+short(x['id']),x['category'].replace('_',' ')] for x in sorted(REG['entities'],key=lambda x:x['name'].lower())]),para('Common design families: passive farm animals; tameable companions; aquatic life; Villagers and traders; Illagers; Undead; Nether creatures; End creatures; Arthropods; Golems; Slimes; bosses. Appearance variants and baby/adult states often share an entity ID. Do not interpret the category column as a complete behavioral taxonomy.')])

def family(id):
 s=short(id)
 if s.endswith('_spawn_egg'):return 'Spawn eggs'
 if any(s.endswith('_'+a) or s==a for a in ['sword','pickaxe','axe','hoe','shovel','spear','helmet','chestplate','leggings','boots','horse_armor']):return 'Tools, weapons and equipment'
 if any(a in s for a in ['potion','arrow','bow','trident','mace','shield','elytra','fishing_rod','shears','brush','armor','saddle','lead']):return 'Equipment, projectiles and potions'
 if s.endswith('_ore') or any(a in s for a in ['ingot','raw_','nugget','diamond','emerald','lapis','quartz','coal','netherite','amethyst']):return 'Ores, minerals and materials'
 if any(a in s for a in ['redstone','piston','dispenser','dropper','hopper','observer','repeater','comparator','lever','button','pressure_plate','rail','tnt','target','crafter']):return 'Redstone, mechanisms and rails'
 if s.endswith(('_boat','_raft')) or 'minecart' in s:return 'Transport'
 if any(a in s for a in ['porkchop','beef','chicken','mutton','rabbit','cod','salmon','bread','cookie','cake','stew','soup','apple','carrot','potato','melon','beetroot','berries','wheat','egg','honey','milk','sugar','pumpkin_pie','tropical_fish','pufferfish','rotten_flesh','kelp']):return 'Food, crops and ingredients'
 if any(a in s for a in ['sapling','leaves','flower','tulip','orchid','rose','lily','grass','fern','vine','moss','mushroom','fungus','roots','azalea','cactus','bamboo','coral','seagrass','cane','bush','petals','dandelion','poppy','allium','torchflower','pitcher','spore','lichen','dripleaf','propagule']):return 'Plants, fungi and aquatic growth'
 if any(a in s for a in ['oak','spruce','birch','jungle','acacia','cherry','mangrove','poplar','crimson','warped','log','wood','planks']):return 'Wood and building families'
 if any(a in s for a in ['wool','carpet','terracotta','concrete','glass','candle','banner','bed','dye']):return 'Color, decoration and furnishings'
 if any(a in s for a in ['stone','brick','deepslate','tuff','granite','diorite','andesite','basalt','prismarine','sand','gravel','dirt','mud','clay','snow','ice','obsidian','netherrack','nylium','purpur','resin','copper','sulfur','calcite']):return 'Terrain, masonry and construction'
 return 'Utility, special and other entries'
def catalogue(title_,key):
 vals=REG[key]
 groups=collections.defaultdict(list)
 for x in vals:groups[family(x['id'])].append(x)
 b=[para('VANILLA INDEX: all '+str(len(vals))+' registered '+key+'. Every identifier in this index has the minecraft: namespace; it is omitted below to keep entries readable. Categories are editorial browsing groups, not authoritative tags. All variants with separate registry IDs are listed. The same object can appear in both item and block indexes.')]
 for g,items in sorted(groups.items()):
  b.append({'kind':'h2','text':g+' ('+str(len(items))+')'})
  b.append({'kind':'catalogue','entries':[{'name':x['name'],'id':short(x['id'])} for x in sorted(items,key=lambda x:(x['name'].lower(),x['id']))]})
 section(title_,b)
catalogue('Complete vanilla item index','items')
catalogue('Complete vanilla block index','blocks')
section('Vanilla biomes and structures',[para('VANILLA DATA INDEX. Biomes are proposed as cosmetic board themes and NPC deck inspiration for Foundation, not additional combat rules. Structures can inspire authored encounters or rewards. IDs are exact data-resource identifiers; visual variants and generated arrangements are not separate entries.'),{'kind':'h2','text':'Biomes ('+str(len(REG['biomes']))+')'},{'kind':'catalogue','entries':[{'name':x['name'],'id':short(x['id'])} for x in REG['biomes']]},{'kind':'h2','text':'Structures ('+str(len(REG['structures']))+')'},{'kind':'catalogue','entries':[{'name':title(x['id']),'id':short(x['id'])} for x in REG['structures']]}])
b=[para('CURRENT SNAPSHOT of the bundled catalogue, not your custom development file: '+str(len(cards))+' definitions, '+str(sum(not c['token'] for c in cards))+' collectibles. Existing IDs/order are unchanged. This is a source-backed inventory of current text, not an endorsement of the old Foundation balance. Custom descriptions remain authored text and can differ in style from newly generated descriptions.')]
for cls in ['NEUTRAL','FIGHTER','BUILDER','FARMER','FISHERMAN','ALCHEMIST','MINER','CHEF','TAMER','REDSTONER']:
 b.append({'kind':'h2','text':title(cls)})
 rows=[]
 for c in cards:
  if c['class']!=cls:continue
  kind='Block' if 'BLOCK'in c['keywords'] else title(c['kind'])
  stats='' if c['kind'] in ['ITEM','ENCHANTMENT'] else ' | '+str(c['attack'])+'/'+str(c['health'])+(' Durability' if c['kind']=='TOOL' else '')
  rows.append([c['name']+'\n'+c['id'],str(c['cost'])+' XP | '+title(c['rarity'])+'\n'+kind+(' | '+title(c['type']) if c['type'] else '')+stats+(' | Token' if c['token'] else ''),c['generatedDescription'] or 'No ability.'])
 b.append(table(['Card / stable ID','Current attributes','Current rules text'],rows))
section('Current Craftbound card snapshot',b)

def escaped(s):return html.escape(str(s),quote=True)
def prose(s):
 t=escaped(s).replace('\n','<br/>')
 return re.sub(r'(https://[^\s<]+)',r'<a href="\1">\1</a>',t)

# A self-contained browser reference: no CDN, no tracking, no catalogue writes.
css='''*{box-sizing:border-box}body{margin:0;background:#f4f1e9;color:#24372f;font:16px/1.6 system-ui,Segoe UI,sans-serif}a{color:#245b53}header{position:sticky;top:0;z-index:5;background:#203c33;color:#fff;padding:14px 24px;box-shadow:0 2px 6px #0003}header strong{letter-spacing:.08em;font-size:13px}header p{margin:0;font-size:12px;color:#c8d9cf}.tools{display:flex;gap:12px;align-items:center;margin-top:8px}input{font:inherit;padding:9px 12px;border:1px solid #bdc9be;border-radius:6px;flex:1;min-width:100px}button{font:inherit;padding:8px 13px;cursor:pointer;border:1px solid #bdc9be;border-radius:6px;background:#fff;color:#203c33}.layout{display:grid;grid-template-columns:245px minmax(0,1fr);max-width:1500px;margin:auto}nav{position:sticky;top:131px;height:calc(100vh - 145px);overflow:auto;padding:22px 16px;font-size:13px}nav a{display:block;padding:5px 9px;border-left:2px solid transparent;text-decoration:none}nav a[aria-current="page"],nav a:hover{background:#e3e7dd;border-color:#be8d43}main{padding:32px;min-width:0}section{scroll-margin-top:150px;margin-bottom:44px;background:#fffef9;padding:28px;border:1px solid #d8ded3;border-radius:9px}section{content-visibility:auto;contain-intrinsic-size:auto 1000px}.catalogue{content-visibility:auto;contain-intrinsic-size:auto 600px}h1{font-size:28px;line-height:1.2;margin:0 0 20px;color:#203c33}h2{font-size:19px;line-height:1.3;margin:28px 0 12px}p{margin:12px 0}li{margin:8px 0}table{border-collapse:collapse;width:100%;font-size:14px;margin:16px 0;table-layout:fixed}th{background:#e1e9df;text-align:left}th,td{padding:11px;border:1px solid #cbd5c8;vertical-align:top;overflow-wrap:anywhere}tr:nth-child(even){background:#f4f6ef}.catalogue{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.catalogue .entry{padding:9px 10px;border:1px solid #dce1d6;background:#f7f8f2;border-radius:5px;font-size:13px;line-height:1.35;overflow-wrap:anywhere}.entry code{display:block;font:11px/1.4 Consolas,monospace;color:#647065;margin-top:4px}#resultCount{font-size:12px;white-space:nowrap}.tag{background:#eadbbd;padding:3px 8px;font-size:12px;border-radius:5px}.empty{padding:30px;font-size:20px} [hidden]{display:none!important}@media(max-width:900px){.layout{grid-template-columns:180px minmax(0,1fr)}main{padding:15px}section{padding:20px}.catalogue{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:620px){.layout{display:block}nav{position:static;height:auto;max-height:170px;display:flex;flex-wrap:wrap;padding:8px}nav a{padding:4px 8px}header{padding:12px}h1{font-size:24px}.catalogue{grid-template-columns:1fr}table{font-size:12px}th,td{padding:6px}#resultCount{white-space:normal}}@media print{section,.catalogue{content-visibility:visible;contain-intrinsic-size:none}main section[hidden],main .entry[hidden],main table[hidden],main .catalogue[hidden],main h2[hidden]{display:revert!important}header,nav,.tools{display:none}.layout{display:block}main{padding:0}section{break-before:page;border:0;padding:0}a{color:inherit}body{font-size:10pt}table{font-size:8pt}}'''
css+='''html,body{height:100%;overflow:hidden}body{display:grid;grid-template-rows:auto minmax(0,1fr) auto}header{position:static;box-shadow:none}.layout{width:100%;height:100%;margin:0 auto;min-height:0;overflow:hidden;grid-template-rows:minmax(0,1fr)}nav{position:static;top:auto;height:auto;min-height:0;overscroll-behavior:contain}main{overflow:auto;min-height:0;overscroll-behavior:contain;scrollbar-gutter:stable;contain:layout paint}section,.catalogue{content-visibility:visible;contain-intrinsic-size:none}.pager{display:flex;align-items:center;justify-content:center;gap:20px;padding:8px;background:#e1e9df;border-top:1px solid #bdc9be}.pager button:disabled{opacity:.45;cursor:default}@media(max-width:620px){.layout{display:grid;grid-template-columns:1fr;grid-template-rows:auto minmax(0,1fr)}nav{max-height:100px;overflow:auto}.pager{gap:10px}}@media print{html,body{height:auto;overflow:visible;display:block}.layout{overflow:visible}main{overflow:visible;contain:none}.pager{display:none}}'''
htmlparts=['<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Craftbound Design Reference</title><style>'+css+'</style></head><body><header><strong>CRAFTBOUND · DESIGN REFERENCE</strong><p>Foundation rebuild · Minecraft 26.3 · Source snapshot 2 November 2026</p><div class="tools"><input id="search" type="search" aria-label="Search the design reference" placeholder="Search every chapter: keywords, classes, mobs, blocks, items…"><button id="clear">Clear</button><span id="resultCount"></span></div></header><div class="layout"><nav aria-label="Reference chapters">']
for s in sections:htmlparts.append('<a href="#'+s['id']+'">'+escaped(s['title'])+'</a>')
htmlparts.append('</nav><main><p class="empty" id="empty" hidden>No matches. Try a mob name, class, keyword or registry ID.</p>')
for s in sections:
 htmlparts.append('<section id="'+s['id']+'"><h1>'+escaped(s['title'])+'</h1>')
 for b in s['blocks']:
  if b['kind']=='p':htmlparts.append('<p>'+prose(b['text'])+'</p>')
  elif b['kind']=='h2':htmlparts.append('<h2>'+escaped(b['text'])+'</h2>')
  elif b['kind']=='li':htmlparts.append('<ul><li>'+prose(b['text'])+'</li></ul>')
  elif b['kind']=='table':htmlparts.append('<table><thead><tr>'+''.join('<th>'+escaped(x)+'</th>'for x in b['headers'])+'</tr></thead><tbody>'+''.join('<tr class="entry">'+''.join('<td>'+prose(x)+'</td>'for x in row)+'</tr>'for row in b['rows'])+'</tbody></table>')
  elif b['kind']=='catalogue':htmlparts.append('<div class="catalogue">'+''.join('<article class="entry"><strong>'+escaped(x['name'])+'</strong><code>'+escaped(x['id'])+'</code></article>'for x in b['entries'])+'</div>')
 htmlparts.append('</section>')
htmlparts.append('''</main></div><script>
const search=document.getElementById('search'),main=document.querySelector('main');
const sections=[...main.querySelectorAll('section')];
// Keep the complete reference off the live DOM. Only one small, stable page is laid out.
const index=sections.map(section=>({section,id:section.id,title:section.querySelector('h1').textContent,
 text:section.textContent.toLowerCase(),blocks:[...section.children].filter(e=>e.tagName!=='H1').map(element=>({
 element,text:element.textContent.toLowerCase(),rows:[...element.querySelectorAll('.entry')].map(e=>({element:e,text:e.textContent.toLowerCase()}))}))}));
sections.forEach(s=>s.remove());
const empty=document.getElementById('empty'),content=document.createElement('div'),pager=document.createElement('div');
pager.className='pager';pager.innerHTML='<button id="previousPage">← Previous</button><span id="pageNumber" aria-live="polite"></span><button id="nextPage">Next →</button>';
main.append(content);document.querySelector('.layout').after(pager);
let pages=[],page=0,timer;
function render(){
 content.replaceChildren();empty.hidden=pages.length>0;
 if(pages.length){const p=pages[page],s=document.createElement('section'),h=document.createElement('h1');s.dataset.chapter=p.id;h.textContent=p.title;s.append(h);p.blocks.forEach(b=>s.append(b.cloneNode(true)));content.append(s)}
 document.getElementById('previousPage').disabled=page===0;document.getElementById('nextPage').disabled=page>=pages.length-1;
 document.getElementById('pageNumber').textContent=pages.length?'Page '+(page+1)+' of '+pages.length:'No results';
 main.scrollTop=0;
}
function filter(){
 const terms=search.value.toLowerCase().trim().replaceAll('minecraft:','').split(/\\s+/).filter(Boolean),match=text=>terms.every(t=>text.includes(t));
 const selected=index.some(i=>i.id===location.hash.slice(1))?location.hash.slice(1):index[0].id;
 pages=[];page=0;let chapters=0;
 for(const item of index){
  if(terms.length?!match(item.text):item.id!==selected)continue;
  chapters++;let current=null,weight=0,heading=null;
  const add=(block,cost=4)=>{if(!current||weight+cost>72){current={id:item.id,title:item.title,blocks:[]};pages.push(current);weight=0;if(heading)current.blocks.push(heading)}current.blocks.push(block);weight+=cost};
  const whole=!terms.length||match(item.title.toLowerCase());
  for(const b of item.blocks){
   if(b.element.tagName==='H2'){heading=b.element;continue}
   if(b.rows.length){
    const rows=b.rows.filter(r=>whole||heading&&match(heading.textContent.toLowerCase())||match(r.text));
    for(let i=0;i<rows.length;i+=48){const group=b.element.cloneNode(false);if(group.tagName==='TABLE'){group.append(b.element.querySelector('thead').cloneNode(true));const body=document.createElement('tbody');rows.slice(i,i+48).forEach(r=>body.append(r.element.cloneNode(true)));group.append(body)}else rows.slice(i,i+48).forEach(r=>group.append(r.element.cloneNode(true)));if(heading&&current&&current.blocks.at(-1)!==heading)add(heading,1);add(group,Math.min(48,rows.length-i))}
   }else if(whole||match(b.text)){if(heading&&current&&current.blocks.at(-1)!==heading)add(heading,1);add(b.element,Math.max(4,Math.ceil(b.text.length/160)))}
  }
 }
 document.getElementById('resultCount').textContent=terms.length?chapters+' matching chapters':'Browse chapters';
 document.querySelectorAll('nav a').forEach(a=>{if(!terms.length&&a.hash==='#'+selected)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current')});render();
}
search.addEventListener('input',()=>{clearTimeout(timer);timer=setTimeout(filter,120)});
document.getElementById('clear').onclick=()=>{clearTimeout(timer);search.value='';filter();search.focus()};
document.getElementById('previousPage').onclick=()=>{if(page>0){page--;render()}};
document.getElementById('nextPage').onclick=()=>{if(page+1<pages.length){page++;render()}};
document.querySelectorAll('nav a').forEach(a=>a.onclick=e=>{e.preventDefault();clearTimeout(timer);search.value='';history.replaceState(null,'',a.hash);filter()});
window.addEventListener('hashchange',()=>{clearTimeout(timer);search.value='';filter()});
window.addEventListener('beforeprint',()=>{content.replaceChildren(...sections.map(s=>s.cloneNode(true)))});
window.addEventListener('afterprint',render);filter();
</script></body></html>''')
(PROJECT/'tools/card-editor/reference.html').write_text(''.join(htmlparts),encoding='utf-8')
(OUT/'Minecraft-26.3-reference-data.json').write_text(json.dumps(REG,ensure_ascii=False,indent=2),encoding='utf-8')

if '--html-only' in __import__('sys').argv: raise SystemExit(0)

# Printable guide. All text remains selectable; catalogue cells wrap long IDs.
styles={
'body':ParagraphStyle('Body',fontName='Helvetica',fontSize=9.5,leading=13.5,textColor=HexColor('#24372f'),spaceAfter=8),
'h1':ParagraphStyle('Heading1',fontName='Helvetica-Bold',fontSize=22,leading=27,textColor=HexColor('#203c33'),spaceAfter=18,keepWithNext=True),
'h2':ParagraphStyle('Heading2',fontName='Helvetica-Bold',fontSize=12.5,leading=17,textColor=HexColor('#42624d'),spaceBefore=14,spaceAfter=8,keepWithNext=False),
'cell':ParagraphStyle('Cell',fontName='Helvetica',fontSize=8,leading=10.5,textColor=HexColor('#24372f'),wordWrap='CJK'),
'small':ParagraphStyle('Small',fontName='Helvetica',fontSize=7.2,leading=9,textColor=HexColor('#647065'),wordWrap='CJK'),
'bullet':ParagraphStyle('Bullet',fontName='Helvetica',fontSize=9.5,leading=13.5,leftIndent=12,firstLineIndent=-8,spaceAfter=7),
'cover':ParagraphStyle('Cover',fontName='Helvetica-Bold',fontSize=39,leading=44,textColor=HexColor('#203c33'),spaceAfter=24),
'lead':ParagraphStyle('Lead',fontName='Helvetica',fontSize=14,leading=20,textColor=HexColor('#42624d'),spaceAfter=18)}
def clean(t):return str(t).replace('’',"'").replace('‘',"'").replace('—',' - ').replace('–','-').replace('…','...').replace('×','x').replace('•','/')
def pp(t,style='body'):return Paragraph(prose(clean(t)),styles[style])
W=A4[0]-92
story=[Spacer(1,64),pp('CRAFTBOUND','h2'),pp('Foundation\nDesign Reference','cover'),pp('Clear wording. Distinct classes.\nA complete source of Minecraft card inspiration.','lead'),Spacer(1,30),pp('WORKSHOP EDITION  /  2 NOVEMBER 2026','h2'),pp('Minecraft Java 26.3 | Fabric | Current rules and proposed designs clearly separated.'),Spacer(1,24),pp('1,658 items  •  1,286 blocks  •  161 entity types\n40 effects  •  46 potion definitions  •  8 playable classes','lead'),pp('Use the searchable Design Reference tab in Card Workshop while editing. Keep this PDF for reading, printing and planning.'),PageBreak(),pp('Contents','h1')]
toc=TableOfContents();toc.levelStyles=[ParagraphStyle('TOC',fontName='Helvetica',fontSize=9.5,leading=14,spaceBefore=0,spaceAfter=0,leftIndent=0,firstLineIndent=0,textColor=HexColor('#24372f'))];story.extend([toc,PageBreak()])
for si,s in enumerate(sections):
 if si:story.append(PageBreak())
 h=pp(s['title'],'h1');h._refid=s['id'];story.append(h)
 for b in s['blocks']:
  k=b['kind']
  if k=='p':story.append(pp(b['text']))
  elif k=='h2':story.extend([CondPageBreak(95),pp(b['text'],'h2')])
  elif k=='li':story.append(pp('- '+b['text'],'bullet'))
  elif k=='table':
   n=len(b['headers']);fractions={2:[.38,.62],3:[.25,.31,.44],4:[.18,.29,.27,.26]}.get(n,[1/n]*n)
   rows=[[pp('<invalid>')]] if False else [[pp(x,'cell')for x in b['headers']]]+[[pp(x,'cell') for x in row]for row in b['rows']]
   t=Table(rows,colWidths=[W*x for x in fractions],repeatRows=1,hAlign='LEFT')
   t.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),HexColor('#dce6d9')),('ROWBACKGROUNDS',(0,1),(-1,-1),[HexColor('#f5f6ee'),white]),('VALIGN',(0,0),(-1,-1),'TOP'),('GRID',(0,0),(-1,-1),.35,HexColor('#cbd4c5')),('LEFTPADDING',(0,0),(-1,-1),7),('RIGHTPADDING',(0,0),(-1,-1),7),('TOPPADDING',(0,0),(-1,-1),7),('BOTTOMPADDING',(0,0),(-1,-1),7)]));story.extend([t,Spacer(1,10)])
  elif k=='catalogue':
   entries=b['entries'];rows=[]
   for i in range(0,len(entries),3):
    row=[[pp(x['name'],'cell'),pp(x['id'],'small')]for x in entries[i:i+3]]
    row+=['']*(3-len(row));rows.append(row)
   if rows:
    t=Table(rows,colWidths=[W/3]*3,hAlign='LEFT');t.setStyle(TableStyle([('VALIGN',(0,0),(-1,-1),'TOP'),('GRID',(0,0),(-1,-1),.25,HexColor('#d9dfd2')),('ROWBACKGROUNDS',(0,0),(-1,-1),[HexColor('#f5f6ee'),white]),('LEFTPADDING',(0,0),(-1,-1),7),('RIGHTPADDING',(0,0),(-1,-1),7),('TOPPADDING',(0,0),(-1,-1),5),('BOTTOMPADDING',(0,0),(-1,-1),5)]));story.append(t)
def page(canvas,doc):
 canvas.saveState();canvas.setStrokeColor(HexColor('#bdcbb9'));canvas.setLineWidth(.5);canvas.line(46,42,A4[0]-46,42)
 canvas.setFillColor(HexColor('#647065'));canvas.setFont('Helvetica',7.5);canvas.drawString(46,29,'CRAFTBOUND  /  FOUNDATION DESIGN REFERENCE  /  26.3');canvas.drawRightString(A4[0]-46,29,str(doc.page))
 if doc.page>1:canvas.drawString(46,A4[1]-31,'CURRENT RULES + PROPOSALS  |  2 NOVEMBER 2026')
 canvas.restoreState()
class ReferenceDoc(SimpleDocTemplate):
 def afterFlowable(self,f):
  if hasattr(f,'_refid'):
   self.canv.bookmarkPage(f._refid);self.canv.addOutlineEntry(f.getPlainText(),f._refid,0,False);self.notify('TOCEntry',(0,f.getPlainText(),self.page,f._refid))
doc=ReferenceDoc(str(OUT/'Craftbound-Foundation-Reference.pdf'),pagesize=A4,rightMargin=46,leftMargin=46,topMargin=52,bottomMargin=53,title='Craftbound Foundation Design Reference',author='Craftbound design workspace')
doc.multiBuild(story,onFirstPage=page,onLaterPages=page)
manifest={'version':'Minecraft Java 26.3','date':'2026-11-02','counts':{k:len(v)for k,v in REG.items()},'chapters':len(sections),'cards':len(cards),'source_sha256':hashlib.sha256((BASE/'Minecraft-26.3-reference-data.json').read_bytes()).hexdigest()}
(OUT/'reference-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print(json.dumps(manifest,indent=2))
print(OUT)
