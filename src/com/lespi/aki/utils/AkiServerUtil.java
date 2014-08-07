package com.lespi.aki.utils;

import java.io.FileNotFoundException;
import java.io.IOException;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.AkiMain;
import com.lespi.aki.http.AkiHttpUtils;
import com.lespi.aki.json.JsonObject;
import com.parse.PushService;

public class AkiServerUtil {

	private static boolean activeOnServer = false;
	private static String activeUser = null;
	
	public static boolean isActiveOnServer(){
		return activeOnServer;
	}
	
	/*
	 * This filed is currently not being used externally - study possibility of removing this
	 */
	public static String getActiveUser(){
		return activeUser;
	}
	
	public static boolean getPresenceFromServer(Context context){

		JsonObject response = AkiHttpUtils.doGETHttpRequest("/presence");
		if ( response != null ){
			String responseCode = response.get("code").asString();
			if ( responseCode.equals("ok") ){
				activeOnServer = true;
				String username = response.get("username").asString();
				activeUser = username;
				if ( AkiApplication.DEBUG_MODE ){
					CharSequence toastText = "You are logged as: " + username;
					Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
					toast.show();
				}
				return true;
			}
		}
		activeOnServer = false;
		activeUser = null;
		if ( AkiApplication.DEBUG_MODE ){
			CharSequence toastText = "You are not logged.";
			Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
			toast.show();
		}
		return false;
	}
	
	public static boolean sendPresenceToServer(Context context, String username){

		JsonObject response = AkiHttpUtils.doPOSTHttpRequest("/presence/"+username);
		if ( response != null ){
			String responseCode = response.get("code").asString();
			if ( responseCode.equals("ok") ){
				activeOnServer = true;
				activeUser = username;
				if ( AkiApplication.DEBUG_MODE ){
					CharSequence toastText = "You have just logged as: " + username;
					Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
					toast.show();
				}
				return true;
			}
		}
		if ( AkiApplication.DEBUG_MODE ){
			CharSequence toastText = "You could not log in.";
			Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
			toast.show();
		}
		return false;
	}
	
	public static boolean leaveServer(Context context){

		JsonObject response = AkiHttpUtils.doPOSTHttpRequest("/leave");
		if ( response != null ){
			String responseCode = response.get("code").asString();
			if ( responseCode.equals("ok") ){
				activeOnServer = false;
				activeUser = null;
				if ( AkiApplication.DEBUG_MODE ){
					CharSequence toastText = "You have just logged out.";
					Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
					toast.show();
				}
				return true;
			}
		}
		if ( AkiApplication.DEBUG_MODE ){
			CharSequence toastText = "You could not log out.";
			Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
			toast.show();
		}
		return false;
	}
	
	public static void enterChatRoom(Context context) {

		JsonObject response = AkiHttpUtils.doPOSTHttpRequest("/chat");
		if ( response != null ){
			String responseCode = response.get("code").asString();
			if ( responseCode.equals("ok") ){
				String newChatRoom = response.get("chat_room").asString();
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
		}
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

	public static boolean sendMessage(Context context, String message) {

		JsonObject payload = new JsonObject();
		payload.add("message", message);
		try {
			payload.add("chat_room", AkiInternalStorageUtil.getCurrentChatRoom(context));
		} catch (FileNotFoundException e) {
			Log.e(AkiApplication.TAG, "Chat room address is not cached!");
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "A problem occurred while trying to retrieve chat room address from cache!");
			e.printStackTrace();
			return false;
		}
		
		JsonObject response = AkiHttpUtils.doPOSTHttpRequest("/message", payload);
		if ( response != null ){
			String responseCode = response.get("code").asString();
			if ( responseCode.equals("ok") ){
				if ( AkiApplication.DEBUG_MODE ){
					CharSequence toastText = "Message sent!";
					Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
					toast.show();
				}
				return true;
			}
		}
		if ( AkiApplication.DEBUG_MODE ){
			CharSequence toastText = "You could not send message!";
			Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
			toast.show();
		}
		return false;
	}
	
}