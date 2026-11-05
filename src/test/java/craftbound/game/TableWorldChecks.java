package craftbound.game;

import craftbound.client.block.CardMasterRenderer;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.core.*;

public final class TableWorldChecks {
    static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        DeveloperAccessChecks.check();
        MultiplayerTableChecks.check();
        var table=new BlockPos(12,64,-7);
        for(Direction side:Direction.Plane.HORIZONTAL){
            var plan=TableSeats.opposite(table,side);
            ok(TableSeats.oppositePositions(table,plan.first(),plan.second()),"Reuse opposite persistent chairs");
            ok(TableSeats.oppositePositions(table,plan.second(),plan.first()),"Either chair can be player seat");
            ok(!TableSeats.oppositePositions(table,plan.first(),plan.first()),"Reject duplicate chair position");
            ok(!TableSeats.oppositePositions(table,plan.first().above(),plan.second()),"Reject wrong chair height");
            ok(!TableSeats.oppositePositions(table,table.relative(side.getClockWise()),plan.second()),"Reject adjacent chairs");
            ok(plan.first().distManhattan(plan.second())==2,"Opposite sides");
            ok(plan.first().getY()==table.getY()&&plan.second().getY()==table.getY(),"Floor level");
            ok(Direction.fromYRot(plan.firstYaw())==side.getOpposite()&&Direction.fromYRot(plan.secondYaw())==side,"Faces inward");
            var alternate=TableSeats.plan(table,side,pos->!pos.equals(table.relative(side)));
            ok(alternate!=null&&!alternate.first().equals(table.relative(side))&&!alternate.second().equals(table.relative(side)),"Blocked side chooses another complete pair");
            ok(TableSeats.plan(table,side,pos->pos.equals(table.relative(side)))==null,"One clear side is insufficient");
            ok(TableSeats.plan(table,side,pos->false)==null,"Fully blocked table rejected");
            var a=net.minecraft.world.phys.Vec3.atBottomCenterOf(plan.first());
            var b=net.minecraft.world.phys.Vec3.atBottomCenterOf(plan.second());
            for(Direction approach:Direction.Plane.HORIZONTAL) {
                var player=net.minecraft.world.phys.Vec3.atBottomCenterOf(table.relative(approach,5));
                boolean swap=TableSeats.preferSecondChair(a,b,player,null,null);
                ok(swap==(player.distanceToSqr(b)<player.distanceToSqr(a)),"Choose among actual chairs even when player approaches a chairless side");
                ok(!TableSeats.preferSecondChair(a,b,player,null,plan.second()),"Master keeps second chair when player walks around");
                ok(TableSeats.preferSecondChair(a,b,player,null,plan.first()),"Master keeps first chair regardless of pair order");
                ok(TableSeats.preferSecondChair(a,b,player,plan.second(),null),"Respect player's already occupied chair");
            }
        }
        var root=CardMasterRenderer.SeatedModel.createLayer().bakeRoot();
        var model=new CardMasterRenderer.SeatedModel(root);
        var state=new CardMasterRenderer.State();state.seated=true;model.setupAnim(state);
        for(String side:new String[]{"right","left"}) {
            var thigh=root.getChild(side+"_seated_leg");
            ok(thigh.visible&&!root.getChild(side+"_leg").visible,"Show seated legs only");
            ok(thigh.xRot < -1.5 && thigh.getChild("shin").xRot > 1.5,"Master bends hips and knees");
            var pose=new com.mojang.blaze3d.vertex.PoseStack();
            thigh.translateAndRotate(pose);thigh.getChild("shin").translateAndRotate(pose);
            var down=pose.last().pose().transformDirection(new org.joml.Vector3f(0,1,0));
            ok(Math.abs(down.y-1)<0.001&&Math.abs(down.z)<0.001,"Shins hang vertically below knees");
        }
        ok(!root.getChild("body").getChild("jacket").visible&&root.getChild("body").getChild("seated_jacket").visible,"Standing robe does not hide seated knees");
        state.seated=false;model.setupAnim(state);
        ok(root.getChild("right_leg").xRot==0&&root.getChild("left_leg").xRot==0,"Standing restores normal legs");
        ok(root.getChild("right_leg").zRot==0&&root.getChild("left_leg").zRot==0,"No seated pose leaks into walking");
        ok(root.getChild("right_leg").visible&&root.getChild("left_leg").visible,"Walking legs restored");
        ok(!root.getChild("right_seated_leg").visible&&!root.getChild("left_seated_leg").visible,"Hide seated legs when standing");
        ok(root.getChild("body").getChild("jacket").visible&&!root.getChild("body").getChild("seated_jacket").visible,"Standing robe restored");
        System.out.println("PASS: all seating directions, blocked-side fallback/rejection and Card Master sitting/standing pose.");
    }
}
