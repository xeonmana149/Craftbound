package craftbound.client.match;

/** Circular fan shared by duel and multiplayer hands. Positive screen Y is downward. */
record HandFan(double x,double drop,double angle) {
    static HandFan at(int index,int count,double step,double degreesApart) {
        double apart=Math.toRadians(degreesApart);
        double radius=step/apart,angle=(index-(count-1)*.5)*apart;
        return new HandFan(radius*Math.sin(angle),radius*(1-Math.cos(angle)),angle);
    }
}
