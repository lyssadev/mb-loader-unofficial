package io.bambosan.mbloader;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.fragment.app.Fragment;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsFragment extends Fragment {
    private SharedPreferences prefs;
    private View rootView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_settings, container, false);

        TextInputEditText mcPackageName = rootView.findViewById(R.id.mc_pkgname);
        mcPackageName.setText(MainActivity.MC_PACKAGE_NAME);
        
        return rootView;
    }
} 