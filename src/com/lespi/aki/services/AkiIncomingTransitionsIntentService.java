package com.lespi.aki.services;

import java.util.List;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.LocationClient;
import com.lespi.aki.AkiApplication;
import com.lespi.aki.R;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiServerUtil;
import com.parse.internal.AsyncCallback;

public class AkiIncomingTransitionsIntentService extends IntentService {

	public AkiIncomingTransitionsIntentService(String name) {
		super(AkiApplication.TAG+":IncomingTransitionsIntentService$" + name);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.v(AkiApplication.TAG, "AkiMAINActivity.IncomingTransitionsIntentService$onHandleIntent");
		if (LocationClient.hasError(intent)) {
			int errorCode = LocationClient.getErrorCode(intent);
			Log.e(AkiApplication.TAG, "Location Service geofence error: " + Integer.toString(errorCode));
		} else {

			final Context context = getApplicationContext();

			int transitionType = LocationClient.getGeofenceTransition(intent);
			if ( transitionType == Geofence.GEOFENCE_TRANSITION_EXIT ) {

				String currentChatRoom = AkiInternalStorageUtil.getCurrentChatRoom(context);

				if ( currentChatRoom == null ){
					Log.e(AkiApplication.TAG, "There's no current chat room so cannot exit it due to being too far from it.");
					return;
				}

				List<Geofence> triggerList = LocationClient.getTriggeringGeofences(intent);
				for ( Geofence geofence : triggerList ){
					String geofenceId = geofence.getRequestId();
					if ( geofenceId.equals(currentChatRoom) ){
						AkiServerUtil.sendExitToServer(context, new AsyncCallback() {

							@Override
							public void onSuccess(Object response) {
								
								String currentUserId = AkiInternalStorageUtil.getCurrentUser(context);
								AkiServerUtil.leaveChatRoom(context, currentUserId);
								if ( !AkiApplication.IN_BACKGROUND ){

									CharSequence toastText = "You walked away from a chat room!";
									Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
									toast.show();
								}
								else{

									String contentTitle = context.getString(R.string.com_lespi_aki_notif_exit_title);
									String contentText = "You walked too far away from a chat room.";

									Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

									NotificationCompat.Builder notifyBuilder = new NotificationCompat.Builder(context)
									.setSmallIcon(R.drawable.notification_icon)
									.setContentTitle(contentTitle)
									.setContentText(contentText)
									.setSound(alarmSound)
									.setAutoCancel(true);

									NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
									notificationManager.notify(AkiApplication.EXITED_ROOM_NOTIFICATION_ID, notifyBuilder.build());
								}
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
				}

			}
			else {
				Log.e(AkiApplication.TAG, "Location Service geofence error: " + Integer.toString(transitionType));
			}
		}
	}
}