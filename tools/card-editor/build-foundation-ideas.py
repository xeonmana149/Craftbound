"""Regenerate the standalone Workshop idea book from the editable design chapters."""
from pathlib import Path
from html import escape
import re

ROOT = Path(__file__).resolve().parents[2]
CHAPTERS = ROOT / 'docs/design/foundation-ideas'
OUT = Path(__file__).with_name('foundation-ideas.html')
CLASS = {
    'fighter': ('#ea897a', 'Illager raids', 'Spend the last swing of a tool or keep it for an enchantment. A living Illager is often worth more than two early damage.'),
    'builder': ('#89b5ef', 'Golems and fortifications', 'Commit a block slot to long-term value, then decide which Golem can afford to stand in front of it.'),
    'farmer': ('#a2c972', 'Crops and late turns', 'Plant for future XP or put down a body now. A crop matters most when you have something worth ramping into.'),
    'fisherman': ('#6ccfc8', 'Aquatic schools', 'The catch changes your turn: protect a Cod, trade a Salmon, or leave a Pufferfish where trading hurts.'),
    'alchemist': ('#bd92ed', 'Undead and splashes', 'Wound a crowd, then choose your last hit. Your own expendable Undead can make a symmetrical splash worthwhile.'),
    'miner': ('#e5be61', 'Ore and excavation', 'Mine a vein to collect its Drop and trigger your crew, or let it stand so your next survey counts it.'),
    'chef': ('#f3ab76', 'Villagers and healing', 'Trade before serving the meal. A surviving, wounded Villager gives healing a job on a turn when face healing would be wasted.'),
    'tamer': ('#e49db7', 'Tameables and packs', 'Decide whether one more Pup is worth the board slot. A small pack with room to grow can beat a full row.'),
    'redstoner': ('#f28760', 'Blocks and timing', 'Choose which contraption gets the one hero-power activation. Building a second machine creates a decision, not a second button press.')
}

def plain(s):
    return s.replace('**','').replace('*','').replace(chr(96),'').strip()

def rich(s):
    s = escape(s.strip())
    s = re.sub(r'\*\*(.+?)\*\*',r'<strong>\1</strong>',s)
    s = re.sub(r'(?<!\w)\*([^*]+)\*(?!\w)',r'<em>\1</em>',s)
    return re.sub(r'\x60([^\x60]+)\x60',r'<code>\1</code>',s)

def prose(lines):
    body=[]; buf=[]; in_list=False
    def flush():
        if buf:
            body.append('<p>'+rich(' '.join(buf))+'</p>'); buf.clear()
    for line in [*lines, '']:
        s=line.strip()
        if not s or s.startswith('#') or s.startswith('- '): flush()
        if in_list and not s.startswith('- '): body.append('</ul>'); in_list=False
        if not s or s.startswith('# '): continue
        if s.startswith('##'):
            body.append('<h3>'+rich(s.lstrip('#').strip())+'</h3>')
        elif s.startswith('- '):
            if not in_list: body.append('<ul>'); in_list=True
            body.append('<li>'+rich(s[2:])+'</li>')
        else: buf.append(s)
    return ''.join(body)

def parts(line): return [c.strip() for c in line.strip().strip('|').split('|')]
def support(text):
    first=plain(text).split(' ')[0].strip('·:—.').lower()
    if first in ('p','new','prototype','proposed','engine'): return 'Prototype'
    if first in ('pair','linked','link','companion'): return 'Linked'
    if first in ('w','ready','workshop','existing','supported','current'): return 'Workshop'
    raise ValueError('Unknown support label: '+text)

def tag_html(tag,status):
    parts=re.split(r'\s*(?:·|:|—)\s*', plain(tag), maxsplit=1)
    label={'Workshop':'Workshop','Linked':'Linked card','Prototype':'New code'}[status]
    return '<strong>'+label+'</strong>'+(' · '+rich(parts[1]) if len(parts)==2 else '')
    
