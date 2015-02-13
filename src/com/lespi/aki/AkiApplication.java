package com.lespi.aki;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import com.parse.Parse;
import com.parse.ParseInstallation;
import com.parse.PushService;


public class AkiApplication extends Application {

	public final static String TAG = "__AkiApplication__";
	public final static boolean DEBUG_MODE = false;

	public static boolean IN_BACKGROUND = true;
	public static boolean IN_SETTINGS = false;
	public static boolean LOGGED_IN = false;
	public static boolean SERVER_DOWN = false;
	
	public static int INCOMING_MESSAGES_COUNTER = 0;
	public static final int INCOMING_MESSAGE_NOTIFICATION_ID = 1011;
	public static final int EXITED_ROOM_NOTIFICATION_ID = 1012;
	public static final int NEW_MATCH_NOTIFICATION_ID = 1013;
	public static final int INCOMING_PRIVATE_MESSAGE_NOTIFICATION_ID = 1014;
	
	public static final String SYSTEM_SENDER_ID = "System";
	public static final String SYSTEM_EMPTY_ID = "Empty";
	
	public static final float MIN_RADIUS = 0.03f; // In kilometers
	
    public final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    public static final int UPDATE_INTERVAL_IN_SECONDS = 45;
    public static final int FAST_CEILING_IN_SECONDS = 30;
    public static final long UPDATE_INTERVAL_IN_MILLISECONDS =
            1000 * UPDATE_INTERVAL_IN_SECONDS;
    public static final long FAST_INTERVAL_CEILING_IN_MILLISECONDS =
            1000 * FAST_CEILING_IN_SECONDS;
    
    public static String CURRENT_PRIVATE_ID = null;
    
	public static CookieManager cookieManager;
	
	static {
		disableConnectionReuseIfNecessary();
		enableCookieManagement();
	}
	
	private static void disableConnectionReuseIfNecessary(){
	    // Http connection reuse which was buggy pre-froyo
	    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
	        System.setProperty("http.keepAlive", "false");
	    }
	}

	private static void enableCookieManagement(){
		cookieManager = new CookieManager();
		CookieHandler.setDefault(cookieManager);
		cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
	}
	
	@Override
	public void onCreate() {
		super.onCreate();

		Parse.initialize(this, "62IWkJFCtqyCkyRajF25oAK8ocitulaV2jjpCzZY",
				"AmKpgc2r0KHmochB1sEsW0IbELp7cC32HKZxDPAF");
		PushService.setDefaultPushCallback(this, AkiMainActivity.class);
		Log.i(TAG, "Parse Push notification service initialized");

		ParseInstallation.getCurrentInstallation().saveInBackground();
	}

	public static void isNowInForeground() {
		IN_BACKGROUND = false;
		INCOMING_MESSAGES_COUNTER = 0;
		Log.wtf(AkiApplication.TAG, "IN FOREGROUND NOW!!"); //TODO remove this
	}
	
	public static void isNowInBackground() {
		IN_BACKGROUND = true;
		Log.wtf(AkiApplication.TAG, "IN BACKGROUND NOW!!"); //TODO remove this
	}

	public static void isShowingSettingsMenu() {
		IN_SETTINGS = true;
		Log.wtf(AkiApplication.TAG, "IS SHOWING SETTINGS NOW!!"); //TODO remove this
	}
	
	public static void isNotShowingSettingsMenu() {
		IN_SETTINGS = false;
		Log.wtf(AkiApplication.TAG, "IS NO LONGER SHOWING SETTINGS!!"); //TODO remove this
	}
	
	public static void isLoggedIn() {
		LOGGED_IN = true;
		Log.wtf(AkiApplication.TAG, "IS LOGGED IN!!"); //TODO remove this
	}

	public static void isNotLoggedIn() {
		LOGGED_IN = false;
		Log.wtf(AkiApplication.TAG, "IS NOT LOGGED IN!!"); //TODO remove this
	}
	
	public static void serverDown(){
		SERVER_DOWN = true;
		Log.wtf(AkiApplication.TAG, "SERVER IS DOWN!!"); //TODO remove this
	}
	
	public static void serverNotDown(){
		SERVER_DOWN = false;
		Log.wtf(AkiApplication.TAG, "SERVER IS NOT DOWN!!"); //TODO remove this
	}

	public static boolean isPrivateChatShowing(String userId){
		return userId.equals(CURRENT_PRIVATE_ID);
	}

	public static synchronized void setCurrentPrivateId(String userId){
		CURRENT_PRIVATE_ID = userId;
		if ( userId == null ){
			Log.wtf(AkiApplication.TAG, "IS NO LONGER SHOWING PRIVATE CHAT ROOM!!"); //TODO remove this
		}
		else{
			Log.wtf(AkiApplication.TAG, "IS SHOWING PRIVATE CHAT WITH {" + userId + "}!!"); //TODO remove this
		}
	}
	
}