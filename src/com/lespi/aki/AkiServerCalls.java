package com.lespi.aki;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.lespi.aki.http.AkiHttpUtils;
import com.lespi.aki.json.JsonObject;
import com.parse.PushService;

public class AkiServerCalls {

	private static boolean activeOnServer = false;
	
	public static boolean isActiveOnServer(){
		return activeOnServer;
	}
	
	public static boolean getPresenceFromServer(Context context){

		JsonObject response = AkiHttpUtils.doGETHttpRequest("/presence");
		if ( response != null ){
			String responseCode = response.get("code").asString();
			if ( responseCode.equals("ok") ){
				activeOnServer = true;
				String username = response.get("username").asString();
				CharSequence toastText = "You are logged as: " + username;
				Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
				toast.show();
				return true;
			}
		}
		activeOnServer = false;
		CharSequence toastText = "You are not logged.";
		Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
		toast.show();
		return false;
	}
	
	public static boolean sendPresenceToServer(Context context, String username){

		JsonObject response = AkiHttpUtils.doPOSTHttpRequest("/presence/"+username);
		if ( response != null ){
			String responseCode = response.get("code").asString();
			if ( responseCode.equals("ok") ){
				activeOnServer = true;
				CharSequence toastText = "You have just logged as: " + username;
				Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
				toast.show();
				return true;
			}
		}
		CharSequence toastText = "You could not log in.";
		Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
		toast.show();
		return false;
	}
	
	public static boolean leaveServer(Context context){

		JsonObject response = AkiHttpUtils.doPOSTHttpRequest("/leave");
		if ( response != null ){
			String responseCode = response.get("code").asString();
			if ( responseCode.equals("ok") ){
				activeOnServer = false;
				CharSequence toastText = "You have just logged out.";
				Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
				toast.show();
				return true;
			}
		}
		CharSequence toastText = "You could not log out.";
		Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
		toast.show();
		return false;
	}
	
	public static void enterChatRoom(Context context) {

		JsonObject response = AkiHttpUtils.doPOSTHttpRequest("/chat");
		if ( response != null ){
			String responseCode = response.get("code").asString();
			if ( responseCode.equals("ok") ){
				String newChatRoom = response.get("chat_room").asString();
				try {
					String currentChatRoom = getCurrentChatRoom(context);
					if ( currentChatRoom.equals(newChatRoom) ){
						Log.i(AkiApplication.TAG, "No need to update current chat room, which" +
								" has address {" + currentChatRoom + "}.");
						return;
					}
					else{
						leaveChatRoom(context);
						Log.e(AkiApplication.TAG, "Had to leave current chat room address {" +
								currentChatRoom + "} because will be assigned to new chat room " +
								"address {" + newChatRoom + "}.");
					}
				} catch (FileNotFoundException e) {
					Log.i(AkiApplication.TAG, "No current chat room address set.");
				} catch (IOException e) {
					e.printStackTrace();
					leaveChatRoom(context);
					unsetCurrentChatRoom(context);
					Log.e(AkiApplication.TAG, "IO error while attempting to figure out current chat room address. " + 
							"Performed cleanup operations.");
				}
				if ( !PushService.getSubscriptions(context).contains(newChatRoom) ){
		    		PushService.subscribe(context, newChatRoom, AkiMain.class);
					Log.i(AkiApplication.TAG, "Subscribed to chat room address {" + newChatRoom + "}.");
				}
				setCurrentChatRoom(context, newChatRoom);
				Log.i(AkiApplication.TAG, "Current chat room set to chat room address {" + newChatRoom + "}.");
			}
		}
	}
	
	public static void leaveChatRoom(Context context) {

		try {

			String currentChatRoom = getCurrentChatRoom(context);
			PushService.unsubscribe(context, currentChatRoom);
			Log.i(AkiApplication.TAG, "Unsubscribed from chat room address {" + currentChatRoom + "}.");
		} catch ( FileNotFoundException e ){ 
			Log.i(AkiApplication.TAG, "No need to unsubscribe as no current chat room address is set.");
		} catch ( IOException e ){
			e.printStackTrace();
			Log.e(AkiApplication.TAG, "Could NOT figure out current chat room address.");
			unsetCurrentChatRoom(context);
			Log.e(AkiApplication.TAG, "Cleanup -> removed current chat room cache file.");
		}
		for ( String remainingChatRoom : PushService.getSubscriptions(context) ){
			PushService.unsubscribe(context, remainingChatRoom);
			Log.e(AkiApplication.TAG, "Cleanup -> unsubscribing from chat room address: {" + remainingChatRoom + "}.");
		}
	}
	
	private static String getCurrentChatRoom(Context context) throws FileNotFoundException, IOException{

		FileInputStream fis = context.openFileInput("current-chat-room");
		StringBuilder currentChatRoom = new StringBuilder();
		int content;
		while ( (content=fis.read()) != -1 ){
			currentChatRoom.append((char) content);
		}
		fis.close();
		return currentChatRoom.toString();
	}
	
	private static void setCurrentChatRoom(Context context, String newChatRoom) {
		
		try {
			FileOutputStream fos = context.openFileOutput("current-chat-room", Context.MODE_PRIVATE);
			fos.write(newChatRoom.getBytes());
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void unsetCurrentChatRoom(Context context) {
		
		File file = new File(context.getFilesDir(), "current-chat-room");
		file.delete();
	}
	
}