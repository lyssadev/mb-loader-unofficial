package io.bambosan.mbloader;

import org.jetbrains.annotations.NotNull;
import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import android.widget.Toast;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.fragment.app.Fragment;
import android.content.Context;
import android.widget.ScrollView;

public class MainActivity extends AppCompatActivity {

    public static final String MC_PACKAGE_NAME = "com.mojang.minecraftpe";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Add entry animation
        View rootView = findViewById(android.R.id.content);
        rootView.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in));
        
        // Setup bottom navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            
            if (item.getItemId() == R.id.navigation_home) {
                selectedFragment = new HomeFragment();
            } else if (item.getItemId() == R.id.navigation_settings) {
                selectedFragment = new SettingsFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(
                        R.anim.fade_in,
                        R.anim.fade_out
                    )
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit();
                return true;
            }
            return false;
        });

        // Set default fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new HomeFragment())
                .commit();
        }
    }

    public void startLauncher(Handler handler, TextView listener, ScrollView logScrollView, String launcherDexName, String mcPackageName) {    
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File cacheDexDir = new File(getCodeCacheDir(), "dex");
                handleCacheCleaning(cacheDexDir, handler, listener, logScrollView);
                ApplicationInfo mcInfo = getPackageManager().getApplicationInfo(mcPackageName, PackageManager.GET_META_DATA);
                Object pathList = getPathList(getClassLoader());
                processDexFiles(mcInfo, cacheDexDir, pathList, handler, listener, logScrollView, launcherDexName);
                processNativeLibraries(mcInfo, pathList, handler, listener, logScrollView);
                launchMinecraft(mcInfo);
            } catch (Exception e) {
                String logMessage = e.getCause() != null ? e.getCause().toString() : e.toString();
                handler.post(() -> {
                    listener.setText("Launching failed: " + logMessage);
                    logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
                });
            }
        });    
    }
    @SuppressLint("SetTextI18n")
    private void handleCacheCleaning(@NotNull File cacheDexDir, Handler handler, TextView listener, ScrollView logScrollView) {
        if (cacheDexDir.exists() && cacheDexDir.isDirectory()) {
            handler.post(() -> {
                listener.setText("-> " + cacheDexDir.getAbsolutePath() + " not empty, do cleaning");
                logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
            });
            for (File file : Objects.requireNonNull(cacheDexDir.listFiles())) {
                if (file.delete()) {
                    handler.post(() -> {
                        listener.append("\n-> " + file.getName() + " deleted");
                        logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
                    });
                }
            }
        } else {
            handler.post(() -> {
                listener.setText("-> " + cacheDexDir.getAbsolutePath() + " is empty, skip cleaning");
                logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
            });
        }
    }

    private Object getPathList(@NotNull ClassLoader classLoader) throws Exception {
        Field pathListField = Objects.requireNonNull(classLoader.getClass().getSuperclass()).getDeclaredField("pathList");
        pathListField.setAccessible(true);
        return pathListField.get(classLoader);
    }

    private void processDexFiles(ApplicationInfo mcInfo, File cacheDexDir, @NotNull Object pathList, @NotNull Handler handler, TextView listener, ScrollView logScrollView, String launcherDexName) throws Exception {
        Method addDexPath = pathList.getClass().getDeclaredMethod("addDexPath", String.class, File.class);
        File launcherDex = new File(cacheDexDir, launcherDexName);

        copyFile(getAssets().open(launcherDexName), launcherDex);
        handler.post(() -> {
             listener.append("\n-> " + launcherDexName + " copied to " + launcherDex.getAbsolutePath());
             logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
        });

        if (launcherDex.setReadOnly()) {
            addDexPath.invoke(pathList, launcherDex.getAbsolutePath(), null);
            handler.post(() -> {
                listener.append("\n-> " + launcherDexName + " added to dex path list");
                logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
            });
        }

        try (ZipFile zipFile = new ZipFile(mcInfo.sourceDir)) {
            for (int i = 10; i >= 0; i--) {
                String dexName = "classes" + (i == 0 ? "" : i) + ".dex";
                ZipEntry dexFile = zipFile.getEntry(dexName);
                if (dexFile != null) {
                    File mcDex = new File(cacheDexDir, dexName);
                    copyFile(zipFile.getInputStream(dexFile), mcDex);
                     handler.post(() -> {
                         listener.append("\n-> " + mcInfo.sourceDir + "/" + dexName + " copied to " + mcDex.getAbsolutePath());
                         logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
                     });
                    if (mcDex.setReadOnly()) {
                        addDexPath.invoke(pathList, mcDex.getAbsolutePath(), null);
                        handler.post(() -> {
                             listener.append("\n-> " + dexName + " added to dex path list");
                             logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
                        });
                    }
                }
            }
        } catch (Throwable th) {}
         handler.post(() -> {
             listener.append("\n-> Processed dex files.");
             logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
         });
    }

    @SuppressLint("SoonBlockedPrivateApi")
    private void processNativeLibraries(ApplicationInfo mcInfo, @NotNull Object pathList, @NotNull Handler handler, TextView listener, ScrollView logScrollView) throws Exception {
        Method makePathElements = null;
        try {
             makePathElements = pathList.getClass().getDeclaredMethod("makePathElements", java.util.List.class);
        } catch(NoSuchMethodException e) {
            makePathElements = pathList.getClass().getDeclaredMethod("makePathElements", java.util.List.class, java.io.File.class, java.util.List.class);
        }

        Collection<String> libDirs = new ArrayList<>();
        Collections.addAll(libDirs, mcInfo.nativeLibraryDir);
        if (mcInfo.splitSourceDirs != null) {
            ZipFile zipFile;
            ZipEntry entry;
            for (String srcDir : mcInfo.splitSourceDirs) {
                if (srcDir.endsWith(".apk") && (zipFile = new ZipFile(srcDir)) != null && (entry = zipFile.getEntry("lib/")) != null && entry.isDirectory()) {
                    libDirs.add(srcDir + "!/lib");
                }
            }
        }

        Field nativeLibraryPathElements = pathList.getClass().getDeclaredField("nativeLibraryPathElements");
        nativeLibraryPathElements.setAccessible(true);

        Object[] elements = (Object[]) makePathElements.invoke(null, libDirs);
        nativeLibraryPathElements.set(pathList, elements);
        handler.post(() -> {
             listener.append("\n-> Processed native libraries.");
             logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void launchMinecraft(@NotNull ApplicationInfo mcInfo) throws ClassNotFoundException {
        Class<?> launcherClass = getClassLoader().loadClass("com.mojang.minecraftpe.Launcher");
        // We do this to preserve data that apps like file managers pass 
        Intent mcActivity = getIntent().setClass(this, launcherClass);
        mcActivity.putExtra("MC_SRC", mcInfo.sourceDir);

        if (mcInfo.splitSourceDirs != null) {
            ArrayList<String> listSrcSplit = new ArrayList<>();
            Collections.addAll(listSrcSplit, mcInfo.splitSourceDirs);
            mcActivity.putExtra("MC_SPLIT_SRC", listSrcSplit);
        }

        if (getSharedPreferences("app_settings", Context.MODE_PRIVATE).getBoolean("fps_overlay_enabled", false)) {
            // Stop any existing service first
            stopService(new Intent(this, FPSOverlayService.class));
            // Start a fresh instance
            startService(new Intent(this, FPSOverlayService.class));
        }

        startActivity(mcActivity);
        finish();
    }

    private void handleException(@NotNull Exception e, @NotNull Intent fallbackActivity) {
        String logMessage = e.getCause() != null ? e.getCause().toString() : e.toString();
        fallbackActivity.putExtra("LOG_STR", logMessage);
        startActivity(fallbackActivity);
        finish();
    }

    private static void copyFile(InputStream from, @NotNull File to) throws IOException {
        File parentDir = to.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create directories");
        }
        if (!to.exists() && !to.createNewFile()) {
            throw new IOException("Failed to create new file");
        }
        try (BufferedInputStream input = new BufferedInputStream(from);
             BufferedOutputStream output = new BufferedOutputStream(Files.newOutputStream(to.toPath()))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }
    }
    
}
