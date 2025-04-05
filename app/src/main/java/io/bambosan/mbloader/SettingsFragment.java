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
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.TextView;

public class SettingsFragment extends Fragment {
    private SharedPreferences prefs;
    private View rootView;
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 1234;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_settings, container, false);
        prefs = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE);

        EditText mcPackageName = rootView.findViewById(R.id.mc_pkgname);
        mcPackageName.setText(MainActivity.MC_PACKAGE_NAME);

        SwitchMaterial fpsOverlaySwitch = rootView.findViewById(R.id.fps_overlay_switch);
        fpsOverlaySwitch.setChecked(prefs.getBoolean("fps_overlay_enabled", false));
        
        fpsOverlaySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !Settings.canDrawOverlays(requireContext())) {
                // Request overlay permission
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + requireContext().getPackageName())
                );
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
                buttonView.setChecked(false);
            } else {
                prefs.edit().putBoolean("fps_overlay_enabled", isChecked).apply();
                if (isChecked) {
                    requireContext().startService(new Intent(requireContext(), FPSOverlayService.class));
                } else {
                    requireContext().stopService(new Intent(requireContext(), FPSOverlayService.class));
                }
            }
        });
        
        return rootView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(requireContext())) {
                SwitchMaterial fpsOverlaySwitch = rootView.findViewById(R.id.fps_overlay_switch);
                fpsOverlaySwitch.setChecked(true);
                prefs.edit().putBoolean("fps_overlay_enabled", true).apply();
                requireContext().startService(new Intent(requireContext(), FPSOverlayService.class));
            } else {
                Toast.makeText(requireContext(), "Overlay permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
} 