def make():
    topics=[];chapters=[]; seen=set();all_names=[]
    for slug,(colour,theme,choice) in CLASS.items():
        title=slug.title()
        lines=(CHAPTERS/(slug+'.md')).read_text(encoding='utf-8-sig').splitlines()
        ids=[i for i,l in enumerate(lines) if l.lstrip().startswith('|') and re.fullmatch(r'\d+',parts(l)[0])]
        if len(ids)!=40: raise ValueError((slug,'rows:',len(ids)))
        first=ids[0];last=ids[-1]
        pre=prose([l for l in lines[:first] if not l.startswith('|')])
        post=prose([l for l in lines[last+1:] if not l.startswith('|')])
        rows=[];nleg=0
        for n,idx in enumerate(ids,1):
            cells=parts(lines[idx]); assert len(cells)==8,(slug,n,cells)
            number,name,xp,rarity,kind,stats,txt,tag=cells
            assert int(number)==n and 0<=int(xp)<=10 and rarity in ('Common','Rare','Epic','Legendary'), cells
            nleg+=rarity=='Legendary'
            key=plain(name).casefold(); assert key not in seen,('Duplicate card name',name)
            seen.add(key); all_names.append(key); status=support(tag)
            rows.append(f'<tr data-xp="{int(xp)}" data-rarity="{rarity}" data-support="{status}"><td class="num">{int(number):02}</td><td class="name">{rich(plain(name))}</td><td class="xp">{int(xp)}</td><td class="r-{rarity.lower()}">{rarity}</td><td>{rich(kind)}</td><td class="stats">{rich(stats)}</td><td class="rules">{rich(txt)}</td><td class="support">{tag_html(tag,status)}</td></tr>')
        assert nleg==2,(slug,'legendaries:',nleg)
        topics.append(f'<button class="topic" style="--class:{colour}" data-jump="{slug}"><b>{title}</b><em>{escape(theme)}</em><span>{escape(choice)}</span></button>')
        chapters.append(f'<article data-class="{slug}" id="{slug}" style="--class:{colour}"><div class="chapterhead"><small>FIELD NOTES &nbsp; / &nbsp; {escape(theme.upper())}</small><h2>{title}</h2><a class="skip" href="#plan-{slug}">Jump to deck notes ↓</a></div><details class="notes" open><summary>Hero power &amp; class identity</summary>{pre}</details><div class="scroll"><table><thead><tr><th>#</th><th>Card</th><th>XP</th><th>Rarity</th><th>Kind / family</th><th>Stats</th><th>Card text</th><th>Workshop support</th></tr></thead><tbody>{"".join(rows)}</tbody></table></div><details id="plan-{slug}" class="notes strategy" open><summary>Playing the deck</summary>{post or "<p>See the class notes above.</p>"}</details></article>')
    page=TEMPLATE.replace('@@TOPICS@@',''.join(topics)).replace('@@ARTICLES@@',''.join(chapters)).replace('@@OPTIONS@@',''.join(f'<option value="{s}">{s.title()}</option>' for s in CLASS))
    OUT.write_text(page, encoding='utf-8')
    print(f'{OUT.name}: {len(seen)} distinct names, {len(CLASS)} classes, 18 legendaries.')

