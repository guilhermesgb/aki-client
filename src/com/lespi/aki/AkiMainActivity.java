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
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
		setSlidingActionBarEnabled(true);

	    if (savedInstanceState == null) {
	        chatFragment = new AkiChatFragment();
	    } else {
	        chatFragment = (AkiChatFragment) getSupportFragmentManager()
	            .findFragmentById(R.id.aki_chat_frame);
	    }
	    
	    Bundle extras = getIntent().getExtras();
	    if ( extras != null ){
	    	chatFragment.setSeenSplash(extras.getBoolean("seenSplash", true));
	    }
	    
	    setContentView(R.layout.aki_chat_fragment);

	    getSupportFragmentManager()
	        .beginTransaction()
	        .replace(R.id.aki_chat_frame, chatFragment)
	        .commit();

	    settingsFragment = new AkiSettingsFragment();
	    setBehindContentView(R.layout.aki_menu_frame);

	    getSupportFragmentManager()
	        .beginTransaction()
	        .replace(R.id.aki_menu_frame, settingsFragment)
	        .commit();

		slidingMenu = super.getSlidingMenu();
		slidingMenu.setShadowWidthRes(R.dimen.shadow_width);
		slidingMenu.setShadowDrawable(R.drawable.shadow);
		slidingMenu.setBehindOffsetRes(R.dimen.slidingmenu_offset);
		slidingMenu.setFadeDegree(0.15f);
		slidingMenu.setMode(SlidingMenu.RIGHT);
		slidingMenu.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
		slidingMenu.setTouchModeBehind(SlidingMenu.TOUCHMODE_MARGIN);
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
	}

	public SlidingMenu getSlidingMenu(){
		return slidingMenu;
	}

	public AkiSettingsFragment getSettingsFragment() {
		return settingsFragment;
	}
	
	public void sendMessage(View view){
		chatFragment.sendMessage();
	}
	
	public void openSettings(View view){
		slidingMenu.showMenu(true);
	}
	
	public void changeNickname(View view){
		
	}
	
	public void togglePrivacySettings(View view){
		
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