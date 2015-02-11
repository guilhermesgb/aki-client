package com.lespi.aki;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.Window;

import com.actionbarsherlock.app.SherlockActivity;

public class AkiSplashActivity extends SherlockActivity {

	private static final int SPLASH_DURATION_MS = 2000;
    
    private Handler handler = new Handler();
     
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
        setContentView(R.layout.aki_splash_activity);
        
        handler.postDelayed(endSplash, SPLASH_DURATION_MS);
    }
     
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        endSplash.run();
        return super.onTouchEvent(event);
    }
     
    private Runnable endSplash = new Runnable() {
        public void run() {
        	
            if (!isFinishing()) {
 
            	handler.removeCallbacks(this);
            	Intent intent = new Intent(AkiSplashActivity.this, AkiMainActivity.class);
            	intent.putExtra("seenSplash", true);
                startActivity(intent);
    			overridePendingTransition(R.anim.hold, R.anim.fade_in);
                finish();
    			overridePendingTransition(R.anim.hold, R.anim.fade_out);
            }
        };
    };



}