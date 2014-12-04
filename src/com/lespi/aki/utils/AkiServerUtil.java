package com.lespi.aki.utils;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.AkiMainActivity;
import com.lespi.aki.R;
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

	public static void isServerUp(final Context context, final AsyncCallback callback){

		AkiHttpUtil.doGETHttpRequest(context, "/", new AsyncCallback() { //AndWait taken out

			@Override
			public void onSuccess(Object response) {
				callback.onSuccess(response);
			}

			@Override
			public void onFailure(Throwable failure) {
				AkiApplication.serverDown();
				callback.onFailure(failure);
			}

			@Override
			public void onCancel() {
				if ( AkiApplication.SERVER_DOWN ){
					callback.onFailure(new Exception("Server is down!"));
				}
				else{
					callback.onSuccess(null);
				}
			}
		});
	}

	public static void getPresenceFromServer(final Context context, final AsyncCallback callback){

		AkiHttpUtil.doGETHttpRequest(context, "/presence", new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				JsonObject responseJSON = (JsonObject) response;
				if ( responseJSON.get("user_id") != null && !responseJSON.get("user_id").isNull() ){
					setActiveOnServer(true);
					callback.onSuccess(response);
				}
				else{
					setActiveOnServer(false);
					callback.onFailure(null);
				}
			}

			@Override
			public void onFailure(Throwable failure) {
				setActiveOnServer(false);
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

		AkiHttpUtil.doPOSTHttpRequest(context, "/presence/"+userId, payload, new AsyncCallback(){

			@Override
			public void onSuccess(Object response) {
				setActiveOnServer(true);
				if ( callback != null ){
					callback.onSuccess(response);
				}
			}

			@Override
			public void onFailure(Throwable failure) {
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

	public static void sendInactiveToServer(final Context context){

		AkiHttpUtil.doPOSTHttpRequest(context, "/inactive", new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				JsonObject responseJSON = (JsonObject) response;
				String responseCode = responseJSON.get("code").asString();
				if ( responseCode.equals("ok") ){
					setActiveOnServer(false);
				}				
			}

			@Override
			public void onFailure(Throwable failure) {
				Log.e(AkiApplication.TAG, "Could not send inactive.");
				failure.printStackTrace();
			}

			@Override
			public void onCancel() {
				Log.e(AkiApplication.TAG, "Endpoint:sendInactiveToServer callback canceled.");
			}
		});
	}

	public static void sendExitToServer(final Context context, final AsyncCallback callback){

		AkiHttpUtil.doPOSTHttpRequest(context, "/exit", new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				JsonObject responseJSON = (JsonObject) response;
				String responseCode = responseJSON.get("code").asString();
				if ( responseCode.equals("ok") ){
					setActiveOnServer(false);
					callback.onSuccess(response);
				}
			}

			@Override
			public void onFailure(Throwable failure) {
				Log.e(AkiApplication.TAG, "Could not send exit.");
				callback.onFailure(failure);
			}

			@Override
			public void onCancel() {
				Log.e(AkiApplication.TAG, "Endpoint:sendExitToServer callback canceled.");
				callback.onCancel();
			}
		});
	}

	public static void sendSkipToServer(final Context context, final AsyncCallback callback){

		AkiHttpUtil.doPOSTHttpRequest(context, "/skip", new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				JsonObject responseJSON = (JsonObject) response;
				String responseCode = responseJSON.get("code").asString();
				if ( responseCode.equals("ok") ){
					callback.onSuccess(response);
				}
			}

			@Override
			public void onFailure(Throwable failure) {
				Log.e(AkiApplication.TAG, "Could not send exit.");
				callback.onFailure(failure);
			}

			@Override
			public void onCancel() {
				Log.e(AkiApplication.TAG, "Endpoint:sendExitToServer callback canceled.");
				callback.onCancel();
			}
		});
	}

	public static void enterChatRoom(Context context, String currentUserId, String newChatRoom) {

		String currentChatRoom = AkiInternalStorageUtil.getCurrentChatRoom(context);
		if ( currentChatRoom == null ){
			Log.i(AkiApplication.TAG, "No current chat room address set.");
		}
		else if ( currentChatRoom.equals(newChatRoom) ){
			Log.i(AkiApplication.TAG, "No need to update current chat room, which" +
					" has address {" + currentChatRoom + "}.");
			PushService.subscribe(context, newChatRoom, AkiMainActivity.class);
			Log.i(AkiApplication.TAG, "Subscribed to chat room address {" + newChatRoom + "}.");
			return;
		}
		else{
			leaveChatRoom(context, currentUserId);
			Log.i(AkiApplication.TAG, "Had to leave current chat room address {" +
					currentChatRoom + "} because will be assigned to new chat room " +
					"address {" + newChatRoom + "}.");
			CharSequence toastText = context.getText(R.string.com_lespi_aki_toast_kicked_chat);
			Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_LONG);
			toast.show();
		}

		if ( !PushService.getSubscriptions(context).contains(newChatRoom) ){
			PushService.subscribe(context, newChatRoom, AkiMainActivity.class);
			Log.i(AkiApplication.TAG, "Subscribed to chat room address {" + newChatRoom + "}.");
		}
		AkiInternalStorageUtil.setCurrentChatRoom(context, newChatRoom);
		Log.i(AkiApplication.TAG, "Current chat room set to chat room address {" + newChatRoom + "}.");

		AkiInternalStorageUtil.setAnonymousSetting(context, currentUserId, true);
		AkiInternalStorageUtil.wipeCachedGeofenceCenter(context);
		AkiInternalStorageUtil.cacheGeofenceRadius(context, -1);
		AkiInternalStorageUtil.willUpdateGeofence(context);

		AkiInternalStorageUtil.storeNewMessage(context, newChatRoom, AkiApplication.SYSTEM_SENDER_ID,
				context.getResources().getString(R.string.com_lespi_aki_message_system_joined_new_chat_room));
	}

	public static void leaveChatRoom(Context context, String currentUserId) {

		if ( currentUserId != null ){
			AkiInternalStorageUtil.setAnonymousSetting(context, currentUserId, true);
		}
		
		String currentChatRoom = AkiInternalStorageUtil.getCurrentChatRoom(context);
		if ( currentChatRoom == null ){
			Log.i(AkiApplication.TAG, "No need to unsubscribe as no current chat room address is set.");
		}
		else{
			PushService.unsubscribe(context, currentChatRoom);
			AkiInternalStorageUtil.setCurrentChatRoom(context, null);
			Log.i(AkiApplication.TAG, "Unsubscribed from chat room address {" + currentChatRoom + "}.");
			AkiInternalStorageUtil.removeCachedMessages(context, currentChatRoom);
		}
		for ( String remainingChatRoom : PushService.getSubscriptions(context) ){
			PushService.unsubscribe(context, remainingChatRoom);
			Log.e(AkiApplication.TAG, "Cleanup -> unsubscribing from chat room address: {" + remainingChatRoom + "}.");
		}
	}

	public static void getMembersList(final Context context, final AsyncCallback callback) {

		AkiHttpUtil.doGETHttpRequest(context, "/members", new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				JsonObject members = ((JsonObject) response).get("members").asObject();
				List<String> memberIds = new ArrayList<String>();
				for ( String memberId : members.names() ){
					memberIds.add(memberId);
				}
				callback.onSuccess(memberIds);
			}

			@Override
			public void onFailure(Throwable failure) {
				callback.onFailure(failure);
			}

			@Override
			public void onCancel() {
				callback.onCancel();
			}
		});
	}

	public static void sendMessage(final Context context, String message, final AsyncCallback callback) {

		JsonObject payload = new JsonObject();
		payload.add("message", message);

		AkiHttpUtil.doPOSTHttpRequest(context, "/message", payload, new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				callback.onSuccess(response);
			}

			@Override
			public void onFailure(Throwable failure) {
				callback.onFailure(failure);
			}

			@Override
			public void onCancel() {
				callback.onCancel();
			}
		});
	}
}