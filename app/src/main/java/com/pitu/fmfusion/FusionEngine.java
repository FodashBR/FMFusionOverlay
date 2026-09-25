package com.pitu.fmfusion;

import java.util.*;

public final class FusionEngine {
    public static final class FusionPath {
        public final int result;
        public final int[] chain;
        public FusionPath(int result, int[] chain) { this.result=result; this.chain=chain; }
    }

    public static List<FusionPath> find(GameData gd, int[] hand) {
        LinkedHashMap<String,FusionPath> unique = new LinkedHashMap<>();
        for (int i = 0; i < hand.length; i++) {
            boolean[] used = new boolean[hand.length];
            used[i] = true;
            int[] chain = new int[hand.length];
            chain[0] = hand[i];
            dfs(gd, hand, used, hand[i], chain, 1, unique);
        }
        ArrayList<FusionPath> out = new ArrayList<>(unique.values());
        out.sort((a,b) -> {
            int d = Integer.compare(gd.cards[b.result].attack, gd.cards[a.result].attack);
            if (d != 0) return d;
            d = Integer.compare(b.chain.length, a.chain.length);
            if (d != 0) return d;
            return gd.cards[a.result].name.compareToIgnoreCase(gd.cards[b.result].name);
        });
        return out;
    }

    private static void dfs(GameData gd, int[] hand, boolean[] used, int current, int[] chain, int len,
                            LinkedHashMap<String,FusionPath> unique) {
        if (len >= 2) {
            int[] copy = Arrays.copyOf(chain, len);
            String key = current + ":" + Arrays.toString(copy);
            unique.putIfAbsent(key, new FusionPath(current, copy));
        }
        for (int i = 0; i < hand.length; i++) {
            if (used[i]) continue;
            int r = gd.fusionResult(current, hand[i]);
            if (r < 0) continue;
            used[i] = true;
            chain[len] = hand[i];
            dfs(gd, hand, used, r, chain, len+1, unique);
            used[i] = false;
        }
    }
}
