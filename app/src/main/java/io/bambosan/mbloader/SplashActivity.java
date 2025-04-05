package io.bambosan.mbloader;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 3000; // 3 seconds
    
    private ImageView logoView;
    private TextView titleView;
    private TextView subtitleView;
    private View highlightView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        // Find views
        logoView = findViewById(R.id.splash_logo);
        titleView = findViewById(R.id.splash_title);
        subtitleView = findViewById(R.id.splash_subtitle);
        highlightView = findViewById(R.id.splash_highlight);
        
        // Start animations
        startAnimations();
        
        // Delay transition to MainActivity
        new Handler().postDelayed(this::startMainActivity, SPLASH_DURATION);
    }
    
    private void startAnimations() {
        // Logo animation
        Animation zoomIn = AnimationUtils.loadAnimation(this, R.anim.logo_zoom_in);
        Animation rotate = AnimationUtils.loadAnimation(this, R.anim.logo_rotate);
        
        logoView.startAnimation(zoomIn);
        
        zoomIn.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            
            @Override
            public void onAnimationEnd(Animation animation) {
                logoView.startAnimation(rotate);
            }
            
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        
        // Text animations
        Animation textAnim = AnimationUtils.loadAnimation(this, R.anim.text_slide_up);
        titleView.startAnimation(textAnim);
        
        Animation subtitleAnim = AnimationUtils.loadAnimation(this, R.anim.text_slide_up);
        subtitleAnim.setStartOffset(1300); // Slight delay for subtitle
        subtitleView.startAnimation(subtitleAnim);
        
        // Highlight animation
        Animation pulseAnim = AnimationUtils.loadAnimation(this, R.anim.logo_pulse);
        highlightView.startAnimation(pulseAnim);
    }
    
    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        
        // Create a shared element transition
        ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                this, logoView, "app_logo");
                
        startActivity(intent, options.toBundle());
        
        // Delayed finish to allow smooth transition
        new Handler().postDelayed(this::finish, 500);
    }
} 