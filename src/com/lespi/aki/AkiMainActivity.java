package com.lespi.aki;

import java.util.Collections;

import android.app.Activity;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.facebook.Session;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationStatusCodes;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu.OnClosedListener;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu.OnOpenedListener;
import com.jeremyfeinstein.slidingmenu.lib.app.SlidingFragmentActivity;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.services.AkiIncomingTransitionsIntentService;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiInternalStorageUtil.AkiLocation;
import com.lespi.aki.utils.AkiServerUtil;
import com.parse.internal.AsyncCallback;

public class AkiMainActivity extends SlidingFragmentActivity implements
LocationListener,
GooglePlayServicesClient.ConnectionCallbacks,
GooglePlayServicesClient.OnConnectionFailedListener,
LocationClient.OnAddGeofencesResultListener,
LocationClient.OnRemoveGeofencesResultListener {

	private LocationRequest locationRequest;
	private LocationClient locationClient;
	private Geofence currentGeofence;
	private PendingIntent geofencePendingIntent;
	private AkiChatFragment chatFragment;
	private AkiSettingsFragment settingsFragment;
	private AkiMutualFragment mutualsFragment;
	private SlidingMenu slidingMenu;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(AkiApplication.TAG, "AkiMAINActivity$onCreate");

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		locationRequest = LocationRequest.create();
		locationRequest.setInterval(AkiApplication.UPDATE_INTERVAL_IN_MILLISECONDS);
		locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
		locationRequest.setFastestInterval(AkiApplication.FAST_INTERVAL_CEILING_IN_MILLISECONDS);
		locationClient = new LocationClient(this, this, this);


		setSlidingActionBarEnabled(true);

		if (savedInstanceState == null) {
			chatFragment = AkiChatFragment.getInstance();
		} else {
			chatFragment = (AkiChatFragment) getSupportFragmentManager()
					.findFragmentById(R.id.aki_chat_frame);
		}

		setContentView(R.layout.aki_chat_fragment);

		RelativeLayout background = (RelativeLayout) findViewById(R.id.com_lespi_aki_main_background);
		background.setVisibility(View.VISIBLE);
		
		Bundle extras = getIntent().getExtras();
		if ( extras != null ){
			boolean seenSplash = extras.getBoolean("seenSplash", false);
			chatFragment.setSeenSplash(seenSplash);
			
			if ( seenSplash ){
				background.setVisibility(View.GONE);
			}
		}
		
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

		mutualsFragment = new AkiMutualFragment();
		getSupportFragmentManager()
		.beginTransaction()
		.replace(R.id.aki_mutual_interest_frame, mutualsFragment)
		.commit();
		
		slidingMenu = super.getSlidingMenu();
		slidingMenu.setShadowWidthRes(R.dimen.shadow_width);
		slidingMenu.setShadowDrawable(R.drawable.shadow);
		slidingMenu.setBehindOffsetRes(R.dimen.slidingmenu_offset);
		slidingMenu.setFadeDegree(0.15f);
		slidingMenu.setMode(SlidingMenu.LEFT_RIGHT);
		slidingMenu.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
		slidingMenu.setTouchModeBehind(SlidingMenu.TOUCHMODE_MARGIN);
		slidingMenu.setMenu(R.layout.aki_mutual_interest_frame);
		slidingMenu.setSecondaryMenu(R.layout.aki_menu_frame);
		
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

		geofencePendingIntent = PendingIntent.getService(this, 0,
				new Intent(this, AkiIncomingTransitionsIntentService.class),
				PendingIntent.FLAG_UPDATE_CURRENT);

		ProgressBar loadingIcon = (ProgressBar) findViewById(R.id.com_lespi_aki_main_chat_progress_bar);
		loadingIcon.setVisibility(View.VISIBLE);
		slidingMenu.showSecondaryMenu();
		slidingMenu.setSlidingEnabled(false);
	}

	@Override
	protected void onDestroy(){

		Log.v(AkiApplication.TAG, "AkiMAINActivity$onDestroy");
		final Context context = getApplicationContext();

		stopPeriodicLocationUpdates();
		removeGeofence();

		if ( AkiApplication.LOGGED_IN ){
			AkiServerUtil.sendExitToServer(context, new AsyncCallback() {

				@Override
				public void onSuccess(Object response) {
					
					String currentUserId = AkiInternalStorageUtil.getCurrentUser(context);
					AkiServerUtil.leaveChatRoom(context, currentUserId);

					String contentTitle = context.getString(R.string.com_lespi_aki_notif_exit_title);
					String contentText = context.getString(R.string.com_lespi_aki_notif_exit_text_abort);

					Notification.Builder notifyBuilder = new Notification.Builder(context)
					.setSmallIcon(R.drawable.notification_icon)
					.setContentTitle(contentTitle)
					.setContentText(contentText)
					.setAutoCancel(true);

					NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
					notificationManager.notify(AkiApplication.EXITED_ROOM_NOTIFICATION_ID, notifyBuilder.build());
				}

				@Override
				public void onFailure(Throwable failure) {
					Log.e(AkiApplication.TAG, "A problem happened while exiting chat room!");
					failure.printStackTrace();
				}

				@Override
				public void onCancel() {
					Log.e(AkiApplication.TAG, "Could not cancel exiting chat room.");
				}
			});			
		}

		locationClient.disconnect();

		super.onDestroy();
	}

	@Override
	protected void onStop(){

		super.onStop();
		Log.v(AkiApplication.TAG, "AkiMAINActivity$onStop");

		Log.wtf("PULL MAN!", "Stopping getMessages runnable!");
		AkiServerUtil.stopGettingMessages(getApplicationContext());
		AkiApplication.isNowInBackground();

		if ( AkiServerUtil.isActiveOnServer() ){
			AkiServerUtil.sendInactiveToServer(getApplicationContext());
		}
	}

	@Override
	protected void onPause(){

		super.onPause();
		Log.v(AkiApplication.TAG, "AkiMAINActivity$onPause");

		Log.wtf("PULL MAN!", "Stopping getMessages runnable!");
		AkiServerUtil.stopGettingMessages(getApplicationContext());
		AkiApplication.isNowInBackground();
	}

	@Override
	protected void onStart() {

		super.onStart();
		Log.v(AkiApplication.TAG, "AkiMAINActivity$onStart");
		if ( !locationClient.isConnected() && !locationClient.isConnecting() ){
			locationClient.connect();
		}
	}

	@Override
	protected void onResume(){
		super.onResume();
		Log.v(AkiApplication.TAG, "AkiMAINActivity$onResume");
		AkiApplication.isNowInForeground();

		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(AkiApplication.INCOMING_MESSAGE_NOTIFICATION_ID);
		notificationManager.cancel(AkiApplication.EXITED_ROOM_NOTIFICATION_ID);

		if ( AkiServerUtil.isActiveOnServer() ){
			chatFragment.onResume();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data){

		Log.v(AkiApplication.TAG, "AkiMAINActivity$onActivityResult");

		switch (requestCode) {
		case AkiApplication.CONNECTION_FAILURE_RESOLUTION_REQUEST:

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

		Log.v(AkiApplication.TAG, "AkiMAINActivity$locationServicesConnected");

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
		Log.v(AkiApplication.TAG, "AkiMAINActivity$onConnected");
		Log.i(AkiApplication.TAG, "Just connected to Location Service!");
		if ( AkiApplication.LOGGED_IN && locationServicesConnected() ){
			startPeriodicLocationUpdates();
		}
	}

	@Override
	public void onDisconnected() {
		Log.v(AkiApplication.TAG, "AkiMAINActivity$onDisconnected");
		/*
		 * TODO: Location client connection dropped because of an error, handle this!
		 */
		Log.e(AkiApplication.TAG, "Location Service connection dropped!");
	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {

		Log.v(AkiApplication.TAG, "AkiMAINActivity$onConnectionFailed");

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

		Log.v(AkiApplication.TAG, "AkiMAINActivity$onLocationChanged");

		final Context context = getApplicationContext();
		if ( context == null ){
			return;
		}
		final String currentUserId = AkiInternalStorageUtil.getCurrentUser(context);
		if ( currentUserId == null ){
			return;
		}

		boolean sendPresence = false;

		final AkiLocation oldLocation = AkiInternalStorageUtil.getCachedUserLocation(context, currentUserId);
		if ( oldLocation == null ){
			sendPresence = true;
		}

		AkiInternalStorageUtil.cacheUserLocation(context, currentUserId, location);
		Log.w(AkiApplication.TAG, "Current location updated to: " +
				location.getLatitude() + ", " + location.getLongitude());

		if ( sendPresence ){
			AkiServerUtil.sendPresenceToServer(context, currentUserId, new AsyncCallback() {

				@Override
				public void onSuccess(Object response) {
					if ( oldLocation == null ){
						Log.d(AkiApplication.TAG, "Reloading as we just got user location information for the first time.");
						chatFragment.onResume();
					}
					else if ( AkiApplication.LOGGED_IN ){
						JsonObject responseJSON = (JsonObject) response;
						final JsonValue newChatRoomId = responseJSON.get("chat_room");
						String currentChatRoomId = AkiInternalStorageUtil.getCurrentChatRoom(context);
						if ( newChatRoomId == null || ( currentChatRoomId != null && !newChatRoomId.asString().equals(currentChatRoomId) ) ){
							Log.d(AkiApplication.TAG, "Server informed it is a good time to reload.");
							chatFragment.onResume();
						}
					}
				}

				@Override
				public void onFailure(Throwable failure) {
					Log.e(AkiApplication.TAG, "Could not send presence to server.");
					failure.printStackTrace();
				}

				@Override
				public void onCancel() {
					CharSequence toastText;
					if ( AkiApplication.SERVER_DOWN ){
						toastText = context.getText(R.string.com_lespi_aki_toast_server_down);
					}
					else{
						toastText = context.getText(R.string.com_lespi_aki_toast_no_internet_connection);
					}
					Toast toast = Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_SHORT);
					toast.show();
					Log.e(AkiApplication.TAG, "Endpoint:sendPresenceToServer callback canceled!!");
					onResume();
				}
			});
		}
	}

	public void startPeriodicLocationUpdates() {
		Log.w(AkiApplication.TAG, "Started periodic location updates!");
		if ( locationClient.isConnected() ){
			locationClient.requestLocationUpdates(locationRequest, this);
		}
	}

	public void stopPeriodicLocationUpdates() {
		Log.v(AkiApplication.TAG, "AkiMAINActivity$stopPeriodicLocationUpdates");
		if (locationClient.isConnected()) {
			Log.w(AkiApplication.TAG, "Stopped periodic location updates!");
			locationClient.removeLocationUpdates(this);
		}
	}

	public void setGeofence() {

		Log.v(AkiApplication.TAG, "AkiMAINActivity$setGeofence");
		Context context = getApplicationContext();

		if ( AkiInternalStorageUtil.shouldUpdateGeofence(context) ){

			locationClient.removeGeofences(geofencePendingIntent, this);

			AkiLocation center = AkiInternalStorageUtil.getCachedGeofenceCenter(context);
			float radius = AkiInternalStorageUtil.getCachedGeofenceRadius(context);

			if ( center == null || radius == -1 ){
				String currentUserId = AkiInternalStorageUtil.getCurrentUser(context);
				if ( currentUserId == null ){
					return;
				}
				center = AkiInternalStorageUtil.getCachedUserLocation(context, currentUserId);
				if ( center == null ){
					return;
				}
				AkiInternalStorageUtil.cacheGeofenceCenter(context, center);

				radius = AkiApplication.MIN_RADIUS;
				AkiInternalStorageUtil.cacheGeofenceRadius(context, radius);
			}

			currentGeofence = new Geofence.Builder()
			.setCircularRegion(center.latitude, center.longitude, radius)
			.setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
			.setExpirationDuration(Geofence.NEVER_EXPIRE)
			.setRequestId(AkiApplication.TAG + ":geofence")
			.build();
			if ( locationClient.isConnected() ){
				Log.w(AkiApplication.TAG, "Geofence will be updated to: center=(" + center.latitude + ", " + center.longitude + "), radius=" + radius);
				locationClient.addGeofences(Collections.singletonList(currentGeofence), geofencePendingIntent, this);
				AkiInternalStorageUtil.willNotUpdateGeofence(context);
			}
		}
	}

	public void removeGeofence(){

		Log.v(AkiApplication.TAG, "AkiMAINActivity$removeGeofence");

		if ( locationClient.isConnected() ){
			locationClient.removeGeofences(geofencePendingIntent, this);

			Context context = getApplicationContext();
			AkiInternalStorageUtil.wipeCachedGeofenceCenter(context);
			AkiInternalStorageUtil.cacheGeofenceRadius(context, -1);
			AkiInternalStorageUtil.willUpdateGeofence(context);
		}
	}

	@Override
	public void onAddGeofencesResult(int statusCode, String[] geofenceRequestIds) {
		Log.v(AkiApplication.TAG, "AkiMAINActivity$onAddGeofencesResult");
		if (LocationStatusCodes.SUCCESS == statusCode) {
			Log.d(AkiApplication.TAG, "Geofence updated successfully!");
			if ( geofenceRequestIds.length > 1 ){
				for ( int i=1; i<geofenceRequestIds.length; i++ ){
					Log.w(AkiApplication.TAG, "Multiple geofences detected: " + geofenceRequestIds[i]);
				}
			}
		} else {
			/*
			 * TODO: Connection to Location Service failed, handle this!
			 */
			Log.e(AkiApplication.TAG, "Could not add a Geofence using Location Service!");
		}
	}

	@Override
	public void onRemoveGeofencesByPendingIntentResult(int statusCode, PendingIntent pendingIntent) {
		Log.v(AkiApplication.TAG, "AkiMAINActivity$onRemoveGeofencesByPendingIntentResult");
		if (LocationStatusCodes.SUCCESS == statusCode) {
			Log.d(AkiApplication.TAG, "Geofence removed!");
		}
		else {
			/*
			 * TODO: Connection to Location Service failed, handle this!
			 */
			Log.e(AkiApplication.TAG, "Could not remove a Geofence using Location Service!");
		}
	}

	@Override
	public void onRemoveGeofencesByRequestIdsResult(int statusCode, String[] pendingIntent) {
		Log.v(AkiApplication.TAG, "AkiMAINActivity$onRemoveGeofencesByRequestIdsResult");
		if (LocationStatusCodes.SUCCESS == statusCode) {
			Log.d(AkiApplication.TAG, "Geofence removed!");
		}
		else {
			/*
			 * TODO: Connection to Location Service failed, handle this!
			 */
			Log.e(AkiApplication.TAG, "Could not remove a Geofence using Location Service!");
		}
	}

	public static boolean isLocationProviderEnabled(Context context){
		final LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
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

		Log.v(AkiApplication.TAG, "AkiMAINActivity$showErrorDialog");

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
