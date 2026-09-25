package com.pitu.fmfusion;

import android.graphics.Bitmap;
import java.util.*;

public final class CardRecognizer {
    public static final class Match {
        public int cardId;
        public double score;
        public Match(int id, double s) { cardId=id; score=s; }
    }

    private static final int[] X_NATIVE = {30, 90, 150, 210, 270};
    private static final int Y_NATIVE = 157;

    public static Match[] recognize(Bitmap screenshot, GameData gd) {
        int sw = screenshot.getWidth(), sh = screenshot.getHeight();
        float vw, vh, vx, vy;
        if ((float)sw / sh >= 4f/3f) {
            vh = sh; vw = sh * 4f/3f; vx = (sw-vw)/2f; vy=0;
        } else {
            vw = sw; vh = sw * 3f/4f; vx=0; vy=(sh-vh)/2f;
        }
        Match[] out = new Match[5];
        for (int i=0;i<5;i++) {
            out[i] = recognizeOne(screenshot, gd, vx,vy,vw,vh, X_NATIVE[i], Y_NATIVE);
        }
        return out;
    }

    private static Match recognizeOne(Bitmap bm, GameData gd, float vx,float vy,float vw,float vh,int nx,int ny) {
        // Stage 1: fixed position at 20x16; keep 8 best.
        byte[] patchSmall = sampleGray(bm, vx,vy,vw,vh, nx,ny,40,32,20,16);
        PriorityQueue<Match> top = new PriorityQueue<>(Comparator.comparingDouble(a -> a.score));
        for (int c=0;c<GameData.CARD_COUNT;c++) {
            double s = ncc(patchSmall, gd.cards[c].thumbSmall);
            if (top.size()<8) top.add(new Match(c,s));
            else if (s > top.peek().score) { top.poll(); top.add(new Match(c,s)); }
        }
        ArrayList<Match> cand = new ArrayList<>(top);
        Match best = new Match(-1,-2);
        // Stage 2: full resolution, small offset search around expected native pixel.
        for (Match m : cand) {
            for (int dy=-2; dy<=2; dy++) for (int dx=-2; dx<=2; dx++) {
                byte[] p = sampleGray(bm, vx,vy,vw,vh, nx+dx,ny+dy,40,32,40,32);
                double s = ncc(p, gd.cards[m.cardId].thumbGray);
                if (s > best.score) best = new Match(m.cardId,s);
            }
        }
        return best;
    }

    private static byte[] sampleGray(Bitmap bm, float vx,float vy,float vw,float vh,
                                     int nx,int ny,int nw,int nh,int ow,int oh) {
        byte[] out = new byte[ow*oh];
        int bw=bm.getWidth(), bh=bm.getHeight();
        for (int y=0;y<oh;y++) {
            float gy = ny + (y+0.5f)*nh/oh;
            int sy = clamp(Math.round(vy + gy/240f*vh), 0, bh-1);
            for (int x=0;x<ow;x++) {
                float gx = nx + (x+0.5f)*nw/ow;
                int sx = clamp(Math.round(vx + gx/320f*vw), 0, bw-1);
                int p = bm.getPixel(sx,sy);
                int r=(p>>16)&255, g=(p>>8)&255, b=p&255;
                out[y*ow+x]=(byte)((77*r+150*g+29*b)>>8);
            }
        }
        return out;
    }

    private static int clamp(int x,int lo,int hi){return Math.max(lo,Math.min(hi,x));}

    private static double ncc(byte[] a, byte[] b) {
        int n=a.length;
        double ma=0,mb=0;
        for(int i=0;i<n;i++){ma+=(a[i]&255);mb+=(b[i]&255);} ma/=n;mb/=n;
        double num=0,da=0,db=0;
        for(int i=0;i<n;i++){
            double x=(a[i]&255)-ma,y=(b[i]&255)-mb;
            num+=x*y;da+=x*x;db+=y*y;
        }
        if(da<1e-6||db<1e-6)return -1;
        return num/Math.sqrt(da*db);
    }
}
