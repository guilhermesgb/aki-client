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
import com.lespi.aki.utils.AkiInternalStorageUtil.AkiLocation;

public class AkiIncomingUserInfoUpdateReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {

		String event = intent.getAction();
		String chatRoom = intent.getExtras().getString("com.parse.Channel");
		JsonObject incomingData = JsonValue.readFrom(intent.getExtras().getString("com.parse.Data")).asObject();

		Log.d(AkiApplication.TAG, "Received event [" + event + "] on chat room [" + chatRoom + "].");

		JsonValue userIdJSON = incomingData.get("from");
		
		String currentUserId = AkiInternalStorageUtil.getCurrentUser(context);
		
		if ( userIdJSON != null ){

			String userId = userIdJSON.asString();
			
			JsonValue firstName = incomingData.get("first_name");
			if ( firstName != null ){
				AkiInternalStorageUtil.cacheUserFirstName(context, userId, firstName.asString());
			}

			JsonValue fullName = incomingData.get("full_name");
			if ( fullName != null ){
				String oldFullName = AkiInternalStorageUtil.getCachedUserFullName(context, userId);
				if ( oldFullName != null && !oldFullName.equals(fullName.asString())
						&& currentUserId != null && !currentUserId.equals(userId) ){

					String format = "%s is now called: %s";
					AkiInternalStorageUtil.storeNewMessage(context, chatRoom,
							AkiApplication.SYSTEM_SENDER_ID, String.format(format, oldFullName, fullName.asString()));
				}
				AkiInternalStorageUtil.cacheUserFullName(context, userId, fullName.asString());
			}
			
			JsonValue userGender = incomingData.get("gender");
			if ( userGender != null ){

				AkiInternalStorageUtil.cacheUserGender(context, userId, userGender.asString());
			}
			
			JsonValue nickname = incomingData.get("nickname");
			if ( nickname != null ){
				
				String oldNickname = AkiInternalStorageUtil.getCachedUserNickname(context, userId);
				if ( oldNickname != null && !oldNickname.equals(nickname.asString())
						&& currentUserId != null && !currentUserId.equals(userId) ){

					String format = "%s has changed his nickname to: %s";
					AkiInternalStorageUtil.storeNewMessage(context, chatRoom,
							AkiApplication.SYSTEM_SENDER_ID, String.format(format, oldNickname, nickname.asString()));
				}
				AkiInternalStorageUtil.cacheUserNickname(context, userId, nickname.asString());
			}

			boolean anonymous = true;
			try {
				anonymous = incomingData.get("anonymous").asBoolean();
			}
			catch(UnsupportedOperationException e){
				Log.e(AkiApplication.TAG, "Received badly formatted JSON! Someone might be trying to pose as the server.");
				e.printStackTrace();
			}
			boolean previouslyAnonymous = AkiInternalStorageUtil.getAnonymousSetting(context, userId);
			if ( !anonymous && previouslyAnonymous ){
				if ( userGender != null && fullName != null
						&& currentUserId != null && !currentUserId.equals(userId) ){

					String format = userGender.equals("female") ? "%s has revealed she's called: %s" : "%s has revealed he's called: %s";
					AkiInternalStorageUtil.storeNewMessage(context, chatRoom,
							AkiApplication.SYSTEM_SENDER_ID, String.format(format, nickname, fullName));
				}
			}
			AkiInternalStorageUtil.setAnonymousSetting(context, userId, anonymous);
			
			try{
				
				AkiLocation location;
				if ( incomingData.get("location").isString() && incomingData.get("location").asString().equals("unknown") ){
					location = null;
				}
				else{
					JsonObject locationJSON = incomingData.get("location").asObject();
					location = new AkiLocation(locationJSON.get("lat").asDouble(), locationJSON.get("long").asDouble());
				}
				if ( location != null ){
					AkiInternalStorageUtil.cacheUserLocation(context, userId, location);
				}
			}
			catch(Exception e){
				Log.e(AkiApplication.TAG, "Received badly formatted JSON! Someone might be trying to pose as the server.");
				e.printStackTrace();
			}
			
			AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(context);
			chatAdapter.notifyDataSetChanged();
		}
	}
}