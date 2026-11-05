package craftbound.game;

import com.mojang.authlib.GameProfile;
import craftbound.block.*;
import craftbound.entity.CardSeatEntity;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Real server placement, occupied chair rejection, group mounts and one-item teardown. */
public final class MultiplayerFurnitureScenario {
    private static void ok(boolean value,String issue){if(!value)throw new AssertionError(issue);}
    public static void prepare(ServerLevel level,BlockPos root) {
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++) {
            level.setBlockAndUpdate(root.offset(x,-1,z),Blocks.STONE.defaultBlockState());
            for(int y=0;y<3;y++)level.setBlockAndUpdate(root.offset(x,y,z),Blocks.AIR.defaultBlockState());
        }
    }
    public static void place(ServerLevel level,BlockPos root,Block block,Direction facing) {
        var state=block.defaultBlockState().setValue(MultiplayerTableBlock.FACING,facing);
        level.setBlockAndUpdate(root,state);
        block.setPlacedBy(level,root,state,null,new ItemStack(block));
    }
    public static void check(ClientGameTestContext ctx,TestSingleplayerContext world) {
        world.getServer().runOnServer(server->{
            var level=server.overworld(); var real=server.getPlayerList().getPlayers().getFirst();
            var base=real.blockPosition().offset(10,0,0);
            var saved=real.getMainHandItem().copy();float yaw=real.getYRot();
            try {
                for(var block:new Block[]{ModBlocks.TEAM_TABLE,ModBlocks.COMMANDER_TABLE})for(Direction facing:Direction.Plane.HORIZONTAL) {
                    BlockPos root=base;
                    prepare(level,root);
                    // Chunks containing chair entity positions must be entity-ticking before furniture is created.
                    for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)level.getChunkAt(root.offset(x,0,z));
                    var table=(MultiplayerTableBlock)block;
                    real.setYRot(facing.getOpposite().toYRot());
                    real.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block));
                    var context=new BlockPlaceContext(new UseOnContext(real,InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(root.below()).add(0,.5,0),Direction.UP,root.below(),false)));
                    ok(context.getClickedPos().equals(root),"Placement context resolves the anchor");
                    ok(table.getStateForPlacement(context)!=null,"Complete clear footprint can be placed");
                    var footprint=MultiplayerTableBlock.footprint(root,facing,table.mode());
                    var obstruction=footprint.stream().filter(p->!p.equals(root)).findFirst().orElseThrow();
                    level.setBlockAndUpdate(obstruction,Blocks.STONE.defaultBlockState());
                    ok(table.getStateForPlacement(context)==null,"An obstruction in any tabletop piece rejects placement");
                    level.setBlockAndUpdate(obstruction,Blocks.AIR.defaultBlockState());
                    var chairPlans=MultiplayerTableBlock.chairs(root,facing,table.mode());
                    var floor=chairPlans.getFirst().pos().below();
                    level.setBlockAndUpdate(floor,Blocks.AIR.defaultBlockState());
                    ok(table.getStateForPlacement(context)==null,"Missing chair support rejects placement");
                    level.setBlockAndUpdate(floor,Blocks.STONE.defaultBlockState());
                    place(level,root,block,facing);
                    ok(table.complete(level,root,level.getBlockState(root)),"Every table section is placed");
                    for(var part:footprint)ok(CardTableBlock.anchor(level,part).equals(root),"Every part routes to the shared anchor");
                    var seats=TableSeats.ensure(level,root,facing);
                    ok(seats.size()==4,"Four permanent seats appear");
                    var uuids=seats.stream().map(CardSeatEntity::getUUID).toList();
                    ok(TableSeats.ensure(level,root,facing).stream().map(CardSeatEntity::getUUID).toList().equals(uuids),"Maintenance reuses the chairs without duplicates");
                    var players=new ArrayList<ServerPlayer>();
                    for(int s=0;s<4;s++) {
                        var fake=FakePlayer.get(level,new GameProfile(UUID.randomUUID(),"Furniture"+s));
                        fake.setPos(root.getX()+4,root.getY(),root.getZ()+4);players.add(fake);
                    }
                    var stranger=FakePlayer.get(level,new GameProfile(UUID.randomUUID(),"ChairGuest"));
                    ok(stranger.startRiding(seats.get(0),true,false),"Chair is rideable and serializable");
                    ok(MultiplayerSeats.start(level,root,players)==null,"A spectator cannot be displaced from an occupied chair");
                    stranger.stopRiding();
                    // Swap two pre-seated captains: physical seats must follow their selected team IDs.
                    ok(players.get(0).startRiding(seats.get(2),true,false),"Participant can sit before joining");
                    ok(players.get(2).startRiding(seats.get(0),true,false),"Both swapped chairs occupied");
                    var group=MultiplayerSeats.start(level,root,players);
                    ok(group!=null,"All four players mount atomically");
                    for(int s=0;s<4;s++)ok(players.get(s).getVehicle()==seats.get(s),"Match seat ID matches physical seat "+s);
                    group.close();
                    ok(players.stream().noneMatch(ServerPlayer::isPassenger),"Closing a match releases every rider");
                    ok(TableSeats.ensure(level,root,facing).size()==4,"Finishing a match keeps its permanent chairs");
                    var area=new AABB(root).inflate(4);
                    level.getEntitiesOfClass(ItemEntity.class,area).forEach(e->e.discard());
                    level.destroyBlock(obstruction,true);
                    ok(footprint.stream().noneMatch(p->level.getBlockState(p).is(block)),"Breaking any piece removes the whole table");
                    ok(level.getEntitiesOfClass(CardSeatEntity.class,area,s->s.belongsTo(root)).isEmpty(),"Table removal removes all four chairs");
                    int drops=level.getEntitiesOfClass(ItemEntity.class,area).stream().filter(e->e.getItem().is(block.asItem())).mapToInt(e->e.getItem().getCount()).sum();
                    ok(drops==1,"Multiblock removal drops exactly one item, got "+drops);
                    level.getEntitiesOfClass(ItemEntity.class,area).forEach(e->e.discard());
                }
            } finally {real.setItemInHand(InteractionHand.MAIN_HAND,saved);real.setYRot(yaw);}
        });
    }
    private MultiplayerFurnitureScenario(){}
}
