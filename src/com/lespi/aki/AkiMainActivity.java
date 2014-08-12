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
	private SlidingMenu slidingMenu;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		setSlidingActionBarEnabled(true);
		
		setBehindContentView(R.layout.aki_menu_frame);
		setContentView(R.layout.aki_main_frame);

		slidingMenu = getSlidingMenu();
		slidingMenu.setShadowWidthRes(R.dimen.shadow_width);
		slidingMenu.setShadowDrawable(R.drawable.shadow);
		slidingMenu.setBehindOffsetRes(R.dimen.slidingmenu_offset);
		slidingMenu.setFadeDegree(0.15f);
		slidingMenu.setMode(SlidingMenu.RIGHT);
		slidingMenu.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
		slidingMenu.setTouchModeBehind(SlidingMenu.TOUCHMODE_FULLSCREEN);
		slidingMenu.setOnOpenedListener(new OnOpenedListener() {
			@Override
			public void onOpened() {

				AkiApplication.isShowingSettingsMenu();
			}
		});
		slidingMenu.setOnClosedListener(new OnClosedListener() {
			@Override
			public void onClosed() {

				AkiApplication.isNotShowingSettingsMenu();
			}
		});
		
	    if (savedInstanceState == null) {
	        chatFragment = new AkiChatFragment();
	    } else {
	        chatFragment = (AkiChatFragment) getSupportFragmentManager()
	            .findFragmentById(android.R.id.content);
//	        if ( AkiApplication.IN_SETTINGS ){
//	        	slidingMenu.setBehindOffset(0);
//	        }
	    }

	    getSupportFragmentManager()
	    .beginTransaction()
	    .replace(android.R.id.content, chatFragment)
	    .commit();

        settingsFragment = new AkiSettingsFragment();
        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.menu_frame, settingsFragment)
            .commitAllowingStateLoss();

//		supportInvalidateOptionsMenu();
	}

	public void sendMessage(View view){
		
		chatFragment.sendMessage();
	}
	
	public void openSettings(View view){
		
		slidingMenu.showMenu(true);
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