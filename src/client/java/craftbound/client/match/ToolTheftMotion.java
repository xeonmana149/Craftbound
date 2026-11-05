package craftbound.client.match;

/** One bounded out-and-back theft beat; grab and removal share the same timestamp. */
final class ToolTheftMotion {
    static final int GRAB = 260, RETURN_START = 310, HOME = 650, DURATION = 820;
    record Frame(float travel, float lift, boolean carrying, float toolScale) {}
    static Frame sample(float progress) {
        float time=Math.clamp(progress,0,1)*DURATION;
        float travel=time<GRAB?smooth(time/GRAB):time<RETURN_START?1:1-smooth((time-RETURN_START)/(HOME-RETURN_START));
        float lift=18*(float)Math.sin(Math.PI*travel);
        float scale=time<HOME?1:1-smooth((time-HOME)/(DURATION-HOME));
        return new Frame(travel,lift,time>=GRAB,scale);
    }
    private static float smooth(float t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
    private ToolTheftMotion(){}
}
