package com.pitu.fmfusion;

import java.io.*;
import java.util.*;

public final class GameData {
    public static final int CARD_COUNT = 722;
    public static final int THUMB_W = 40;
    public static final int THUMB_H = 32;

    public static final class Card {
        public int id;               // 0-based internally
        public String name;
        public int attack;
        public int defense;
        public byte[] thumbGray;     // 40*32, unsigned bytes
        public byte[] thumbSmall;    // 20*16, unsigned bytes
        public byte[] thumbRgb;      // 40*32*3, RGB for robust art comparison
        public byte[] thumbRgbTiny;  // 10*8*3, cached coarse search template
        public final Map<Integer, Integer> fusions = new HashMap<>();
    }

    public final Card[] cards = new Card[CARD_COUNT];

    public GameData() {
        for (int i = 0; i < CARD_COUNT; i++) {
            Card c = new Card();
            c.id = i;
            c.name = "Card #" + (i + 1);
            c.thumbGray = new byte[THUMB_W * THUMB_H];
            c.thumbSmall = new byte[20 * 16];
            c.thumbRgb = new byte[THUMB_W * THUMB_H * 3];
            c.thumbRgbTiny = new byte[10 * 8 * 3];
            cards[i] = c;
        }
    }

    public int fusionResult(int a, int b) {
        Integer x = cards[a].fusions.get(b);
        if (x != null) return x;
        x = cards[b].fusions.get(a);
        return x == null ? -1 : x;
    }

    public void save(File file) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeInt(0x464D4632); // FMF2: includes original card artwork colors
            out.writeInt(CARD_COUNT);
            for (Card c : cards) {
                out.writeUTF(c.name == null ? "" : c.name);
                out.writeInt(c.attack);
                out.writeInt(c.defense);
                out.writeInt(c.thumbRgb.length);
                out.write(c.thumbRgb);
                out.writeInt(c.fusions.size());
                for (Map.Entry<Integer,Integer> e : c.fusions.entrySet()) {
                    out.writeShort(e.getKey());
                    out.writeShort(e.getValue());
                }
            }
        }
    }

    public static GameData load(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != 0x464D4632)
                throw new IOException("Dados antigos sem cores. Importe o novo arquivo -cores.fmf.");
            if (in.readInt() != CARD_COUNT) throw new IOException("Quantidade de cartas inesperada");
            GameData gd = new GameData();
            for (int i = 0; i < CARD_COUNT; i++) {
                Card c = gd.cards[i];
                c.name = in.readUTF();
                c.attack = in.readInt();
                c.defense = in.readInt();
                int n = in.readInt();
                if (n != THUMB_W * THUMB_H * 3) throw new IOException("Miniatura inválida");
                in.readFully(c.thumbRgb);
                prepareThumbnails(c);
                int nf = in.readInt();
                if (nf < 0 || nf > CARD_COUNT) throw new IOException("Quantidade de fusões inválida");
                for (int j = 0; j < nf; j++) {
                    int material = in.readUnsignedShort(), result = in.readUnsignedShort();
                    if (material >= CARD_COUNT || result >= CARD_COUNT) throw new IOException("ID de carta inválido");
                    c.fusions.put(material, result);
                }
            }
            if (gd.fusionResult(3,15) != 68)
                throw new IOException("Dados de fusão incorretos. Importe o novo arquivo .fmf corrigido.");
            return gd;
        }
    }

    public static byte[] downsample2(byte[] src, int w, int h) {
        int nw = w / 2, nh = h / 2;
        byte[] out = new byte[nw * nh];
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
                int i0 = (2*y)*w + 2*x;
                int a = src[i0] & 0xff;
                int b = src[i0+1] & 0xff;
                int c = src[i0+w] & 0xff;
                int d = src[i0+w+1] & 0xff;
                out[y*nw+x] = (byte)((a+b+c+d)/4);
            }
        }
        return out;
    }

    public static void prepareThumbnails(Card c) {
        for(int i=0;i<THUMB_W*THUMB_H;i++) {
            int p=i*3;
            int r=c.thumbRgb[p]&255, g=c.thumbRgb[p+1]&255, b=c.thumbRgb[p+2]&255;
            c.thumbGray[i]=(byte)((77*r+150*g+29*b)>>8);
        }
        c.thumbSmall=downsample2(c.thumbGray,THUMB_W,THUMB_H);
        for(int y=0;y<8;y++) for(int x=0;x<10;x++) for(int channel=0;channel<3;channel++) {
            int sum=0;
            for(int yy=0;yy<4;yy++) for(int xx=0;xx<4;xx++)
                sum+=c.thumbRgb[((y*4+yy)*THUMB_W+x*4+xx)*3+channel]&255;
            c.thumbRgbTiny[(y*10+x)*3+channel]=(byte)(sum/16);
        }
    }
}
