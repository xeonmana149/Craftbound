package craftbound.client.match;

public final class PortraitFragmentChecks {
    public static void run() {
        for(int seed=0;seed<80;seed++){
            var shards=PortraitFragments.create(seed);
            int[][] coverage=new int[PortraitFragments.GRID][PortraitFragments.GRID];
            var sizes=new java.util.HashSet<Integer>();
            int patchCount=0;
            for(var shard:shards){
                if(Math.hypot(shard.dx(),shard.dy())>1.0001)throw new AssertionError("Unbounded portrait burst");
                int area=0;
                for(var p:shard.patches()){
                    patchCount++;
                    for(int y=p.y();y<p.y()+p.height();y++)for(int x=p.x();x<p.x()+p.width();x++){coverage[y][x]++;area++;}
                }
                sizes.add(area);
            }
            for(var row:coverage)for(int n:row)if(n!=1)throw new AssertionError("Portrait pieces overlap or lose pixels");
            if(sizes.size()<3||patchCount>144)throw new AssertionError("Uniform shards or excessive render budget");
        }
        System.out.println("PASS: irregular portrait partitions preserve all pixels, varied sizes and bounded travel.");
    }
}
