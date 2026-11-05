package craftbound.client.match;

public final class ToolTheftChecks {
    public static void run() {
        var start=ToolTheftMotion.sample(0);
        var grab=ToolTheftMotion.sample(ToolTheftMotion.GRAB/(float)ToolTheftMotion.DURATION);
        var home=ToolTheftMotion.sample(ToolTheftMotion.HOME/(float)ToolTheftMotion.DURATION);
        var end=ToolTheftMotion.sample(1);
        if(start.travel()!=0||start.carrying()||grab.travel()!=1||!grab.carrying())
            throw new AssertionError("Fox must reach tool before carrying it");
        if(home.travel()!=0||home.toolScale()!=1||end.travel()!=0||end.toolScale()!=0)
            throw new AssertionError("Tool must be carried home before tucking away");
        for(int ms=0;ms<=ToolTheftMotion.DURATION;ms++) {
            var f=ToolTheftMotion.sample(ms/(float)ToolTheftMotion.DURATION);
            if(f.travel()<0||f.travel()>1||f.lift()<-.001||f.lift()>18.001||f.toolScale()<0||f.toolScale()>1)
                throw new AssertionError("Theft motion must stay bounded");
            if(ms<ToolTheftMotion.GRAB&&f.carrying())throw new AssertionError("Premature tool removal");
        }
        if(CombatVfx.fox(null)||CombatVfx.fox(net.minecraft.resources.Identifier.parse("craftbound:missing_fox")))
            throw new AssertionError("Unknown cards cannot trigger fox-specific animation");
        System.out.println("PASS: fox theft reaches tool, grabs, carries it home then shrinks it away within a bounded 820ms beat.");
    }
}
