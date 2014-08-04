package com.lespi.aki;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;

import com.facebook.Session;

public class AkiMain extends FragmentActivity {

	private AkiMainFragment mainFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		System.out.println("This was called...");
		
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

	public void sendMessage(View view){
		
		EditText chatBox = (EditText) findViewById(R.id.chatBox);
		AkiServerCalls.sendMessage(getApplicationContext(), chatBox.getText().toString());
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data){
		super.onActivityResult(requestCode, resultCode, data);

		System.out.println("Received result data: " + data.getAction() + "!");
		
		Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
	}
	
	@Override
	protected void onResume(){
		super.onResume();
		System.out.println("This was called when a new message arrived!!!!");
	}
	
	@Override
	protected void onStop(){
		super.onStop();

		if ( AkiServerCalls.isActiveOnServer() ){
			AkiServerCalls.leaveServer(getApplicationContext());
		}
	}
}