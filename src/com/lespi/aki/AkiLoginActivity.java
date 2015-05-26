package com.lespi.aki;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;

import com.actionbarsherlock.app.SherlockActivity;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;

public class AkiLoginActivity extends SherlockActivity {

	public static CallbackManager callbackManager;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.v(AkiMainActivity.TAG, "AkiLOGINActivity$onCreate");
		super.onCreate(savedInstanceState);

		callbackManager = CallbackManager.Factory.create();
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		setContentView(R.layout.aki_login_activity);
		
		LoginButton authButton = (LoginButton) findViewById(R.id.com_lespi_aki_login_login_auth_btn);
		authButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
		authButton.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
			@Override
			public void onSuccess(LoginResult result) {
				Intent intent = new Intent(AkiLoginActivity.this, AkiMainActivity.class);
            	intent.putExtra("loggedIn", true);
				AkiLoginActivity.this.startActivity(intent);
				AkiLoginActivity.this.overridePendingTransition(R.anim.hold, R.anim.fade_in);
				AkiLoginActivity.this.finish();
				AkiLoginActivity.this.overridePendingTransition(R.anim.hold, R.anim.fade_out);
			}
			@Override
			public void onError(FacebookException error) {
				
			}
			@Override
			public void onCancel() {
				
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.v(AkiMainActivity.TAG, "AkiLOGINActivity$onActivityResult");
	    super.onActivityResult(requestCode, resultCode, data);
	    callbackManager.onActivityResult(requestCode, resultCode, data);
	}
}