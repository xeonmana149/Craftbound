package craftbound.client.card;

import craftbound.card.Cards;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/** Headless layout contract: the world target consumes the complete inspection face. */
public final class CardFaceChecks {
 static class FontMetrics extends Font {
  FontMetrics(){super(null);}
  @Override public int width(String text){return text.codePointCount(0,text.length())*6;}
  @Override public String plainSubstrByWidth(String text,int width){return text.substring(0,Math.min(text.length(),width/6));}
  @Override public int width(FormattedText text){return width(text.getString());}
  @Override public int width(FormattedCharSequence text){return string(text).length()*6;}
  @Override public List<FormattedCharSequence> split(FormattedText text,int width){
   var result=new ArrayList<FormattedCharSequence>();
   String words=text.getString();int n=Math.max(1,width/6);
   for(int i=0;i<words.length();i+=n)result.add(Component.literal(words.substring(i,Math.min(words.length(),i+n))).getVisualOrderText());
   return result;
  }
 }
 static String string(FormattedCharSequence text) {
  var s=new StringBuilder();text.accept((index,style,cp)->{s.appendCodePoint(cp);return true;});return s.toString();
 }
 static class Recorder implements CardCanvas {
  final Matrix3x2fStack pose=new Matrix3x2fStack(16);
  final List<String> text=new ArrayList<>();
  final List<Integer> textColors=new ArrayList<>();
  int items,fills;
  public Matrix3x2fStack pose(){return pose;}
  public void fill(int a,int b,int c,int d,int color){fills++;}
  public void text(Font f,FormattedCharSequence t,int x,int y,int color,boolean shadow){text.add(string(t));textColors.add(color);}
  public void frameRegion(net.minecraft.resources.Identifier texture,int u,int v,int sw,int sh,int x,int y,int w,int h){}
  public void item(ItemStack item,int x,int y){if(item.isEmpty())throw new AssertionError("Missing artwork");items++;}
  public void shimmer(int x,int y,int w,int h,float d,int half,int alpha){}
 }
 public static void main(String[] args) {
  craftbound.test.TestCatalogue.install();
  craftbound.client.match.PortraitFragmentChecks.run();
  craftbound.client.match.ToolTheftChecks.run();
  SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
  // Standalone checks have no data-pack loader to bind vanilla item components.
  net.minecraft.core.registries.BuiltInRegistries.ITEM.listElements().forEach(holder -> {
   if(!holder.areComponentsBound())holder.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
  });
  StatusExplanationChecks.run();
  craftbound.client.match.HandRevealChecks.run();
  craftbound.client.match.EyeOfEnderChecks.run();
        craftbound.client.match.AnimationChoiceChecks.run();
  var font=new FontMetrics();
  var missing=net.minecraft.resources.Identifier.parse("craftbound:removed_tool");
  if(!CardRenderer.placeholderArt(missing,Cards.get(missing)).is(net.minecraft.world.item.Items.PAPER))
   throw new AssertionError("Missing catalogue card must have safe fallback artwork");
  if(!CardRenderer.placeholderArt(null,null).is(net.minecraft.world.item.Items.PAPER))
   throw new AssertionError("Absent card ID must have safe fallback artwork");
  for(String name:List.of("zombie","skeleton","spider","enderman")) {
   var id=net.minecraft.resources.Identifier.parse("craftbound:"+name);
   var card=craftbound.card.CardDefinition.mob(craftbound.card.CardRarity.COMMON,2,2,3).art("minecraft:"+name).build();
   var expected=net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("minecraft:"+name+"_spawn_egg"));
   if(!CardRenderer.placeholderArt(id,card).is(expected))throw new AssertionError("Mob artwork ID did not resolve: "+name);
  }
  var fishArt=craftbound.card.CardDefinition.mob(craftbound.card.CardRarity.COMMON,1,1,1).art("minecraft:cod").build();
  if(!CardRenderer.placeholderArt(Cards.CAUGHT_COD,fishArt).is(net.minecraft.world.item.Items.COD))throw new AssertionError("Valid item artwork was replaced by an egg");
  checkRenderCache(font);
  craftbound.client.block.TableTextureChecks.run();
  checkTrialTraining(font);
  checkWorldShimmer();
  PackRevealChecks.run();
  checkTableProjection(font);
  int checked=0;
  for(var id:Cards.collectible()) for(boolean enchanted:new boolean[]{false,true}) {
   var card=Cards.get(id);var capture=new Recorder();
   CardRenderer.drawFace(capture,font,id,enchanted,0,0,1,-1,-1);
   if(capture.items!=1 || capture.fills<12)throw new AssertionError("Incomplete face "+id);
   if(!capture.text.contains(Cards.name(id).getString()))throw new AssertionError("Printed name changed "+id);
   if(!capture.text.contains("#"+Cards.collectorNumber(id)))throw new AssertionError("Missing collector number "+id);
   if(!capture.text.contains(CardRenderer.kindLabel(card).getString()))throw new AssertionError("Missing type "+id);
   String body=String.join("",capture.text).replace(" ","");
   if(!body.contains(Cards.text(id).getString().replace(" ","")))throw new AssertionError("Missing ability text "+id);
   if(!capture.pose.equals(new org.joml.Matrix3x2f()))throw new AssertionError("Unbalanced layout pose");
   checked++;
  }
  System.out.println("PASS: "+checked+" complete card faces, artwork, base names, rules text, type labels, collector numbers and balanced transforms.");
 }
 static void checkRenderCache(Font font) {
  var original=craftbound.card.CardCatalog.current();
  try {
   CardRenderer.clearTextLayouts();
   var first=new Recorder();
   var firstRegions=CardRenderer.drawFace(first,font,Cards.ENDERMAN,false,13,21,1.75F,-1,-1);
   long built=CardRenderer.layoutBuilds();
   var second=new Recorder();
   var secondRegions=CardRenderer.drawFace(second,font,Cards.ENDERMAN,false,13,21,1.75F,-1,-1);
   if(CardRenderer.layoutBuilds()!=built || !first.text.equals(second.text) || !firstRegions.equals(secondRegions))
    throw new AssertionError("Warm layout must reuse the measured text and identical hover regions");
   var changed=new craftbound.card.CardCatalog.State(original.entries(),original.json());
   craftbound.card.CardCatalog.install(changed);
   CardRenderer.drawFace(new Recorder(),font,Cards.ENDERMAN,false,0,0,1,-1,-1);
   if(CardRenderer.layoutBuilds()!=built+1)throw new AssertionError("Catalogue install did not invalidate card layout");
   CardRenderer.drawFace(new Recorder(),new FontMetrics(),Cards.ENDERMAN,false,0,0,1,-1,-1);
   if(CardRenderer.layoutBuilds()!=built+2)throw new AssertionError("Font change did not invalidate card layout");
   var ids=Cards.collectible().stream().limit(16).toList();
   CardRenderer.clearTextLayouts();
   for(var id:ids)CardRenderer.drawFace(new Recorder(),font,id,false,0,0,1,-1,-1);
   built=CardRenderer.layoutBuilds();
   long start=System.nanoTime();
   for(int repeat=0;repeat<30;repeat++)for(var id:ids)
    CardRenderer.drawFace(new Recorder(),font,id,false,0,0,1,-1,-1);
   long warm=System.nanoTime()-start;
   if(CardRenderer.layoutBuilds()!=built)throw new AssertionError("Repeated binder page rebuilt text layouts");
   start=System.nanoTime();
   for(int repeat=0;repeat<30;repeat++)for(var id:ids) {
    CardRenderer.clearTextLayouts();
    CardRenderer.drawFace(new Recorder(),font,id,false,0,0,1,-1,-1);
   }
   long cold=System.nanoTime()-start;
   if(CardRenderer.layoutBuilds()-built!=30L*ids.size())throw new AssertionError("Cold workload did not exercise every layout");
   System.out.println("Card layout workload (headless font, "+(30*ids.size())+" faces): warm="+warm/1_000_000+"ms, cold="+cold/1_000_000+"ms; warm builds=0.");
   for(int attack=0;attack<530;attack++)
    CardRenderer.drawFace(new Recorder(),font,Cards.CRAFTED_SWORD,false,0,0,1,attack,2);
   if(CardRenderer.cachedTextLayouts()>512)throw new AssertionError("Card layout cache exceeded its bound");
   System.out.println("PASS: card layout reuse, catalogue/font invalidation, stable hover regions and bounded live-tool variants.");
  } finally {
   craftbound.card.CardCatalog.install(original);
   CardRenderer.clearTextLayouts();
  }
 }
 static void checkTrialTraining(Font font) {
  for(var id:List.of(Cards.ZOMBIE,Cards.CRAFTED_SWORD,net.minecraft.resources.Identifier.parse("craftbound:cobblestone_wall"))) {
   var definition=Cards.get(id);
   var ordinary=new Recorder();CardRenderer.drawTrial(ordinary,font,id,false,craftbound.game.TrialTraining.NONE,0,0,1);
   var trained=new Recorder();CardRenderer.drawTrial(trained,font,id,false,craftbound.game.TrialTraining.TEMPER,0,0,1);
   long green=trained.textColors.stream().filter(c->c==0xFF80FF74).count();
   int expected=definition.isTool()||definition.keywords().contains(craftbound.card.Keyword.BLOCK)?1:2;
   if(green!=expected || ordinary.textColors.contains(0xFF80FF74))
    throw new AssertionError("Only genuinely upgraded stats should be green: "+id);
  }
  var text=CardRenderer.trialText(Cards.ZOMBIE,craftbound.game.TrialTraining.SHIELD);
  var marked=new StringBuilder();var inherited=new StringBuilder();
  text.visit((style,part)->{
   if(style.getColor()!=null&&style.getColor().getValue()==0x256B2D) {
    if(!style.isBold()||craftbound.card.Keyword.fromInsertion(style.getInsertion())!=craftbound.card.Keyword.SHIELD)
     throw new AssertionError("Upgrade keyword lost its bold/tooltip metadata");
    marked.append(part);
   } else inherited.append(part);
   return Optional.empty();
  },Style.EMPTY);
  if(!marked.toString().equals(craftbound.card.Keyword.SHIELD.displayName().getString())
     ||!inherited.toString().contains(Cards.text(Cards.ZOMBIE).getString()))
   throw new AssertionError("Injected keyword coloring must not leak onto original rules");
  System.out.println("PASS: Trials trained stats and added keywords are green; ordinary values and rules retain their styling.");
 }
 static void checkTableProjection(Font font) {
  var deck=Collections.nCopies(30,new craftbound.game.CardRef(Cards.ZOMBIE,false));
  var match=new craftbound.game.Match(
    new craftbound.game.MatchPlayer("Player",UUID.randomUUID(),craftbound.card.CardClass.FIGHTER,craftbound.sleeve.Sleeves.CLASSIC,deck),
    new craftbound.game.MatchPlayer("Card Master",null,craftbound.card.CardClass.FARMER,craftbound.sleeve.Sleeves.CLASSIC,deck),
    net.minecraft.util.RandomSource.create(21));
  match.phase=craftbound.game.Match.Phase.PLAYING;
  for(int side=0;side<2;side++)for(int i=0;i<7;i++){
   var m=new craftbound.game.Minion(1+side*7+i,new craftbound.game.CardRef(Cards.ZOMBIE,i%2==0));
   match.player(side).board.add(m);
   m.frozen=true;m.withered=true;m.totem=true;m.invulnerable=true;
   m.grantedKeywords.add(craftbound.card.Keyword.SHIELD);
  }
  var projection=com.google.gson.JsonParser.parseString(craftbound.game.MatchView.publicBoard(match,true)).getAsJsonObject();
  var capture=new Recorder();
  craftbound.client.match.TableBoardFace.draw(capture,font,projection,2000);
  if(capture.items!=18||!capture.text.contains("PAUSED")||capture.fills<100)
   throw new AssertionError("Table must draw fourteen mobs, two heroes, statuses and pause label");
  if(!capture.pose.equals(new org.joml.Matrix3x2f()))throw new AssertionError("Table transform leaked");
  var spots=craftbound.client.match.TableBoardFace.spots(projection);
  for(var spot:spots){
   if(spot.side()==0 && (spot.y()<340||spot.y()+spot.h()>450))
    throw new AssertionError("Tabletop mob stats must clear the turn label and hero dashboard");
   for(var other:spots)if(spot.side()==other.side()&&spot.index()<other.index()){
    float gap=Math.max(other.x()-spot.x()-spot.w(),spot.x()-other.x()-other.w());
    if(gap<16)throw new AssertionError("Seven mobs need space for both stat badges");
   }
  }
  // Reproduce the server-join crash: persisted public table has an unknown equipped tool.
  var tool=new com.google.gson.JsonObject();tool.addProperty("id","craftbound:removed_tool");
  tool.addProperty("atk",2);tool.addProperty("uses",2);tool.addProperty("ench",true);
  projection.getAsJsonArray("players").get(0).getAsJsonObject().add("tool",tool);
  var unknownCapture=new Recorder();craftbound.client.match.TableBoardFace.draw(unknownCapture,font,projection,2200);
  if(unknownCapture.items!=19||!unknownCapture.pose.equals(new org.joml.Matrix3x2f()))
   throw new AssertionError("Missing tabletop tool must preserve the board and balanced transforms");
  // A subsequent render with a known definition must recover without clearing a card-art cache.
  tool.addProperty("id","craftbound:crafted_sword");
  var recovered=new Recorder();craftbound.client.match.TableBoardFace.draw(recovered,font,projection,2400);
  if(recovered.items!=19||!recovered.pose.equals(new org.joml.Matrix3x2f()))
   throw new AssertionError("Table must recover when tool artwork becomes available");
  System.out.println("PASS: server-join tabletop with unknown tool renders safely and recovers on the next valid render.");
  for(int yaw:new int[]{0,90,180,270}) {
   var pose=new com.mojang.blaze3d.vertex.PoseStack();
   craftbound.client.block.CardTableRenderer.transform(pose,yaw);
   var ownerEdge=pose.last().pose().transformPosition(new org.joml.Vector3f(320,640,0));
   double radians=Math.toRadians(yaw);
   if((ownerEdge.x-.5)*Math.sin(radians)-(ownerEdge.z-.5)*Math.cos(radians)<.4)
    throw new AssertionError("Player zero's cards must be nearest their chair at yaw "+yaw);
   var normal=pose.last().pose().transformDirection(new org.joml.Vector3f(0,0,1)).normalize();
     if(normal.y<.999)throw new AssertionError("Table face must point upwards");
     // Minecraft's font background vertices run bottom-left, bottom-right, top-right.
     var matrix=pose.last().pose();
     var a=matrix.transformPosition(new org.joml.Vector3f(0,640,0));
     var b=matrix.transformPosition(new org.joml.Vector3f(640,640,0));
     var c=matrix.transformPosition(new org.joml.Vector3f(640,0,0));
     if(b.sub(a).cross(c.sub(a)).y<=0)throw new AssertionError("Actual glyph winding must face above the table");
     for(int tableY:new int[]{-60,64,319}) {
      var world=new org.joml.Matrix4f().translate(12,tableY,-14).mul(matrix);
      float base=world.transformPosition(new org.joml.Vector3f(100,100,0)).y;
      float next=world.transformPosition(new org.joml.Vector3f(100,100,.01F)).y;
      if(next<=base)throw new AssertionError("Printed tabletop layers collapse at height "+tableY);
     }
   for(int x:new int[]{0,640})for(int y:new int[]{0,640}){
    var point=pose.last().pose().transformPosition(new org.joml.Vector3f(x,y,0));
    if(point.x<0||point.x>1||point.z<0||point.z>1||point.y<1||point.y>1.01)
     throw new AssertionError("Table board must fit on the top surface");
   }
  }
  System.out.println("PASS: full public tabletop board with shared status/icon rendering, balanced transforms and upward-facing geometry in all four orientations.");
 }
 static void checkWorldShimmer() {
  var face=new WorldCardFace(null,null);
  face.fill(0,0,120,168,0xFFFFFFFF);
  face.shimmer(0,0,120,168,100,20,112);
  var pose=new com.mojang.blaze3d.vertex.PoseStack();
  var original=new org.joml.Matrix4f(pose.last().pose());
  int[] overlayOrder={-1},submissions={0};
  float[] backgroundZ={0},shimmerZ={0};
  var overlay=java.lang.reflect.Proxy.newProxyInstance(CardFaceChecks.class.getClassLoader(),
    new Class<?>[]{net.minecraft.client.renderer.OrderedSubmitNodeCollector.class},(proxy,method,args)->{
     if(method.getName().equals("submitCustomGeometry")) {
      submissions[0]++;
      shimmerZ[0]=((com.mojang.blaze3d.vertex.PoseStack)args[0]).last().pose().m32();
     }
     return null;
    });
  var collector=(net.minecraft.client.renderer.SubmitNodeCollector)java.lang.reflect.Proxy.newProxyInstance(
    CardFaceChecks.class.getClassLoader(),new Class<?>[]{net.minecraft.client.renderer.SubmitNodeCollector.class},
    (proxy,method,args)->{
     if(method.getName().equals("order")){overlayOrder[0]=(int)args[0];return overlay;}
     if(method.getName().equals("submitCustomGeometry"))throw new AssertionError("Shimmer mixed into base face passes");
     if(method.getName().equals("submitTextBackground"))
      backgroundZ[0]=((com.mojang.blaze3d.vertex.PoseStack)args[0]).last().pose().m32();
     return null;
    });
  face.submit(pose,collector,15728880);
  if(submissions[0]!=1||overlayOrder[0]<=0||shimmerZ[0]-backgroundZ[0]<0.5F)
   throw new AssertionError("World shimmer needs a later pass and clearance above printed layers");
  if(!pose.last().pose().equals(original))throw new AssertionError("Shimmer leaked a world transform");
  System.out.println("PASS: stand shimmer uses one separate overlay submission above the card face, with balanced pose.");
 }
}
