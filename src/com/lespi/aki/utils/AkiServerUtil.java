package com.lespi.aki.utils;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;
import android.widget.Toast;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.AkiMainActivity;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.utils.AkiInternalStorageUtil.AkiLocation;
import com.parse.PushService;
import com.parse.internal.AsyncCallback;

public class AkiServerUtil {

	private static boolean activeOnServer = false;

	public static synchronized boolean isActiveOnServer(){
		return activeOnServer;
	}

	public static synchronized void setActiveOnServer(boolean active){
		AkiServerUtil.activeOnServer = active;
	}

	public static void getPresenceFromServer(final Context context, final AsyncCallback callback){

		AkiHttpUtil.doGETHttpRequest("/presence", new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				JsonObject responseJSON = (JsonObject) response;
				setActiveOnServer(true);
				if ( AkiApplication.DEBUG_MODE ){
					String username = responseJSON.get("username").asString();
					CharSequence toastText = "You are logged as: " + username;
					Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
					toast.show();
				}
				callback.onSuccess(response);
			}

			@Override
			public void onFailure(Throwable failure) {
				setActiveOnServer(false);
				if ( AkiApplication.DEBUG_MODE ){
					CharSequence toastText = "You are not logged.";
					Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
					toast.show();
				}
				callback.onFailure(failure);
			}

			@Override
			public void onCancel() {
				callback.onCancel();
			}
		});
	}

	public static void sendPresenceToServer(final Context context, final String userId){
		sendPresenceToServer(context, userId, null);
	}
	
	public static void sendPresenceToServer(final Context context, final String userId, final AsyncCallback callback){

		JsonObject payload = new JsonObject();
		String firstName = AkiInternalStorageUtil.getCachedUserFirstName(context, userId);
		if ( firstName != null ){
			payload.add("first_name", firstName);
		}
		
		String fullName = AkiInternalStorageUtil.getCachedUserFullName(context, userId);
		if ( fullName != null ){
			payload.add("full_name", fullName);
		}
		
		String gender = AkiInternalStorageUtil.getCachedUserGender(context, userId);
		if ( gender != null ){
			payload.add("gender", gender);
		}
		
		String nickname = AkiInternalStorageUtil.getCachedUserNickname(context, userId);
		if ( nickname != null ){
			payload.add("nickname", nickname);
		}
		
		boolean anonymous = AkiInternalStorageUtil.getAnonymousSetting(context, userId);
		payload.add("anonymous", anonymous);
		AkiLocation location = AkiInternalStorageUtil.getCachedUserLocation(context, userId);
		if ( location != null ){
			JsonObject locationJSON = new JsonObject();
			locationJSON.add("lat", location.latitude);
			locationJSON.add("long", location.longitude);
			payload.add("location", locationJSON);
		}
		else{
			payload.add("location", "unknown");
		}
		
		AkiHttpUtil.doPOSTHttpRequest("/presence/"+userId, payload, new AsyncCallback(){

			@Override
			public void onSuccess(Object response) {
				setActiveOnServer(true);
				if ( AkiApplication.DEBUG_MODE ){
					CharSequence toastText = "You have just sent presence as: " + userId;
					Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
					toast.show();
				}
				if ( callback != null ){
					callback.onSuccess(response);
				}
			}

			@Override
			public void onFailure(Throwable failure) {
				if ( AkiApplication.DEBUG_MODE ){
					CharSequence toastText = "You could not send presence.";
					Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
					toast.show();
				}
				if ( callback != null ){
					callback.onFailure(failure);
				}
			}

			@Override
			public void onCancel() {
				if ( callback != null ){
					callback.onCancel();
				}
			}
		});
	}

	public static void leaveServer(final Context context){

		AkiHttpUtil.doPOSTHttpRequest("/leave", new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				JsonObject responseJSON = (JsonObject) response;
				String responseCode = responseJSON.get("code").asString();
				if ( responseCode.equals("ok") ){
					setActiveOnServer(false);
					if ( AkiApplication.DEBUG_MODE ){
						CharSequence toastText = "You have just sent leave.";
						Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
						toast.show();
					}
				}				
			}

			@Override
			public void onFailure(Throwable failure) {
				if ( AkiApplication.DEBUG_MODE ){
					CharSequence toastText = "You could not send leave.";
					Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
					toast.show();
				}
				Log.e(AkiApplication.TAG, "Could not leave server.");
				failure.printStackTrace();
			}

			@Override
			public void onCancel() {
				Log.e(AkiApplication.TAG, "Endpoint:leaveServer callback canceled.");
			}
		});
	}

	public static void enterChatRoom(Context context, String newChatRoom) {

		String currentChatRoom = AkiInternalStorageUtil.getCurrentChatRoom(context);
		if ( currentChatRoom == null ){
			Log.i(AkiApplication.TAG, "No current chat room address set.");
		}
		else if ( currentChatRoom.equals(newChatRoom) ){
			Log.i(AkiApplication.TAG, "No need to update current chat room, which" +
					" has address {" + currentChatRoom + "}.");
			if ( !PushService.getSubscriptions(context).contains(newChatRoom) ){
				PushService.subscribe(context, newChatRoom, AkiMainActivity.class);
				Log.i(AkiApplication.TAG, "Subscribed to chat room address {" + newChatRoom + "}.");
			}
			return;
		}
		else{
			leaveChatRoom(context);
			Log.i(AkiApplication.TAG, "Had to leave current chat room address {" +
					currentChatRoom + "} because will be assigned to new chat room " +
					"address {" + newChatRoom + "}.");
			AkiInternalStorageUtil.removeCachedMessages(context, currentChatRoom);
		}

		if ( !PushService.getSubscriptions(context).contains(newChatRoom) ){
			PushService.subscribe(context, newChatRoom, AkiMainActivity.class);
			Log.i(AkiApplication.TAG, "Subscribed to chat room address {" + newChatRoom + "}.");
		}
		AkiInternalStorageUtil.setCurrentChatRoom(context, newChatRoom);
		Log.i(AkiApplication.TAG, "Current chat room set to chat room address {" + newChatRoom + "}.");
	}

	public static void leaveChatRoom(Context context) {

		String currentChatRoom = AkiInternalStorageUtil.getCurrentChatRoom(context);
		if ( currentChatRoom == null ){
			Log.i(AkiApplication.TAG, "No need to unsubscribe as no current chat room address is set.");
		}
		else{
			PushService.unsubscribe(context, currentChatRoom);
			Log.i(AkiApplication.TAG, "Unsubscribed from chat room address {" + currentChatRoom + "}.");
		}
		for ( String remainingChatRoom : PushService.getSubscriptions(context) ){
			PushService.unsubscribe(context, remainingChatRoom);
			Log.e(AkiApplication.TAG, "Cleanup -> unsubscribing from chat room address: {" + remainingChatRoom + "}.");
		}
	}

	public static void sendMessage(final Context context, String message, final AsyncCallback callback) {

		JsonObject payload = new JsonObject();
		payload.add("message", message);
		String currentChatRoom = AkiInternalStorageUtil.getCurrentChatRoom(context);
		if ( currentChatRoom == null ){
			Log.e(AkiApplication.TAG, "Chat room address is not cached!");
			callback.onFailure(new Exception("Chat room address is not cached!"));
			return;
		}
		else{
			payload.add("chat_room", currentChatRoom);
		}

		AkiHttpUtil.doPOSTHttpRequest("/message", payload, new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				if ( AkiApplication.DEBUG_MODE ){
					CharSequence toastText = "Message sent!";
					Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
					toast.show();
				}
				callback.onSuccess(response);
			}

			@Override
			public void onFailure(Throwable failure) {
				if ( AkiApplication.DEBUG_MODE ){
					CharSequence toastText = "You could not send message!";
					Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
					toast.show();
				}
				callback.onFailure(failure);
			}

			@Override
			public void onCancel() {
				callback.onCancel();
			}
		});
	}

	public static void updateGeolocation(Context context, String userId) {

		LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		if ( locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ) {
			Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			if ( location != null ){
				AkiInternalStorageUtil.cacheUserLocation(context, userId, location);
			}
		}
	}
}