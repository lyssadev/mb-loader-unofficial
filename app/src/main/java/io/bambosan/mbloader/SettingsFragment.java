package io.bambosan.mbloader;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        
        EditText mcPackageName = view.findViewById(R.id.mc_pkgname);
        mcPackageName.setText(MainActivity.MC_PACKAGE_NAME);
        
        return view;
    }
} 