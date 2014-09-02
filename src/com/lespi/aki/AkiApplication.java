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

	public final static String TAG = "com.lespi.aki";
	public final static boolean DEBUG_MODE = false;

	public static boolean IN_BACKGROUND = true;
	public static boolean IN_SETTINGS = false;
	public static boolean LOGGED_IN = false;
	public static boolean SERVER_DOWN = false;
	
	public static int INCOMING_MESSAGES_COUNTER = 0;
	public static final int INCOMING_MESSAGE_NOTIFICATION_ID = 1011;
	public static final int EXITED_ROOM_NOTIFICATION_ID = 1012;
	
	public static final String SYSTEM_SENDER_ID = "System";
	
	public static final float MIN_RADIUS = 0.05f; // In kilometers
	
    public final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    public static final int UPDATE_INTERVAL_IN_SECONDS = 45;
    public static final int FAST_CEILING_IN_SECONDS = 30;
    public static final long UPDATE_INTERVAL_IN_MILLISECONDS =
            1000 * UPDATE_INTERVAL_IN_SECONDS;
    public static final long FAST_INTERVAL_CEILING_IN_MILLISECONDS =
            1000 * FAST_CEILING_IN_SECONDS;
    
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
	}
	
	public static void isNowInBackground() {
		IN_BACKGROUND = true;
	}

	public static void isShowingSettingsMenu() {
		IN_SETTINGS = true;
	}
	
	public static void isNotShowingSettingsMenu() {
		IN_SETTINGS = false;
	}
	
	public static void isLoggedIn() {
		LOGGED_IN = true;
	}

	public static void isNotLoggedIn() {
		LOGGED_IN = false;
	}
	
	public static void serverDown(){
		SERVER_DOWN = true;
	}
	
	public static void serverNotDown(){
		SERVER_DOWN = false;
	}
}