package com.lespi.aki.utils;

import java.io.FileNotFoundException;
import java.io.IOException;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.AkiMain;
import com.lespi.aki.json.JsonObject;
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

	public static void sendPresenceToServer(final Context context, final String username, final AsyncCallback callback){

		AkiHttpUtil.doPOSTHttpRequest("/presence/"+username, new AsyncCallback(){

			@Override
			public void onSuccess(Object response) {
				setActiveOnServer(true);
				if ( AkiApplication.DEBUG_MODE ){
					CharSequence toastText = "You have just logged as: " + username;
					Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
					toast.show();
				}
				callback.onSuccess(response);
			}

			@Override
			public void onFailure(Throwable failure) {
				if ( AkiApplication.DEBUG_MODE ){
					CharSequence toastText = "You could not log in.";
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

	public static void leaveServer(final Context context){

		AkiHttpUtil.doPOSTHttpRequest("/leave", new AsyncCallback() {
			
			@Override
			public void onSuccess(Object response) {
				JsonObject responseJSON = (JsonObject) response;
				String responseCode = responseJSON.get("code").asString();
				if ( responseCode.equals("ok") ){
					setActiveOnServer(false);
					if ( AkiApplication.DEBUG_MODE ){
						CharSequence toastText = "You have just logged out.";
						Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
						toast.show();
					}
				}				
			}
			
			@Override
			public void onFailure(Throwable failure) {
				if ( AkiApplication.DEBUG_MODE ){
					CharSequence toastText = "You could not log out.";
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

		try {
			String currentChatRoom = AkiInternalStorageUtil.getCurrentChatRoom(context);
			if ( currentChatRoom.equals(newChatRoom) ){
				Log.i(AkiApplication.TAG, "No need to update current chat room, which" +
						" has address {" + currentChatRoom + "}.");
				if ( !PushService.getSubscriptions(context).contains(newChatRoom) ){
					PushService.subscribe(context, newChatRoom, AkiMain.class);
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
		} catch (FileNotFoundException e) {
			Log.i(AkiApplication.TAG, "No current chat room address set.");
		} catch (IOException e) {
			leaveChatRoom(context);
			AkiInternalStorageUtil.unsetCurrentChatRoom(context);
			Log.e(AkiApplication.TAG, "IO error while attempting to figure out current chat room address. " + 
					"Performed cleanup operations.");
			e.printStackTrace();
		}
		if ( !PushService.getSubscriptions(context).contains(newChatRoom) ){
			PushService.subscribe(context, newChatRoom, AkiMain.class);
			Log.i(AkiApplication.TAG, "Subscribed to chat room address {" + newChatRoom + "}.");
		}
		AkiInternalStorageUtil.setCurrentChatRoom(context, newChatRoom);
		Log.i(AkiApplication.TAG, "Current chat room set to chat room address {" + newChatRoom + "}.");
	}

	public static void leaveChatRoom(Context context) {

		try {

			String currentChatRoom = AkiInternalStorageUtil.getCurrentChatRoom(context);
			PushService.unsubscribe(context, currentChatRoom);
			Log.i(AkiApplication.TAG, "Unsubscribed from chat room address {" + currentChatRoom + "}.");
		} catch ( FileNotFoundException e ){
			Log.i(AkiApplication.TAG, "No need to unsubscribe as no current chat room address is set.");
		} catch ( IOException e ){
			Log.e(AkiApplication.TAG, "Could NOT figure out current chat room address.");
			e.printStackTrace();
			AkiInternalStorageUtil.unsetCurrentChatRoom(context);
			Log.e(AkiApplication.TAG, "Cleanup -> removed current chat room cache file.");
		}
		for ( String remainingChatRoom : PushService.getSubscriptions(context) ){
			PushService.unsubscribe(context, remainingChatRoom);
			Log.e(AkiApplication.TAG, "Cleanup -> unsubscribing from chat room address: {" + remainingChatRoom + "}.");
		}
	}

	public static void sendMessage(final Context context, String message, final AsyncCallback callback) {

		JsonObject payload = new JsonObject();
		payload.add("message", message);
		try {
			payload.add("chat_room", AkiInternalStorageUtil.getCurrentChatRoom(context));
		} catch (FileNotFoundException e) {
			Log.e(AkiApplication.TAG, "Chat room address is not cached!");
			e.printStackTrace();
			callback.onFailure(new Exception("Chat room address is not cached!"));
			return;
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "A problem occurred while trying to retrieve chat room address from cache!");
			e.printStackTrace();
			callback.onFailure(new Exception("A problem occurred while trying to retrieve chat room address from cache!"));
			return;
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

}