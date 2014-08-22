package com.lespi.aki;

import java.util.Random;

import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.Window;

import com.facebook.Session;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu.OnClosedListener;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu.OnOpenedListener;
import com.jeremyfeinstein.slidingmenu.lib.app.SlidingFragmentActivity;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiServerUtil;

public class AkiMainActivity extends SlidingFragmentActivity implements
	LocationListener,
	GooglePlayServicesClient.ConnectionCallbacks,
	GooglePlayServicesClient.OnConnectionFailedListener {

	private LocationRequest locationRequest;
	private LocationClient locationClient;
	private AkiChatFragment chatFragment;
	private AkiSettingsFragment settingsFragment;
	private SlidingMenu slidingMenu;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		locationRequest = LocationRequest.create();
		locationRequest.setInterval(AkiApplication.UPDATE_INTERVAL_IN_MILLISECONDS);
		locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
		locationRequest.setFastestInterval(AkiApplication.FAST_INTERVAL_CEILING_IN_MILLISECONDS);
		locationClient = new LocationClient(this, this, this);

		setSlidingActionBarEnabled(true);

		if (savedInstanceState == null) {
			chatFragment = new AkiChatFragment();
		} else {
			chatFragment = (AkiChatFragment) getSupportFragmentManager()
					.findFragmentById(R.id.aki_chat_frame);
		}

		Bundle extras = getIntent().getExtras();
		if ( extras != null ){
			chatFragment.setSeenSplash(extras.getBoolean("seenSplash", false));
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

	@Override
	protected void onDestroy(){

		//I added this
		stopPeriodicLocationUpdates();
		locationClient.disconnect();
		super.onDestroy();
	}

	@Override
	protected void onStop(){

		//We might not actually want this here, but in onDestroy instead:
//		stopPeriodicLocationUpdates();
//		locationClient.disconnect();

		super.onStop();

		AkiApplication.isNowInBackground();

		if ( AkiServerUtil.isActiveOnServer() ){
			AkiServerUtil.leaveServer(getApplicationContext());
		}
	}

	@Override
	protected void onPause(){

		super.onPause();
		AkiApplication.isNowInBackground();
	}

	@Override
	protected void onStart() {

		super.onStart();
		if ( !locationClient.isConnected() && !locationClient.isConnecting() ){
			locationClient.connect();
		}
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
	protected void onActivityResult(int requestCode, int resultCode, Intent data){

		switch (requestCode) {
		case AkiApplication.CONNECTION_FAILURE_RESOLUTION_REQUEST :

			switch (resultCode) {
			case Activity.RESULT_OK:
				Log.d(AkiApplication.TAG, "Google Play Services resolved the problem.");
				break;

			default:
				/*
				 * TODO: Google Play Services is not available, handle this!
				 */
				Log.e(AkiApplication.TAG, "Cannot use mandatory Google Play Services!");
				break;
			}

		default:
			super.onActivityResult(requestCode, resultCode, data);
			Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
			break;
		}
	}

	public boolean locationServicesConnected() {

		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

		if (ConnectionResult.SUCCESS == resultCode) {
			Log.d(AkiApplication.TAG, "Google Play Services Location API available!");
			return true;
		} else {
			Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0);
			if (dialog != null) {
				ErrorDialogFragment errorFragment = new ErrorDialogFragment();
				errorFragment.setDialog(dialog);
				errorFragment.show(getSupportFragmentManager(), AkiApplication.TAG);
			}
			/*
			 * TODO: Google Play Services is not available, handle this!
			 */
			Log.e(AkiApplication.TAG, "Cannot use mandatory Google Play Services!");
			return false;
		}
	}

	@Override
	public void onConnected(Bundle extras) {
		Log.i(AkiApplication.TAG, "Just connected to Location Service!");
		if ( AkiApplication.LOGGED_IN && locationServicesConnected() ){
			startPeriodicLocationUpdates();
		}
	}

	@Override
	public void onDisconnected() {
		/*
		 * TODO: Location client connection dropped because of an error, handle this!
		 */
		Log.e(AkiApplication.TAG, "Location Service connection dropped!");
	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {

		if (connectionResult.hasResolution()) {
			try {
				connectionResult.startResolutionForResult(this, 
					AkiApplication.CONNECTION_FAILURE_RESOLUTION_REQUEST);
			} catch (IntentSender.SendIntentException e) {
				Log.e(AkiApplication.TAG, "Could not start Google Play Services problem solver Intent.");
				e.printStackTrace();
				Log.e(AkiApplication.TAG, "Could not connect to Location Service!");
			}
		} else {
			showErrorDialog(connectionResult.getErrorCode());
			/*
			 * TODO: Connection to Location Service failed, handle this!
			 */
			Log.e(AkiApplication.TAG, "Could not connect to Location Service!");
		}
	}

	@Override
	public void onLocationChanged(Location location) {

		Context context = getApplicationContext();
		if ( context == null ){
			return;
		}
		String currentUserId = AkiInternalStorageUtil.getCurrentUser(context);
		if ( currentUserId == null ){
			return;
		}
		AkiInternalStorageUtil.cacheUserLocation(context, currentUserId, location);
		Log.w(AkiApplication.TAG, "Current location updated to: " +
				location.getLatitude() + ", " + location.getLongitude());
		
		double probability = (new Random()).nextDouble();
		if ( (!AkiApplication.IN_BACKGROUND && probability > 0.95)
				|| (AkiApplication.IN_BACKGROUND && probability > 0.75) ){
			AkiServerUtil.sendPresenceToServer(context, currentUserId);
		}
	}

	public void startPeriodicLocationUpdates() {
		Log.w(AkiApplication.TAG, "Started periodic location updates!");
		locationClient.requestLocationUpdates(locationRequest, this);
	}

	public void stopPeriodicLocationUpdates() {
		if (locationClient.isConnected()) {
			Log.w(AkiApplication.TAG, "Stopped periodic location updates!");
			locationClient.removeLocationUpdates(this);
		}
	}

	public SlidingMenu getSlidingMenu(){
		return slidingMenu;
	}

	public AkiSettingsFragment getSettingsFragment() {
		if ( settingsFragment == null ){
			settingsFragment = new AkiSettingsFragment();
		}
		return settingsFragment;
	}

	private void showErrorDialog(int errorCode) {

		Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(errorCode,
				this, AkiApplication.CONNECTION_FAILURE_RESOLUTION_REQUEST);

		if (errorDialog != null) {
			ErrorDialogFragment errorFragment = new ErrorDialogFragment();
			errorFragment.setDialog(errorDialog);
			errorFragment.show(getSupportFragmentManager(), AkiApplication.TAG);
		}
	}

	public static class ErrorDialogFragment extends DialogFragment {

		private Dialog dialog;

		public ErrorDialogFragment() {
			super();
			dialog = null;
		}

		public void setDialog(Dialog dialog) {
			this.dialog = dialog;
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			return dialog;
		}
	}
}