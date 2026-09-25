package com.pitu.fmfusion;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int REQ_ROM = 1001;
    private static final int REQ_CAPTURE = 1002;
    private static final int REQ_NOTIF = 1003;

    private TextView status;
    private ProgressBar progress;
    private Button startButton;
    private File cacheFile;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        cacheFile = new File(getFilesDir(), "fm_cache.bin");
        setContentView(buildUi());
        updateStatus();
    }

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(20,20,24));
        sv.addView(root, new ScrollView.LayoutParams(-1,-1));

        TextView title = text("FM Fusion Overlay", 27, true);
        root.addView(title);
        TextView sub = text("Assistente de fusões para Yu-Gi-Oh! Forbidden Memories no DuckStation.", 16, false);
        sub.setTextColor(Color.LTGRAY);
        root.addView(sub, lpTop(8));

        status = text("", 16, true);
        root.addView(status, lpTop(22));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); progress.setVisibility(View.GONE);
        root.addView(progress, lpTop(10));

        Button rom = button("1. Selecionar ROM do Forbidden Memories (.bin/.iso)");
        rom.setOnClickListener(v -> chooseRom());
        root.addView(rom, lpTop(22));

        Button overlay = button("2. Permitir botão flutuante");
        overlay.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        root.addView(overlay, lpTop(12));

        startButton = button("3. Iniciar assistente");
        startButton.setOnClickListener(v -> startAssistant());
        root.addView(startButton, lpTop(12));

        Button stop = button("Parar assistente");
        stop.setOnClickListener(v -> stopService(new Intent(this, CaptureOverlayService.class)));
        root.addView(stop, lpTop(12));

        TextView help = text(
                "Como usar:\n\n" +
                "• Na primeira vez, selecione o mesmo BIN/ISO que você usa no DuckStation. O app extrai localmente as 722 miniaturas e as regras de fusão.\n\n" +
                "• Inicie o assistente e aceite a captura de tela. Depois abra o DuckStation.\n\n" +
                "• Quando as 5 cartas estiverem na mão, toque no botão flutuante “FM”. Ele reconhece as cartas e mostra as fusões possíveis.\n\n" +
                "Nada da sua tela é enviado para a internet.", 15, false);
        help.setTextColor(Color.LTGRAY);
        root.addView(help, lpTop(24));
        return sv;
    }

    private void chooseRom() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_ROM);
    }

    private void startAssistant() {
        if (!cacheFile.exists()) {
            toast("Primeiro selecione a ROM e aguarde a indexação.");
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Ative a permissão de sobreposição para o botão flutuante.");
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        MediaProjectionManager m = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_ROM && result == RESULT_OK && data != null && data.getData() != null) {
            indexRom(data.getData());
        } else if (req == REQ_CAPTURE && result == RESULT_OK && data != null) {
            Intent s = new Intent(this, CaptureOverlayService.class);
            s.putExtra(CaptureOverlayService.EXTRA_RESULT_CODE, result);
            s.putExtra(CaptureOverlayService.EXTRA_RESULT_DATA, data);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
            toast("Assistente iniciado. Abra o DuckStation e toque em FM quando quiser analisar a mão.");
            moveTaskToBack(true);
        }
    }

    private void indexRom(Uri uri) {
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        startButton.setEnabled(false);
        status.setText("Indexando ROM…");
        exec.submit(() -> {
            try (android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
                if (pfd == null) throw new IOException("Não foi possível abrir o arquivo");
                FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
                FileChannel ch = fis.getChannel();
                GameData gd = RomParser.parse(ch, (pct, txt) -> runOnUiThread(() -> {
                    progress.setProgress(pct);
                    status.setText(txt);
                }));
                File tmp = new File(getFilesDir(), "fm_cache.tmp");
                gd.save(tmp);
                if (cacheFile.exists() && !cacheFile.delete()) throw new IOException("Não foi possível substituir o cache antigo");
                if (!tmp.renameTo(cacheFile)) throw new IOException("Não foi possível salvar o índice");
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    startButton.setEnabled(true);
                    status.setText("✓ ROM indexada: 722 cartas prontas");
                    toast("ROM indexada com sucesso.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    startButton.setEnabled(true);
                    status.setText("Erro ao indexar: " + e.getMessage());
                    new AlertDialog.Builder(this).setTitle("Não consegui ler essa ROM")
                            .setMessage(e.getMessage() + "\n\nA primeira versão foi feita para a edição NTSC-U (SLUS_014.11) de Forbidden Memories.")
                            .setPositiveButton("OK", null).show();
                });
            }
        });
    }

    private void updateStatus() {
        if (cacheFile.exists()) status.setText("✓ Dados do jogo já indexados");
        else status.setText("Aguardando a ROM do jogo");
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.WHITE);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b; }
    private LinearLayout.LayoutParams lpTop(int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(top); return p; }
    private int dp(int x) { return Math.round(x*getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }

    @Override protected void onDestroy() { super.onDestroy(); exec.shutdownNow(); }
}
