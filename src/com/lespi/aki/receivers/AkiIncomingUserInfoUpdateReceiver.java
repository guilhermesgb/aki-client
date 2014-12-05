package com.lespi.aki.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.AkiChatAdapter;
import com.lespi.aki.AkiChatFragment;
import com.lespi.aki.R;
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
				AkiInternalStorageUtil.cacheUserFullName(context, userId, fullName.asString());
			}
			
			String knownGender = "unknown";
			JsonValue userGender = incomingData.get("gender");
			if ( userGender != null ){
				
				if ( userGender.asString().equals("male") ){
					knownGender = "male";
				}
				else if ( userGender.asString().equals("female") ){
					knownGender = "female";
				}

				AkiInternalStorageUtil.cacheUserGender(context, userId, userGender.asString());
			}
			
			JsonValue nickname = incomingData.get("nickname");
			if ( nickname != null ){
				
				String oldNickname = AkiInternalStorageUtil.getCachedUserNickname(context, userId);
				if ( oldNickname != null && !oldNickname.equals(nickname.asString())
						&& currentUserId != null && !currentUserId.equals(userId) ){

					int formatId = R.string.com_lespi_aki_message_system_nickname_change_other;
					if ( knownGender.equals("male") ){
						formatId = R.string.com_lespi_aki_message_system_nickname_change_male;
					}
					else if ( knownGender.equals("female") ){
						formatId = R.string.com_lespi_aki_message_system_nickname_change_female;
					}
					String format = "%s " + context.getResources().getString(formatId) + " %s";
					AkiInternalStorageUtil.storeNewSystemMessage(context, chatRoom,
							String.format(format, oldNickname, nickname.asString()));
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
				if ( fullName != null
						&& currentUserId != null && !currentUserId.equals(userId) ){

					int formatId = R.string.com_lespi_aki_message_system_realname_reveal_other;
					if ( knownGender.equals("male") ){
						formatId = R.string.com_lespi_aki_message_system_realname_reveal_male;
					}
					else if ( knownGender.equals("female") ){
						formatId = R.string.com_lespi_aki_message_system_realname_reveal_female;
					}
					String format = "%s " + context.getResources().getString(formatId) + " %s";
					AkiInternalStorageUtil.storeNewSystemMessage(context, chatRoom,
							String.format(format, nickname.asString(), fullName.asString()));
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
			
			AkiChatFragment.getInstance().externalRefreshAll();
		}
	}
}