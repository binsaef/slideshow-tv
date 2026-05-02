package com.eradah.slideshow;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 100;
    private static final int PERMISSION_REQUEST   = 200;

    private static final List<String> IMG_EXTS = Arrays.asList(
        "jpg","jpeg","png","webp","gif","bmp","avif"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );

        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);

        setupWebView();
        requestPermissionsIfNeeded();
        webView.loadUrl("file:///android_asset/slideshow.html");
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.addJavascriptInterface(new SlideshowBridge(), "SlideshowBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                webView.evaluateJavascript(
                    "window._isAndroidApp = true;" +
                    "window._androidSdkVersion = " + Build.VERSION.SDK_INT + ";",
                    null
                );
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb,
                                              FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                Intent intent = params.createIntent();
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(Intent.createChooser(intent, "اختر الصور"), FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });
    }

    class AndroidBridge {

        @JavascriptInterface
        public String scanDirectory(String dirPath) {
            File dir = new File(dirPath);
            if (!dir.exists() || !dir.isDirectory()) return "[]";
            StringBuilder sb = new StringBuilder("[");
            scanDir(dir, sb, new boolean[]{false});
            if (sb.length() > 1) sb.setLength(sb.length() - 1);
            sb.append("]");
            return sb.toString();
        }

        private void scanDir(File dir, StringBuilder sb, boolean[] first) {
            File[] files = dir.listFiles();
            if (files == null) return;
            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : files) {
                if (f.isFile() && isImage(f.getName())) {
                    if (!first[0]) first[0] = true; else sb.append(",");
                    String name = f.getName().replace("\"", "\\\"");
                    String path = f.getAbsolutePath().replace("\\", "/").replace("\"", "\\\"");
                    sb.append("{\"name\":\"").append(name)
                      .append("\",\"path\":\"").append(path)
                      .append("\",\"size\":").append(f.length())
                      .append(",\"lastModified\":").append(f.lastModified())
                      .append("}");
                }
            }
        }

        @JavascriptInterface
        public String readImageAsBase64(String path) {
            try {
                File f = new File(path);
                if (!f.exists() || !f.canRead()) return "";
                byte[] bytes = new byte[(int) f.length()];
                try (FileInputStream fis = new FileInputStream(f)) {
                    fis.read(bytes);
                }
                String b64;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    b64 = Base64.getEncoder().encodeToString(bytes);
                } else {
                    b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                }
                return "data:" + getMime(path) + ";base64," + b64;
            } catch (IOException e) {
                return "";
            }
        }

        @JavascriptInterface
        public String getStoragePaths() {
            List<String> paths = new ArrayList<>();
            paths.add(Environment.getExternalStorageDirectory().getAbsolutePath());
            File[] externalDirs = ContextCompat.getExternalFilesDirs(MainActivity.this, null);
            for (File dir : externalDirs) {
                if (dir == null) continue;
                String path = dir.getAbsolutePath();
                int idx = path.indexOf("/Android/data");
                if (idx >= 0) path = path.substring(0, idx);
                if (!paths.contains(path)) paths.add(path);
            }
            File storage = new File("/storage");
            if (storage.exists()) {
                File[] vols = storage.listFiles();
                if (vols != null) {
                    for (File v : vols) {
                        String p = v.getAbsolutePath();
                        if (!p.equals("/storage/emulated") && !paths.contains(p)) paths.add(p);
                    }
                }
            }
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < paths.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(paths.get(i).replace("\"","\\\"")).append("\"");
            }
            sb.append("]");
            return sb.toString();
        }

        @JavascriptInterface
        public void showToast(String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void setKeepScreenOn(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        private boolean isImage(String name) {
            int dot = name.lastIndexOf('.');
            if (dot < 0) return false;
            return IMG_EXTS.contains(name.substring(dot + 1).toLowerCase());
        }

        private String getMime(String path) {
            int dot = path.lastIndexOf('.');
            if (dot < 0) return "image/jpeg";
            String ext = path.substring(dot + 1).toLowerCase();
            switch (ext) {
                case "jpg": case "jpeg": return "image/jpeg";
                case "png":  return "image/png";
                case "webp": return "image/webp";
                case "gif":  return "image/gif";
                case "bmp":  return "image/bmp";
                case "avif": return "image/avif";
                default:     return "image/jpeg";
            }
        }
    }

    // ── TV Remote ──
    private boolean slideshowStarted = false;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (slideshowStarted) { webView.evaluateJavascript("next();", null); return true; }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                if (slideshowStarted) { webView.evaluateJavascript("prev();", null); return true; }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (!slideshowStarted) {
                    runOnUiThread(() -> {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        try {
                            startActivityForResult(Intent.createChooser(intent, "اختر الصور"), FILE_CHOOSER_REQUEST);
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "لم يتم العثور على تطبيق لاختيار الملفات", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return true;
                } else {
                    webView.evaluateJavascript("togglePause();", null);
                    return true;
                }
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                webView.evaluateJavascript("togglePause();", null);
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
                webView.evaluateJavascript("togglePanel();", null);
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (webView.canGoBack()) { webView.goBack(); return true; }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    class SlideshowBridge {
        @JavascriptInterface
        public void onSlideshowStarted() { slideshowStarted = true; }
        @JavascriptInterface
        public void onSlideshowStopped() { slideshowStarted = false; }
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES}, PERMISSION_REQUEST);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == PERMISSION_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            webView.evaluateJavascript("if(typeof onPermissionGranted==='function') onPermissionGranted();", null);
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) return;
            Uri[] uris = null;
            if (result == Activity.RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    uris = new Uri[count];
                    for (int i = 0; i < count; i++)
                        uris[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    uris = new Uri[]{data.getData()};
                }
            }
            filePathCallback.onReceiveValue(uris);
            filePathCallback = null;
        }
    }

    @Override
    protected void onResume() { super.onResume(); webView.onResume(); }

    @Override
    protected void onPause() { super.onPause(); webView.onPause(); }
}
