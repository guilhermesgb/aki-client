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

	public static final String TAG = "__AkiMainActivity__";
	public static final String KEY_USER_ID = "user-id";

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
		Log.v(AkiMainActivity.TAG, "AkiMAINActivity$onCreate");

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
		Bundle extras = getIntent().getExtras();

		setContentView(R.layout.aki_chat_fragment);

		RelativeLayout background = (RelativeLayout) findViewById(R.id.com_lespi_aki_main_background);
		background.setVisibility(View.VISIBLE);

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
		slidingMenu.setShadowDrawable(R.drawable.shadow_left);
		slidingMenu.setSecondaryShadowDrawable(R.drawable.shadow_right);
		slidingMenu.setBehindOffsetRes(R.dimen.slidingmenu_offset);
		slidingMenu.setFadeEnabled(true);
		slidingMenu.setFadeDegree(0.75f);
		slidingMenu.setMode(SlidingMenu.LEFT_RIGHT);
		slidingMenu.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
		slidingMenu.setTouchModeBehind(SlidingMenu.TOUCHMODE_MARGIN);
		slidingMenu.setMenu(R.layout.aki_menu_frame);
		slidingMenu.setSecondaryMenu(R.layout.aki_mutual_interest_frame);
		slidingMenu.setContentFadeEnabled(true);
		slidingMenu.setContentFadeDegree(0.25f);

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

		slidingMenu.showMenu();
		slidingMenu.setSlidingEnabled(false);
	}

	@Override
	protected void onDestroy(){

		Log.v(AkiMainActivity.TAG, "AkiMAINActivity$onDestroy");
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
					.setTicker(contentTitle)
					.setContentIntent(PendingIntent.getActivity(AkiMainActivity.this, 0, new Intent(), 0))
					.setOnlyAlertOnce(true)
					.setAutoCancel(true);
					Notification.InboxStyle notifyBigBuilder = new Notification.InboxStyle(notifyBuilder);
					String[] contentLines = contentText.split("\n");
					for ( int i=0; i<contentLines.length; i++ ){
						notifyBigBuilder.addLine(contentLines[i]);
					}
					notifyBigBuilder.setBigContentTitle(contentTitle);

					NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
					notificationManager.notify(AkiApplication.EXITED_ROOM_NOTIFICATION_ID, notifyBigBuilder.build());
				}

				@Override
				public void onFailure(Throwable failure) {
					Log.e(AkiMainActivity.TAG, "A problem happened while exiting chat room!");
					failure.printStackTrace();
				}

				@Override
				public void onCancel() {
					Log.e(AkiMainActivity.TAG, "Could not cancel exiting chat room.");
				}
			});			
		}

		locationClient.disconnect();

		super.onDestroy();
	}

	@Override
	protected void onStop(){

		super.onStop();
		Log.v(AkiMainActivity.TAG, "AkiMAINActivity$onStop");

		AkiServerUtil.stopGettingMessages(getApplicationContext());
		if ( AkiApplication.CURRENT_PRIVATE_ID == null ){
			AkiApplication.isNowInBackground();
		}

		if ( AkiServerUtil.isActiveOnServer() ){
			AkiServerUtil.sendInactiveToServer(getApplicationContext());
		}
	}

	@Override
	protected void onPause(){
		Log.v(AkiMainActivity.TAG, "AkiMAINActivity$onPause");

		AkiServerUtil.stopGettingMessages(getApplicationContext());
		if ( AkiApplication.CURRENT_PRIVATE_ID == null ){
			AkiApplication.isNowInBackground();
		}
		super.onPause();
	}

	@Override
	protected void onStart() {

		super.onStart();
		Log.v(AkiMainActivity.TAG, "AkiMAINActivity$onStart");
		AkiApplication.isNowInForeground();
		if ( !locationClient.isConnected() && !locationClient.isConnecting() ){
			locationClient.connect();
		}
	}

	@Override
	protected void onResume(){
		Log.v(AkiMainActivity.TAG, "AkiMAINActivity$onResume");
		AkiApplication.isNowInForeground();

		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(AkiApplication.INCOMING_MESSAGE_NOTIFICATION_ID);
		notificationManager.cancel(AkiApplication.EXITED_ROOM_NOTIFICATION_ID);
		notificationManager.cancel(AkiApplication.NEW_MATCH_NOTIFICATION_ID);

		if ( AkiServerUtil.isActiveOnServer() ){
			chatFragment.onResume();
		}
		super.onResume();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data){

		Log.v(AkiMainActivity.TAG, "AkiMAINActivity$onActivityResult");

		switch (requestCode) {
		case AkiApplication.CONNECTION_FAILURE_RESOLUTION_REQUEST:

			switch (resultCode) {
			case Activity.RESULT_OK:
				Log.d(AkiMainActivity.TAG, "Google Play Services resolved the problem.");
				break;

			default:
				/*
				 * TODO: Google Play Services is not available, handle this!
				 */
				Log.e(AkiMainActivity.TAG, "Cannot use mandatory Google Play Services!");
				break;
			}

		default:
			super.onActivityResult(requestCode, resultCode, data);
			Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
			break;
		}
	}

	public boolean locationServicesConnected() {

		Log.v(AkiMainActivity.TAG, "AkiMAINActivity$locationServicesConnected");

		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

		if (ConnectionResult.SUCCESS == resultCode) {
			Log.d(AkiMainActivity.TAG, "Google Play Services Location API available!");
			return true;
		} else {
			Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0);
			if (dialog != null) {
				ErrorDialogFragment errorFragment = new ErrorDialogFragment();
				errorFragment.setDialog(dialog);
				errorFragment.show(getSupportFragmentManager(), AkiMainActivity.TAG);
			}
			/*
			 * TODO: Google Play Services is not available, handle this!
			 */
			Log.e(AkiMainActivity.TAG, "Cannot use mandatory Google Play Services!");
			return false;
		}
	}

	@Override
	public void onConnected(Bundle extras) {
		Log.v(AkiMainActivity.TAG, "AkiMAINActivity$onConnected");
		Log.i(AkiMainActivity.TAG, "Just connected to Location Service!");
		if ( AkiApplication.LOGGED_IN && locationServicesConnected() ){
			startPeriodicLocationUpdates();
		}
	}

	@Override
	public void onDisconnected() {
		Log.v(AkiMainActivity.TAG, "AkiMAINActivity$onDisconnected");
		/*
		 * TODO: Location client connection dropped because of an error, handle this!
		 */
		Log.e(AkiMainActivity.TAG, "Location Service connection dropped!");
	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {

		Log.v(AkiMainActivity.TAG, "AkiMAINActivity$onConnectionFailed");

		if (connectionResult.hasResolution()) {
			try {
				connectionResult.startResolutionForResult(this, 
						AkiApplication.CONNECTION_FAILURE_RESOLUTION_REQUEST);
			} catch (IntentSender.SendIntentException e) {
				Log.e(AkiMainActivity.TAG, "Could not start Google Play Services problem solver Intent.");
				e.printStackTrace();
				Log.e(AkiMainActivity.TAG, "Could not connect to Location Service!");
			}
		} else {
			showErrorDialog(connectionResult.getErrorCode());
			/*
			 * TODO: Connection to Location Service failed, handle this!
			 */
			Log.e(AkiMainActivity.TAG, "Could not connect to Location Service!");
		}
	}

	@Override
	public void onLocationChanged(Location location) {

		Log.v(AkiMainActivity.TAG, "AkiMAINActivity$onLocationChanged");

		final Context context = getApplicationContext();
		if ( context == null ){
			return;
		}
		final String currentUserId = AkiInternalStorageUtil.getCurrentUser(context);
		if ( currentUserId == null ){
			return;
		}

		if ( AkiInternalStorageUtil.isMandatorySettingMissing(context) ){
			return;
		}

		boolean sendPresence = false;

		final AkiLocation oldLocation = AkiInternalStorageUtil.getCachedUserLocation(context, currentUserId);
		if ( oldLocation == null ){
			sendPresence = true;
		}

		AkiInternalStorageUtil.cacheUserLocation(context, currentUserId, location);

		if ( sendPresence ){
			AkiServerUtil.sendPresenceToServer(context, currentUserId, new AsyncCallback() {

				@Override
				public void onSuccess(Object response) {
					if ( oldLocation == null ){
						chatFragment.onResume();
					}
					else if ( AkiApplication.LOGGED_IN ){

						JsonObject responseJSON = (JsonObject) response;
						final JsonValue newChatRoomId = responseJSON.get("chat_room");
						String currentChatRoomId = AkiInternalStorageUtil.getCurrentChatRoom(context);
						if ( newChatRoomId == null || ( currentChatRoomId != null 
								&& !newChatRoomId.asString().equals(currentChatRoomId) ) ){
							chatFragment.onResume();
						}
					}
				}

				@Override
				public void onFailure(Throwable failure) {
					Log.e(AkiMainActivity.TAG, "Could not send presence to server.");
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
					Log.e(AkiMainActivity.TAG, "Endpoint:sendPresenceToServer callback canceled!!");
					onResume();
				}
			});
		}
	}

	public void startPeriodicLocationUpdates() {
		if ( locationClient.isConnected() ){
			Log.wtf(AkiMainActivity.TAG, "AkiMAINActivity$startPeriodicLocationUpdates");
			locationClient.requestLocationUpdates(locationRequest, this);
		}
	}

	public void stopPeriodicLocationUpdates() {
		if (locationClient.isConnected()) {
			Log.wtf(AkiMainActivity.TAG, "AkiMAINActivity$stopPeriodicLocationUpdates");
			locationClient.removeLocationUpdates(this);
		}
	}

	public void setGeofence() {

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
			.setRequestId(AkiMainActivity.TAG + ":geofence")
			.build();
			if ( locationClient.isConnected() ){
				locationClient.addGeofences(Collections.singletonList(currentGeofence), geofencePendingIntent, this);
				AkiInternalStorageUtil.willNotUpdateGeofence(context);
			}
		}
	}

	public void removeGeofence(){

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
		if (LocationStatusCodes.SUCCESS == statusCode) {
			if ( geofenceRequestIds.length > 1 ){
				//				for ( int i=1; i<geofenceRequestIds.length; i++ ){
				//					Log.w(AkiMainActivity.TAG, "Multiple geofences detected: " + geofenceRequestIds[i]);
				//				}
			}
		} else {
			/*
			 * TODO: Connection to Location Service failed, handle this!
			 */
			Log.e(AkiMainActivity.TAG, "Could not add a Geofence using Location Service!");
		}
	}

	@Override
	public void onRemoveGeofencesByPendingIntentResult(int statusCode, PendingIntent pendingIntent) {
		Log.v(AkiMainActivity.TAG, "AkiMAINActivity$onRemoveGeofencesByPendingIntentResult");
		if (LocationStatusCodes.SUCCESS == statusCode) {
			Log.d(AkiMainActivity.TAG, "Geofence removed!");
		}
		else {
			/*
			 * TODO: Connection to Location Service failed, handle this!
			 */
			Log.e(AkiMainActivity.TAG, "Could not remove a Geofence using Location Service!");
		}
	}

	@Override
	public void onRemoveGeofencesByRequestIdsResult(int statusCode, String[] pendingIntent) {
		Log.v(AkiMainActivity.TAG, "AkiMAINActivity$onRemoveGeofencesByRequestIdsResult");
		if (LocationStatusCodes.SUCCESS == statusCode) {
			Log.d(AkiMainActivity.TAG, "Geofence removed!");
		}
		else {
			/*
			 * TODO: Connection to Location Service failed, handle this!
			 */
			Log.e(AkiMainActivity.TAG, "Could not remove a Geofence using Location Service!");
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

		Log.v(AkiMainActivity.TAG, "AkiMAINActivity$showErrorDialog");

		Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(errorCode,
				this, AkiApplication.CONNECTION_FAILURE_RESOLUTION_REQUEST);

		if (errorDialog != null) {
			ErrorDialogFragment errorFragment = new ErrorDialogFragment();
			errorFragment.setDialog(errorDialog);
			errorFragment.show(getSupportFragmentManager(), AkiMainActivity.TAG);
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