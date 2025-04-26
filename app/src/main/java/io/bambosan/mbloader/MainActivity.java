package io.bambosan.mbloader;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity {

    public static final String MC_PACKAGE_NAME = "com.mojang.minecraftpe";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Support for shared element transition from splash screen
        postponeEnterTransition();
        
        // Add entry animation
        View rootView = findViewById(android.R.id.content);
        rootView.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in));
        
        // Complete the postponed shared element transition
        startPostponedEnterTransition();
        
        if (savedInstanceState == null) {
            // --- Apply custom window flags ---
            getWindow().setStatusBarColor(getColor(R.color.background));
            getWindow().setNavigationBarColor(getColor(R.color.background));
            
            // --- Set content view and attach navigation ---
            setContentView(R.layout.activity_main);
            setupNavigation();
            
            // --- Set default fragment ---
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
                ApplicationInfo mcInfo = null;
                try {
                    mcInfo = getPackageManager().getApplicationInfo(mcPackageName, PackageManager.GET_META_DATA);
                } catch(Exception e) {
                    handler.post(() -> alertAndExit("Minecraft not found", "Perhaps you don't have it installed?"));
                    return;
                }
                Object pathList = getPathList(getClassLoader());
                processDexFiles(mcInfo, cacheDexDir, pathList, handler, listener, logScrollView, launcherDexName);
                if (!processNativeLibraries(mcInfo, pathList, handler, listener, logScrollView)) {
                    return;
                }
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
    private boolean processNativeLibraries(ApplicationInfo mcInfo, @NotNull Object pathList, @NotNull Handler handler, TextView listener, ScrollView logScrollView) throws Exception {
        FileInputStream inStream = new FileInputStream(getApkWithLibs(mcInfo));
 		BufferedInputStream bufInStream = new BufferedInputStream(inStream);
 		ZipInputStream inZipStream = new ZipInputStream(bufInStream);
 		if (!checkLibCompatibility(inZipStream)) {
 		    handler.post(() -> alertAndExit("Wrong Minecraft architecture", "The Minecraft you have installed does not support the same main architecture (" + Build.SUPPORTED_ABIS[0] + ") your device uses, MBLoader can't work with it"));
 		    return false;
 		} 		    
        Method addNativePath = pathList.getClass().getDeclaredMethod("addNativePath", Collection.class);
        ArrayList<String> libDirList = new ArrayList<>();
        File libdir = new File(mcInfo.nativeLibraryDir);
		if (libdir.list() == null || libdir.list().length == 0 
		 || (mcInfo.flags & ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) != ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) {
			loadUnextractedLibs(mcInfo);
			libDirList.add(getCodeCacheDir().getAbsolutePath() + "/");
		} else {
            libDirList.add(mcInfo.nativeLibraryDir);
        }
        addNativePath.invoke(pathList, libDirList);
        handler.post(() -> {
            listener.append("\n-> " + mcInfo.nativeLibraryDir + " added to native library directory path");
            logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
        });
        return true;
    }
    
    private static Boolean checkLibCompatibility(ZipInputStream zip) throws Exception{
         ZipEntry ze = null;
         String requiredLibDir = "lib/" + Build.SUPPORTED_ABIS[0] + "/";
         while ((ze = zip.getNextEntry()) != null) {
             if (ze.getName().startsWith(requiredLibDir)) {
                 return true;
             }
         }
         zip.close();
         return false;
     }
     
    private void alertAndExit(String issue, String description) {
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle(issue);
        alertDialog.setMessage(description);
        alertDialog.setCancelable(false);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Exit",
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
        alertDialog.show();
    }
     
    private void loadUnextractedLibs(ApplicationInfo appInfo) throws Exception {
		FileInputStream inStream = new FileInputStream(getApkWithLibs(appInfo));
		BufferedInputStream bufInStream = new BufferedInputStream(inStream);
		ZipInputStream inZipStream = new ZipInputStream(bufInStream);
		String zipPath = "lib/" + Build.SUPPORTED_ABIS[0] + "/";
		String outPath = getCodeCacheDir().getAbsolutePath() + "/";
		File dir = new File(outPath);
		dir.mkdir();
		extractDir(appInfo, inZipStream, zipPath, outPath);
	}
	
	public String getApkWithLibs(ApplicationInfo pkg) throws PackageManager.NameNotFoundException {
		// get installed split's Names
		String[] sn=pkg.splitSourceDirs;

		// check whether if it's really split or not
		if (sn != null && sn.length > 0) {
			String cur_abi = Build.SUPPORTED_ABIS[0].replace('-','_');
			// search installed splits
			for(String n:sn){
				//check whether is the one required
				if(n.contains(cur_abi)){
				// yes, it's installed!
					return n;
				}
			}
		}
		// couldn't find!
		return pkg.sourceDir;
	}
	
	private static void extractDir(ApplicationInfo mcInfo, ZipInputStream zip, String zip_folder, String out_folder) throws Exception {
        ZipEntry ze = null;
        while ((ze = zip.getNextEntry()) != null) {
            if (ze.getName().startsWith(zip_folder) && !ze.getName().contains("c++_shared")) {
				String strippedName = ze.getName().substring(zip_folder.length());
				String path = out_folder + "/" + strippedName;
				OutputStream out = new FileOutputStream(path);
				BufferedOutputStream outBuf = new BufferedOutputStream(out);
                byte[] buffer = new byte[9000];
                int len;
                while ((len = zip.read(buffer)) != -1) {
                    outBuf.write(buffer, 0, len);
                }
                outBuf.close();
            }
        }
        zip.close();
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
    
    private void setupNavigation() {
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
    }
}
