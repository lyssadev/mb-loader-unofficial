package io.bambosan.mbloader;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        TextView listener = view.findViewById(R.id.listener);
        Button mbl2_button = view.findViewById(R.id.mbl2_load);
        Button draco_button = view.findViewById(R.id.draco_load);
        Handler handler = new Handler(Looper.getMainLooper());

        mbl2_button.setOnClickListener(v -> {
            ((MainActivity) requireActivity()).startLauncher(handler, listener, "launcher_mbl2.dex", MainActivity.MC_PACKAGE_NAME);
        });
        
        draco_button.setOnClickListener(v -> {
            ((MainActivity) requireActivity()).startLauncher(handler, listener, "launcher_draco.dex", MainActivity.MC_PACKAGE_NAME);
        });

        return view;
    }
} 