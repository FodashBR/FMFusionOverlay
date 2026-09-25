package com.pitu.fmfusion;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.*;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.*;
import android.os.*;
import android.provider.Settings;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.net.Uri;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CaptureOverlayService extends Service {
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";
    private static final String CHANNEL = "fm_fusion_capture";
    private static final int NOTIF_ID = 7731;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private WindowManager wm;
    private View bubble;
    private View panel;
    private GameData gameData;
    private final AtomicBoolean analyzeRequested = new AtomicBoolean(false);
    private final AtomicBoolean analyzing = new AtomicBoolean(false);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Handler main;
    private int capW, capH, density;
    private Bitmap lastCapture;

    @Override public void onCreate() {
        super.onCreate();
        main = new Handler(Looper.getMainLooper());
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        createChannel();
        try { gameData = GameData.load(new File(getFilesDir(), "fm_cache.bin")); }
        catch (Exception e) { stopSelf(); }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("FM Fusion Overlay")
                .setContentText("Toque no botão FM sobre o DuckStation para analisar a mão")
                .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(NOTIF_ID, n);

        if (gameData == null) { stopSelf(); return START_NOT_STICKY; }

        if (projection == null && intent != null) {
            int code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
            Intent data = (Intent) intent.getParcelableExtra(EXTRA_RESULT_DATA);
            MediaProjectionManager m = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = m.getMediaProjection(code, data);
            if (projection == null) { stopSelf(); return START_NOT_STICKY; }
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopSelf(); }
            }, main);
            setupCapture(true);
            showBubble();
        }
        return START_NOT_STICKY;
    }

    private void setupCapture(boolean first) {
        int[] sz = screenSize();
        capW = sz[0]; capH = sz[1]; density = getResources().getDisplayMetrics().densityDpi;
        ImageReader old = imageReader;
        imageReader = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(this::onImage, main);
        if (first) {
            virtualDisplay = projection.createVirtualDisplay("FMFusionScreen", capW,capH,density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, main);
        } else if (virtualDisplay != null) {
            virtualDisplay.resize(capW,capH,density);
            virtualDisplay.setSurface(imageReader.getSurface());
        }
        if (old != null) old.close();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        main.postDelayed(() -> setupCapture(false), 300);
    }

    private int[] screenSize() {
        if (Build.VERSION.SDK_INT >= 30) {
            Rect b = wm.getMaximumWindowMetrics().getBounds();
            return new int[]{b.width(), b.height()};
        }
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        return new int[]{dm.widthPixels, dm.heightPixels};
    }

    private void onImage(ImageReader reader) {
        Image img = null;
        try {
            img = reader.acquireLatestImage();
            if (img == null) return;
            if (!analyzeRequested.compareAndSet(true,false) || !analyzing.compareAndSet(false,true)) return;
            final Bitmap bm = imageToBitmap(img);
            worker.submit(() -> analyze(bm));
        } catch (Exception e) {
            analyzing.set(false);
            showError("Falha na captura: " + e.getMessage());
        } finally {
            if (img != null) img.close();
        }
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane p = image.getPlanes()[0];
        ByteBuffer buf = p.getBuffer();
        int pixelStride = p.getPixelStride();
        int rowStride = p.getRowStride();
        int rowPadding = rowStride - pixelStride * capW;
        Bitmap padded = Bitmap.createBitmap(capW + rowPadding/pixelStride, capH, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buf);
        Bitmap cropped = Bitmap.createBitmap(padded, 0,0,capW,capH);
        if (cropped != padded) padded.recycle();
        return cropped;
    }

    private void analyze(Bitmap bm) {
        try {
            CardRecognizer.Match[] matches = CardRecognizer.recognize(bm, gameData);
            int previewW = Math.min(bm.getWidth(), 1600);
            int previewH = Math.round((float)bm.getHeight() * previewW / bm.getWidth());
            Bitmap preview = Bitmap.createScaledBitmap(bm, previewW, previewH, true);
            int[] hand = new int[5];
            double min = 1;
            for (int i=0;i<5;i++) { hand[i]=matches[i].cardId; min=Math.min(min,matches[i].score); }
            List<FusionEngine.FusionPath> fusions = FusionEngine.find(gameData, hand);
            final double confidence = min;
            main.post(() -> {
                if (lastCapture != null) lastCapture.recycle();
                lastCapture = preview;
                showResults(matches, fusions, confidence);
            });
        } catch (Exception e) {
            showError("Erro na análise: " + e.getMessage());
        } finally {
            bm.recycle();
            analyzing.set(false);
        }
    }

    private void showBubble() {
        if (!Settings.canDrawOverlays(this) || bubble != null) return;
        TextView v = new TextView(this);
        v.setText("FM"); v.setTextColor(Color.WHITE); v.setTextSize(16); v.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.argb(235, 90,55,170)); bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(dp(2), Color.WHITE); v.setBackground(bg);
        int size=dp(58);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(size,size,
                Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START; lp.x=dp(12); lp.y=dp(90);
        bubble=v;
        final float[] down = new float[4];
        v.setOnTouchListener((view,e)->{
            switch(e.getAction()){
                case MotionEvent.ACTION_DOWN: down[0]=e.getRawX();down[1]=e.getRawY();down[2]=lp.x;down[3]=lp.y;return true;
                case MotionEvent.ACTION_MOVE:
                    lp.x=(int)(down[2]+e.getRawX()-down[0]); lp.y=(int)(down[3]+e.getRawY()-down[1]); wm.updateViewLayout(v,lp); return true;
                case MotionEvent.ACTION_UP:
                    if(Math.abs(e.getRawX()-down[0])<dp(8) && Math.abs(e.getRawY()-down[1])<dp(8)) requestAnalysis();
                    return true;
            }
            return false;
        });
        wm.addView(v,lp);
    }

    private void requestAnalysis() {
        if (analyzing.get()) { Toast.makeText(this,"Já estou analisando…",Toast.LENGTH_SHORT).show(); return; }
        removePanel();
        analyzeRequested.set(true);
        Toast.makeText(this,"Lendo as 5 cartas…",Toast.LENGTH_SHORT).show();
    }

    private void showResults(CardRecognizer.Match[] m, List<FusionEngine.FusionPath> fusions, double minScore) {
        removePanel();
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(16),dp(14),dp(16),dp(14));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.argb(248,20,20,28)); bg.setCornerRadius(dp(16)); bg.setStroke(dp(1),Color.LTGRAY); box.setBackground(bg);
        TextView title=tv("Fusões disponíveis",20,true); box.addView(title);
        StringBuilder handText=new StringBuilder("Mão reconhecida:\n");
        for(int i=0;i<5;i++) handText.append(i+1).append(". ").append(gameData.cards[m[i].cardId].name)
                .append(String.format(Locale.US,"  (%.0f%%)\n",Math.max(0,m[i].score)*100));
        TextView hand=tv(handText.toString().trim(),14,false); hand.setTextColor(Color.LTGRAY); box.addView(hand,marginTop(8));
        TextView captureSize=tv("Captura: " + capW + " × " + capH,12,false);
        captureSize.setTextColor(Color.LTGRAY);box.addView(captureSize,marginTop(5));
        if(minScore<0.60){ TextView warn=tv("⚠ Reconhecimento com baixa confiança. Se alguma carta estiver errada, deixe a mão parada e toque em FM de novo.",13,true); warn.setTextColor(Color.rgb(255,190,90)); box.addView(warn,marginTop(8)); }

        if(fusions.isEmpty()) {
            box.addView(tv("Nenhuma fusão encontrada entre essas 5 cartas.",16,true),marginTop(14));
        } else {
            int limit=Math.min(10,fusions.size());
            for(int i=0;i<limit;i++) {
                FusionEngine.FusionPath fp=fusions.get(i);
                StringBuilder s=new StringBuilder();
                for(int j=0;j<fp.chain.length;j++){
                    if(j>0)s.append(" + ");
                    s.append(gameData.cards[fp.chain[j]].name);
                }
                GameData.Card r=gameData.cards[fp.result];
                s.append("\n→ ").append(r.name).append("  ").append(r.attack).append("/").append(r.defense);
                TextView row=tv(s.toString(),14,i==0); row.setPadding(0,dp(7),0,dp(7)); box.addView(row);
            }
            if(fusions.size()>10) box.addView(tv("+ "+(fusions.size()-10)+" outras combinações",13,false));
        }
        Button close=new Button(this);close.setText("Fechar");close.setAllCaps(false);close.setOnClickListener(v->removePanel());box.addView(close,marginTop(10));
        Button save=new Button(this);save.setText("Salvar captura para ajuste");save.setAllCaps(false);
        save.setOnClickListener(v->saveDiagnosticCapture());box.addView(save,marginTop(4));
        ScrollView sc=new ScrollView(this);sc.addView(box);panel=sc;
        int w=Math.min(screenSize()[0]-dp(28),dp(620));
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(w,WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.CENTER; wm.addView(sc,lp);
    }

    private TextView tv(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.WHITE);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private LinearLayout.LayoutParams marginTop(int x){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(x);return p;}

    private void showError(String s) { main.post(() -> Toast.makeText(this,s,Toast.LENGTH_LONG).show()); }
    private void saveDiagnosticCapture() {
        if (lastCapture == null) { showError("Nenhuma captura disponível"); return; }
        Bitmap snapshot = lastCapture.copy(Bitmap.Config.ARGB_8888, false);
        worker.submit(() -> {
            Uri uri = null;
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "FM-Fusion-" + System.currentTimeMillis() + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FM Fusion Overlay");
                uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("Não foi possível criar o arquivo");
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null || !snapshot.compress(Bitmap.CompressFormat.PNG, 100, out))
                        throw new IOException("Não foi possível salvar a imagem");
                }
                showError("Captura salva em Imagens/FM Fusion Overlay");
            } catch (Exception e) {
                if (uri != null) getContentResolver().delete(uri, null, null);
                showError("Falha ao salvar captura: " + e.getMessage());
            } finally { snapshot.recycle(); }
        });
    }
    private void removePanel(){ if(panel!=null){try{wm.removeView(panel);}catch(Exception ignored){}panel=null;} }
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}

    private void createChannel(){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel c=new NotificationChannel(CHANNEL,"FM Fusion Overlay",NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(c);
    }

    @Override public void onDestroy(){
        removePanel();
        if(bubble!=null){try{wm.removeView(bubble);}catch(Exception ignored){}bubble=null;}
        if(virtualDisplay!=null)virtualDisplay.release();
        if(imageReader!=null)imageReader.close();
        if(projection!=null)projection.stop();
        worker.shutdownNow();
        if(lastCapture!=null)lastCapture.recycle();
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent i){return null;}
}
