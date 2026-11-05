package craftbound.test;
import craftbound.card.CardCatalog;
public final class TestCatalogue {
 private static final CardCatalog.State FULL=load();
 private static CardCatalog.State load(){
  try(var stream=TestCatalogue.class.getResourceAsStream("/craftbound/test-cards.json")){
   if(stream==null)throw new IllegalStateException("Missing test-only card fixture");
   return CardCatalog.parse(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
  }catch(java.io.IOException e){throw new IllegalStateException(e);}
 }
 public static CardCatalog.State full(){return FULL;}
 public static void install(){CardCatalog.install(FULL);}
}
