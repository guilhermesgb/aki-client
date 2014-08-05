package com.lespi.aki.receivers;

import java.io.FileOutputStream;
import java.io.IOException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.AkiMain;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;

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
		
		storeNewMessage(context, channel, from, message);
		
		intent.setClass(context, AkiMain.class);
		intent.setFlags(Intent.FLAG_FROM_BACKGROUND | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(intent);
	}
	
	private void storeNewMessage(Context context, String chat_room, String from, String message) {
		
		try {
			FileOutputStream fos = context.openFileOutput(chat_room, Context.MODE_PRIVATE | Context.MODE_APPEND);
			fos.write(String.format("%s:[%s]\n", from, message).getBytes());
			fos.close();
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "Message received from " + from + " could not be stored.");
			e.printStackTrace();
		}
	}

}