TEMPLATE = r'''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Foundation Field Notes — Craftbound</title>
<style>
:root{color-scheme:dark;--bg:#151916;--panel:#212821;--ink:#f4eddb;--soft:#bbc2b4;--line:#3c463c;--gold:#f0ca82}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.58 system-ui,sans-serif}main,header>div{max-width:1460px;margin:auto;padding:0 clamp(16px,3.8vw,60px)}header{border-top:5px solid #a8bd75;background:#1b211c;border-bottom:1px solid var(--line);padding:2.25rem 0 1.5rem}small,.kicker{letter-spacing:.15em;font-size:.73rem;color:var(--gold);font-weight:700;text-transform:uppercase}h1,h2,h3{line-height:1.15;margin:.35em 0;font-family:Georgia,serif}h1{font-size:clamp(2.4rem,6vw,4.5rem);font-weight:500}h2{font-size:2.1rem}h3{font-size:1.45rem}header p{margin:.6rem 0;max-width:75ch;color:var(--soft)}main{padding-bottom:4rem}button,select,input{font:inherit;color:var(--ink);background:#242e26;border:1px solid #516052;border-radius:.4em;padding:.4em .65em}button,summary,select{cursor:pointer}button:focus-visible,input:focus-visible,select:focus-visible,summary:focus-visible{outline:2px solid var(--gold);outline-offset:2px}.filters{display:flex;align-items:end;gap:8px;flex-wrap:wrap;padding:1rem 0}.filters label{display:grid;gap:2px;font-size:.78rem;color:var(--soft)}.filters :is(input,select,button){font-size:.96rem}.search{flex:1;min-width:170px}.filters input{width:100%}#count{margin:.25rem 0 1.25rem;color:var(--gold);font-variant-numeric:tabular-nums}.topics{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,250px),1fr));gap:10px}.topic{padding:12px 16px;border-color:var(--line);border-left:4px solid var(--class);text-align:left;line-height:1.4;background:var(--panel)}.topic b,.topic em,.topic span{display:block}.topic b{color:var(--class);font-size:1.2rem}.topic em{font-style:normal;font-size:.8rem;color:var(--ink);margin-bottom:.45rem}.topic span{font-size:.88rem;color:var(--soft)}.topic:hover{background:#2b342d}.help{margin:1.2rem 0 1.5rem;color:var(--soft);max-width:90ch}.help strong{color:var(--gold)}article{border:1px solid var(--line);border-radius:8px;overflow:hidden;margin:18px 0 32px;background:#191f1b}.chapterhead{padding:1.3rem 1.4rem .7rem;border-top:4px solid var(--class)}.chapterhead small{color:var(--class)}.skip{color:var(--class);font-size:.85rem;text-underline-offset:3px}.notes{padding:0 1.4rem 1rem}.notes summary{color:var(--class);font-weight:650}.notes p,.notes li{max-width:100ch;font-size:.96rem}.strategy{padding-top:1rem}.scroll{overflow-x:auto}table{width:100%;border-collapse:collapse;line-height:1.4;font-size:.91rem}td,th{padding:.65em .6em;border-bottom:1px solid #354037;text-align:left;vertical-align:top}th{background:#29332b;color:#c6d3c1;font-size:.76rem;white-space:nowrap}tr:nth-child(even){background:#202720}.num{color:#9ca79e;font-size:.78rem}.name{font-weight:650;min-width:105px}.rules{min-width:220px}.stats,.xp{white-space:nowrap;font-variant-numeric:tabular-nums}.support{font-size:.78rem;color:var(--soft);max-width:210px}.r-rare{color:#a4c8fc}.r-epic{color:#deacfa}.r-legendary{color:var(--gold)}[hidden]{display:none!important}footer{padding:1rem 0;color:var(--soft)}@media(max-width:650px){header{padding:1rem 0}.notes,.chapterhead{padding-left:.8rem;padding-right:.8rem}td,th{padding:.55em .5em}}@media print{.filters,.topics,#count,footer{display:none}body,article{background:white;color:black}article{break-before:page;border:none}.scroll{overflow:visible}td,th{font-size:8pt;color:black!important;background:white}tr{break-inside:avoid}}
</style></head><body><header><div><div class="kicker">Craftbound &nbsp; / &nbsp; Foundation design notebook</div><h1>Foundation Field Notes</h1><p>360 candidates for nine classes. Each chapter starts with a decision you want the player to face. The stats are starting points for testing; none of these ideas has been added to the game.</p></div></header>
<main><div class="filters" aria-label="Find ideas"><label>Class<select id="cls"><option value="">All classes</option>@@OPTIONS@@</select></label><label class="search">Search the cards<input id="q" type="search" placeholder="Try Ore, Sprint, Villager or a card name"></label><label>Rarity<select id="rar"><option value="">All rarities</option><option>Common</option><option>Rare</option><option>Epic</option><option>Legendary</option></select></label><label>Design support<select id="sup"><option value="">Any support</option><option value="author">Existing Workshop rules</option><option value="Prototype">Needs new code</option></select></label><button id="reset">Reset</button></div>
<p id="count" role="status"></p><div id="intro"><div class="topics">@@TOPICS@@</div><div class="help"><strong>How to use this pool.</strong> Pick one early play, one payoff, and one way an opponent can interrupt them. Test that interaction with familiar Neutral cards before adding a second package. “Existing Workshop rules” can still require another proposed card, token or pool to be authored first. A proposed keyword is a design shorthand until the engine supports its exact timing.</div><details class="help"><summary><strong>Nine mechanics to test before printing a new keyword</strong></summary><p>Use a name only if it shortens several cards. These are design suggestions, not nine new rules already in the mod.</p><ul>
<li><strong>Fighter · Breakthrough.</strong> Shorthand for the existing “after this attacks, destroys a mob or block, and survives” trigger. A defensive kill does not count.</li>
<li><strong>Builder · Blueprint.</strong> Pick one of three different blocks from your deck and draw it. Start on Blueprint Chest; face-up choices matter when there are only three block slots.</li>
<li><strong>Farmer · Mature.</strong> A Crop completes its own growth effect. Destroying it early cuts the harvest short. A new shared event would let Seed Vault recognise a successful harvest.</li>
<li><strong>Fisherman · Haul.</strong> The first time each turn you add a card to your hand without drawing it. Catching a fish directly onto the board is not a Haul.</li>
<li><strong>Alchemist · Dose.</strong> After your second Action resolves this turn. A hero power or a third potion doesn’t trigger it again.</li>
<li><strong>Miner · Vein.</strong> An extra payoff only when you deliberately mine this Ore. Its normal Drop still happens when an opponent destroys it.</li>
<li><strong>Chef · Well Fed.</strong> Already supported: healing beyond full Health raises maximum Health. Test it alongside a payoff for ordinary healing so healing a wounded mob stays worthwhile.</li>
<li><strong>Tamer · Tame.</strong> Take control of the specified enemy mob if there’s a free slot. Test exhausted arrivals that do not repeat Spawn, and give rare narrow capture effects a fallback.</li>
<li><strong>Redstoner · Relay.</strong> Resolve another friendly block’s Activate text. Prevent a machine from re-entering the same chain so two relays cannot activate one another forever.</li></ul></details></div><div id="chapters">@@ARTICLES@@</div><footer><button id="up">Back to filters ↑</button></footer></main>
<script>
const $=id=>document.getElementById(id), cls=$('cls'),q=$('q'),rar=$('rar'),sup=$('sup'), articles=[...document.querySelectorAll('article')];
function update(){
 let n=0,groups=0; const words=q.value.toLocaleLowerCase().trim().split(/\s+/).filter(Boolean); const filtering=!!(words.length||rar.value||sup.value);
 for(const a of articles){const chosen=!cls.value||cls.value===a.dataset.class;let visible=0;
 for(const row of a.querySelectorAll('tbody tr')){const ok=chosen&&(!rar.value||row.dataset.rarity===rar.value)&&(!sup.value||(sup.value==='Prototype'?row.dataset.support==='Prototype':row.dataset.support!=='Prototype'))&&words.every(x=>(a.dataset.class+' '+row.textContent).toLowerCase().includes(x));row.hidden=!ok;if(ok)visible++;}
 a.hidden=!visible; for(const note of a.querySelectorAll('.notes')){if(filtering&&!note.dataset.auto){note.dataset.auto='1';note.open=false;}else if(!filtering&&note.dataset.auto){delete note.dataset.auto;note.open=true;}} n+=visible;if(visible)groups++;
 }$('intro').hidden=!!cls.value||filtering;$('count').textContent=n?n+' idea'+(n===1?'':'s')+' · '+groups+' class'+(groups===1?'':'es'):'No matching ideas. Clear a filter or try a different word.';}
for(const el of [cls,q,rar,sup])el.addEventListener('input',update);
function pick(c){cls.value=c; q.value='';sup.value='';rar.value='';update();window.scrollTo(0,0);}
document.querySelectorAll('[data-jump]').forEach(b=>b.onclick=()=>pick(b.dataset.jump));$('reset').onclick=()=>pick('');$('up').onclick=()=>{window.scrollTo(0,0);cls.focus();}; if([...cls.options].some(o=>o.value===location.hash.slice(1)))cls.value=location.hash.slice(1);update();
</script></body></html>'''

if __name__=='__main__': make()
