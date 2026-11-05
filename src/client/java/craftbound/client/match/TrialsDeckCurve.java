package craftbound.client.match;

import java.util.List;

/** Pure presentation math: candidate cards never alter the server-owned expedition deck. */
public final class TrialsDeckCurve {
    public static final int BUCKETS=11;
    public record Snapshot(int[] current,int[] projected,int size,int projectedSize,
                           double average,double projectedAverage) {
        public boolean changed(){return size!=projectedSize||Double.compare(average,projectedAverage)!=0||!java.util.Arrays.equals(current,projected);}
    }
    public static Snapshot of(List<Integer> costs,Integer added,Integer removed) {
        int[] current=new int[BUCKETS];
        int size=0,total=0;
        for(int cost:costs)if(cost>=0){current[Math.min(BUCKETS-1,cost)]++;size++;total+=cost;}
        int[] projected=current.clone();
        int nextSize=size,nextTotal=total;
        // Remove one actual copy only, including when the destination shares its bucket.
        if(removed!=null&&removed>=0&&costs.contains(removed)) {
            projected[Math.min(BUCKETS-1,removed)]--;nextSize--;nextTotal-=removed;
        }
        if(added!=null&&added>=0){projected[Math.min(BUCKETS-1,added)]++;nextSize++;nextTotal+=added;}
        return new Snapshot(current,projected,size,nextSize,size==0?0:total/(double)size,
                nextSize==0?0:nextTotal/(double)nextSize);
    }
    private TrialsDeckCurve(){}
}
