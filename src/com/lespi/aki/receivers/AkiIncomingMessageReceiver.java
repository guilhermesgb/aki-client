package com.lespi.aki.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;

public class AkiIncomingMessageReceiver extends BroadcastReceiver {

	private static final String TAG = "MessageReceiver";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		String channel = intent.getExtras().getString("com.parse.Channel");
		JsonObject dataJSON = JsonValue.readFrom(intent.getExtras().getString("com.parse.Data")).asObject();


		Log.d(TAG, "got action " + action + " on channel " + channel + " with:");

		for ( String key : dataJSON.names() ){
			Log.d(TAG, "..." + key + " => " + dataJSON.get(key).asString());
		}
	}
	
}