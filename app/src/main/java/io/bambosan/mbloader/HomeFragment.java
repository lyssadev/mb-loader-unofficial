package io.bambosan.mbloader;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HomeFragment extends Fragment {
    
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private TextView listener;
    private ScrollView logScrollView;
    private Handler handler;
    private Uri pendingFileUri;
    private String pendingLoaderDexName;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        
        
        // Register permission launcher
        requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    // Permission granted, proceed with file import
                    if (pendingFileUri != null && pendingLoaderDexName != null) {
                        proceedWithImport(pendingFileUri, pendingLoaderDexName);
                    }
                } else {
                    // Permission denied
                    Toast.makeText(requireContext(), "Storage permission is required to import files", Toast.LENGTH_LONG).show();
                }
            }
        );
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        listener = view.findViewById(R.id.listener);
        Button mbl2_button = view.findViewById(R.id.mbl2_load);
        Button draco_button = view.findViewById(R.id.draco_load);
        logScrollView = view.findViewById(R.id.log_scroll_view);
        handler = new Handler(Looper.getMainLooper());
        

        mbl2_button.setOnClickListener(v -> {
            ((MainActivity) requireActivity()).startLauncher(handler, listener, logScrollView, "launcher_mbl2.dex", MainActivity.MC_PACKAGE_NAME);
        });
        
        draco_button.setOnClickListener(v -> {
            ((MainActivity) requireActivity()).startLauncher(handler, listener, logScrollView, "launcher_draco.dex", MainActivity.MC_PACKAGE_NAME);
        });
        
        

        return view;
    }
    
    
    
    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        
        return result;
    }
    
    public void importMcpackFile(Uri fileUri, String loaderDexName) {
        // Check for storage permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+ (API 29+), we use the scoped storage model
            proceedWithImport(fileUri, loaderDexName);
        } else {
            // For Android 9 (Pie) and below, we need to check for the WRITE_EXTERNAL_STORAGE permission
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                // Store the URI and loader for later use
                pendingFileUri = fileUri;
                pendingLoaderDexName = loaderDexName;
                
                // Request the permission
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                // Permission already granted, proceed
                proceedWithImport(fileUri, loaderDexName);
            }
        }
    }
    
    private void proceedWithImport(Uri fileUri, String loaderDexName) {
        // Update UI to show importing status
        listener.setText("Starting Minecraft with loader...");
        logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
        
        // First, launch Minecraft with the selected loader
        ((MainActivity) requireActivity()).startLauncher(handler, listener, logScrollView, 
                                                  loaderDexName, MainActivity.MC_PACKAGE_NAME);
        
        // After a short delay to ensure Minecraft has started, send the file to it
        handler.postDelayed(() -> {
            try {
                // Create an intent to open the file with Minecraft directly
                Intent openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setDataAndType(fileUri, "application/octet-stream");
                openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                openIntent.setPackage(MainActivity.MC_PACKAGE_NAME);
                
                startActivity(openIntent);
                
                // Update UI to show success
                listener.append("\nSent .mcpack file to Minecraft");
                logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
            } catch (Exception e) {
                // If sending to Minecraft directly fails, try with a chooser
                listener.append("\nCouldn't send directly to Minecraft, showing chooser...");
                logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
                
                try {
                    Intent openIntent = new Intent(Intent.ACTION_VIEW);
                    openIntent.setDataAndType(fileUri, "application/octet-stream");
                    openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    
                    Intent chooser = Intent.createChooser(openIntent, "Open with Minecraft");
                    startActivity(chooser);
                } catch (Exception ex) {
                    listener.append("\nError showing chooser: " + ex.getMessage());
                    logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
                }
            }
        }, 3000); // Wait 3 seconds to ensure Minecraft is running
    }
    
    
    
    
} 