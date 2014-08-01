package com.lespi.aki;

import android.content.Context;
import android.widget.Toast;

import com.lespi.aki.http.AkiHttpUtils;
import com.lespi.aki.json.JsonObject;

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
	
	public  static boolean sendPresenceToServer(Context context, String username){

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
	
	public  static boolean leaveServer(Context context){

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
	
}