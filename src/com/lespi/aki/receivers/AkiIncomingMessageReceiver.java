package com.lespi.aki.receivers;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.AkiMainActivity;
import com.lespi.aki.R;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiInternalStorageUtil;

public class AkiIncomingMessageReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		String channel = intent.getExtras().getString("com.parse.Channel");
		JsonObject incomingData = JsonValue.readFrom(intent.getExtras().getString("com.parse.Data")).asObject();

		Log.d(AkiApplication.TAG, "Received Action " + action + " on Parse Channel " + channel + " with:");

		for ( String key : incomingData.names() ){
			Log.d(AkiApplication.TAG, "..." + key + " => " + incomingData.get(key).asString());
		}
		
		String from = incomingData.get("from").asString();
		String message = incomingData.get("message").asString();
		
		AkiInternalStorageUtil.storeNewMessage(context, channel, from, message);
		
		intent.setClass(context, AkiMainActivity.class);
		intent.setFlags(Intent.FLAG_FROM_BACKGROUND | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

		if ( !AkiApplication.IN_BACKGROUND ){

			context.startActivity(intent);
		}
		else{
			
			++AkiApplication.INCOMING_MESSAGES_COUNTER;
			
			String contentTitle = context.getString(R.string.com_lespi_aki_notif_new_message_title);
			String contentText = context.getString(R.string.com_lespi_aki_notif_new_message_text);
			
			if ( AkiApplication.INCOMING_MESSAGES_COUNTER > 1 ){
				contentTitle = String.format(context.getString(R.string.com_lespi_aki_notif_new_messages_title),
						AkiApplication.INCOMING_MESSAGES_COUNTER);
				contentText = String.format(context.getString(R.string.com_lespi_aki_notif_new_messages_text),
						AkiApplication.INCOMING_MESSAGES_COUNTER);
			}
			
			Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
			
			NotificationCompat.Builder notifyBuilder = new NotificationCompat.Builder(context)
			        .setSmallIcon(R.drawable.new_message_icon)
			        .setContentTitle(contentTitle)
			        .setContentText(contentText)
			        .setNumber(AkiApplication.INCOMING_MESSAGES_COUNTER)
			        .setSound(alarmSound)
			        .setAutoCancel(true);
			PendingIntent pending = PendingIntent.getActivity(context, 0, intent, 0);
			notifyBuilder.setContentIntent(pending);
			
			NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.notify(AkiApplication.INCOMING_MESSAGE_NOTIFICATION_ID, notifyBuilder.build());
		}
	}
	
}