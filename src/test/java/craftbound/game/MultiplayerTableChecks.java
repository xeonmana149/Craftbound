package craftbound.game;
import craftbound.block.MultiplayerTableBlock;
import java.util.*;
import net.minecraft.core.*;
/** Pure geometry checks: stable seat identity in every physical orientation. */
final class MultiplayerTableChecks {
    static void check() {
        var root=new BlockPos(37,65,-19);
        for(int mode:new int[]{MultiplayerTableBlock.DUOS,MultiplayerTableBlock.COMMANDER})
            for(Direction facing:Direction.Plane.HORIZONTAL) {
                var footprint=MultiplayerTableBlock.footprint(root,facing,mode);
                var chairs=MultiplayerTableBlock.chairs(root,facing,mode);
                ok(footprint.size()==(mode==1?3:9) && new HashSet<>(footprint).size()==footprint.size(),"Every occupied block reserved exactly once");
                ok(chairs.size()==4 && chairs.stream().map(MultiplayerTableBlock.Chair::pos).distinct().count()==4,"Four distinct physical seats");
                ok(chairs.stream().noneMatch(s->footprint.contains(s.pos())),"Chairs do not collide with the tabletop");
                for(var seat:chairs) {
                    ok(seat.pos().getY()==root.getY(),"Chair legs rest at the table floor");
                    ok(footprint.contains(seat.pos().relative(seat.facing())),"Every seat faces inward toward a tabletop piece");
                }
                if(mode==1) {
                    ok(chairs.get(0).facing()==chairs.get(2).facing(),"Ember teammates sit along one long side");
                    ok(chairs.get(1).facing()==chairs.get(3).facing(),"Tide teammates sit along the other long side");
                    ok(chairs.get(0).facing()==chairs.get(1).facing().getOpposite(),"Opposing teams face each other");
                    ok(chairs.get(0).pos().distManhattan(chairs.get(2).pos())==2,"Room between neighboring teammates");
                    var entities=chairs.stream().map(c->new RelayTables.EntitySeat(c.pos(),net.minecraft.world.phys.Vec3.atBottomCenterOf(c.pos()))).toList();
                    for(int seat=0;seat<4;seat++) {
                        var approach=net.minecraft.world.phys.Vec3.atBottomCenterOf(chairs.get(seat).pos().relative(chairs.get(seat).facing().getOpposite(),2));
                        ok(RelayTables.chooseChair(entities,approach,null,Set.of(),Set.of())==seat,"Standing approach picks the nearest chair on that side");
                        ok(RelayTables.chooseChair(entities,entities.get((seat+1)%4).center(),chairs.get(seat).pos(),Set.of(),Set.of())==seat,"Existing seat overrides proximity");
                        ok(RelayTables.chooseChair(entities,approach,null,Set.of(seat,seat^2),Set.of())==-1,"Full side never silently changes a player's team");
                        ok(RelayTables.chooseChair(entities,approach,null,Set.of(seat),Set.of())==(seat^2),"Reserved chair uses remaining teammate chair");
                        ok(RelayTables.chooseChair(entities,approach,null,Set.of(),Set.of(seat,seat^2))==-1,"Other sitters cannot be displaced");
                        ok(RelayTables.chooseChair(entities,approach,chairs.get(seat).pos(),Set.of(seat),Set.of())==-1,"Sitting in a reserved chair cannot steal its assignment");
                        ok(RelayTables.chooseChair(entities,approach,null,Set.of(),Set.of(seat))==(seat^2),"An unqueued sitter leaves only the teammate chair available");
                    }
                } else ok(chairs.stream().map(MultiplayerTableBlock.Chair::facing).distinct().count()==4,"Commander uses each of the four sides");
                for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++) {
                    var part=MultiplayerTableBlock.offset(root,facing,x,z);
                    ok(MultiplayerTableBlock.offset(part,facing,-x,-z).equals(root),"All rotated parts recover the same anchor");
                }
            }
    }
    private static void ok(boolean test,String reason){if(!test)throw new AssertionError(reason);}
}
