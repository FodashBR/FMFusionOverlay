package com.pitu.fmfusion;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.*;

public final class RomParser {
    private static final int CARD_COUNT = GameData.CARD_COUNT;

    public interface Progress { void onProgress(int percent, String text); }

    public static GameData parse(FileChannel ch, Progress progress) throws IOException {
        if (progress != null) progress.onProgress(5, "Abrindo o disco…");
        byte[] slus, mrg;
        try (PsxBinIsoReader iso = new PsxBinIsoReader(ch)) {
            slus = iso.readFile("SLUS_014.11");
            if (progress != null) progress.onProgress(20, "Lendo dados das cartas…");
            mrg = iso.readFile("WA_MRG.MRG");
        }
        require(slus.length > 0x1C7000, "SLUS incompatível");
        require(mrg.length > 0xB97800, "WA_MRG.MRG incompatível");

        GameData gd = new GameData();
        parseStatsAndNames(slus, gd);
        if (progress != null) progress.onProgress(45, "Extraindo 722 miniaturas…");
        parseThumbs(mrg, gd, progress);
        if (progress != null) progress.onProgress(85, "Lendo regras de fusão…");
        parseFusions(mrg, gd);
        if (progress != null) progress.onProgress(100, "Pronto");
        return gd;
    }

    private static void parseStatsAndNames(byte[] slus, GameData gd) throws IOException {
        int p = 0x1C4A44;
        for (int i = 0; i < CARD_COUNT; i++) {
            long v = u32(slus, p); p += 4;
            gd.cards[i].attack = (int)(v & 0x1FF) * 10;
            gd.cards[i].defense = (int)((v >>> 9) & 0x1FF) * 10;
        }
        int names = 0x1C6002;
        for (int i = 0; i < CARD_COUNT; i++) {
            int off = u16(slus, names + i*2);
            int addr = 0x1C6800 + off - 0x6000;
            if (addr < 0 || addr >= slus.length) throw new IOException("Offset de nome inválido");
            String s = readGameString(slus, addr);
            if (!s.isEmpty()) gd.cards[i].name = s;
        }
    }

    private static void parseThumbs(byte[] mrg, GameData gd, Progress progress) throws IOException {
        final int base = 0x16BAE0;
        final int stride = 14336;
        final int pxCount = 40*32;
        for (int i = 0; i < CARD_COUNT; i++) {
            int start = base + i*stride;
            int palette = start + pxCount;
            if (palette + 512 > mrg.length) throw new IOException("MRG terminou durante as miniaturas");
            byte[] gray = gd.cards[i].thumbGray;
            for (int j = 0; j < pxCount; j++) {
                int idx = mrg[start+j] & 0xff;
                int c = u16(mrg, palette + idx*2);
                int r = (c & 31) * 8;
                int g = ((c >>> 5) & 31) * 8;
                int b = ((c >>> 10) & 31) * 8;
                int y = (77*r + 150*g + 29*b) >>> 8;
                gray[j] = (byte)y;
            }
            gd.cards[i].thumbSmall = GameData.downsample2(gray, 40, 32);
            if (progress != null && i % 72 == 0) progress.onProgress(45 + (i*35/CARD_COUNT), "Miniaturas: " + i + "/722");
        }
    }

    private static void parseFusions(byte[] mrg, GameData gd) throws IOException {
        int base = 0xB87800;
        for (int i = 0; i < CARD_COUNT; i++) {
            int pos = u16(mrg, base + i*2 + 2);
            if (pos == 0) continue;
            int p = base + pos;
            int amount = mrg[p++] & 0xff;
            if (amount == 0) amount = 511 - (mrg[p++] & 0xff);
            int left = amount;
            while (left > 0) {
                if (p + 5 > mrg.length) throw new IOException("Tabela de fusão inválida");
                int n3 = mrg[p++] & 0xff;
                int n4 = mrg[p++] & 0xff;
                int n5 = mrg[p++] & 0xff;
                int n6 = mrg[p++] & 0xff;
                int n7 = mrg[p++] & 0xff;

                int card2 = (((n3 & 3) << 8) | n4) - 1;
                int result = ((((n3 >>> 2) & 3) << 8) | n5) - 1;
                if (valid(card2) && valid(result)) gd.cards[i].fusions.put(card2, result);
                left--;
                if (left <= 0) break;

                int card2b = ((((n3 >>> 4) & 3) << 8) | n6) - 1;
                int resultb = ((((n3 >>> 6) & 3) << 8) | n7) - 1;
                if (valid(card2b) && valid(resultb)) gd.cards[i].fusions.put(card2b, resultb);
                left--;
            }
        }
    }

    private static boolean valid(int x) { return x >= 0 && x < CARD_COUNT; }

    private static void require(boolean ok, String msg) throws IOException { if (!ok) throw new IOException(msg); }

    private static int u16(byte[] a, int p) {
        return (a[p]&255) | ((a[p+1]&255)<<8);
    }
    private static long u32(byte[] a, int p) {
        return (a[p]&255L) | ((a[p+1]&255L)<<8) | ((a[p+2]&255L)<<16) | ((a[p+3]&255L)<<24);
    }

    private static String readGameString(byte[] a, int p) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100 && p+i < a.length; i++) {
            int b = a[p+i] & 0xff;
            if (b == 0xff) break;
            if (b == 0xfe) { sb.append(' '); continue; }
            String s = CHAR_MAP.get(b);
            if (s != null) sb.append(s);
        }
        return sb.toString().trim();
    }

    private static final Map<Integer,String> CHAR_MAP = buildCharMap();
    private static Map<Integer,String> buildCharMap() {
        Map<Integer,String> m = new HashMap<>();
        String[] rows = {
            "00=","01=e","02=t","03=a","04=o","05=i","06=n","07=s","08=r","09=h","0A=l","0B=.","0C=d","0D=u","0E=m","0F=c",
            "10=g","11=y","12=w","13=f","14=p","15=b","16=k","17=!","18=A","19=v","1A=I","1B='","1C=T","1D=S","1E=M","1F=,",
            "20=D","21=O","22=W","23=H","24=Y","25=E","26=R","29=G","2A=L","2B=C","2C=N","2D=B","2E=?","2F=P","30=-","31=F",
            "32=z","33=K","34=j","35=U","36=x","37=q","38=0","39=V","3A=2","3B=J","3C=#","3D=1","3E=Q","3F=Z","40=\"","41=3",
            "42=5","43=&","44=/","45=7","46=X","48=:","4A=4","4E=6","4F=$","50=*","56=+","57=8","59=9","5B=%"
        };
        for (String r : rows) {
            int k = Integer.parseInt(r.substring(0,2), 16);
            m.put(k, r.substring(3));
        }
        m.put(0x00, " "); // o arquivo .tbl usa o valor em branco como espaço
        return m;
    }
}
