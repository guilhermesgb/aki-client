package com.lespi.aki.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiInternalStorageUtil;

public class AkiIncomingUserMatchReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {

		String event = intent.getAction();
		String chatRoom = intent.getExtras().getString("com.parse.Channel");
		JsonObject incomingData = JsonValue.readFrom(intent.getExtras().getString("com.parse.Data")).asObject();

		Log.d(AkiApplication.TAG, "Received event [" + event + "] on chat room [" + chatRoom + "].");

		JsonValue userId1JSON = incomingData.get("uid1");
		JsonValue userId2JSON = incomingData.get("uid2");

		String currentUserId = AkiInternalStorageUtil.getCurrentUser(context);

		if ( userId1JSON != null && userId2JSON != null ) {
			String userId1 = userId1JSON.asString();
			String userId2 = userId2JSON.asString();
			if ( userId1.equals(currentUserId) ) {
				AkiInternalStorageUtil.storeNewMatch(context, userId2, true);
			}
			else if ( userId2.equals(currentUserId) ) {
				AkiInternalStorageUtil.storeNewMatch(context, userId1, true);
			}
		}
	}
}