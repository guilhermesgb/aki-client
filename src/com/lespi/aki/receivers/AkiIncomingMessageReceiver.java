package com.lespi.aki.receivers;

import java.util.List;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.AkiChatAdapter;
import com.lespi.aki.AkiChatFragment;
import com.lespi.aki.AkiMainActivity;
import com.lespi.aki.R;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiServerUtil;

public class AkiIncomingMessageReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String event = intent.getAction();
		String chatRoom = intent.getExtras().getString("com.parse.Channel");
		JsonObject incomingData = JsonValue.readFrom(intent.getExtras().getString("com.parse.Data")).asObject();

		Log.d(AkiApplication.TAG, "Received event [" + event + "] on chat room [" + chatRoom + "].");

		String from = incomingData.get("from").asString();
		
		if ( from.equals(AkiApplication.SYSTEM_SENDER_ID) ){
			return;
		}
		
		String message = incomingData.get("message").asString();
		String timestamp = incomingData.get("timestamp").asString();
		
		AkiInternalStorageUtil.storePushedMessage(context, chatRoom, from, message, timestamp);

		if ( !AkiApplication.IN_BACKGROUND && AkiServerUtil.isActiveOnServer() ){

			AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(context);
			List<JsonObject> messages = AkiChatAdapter.toJsonObjectList(AkiInternalStorageUtil.retrieveMessages(context,
					AkiInternalStorageUtil.getCurrentChatRoom(context)));
			
			chatAdapter.clear();
			if ( messages != null ){
				chatAdapter.addAll(messages);
			}
			chatAdapter.notifyDataSetChanged();
			
			AkiChatFragment.getInstance().externalRefreshAll();
		}
		else{
			
			++AkiApplication.INCOMING_MESSAGES_COUNTER;
			
			String contentTitle = context.getString(R.string.com_lespi_aki_notif_new_message_title);
			String contentTicker = context.getString(R.string.com_lespi_aki_notif_new_message_ticker);
			
			if ( AkiApplication.INCOMING_MESSAGES_COUNTER > 1 ){
				contentTitle = String.format(context.getString(R.string.com_lespi_aki_notif_new_messages_title),
						AkiApplication.INCOMING_MESSAGES_COUNTER);
				contentTicker = String.format(context.getString(R.string.com_lespi_aki_notif_new_messages_ticker),
						AkiApplication.INCOMING_MESSAGES_COUNTER);
			}
			
			String identifier = AkiInternalStorageUtil.getCachedUserFirstName(context, from);
			if ( identifier == null || AkiInternalStorageUtil.getAnonymousSetting(context, from) ){
				identifier = AkiInternalStorageUtil.getCachedUserNickname(context, from);
				if ( identifier == null){
					identifier = from;
				}
			}

			if ( AkiApplication.INCOMING_MESSAGES_CACHE.trim().isEmpty() ){
				AkiApplication.INCOMING_MESSAGES_CACHE = identifier + ": " + message.trim();
			}
			else {
				AkiApplication.INCOMING_MESSAGES_CACHE += "\n" + identifier + ": " + message.trim();
			}
			
			Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
			
			Notification.Builder notifyBuilder = new Notification.Builder(context)
			        .setSmallIcon(R.drawable.notification_icon)
			        .setContentTitle(contentTitle)
			        .setTicker(contentTicker)
			        .setSubText(contentTicker)
			        .setSound(alarmSound)
			        .setAutoCancel(true);
			Notification.InboxStyle notifyBigBuilder = new Notification.InboxStyle(notifyBuilder);
			String[] contentLines = AkiApplication.INCOMING_MESSAGES_CACHE.split("\n");
			for ( int i=0; i<contentLines.length; i++ ){
				notifyBigBuilder.addLine(contentLines[i]);
			}
			notifyBigBuilder.setBigContentTitle(contentTitle);
			intent.setClass(context, AkiMainActivity.class);
			if ( AkiApplication.IN_BACKGROUND ){
				intent.setFlags(Intent.FLAG_FROM_BACKGROUND | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			}
			PendingIntent pending = PendingIntent.getActivity(context, 0, intent, 0);
			notifyBuilder.setContentIntent(pending);
			
			NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.notify(AkiApplication.INCOMING_MESSAGE_NOTIFICATION_ID, notifyBigBuilder.build());
		}
	}
}