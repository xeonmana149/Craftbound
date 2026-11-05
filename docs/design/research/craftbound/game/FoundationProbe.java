package craftbound.game;
import craftbound.card.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import java.util.*;
public class FoundationProbe {
 static final String[] CLASSES={"FIGHTER","BUILDER","FARMER","FISHERMAN"};
 static final String[] LISTS={"iron_sword diamond_sword potion_of_strength vindicator sweeping_edge sharpness pillager zombie skeleton drowned villager librarian piglin_brute ravager enderman","furnace dispenser beacon piston bow iron_pickaxe efficiency zombie skeleton drowned villager librarian shulker enderman ravager","bee bone_meal sweet_berry_bush bountiful_harvest composter sniffer iron_hoe unbreaking cow shepherd zombie skeleton drowned villager librarian","fishing_rod cod salmon turtle dolphin guardian conduit school_of_fish lure zombie skeleton drowned villager librarian enderman"};
 static List<CardRef> deck(int i){
  var out=new ArrayList<CardRef>();var cls=CardClass.valueOf(CLASSES[i]);
  for(String raw:LISTS[i].split(" ")){var id=Identifier.parse("craftbound:"+raw);
   if(!DeckRules.allowed(id,cls)||Cards.get(id).token()||DeckRules.maxCopies(Cards.get(id).rarity())<2)throw new AssertionError("Illegal "+raw);
   out.add(new CardRef(id,false));out.add(new CardRef(id,false));}
  if(out.size()!=30)throw new AssertionError("Deck size");return out;
 }
 public static void main(String[] args){
  long allTurns=0;int games=0,unfinished=0,firstWins=0,decisive=0;
  System.out.println("pair,games,winsA,winsB,draws,unfinished,meanPlayerTurns");
  for(int a=0;a<4;a++)for(int b=a+1;b<4;b++){
   int wa=0,wb=0,draws=0,stalled=0;long turns=0;
   for(int n=0;n<100;n++){
    var pa=new MatchPlayer(CLASSES[a],null,CardClass.valueOf(CLASSES[a]),Identifier.parse("craftbound:classic"),deck(a));
    var pb=new MatchPlayer(CLASSES[b],null,CardClass.valueOf(CLASSES[b]),Identifier.parse("craftbound:classic"),deck(b));
    var m=new Match(pa,pb,RandomSource.create(20261102000L+a*10000+b*100+n));
    MatchAI.mulligan(m,0);MatchAI.mulligan(m,1);
    int actions=0;
    while(!m.over&&m.turn<200&&actions++<3000){MatchAI.step(m,m.current);m.events.clear();}
    if(!m.over)stalled++;else if(m.winner<0)draws++;else {
     if(m.player(m.winner)==pa)wa++;else wb++;
     decisive++;if(m.winner==0)firstWins++;
    }
    turns+=m.turn;
   }
   games+=100;unfinished+=stalled;allTurns+=turns;
   System.out.println(CLASSES[a]+" vs "+CLASSES[b]+",100,"+wa+","+wb+","+draws+","+stalled+","+(turns/100.0));
  }
  System.out.println("TOTAL games="+games+" unfinished="+unfinished+" firstWins="+firstWins+" decisive="+decisive+" meanPlayerTurns="+(allTurns/(double)games));
 }
}
