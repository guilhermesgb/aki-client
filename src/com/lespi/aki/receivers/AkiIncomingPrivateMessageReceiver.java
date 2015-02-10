package com.lespi.aki.receivers;

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
import com.lespi.aki.AkiMainActivity;
import com.lespi.aki.AkiMutualAdapter;
import com.lespi.aki.R;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiServerUtil;

public class AkiIncomingPrivateMessageReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String event = intent.getAction();
		String privateChatRoom = intent.getExtras().getString("com.parse.Channel");
		JsonObject incomingData = JsonValue.readFrom(intent.getExtras().getString("com.parse.Data")).asObject();

		Log.d(AkiApplication.TAG, "Received event [" + event + "] on chat room [" + privateChatRoom + "].");

		String from = incomingData.get("from").asString();
		if (from.isEmpty()){
			return;
		}
		
		JsonValue anonymous = incomingData.get("anonymous");
		if ( anonymous != null && !anonymous.isNull() ){
			AkiInternalStorageUtil.setPrivateChatRoomAnonymousSetting(context, privateChatRoom, from, anonymous.asBoolean());
		}
		
		String currentUserId = AkiInternalStorageUtil.getCurrentUser(context);
		if ( currentUserId == null || currentUserId.equals(from) ){
			return;
		}

		intent.setClass(context, AkiMainActivity.class);
		intent.setFlags(Intent.FLAG_FROM_BACKGROUND | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

		Log.wtf(AkiApplication.TAG, "CURRENT PRIVATE CHAT ID: " + AkiApplication.CURRENT_PRIVATE_ID); //TODO remove this
		Log.wtf(AkiApplication.TAG, "RECEIVED PRIVATE CHAT ID: " + from); //TODO remove this
		Log.wtf(AkiApplication.TAG, "IN BACKGROUND: " + AkiApplication.IN_BACKGROUND); //TODO remove this
		
		if ( !AkiApplication.IN_BACKGROUND && AkiApplication.isPrivateChatShowing(from) ){
			Log.wtf(AkiApplication.TAG, "WILL NOT DISPLAY NOTIF!"); //TODO remove this			
			AkiInternalStorageUtil.setPrivateChatRoomUnreadCounter(context, privateChatRoom, 0);
			AkiServerUtil.restartGettingPrivateMessages(context, from);
		}
		else{

			//TODO increment unread counter for given private chat room
			int unreadCounter = AkiInternalStorageUtil.getPrivateChatRoomUnreadCounter(context, privateChatRoom);
			AkiInternalStorageUtil.setPrivateChatRoomUnreadCounter(context, privateChatRoom, ++unreadCounter);

			String contentTitle = context.getString(R.string.com_lespi_aki_notif_new_private_message_title);
			String contentText = context.getString(R.string.com_lespi_aki_notif_new_message_text);

			if ( unreadCounter > 1 ){
				contentTitle = String.format(context.getString(R.string.com_lespi_aki_notif_new_private_messages_title), unreadCounter);
				contentText = String.format(context.getString(R.string.com_lespi_aki_notif_new_messages_text), unreadCounter);
			}
			contentTitle = AkiInternalStorageUtil.getCachedUserFirstName(context, from) + " " + contentTitle;

			Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

			Notification.Builder notifyBuilder = new Notification.Builder(context)
			.setSmallIcon(R.drawable.notification_icon)
			.setContentTitle(contentTitle)
			.setContentText(contentText)
			.setNumber(unreadCounter)
			.setSound(alarmSound)
			.setAutoCancel(true);
			PendingIntent pending = PendingIntent.getActivity(context, 0, intent, 0);
			notifyBuilder.setContentIntent(pending);

			NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.notify(AkiApplication.INCOMING_MESSAGE_NOTIFICATION_ID, notifyBuilder.build());
			
			AkiMutualAdapter mutualAdapter = AkiMutualAdapter.getInstance(context);
			mutualAdapter.notifyDataSetChanged();
		}
	}
}