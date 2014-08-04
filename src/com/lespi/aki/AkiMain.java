package com.lespi.aki;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;

import com.facebook.Session;

public class AkiMain extends FragmentActivity {

	private AkiMainFragment mainFragment;

	public void sendMessage(View view){
		Log.e(AkiApplication.TAG, "HERE!!");
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);

	    if (savedInstanceState == null) {
	        mainFragment = new AkiMainFragment();
	        getSupportFragmentManager()
	        .beginTransaction()
	        .add(android.R.id.content, mainFragment)
	        .commit();
	    } else {
	        mainFragment = (AkiMainFragment) getSupportFragmentManager()
	        .findFragmentById(android.R.id.content);
	    }
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data){
		super.onActivityResult(requestCode, resultCode, data);

		Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
	}
	
	@Override
	protected void onStop(){
		super.onStop();

		if ( AkiServerCalls.isActiveOnServer() ){
			AkiServerCalls.leaveServer(getApplicationContext());
		}
	}
	
}