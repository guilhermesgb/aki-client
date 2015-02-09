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
import com.lespi.aki.AkiMatchProfileActivity;
import com.lespi.aki.AkiPrivateChatActivity;
import com.lespi.aki.AkiPrivateChatAdapter;
import com.lespi.aki.R;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiServerUtil;

public class AkiIncomingPrivateMessageReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String event = intent.getAction();
		//String chatRoom = intent.getExtras().getString("com.parse.Channel");
		JsonObject incomingData = JsonValue.readFrom(intent.getExtras().getString("com.parse.Data")).asObject();
		String currentUser = AkiInternalStorageUtil.getCurrentUser(context);

		Log.d(AkiApplication.TAG, "Received event [" + event + "] on chat room [" + intent.getExtras().getString("com.parse.Data") + "].");

		String from = incomingData.get("from").asString();
		
		if (from.isEmpty()){
			return;
		}
		
		intent.setClass(context, AkiPrivateChatActivity.class);
		
		intent.putExtra(AkiMatchProfileActivity.KEY_USER_ID, from);
		intent.setFlags(Intent.FLAG_FROM_BACKGROUND | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		
		if ( AkiApplication.isPrivateChatShowing(from) ){
			AkiServerUtil.restartGettingPrivateMessages(context, from);
		}
	}
}