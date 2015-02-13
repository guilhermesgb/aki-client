package com.lespi.aki.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.AkiMutualAdapter;
import com.lespi.aki.AkiPrivateChatAdapter;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiInternalStorageUtil;

public class AkiIncomingMatchInfoUpdateReceiver extends BroadcastReceiver {

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
		
		if ( !AkiApplication.IN_BACKGROUND && AkiApplication.isPrivateChatShowing(from) ){
			AkiPrivateChatAdapter privateChatAdapter = AkiPrivateChatAdapter.getInstance(context, privateChatRoom);
			privateChatAdapter.notifyDataSetChanged();
		}
		else{
			AkiMutualAdapter mutualAdapter = AkiMutualAdapter.getInstance(context);
			mutualAdapter.notifyDataSetChanged();
		}
	}
}