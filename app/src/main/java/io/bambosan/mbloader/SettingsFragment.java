package io.bambosan.mbloader;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;
import android.content.Intent;
import androidx.fragment.app.Fragment;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.TextView;

public class SettingsFragment extends Fragment {
    private SharedPreferences prefs;
    private View rootView;
    private SwitchMaterial fpsOverlaySwitch;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        
        // Register the permission launcher
        overlayPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (Settings.canDrawOverlays(requireContext())) {
                    // Permission granted
                    fpsOverlaySwitch.setChecked(true);
                    prefs.edit().putBoolean("fps_overlay_enabled", true).apply();
                    requireContext().startService(new Intent(requireContext(), FPSOverlayService.class));
                    Toast.makeText(requireContext(), "FPS overlay enabled", Toast.LENGTH_SHORT).show();
                } else {
                    // Permission denied
                    fpsOverlaySwitch.setChecked(false);
                    Toast.makeText(requireContext(), "Overlay permission denied", Toast.LENGTH_SHORT).show();
                }
            }
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_settings, container, false);

        EditText mcPackageName = rootView.findViewById(R.id.mc_pkgname);
        mcPackageName.setText(MainActivity.MC_PACKAGE_NAME);

        fpsOverlaySwitch = rootView.findViewById(R.id.fps_overlay_switch);
        fpsOverlaySwitch.setChecked(prefs.getBoolean("fps_overlay_enabled", false));
        
        fpsOverlaySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !Settings.canDrawOverlays(requireContext())) {
                // Request overlay permission
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + requireContext().getPackageName())
                );
                overlayPermissionLauncher.launch(intent);
                // Don't set the switch to checked yet, wait for permission result
                buttonView.setChecked(false);
            } else {
                prefs.edit().putBoolean("fps_overlay_enabled", isChecked).apply();
                if (isChecked) {
                    requireContext().startService(new Intent(requireContext(), FPSOverlayService.class));
                    Toast.makeText(requireContext(), "FPS overlay enabled", Toast.LENGTH_SHORT).show();
                } else {
                    requireContext().stopService(new Intent(requireContext(), FPSOverlayService.class));
                    Toast.makeText(requireContext(), "FPS overlay disabled", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        return rootView;
    }
} 