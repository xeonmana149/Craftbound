package craftbound.game;
import craftbound.card.*;
import craftbound.binder.BinderContents;
import craftbound.deck.*;
import com.google.gson.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import java.util.*;
import java.nio.file.*;

public class WorkshopChecks {
 static int checks;
 static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static Identifier id(String s){return Identifier.parse("craftbound:"+s);}
 static Match match(){
  var deck=Collections.nCopies(30,new CardRef(Cards.ZOMBIE,false));
  var sleeve=id("classic");
  var m=new Match(new MatchPlayer("A",UUID.randomUUID(),CardClass.FISHERMAN,sleeve,deck),new MatchPlayer("B",null,CardClass.FISHERMAN,sleeve,deck),RandomSource.create(7));
  m.phase=Match.Phase.PLAYING;m.current=0;m.player(0).hand.clear();m.player(1).hand.clear();m.player(0).xp=10;m.events.clear();return m;
 }
 static Minion mob(Match m,int side,String name){return m.summon(side,new CardRef(id(name),false));}
 static void play(Match m,String name,Target target){m.player(0).xp=10;var ref=new CardRef(id(name),false);m.player(0).hand.add(ref);m.playCard(0,m.player(0).hand.size()-1,target);}
 static void play(Match m,String name){play(m,name,Target.NONE);}
 static int hand(Match m,String name){return (int)m.player(0).hand.stream().filter(c->c.id().equals(id(name))).count();}
 static void validation() {
  var base=craftbound.test.TestCatalogue.full();
  ok(base.entries().size()==115,"catalogue size");ok(Cards.collectible().size()==94,"collectible size");
  ok(CardCatalog.parse(base.json()).entries().equals(base.entries()),"catalogue round trip");
  ok(!CardCatalog.description(CardCatalog.entry(id("sharpness"))).contains("Play:"),"Item/Enchantment text omits redundant Play");
  ok(CardCatalog.description(CardCatalog.entry(id("iron_hoe"))).contains("After your hero attacks: Summon Wheat"),"Tool text identifies the hero and uses singular card name");
  for (String label : List.of("Play", "After attack", "At the end of your turn", "After a friendly Undead mob is healed"))
   ok(Cards.styledText(label+": Draw 1 card.").getSiblings().stream().anyMatch(c->c.getString().equals(label)&&c.getStyle().isBold()),"Bold timing label: "+label);
  for(int n=0;n<14;n++) {
   var root=JsonParser.parseString(base.json()).getAsJsonObject();var cards=root.getAsJsonArray("cards");var c=cards.get(0).getAsJsonObject();
   switch(n){
    case 0->c.addProperty("cost",1.5); case 1->c.addProperty("cost",-1);case 2->c.addProperty("cost","1");
    case 3->c.addProperty("kind","SPELL");case 4->c.addProperty("token",false);case 5->c.addProperty("name","");
    case 6->cards.add(c.deepCopy());case 7->cards.remove(cards.size()-1);case 8->c.addProperty("id","craftbound:missing");
    case 9->c.getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("action","BUFF");
    case 10->c.getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("amount",0);
    case 11->c.getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("amount",101);
    case 12->c.addProperty("passive","ARMOR");case 13->c.addProperty("health",2);
   }
   boolean rejected=false;
   try{var next=CardCatalog.parse(root.toString());CardCatalog.validateReplacement(base,next);}catch(RuntimeException e){rejected=true;}
   ok(rejected,"bad catalogue accepted "+n);ok(CardCatalog.current()==base,"failed validation changed live rules");
  }
 }
 static void buffs(){
  var m=match();var wheat=mob(m,0,"wheat");play(m,"bone_meal",Target.mob(0,wheat.id));
  ok(wheat.attack==0&&wheat.health==4&&wheat.maxHealth==4,"Bone Meal block +2 health");
  var zombie=mob(m,0,"zombie");play(m,"bone_meal",Target.mob(0,zombie.id));
  ok(zombie.attack==3&&zombie.health==4&&zombie.maxHealth==4,"Bone Meal mob +1/+1");
  ok(!CardEffects.canTarget(id("potion_of_strength"),id("wheat")),"Attack potion cannot target blocks");
  ok(CardEffects.canTarget(id("bone_meal"),id("wheat")),"Bone Meal can target blocks");
  ok(CardEffects.canTarget(id("bone_meal"),id("zombie")),"Bone Meal can target mobs");
  var comp=mob(m,0,"composter");CardEffects.onEndTurn(m,0);
  ok(wheat.health==4&&wheat.attack==0,"Composter ignores blocks");ok(zombie.attack==4&&zombie.health==5,"Composter buffs mob");
  m=match();wheat=mob(m,0,"wheat");mob(m,0,"composter");CardEffects.onEndTurn(m,0);
  ok(wheat.health==2,"Composter does nothing with only blocks");
  m.player(0).hand.add(new CardRef(id("potion_of_strength"),false));ok(m.whyCantPlay(0,0)!=null,"No mob makes attack buff unplayable");
  int xp=m.player(0).xp;m.playCard(0,0,Target.mob(0,wheat.id));ok(m.player(0).xp==xp&&m.player(0).hand.size()==1,"Invalid target spends nothing");
  m=match();var cod=mob(m,0,"cod");var cow=mob(m,0,"cow");play(m,"dolphin");ok(cod.attack==2&&cow.attack==2,"Aquatic filter");
  m=match();var villager=mob(m,0,"villager");cow=mob(m,0,"cow");play(m,"librarian");ok(villager.health==4&&cow.health==3,"Villager filter");
  m=match();cow=mob(m,0,"cow");wheat=mob(m,0,"wheat");play(m,"shepherd");ok(cow.attack==3&&cow.health==4&&wheat.health==2,"Animal filter");
  m=match();zombie=mob(m,0,"zombie");cow=mob(m,0,"cow");play(m,"drowned");ok(zombie.attack==3&&cow.attack==2,"Undead filter");
  m=match();var v=mob(m,0,"vindicator");zombie=mob(m,0,"zombie");play(m,"pillager");ok(v.attack==5&&zombie.attack==2,"Illager filter");
 }
 static void legacy(){
  var m=match();var enemy=mob(m,1,"furnace");play(m,"warden");ok(!m.player(1).board.contains(enemy),"Warden damages blocks");
  m=match();var own=mob(m,0,"ravager");enemy=mob(m,1,"ravager");play(m,"wither");ok(own.health==5&&enemy.health==5&&m.player(0).board.get(1).health==6,"Wither excludes itself");
  m=match();play(m,"evoker");ok(m.player(0).board.size()==3,"Evoker two Vex");var ev=m.player(0).board.get(0);m.damage(ev,100,null);m.resolveDeaths();ok(hand(m,"totem_of_undying")==1,"Evoker Drop");
  for(String[] pair:new String[][]{{"zombie","rotten_flesh"},{"skeleton","arrow"},{"wither","nether_star"},{"ender_dragon","dragon_egg"}}){
   m=match();var unit=mob(m,0,pair[0]);m.damage(unit,100,null);m.resolveDeaths();ok(hand(m,pair[1])==1,"Drop "+pair[0]);
  }
  m=match();play(m,"bee");ok(m.player(0).board.size()==2,"Bee wheat");
  m=match();play(m,"sniffer");ok(m.player(0).board.size()==3,"Sniffer two wheat");
  m=match();play(m,"bountiful_harvest");ok(m.player(0).board.size()==2&&m.player(0).hand.size()==1,"Harvest");
  m=match();play(m,"nether_star");ok(m.player(0).hand.size()==2,"Nether Star draw");
  m=match();m.player(0).health=10;play(m,"rotten_flesh");ok(m.player(0).health==12,"Flesh heal");
  play(m,"experience_bottle");ok(m.player(0).xp==11,"Bottle temporary XP");
  play(m,"dragon_egg");ok(m.player(0).xp==13,"Egg temporary XP");
  m=match();var victim=mob(m,1,"wheat");play(m,"arrow",Target.mob(1,victim.id));ok(m.player(1).board.isEmpty(),"Arrow hits blocks");
  m=match();victim=mob(m,1,"wheat");play(m,"piston",Target.mob(1,victim.id));ok(m.player(1).board.isEmpty()&&m.player(1).hand.isEmpty(),"Token bounce destroys");
  m=match();victim=mob(m,1,"zombie");play(m,"enderman",Target.mob(1,victim.id));ok(m.player(1).board.isEmpty()&&m.player(1).hand.size()==1,"Enderman bounce");
  m=match();var zombie=mob(m,0,"zombie");play(m,"totem_of_undying",Target.mob(0,zombie.id));m.damage(zombie,100,null);m.resolveDeaths();ok(m.player(0).board.size()==1,"Totem resummons");
  var born=m.player(0).board.get(0);play(m,"enchanted_golden_apple",Target.mob(0,born.id));m.damage(born,5,null);ok(born.health==3,"Apple immunity");
  m=match();var source=mob(m,0,"shulker");victim=mob(m,1,"ravager");m.damage(victim,1,source);ok(victim.frozen,"Shulker freeze");
  source=mob(m,0,"wither_skeleton");m.damage(victim,1,source);ok(victim.withered,"Wither Skeleton effect");
  m=match();var creeper=mob(m,0,"creeper");var block=mob(m,1,"furnace");m.damage(creeper,100,null);m.resolveDeaths();ok(block.health==1,"Creeper hits blocks");
  m=match();creeper=mob(m,0,"charged_creeper");m.damage(creeper,100,null);m.resolveDeaths();ok(m.player(1).health==Match.START_HEALTH-4,"Charged creeper hero damage");
  m=match();m.player(0).health=10;mob(m,0,"furnace");CardEffects.onEndTurn(m,0);ok(m.player(0).health==12,"Furnace");
  m=match();mob(m,0,"dispenser");CardEffects.onEndTurn(m,0);ok(m.player(1).health==Match.START_HEALTH-1,"Dispenser");
  m=match();zombie=mob(m,0,"zombie");block=mob(m,0,"wheat");mob(m,0,"beacon");ok(m.attackOf(zombie)==3&&m.attackOf(block)==0,"Beacon aura ignores blocks");
 }
 static void sprint(){
  for(String name:List.of("vex","bee","vindicator","ender_dragon")){
   var m=match();var a=mob(m,0,name);var target=mob(m,1,"zombie");
   ok(m.whyCantAttack(0,a.id,Target.mob(1,target.id))==null,"Sprint board "+name);
   ok(m.whyCantAttack(0,a.id,Target.hero(1))!=null,"Sprint face denied "+name);
   m.attack(0,a.id,Target.hero(1));ok(m.player(1).health==Match.START_HEALTH&&a.ready,"Rejected face doesn't consume attack");
   m.endTurn(0);m.endTurn(1);ok(!a.summonedThisTurn&&m.whyCantAttack(0,a.id,Target.hero(1))==null,"Sprint next turn face "+name);
   mob(m,1,"turtle");ok(m.whyCantAttack(0,a.id,Target.hero(1))!=null,"Shield still protects face");
  }
  var m=match();var a=mob(m,0,"vex");m.bounce(a);ok(m.player(0).board.isEmpty(),"Token bounce");
  var fresh=mob(m,0,"vindicator");fresh.frozen=true;m.endTurn(0);m.endTurn(1);ok(!fresh.ready&&!fresh.summonedThisTurn,"Frozen sprint skips first full turn");
  m.endTurn(0);m.endTurn(1);ok(m.whyCantAttack(0,fresh.id,Target.hero(1))==null,"Frozen sprint eventually face");
  m=match();mob(m,0,"vex");m.player(0).xp=0;MatchAI.step(m,0);ok(m.player(1).health==Match.START_HEALTH&&m.current==1,"AI doesn't hit face with fresh Sprint");
 }
 static void tools(){
  for(String[] pair:new String[][]{{"iron_sword","sharpness","5","2"},{"iron_pickaxe","efficiency","3","4"},{"iron_hoe","unbreaking","1","5"},{"fishing_rod","lure","1","4"}}){
   var m=match();m.player(0).hand.add(new CardRef(id(pair[1]),false));ok(m.whyCantPlay(0,0)!=null,"Enchant requires tool");m.player(0).hand.clear();
   play(m,pair[0]);m.player(0).heroAttacked=true;play(m,pair[1]);
   ok(m.player(0).tool.attack==Integer.parseInt(pair[2])&&m.player(0).tool.uses==Integer.parseInt(pair[3]),"Enchantment "+pair[1]);
   ok(!m.heroCanAttack(0),"Enchant does not refresh attack");
   if(pair[1].equals("lure"))ok(hand(m,"caught_cod")==1,"Lure catches cod");
  }
  var m=match();play(m,"fishing_rod");m.attack(0,Match.HERO,Target.hero(1));ok(hand(m,"caught_cod")==1&&m.player(0).tool.uses==2,"Rod catches after attack");
  m=match();play(m,"iron_hoe");m.attack(0,Match.HERO,Target.hero(1));ok(m.player(0).board.size()==1&&m.player(0).board.get(0).card.id().equals(id("wheat")),"Hoe plants after attack");
  m=match();ok(HeroPowers.of(m.player(0))==HeroPowers.Power.FISH,"Fisherman power");m.usePower(0);ok(m.player(0).board.size()==1&&m.player(0).board.getFirst().definition().mobType()==MobType.AQUATIC,"Catch summons an Aquatic token");
  for(int n=0;n<10;n++)m.player(0).hand.add(new CardRef(Cards.ZOMBIE,false));m.player(0).powerUsed=false;ok(m.whyCantUsePower(0)==null,"Fishing summons directly even with a full hand");
  while(m.player(0).board.size()<Match.MAX_MOBS)mob(m,0,"wolf_pup");ok(m.whyCantUsePower(0)!=null,"Fishing requires a board slot");
 }
 static void decks() throws Exception {
  var cards=BinderContents.EMPTY.add(Cards.ZOMBIE,false,2).add(Cards.SKELETON,true,1).add(Cards.IRON_SWORD,false,2);
  var old=new DeckContents(CardClass.FIGHTER,cards,true);var switched=DeckBuilding.forClass(old,CardClass.FARMER);
  ok(switched.size()==3&&switched.cards().count(Cards.SKELETON,true)==1&&switched.copies(Cards.IRON_SWORD)==0,"Class change keeps neutral normal and enchanted copies");
  ok(old.size()==5&&cards.total()==5,"Class change leaves original collection unchanged");
  var factory=MatchManager.class.getDeclaredMethod("cardMasterDeck",CardClass.class,RandomSource.class);factory.setAccessible(true);
  for(var cls:CardClass.values())if(cls!=CardClass.NEUTRAL)for(int seed=0;seed<250;seed++){
   @SuppressWarnings("unchecked") var deck=(List<CardRef>)factory.invoke(null,cls,RandomSource.create(seed));
   if(seed==0) {
    long available=Cards.collectible().stream().map(Cards::get)
        .filter(c->(c.cardClass()==CardClass.NEUTRAL || c.cardClass()==cls)&&MatchManager.frontliner(c)&&c.cost()<=2)
        .mapToInt(c->DeckRules.maxCopies(c.rarity())).sum();
    ok(deck.stream().map(c->Cards.get(c.id())).filter(c->MatchManager.frontliner(c)&&c.cost()<=2).count()>=Math.min(8,available),
        "Card Master reserves affordable attackers when its class pool supports them");
   }
   ok(deck.size()==30,"AI deck size");var counts=new HashMap<Identifier,Integer>();
   for(var ref:deck){ok(DeckRules.allowed(ref.id(),cls),"Legal AI class");counts.merge(ref.id(),1,Integer::sum);}
   counts.forEach((id,n)->ok(n<=DeckRules.maxCopies(Cards.get(id).rarity()),"AI copy limit"));
  }
 }
 static void healAndCycles(){
  var root=JsonParser.parseString(craftbound.test.TestCatalogue.full().json()).getAsJsonObject();
  var cards=root.getAsJsonArray("cards");
  var custom=cards.asList().stream().map(JsonElement::getAsJsonObject).filter(c->c.get("id").getAsString().equals("craftbound:bone_meal")).findFirst().orElseThrow().deepCopy();
  custom.addProperty("id","craftbound:test_heal");custom.getAsJsonArray("effects").remove(0);
  var effect=custom.getAsJsonArray("effects").get(0).getAsJsonObject();effect.addProperty("action","HEAL");effect.addProperty("amount",2);
  cards.add(custom);var next=CardCatalog.parse(root.toString());CardCatalog.validateReplacement(craftbound.test.TestCatalogue.full(),next);CardCatalog.install(next);
  try{
   var m=match();var block=mob(m,0,"furnace");block.health=1;play(m,"test_heal",Target.mob(0,block.id));ok(block.health==3&&block.maxHealth==3,"Healing restores, not grows, health");
   ok(m.events.stream().anyMatch(e->e.type()==MatchEvent.Type.HEAL&&e.mid()==block.id),"Heal targets mob animation");
  }finally{CardCatalog.install(craftbound.test.TestCatalogue.full());}
 }

