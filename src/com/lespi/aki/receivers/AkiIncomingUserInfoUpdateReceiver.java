package com.lespi.aki.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.AkiChatAdapter;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiInternalStorageUtil;

public class AkiIncomingUserInfoUpdateReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {

		String event = intent.getAction();
		String chatRoom = intent.getExtras().getString("com.parse.Channel");
		JsonObject incomingData = JsonValue.readFrom(intent.getExtras().getString("com.parse.Data")).asObject();

		Log.d(AkiApplication.TAG, "Received event [" + event + "] on chat room [" + chatRoom + "].");


		String userId = incomingData.get("from").asString();

		String nickname = incomingData.get("nickname").asString();
		String oldNickname = AkiInternalStorageUtil.getCachedNickname(context, userId);
		if ( oldNickname != null && !oldNickname.equals(nickname) ){
			AkiInternalStorageUtil.storeNewMessage(context, chatRoom,
					AkiApplication.SYSTEM_SENDER_ID, String.format("%s has changed his nickname to: %s", oldNickname, nickname));
		}
		AkiInternalStorageUtil.cacheNickname(context, userId, nickname);
		
		Boolean anonymous = incomingData.get("anonymous").asBoolean();
		AkiInternalStorageUtil.setAnonymousSetting(context, userId, anonymous);
		
		AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(context);
		chatAdapter.notifyDataSetChanged();
	}
}