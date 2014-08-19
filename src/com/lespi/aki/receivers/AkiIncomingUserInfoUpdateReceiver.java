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

			Log.e(AkiApplication.TAG, "FALL HERE");
			
			String userId = userIdJSON.asString();
			
			String firstName = incomingData.get("first_name").asString();
			if ( firstName != null && !firstName.trim().equals("null") ){
				AkiInternalStorageUtil.cacheUserFirstName(context, userId, firstName);
			}

			String fullName = incomingData.get("full_name").asString();
			if ( fullName != null && !fullName.trim().equals("null") ){
				String oldFullName = AkiInternalStorageUtil.getCachedUserFullName(context, userId);
				if ( oldFullName != null && !oldFullName.equals(fullName)
						&& currentUserId != null && !currentUserId.equals(userId) ){

					String format = "%s is now called: %s";
					AkiInternalStorageUtil.storeNewMessage(context, chatRoom,
							AkiApplication.SYSTEM_SENDER_ID, String.format(format, oldFullName, fullName));
				}
				AkiInternalStorageUtil.cacheUserFullName(context, userId, fullName);
			}
			
			String userGender = incomingData.get("gender").asString();
			if ( userGender != null ){

				AkiInternalStorageUtil.cacheUserGender(context, userId, userGender);
			}
			
			String nickname = incomingData.get("nickname").asString();
			if ( nickname != null && !nickname.trim().equals("null") ){
				
				String oldNickname = AkiInternalStorageUtil.getCachedUserNickname(context, userId);
				if ( oldNickname != null && !oldNickname.equals(nickname)
						&& currentUserId != null && !currentUserId.equals(userId) ){

					String format = "%s has changed his nickname to: %s";
					AkiInternalStorageUtil.storeNewMessage(context, chatRoom,
							AkiApplication.SYSTEM_SENDER_ID, String.format(format, oldNickname, nickname));
				}
				AkiInternalStorageUtil.cacheUserNickname(context, userId, nickname);
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

			Log.e(AkiApplication.TAG, "FALL HERE AS WELL");
		}
	}
}