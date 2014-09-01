package com.lespi.aki.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.AkiMainActivity;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiInternalStorageUtil.AkiLocation;

public class AkiIncomingGeofenceUpdateReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {

		String event = intent.getAction();
		String chatRoom = intent.getExtras().getString("com.parse.Channel");
		JsonObject incomingData = JsonValue.readFrom(intent.getExtras().getString("com.parse.Data")).asObject();

		Log.d(AkiApplication.TAG, "Received event [" + event + "] on chat room [" + chatRoom + "].");

		AkiLocation center = null;
		JsonObject centerJSON = incomingData.get("center").asObject();
		if ( centerJSON != null ){
			try{
				center = new AkiLocation(centerJSON.get("lat").asDouble(), centerJSON.get("long").asDouble());
			}
			catch(Exception e){
				center = null;
			}
		}
		Float radius = incomingData.get("radius").asFloat();
		if ( center != null && radius != null ){
			AkiInternalStorageUtil.cacheGeofenceCenter(context, center);
			AkiInternalStorageUtil.cacheGeofenceRadius(context, radius);
			AkiInternalStorageUtil.willUpdateGeofence(context);
		}
		
		intent.setClass(context, AkiMainActivity.class);
		intent.setFlags(Intent.FLAG_FROM_BACKGROUND | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

		if ( !AkiApplication.IN_BACKGROUND ){

			context.startActivity(intent);
		}
	}
}