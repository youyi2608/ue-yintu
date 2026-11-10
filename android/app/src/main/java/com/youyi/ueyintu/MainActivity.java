package com.youyi.ueyintu;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 1001;
    private static final int REQ_STORAGE = 1002;
    private WebView web;
    private String sharedData = null;
    private ValueCallback<Uri[]> filePathCallback = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT < 29 &&
                checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { android.Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQ_STORAGE);
        }
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "选择图片"), REQ_PICK);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "UEAndroid");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
        handle(getIntent(), 1200);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_PICK) {
            Uri[] out = null;
            if (res == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    ClipData cd = data.getClipData();
                    ArrayList<Uri> list = new ArrayList<Uri>();
                    for (int k = 0; k < cd.getItemCount(); k++) {
                        list.add(cd.getItemAt(k).getUri());
                    }
                    out = list.toArray(new Uri[list.size()]);
                } else if (data.getData() != null) {
                    out = new Uri[] { data.getData() };
                }
            }
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(out);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent, 200);
    }

    private void handle(Intent intent, final int delay) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri uri = null;
        if (Intent.ACTION_SEND.equals(action)) {
            uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null && !list.isEmpty()) uri = list.get(0);
        } else if (Intent.ACTION_VIEW.equals(action)) {
            uri = intent.getData();
        }
        if (uri == null) {
            Toast.makeText(this, "ue隐图只处理图片：请长按图片再分享", Toast.LENGTH_LONG).show();
            return;
        }
        sharedData = toDataUrl(uri);
        if (sharedData == null) return;
        web.postDelayed(new Runnable() {
            public void run() {
                web.evaluateJavascript("window.UEOnShared&&window.UEOnShared()", null);
            }
        }, delay);
    }

    private String toDataUrl(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            String type = getContentResolver().getType(uri);
            if (type == null) type = "image/png";
            return "data:" + type + ";base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() { Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show(); }
        });
    }

    private void doSave(String dataUrl, String name) {
        try {
            int i = dataUrl.indexOf(',');
            if (i < 0) throw new Exception("数据格式不对");
            String meta = dataUrl.substring(5, i);
            byte[] bytes = Base64.decode(dataUrl.substring(i + 1), Base64.DEFAULT);
            String mime = meta.split(";")[0];
            if (mime == null || mime.isEmpty()) mime = "image/png";
            String low = name.toLowerCase();
            if (!low.endsWith(".png") && !low.endsWith(".jpg") && !low.endsWith(".jpeg")) {
                name = name + (mime.contains("jpeg") ? ".jpg" : ".png");
            }
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                cv.put(MediaStore.Images.Media.MIME_TYPE, mime);
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ue隐图");
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new Exception("系统相册拒绝写入");
                OutputStream os = getContentResolver().openOutputStream(uri);
                os.write(bytes);
                os.close();
                toast("已保存到相册 · Pictures/ue隐图/" + name);
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ue隐图");
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, name);
                FileOutputStream fos = new FileOutputStream(out);
                fos.write(bytes);
                fos.close();
                MediaScannerConnection.scanFile(this, new String[] { out.getAbsolutePath() }, new String[] { mime }, null);
                toast("已保存到相册 · " + out.getAbsolutePath());
            }
        } catch (Exception e) {
            toast("保存失败：" + e.getMessage());
        }
    }

    public class Bridge {
        @JavascriptInterface
        public String getShared() { return sharedData == null ? "" : sharedData; }
        @JavascriptInterface
        public void clearShared() { sharedData = null; }
        @JavascriptInterface
        public void saveImage(final String dataUrl, final String fileName) {
            new Thread(new Runnable() {
                public void run() { doSave(dataUrl, fileName == null ? "ue隐图.png" : fileName); }
            }).start();
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}