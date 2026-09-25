package com.pitu.fmfusion;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;

/** Minimal ISO-9660 reader for PS1 BIN (2352-byte Mode2/Form1 sectors) and plain 2048-byte ISO. */
public final class PsxBinIsoReader implements Closeable {
    private final FileChannel ch;
    private final int sectorSize;
    private final int userOffset;

    public static final class Entry {
        public String name;
        public int extent;
        public int size;
        public boolean directory;
        public Entry(String n, int e, int s, boolean d) { name=n; extent=e; size=s; directory=d; }
    }

    public PsxBinIsoReader(FileChannel ch) throws IOException {
        this.ch = ch;
        if (looksLike(2352, 24)) { sectorSize = 2352; userOffset = 24; }
        else if (looksLike(2048, 0)) { sectorSize = 2048; userOffset = 0; }
        else throw new IOException("Formato não reconhecido. Use o BIN/ISO do disco de Forbidden Memories.");
    }

    private boolean looksLike(int ss, int off) throws IOException {
        long pos = 16L * ss + off;
        if (ch.size() < pos + 8) return false;
        ByteBuffer b = ByteBuffer.allocate(8);
        ch.position(pos);
        if (ch.read(b) != 8) return false;
        byte[] a = b.array();
        return a[0] == 1 && a[1] == 'C' && a[2] == 'D' && a[3] == '0' && a[4] == '0' && a[5] == '1';
    }

    public byte[] readFile(String wanted) throws IOException {
        byte[] pvd = readSector(16);
        if (pvd[0] != 1 || pvd[1] != 'C') throw new IOException("ISO9660 inválido");
        int rootLen = pvd[156] & 0xff;
        if (rootLen < 34) throw new IOException("Diretório raiz inválido");
        Entry root = parseRecord(pvd, 156);
        Entry hit = findRecursive(root, normalize(wanted), 0);
        if (hit == null) throw new FileNotFoundException(wanted + " não encontrado dentro do disco");
        return readExtent(hit.extent, hit.size);
    }

    private Entry findRecursive(Entry dir, String target, int depth) throws IOException {
        if (depth > 5) return null;
        byte[] data = readExtent(dir.extent, dir.size);
        int p = 0;
        while (p < data.length) {
            int len = data[p] & 0xff;
            if (len == 0) {
                p = ((p / 2048) + 1) * 2048;
                continue;
            }
            if (p + len > data.length) break;
            Entry e = parseRecord(data, p);
            p += len;
            if (e == null) continue;
            String n = normalize(e.name);
            if (n.equals(target)) return e;
        }
        // second pass for subdirectories
        p = 0;
        while (p < data.length) {
            int len = data[p] & 0xff;
            if (len == 0) { p = ((p / 2048) + 1) * 2048; continue; }
            if (p + len > data.length) break;
            Entry e = parseRecord(data, p);
            p += len;
            if (e != null && e.directory && !e.name.equals(".") && !e.name.equals("..")) {
                Entry found = findRecursive(e, target, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String normalize(String s) {
        String x = s.toUpperCase(Locale.ROOT);
        int sem = x.indexOf(';');
        if (sem >= 0) x = x.substring(0, sem);
        return x;
    }

    private Entry parseRecord(byte[] d, int p) {
        int len = d[p] & 0xff;
        if (len < 34) return null;
        int extent = le32(d, p + 2);
        int size = le32(d, p + 10);
        int flags = d[p + 25] & 0xff;
        int nameLen = d[p + 32] & 0xff;
        if (p + 33 + nameLen > d.length) return null;
        String name;
        if (nameLen == 1 && d[p+33] == 0) name = ".";
        else if (nameLen == 1 && d[p+33] == 1) name = "..";
        else name = new String(d, p + 33, nameLen, java.nio.charset.StandardCharsets.US_ASCII);
        return new Entry(name, extent, size, (flags & 0x02) != 0);
    }

    private byte[] readExtent(int lba, int size) throws IOException {
        byte[] out = new byte[size];
        int copied = 0;
        int sector = lba;
        while (copied < size) {
            byte[] s = readSector(sector++);
            int n = Math.min(2048, size - copied);
            System.arraycopy(s, 0, out, copied, n);
            copied += n;
        }
        return out;
    }

    private byte[] readSector(int lba) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(2048);
        ch.position((long)lba * sectorSize + userOffset);
        int got = 0;
        while (got < 2048) {
            int n = ch.read(b);
            if (n < 0) throw new EOFException("Fim inesperado do disco");
            got += n;
        }
        return b.array();
    }

    private static int le32(byte[] a, int p) {
        return (a[p]&255) | ((a[p+1]&255)<<8) | ((a[p+2]&255)<<16) | ((a[p+3]&255)<<24);
    }

    @Override public void close() throws IOException { ch.close(); }
}