 static void clientTargets() throws Exception {
  var uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);var unsafe=(sun.misc.Unsafe)uf.get(null);
  var screenType=Class.forName("craftbound.client.match.MatchScreen");
  var stateType=Class.forName("craftbound.client.match.MatchState");
  var spotType=Class.forName("craftbound.client.match.MatchScreen$Spot");
  var handType=Class.forName("craftbound.client.match.MatchState$HandCard");
  var spotCtor=spotType.getDeclaredConstructors()[0];spotCtor.setAccessible(true);
  var parse=stateType.getDeclaredMethod("parse",JsonObject.class,List.class);parse.setAccessible(true);
  var shown=screenType.getDeclaredField("shown");shown.setAccessible(true);
  var attack=screenType.getDeclaredMethod("isAttackTargetFor",spotType,spotType);attack.setAccessible(true);
  var valid=screenType.getDeclaredMethod("isValidTargetFor",handType,spotType);valid.setAccessible(true);
  var m=match();var a=mob(m,0,"vindicator");var b=mob(m,0,"wheat");var enemy=mob(m,1,"zombie");
  var source=spotCtor.newInstance(0,0,30,40,0,a.id,-1);
  var face=spotCtor.newInstance(0,0,30,40,1,Match.HERO,-1);
  var enemySpot=spotCtor.newInstance(0,0,30,40,1,enemy.id,-1);
  for(boolean fresh:new boolean[]{true,false}){
   a.summonedThisTurn=fresh;var screen=unsafe.allocateInstance(screenType);
   shown.set(screen,parse.invoke(null,JsonParser.parseString(MatchView.build(m,0,m.events.size())).getAsJsonObject(),new ArrayList<>()));
   ok((boolean)attack.invoke(screen,source,face)==!fresh,"Client Sprint face highlight/release");
   ok((boolean)attack.invoke(screen,source,enemySpot),"Client Sprint board highlight");
   var blockSpot=spotCtor.newInstance(0,0,30,40,0,b.id,-1);
   for(String name:List.of("bone_meal","potion_of_strength")){
    var hc=unsafe.allocateInstance(handType);var field=handType.getDeclaredField("id");field.setAccessible(true);field.set(hc,id(name));
    field=handType.getDeclaredField("target");field.setAccessible(true);field.set(hc,"FRIENDLY_MOB");
    ok((boolean)valid.invoke(screen,hc,blockSpot)==name.equals("bone_meal"),"Client block target "+name);
    ok(!(boolean)valid.invoke(screen,hc,face),"Buff cannot highlight hero");
   }
  }
 }
 static void packet() {
  var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
  try {
   var original=new craftbound.network.CardCatalogPayload(CardCatalog.current().json());
   craftbound.network.CardCatalogPayload.CODEC.encode(buf,original);
   var restored=craftbound.network.CardCatalogPayload.CODEC.decode(buf);
   ok(restored.equals(original),"Network catalogue round trip");
   ok(CardCatalog.parse(restored.json()).entries().equals(CardCatalog.current().entries()),"Network catalogue rules");
  } finally {buf.release();}
 }
 static void resolutionLimit(){
  var root=JsonParser.parseString(craftbound.test.TestCatalogue.full().json()).getAsJsonObject();
  var cards=root.getAsJsonArray("cards");
  var zombie=cards.asList().stream().map(JsonElement::getAsJsonObject).filter(c->c.get("id").getAsString().equals("craftbound:zombie")).findFirst().orElseThrow().deepCopy();
  zombie.addProperty("id","craftbound:loop_test");zombie.addProperty("health",1);
  var summon=zombie.getAsJsonArray("effects").get(0).getAsJsonObject();summon.addProperty("action","SUMMON");summon.addProperty("card","craftbound:loop_test");
  var damage=summon.deepCopy();damage.addProperty("action","DAMAGE");damage.addProperty("target","ALL_MOBS");damage.addProperty("card","");damage.addProperty("amount",100);
  zombie.getAsJsonArray("effects").add(damage);cards.add(zombie);
  var next=CardCatalog.parse(root.toString());CardCatalog.validateReplacement(craftbound.test.TestCatalogue.full(),next);CardCatalog.install(next);
  try{var m=match();var loop=mob(m,0,"loop_test");m.damage(loop,100,null);m.resolveDeaths();ok(m.over&&m.winner==-1,"Repeating effect ends in bounded draw");}
  finally{CardCatalog.install(craftbound.test.TestCatalogue.full());}
 }

 static void creativeCollection() {
  var contents=BinderContents.fullCollection();
  ok(contents.collected(Cards.collectible())==Cards.collectible().size(),"Creative binder has every collectible");
  ok(contents.collectedEnchanted(Cards.collectible())==Cards.collectible().size(),"Creative binder has every foil");
  for(var id:Cards.ids()) {
   var definition=Cards.get(id);
   int expected=definition.token()?0:DeckRules.maxCopies(definition.rarity());
   ok(contents.count(id,false)==expected&&contents.count(id,true)==expected,"Creative binder playset "+id);
  }
  ok(craftbound.binder.Disenchanting.disenchantExtras(contents).disenchanted()==0,"Creative preset respects binder caps");
  var encoded=BinderContents.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,contents).getOrThrow();
  ok(BinderContents.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,encoded).getOrThrow().equals(contents),"Creative binder save/load");
  for(Keyword keyword:Keyword.values())ok(keyword.displayName().getStyle().isBold(),"Bold keyword "+keyword);
 }
 public static void main(String[]args)throws Exception{
  craftbound.client.match.MultiplayerBoardGeometryChecks.run();
  FoundationResetChecks.run();
  HeroPowerChecks.run();
  craftbound.test.TestCatalogue.install();
  CombatTacticsChecks.run();
  EffectSequenceChecks.run();
  IgnoreShieldChecks.run();
  CompoundBuffChecks.run();
  AuraAndClassCardChecks.run();
  BlockUtilityChecks.run();
  badOmenChecks();
  tntChecks();
  SurvivingKillChecks.run();
  DamageHeroChecks.run();
  HandEffectChecks.run();
  RepairToolChecks.run();
  KeywordGrantChecks.run();
  NegateKeywordChecks.run();
  PickOneChecks.run();
  ElementBoostChecks.run();
  RowShieldChecks.run();
  BrewChecks.run();
  UsesChecks.run();
  TridentChecks.run();
  BundledConfigChecks.run();
  RandomToolChecks.run();
  CommanderChecks.run();
  TrialsChecks.run();
  TrialsOpponentChecks.run();
  TrialTrainingChecks.run();
  TeamTargetingChecks.run();
  ClockSpectatorChecks.run();
  PausedMatchChecks.run();
  PausedMatchChecks.deletion();
  foundationMechanics();
  excavateAndLiveTools();
  aiHealingFreezeAndIcons();
  feedReleaseAndTotemAI();
  savedBinderDecks();
  foundationFollowup();
  alchemistAndThorns();
  chosenCharacters();
  deckXpCurve();
  minerRules();
  heroStatusesAndNewClasses();
  expandedPreviews();
  playPreviewsAndBoardLimit();
  binderPages();
  creativeCollection();
  validation();buffs();legacy();sprint();tools();decks();healAndCycles();clientTargets();packet();resolutionLimit();
  System.out.println("PASS: "+checks+" assertions; catalogue validation, all migrated effect families, mob/block targeting, tribal synergies, Sprint, tools/enchantments, Fisherman power, class switching client highlights, packet round trip, resolution limit and 2,000 AI decks.");
 }
 static EffectPreview.Subject previewMob(String name,int side,int mid,int health,int max) {
  return new EffectPreview.Subject(EffectPreview.Kind.BOARD,id(name),side,mid,health,max,false,false,false,false);
 }
 static EffectPreview.Highlight preview(String name,boolean ongoing,EffectPreview.Subject target) {
  return EffectPreview.forTarget(new EffectPreview.Source(id(name),0,42,ongoing,2),target);
 }
 static void expandedPreviews() {
  var zombie=previewMob("zombie",0,7,3,3);var cow=previewMob("cow",0,8,3,3);var wheat=previewMob("wheat",0,9,2,2);
  var buff=preview("drowned",false,zombie);ok(buff!=null&&!buff.harmful()&&!buff.possible(),"Solid yellow tribal buff");
  ok(preview("drowned",true,zombie)==null,"Board hover does not replay Spawn");
  var compost=preview("composter",true,zombie);ok(compost!=null&&!compost.harmful()&&compost.possible(),"Composter future random buff corners");
  ok(preview("composter",true,wheat)==null&&preview("composter",true,previewMob("zombie",1,10,3,3))==null,"Composter excludes blocks and enemies");
  ok(preview("rotten_flesh",false,EffectPreview.Subject.hero(0,Match.START_HEALTH))==null,"Full-health hero has no heal preview");
  var heal=preview("rotten_flesh",false,EffectPreview.Subject.hero(0,15));ok(heal!=null&&!heal.harmful()&&!heal.possible(),"Wounded hero healing preview");
  ok(preview("rotten_flesh",false,EffectPreview.Subject.hero(1,15))==null,"Healing uses correct hero");
  ok(preview("furnace",true,EffectPreview.Subject.hero(0,15)).possible(),"Ongoing hero heal is future benefit");
  ok(preview("totem_of_undying",false,zombie).possible()&&!preview("totem_of_undying",false,zombie).harmful(),"Protection targets yellow");
  var protectedMob=new EffectPreview.Subject(EffectPreview.Kind.BOARD,id("zombie"),0,7,3,3,true,false,false,true);
  ok(preview("totem_of_undying",false,protectedMob)==null&&preview("enchanted_golden_apple",false,protectedMob)==null,"Already protected cards do not falsely glow");
  ok(preview("sharpness",false,EffectPreview.Subject.tool(0,id("iron_sword")))!=null,"Enchant equipped friendly tool");
  ok(preview("sharpness",false,EffectPreview.Subject.tool(1,id("iron_sword")))==null,"Do not enchant enemy tool");
  ok(preview("sharpness",false,EffectPreview.Subject.tool(0,null))==null,"No tool means no enchant preview");
  ok(preview("lava_bucket",false,EffectPreview.Subject.tool(1,id("iron_sword"))).harmful(),"Tool removal red");
  ok(preview("lava_bucket",false,EffectPreview.Subject.hero(1,20))==null,"Tool removal highlights tool, not face");
  var enemy=previewMob("zombie",1,10,3,3);
  ok(preview("arrow",false,enemy).harmful()&&preview("arrow",false,enemy).possible(),"Targeted damage red corners");
  ok(preview("warden",false,enemy).harmful()&&!preview("warden",false,enemy).possible(),"Automatic enemy damage solid red");
  ok(preview("sweeping_edge",false,enemy).harmful(),"Tool damage uses equipped attack");
  ok(EffectPreview.forTarget(new EffectPreview.Source(id("sweeping_edge"),0,-1,false,0),enemy)==null,"No attack means no damage preview");
  ok(preview("arrow",false,new EffectPreview.Subject(EffectPreview.Kind.BOARD,id("zombie"),1,10,3,3,true,false,false,false))==null,"Invulnerable target takes no damage");
  ok(preview("dispenser",true,EffectPreview.Subject.hero(1,20)).harmful(),"Random enemy damage includes hero");
  ok(preview("beacon",true,zombie)!=null&&preview("beacon",true,wheat)==null,"Board aura preview excludes blocks");
  var original=CardCatalog.current();
  try {
   var root=JsonParser.parseString(original.json()).getAsJsonObject();var cards=root.getAsJsonArray("cards");
   var base=cards.asList().stream().map(JsonElement::getAsJsonObject).filter(c->c.get("id").getAsString().equals("craftbound:bone_meal")).findFirst().orElseThrow();
   var custom=base.deepCopy();custom.addProperty("id","craftbound:preview_heal");
   var effect=custom.getAsJsonArray("effects").get(0).getAsJsonObject();
   effect.addProperty("action","HEAL");effect.addProperty("amount",2);effect.addProperty("attack",0);effect.addProperty("health",0);effect.addProperty("boardKind","BOTH");
   var effects=new JsonArray();effects.add(effect);custom.add("effects",effects);cards.add(custom);
   CardCatalog.install(CardCatalog.parse(root.toString()));
   ok(preview("preview_heal",false,zombie)==null&&preview("preview_heal",false,wheat)==null,"Full-health mobs and blocks do not glow for heals");
   ok(preview("preview_heal",false,previewMob("zombie",0,7,1,3))!=null,"Wounded mob can receive heal");
   ok(preview("preview_heal",false,previewMob("wheat",0,9,1,2))!=null,"Wounded block can receive heal");
  } finally {CardCatalog.install(original);}
 }

 static void playPreviewsAndBoardLimit() {
  ok(CardEffects.previewOnPlay(id("drowned"),id("zombie"),true)==CardEffects.PlayPreview.AFFECTED,"Drowned previews friendly Undead");
  ok(CardEffects.previewOnPlay(id("drowned"),id("drowned"),true)==CardEffects.PlayPreview.AFFECTED,"Another Drowned is not excluded as self");
  ok(CardEffects.previewOnPlay(id("drowned"),id("zombie"),false)==CardEffects.PlayPreview.NONE,"No enemy Undead buff preview");
  ok(CardEffects.previewOnPlay(id("drowned"),id("cow"),true)==CardEffects.PlayPreview.NONE,"No unrelated type preview");
  ok(CardEffects.previewOnPlay(id("drowned"),id("wheat"),true)==CardEffects.PlayPreview.NONE,"No block attack preview");
  ok(CardEffects.previewOnPlay(id("dolphin"),id("cod"),true)==CardEffects.PlayPreview.AFFECTED,"Aquatic preview");
  ok(CardEffects.previewOnPlay(id("bone_meal"),id("wheat"),true)==CardEffects.PlayPreview.POSSIBLE,"Chosen block target is eligible, not guaranteed");
  ok(CardEffects.previewOnPlay(id("bone_meal"),id("wheat"),false)==CardEffects.PlayPreview.NONE,"Chosen friendly excludes enemies");
  ok(CardEffects.previewOnPlay(id("composter"),id("zombie"),true)==CardEffects.PlayPreview.NONE,"End-turn effect not shown as on-play buff");
  ok(CardEffects.previewOnPlay(id("beacon"),id("zombie"),true)==CardEffects.PlayPreview.AFFECTED,"Immediate attack aura preview");
  ok(CardEffects.previewOnPlay(id("beacon"),id("wheat"),true)==CardEffects.PlayPreview.NONE,"Aura excludes blocks");
  ok(CardEffects.previewOnPlay(id("evoker"),id("vex"),true)==CardEffects.PlayPreview.NONE,"Summons do not affect existing copies");
  var m=match();
  var undead=mob(m,0,"zombie");var drowned=mob(m,0,"drowned");var animal=mob(m,0,"cow");var block=mob(m,0,"wheat");var enemy=mob(m,1,"zombie");
  var before=new HashMap<Minion,Integer>();for(var unit:List.of(undead,drowned,animal,block,enemy))before.put(unit,unit.attack);
  play(m,"drowned");
  for(var unit:before.keySet())ok((unit.attack>before.get(unit))==(CardEffects.previewOnPlay(id("drowned"),unit.card.id(),unit!=enemy)==CardEffects.PlayPreview.AFFECTED),"Drowned preview matches actual buff recipients");
  m=match();
  for(int side=0;side<2;side++)for(int n=0;n<6;n++)ok(mob(m,side,"zombie")!=null,"First six board cards");
  play(m,"zombie");ok(m.player(0).board.size()==7,"Seventh card can be played");
  ok(mob(m,1,"zombie")!=null&&m.player(1).board.size()==7,"Opponent has seven independent slots");
  for(int side=0;side<2;side++)ok(mob(m,side,"zombie")==null&&m.player(side).board.size()==7,"Eighth summon rejected");
  m.player(0).xp=10;m.player(0).hand.add(new CardRef(id("zombie"),false));
  ok(m.whyCantPlay(0,0)!=null,"Eighth play unavailable");m.playCard(0,0,Target.NONE);
  ok(m.player(0).xp==10&&m.player(0).hand.size()==1&&m.player(0).board.size()==7,"Rejected play spends no card or XP");
  play(m,"arrow",Target.mob(1,m.player(1).board.get(0).id));ok(hand(m,"arrow")==0,"Items remain playable on a full board");
  m=match();for(int n=0;n<2;n++)mob(m,0,"wheat");
  play(m,"bountiful_harvest");ok(m.player(0).board.size()==3,"Multi-summon stops at three blocks");
 }

 static void binderPages() {
  var original=CardCatalog.current();
  var numbers=new HashMap<Identifier,Integer>();
  for(var card:Cards.collectible())numbers.put(card,Cards.collectorNumber(card));
  try {
   // Force multiple spreads in two adjacent rarities, with out-of-order costs/names.
   var root=JsonParser.parseString(original.json()).getAsJsonObject();
   var cards=root.getAsJsonArray("cards");
   var template=cards.asList().stream().map(JsonElement::getAsJsonObject).filter(c->c.get("id").getAsString().equals("craftbound:zombie")).findFirst().orElseThrow();
   for(int n=0;n<42;n++){
    var c=template.deepCopy();c.addProperty("id","craftbound:binder_test_"+n);c.addProperty("name","Binder "+(42-n));
    c.addProperty("class","NEUTRAL");c.addProperty("rarity",n<21?"COMMON":"RARE");c.addProperty("cost",n%11);cards.add(c);
   }
   CardCatalog.install(CardCatalog.parse(root.toString()));
   var seen=new HashSet<Identifier>();
   for(var cls:CardClass.values()){
    var spreads=craftbound.binder.BinderPages.forClass(cls);
    int previousRarity=-1;
    for(var rarity:CardRarity.values()){
     int start=craftbound.binder.BinderPages.firstSpread(spreads,rarity);
     ok(spreads.get(start).rarity()==rarity&&spreads.get(start).sectionPage()==1,"Rarity jump starts a fresh spread");
    }
    for(var spread:spreads){
     ok(spread.rarity().ordinal()>=previousRarity,"Rarity order");previousRarity=spread.rarity().ordinal();
     ok(spread.cards().size()<=18,"Spread capacity");
     for(var card:spread.cards()){
      ok(seen.add(card),"Card appears once");
      ok(Cards.get(card).cardClass()==cls&&Cards.get(card).rarity()==spread.rarity()&&!Cards.get(card).token(),"Class/rarity/token grouping");
     }
    }
    for(var rarity:CardRarity.values()){
     var section=spreads.stream().filter(s->s.rarity()==rarity).toList();
     var flattened=section.stream().flatMap(s->s.cards().stream()).toList();
     var expected=Cards.collectible().stream().filter(id->Cards.get(id).cardClass()==cls&&Cards.get(id).rarity()==rarity)
       .sorted(Comparator.<Identifier>comparingInt(id->Cards.get(id).cost()).thenComparing(id->Cards.name(id).getString(),String.CASE_INSENSITIVE_ORDER).thenComparing(Identifier::toString)).toList();
     ok(flattened.equals(expected),"Cost/name order across spreads");
     for(int n=0;n<section.size();n++){
      ok(section.get(n).sectionPage()==n+1&&section.get(n).sectionPages()==section.size(),"Section pagination");
      if(n<section.size()-1)ok(section.get(n).cards().size()==18,"Only last spread may be partial");
     }
    }
   }
   ok(seen.equals(new HashSet<>(Cards.collectible())),"All collectibles covered");
   for(var entry:numbers.entrySet())ok(Cards.collectorNumber(entry.getKey())==entry.getValue(),"Collector number unchanged");
  } finally {CardCatalog.install(original);}
 }
 static EffectPreview.Subject subject(String name,boolean friendly,int health,int max,boolean invulnerable,boolean frozen,boolean withered,boolean totem) {
  return new EffectPreview.Subject(EffectPreview.Kind.BOARD,id(name),friendly?0:1,7,health,max,invulnerable,frozen,withered,totem);
 }
 static void foundationFollowup() {
  ok(Match.START_HEALTH==30,"30 starting hero Health");var m=match();
  ok(m.player(0).health==30&&m.player(1).health==30,"Both players begin at 30");
  m.player(0).health=28;m.healHero(0,100);ok(m.player(0).health==30,"Healing caps at 30");
  var origin=CardCatalog.current();
  try {
   var root=JsonParser.parseString(origin.json()).getAsJsonObject();var cards=root.getAsJsonArray("cards");
   for(String[] spec:new String[][]{{"test_grazer","GRAZER"},{"test_hardened","BATTLE_HARDENED"},{"test_school","SCHOOLING"}}){
    var card=cards.asList().stream().map(JsonElement::getAsJsonObject).filter(c->c.get("id").getAsString().equals("craftbound:cow")).findFirst().orElseThrow().deepCopy();
    card.addProperty("id","craftbound:"+spec[0]);card.addProperty("passive",spec[1]);card.addProperty("passiveAmount",spec[1].equals("GRAZER")?2:1);
    card.addProperty("attack",2);card.addProperty("health",6);card.addProperty("cost",1);card.addProperty("autoText",true);
    card.add("effects",new JsonArray());card.add("keywords",new JsonArray());cards.add(card);
   }
   var cleanser=cards.asList().stream().map(JsonElement::getAsJsonObject).filter(c->c.get("id").getAsString().equals("craftbound:bone_meal")).findFirst().orElseThrow().deepCopy();
   cleanser.addProperty("id","craftbound:test_cleanse");cleanser.addProperty("autoText",true);
   var effect=cleanser.getAsJsonArray("effects").get(0).getAsJsonObject().deepCopy();
   effect.addProperty("action","CLEANSE");effect.addProperty("boardKind","BOTH");effect.addProperty("attack",0);effect.addProperty("health",0);
   var effects=new JsonArray();effects.add(effect);cleanser.add("effects",effects);cards.add(cleanser);
   var shelf=cards.asList().stream().map(JsonElement::getAsJsonObject).filter(c->c.get("id").getAsString().equals("craftbound:wheat")).findFirst().orElseThrow().deepCopy();
   shelf.addProperty("id","craftbound:test_spent_block");shelf.addProperty("type","");shelf.add("effects",new JsonArray());cards.add(shelf);
   var dropBlock=shelf.deepCopy();dropBlock.addProperty("id","craftbound:test_drop_block");
   var dropEffect=effect.deepCopy();dropEffect.addProperty("action","GIVE");dropEffect.addProperty("trigger","DROP");dropEffect.addProperty("target","SELF");
   dropEffect.addProperty("card","craftbound:arrow");dropEffect.addProperty("type","");dropEffect.addProperty("excludeSelf",false);
   var dropEffects=new JsonArray();dropEffects.add(dropEffect);dropBlock.add("effects",dropEffects);cards.add(dropBlock);
   CardCatalog.install(CardCatalog.parse(root.toString()));
   ok(CardCatalog.description(CardCatalog.entry(id("test_school"))).contains("until the end of the turn"),"Schooling generated wording");
   ok(CardCatalog.description(CardCatalog.entry(id("test_cleanse"))).contains("Cleanse"),"Cleanse generated wording");

   m=match();var grazer=mob(m,0,"test_grazer");grazer.health=2;CardEffects.onEndTurn(m,0);
   ok(grazer.health==2,"Grazer needs friendly Crop");var crop=mob(m,1,"wheat");CardEffects.onEndTurn(m,0);
   ok(grazer.health==2,"Enemy Crop does not feed Grazer");mob(m,0,"wheat");CardEffects.onEndTurn(m,0);
   ok(grazer.health==4,"Grazer heals 2");CardEffects.onEndTurn(m,1);ok(grazer.health==4,"Grazer only owner turn");
   CardEffects.onEndTurn(m,0);CardEffects.onEndTurn(m,0);ok(grazer.health==6&&grazer.maxHealth==6,"Grazer caps at maximum");

   m=match();var tough=mob(m,0,"test_hardened");m.damage(tough,1,null);
   ok(tough.attack==3,"Surviving damage increases Attack");m.damage(tough,1,null);ok(tough.attack==3,"Only once in same turn");
   m.turn++;m.damage(tough,1,null);ok(tough.attack==4,"Can trigger on next player's turn");
   m.turn++;tough.invulnerable=true;m.damage(tough,5,null);ok(tough.attack==4,"Protected damage gives no buff");
   tough.invulnerable=false;m.damage(tough,0,null);ok(tough.attack==4,"Zero damage gives no buff");
   m.damage(tough,100,null);ok(tough.attack==4,"Lethal damage gives no buff");

   m=match();var school=mob(m,0,"test_school");var enemySchool=mob(m,1,"test_school");
   m.buff(school,2,0);play(m,"cod");play(m,"salmon");
   ok(school.attack==6&&school.temporaryAttack==2,"Each played Aquatic gives temporary Attack");
   ok(enemySchool.attack==2,"Enemy Schooling does not trigger");
   mob(m,0,"cod");play(m,"zombie");ok(school.attack==6,"Generated summons and non-Aquatics do not trigger");
   var glow=preview("cod",false,subject("test_school",true,6,6,false,false,false,false));
   ok(glow!=null&&!glow.harmful(),"Aquatic hover previews Schooling");
   m.endTurn(0);ok(school.attack==4&&school.temporaryAttack==0,"Temporary gain expires without removing permanent buffs");
   m.current=0;m.player(0).xp=10;play(m,"cod");ok(school.attack==5,"Schooling can trigger again");
   m.bounce(school);play(m,"test_school");var returned=m.player(0).board.getLast();
   ok(returned.attack==2&&returned.temporaryAttack==0,"Bounce replays with fresh stats");

   m=match();var victim=mob(m,0,"zombie");victim.frozen=true;victim.withered=true;victim.totem=true;victim.invulnerable=true;
   play(m,"test_cleanse",Target.mob(0,victim.id));
   ok(!victim.frozen&&!victim.withered&&!victim.totem&&!victim.invulnerable,"Cleanse clears Frozen, Withered, Totem and invulnerability together");
   victim.ready=false;victim.skippedAttackFromFreeze=true;play(m,"test_cleanse",Target.mob(0,victim.id));
   ok(victim.ready,"Cleanse restores an attack skipped solely because of Frozen");
   victim.ready=false;victim.skippedAttackFromFreeze=true;victim.attackedThisTurn=true;play(m,"test_cleanse",Target.mob(0,victim.id));
   ok(!victim.ready,"Cleanse never grants a second attack");
   ok(preview("test_cleanse",false,subject("zombie",true,2,3,false,true,false,false))!=null,"Cleanse preview sees Frozen");
   ok(preview("test_cleanse",false,subject("zombie",true,2,3,false,false,false,false))==null,"No false cleanse benefit");

   m=match();var block=mob(m,0,"test_drop_block");block.totem=true;block.invulnerable=true;
   var enemyBlock=mob(m,1,"wheat");var body=mob(m,0,"zombie");m.player(0).xp=2;m.events.clear();
   m.dismantle(0,enemyBlock.id);m.dismantle(0,body.id);m.dismantle(1,enemyBlock.id);
   ok(m.player(0).xp==2&&m.player(0).board.size()==2,"Wrong side/non-block/off-turn rejected");
   m.player(0).xp=0;m.dismantle(0,block.id);ok(m.player(0).board.contains(block),"No XP means no dismantling");
   m.player(0).xp=2;int handBefore=m.player(0).hand.size();m.dismantle(0,block.id);
   ok(m.player(0).xp==2&&m.player(0).board.contains(block),"Retired Dismantle never spends XP or removes a block");
   ok(m.player(0).hand.size()==handBefore&&m.events.isEmpty(),"Retired Dismantle fires no effects or events");
   m=match();mob(m,0,"test_spent_block");for(int i=0;i<7;i++)mob(m,0,"zombie");
   m.player(0).hand.add(new CardRef(id("cow"),false));m.player(0).xp=4;
   MatchAI.step(m,0);ok(m.player(0).board.size()==8&&!m.player(0).hand.isEmpty(),"AI cannot dismantle blocks to free a slot");
  } finally {CardCatalog.install(origin);}

  var selected=Cards.collectible().stream().filter(c->DeckCardFilter.matches(Cards.get(c),3,CardRarity.RARE,Set.of(Keyword.SPAWN),Set.of(MobType.UNDEAD))).toList();
  ok(selected.equals(List.of(id("drowned"))),"Cost, rarity, keyword and type stack");
  ok(!DeckCardFilter.matches(Cards.get(id("drowned")),3,CardRarity.RARE,Set.of(Keyword.SHIELD),Set.of(MobType.UNDEAD)),"Keyword exclusion stacks");
  ok(DeckCardFilter.matches(Cards.get(id("drowned")),3,null,Set.of(Keyword.SHIELD,Keyword.SPAWN),Set.of(MobType.ANIMAL,MobType.UNDEAD)),"OR within selected group");
  ok(!DeckCardFilter.matches(Cards.get(id("rotten_flesh")),-1,null,Set.of(),Set.of(MobType.UNDEAD)),"Items excluded by mob type");
  ok(DeckCardFilter.matches(Cards.get(id("ender_dragon")),7,null,Set.of(),Set.of()),"7+ XP filter");
  ok(DeckCardFilter.matches(Cards.get(id("drowned")),3,CardRarity.RARE,Set.of(Keyword.SPAWN),Set.of(MobType.UNDEAD),Set.of(DeckCardFilter.CardType.MOB)),"All five filter groups stack");
  ok(!DeckCardFilter.matches(Cards.get(id("drowned")),3,CardRarity.RARE,Set.of(Keyword.SPAWN),Set.of(MobType.UNDEAD),Set.of(DeckCardFilter.CardType.BLOCK)),"Card type exclusion stacks");
  for(var cardId:Cards.collectible()){
   var definition=Cards.get(cardId);var category=DeckCardFilter.cardType(definition);
   for(var candidate:DeckCardFilter.CardType.values())
    ok(DeckCardFilter.matches(definition,-1,null,Set.of(),Set.of(),Set.of(candidate))==(candidate==category),"Each card belongs to exactly one card type");
   if(definition.keywords().contains(Keyword.BLOCK))ok(category==DeckCardFilter.CardType.BLOCK,"Legacy Block marker resolves to Block type");
   ok(DeckCardFilter.matches(definition,-1,null,Set.of(),Set.of(),Set.of()),"Cleared filters include every card");
   ok(DeckCardFilter.matches(definition,-1,null,Set.of(),Set.of(),Set.of(DeckCardFilter.CardType.MOB,DeckCardFilter.CardType.BLOCK))
      ==(definition.kind()==CardKind.MOB),"Card types use OR within the group");
  }

  var progress=craftbound.mastery.MasteryProgress.EMPTY;
  progress=progress.earn(CardClass.FARMER,100);
  ok(progress.level(CardClass.FARMER)==2&&progress.level(CardClass.FIGHTER)==1,"Mastery isolated per class");
  ok(progress.earn(CardClass.FARMER,-1).equals(progress),"Negative Mastery ignored");
  ok(craftbound.mastery.MasteryProgress.matchPoints(true,true)<craftbound.mastery.MasteryProgress.matchPoints(true,false)&&craftbound.mastery.MasteryProgress.matchPoints(false,true)<craftbound.mastery.MasteryProgress.matchPoints(false,false)&&craftbound.mastery.MasteryProgress.matchPoints(false,true)>0,"Practice pays less Mastery than a duel");
  ok(progress.earn(CardClass.FARMER,Integer.MAX_VALUE).level(CardClass.FARMER)==30,"Mastery level cap");
  var encoded=craftbound.mastery.MasteryProgress.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,progress).getOrThrow();
  ok(craftbound.mastery.MasteryProgress.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,encoded).getOrThrow().equals(progress),"Persistent Mastery codec round trip");
  var buf=io.netty.buffer.Unpooled.buffer();
  try {craftbound.mastery.MasteryProgress.STREAM_CODEC.encode(buf,progress);
   ok(craftbound.mastery.MasteryProgress.STREAM_CODEC.decode(buf).equals(progress),"Mastery network round trip");
  }finally{buf.release();}
 }

 static void alchemistAndThorns() {
  for(String attackerName:List.of("zombie","skeleton","piglin_brute")){
   var m=match();var thorns=mob(m,1,"sweet_berry_bush");var attacker=mob(m,0,attackerName);
   attacker.ready=true;int initial=attacker.health;
   m.attack(0,attacker.id,Target.mob(1,thorns.id));
   ok(thorns.attack==0&&m.attackOf(thorns)==0,"Thorns block retains 0 Attack");
   ok(attacker.health==initial-(attackerName.equals("piglin_brute")?0:1),"Block Thorns hits normal/Ranged attackers and respects armour");
  }
  var m=match();var thorns=mob(m,1,"sweet_berry_bush");play(m,"iron_sword");
  m.attack(0,Match.HERO,Target.mob(1,thorns.id));ok(m.player(0).health==Match.START_HEALTH-1,"Block Thorns hits attacking hero");
  m=match();thorns=mob(m,1,"sweet_berry_bush");play(m,"bow");
  m.attack(0,Match.HERO,Target.mob(1,thorns.id));ok(m.player(0).health==Match.START_HEALTH-1,"Ranged tool does not bypass Thorns");
  m=match();thorns=mob(m,1,"sweet_berry_bush");play(m,"arrow",Target.mob(1,thorns.id));
  ok(m.player(0).health==Match.START_HEALTH,"Spell damage does not count as an attack");

  var deck=Collections.nCopies(30,new CardRef(Cards.ZOMBIE,false));
  m=new Match(new MatchPlayer("Alchemist",UUID.randomUUID(),CardClass.ALCHEMIST,id("classic"),deck),
    new MatchPlayer("Enemy",null,CardClass.FIGHTER,id("classic"),deck),RandomSource.create(1));
  m.phase=Match.Phase.PLAYING;
  int side=m.player(0).heroClass==CardClass.ALCHEMIST?0:1;m.current=side;m.player(side).xp=10;m.player(side).hand.clear();
  ok(HeroPowers.of(m.player(side))==HeroPowers.Power.THROW_POTION,"Alchemist power");
  var enemy=mob(m,1-side,"zombie");m.usePower(side,Target.mob(1-side,enemy.id));
  ok(enemy.health==2&&m.player(side).xp==8,"Throw Potion costs 2 and deals 1");
  m.usePower(side,Target.hero(1-side));ok(m.player(side).xp==8,"Potion once per turn");
  m.player(side).powerUsed=false;for(int n=0;n<10;n++)m.player(side).hand.add(new CardRef(Cards.ZOMBIE,false));
  ok(m.whyCantUsePower(side)==null,"Throw Potion works with a full hand");

  m=match();var a=mob(m,1,"zombie");var b=mob(m,1,"furnace");var friendly=mob(m,0,"zombie");
  play(m,"splash_potion_of_harming");ok(a.health==1&&b.health==1&&friendly.health==3,"Splash damage hits all enemy board cards only");
  m=match();a=mob(m,1,"zombie");play(m,"potion_of_slowness",Target.mob(1,a.id));
  ok(a.frozen&&m.player(0).hand.size()==1,"Slowness freezes and replaces itself");
  m=match();a=mob(m,1,"ravager");play(m,"potion_of_decay",Target.mob(1,a.id));
  ok(a.withered&&a.health==Cards.get(id("ravager")).health()-1,"Decay applies Withered and one damage");
  m=match();play(m,"witch");ok(hand(m,"minor_harming_potion")==2,"Witch provides two potions");
  m=match();mob(m,0,"brewing_stand");CardEffects.onEndTurn(m,0);ok(hand(m,"minor_harming_potion")==1,"Brewing Stand produces a potion");
  m=match();play(m,"blaze_rod");m.attack(0,Match.HERO,Target.hero(1));
  ok(hand(m,"minor_harming_potion")==1&&m.player(0).tool.uses==2,"Blaze Rod brews after an attack");
  play(m,"flame");ok(m.player(0).tool.attack==2&&m.player(0).tool.uses==3,"Alchemist class enchantment upgrades rod");
  ok(Cards.collectible().stream().filter(id->Cards.get(id).cardClass()==CardClass.ALCHEMIST).count()==8,"Eight Alchemist starters");
  ok(!Cards.collectible().contains(Cards.MINOR_HARMING_POTION),"Power token excluded from boosters");
  var progress=craftbound.mastery.MasteryProgress.EMPTY.earn(CardClass.ALCHEMIST,100);
  ok(progress.level(CardClass.ALCHEMIST)==2&&progress.level(CardClass.FISHERMAN)==1,"Alchemist Mastery independent");
  var oldSave=JsonParser.parseString("{\"fighter\":100,\"builder\":0,\"farmer\":0,\"fisherman\":0}");
  ok(craftbound.mastery.MasteryProgress.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,oldSave).getOrThrow().level(CardClass.ALCHEMIST)==1,"Old Mastery data defaults new class to level 1");
 }

 static void chosenCharacters() throws Exception {
  var original=CardCatalog.current();
  var root=JsonParser.parseString(original.json()).getAsJsonObject();
  var cards=root.getAsJsonArray("cards");
  var c=cards.asList().stream().map(JsonElement::getAsJsonObject).filter(x->x.get("id").getAsString().equals("craftbound:rotten_flesh")).findFirst().orElseThrow().deepCopy();
  c.addProperty("id","craftbound:test_porkchop");c.addProperty("name","Test Porkchop");c.addProperty("autoText",true);
  var e=c.getAsJsonArray("effects").get(0).getAsJsonObject();
  e.addProperty("target","CHOSEN_CHARACTER");e.addProperty("boardKind","MOB");e.addProperty("amount",4);
  cards.add(c);
  CardCatalog.install(CardCatalog.parse(root.toString()));
  try {
   ok(CardEffects.targetKind(id("test_porkchop"))==Target.Kind.ANY_CHARACTER,"Mixed target kind");
   for(int side=0;side<2;side++){
    var m=match();m.player(side).health=27;
    var body=mob(m,side,"ravager");body.health=1;var block=mob(m,side,"furnace");
    var targets=m.validTargets(0,Target.Kind.ANY_CHARACTER,id("test_porkchop"));
    ok(targets.contains(Target.hero(side))&&targets.contains(Target.mob(side,body.id)),"Either side's mob and hero legal");
    ok(!targets.contains(Target.mob(side,block.id)),"Mob-only food excludes blocks");
    play(m,"test_porkchop",Target.hero(side));ok(m.player(side).health==30,"Either hero heals up to cap");
    play(m,"test_porkchop",Target.mob(side,body.id));ok(body.health==5,"Either mob heals");
    int xp=m.player(0).xp;m.player(0).hand.add(new CardRef(id("test_porkchop"),false));
    m.playCard(0,m.player(0).hand.size()-1,Target.mob(side,block.id));
    ok(m.player(0).xp==xp&&hand(m,"test_porkchop")==1,"Invalid food target spends nothing");
   }
   var source=new EffectPreview.Source(id("test_porkchop"),0,-1,false,0);
   ok(EffectPreview.forTarget(source,EffectPreview.Subject.hero(1,20))!=null,"Enemy hero healing preview");
   ok(EffectPreview.forTarget(source,EffectPreview.Subject.hero(0,30))==null,"No preview at full health");
   var m=match();m.player(0).health=10;m.player(1).health=5;mob(m,1,"ravager");
   var choose=MatchAI.class.getDeclaredMethod("chooseTarget",Match.class,int.class,CardRef.class);choose.setAccessible(true);
   ok(choose.invoke(null,m,0,new CardRef(id("test_porkchop"),false)).equals(Target.hero(0)),"AI heals friendly wounded hero");
   var worth=MatchAI.class.getDeclaredMethod("worthPlaying",Match.class,int.class,CardRef.class);worth.setAccessible(true);
   m.player(0).health=30;
   ok(!(boolean)worth.invoke(null,m,0,new CardRef(id("test_porkchop"),false)),"AI does not waste food healing enemy");

   var uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);var unsafe=(sun.misc.Unsafe)uf.get(null);
   var screenType=Class.forName("craftbound.client.match.MatchScreen");
   var stateType=Class.forName("craftbound.client.match.MatchState");
   var handType=Class.forName("craftbound.client.match.MatchState$HandCard");
   var spotType=Class.forName("craftbound.client.match.MatchScreen$Spot");
   var spotCtor=spotType.getDeclaredConstructors()[0];spotCtor.setAccessible(true);
   var parse=stateType.getDeclaredMethod("parse",JsonObject.class,List.class);parse.setAccessible(true);
   var screen=unsafe.allocateInstance(screenType);var shown=screenType.getDeclaredField("shown");shown.setAccessible(true);
   m.player(0).hand.add(new CardRef(id("test_porkchop"),false));
   String snapshot=MatchView.build(m,0,m.events.size());ok(snapshot.contains("ANY_CHARACTER"),"Mixed targeting sent to client");
   shown.set(screen,parse.invoke(null,JsonParser.parseString(snapshot).getAsJsonObject(),new ArrayList<>()));
   var hand=unsafe.allocateInstance(handType);var field=handType.getDeclaredField("id");field.setAccessible(true);field.set(hand,id("test_porkchop"));
   field=handType.getDeclaredField("target");field.setAccessible(true);field.set(hand,"ANY_CHARACTER");
   var valid=screenType.getDeclaredMethod("isValidTargetFor",handType,spotType);valid.setAccessible(true);
   for(int side=0;side<2;side++)ok((boolean)valid.invoke(screen,hand,spotCtor.newInstance(0,0,30,40,side,Match.HERO,-1)),"Client can target either hero");
   e.addProperty("action","DAMAGE");CardCatalog.install(CardCatalog.parse(root.toString()));
   m=match();play(m,"test_porkchop",Target.hero(1));ok(m.player(1).health==26,"Mixed target also supports damage");
   e.addProperty("boardKind","BOTH");CardCatalog.install(CardCatalog.parse(root.toString()));
   m=match();var block=mob(m,0,"furnace");ok(m.validTargets(0,Target.Kind.ANY_CHARACTER,id("test_porkchop")).contains(Target.mob(0,block.id)),"Both board kinds can include blocks");
   e.addProperty("action","WITHER");boolean rejected=false;
   try{CardCatalog.parse(root.toString());}catch(IllegalArgumentException expected){rejected=true;}
   ok(rejected,"Mob-only statuses cannot target heroes");
  } finally {CardCatalog.install(original);}
 }

 static void deckXpCurve() {
  var deck=DeckContents.EMPTY.withClass(CardClass.FIGHTER).add(id("zombie"),false,2).add(id("zombie"),true,1).add(id("ravager"),false,1);
  var curve=DeckXpCurve.of(deck);
  ok(curve.total()==4,"XP curve counts normal and foil copies");
  ok(curve.counts()[Cards.get(id("zombie")).cost()]==3,"XP bin counts duplicates");
  ok(curve.counts()[7]==1,"Expensive cards grouped at 7+");
  ok(curve.average()==(3*Cards.get(id("zombie")).cost()+Cards.get(id("ravager")).cost())/4.0,"Average uses every copy's actual cost");
  curve=DeckXpCurve.of(deck.add(id("zombie"),true,-1));
  ok(curve.total()==3&&curve.counts()[Cards.get(id("zombie")).cost()]==2,"Removing card updates curve");
  ok(DeckXpCurve.of(DeckContents.EMPTY).total()==0&&DeckXpCurve.of(DeckContents.EMPTY).average()==0,"Empty curve safe");
 }
 static void minerRules() throws Exception {
  ok(Cards.collectible().stream().filter(id->Cards.get(id).cardClass()==CardClass.MINER).count()==10,"Ten Miner starter collectibles");
  var m=match();var ore=mob(m,0,"coal_ore");var enemyOre=mob(m,1,"iron_ore");var block=mob(m,0,"furnace");var body=mob(m,0,"zombie");
  ok(ore.attack==0&&ore.has(Keyword.BLOCK)&&ore.definition().mobType()==MobType.ORE,"Ore is a zero-attack block");
  var legal=m.validTargets(0,CardEffects.targetKind(id("mine")),id("mine"));
  ok(legal.equals(List.of(Target.mob(0,ore.id))),"Mine only targets friendly Ore blocks");
  m.player(0).hand.add(new CardRef(id("mine"),false));int before=m.player(0).xp;
  m.playCard(0,0,Target.mob(1,enemyOre.id));ok(m.player(0).xp==before&&m.player(0).hand.size()==1,"Cannot mine enemy Ore");
  m.playCard(0,0,Target.mob(0,block.id));ok(m.player(0).hand.size()==1,"Cannot mine ordinary block");
  m.playCard(0,0,Target.mob(0,body.id));ok(m.player(0).hand.size()==1,"Cannot mine mob");
  m.playCard(0,0,Target.hero(0));ok(m.player(0).hand.size()==1,"Cannot mine hero");
  ore.invulnerable=true;ore.totem=true;
  m.playCard(0,0,Target.mob(0,ore.id));
  ok(m.player(0).minion(ore.id)==null&&hand(m,"coal_ore")==0,"Mining consumes Ore without bouncing or Totem resurrection");
  ok(hand(m,"mined_coal")==2&&hand(m,"zombie")==1,"Mining pays Drop and Mine draws a card");
  ok(m.player(0).xp==before-1,"Mine spends exactly its XP");
  m=match();ore=mob(m,0,"coal_ore");m.dismantle(0,ore.id);
  ok(hand(m,"mined_coal")==0,"Dismantling Ore forfeits materials");
  m=match();ore=mob(m,1,"coal_ore");m.damage(ore,100,null);m.resolveDeaths();
  ok(m.player(1).hand.stream().filter(c->c.id().equals(id("mined_coal"))).count()==2,"Enemy destruction pays resources to Ore owner");
  m=match();mob(m,0,"iron_ore");play(m,"stone_pickaxe");m.attack(0,Match.HERO,Target.hero(1));
  ok(hand(m,"mined_iron_ingot")==1&&m.player(0).board.isEmpty(),"Pickaxe mines after attacking");
  play(m,"mined_iron_ingot");ok(m.player(0).tool.attack==2&&m.player(0).tool.uses==3,"Iron upgrades tool without refreshing its attack");
  ok(m.player(0).heroAttacked,"Iron cannot give second hero attack");
  m=match();mob(m,0,"diamond_ore");play(m,"cave_miner",Target.mob(0,m.player(0).board.getFirst().id));
  ok(hand(m,"mined_diamond")==1&&m.player(0).board.size()==1,"Cave Miner mines chosen Ore on Spawn");
  m=match();play(m,"prospector");ok(hand(m,"coal_seam")==2,"Prospector creates two playable seams");
  m=match();play(m,"deep_delver");ok(m.player(0).board.stream().filter(b->b.card.id().equals(id("iron_ore"))).count()==2,"Deep Delver places two Ore blocks");
  m=match();m.player(0).xp=3;m.player(0).hand.add(new CardRef(id("mined_coal"),false));m.playCard(0,0,Target.NONE);
  ok(m.player(0).xp==4,"Coal adds one current-turn XP");
  var worth=MatchAI.class.getDeclaredMethod("worthPlaying",Match.class,int.class,CardRef.class);worth.setAccessible(true);
  m.player(0).hand.add(new CardRef(id("mined_coal"),false));ok(!(boolean)worth.invoke(null,m,0,new CardRef(id("mined_coal"),false)),"AI banks Coal without a purchase");
  m.player(0).xp=4;m.player(0).hand.add(new CardRef(id("mined_coal"),false));m.player(0).hand.add(new CardRef(id("deep_delver"),false));
  ok((boolean)worth.invoke(null,m,0,new CardRef(id("mined_coal"),false)),"AI can combine Coal to afford larger cards");

  var deck=Collections.nCopies(30,new CardRef(Cards.ZOMBIE,false));
  m=new Match(new MatchPlayer("Miner",UUID.randomUUID(),CardClass.MINER,id("classic"),deck),
      new MatchPlayer("Enemy",null,CardClass.FIGHTER,id("classic"),deck),RandomSource.create(4));
  int side=m.player(0).heroClass==CardClass.MINER?0:1;m.phase=Match.Phase.PLAYING;m.current=side;m.player(side).xp=10;m.player(side).hand.clear();
  m.usePower(side);ok(m.player(side).xp==8&&m.player(side).hand.getFirst().id().equals(id("iron_ore_token")),"Prospect costs two and gives Iron Ore");
  m.usePower(side);ok(m.player(side).hand.size()==1,"Prospect once per turn");
  m.player(side).powerUsed=false;while(m.player(side).hand.size()<Match.MAX_HAND)m.player(side).hand.add(new CardRef(Cards.ZOMBIE,false));
  ok(m.whyCantUsePower(side)!=null,"Prospect respects full hand");

  var progress=craftbound.mastery.MasteryProgress.EMPTY.earn(CardClass.MINER,100);
  ok(progress.level(CardClass.MINER)==2&&progress.level(CardClass.ALCHEMIST)==1,"Miner mastery independent");
  var oldSave=JsonParser.parseString("{\"fighter\":100,\"alchemist\":100}");
  ok(craftbound.mastery.MasteryProgress.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,oldSave).getOrThrow().level(CardClass.MINER)==1,"Older mastery saves default Miner to level one");
  var source=new EffectPreview.Source(id("mine"),0,-1,false,0);
  var preview=EffectPreview.forTarget(source,previewMob("coal_ore",0,3,2,2));
  ok(preview!=null&&!preview.harmful()&&preview.possible(),"Mining previews friendly Ore");
  ok(EffectPreview.forTarget(source,previewMob("coal_ore",1,3,2,2))==null,"Mining does not highlight enemy Ore");

  for(String field:List.of("target","boardKind","type","trigger")){
   var root=JsonParser.parseString(craftbound.test.TestCatalogue.full().json()).getAsJsonObject();
   var e=root.getAsJsonArray("cards").asList().stream().map(JsonElement::getAsJsonObject)
       .filter(c->c.get("id").getAsString().equals("craftbound:mine")).findFirst().orElseThrow().getAsJsonArray("effects").get(0).getAsJsonObject();
   e.addProperty(field,switch(field){case "target"->"CHOSEN_ENEMY";case "boardKind"->"MOB";case "type"->"CROP";default->"DROP";});
   boolean rejected=false;try{CardCatalog.parse(root.toString());}catch(IllegalArgumentException expected){rejected=true;}
   ok(rejected,"Invalid Mine "+field+" rejected");
  }
 }
 static void heroStatusesAndNewClasses() throws Exception {
  var original=CardCatalog.current();
  var root=JsonParser.parseString(original.json()).getAsJsonObject();var entries=root.getAsJsonArray("cards");
  var base=entries.asList().stream().map(JsonElement::getAsJsonObject).filter(c->c.get("id").getAsString().equals("craftbound:hearty_stew")).findFirst().orElseThrow();
  var freeze=base.deepCopy();freeze.addProperty("id","craftbound:test_hero_freeze");
  freeze.getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("action","FREEZE");entries.add(freeze);
  var milk=base.deepCopy();milk.addProperty("id","craftbound:test_hero_milk");
  var heal=milk.getAsJsonArray("effects").get(0).getAsJsonObject().deepCopy();heal.addProperty("amount",2);
  milk.getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("action","CLEANSE");milk.getAsJsonArray("effects").add(heal);entries.add(milk);
  CardCatalog.install(CardCatalog.parse(root.toString()));
  try {
   ok(Cards.get(id("test_hero_milk")).keywords().contains(Keyword.CLEANSE),"Cleanse exposed to keyword tooltips and filters");
   String text=CardCatalog.description(CardCatalog.entry(id("test_hero_milk")));
   ok(text.contains("Cleanse")&&!text.contains("Frozen and Withered"),"Cleanse keeps card text short");
   var m=match();play(m,"iron_sword");m.player(0).health=25;
   play(m,"test_hero_freeze",Target.hero(0));ok(m.player(0).frozen&&!m.heroCanAttack(0),"Frozen hero cannot attack");
   int uses=m.player(0).tool.uses,enemyHp=m.player(1).health;
   m.attack(0,Match.HERO,Target.hero(1));ok(m.player(0).tool.uses==uses&&m.player(1).health==enemyHp,"Frozen attack spends no durability and deals no damage");
   play(m,"iron_sword");ok(!m.heroCanAttack(0),"Equipping another tool cannot bypass Frozen");
   play(m,"test_hero_milk",Target.hero(0));ok(!m.player(0).frozen&&m.heroCanAttack(0)&&m.player(0).health==27,"Milk cleanses and heals hero");
   m.attack(0,Match.HERO,Target.hero(1));play(m,"test_hero_freeze",Target.hero(0));play(m,"test_hero_milk",Target.hero(0));
   ok(!m.heroCanAttack(0),"Cleanse cannot reset hero's used attack");
   m=match();m.equip(1,new CardRef(id("iron_sword"),false));play(m,"test_hero_freeze",Target.hero(1));
   m.endTurn(0);ok(m.player(1).skippedAttackFromFreeze&&!m.heroCanAttack(1),"Frozen enemy skips its next weapon attack");
   String snapshot=MatchView.build(m,0,m.events.size());ok(JsonParser.parseString(snapshot).getAsJsonObject().getAsJsonArray("players").get(1).getAsJsonObject().get("frozen").getAsBoolean(),"Hero Frozen reaches client snapshot");
   m.player(1).xp=10;ok(m.whyCantUsePower(1)==null,"Frozen does not block hero powers");
   m.endTurn(1);m.endTurn(0);ok(m.heroCanAttack(1),"Hero thaws after skipped turn");
   m=match();m.equip(1,new CardRef(id("iron_sword"),false));m.player(1).health=20;
   play(m,"test_hero_freeze",Target.hero(1));play(m,"test_hero_milk",Target.hero(1));
   ok(!m.player(1).frozen&&m.player(1).health==22,"Milk can cleanse and heal enemy hero");
   var preview=EffectPreview.forTarget(new EffectPreview.Source(id("test_hero_milk"),0,-1,false,0),EffectPreview.Subject.hero(0,30,true));
   ok(preview!=null&&!preview.harmful(),"Cleanse highlights frozen full-health hero");
   var wither=freeze.deepCopy();wither.addProperty("id","craftbound:test_invalid_hero_wither");wither.getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("action","WITHER");entries.add(wither);
   boolean rejected=false;try{CardCatalog.parse(root.toString());}catch(IllegalArgumentException expected){rejected=true;}ok(rejected,"Withered remains board-only");
  } finally {CardCatalog.install(original);}

  for(CardClass cls:List.of(CardClass.CHEF,CardClass.TAMER)){
   var deck=Collections.nCopies(30,new CardRef(Cards.ZOMBIE,false));
   var m=new Match(new MatchPlayer("Test",UUID.randomUUID(),cls,id("classic"),deck),new MatchPlayer("Enemy",null,CardClass.FIGHTER,id("classic"),deck),RandomSource.create(8));
   int side=m.player(0).heroClass==cls?0:1;m.phase=Match.Phase.PLAYING;m.current=side;m.player(side).xp=10;m.player(side).hand.clear();
   if(cls==CardClass.CHEF){
    m.player(1-side).health=25;var ally=mob(m,side,"ravager");ally.health=1;var enemy=mob(m,1-side,"ravager");enemy.health=1;var block=mob(m,side,"furnace");
    m.usePower(side);ok(m.player(side).xp==10&&!m.player(side).powerUsed,"Feed requires a chosen target");
    block.health=1;m.usePower(side,Target.mob(side,block.id));ok(block.health==3&&m.player(side).xp==8,"Feed heals blocks");
    m.player(side).xp=10;m.player(side).powerUsed=false;
    m.usePower(side,Target.hero(1-side));ok(m.player(1-side).health==27&&m.player(side).xp==8,"Feed heals enemy hero for two");
    m.usePower(side,Target.mob(side,ally.id));ok(ally.health==1,"Feed once per turn");
    m.player(side).powerUsed=false;m.usePower(side,Target.mob(side,ally.id));ok(ally.health==3,"Feed heals friendly mob");
    m.player(side).powerUsed=false;m.usePower(side,Target.mob(1-side,enemy.id));ok(enemy.health==3,"Feed heals enemy mob");
    m.player(side).powerUsed=false;m.player(side).health=29;m.usePower(side,Target.hero(side));ok(m.player(side).health==30,"Feed respects hero cap");
   }else{
    m.usePower(side);ok(m.player(side).xp==8&&m.player(side).board.size()==1,"Call Pup costs two XP");
    var pup=m.player(side).board.getFirst();ok(pup.attack==1&&pup.health==1&&pup.definition().mobType()==MobType.TAMEABLE,"Tamer summons a 1/1 Tameable pup");
    m.usePower(side);ok(m.player(side).board.size()==1,"Call Pup once per turn");
    m.player(side).powerUsed=false;while(m.player(side).board.size()<Match.MAX_MOBS)mob(m,side,"wolf_pup");
    ok(m.whyCantUsePower(side)!=null,"Call Pup respects seven-card board cap");
   }
   ok(Cards.collectible().stream().filter(id->Cards.get(id).cardClass()==cls).count()==4,"Four barebones starters per new class");
   var progress=craftbound.mastery.MasteryProgress.EMPTY.earn(cls,125);
   ok(progress.level(cls)==2&&progress.progress(cls)==25,"New class mastery tracks progress");
  }
  var m=match();var pup=mob(m,0,"wolf_pup");var other=mob(m,0,"zombie");play(m,"pack_call");
  ok(pup.attack==2&&pup.health==2&&other.attack==2,"Pack Call only buffs Tameable mobs");
 }

 static void savedBinderDecks(){
  var collection=BinderContents.fullCollection();
  var deck=new DeckContents(CardClass.FIGHTER,BinderContents.EMPTY,true);
  for(var id:Cards.collectible())if(DeckRules.allowed(id,CardClass.FIGHTER)){
   int count=Math.min(DeckRules.maxCopies(Cards.get(id).rarity()),30-deck.size());
   if(count>0)deck=deck.add(id,false,count);
  }
  ok(deck.size()==30,"Saved deck fixture has 30 cards");
  var first=deck.cards().cards().keySet().iterator().next();
  deck=deck.add(first,false,-1).add(first,true,1);
  var a=new SavedDecks.Entry(UUID.randomUUID().toString(),"First",deck,id("classic"));
  var b=new SavedDecks.Entry(UUID.randomUUID().toString(),"Second",deck,id("classic"));
  var saved=SavedDecks.EMPTY.put(a).put(b);
  ok(saved.entries().size()==2&&saved.find(a.id()).equals(a),"Two independent saved IDs can reuse a deck");
  ok(collection.equals(BinderContents.fullCollection()),"Saving lists never consumes physical cards");
  ok(DeckBuilding.whyCantPlay(a.deck(),collection::count)==null&&DeckBuilding.whyCantPlay(b.deck(),collection::count)==null,"Both lists playable from same collection");
  var traded=collection.add(first,true,-collection.count(first,true));
  ok("match_ui.craftbound.deck_missing".equals(DeckBuilding.whyCantPlay(a.deck(),traded::count)),"Trading enchanted copy blocks first list");
  ok("match_ui.craftbound.deck_missing".equals(DeckBuilding.whyCantPlay(b.deck(),traded::count)),"Trading shared copy blocks second list");
  ok(traded.count(first,false)>0,"Normal copies cannot substitute for missing enchanted");
  var restored=traded.add(first,true,1);
  ok(DeckBuilding.whyCantPlay(a.deck(),restored::count)==null,"Replacing copy restores playability");
  var edited=new SavedDecks.Entry(a.id(),"Renamed",deck.add(first,true,-1),id("classic"));
  var next=saved.put(edited);
  ok(next.find(a.id()).equals(edited)&&next.find(b.id()).equals(b),"Editing selected saved ID preserves another deck");
  ok(saved.find(a.id()).equals(a),"Saved component immutable");
  next=next.remove(a.id());
  ok(next.find(a.id())==null&&next.find(b.id()).equals(b),"Delete list preserves other deck and stable ID");
  ok(collection.equals(BinderContents.fullCollection()),"Edit/delete never returns duplicate physical cards");
  var json=SavedDecks.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,saved).getOrThrow();
  ok(SavedDecks.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow().equals(saved),"Saved deck JSON round trip");
  var buf=io.netty.buffer.Unpooled.buffer();
  try{SavedDecks.STREAM_CODEC.encode(buf,saved);ok(SavedDecks.STREAM_CODEC.decode(buf).equals(saved),"Saved deck component stream round trip");}finally{buf.release();}
  var cap=SavedDecks.EMPTY;
  for(int i=0;i<SavedDecks.LIMIT+3;i++)cap=cap.put(new SavedDecks.Entry(UUID.randomUUID().toString(),"Deck",deck,id("classic")));
  ok(cap.entries().size()==SavedDecks.LIMIT,"Saved deck capacity bounded");
  var kept=cap.entries().get(20);
  cap=cap.remove(cap.entries().getFirst().id());
  ok(cap.find(kept.id()).equals(kept),"Removing earlier deck doesn't retarget later IDs");
  var packet=new craftbound.network.BinderDeckPayload(true,b.id(),craftbound.network.BinderDeckPayload.ADD,first.toString(),true);
  var wire=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
  try{craftbound.network.BinderDeckPayload.CODEC.encode(wire,packet);ok(craftbound.network.BinderDeckPayload.CODEC.decode(wire).equals(packet),"Editor packet retains exact deck and variant");}finally{wire.release();}
  var table=new craftbound.network.TableActionPayload(net.minecraft.core.BlockPos.ZERO,0,5,b.id());
  wire=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
  try{craftbound.network.TableActionPayload.CODEC.encode(wire,table);ok(craftbound.network.TableActionPayload.CODEC.decode(wire).equals(table),"Table packet retains binder slot and deck ID");}finally{wire.release();}
  ok(new craftbound.network.TableActionPayload(net.minecraft.core.BlockPos.ZERO,0,5).deckId().isEmpty(),"Legacy table selection stays supported");
  var mixed=new DeckContents(CardClass.FIGHTER,BinderContents.EMPTY,true).add(id("zombie"),false,1).add(id("zombie"),true,1);
  ok("deck_ui.craftbound.max_copies".equals(DeckBuilding.whyNot(mixed,id("zombie"))),"Normal and enchanted still share deck copy limit");
 }

 static void feedReleaseAndTotemAI() throws Exception {
  var aiPlay=MatchAI.class.getDeclaredMethod("playBestCard",Match.class,int.class);aiPlay.setAccessible(true);
  var choose=MatchAI.class.getDeclaredMethod("chooseTarget",Match.class,int.class,CardRef.class);choose.setAccessible(true);
  for(int side=0;side<2;side++){
   var m=match();m.current=side;m.player(side).xp=10;
   var strong=mob(m,side,"ravager");strong.totem=true;
   var block=mob(m,side,"furnace");
   m.player(side).hand.add(new CardRef(id("totem_of_undying"),false));
   m.player(side).hand.add(new CardRef(id("totem_of_undying"),true));
   ok(choose.invoke(null,m,side,m.player(side).hand.getFirst()).equals(Target.mob(side,block.id)),"Totem AI skips protected strongest target");
   ok((boolean)aiPlay.invoke(null,m,side)&&block.totem,"AI protects an unprotected legal block");
   int xp=m.player(side).xp;
   ok(!(boolean)aiPlay.invoke(null,m,side)&&m.player(side).hand.size()==1&&m.player(side).xp==xp,"All protected: enchanted Totem stays in hand without spending XP");
   ok(choose.invoke(null,m,side,m.player(side).hand.getFirst()).equals(Target.NONE),"No fallback to protected target");
   strong.totem=false;
   ok((boolean)aiPlay.invoke(null,m,side)&&strong.totem,"Consumed protection can be applied again");
  }

  var uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);var unsafe=(sun.misc.Unsafe)uf.get(null);
  var screenType=Class.forName("craftbound.client.match.MatchScreen");
  var stateType=Class.forName("craftbound.client.match.MatchState");
  var spotType=Class.forName("craftbound.client.match.MatchScreen$Spot");
  var ctor=spotType.getDeclaredConstructors()[0];ctor.setAccessible(true);
  var parse=stateType.getDeclaredMethod("parse",JsonObject.class,List.class);parse.setAccessible(true);
  var release=screenType.getDeclaredMethod("releaseFeed",int.class,double.class,double.class);release.setAccessible(true);
  var shown=screenType.getDeclaredField("shown");shown.setAccessible(true);
  var aim=screenType.getDeclaredField("aimingPower");aim.setAccessible(true);
  var menu=screenType.getDeclaredField("menuOpen");menu.setAccessible(true);
  var screen=unsafe.allocateInstance(screenType);
  var deck=Collections.nCopies(30,new CardRef(id("zombie"),false));
  var m=new Match(new MatchPlayer("Chef",UUID.randomUUID(),CardClass.CHEF,id("classic"),deck),
    new MatchPlayer("Enemy",null,CardClass.CHEF,id("classic"),deck),RandomSource.create(9));
  m.phase=Match.Phase.PLAYING;m.current=0;m.player(0).xp=10;
  var ally=mob(m,0,"zombie");var enemy=mob(m,1,"zombie");var block=mob(m,0,"furnace");
  shown.set(screen,parse.invoke(null,JsonParser.parseString(MatchView.build(m,0,m.events.size())).getAsJsonObject(),new ArrayList<>()));
  var updates=screenType.getDeclaredField("updates");updates.setAccessible(true);updates.set(screen,new ArrayDeque<>());
  Object allySpot=ctor.newInstance(10,10,20,20,0,ally.id,-1);
  Object enemySpot=ctor.newInstance(40,10,20,20,1,enemy.id,-1);
  Object blockSpot=ctor.newInstance(70,10,20,20,0,block.id,-1);
  var spots=screenType.getDeclaredField("mobSpots");spots.setAccessible(true);spots.set(screen,List.of(allySpot,enemySpot,blockSpot));
  Object heroArray=java.lang.reflect.Array.newInstance(spotType,2);
  Object meHero=ctor.newInstance(10,60,20,20,0,Match.HERO,-1),enemyHero=ctor.newInstance(40,60,20,20,1,Match.HERO,-1);
  java.lang.reflect.Array.set(heroArray,0,meHero);java.lang.reflect.Array.set(heroArray,1,enemyHero);
  var heroes=screenType.getDeclaredField("heroSpots");heroes.setAccessible(true);heroes.set(screen,heroArray);
  int left=com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
  Object[] expected={allySpot,enemySpot,meHero,enemyHero,blockSpot};
  double[][] points={{20,20},{50,20},{20,70},{50,70},{80,20}};
  for(int i=0;i<expected.length;i++){
   aim.set(screen,true);
   ok(release.invoke(screen,left,points[i][0],points[i][1]).equals(expected[i]),"Feed release selects either side's mob/hero");
   ok(!(boolean)aim.get(screen),"Feed gesture clears on successful release");
   ok(release.invoke(screen,left,points[i][0],points[i][1])==null,"Feed cannot fire twice from one gesture");
  }
  for(double[] point:new double[][]{{150,150}}){
   aim.set(screen,true);ok(release.invoke(screen,left,point[0],point[1])==null&&!(boolean)aim.get(screen),"Empty drop cancels Feed");
  }
  aim.set(screen,true);menu.set(screen,true);
  ok(release.invoke(screen,left,20d,20d)==null&&!(boolean)aim.get(screen),"Lost input authority cancels Feed");
  menu.set(screen,false);aim.set(screen,true);
  ok(release.invoke(screen,com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT,20d,20d)==null,"Non-left release never heals");
  ok(m.player(0).xp==10&&!m.player(0).powerUsed,"Choosing/rejecting targets alone spends no XP");

  // Excavate's local choice/cancellation never sends an action until a real choice is committed.
  m=new Match(new MatchPlayer("Miner",UUID.randomUUID(),CardClass.MINER,id("classic"),deck),
      new MatchPlayer("Enemy",null,CardClass.MINER,id("classic"),deck),RandomSource.create(7));
  m.phase=Match.Phase.PLAYING;m.current=0;m.player(0).xp=10;
  var ore=mob(m,0,"coal_ore");var enemyOre=mob(m,1,"coal_ore");block=mob(m,0,"furnace");
  shown.set(screen,parse.invoke(null,JsonParser.parseString(MatchView.build(m,0,m.events.size())).getAsJsonObject(),new ArrayList<>()));
  aim.set(screen,false);menu.set(screen,false);
  var coin=screenType.getDeclaredField("coinStart");coin.setAccessible(true);coin.setLong(screen,-1);
  var choosing=screenType.getDeclaredField("choosingExcavate");choosing.setAccessible(true);
  var mining=screenType.getDeclaredField("aimingExcavate");mining.setAccessible(true);
  var validOre=screenType.getDeclaredMethod("validExcavateSpot",spotType);validOre.setAccessible(true);
  Object oreSpot=ctor.newInstance(10,10,20,20,0,ore.id,-1);
  ok((boolean)validOre.invoke(screen,oreSpot),"Excavate highlights friendly Ore");
  ok(!(boolean)validOre.invoke(screen,ctor.newInstance(40,10,20,20,1,enemyOre.id,-1)),"Excavate excludes enemy Ore");
  ok(!(boolean)validOre.invoke(screen,ctor.newInstance(70,10,20,20,0,block.id,-1)),"Excavate excludes ordinary blocks");
  ok(!(boolean)validOre.invoke(screen,meHero),"Excavate excludes heroes");
  var chooseButton=screenType.getDeclaredField("excavateMineButton");chooseButton.setAccessible(true);chooseButton.set(screen,new int[]{10,10,80,20});
  var cancelButton=screenType.getDeclaredField("excavateCancelButton");cancelButton.setAccessible(true);cancelButton.set(screen,new int[]{10,100,80,20});
  var key=screenType.getDeclaredMethod("keyPressed",net.minecraft.client.input.KeyEvent.class);
  var click=screenType.getDeclaredMethod("mouseClicked",net.minecraft.client.input.MouseButtonEvent.class,boolean.class);
  var escape=new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE,0,0);
  choosing.set(screen,true);key.invoke(screen,escape);
  ok(!(boolean)choosing.get(screen)&&!(boolean)menu.get(screen),"Escape closes choices without opening menu");
  choosing.set(screen,true);
  click.invoke(screen,new net.minecraft.client.input.MouseButtonEvent(20,20,new net.minecraft.client.input.MouseButtonInfo(left,0)),false);
  ok(!(boolean)choosing.get(screen)&&(boolean)mining.get(screen),"Mine choice enters targeting before spending XP");
  key.invoke(screen,escape);
  ok(!(boolean)mining.get(screen),"Escape cancels Ore targeting");
  mining.set(screen,true);
  click.invoke(screen,new net.minecraft.client.input.MouseButtonEvent(150,150,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT,0)),false);
  ok(!(boolean)mining.get(screen),"Right-click cancels Ore targeting");
  choosing.set(screen,true);
  click.invoke(screen,new net.minecraft.client.input.MouseButtonEvent(20,110,new net.minecraft.client.input.MouseButtonInfo(left,0)),false);
  ok(!(boolean)choosing.get(screen),"Cancel button dismisses choices");
  ok(m.player(0).xp==10&&!m.player(0).powerUsed,"Excavate choice/cancellation spends no XP");

 }

 static void aiHealingFreezeAndIcons() throws Exception {
  var worth=MatchAI.class.getDeclaredMethod("worthPlaying",Match.class,int.class,CardRef.class);worth.setAccessible(true);
  var choose=MatchAI.class.getDeclaredMethod("chooseTarget",Match.class,int.class,CardRef.class);choose.setAccessible(true);
  var original=CardCatalog.current();
  var root=JsonParser.parseString(original.json()).getAsJsonObject();var cards=root.getAsJsonArray("cards");
  var heal=cards.asList().stream().map(JsonElement::getAsJsonObject).filter(c->c.get("id").getAsString().equals("craftbound:hearty_stew")).findFirst().orElseThrow().deepCopy();
  heal.addProperty("id","craftbound:test_mob_heal");
  heal.getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("target","CHOSEN_FRIENDLY");cards.add(heal);
  var freeze=cards.asList().stream().map(JsonElement::getAsJsonObject).filter(c->c.get("id").getAsString().equals("craftbound:potion_of_slowness")).findFirst().orElseThrow().deepCopy();
  freeze.addProperty("id","craftbound:test_hero_freeze");freeze.getAsJsonArray("effects").remove(1);
  freeze.getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("target","CHOSEN_CHARACTER");cards.add(freeze);
  CardCatalog.install(CardCatalog.parse(root.toString()));
  try {
   for(int side=0;side<2;side++){
    var m=match();m.current=side;m.player(side).xp=10;
    var full=mob(m,side,"ravager");var wounded=mob(m,side,"zombie");
    var block=mob(m,side,"furnace");var enemy=mob(m,1-side,"ravager");
    for(String name:List.of("test_mob_heal","hearty_stew","splash_potion_of_healing","rotten_flesh")){
     var card=new CardRef(id(name),false);
     ok(!(boolean)worth.invoke(null,m,side,card),"AI keeps healing when all friendlies are full: "+name);
    }
    wounded.health=1;
    for(String name:List.of("test_mob_heal","hearty_stew")){
     var card=new CardRef(id(name),true);
     ok((boolean)worth.invoke(null,m,side,card),"AI uses targeted healing on wounded ally");
     ok(choose.invoke(null,m,side,card).equals(Target.mob(side,wounded.id)),"AI skips full-health strongest ally");
    }
    wounded.health=wounded.maxHealth;block.health=1;
    ok((boolean)worth.invoke(null,m,side,new CardRef(id("splash_potion_of_healing"),false)),"Splash heal useful for damaged block");
    ok(!(boolean)worth.invoke(null,m,side,new CardRef(id("test_mob_heal"),false)),"Mob-only heal ignores damaged block");
    block.health=block.maxHealth;m.player(side).health=25;
    ok(choose.invoke(null,m,side,new CardRef(id("hearty_stew"),false)).equals(Target.hero(side)),"Character heal selects wounded friendly hero");
    m.player(side).health=30;enemy.health=1;m.player(1-side).health=5;
    ok(!(boolean)worth.invoke(null,m,side,new CardRef(id("hearty_stew"),false)),"Does not heal wounded enemies");
    ok((boolean)worth.invoke(null,m,side,new CardRef(id("village_cook"),false)),"Healing body may still be summoned for board presence");
    var fresh=mob(m,1-side,"zombie");enemy.frozen=true;
    var slow=new CardRef(id("potion_of_slowness"),false);
    ok(choose.invoke(null,m,side,slow).equals(Target.mob(1-side,fresh.id)),"Freeze avoids frozen strongest enemy");
    fresh.skippedAttackFromFreeze=true;
    ok(!(boolean)worth.invoke(null,m,side,slow),"Freeze/draw potion held when every target already frozen");
    ok(choose.invoke(null,m,side,slow).equals(Target.NONE),"No frozen fallback target");
    var heroFreeze=new CardRef(id("test_hero_freeze"),false);
    ok(choose.invoke(null,m,side,heroFreeze).equals(Target.hero(1-side)),"Freeze can select unfrozen enemy hero");
    m.player(1-side).frozen=true;
    ok(!(boolean)worth.invoke(null,m,side,heroFreeze),"Already frozen hero not a useful freeze target");
    m.player(1-side).frozen=false;m.player(1-side).skippedAttackFromFreeze=true;
    ok(!(boolean)worth.invoke(null,m,side,heroFreeze),"Hero skipping frozen attack also excluded");
    fresh.skippedAttackFromFreeze=false;
    ok((boolean)worth.invoke(null,m,side,slow),"Freeze useful after freeze expires");
   }
  } finally {CardCatalog.install(original);}
  var art=Class.forName("craftbound.client.match.MatchArt");
  for(String fieldName:List.of("HEART_PIXELS","ATTACK_PIXELS")){
   var field=art.getDeclaredField(fieldName);field.setAccessible(true);var pixels=(String[])field.get(null);
   ok(pixels.length==9,"Both stat icons use nine rows");
   for(String row:pixels)ok(row.length()==9&&row.matches("[.#LRD]+"),"Both stat icons use nine consistent pixel columns");
  }
 }

 static void excavateAndLiveTools() throws Exception {
  var deck=Collections.nCopies(30,new CardRef(Cards.ZOMBIE,false));
  var aiPower=MatchAI.class.getDeclaredMethod("usePower",Match.class,int.class);aiPower.setAccessible(true);
  var stateType=Class.forName("craftbound.client.match.MatchState");
  var parse=stateType.getDeclaredMethod("parse",JsonObject.class,List.class);parse.setAccessible(true);
  var copy=stateType.getDeclaredMethod("copy");copy.setAccessible(true);
  var sidesField=stateType.getDeclaredField("sides");sidesField.setAccessible(true);
  ok(Cards.get(Cards.EXCAVATED_PICKAXE).token()&&!Cards.collectible().contains(Cards.EXCAVATED_PICKAXE),"Power pickaxe never enters collectible pool");
  ok(CardCatalog.entry(Cards.EXCAVATED_PICKAXE).effects().isEmpty(),"Basic pickaxe has no mining trigger");
  for(int side=0;side<2;side++){
   var m=new Match(new MatchPlayer("A",UUID.randomUUID(),CardClass.MINER,id("classic"),deck),
       new MatchPlayer("B",null,CardClass.MINER,id("classic"),deck),RandomSource.create(7));
   m.phase=Match.Phase.PLAYING;m.current=side;m.player(side).xp=10;m.player(side).hand.clear();m.events.clear();
   Tool tool;
   m.usePower(side);ok(m.player(side).hand.getFirst().id().equals(id("iron_ore_token")),"Miner now generates Iron Ore");
   // Cosmetic finish and applied buffs are independent, retained in snapshots and replay copies.
   for(boolean cosmetic:new boolean[]{false,true}){
    m.equip(side,new CardRef(Cards.IRON_SWORD,cosmetic));m.events.clear();
    CardEffects.onItem(m,side,new CardRef(id("sharpness"),false),Target.NONE);
    CardEffects.onItem(m,side,new CardRef(id("sharpness"),true),Target.NONE);
    tool=m.player(side).tool;
    ok(tool.card.enchanted()==cosmetic&&tool.enchantments.size()==2,"Buffs do not alter collectible finish");
    ok(tool.enchantments.get(1).card().enchanted(),"Applied enchantment source finish preserved");
    ok(tool.attack==Cards.get(Cards.IRON_SWORD).attack()+4,"Repeated enchantments stack");
    ok(m.events.stream().filter(e->e.type()==MatchEvent.Type.ENCHANT&&e.card().id().equals(id("sharpness"))).count()==2,
        "Enchant events retain source identity rather than replacing the tool");
    var json=JsonParser.parseString(MatchView.build(m,side,0)).getAsJsonObject();
    var wireTool=json.getAsJsonArray("players").get(side).getAsJsonObject().getAsJsonObject("tool");
    ok(wireTool.get("ench").getAsBoolean()==cosmetic&&wireTool.getAsJsonArray("enchantments").size()==2,"Wire snapshot carries both states");
    Object state=parse.invoke(null,json,new ArrayList<>());
    Object duplicate=copy.invoke(state);
    Object clientSide=((Object[])sidesField.get(duplicate))[side];
    var toolField=clientSide.getClass().getDeclaredField("tool");toolField.setAccessible(true);
    Object clientTool=toolField.get(clientSide);
    var effectsField=clientTool.getClass().getDeclaredField("enchantments");effectsField.setAccessible(true);
    ok(((List<?>)effectsField.get(clientTool)).equals(tool.enchantments),"Client snapshot copy retains applied enchantments");
    Object originalSide=((Object[])sidesField.get(state))[side];
    ((List<?>)effectsField.get(toolField.get(originalSide))).clear();
    ok(((List<?>)effectsField.get(clientTool)).size()==2,"Client copies do not share mutable enchantment lists");
    String live=craftbound.client.card.CardRenderer.liveToolText(Cards.IRON_SWORD,tool.attack,1).getString();
    ok(live.contains(tool.attack+" Attack")&&live.contains("1 Durability remaining")&&!live.contains("Lasts 2 attacks"),
        "Live preview replaces printed tool stats with current values");
    m.player(side).heroAttacked=false;m.attack(side,Match.HERO,Target.hero(1-side));
    ok(tool.enchantments.size()==2,"Attacking retains applied enchantments");
    m.equip(side,new CardRef(Cards.EXCAVATED_PICKAXE,false));
    ok(m.player(side).tool.enchantments.isEmpty()&&!m.player(side).tool.card.enchanted(),"Replacing tool clears buffs and cosmetic finish");
   }
  }
 }

 static JsonObject newEffect(String trigger,String action,String target) {
  JsonObject e=new JsonObject();
  e.addProperty("trigger",trigger);e.addProperty("action",action);e.addProperty("target",target);e.addProperty("boardKind","BOTH");e.addProperty("type","");
  e.addProperty("amount",1);e.addProperty("attack",0);e.addProperty("health",0);e.addProperty("card","");e.addProperty("excludeSelf",false);
  return e;
 }
 static JsonObject fixture(JsonArray cards,String base,String name,JsonObject...effects) {
  JsonObject c=cards.asList().stream().map(JsonElement::getAsJsonObject).filter(x->x.get("id").getAsString().equals("craftbound:"+base)).findFirst().orElseThrow().deepCopy();
  c.addProperty("id","craftbound:"+name);c.addProperty("name",name);c.addProperty("autoText",true);c.addProperty("text","");
  JsonArray list=new JsonArray();for(var effect:effects)list.add(effect);c.add("effects",list);cards.add(c);return c;
 }
 static void tntChecks() {
  var original=CardCatalog.current();var root=JsonParser.parseString(original.json()).getAsJsonObject();
  var gate=newEffect("END_TURN","DRAW","SELF");gate.addProperty("gate","HERO_HURT");
  var witness=fixture(root.getAsJsonArray("cards"),"cow","test_gate",gate);witness.addProperty("arrivalFx","FROST");witness.addProperty("deathFx","SPLIT");
  var sweep=newEffect("START_TURN","DESTROY","ENEMY_MOBS");sweep.addProperty("boardKind","BLOCK");
  fixture(root.getAsJsonArray("cards"),"furnace","test_fuse",sweep,newEffect("START_TURN","DESTROY","SELF")).addProperty("art","minecraft:tnt");
  try {
   CardCatalog.install(CardCatalog.parse(root.toString()));
   for(boolean hurt:new boolean[]{false,true}){
    var gm=match();mob(gm,0,"test_gate");if(hurt)gm.player(0).health--;gm.endTurn(0);
    ok(gm.player(0).hand.size()==(hurt?1:0),"Authored conditional timing checks current hero health");
   }
   var stored=CardCatalog.parse(CardCatalog.current().json()).entries().get(id("test_gate"));
   ok(stored.deathFx()==CardCatalog.DeathFx.SPLIT&&stored.arrivalFx()==CardCatalog.ArrivalFx.FROST,"Animation overrides survive catalogue round trip");
   ok(CardCatalog.hasTntFuse(id("test_fuse")),"TNT art enables fuse on a start-turn self-destruct block");
   var rules=CardCatalog.description(CardCatalog.entry(id("test_fuse")));
   ok(rules.contains("At the start of your turn: Destroy all enemy blocks, then destroy this card."),"Combined TNT text: "+rules);
   for(boolean defused:new boolean[]{false,true}){
    var m=match();var bomb=mob(m,0,"test_fuse");var ally=mob(m,0,"furnace");var enemy=mob(m,1,"cow");
    var a=mob(m,1,"furnace");var b=mob(m,1,"furnace");m.endTurn(0);
    ok(m.player(0).board.contains(bomb)&&m.player(1).board.contains(a),"Opponent gets a full response turn");
    if(defused){m.damage(bomb,100,null);m.resolveDeaths();}m.endTurn(1);
    ok(m.player(0).board.contains(ally)&&!m.player(0).board.contains(bomb)&&m.player(1).board.contains(enemy)
        &&m.player(1).board.contains(a)==defused&&m.player(1).board.contains(b)==defused,"Only live TNT clears the enemy block row");
    ok(m.events.stream().filter(e->e.type()==MatchEvent.Type.TNT_DETONATE).count()==(defused?0:1),"Detonate visual only on a live fuse");
   }
  }finally{CardCatalog.install(original);}
 }
 static void badOmenChecks() {
  var original = CardCatalog.current();
  var root = JsonParser.parseString(original.json()).getAsJsonObject();
  var cards = root.getAsJsonArray("cards");
  var omen = newEffect("DROP", "BAD_OMEN", "ENEMY_HERO");
  omen.addProperty("card", "craftbound:zombie");
  fixture(cards, "zombie", "test_patrol", omen);
  fixture(cards, "bone_meal", "test_clean_omen", newEffect("PLAY", "CLEANSE", "FRIENDLY_HERO"));
  try {
   CardCatalog.install(CardCatalog.parse(root.toString()));
   var m = match();
   for (int i = 0; i < 2; i++) {
    var patrol = mob(m, 1, "test_patrol");
    m.damage(patrol, 100, null);
    m.resolveDeaths();
   }
   m = MatchSnapshot.restore(MatchSnapshot.capture(m));
   mob(m, 0, "cow");
   play(m, "furnace");
   ok(id("zombie").equals(m.player(0).badOmenSummon), "Bad Omen survives pause, generated mobs and block plays");
   play(m, "cow");
   play(m, "cow");
   ok(m.player(0).badOmenSummon == null && m.player(1).board.size() == 1
       && m.player(1).board.getFirst().card.id().equals(id("zombie")), "Only one raid on the opposing side");
   m.player(0).badOmenSummon = id("zombie");
   play(m, "test_clean_omen");
   ok(m.player(0).badOmenSummon == null, "Hero Cleanse removes the pending raid");
  } finally { CardCatalog.install(original); }
 }
 static void foundationMechanics() throws Exception {
  var original=CardCatalog.current();
  var root=JsonParser.parseString(original.json()).getAsJsonObject();var cards=root.getAsJsonArray("cards");
  var harvest=newEffect("HARVEST","GIVE","SELF");harvest.addProperty("card","craftbound:arrow");
  var dismantle=newEffect("DISMANTLE","GIVE","SELF");dismantle.addProperty("card","craftbound:rotten_flesh");
  var drop=newEffect("DROP","GIVE","SELF");drop.addProperty("card","craftbound:mined_coal");
  fixture(cards,"wheat","test_crop_payoffs",harvest,dismantle,drop);
  var kill=newEffect("ON_KILL","SUMMON","SELF");kill.addProperty("card","craftbound:wheat");
  fixture(cards,"zombie","test_kill_planter",kill);
  var enchant=newEffect("PLAY","ENCHANT_TOOL","SELF");
  var loot=newEffect("ON_KILL","GIVE","SELF");loot.addProperty("card","craftbound:mined_gold_nugget");
  var catchFish=newEffect("AFTER_ATTACK","GIVE_RANDOM","SELF");
  JsonArray table=new JsonArray();table.add("craftbound:cod");table.add("craftbound:salmon");catchFish.add("pool",table);
  var fire=newEffect("DAMAGE_DEALT","DAMAGE","CHOSEN_ANY");
  fixture(cards,"sharpness","test_trigger_enchant",enchant,loot,catchFish,fire);
  var angler=newEffect("CARD_ADDED","BUFF","SELF");angler.addProperty("boardKind","MOB");angler.addProperty("attack",1);angler.addProperty("eventType","AQUATIC");
  fixture(cards,"zombie","test_angler_observer",angler);
  var itemWatch=newEffect("ITEM_PLAYED","BUFF","SELF");itemWatch.addProperty("boardKind","MOB");itemWatch.addProperty("attack",1);
  fixture(cards,"zombie","test_item_observer",itemWatch);
  var mineWatch=newEffect("MINED","DRAW","SELF");mineWatch.addProperty("eventType","ORE");
  fixture(cards,"zombie","test_mine_observer",mineWatch);
  var healWatch=newEffect("HEALED","GIVE","SELF");healWatch.addProperty("card","craftbound:mined_gold_nugget");
  fixture(cards,"zombie","test_heal_observer",healWatch);
  var allyHeal=newEffect("HEALED","BUFF","EVENT");allyHeal.addProperty("boardKind","MOB");allyHeal.addProperty("attack",1);allyHeal.addProperty("eventScope","FRIENDLY");
  fixture(cards,"zombie","test_heal_support",allyHeal);
  var aura=fixture(cards,"beacon","test_tribal_aura");aura.addProperty("auraType","TAMEABLE");aura.addProperty("passiveAmount",2);
  for(String keyword:List.of("SHIELD","SPRINT","RANGED")){
   var grant=newEffect("PLAY","GRANT_KEYWORD","CHOSEN_FRIENDLY");grant.addProperty("keyword",keyword);
   grant.addProperty("boardKind",keyword.equals("SHIELD")?"BOTH":"MOB");fixture(cards,"bone_meal","test_grant_"+keyword.toLowerCase(),grant);
  }
  var weaken=newEffect("PLAY","BUFF","CHOSEN_ENEMY");weaken.addProperty("attack",-5);weaken.addProperty("boardKind","MOB");fixture(cards,"bone_meal","test_weaken",weaken);
  fixture(cards,"bone_meal","test_cleanse_debuff",newEffect("PLAY","CLEANSE","CHOSEN_CHARACTER"));
  var fed=newEffect("PLAY","WELL_FED","CHOSEN_CHARACTER");fed.addProperty("amount",4);fixture(cards,"bone_meal","test_well_fed",fed);
  var transform=newEffect("PLAY","TRANSFORM","CHOSEN_ANY");transform.addProperty("card","craftbound:hunting_wolf");fixture(cards,"bone_meal","test_transform",transform);
  var random=newEffect("PLAY","GIVE_RANDOM","SELF");random.addProperty("amount",3);random.add("pool",table.deepCopy());fixture(cards,"bone_meal","test_catch_table",random);
  for(String type:List.of("CROP","ORE")){
   var count=newEffect("PLAY","DAMAGE","CHOSEN_ENEMY");count.addProperty("countScope","FRIENDLY");count.addProperty("countType",type);count.addProperty("countKind","BLOCK");
   fixture(cards,"bone_meal","test_count_"+type.toLowerCase(),count);
  }
  var loop=newEffect("HEALED","WELL_FED","SELF");fixture(cards,"zombie","test_healing_loop",loop);
  try {
   CardCatalog.install(CardCatalog.parse(root.toString()));
   ok(CardCatalog.parse(CardCatalog.current().json()).entries().equals(CardCatalog.current().entries()),"Expanded schema survives exact round trip");
   var m=match();var attacker=mob(m,0,"zombie");var archer=mob(m,1,"skeleton");attacker.ready=true;attacker.summonedThisTurn=false;
   m.attack(0,attacker.id,Target.mob(1,archer.id));ok(attacker.health==1&&archer.isDead(),"Ranged defender retaliates normally");
   m=match();archer=mob(m,0,"skeleton");var melee=mob(m,1,"zombie");archer.ready=true;archer.summonedThisTurn=false;
   m.attack(0,archer.id,Target.mob(1,melee.id));ok(archer.isDead()&&melee.health==1,"Ranged attacker takes normal retaliation");
   m=match();
   var front=mob(m,1,"zombie");var rear=mob(m,1,"furnace");
   attacker=mob(m,0,"zombie");archer=mob(m,0,"skeleton");attacker.ready=true;archer.ready=true;
   var rearTarget=Target.mob(1,rear.id);
   ok(m.whyCantAttack(0,attacker.id,rearTarget)!=null && m.whyCantAttack(0,archer.id,rearTarget)==null,"Only Ranged can reach a screened block");
   m.grantKeyword(front,Keyword.SHIELD);
   ok(m.whyCantAttack(0,archer.id,rearTarget)!=null,"Shield intercepts even a ranged shot at the back row");
   m.grantKeyword(attacker,Keyword.IGNORE_SHIELD);
   ok(m.whyCantAttack(0,attacker.id,rearTarget)!=null,"Ignore Shield alone does not grant back-row reach");
   m.remove(front);
   ok(m.whyCantAttack(0,attacker.id,rearTarget)==null,"Clearing the front row exposes blocks to melee");
   m=match();attacker=mob(m,0,"zombie");melee=mob(m,1,"zombie");attacker.ready=true;
   m.attack(0,attacker.id,Target.mob(1,melee.id));ok(attacker.health==1&&melee.health==1,"Melee combat still retaliates");
   m=match();archer=mob(m,0,"skeleton");var thorns=mob(m,1,"sweet_berry_bush");archer.ready=true;
   m.attack(0,archer.id,Target.mob(1,thorns.id));ok(archer.health==1,"Ranged still takes explicit Thorns damage");
   m=match();var crop=mob(m,0,"test_crop_payoffs");m.dismantle(0,crop.id);
   ok(hand(m,"rotten_flesh")==0&&hand(m,"arrow")==0&&hand(m,"mined_coal")==0&&m.player(0).board.contains(crop),"Legacy Dismantle trigger stays inert");
   m=match();crop=mob(m,0,"test_crop_payoffs");m.damage(crop,9,null);m.resolveDeaths();
   ok(hand(m,"mined_coal")==1&&hand(m,"arrow")==0&&hand(m,"rotten_flesh")==0,"Death pays Drop only");
   var deck=Collections.nCopies(30,new CardRef(Cards.ZOMBIE,false));
   m=new Match(new MatchPlayer("Farmer",UUID.randomUUID(),CardClass.FARMER,id("classic"),deck),new MatchPlayer("Enemy",null,CardClass.FARMER,id("classic"),deck),RandomSource.create(7));
   m.phase=Match.Phase.PLAYING;m.current=0;m.player(0).xp=10;m.player(0).reap=true;m.player(0).hand.clear();
   crop=mob(m,0,"test_crop_payoffs");m.remove(crop);CardEffects.run(m,0,crop.card,crop,CardCatalog.Trigger.HARVEST,Target.NONE);m.draw(0);
   ok(hand(m,"arrow")==1&&hand(m,"mined_coal")==0&&hand(m,"rotten_flesh")==0&&hand(m,"zombie")==1,"Harvest and draw do not fire Drop/Dismantle");
   m=match();attacker=mob(m,0,"test_kill_planter");var victim=mob(m,1,"wolf_pup");attacker.ready=true;
   m.attack(0,attacker.id,Target.mob(1,victim.id));ok(m.player(0).board.stream().anyMatch(x->x.card.id().equals(Cards.WHEAT)),"Killing mob plants a Crop");
   int board=m.player(0).board.size();m.damage(victim,100,attacker);ok(m.player(0).board.size()==board,"Dead targets cannot generate repeated kill credit");

   m=match();play(m,"crafted_sword");play(m,"test_trigger_enchant");victim=mob(m,1,"zombie");victim.health=2;
   m.attack(0,Match.HERO,Target.mob(1,victim.id));
   ok(hand(m,"mined_gold_nugget")==1,"Attached on-hit damage kill gives exactly one Looting payout");
   ok(hand(m,"cod")+hand(m,"salmon")==1&&m.player(0).tool==null,"Attached catch trigger works on tool's final attack then expires");
   play(m,"crafted_sword");victim=mob(m,1,"wolf_pup");m.player(0).heroAttacked=false;m.attack(0,Match.HERO,Target.mob(1,victim.id));
   ok(hand(m,"mined_gold_nugget")==1,"New tool does not inherit old enchantment triggers");

   m=match();var watcher=mob(m,0,"test_angler_observer");var enemyWatcher=mob(m,1,"test_angler_observer");
   m.giveCard(0,new CardRef(id("cod"),false));ok(watcher.attack==3&&enemyWatcher.attack==2,"Added-hand observer filters type and owner");
   m.player(0).deck.addFirst(new CardRef(id("salmon"),false));m.draw(0);ok(watcher.attack==4,"Draw counts as added to hand");
   m.giveCard(0,new CardRef(id("zombie"),false));ok(watcher.attack==4,"Nonmatching card type does not trigger Angler");
   while(m.player(0).hand.size()<Match.MAX_HAND)m.player(0).hand.add(new CardRef(Cards.ZOMBIE,false));
   m.giveCard(0,new CardRef(id("cod"),false));ok(watcher.attack==4,"Burned cards do not trigger hand-added");
   m.player(0).hand.clear();var fish=mob(m,0,"cod");m.bounce(fish);ok(watcher.attack==5,"Returned cards count as entering hand");
   m=match();watcher=mob(m,0,"test_item_observer");play(m,"experience_bottle");ok(watcher.attack==3,"Item played trigger resolves");
   play(m,"crafted_sword");play(m,"sharpness");ok(watcher.attack==3,"Tools and enchantments do not count as Item plays");
   m=match();watcher=mob(m,0,"test_mine_observer");var ore=mob(m,0,"coal_ore");m.mine(ore);
   ok(hand(m,"mined_coal")==2&&hand(m,"zombie")==1,"Mining pays Drop and notifies mining observer");
   ore=mob(m,0,"coal_ore");m.dismantle(0,ore.id);ok(hand(m,"zombie")==1,"Dismantling does not count as mining");

   m=match();watcher=mob(m,0,"test_heal_observer");watcher.health=1;var support=mob(m,0,"test_heal_support");
   m.heal(Target.mob(0,watcher.id),1,false);
   ok(hand(m,"mined_gold_nugget")==1&&watcher.attack==3&&support.attack==2,"Healed self and friendly event-target triggers both work");
   m.heal(Target.mob(0,watcher.id),10,false);int size=m.player(0).hand.size();m.heal(Target.mob(0,watcher.id),1,false);
   ok(m.player(0).hand.size()==size,"Zero healing never fires healed trigger");
   m=match();var fedMob=mob(m,0,"zombie");fedMob.health=1;play(m,"test_well_fed",Target.mob(0,fedMob.id));
   ok(fedMob.health==5&&fedMob.maxHealth==5,"Well Fed turns only excess healing into max Health");
   play(m,"test_well_fed",Target.hero(0));ok(m.player(0).health==34&&m.player(0).maxHealth==34,"Well Fed supports heroes");
   m.player(0).health=29;m.healHero(0,10);ok(m.player(0).health==34,"Ordinary healing uses raised hero maximum");
   var healthWire=JsonParser.parseString(MatchView.build(m,0,0)).getAsJsonObject().getAsJsonArray("players").get(0).getAsJsonObject();
   ok(healthWire.get("maxHealth").getAsInt()==34,"Raised hero maximum is transmitted");

   m.player(0).temporaryHeroAttack=2;play(m,"test_cleanse_debuff",Target.hero(0));
   ok(m.player(0).temporaryHeroAttack==0&&m.player(0).health==30&&m.player(0).maxHealth==30,"Cleanse strips hero buffs and caps Health");
   m.grantKeyword(fedMob,Keyword.SPRINT);fedMob.health=1;play(m,"test_cleanse_debuff",Target.mob(0,fedMob.id));
   ok(!fedMob.ready&&!fedMob.has(Keyword.SPRINT)&&fedMob.health==1&&fedMob.maxHealth==fedMob.definition().health(),"Cleanse removes granted Sprint and max Health without healing damage");

   m=match();victim=mob(m,1,"zombie");play(m,"test_weaken",Target.mob(1,victim.id));m.buff(victim,2,0);
   ok(m.attackOf(victim)==0&&victim.attackDebuff==5,"Negative Attack is a separate debuff and clamps effective Attack at zero");
   play(m,"test_cleanse_debuff",Target.mob(1,victim.id));ok(m.attackOf(victim)==victim.definition().attack()&&victim.attackDebuff==0,"Cleanse removes both positive and negative Attack");
   m=match();var pup=mob(m,0,"wolf_pup");var auraMob=mob(m,0,"test_tribal_aura");var zombie=mob(m,0,"zombie");
   ok(m.attackOf(pup)==3&&m.attackOf(zombie)==2,"Aura affects only other matching mob types");
   auraMob.health=0;ok(m.attackOf(pup)==1,"Dead aura sources immediately stop contributing");
   m=match();pup=mob(m,0,"wolf_pup");play(m,"test_grant_sprint",Target.mob(0,pup.id));
   ok(pup.canAttack()&&m.whyCantAttack(0,pup.id,Target.hero(1))!=null,"Granted Sprint enables board attacks but not same-turn face");
   ore=mob(m,0,"coal_ore");play(m,"test_grant_shield",Target.mob(0,ore.id));
   ok(ore.has(Keyword.SHIELD)&&!ore.canAttack(),"Blocks can gain Shield but still cannot attack");
   play(m,"test_grant_ranged",Target.mob(0,pup.id));ok(pup.has(Keyword.RANGED)&&m.retaliationOf(pup)==m.attackOf(pup),"Granted Ranged uses new combat rule");
   var wire=JsonParser.parseString(MatchView.build(m,0,0)).getAsJsonObject().getAsJsonArray("players").get(0).getAsJsonObject().getAsJsonArray("board").get(0).getAsJsonObject();
   ok(wire.get("ranged").getAsBoolean()&&wire.get("sprint").getAsBoolean(),"Dynamic keywords are transmitted");
   pup.attackedThisTurn=true;pup.totem=true;pup.withered=true;m.buff(pup,5,5);
   play(m,"test_transform",Target.mob(0,pup.id));var wolf=m.player(0).minion(pup.id);
   ok(wolf.card.id().equals(id("hunting_wolf"))&&wolf.health==2&&wolf.attack==3&&!wolf.totem&&!wolf.withered,"Transform replaces stats, abilities and statuses in same slot");
   ok(wolf.attackedThisTurn&&!wolf.canAttack(),"Transform cannot refresh a spent attack");
   ok(m.events.stream().anyMatch(e->e.type()==MatchEvent.Type.TRANSFORM),"Transform has a dedicated client event");

   m=match();victim=mob(m,1,"ravager");play(m,"test_count_crop",Target.mob(1,victim.id));ok(victim.health==7,"For-each zero count has zero effect");
   mob(m,0,"wheat");mob(m,0,"wheat");mob(m,1,"wheat");mob(m,0,"coal_ore");
   play(m,"test_count_crop",Target.mob(1,victim.id));ok(victim.health==5,"For-each filters owner, kind and Crop type");
   play(m,"test_count_ore",Target.mob(1,victim.id));ok(victim.health==4,"Ore counts are independent of Crops");
   m=match();play(m,"test_catch_table");ok(m.player(0).hand.size()==3&&m.player(0).hand.stream().allMatch(c->table.asList().stream().anyMatch(v->v.getAsString().equals(c.id().toString()))),"Random Give only selects authored table entries");
   m=match();var looper=mob(m,0,"test_healing_loop");m.heal(Target.mob(0,looper.id),1,true);
   ok(m.over&&m.winner==-1,"Recursive healed/Well Fed chains terminate safely");

   for(String field:List.of("keyword","pool","countScope","eventScope","auraType")){
    var invalid=root.deepCopy();var list=invalid.getAsJsonArray("cards");
    if(field.equals("auraType"))list.get(list.size()-1).getAsJsonObject().addProperty(field,"INVALID");
    else {
     var f=list.get(list.size()-1).getAsJsonObject().getAsJsonArray("effects").get(0).getAsJsonObject();
     if(field.equals("pool")){JsonArray bad=new JsonArray();bad.add("craftbound:does_not_exist");f.addProperty("action","GIVE_RANDOM");f.add("pool",bad);}
     else f.addProperty(field,"INVALID");
    }
    boolean rejected=false;try{CardCatalog.parse(invalid.toString());}catch(IllegalArgumentException expected){rejected=true;}
    ok(rejected,"Invalid extended field rejected: "+field);
   }
  } finally {CardCatalog.install(original);}
 }
}
