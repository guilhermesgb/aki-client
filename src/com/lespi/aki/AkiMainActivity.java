package com.lespi.aki;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import com.facebook.Session;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu.OnClosedListener;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu.OnOpenedListener;
import com.jeremyfeinstein.slidingmenu.lib.app.SlidingFragmentActivity;
import com.lespi.aki.utils.AkiServerUtil;

public class AkiMainActivity extends SlidingFragmentActivity {

	private AkiChatFragment chatFragment;
	private AkiSettingsFragment settingsFragment;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		setSlidingActionBarEnabled(true);
		
		setBehindContentView(R.layout.aki_menu_frame);
		setContentView(R.layout.aki_main_frame);
		
	    if (savedInstanceState == null) {
	        chatFragment = new AkiChatFragment();
	    } else {
	        chatFragment = (AkiChatFragment) getSupportFragmentManager()
	            .findFragmentById(android.R.id.content);
	    }

	    getSupportFragmentManager()
            .beginTransaction()
            .add(android.R.id.content, chatFragment)
            .commit();

        settingsFragment = new AkiSettingsFragment();
        getSupportFragmentManager()
            .beginTransaction()
            .add(R.id.menu_frame, settingsFragment)
            .commitAllowingStateLoss();

		SlidingMenu sm = getSlidingMenu();
//		sm.setShadowWidthRes(R.dimen.shadow_width);
//		sm.setShadowDrawable(R.drawable.shadow);
//		sm.setBehindOffsetRes(R.dimen.slidingmenu_offset);
		sm.setFadeDegree(0.15f);
		sm.setBehindOffset(50);
		sm.setMode(SlidingMenu.RIGHT);
		sm.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
		sm.setTouchModeBehind(SlidingMenu.TOUCHMODE_FULLSCREEN);
		sm.setOnOpenedListener(new OnOpenedListener() {
			@Override
			public void onOpened() {
				
				AkiApplication.isShowingSettingsMenu();
			}
		});
		sm.setOnClosedListener(new OnClosedListener() {
			@Override
			public void onClosed() {

				AkiApplication.isNotShowingSettingsMenu();
			}
		});
		supportInvalidateOptionsMenu();
	}

	public void sendMessage(View view){
		
		chatFragment.sendMessage();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data){
		super.onActivityResult(requestCode, resultCode, data);
		Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
	}
	
	@Override
	protected void onPause(){
		super.onPause();
		
		AkiApplication.isNowInBackground();
	}
	
	@Override
	protected void onResume(){
		super.onResume();
		
		AkiApplication.isNowInForeground();
		
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(AkiApplication.INCOMING_MESSAGE_NOTIFICATION_ID);
		
		if ( AkiServerUtil.isActiveOnServer() ){
			chatFragment.onResume();
		}
	}
	
	@Override
	protected void onStop(){
		super.onStop();

		AkiApplication.isNowInBackground();

		if ( AkiServerUtil.isActiveOnServer() ){
			AkiServerUtil.leaveServer(getApplicationContext());
		}
	}
}