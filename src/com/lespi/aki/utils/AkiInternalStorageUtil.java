package com.lespi.aki.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.AkiMainActivity;
import com.lespi.aki.R;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.parse.ParsePush;
import com.parse.internal.AsyncCallback;

public class AkiInternalStorageUtil {

	public static String getCurrentChatRoom(Context context){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		return sharedPref.getString(context.getString(R.string.com_lespi_aki_data_current_chat_room), null);
	}

	public static synchronized void setCurrentChatRoom(Context context, String newChatRoom) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_current_chat_room), newChatRoom);
		editor.commit();
	}

	public static String getCurrentUser(Context context){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_persistent_preferences), Context.MODE_PRIVATE);
		return sharedPref.getString(context.getString(R.string.com_lespi_aki_data_current_user), null);
	}

	public static synchronized void setCurrentUser(Context context, String currentUserId) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_persistent_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_current_user), currentUserId);
		editor.commit();
	}

	public static class AkiMessageComparator implements Comparator<JsonObject>, Serializable{
		private static final long serialVersionUID = 333818567341533170L;

		@Override
		public int compare(JsonObject lhs, JsonObject rhs) {
			JsonValue lhsTimestamp = lhs.get("timestamp");
			JsonValue rhsTimestamp = rhs.get("timestamp");
			if ( lhsTimestamp != null && rhsTimestamp != null ){
				if ( compareTimestamps(lhsTimestamp.asString(), rhsTimestamp.asString()) == -1 ){
					return -1;
				}
				else if ( compareTimestamps(lhsTimestamp.asString(), rhsTimestamp.asString()) == 1 ){
					return 1;
				}
				return 0;
			}
			return 0;
		}
	}

	@SuppressWarnings("unchecked")
	public static synchronized PriorityQueue<JsonObject> retrieveMessages(Context context, String chatRoom) {

		PriorityQueue<JsonObject> messages = new PriorityQueue<JsonObject>(11, new AkiMessageComparator());
		if ( chatRoom == null ){
			return messages;
		}
		try {

			ObjectInputStream ois = new ObjectInputStream(context.openFileInput(
					context.getString(R.string.com_lespi_aki_data_chat_messages)+chatRoom));
			messages = (PriorityQueue<JsonObject>) ois.readObject();
			ois.close();
		} catch (FileNotFoundException e) {
			Log.i(AkiApplication.TAG, "There are no messages in chat room " + chatRoom + ".");
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "Could not retrieve the messages in chat room " + chatRoom + ".");
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			Log.e(AkiApplication.TAG, "Could not retrieve the messages in chat room " + chatRoom + ".");
			e.printStackTrace();
		}
		return messages;
	}

	public static synchronized void storePulledMessage(Context context, String chatRoom, String from, String content, String timestamp) {
		storeNewMessage(context, chatRoom, from, content, timestamp, false, false);
	}

	public static synchronized JsonObject storeTemporaryMessage(Context context, String chatRoom, String from, String content, String timestamp) {
		return storeNewMessage(context, chatRoom, from, content, timestamp, true, false);
	}

	public static synchronized void storePushedMessage(Context context, String chatRoom, String from, String content, String timestamp) {
		storeNewMessage(context, chatRoom, from, content, timestamp, false, true);
	}

	public static synchronized void storeSystemMessage(Context context, String chatRoom, String content) {
		String timestamp = getVeryNextTimestamp(getMostRecentTimestamp(context));
		storeNewMessage(context, chatRoom, AkiApplication.SYSTEM_SENDER_ID, content, timestamp, false, false);
	}

	private static synchronized JsonObject storeNewMessage(Context context, String chatRoom, String from,
			String content, String timestamp, boolean temporary, boolean fromPush) {

		try {

			Set<String> timestamps = retrieveTimestamps(context, chatRoom);
			if ( timestamps.contains(timestamp) ){
				return null;
			}
			timestamps.add(timestamp);
			ObjectOutputStream oos = new ObjectOutputStream(context.openFileOutput(
					context.getString(R.string.com_lespi_aki_data_chat_timestamps)+chatRoom, Context.MODE_PRIVATE));
			oos.writeObject(timestamps);
			oos.close();

			PriorityQueue<JsonObject> messages = retrieveMessages(context, chatRoom);

			JsonObject newMessage = new JsonObject();
			newMessage.add("sender", from);
			newMessage.add("message", content);
			newMessage.add("timestamp", timestamp);
			newMessage.add("is_temporary", Boolean.toString(temporary));

			if ( !fromPush && !temporary && compareTimestamps(timestamp, getMostRecentTimestamp(context)) == 1 ){
				setMostRecentTimestamp(context, timestamp);
			}

			messages.add(newMessage);

			oos = new ObjectOutputStream(context.openFileOutput(
					context.getString(R.string.com_lespi_aki_data_chat_messages)+chatRoom, Context.MODE_PRIVATE));
			oos.writeObject(messages);
			oos.close();
			return newMessage;
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "Message received from " + from + " could not be stored.");
			e.printStackTrace();
			return null;
		}
	}

	public static synchronized void removeCachedMessages(Context context, String chatRoom) {

		File file = new File(context.getFilesDir(), context.getString(R.string.com_lespi_aki_data_chat_messages)+chatRoom);
		file.delete();
	}

	public static synchronized void removeTemporaryMessage(Context context, String chatRoom, JsonObject temporaryMessage) {

		try {

			PriorityQueue<JsonObject> messages = retrieveMessages(context, chatRoom);
			messages.remove(temporaryMessage);

			ObjectOutputStream oos = new ObjectOutputStream(context.openFileOutput(
					context.getString(R.string.com_lespi_aki_data_chat_messages)+chatRoom, Context.MODE_PRIVATE));
			oos.writeObject(messages);
			oos.close();
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "Received confirmation that message of temporary timestamp " + temporaryMessage + " was received by the Server.");
			e.printStackTrace();
		}
	}

	public static synchronized void clearTemporaryMessages(Context context, String chatRoom) {

		try {
			PriorityQueue<JsonObject> messages = retrieveMessages(context, chatRoom);
			for ( JsonObject temporaryMessage : messages ){
				if ( Boolean.valueOf(temporaryMessage.get("is_temporary").asString()) ){
					messages.remove(temporaryMessage);
				}
			}
			ObjectOutputStream oos = new ObjectOutputStream(context.openFileOutput(
					context.getString(R.string.com_lespi_aki_data_chat_messages)+chatRoom, Context.MODE_PRIVATE));
			oos.writeObject(messages);
			oos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unchecked")
	public static synchronized Set<String> retrieveTimestamps(Context context, String chatRoom) {

		Set<String> timestamps = new HashSet<String>();
		if ( chatRoom == null ){
			return timestamps;
		}
		try {

			ObjectInputStream ois = new ObjectInputStream(context.openFileInput(
					context.getString(R.string.com_lespi_aki_data_chat_timestamps)+chatRoom));
			timestamps = (Set<String>) ois.readObject();
			ois.close();
		} catch (FileNotFoundException e) {
			Log.i(AkiApplication.TAG, "There are no timestamps saved for chat room " + chatRoom + ".");
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "Could not retrieve timestamps saved in chat room " + chatRoom + ".");
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			Log.e(AkiApplication.TAG, "Could not retrieve timestamps saved in chat room " + chatRoom + ".");
			e.printStackTrace();			
		}
		return timestamps;
	}
	
	public static synchronized void clearTimestamps(Context context, String chatRoom) {
		
		File file = new File(context.getFilesDir(), context.getString(R.string.com_lespi_aki_data_chat_timestamps)+chatRoom);
		file.delete();
	}

	public static String getMostRecentTimestamp(Context context){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		return sharedPref.getString(context.getString(R.string.com_lespi_aki_data_most_recent_timestamp), BigInteger.ZERO.toString());
	}

	public static synchronized void setMostRecentTimestamp(Context context, String mostRecentTimestamp) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_most_recent_timestamp), mostRecentTimestamp);
		editor.commit();
	}

	public static synchronized String getLastServerTimestamp(Context context){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		return sharedPref.getString(context.getString(R.string.com_lespi_aki_data_last_server_timestamp), BigInteger.ZERO.toString());
	}

	public static synchronized void setLastServerTimestamp(Context context, String lastServerTimestamp) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_last_server_timestamp), lastServerTimestamp);
		editor.commit();
	}
	public static synchronized String getLastServerTimestamp(Context context,String chatRoom){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		return sharedPref.getString(context.getString(R.string.com_lespi_aki_data_last_server_timestamp)+chatRoom, BigInteger.ZERO.toString());
	}

	public static synchronized void setLastServerTimestamp(Context context, String lastServerTimestamp,String chatRoom) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_last_server_timestamp)+chatRoom, lastServerTimestamp);
		editor.commit();
	}

	public static int compareTimestamps(String lhsTimestamp, String rhsTimestamp){
		BigInteger lhs = new BigInteger(lhsTimestamp);
		BigInteger rhs = new BigInteger(rhsTimestamp);
		return lhs.compareTo(rhs);
	}

	public static String getVeryNextTimestamp(String timestamp){
		BigInteger val = new BigInteger(timestamp);
		val = val.add(BigInteger.ONE);
		return val.toString();
	}

	protected static class AkiBitmapDataObject implements Serializable {
		private static final long serialVersionUID = 222707456230422059L;
		public byte[] imageByteArray;
	}

	public static String getCachedUserNickname(Context context, String userId){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_persistent_preferences), Context.MODE_PRIVATE);
		return sharedPref.getString(context.getString(R.string.com_lespi_aki_data_user_nickname)+userId, null);
	}

	public static synchronized void cacheUserNickname(Context context, String userId, String nickname) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_persistent_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_user_nickname)+userId, nickname);
		editor.commit();
	}

	public static synchronized void unsetCachedUserNickname(Context context, String userId, String nickname) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_persistent_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_user_nickname)+userId, null);
		editor.commit();
	}

	public static boolean cacheHasLikedUser(Context context, String userId){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_data_current_likes), Context.MODE_PRIVATE);
		return sharedPref.getBoolean(context.getString(R.string.com_lespi_aki_data_user_liked)+userId, false);
	}

	public static synchronized void cacheLikeUser(Context context, String userId) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_data_current_likes), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putBoolean(context.getString(R.string.com_lespi_aki_data_user_liked)+userId, true);
		editor.commit();
	}

	public static synchronized void cacheDislikeUser(Context context, String userId) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_data_current_likes), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putBoolean(context.getString(R.string.com_lespi_aki_data_user_liked)+userId, false);
		editor.commit();
	}

	public static synchronized void clearUserLikes(Context context) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_data_current_likes), Context.MODE_PRIVATE);
		sharedPref.edit().clear().commit();
	}

	public static void cacheLikeMutualInterests(Context context) {

		Set<String> matches = retrieveMatches(context);
//		Set<String> currentChatMembers = getCurrentChatMembers(context); // No need to only cache like users currently in the chat room
		for ( String userId : matches ){
//			if ( currentChatMembers.contains(userId) ){
				AkiServerUtil.sendLikeToServer(context, userId);
				cacheLikeUser(context, userId);
//			}
		}
	}

	public static Bitmap getCachedUserPicture(Context context, String userId){

		Bitmap picture = null;
		try{
			ObjectInputStream ois = new ObjectInputStream(context.openFileInput(
					context.getString(R.string.com_lespi_aki_data_user_picture)+userId));

			AkiBitmapDataObject bitmapDataObject = (AkiBitmapDataObject) ois.readObject();
			picture = BitmapFactory.decodeByteArray(bitmapDataObject.imageByteArray,
					0, bitmapDataObject.imageByteArray.length);

			ois.close();
		}
		catch (FileNotFoundException e){
			Log.i(AkiApplication.TAG, "There is no cached picture for this user "+userId+".");
		}
		catch (ClassNotFoundException e){
			Log.e(AkiApplication.TAG, "A problem happened while trying to retrieve" +
					" a cached picture for this user "+userId+".");
			e.printStackTrace();
		}
		catch (IOException e){
			Log.e(AkiApplication.TAG, "A problem happened while trying to retrieve" +
					" a cached picture for this user "+userId+".");
			e.printStackTrace();
		}
		return picture;
	}

	public static synchronized void cacheUserPicture(Context context, String userId, Bitmap picture){

		if ( picture == null ){
			Log.e(AkiApplication.TAG, "Cannot cache a null picture for this user "+userId+".");			
			return;
		}

		try {
			ObjectOutputStream oos = new ObjectOutputStream(context.openFileOutput(
					context.getString(R.string.com_lespi_aki_data_user_picture)+userId, Context.MODE_PRIVATE));

			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			picture.compress(Bitmap.CompressFormat.PNG, 100, stream);
			AkiBitmapDataObject bitmapDataObject = new AkiBitmapDataObject();     
			bitmapDataObject.imageByteArray = stream.toByteArray();
			oos.writeObject(bitmapDataObject);

			oos.close();
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "A problem happened while trying to cache" +
					" a picture for this user "+userId+".");
			e.printStackTrace();
		}
	}

	public static String getCachedUserFirstName(Context context, String userId) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_persistent_preferences), Context.MODE_PRIVATE);
		String firstName = sharedPref.getString(context.getString(R.string.com_lespi_aki_data_user_firstname)+userId, null);
		return firstName;
	}

	public static synchronized void cacheUserFirstName(Context context, String userId, String firstName) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_persistent_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_user_firstname)+userId, firstName);
		editor.commit();
	}

	public static String getCachedUserFullName(Context context, String userId) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_persistent_preferences), Context.MODE_PRIVATE);
		String fullName = sharedPref.getString(context.getString(R.string.com_lespi_aki_data_user_fullname)+userId, null);
		return fullName;
	}

	public static synchronized void cacheUserFullName(Context context, String userId, String fullName) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_persistent_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_user_fullname)+userId, fullName);
		editor.commit();
	}

	public static String getCachedUserGender(Context context, String userId) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_persistent_preferences), Context.MODE_PRIVATE);
		String fullName = sharedPref.getString(context.getString(R.string.com_lespi_aki_data_user_gender)+userId, "unknown");
		return fullName;
	}

	public static synchronized void cacheUserGender(Context context, String userId, String gender) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_persistent_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_user_gender)+userId, gender);
		editor.commit();
	}

	public static Bitmap getCachedUserCoverPhoto(Context context, String userId) {

		Bitmap picture = null;
		try{
			ObjectInputStream ois = new ObjectInputStream(context.openFileInput(context.getString(R.string.com_lespi_aki_data_user_coverphoto)+userId));

			AkiBitmapDataObject bitmapDataObject = (AkiBitmapDataObject) ois.readObject();
			picture = BitmapFactory.decodeByteArray(bitmapDataObject.imageByteArray,
					0, bitmapDataObject.imageByteArray.length);

			ois.close();
		}
		catch (FileNotFoundException e){
			Log.i(AkiApplication.TAG, "There is no cached cover photo for this user "+userId+".");
		}
		catch (ClassNotFoundException e){
			Log.e(AkiApplication.TAG, "A problem happened while trying to retrieve" +
					" a cached cover photo for this user "+userId+".");
			e.printStackTrace();
		}
		catch (IOException e){
			Log.e(AkiApplication.TAG, "A problem happened while trying to retrieve" +
					" a cached cover photo for this user "+userId+".");
			e.printStackTrace();
		}
		return picture;
	}

	public static synchronized void cacheUserCoverPhoto(Context context, final String userId, Bitmap picture) {

		if ( picture == null ){
			Log.e(AkiApplication.TAG, "Cannot cache a null cover photo for this user "+userId+".");			
			return;
		}

		try {
			ObjectOutputStream oos = new ObjectOutputStream(context.openFileOutput(
					context.getString(R.string.com_lespi_aki_data_user_coverphoto)+userId, Context.MODE_PRIVATE));

			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			picture.compress(Bitmap.CompressFormat.PNG, 100, stream);
			AkiBitmapDataObject bitmapDataObject = new AkiBitmapDataObject();     
			bitmapDataObject.imageByteArray = stream.toByteArray();
			oos.writeObject(bitmapDataObject);

			oos.close();
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "A problem happened while trying to cache" +
					" a cover photo for this user "+userId+".");
			e.printStackTrace();
		}
	}

	public static boolean isMandatorySettingMissing(Context context){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_persistent_preferences), Context.MODE_PRIVATE);
		return sharedPref.getBoolean(context.getString(R.string.com_lespi_aki_data_mandatory_setting_missing), true);
	}

	public static synchronized void aMandatorySettingIsMissing(Context context, boolean missing){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_persistent_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putBoolean(context.getString(R.string.com_lespi_aki_data_mandatory_setting_missing), missing);
		editor.commit();
	}

	public static boolean getAnonymousSetting(Context context, String userId) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		return sharedPref.getBoolean(context.getString(R.string.com_lespi_aki_data_anonymous_setting)+userId, true);
	}

	public static synchronized void setAnonymousSetting(Context context, String userId, boolean checked) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putBoolean(context.getString(R.string.com_lespi_aki_data_anonymous_setting)+userId, checked);
		editor.commit();
	}

	public static boolean getAloneSetting(Context context) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		return sharedPref.getBoolean(context.getString(R.string.com_lespi_aki_data_alone_setting), false);
	}

	public static synchronized void setAloneSetting(Context context, boolean alone) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putBoolean(context.getString(R.string.com_lespi_aki_data_alone_setting), alone);
		editor.commit();
	}

	public static boolean getChatModeGlobalEnabled(Context context) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		return sharedPref.getBoolean(context.getString(R.string.com_lespi_aki_data_global_setting), false);
	}

	public static void setChatModeGlobalEnabled(Context context, boolean globalEnabled) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putBoolean(context.getString(R.string.com_lespi_aki_data_global_setting), globalEnabled);
		editor.commit();
	}
	
	public static class AkiLocation implements Serializable {
		private static final long serialVersionUID = 222707456230422059L;
		public double latitude;
		public double longitude;

		public AkiLocation(double latitude, double longitude){
			this.latitude = latitude;
			this.longitude = longitude;
		}
	}

	public static AkiLocation getCachedUserLocation(Context context, String userId) {

		AkiLocation location = null;
		try{
			ObjectInputStream ois = new ObjectInputStream(context.openFileInput(context.getString(R.string.com_lespi_aki_data_user_location)+userId));

			location = (AkiLocation) ois.readObject();
			ois.close();
		}
		catch (FileNotFoundException e){
			Log.i(AkiApplication.TAG, "There is no cached location for this user "+userId+".");
		}
		catch (ClassNotFoundException e){
			Log.e(AkiApplication.TAG, "A problem happened while trying to retrieve" +
					" the cached location of this user "+userId+".");
			e.printStackTrace();
		}
		catch (IOException e){
			Log.e(AkiApplication.TAG, "A problem happened while trying to retrieve" +
					" a cached location of this user "+userId+".");
			e.printStackTrace();
		}
		return location;
	}

	public static synchronized void cacheUserLocation(Context context, String userId, Location location) {

		cacheUserLocation(context, userId, new AkiLocation(location.getLatitude(), location.getLongitude()));
	}

	public static synchronized void cacheUserLocation(Context context, String userId, AkiLocation location) {

		try {
			ObjectOutputStream oos = new ObjectOutputStream(context.openFileOutput(
					context.getString(R.string.com_lespi_aki_data_user_location)+userId, Context.MODE_PRIVATE));
			oos.writeObject(location);
			oos.close();
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "A problem happened while trying to cache" +
					" the location of this user "+userId+".");
			e.printStackTrace();
		}
	}

	public static synchronized void wipeCachedUserLocation(Context context, AsyncCallback callback) {

		String userId = null;
		try{

			userId = getCurrentUser(context);
			if ( userId == null ){
				callback.onSuccess(null);
				return;
			}
			File file = new File(context.getFilesDir(), R.string.com_lespi_aki_data_user_location+userId);
			file.delete();
		}
		catch (Exception e){
			callback.onFailure(e);
		}
	}

	public static synchronized void wipeCachedUserLocation(Context context, String userId) {
		File file = new File(context.getFilesDir(), R.string.com_lespi_aki_data_user_location+userId);
		file.delete();
	}
	
	public static AkiLocation getCachedGeofenceCenter(Context context) {

		AkiLocation center = null;
		try{
			ObjectInputStream ois = new ObjectInputStream(context.openFileInput(context.getString(R.string.com_lespi_aki_data_geofence_center)));

			center = (AkiLocation) ois.readObject();
			ois.close();
		}
		catch (FileNotFoundException e){
			Log.i(AkiApplication.TAG, "There is no cached geofence center.");
		}
		catch (ClassNotFoundException e){
			Log.e(AkiApplication.TAG, "A problem happened while trying to retrieve" +
					" the cached geofence center.");
			e.printStackTrace();
		}
		catch (IOException e){
			Log.e(AkiApplication.TAG, "A problem happened while trying to retrieve" +
					" the cached geofence center.");
			e.printStackTrace();
		}
		return center;
	}

	public static synchronized void cacheGeofenceCenter(Context context, AkiLocation center) {

		try {
			ObjectOutputStream oos = new ObjectOutputStream(context.openFileOutput(
					context.getString(R.string.com_lespi_aki_data_geofence_center), Context.MODE_PRIVATE));
			oos.writeObject(center);
			oos.close();
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "A problem happened while trying to cache the geofence center.");
			e.printStackTrace();
		}
	}

	public static synchronized void wipeCachedGeofenceCenter(Context context) {

		File file = new File(context.getFilesDir(), context.getString(R.string.com_lespi_aki_data_geofence_center));
		file.delete();
	}

	public static float getCachedGeofenceRadius(Context context) {
		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		return sharedPref.getFloat(context.getString(R.string.com_lespi_aki_data_geofence_radius), -1);
	}

	public static synchronized void cacheGeofenceRadius(Context context, float radius) {
		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putFloat(context.getString(R.string.com_lespi_aki_data_geofence_radius), radius);
		editor.commit();
	}

	public static boolean shouldUpdateGeofence(Context context) {
		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		return sharedPref.getBoolean(context.getString(R.string.com_lespi_aki_data_geofence_should_update), false);
	}

	public static synchronized void willUpdateGeofence(Context context) {
		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putBoolean(context.getString(R.string.com_lespi_aki_data_geofence_should_update), true);
		editor.commit();
	}

	public static synchronized void willNotUpdateGeofence(Context context) {
		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putBoolean(context.getString(R.string.com_lespi_aki_data_geofence_should_update), false);
		editor.commit();
	}

	public static int getNextTimeout(Context context ,String chatRoom) {
		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		int timeout = sharedPref.getInt(context.getString(R.string.com_lespi_aki_data_last_timeout)+chatRoom, 1);
		SharedPreferences.Editor editor = sharedPref.edit();
		int nextTimeout = timeout * 2;
		if ( nextTimeout > 45 ){
			nextTimeout = 45;
		}
		editor.putInt(context.getString(R.string.com_lespi_aki_data_last_timeout)+chatRoom, nextTimeout);
		editor.commit();
		return timeout;
	}

	public static synchronized void resetTimeout(Context context,String chatRoom) {
		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putInt(context.getString(R.string.com_lespi_aki_data_last_timeout)+chatRoom, 1);
		editor.commit();
	}
	
	public static int getNextTimeout(Context context) {
		return getNextTimeout(context,"");		
	}
	public static synchronized void resetTimeout(Context context) {
		resetTimeout(context,"");
	}

	@SuppressWarnings("unchecked")
	public static synchronized Set<String> retrieveMatches(Context context) {

		Set<String> matches = new HashSet<String>();
		try {

			ObjectInputStream ois = new ObjectInputStream(context.openFileInput(
					context.getString(R.string.com_lespi_aki_data_matches)));
			matches = (Set<String>) ois.readObject();
			ois.close();
		} catch (FileNotFoundException e) {
			Log.i(AkiApplication.TAG, "There are no saved matches.");
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "Could not retrieve saved matches.");
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			Log.e(AkiApplication.TAG, "Could not retrieve saved matches.");
			e.printStackTrace();			
		}
		return matches;
	}

	public static synchronized void storeNewMatch(Context context, String userId, boolean notify) {

		try {

			Set<String> matches = retrieveMatches(context);
			if ( matches.contains(userId) ){
				return;
			}

			matches.add(userId);
			String chatRoomId = AkiServerUtil.buildPrivateChatId(context, userId);
			ParsePush.subscribeInBackground(chatRoomId);
			ObjectOutputStream oos = new ObjectOutputStream(context.openFileOutput(
					context.getString(R.string.com_lespi_aki_data_matches), Context.MODE_PRIVATE));
			oos.writeObject(matches);
			oos.close();

			if ( notify ){
				Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

				String contentTicker = context.getString(R.string.com_lespi_aki_notif_new_match_ticker);
				String identifier = AkiInternalStorageUtil.getCachedUserFullName(context, userId);
				if ( identifier == null || AkiInternalStorageUtil.viewGetPrivateChatRoomAnonymousSetting(context, chatRoomId, userId) ){
					identifier = AkiInternalStorageUtil.getCachedUserNickname(context, userId);
					if ( identifier == null){
						identifier = userId;
					}
				}
				String contentTitle = context.getString(R.string.com_lespi_aki_notif_new_match_title) + " " + identifier;
				String contentText = identifier + " " + context.getString(R.string.com_lespi_aki_notif_new_match_text);

				Notification.Builder notifyBuilder = new Notification.Builder(context)
					.setSmallIcon(R.drawable.notification_icon)
					.setContentTitle(contentTitle)
					.setContentText(contentText)
					.setTicker(contentTicker)
					.setSubText(contentTicker)
					.setSound(alarmSound)
					.setOnlyAlertOnce(true)
					.setAutoCancel(true);
				Notification.InboxStyle notifyBigBuilder = new Notification.InboxStyle(notifyBuilder);
				String[] contentLines = contentText.split("\n");
				for ( int i=0; i<contentLines.length; i++ ){
					notifyBigBuilder.addLine(contentLines[i]);
				}
				notifyBigBuilder.setBigContentTitle(contentTitle);
				Intent intent = new Intent();
				intent.setClass(context, AkiMainActivity.class);
				intent.setFlags(Intent.FLAG_FROM_BACKGROUND | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
				PendingIntent pending = PendingIntent.getActivity(context, 0, intent, 0);
				notifyBuilder.setContentIntent(pending);
				
				NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
				notificationManager.notify(AkiApplication.NEW_MATCH_NOTIFICATION_ID, notifyBigBuilder.build());
			}

		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "Could not store new match with user: " + userId + ".");
			e.printStackTrace();
		}
	}

	public static boolean hasMatch(Context context, String userId) {

		Set<String> matches = retrieveMatches(context);
		if ( matches.contains(userId) ){
			return true;
		}
		return false;
	}

	public static synchronized void removeMatch(Context context, String userId) {

		try {

			Set<String> matches = retrieveMatches(context);
			if ( matches.contains(userId) ){
				matches.remove(userId);
				ParsePush.unsubscribeInBackground(AkiServerUtil.buildPrivateChatId(context, userId));
			}

			ObjectOutputStream oos = new ObjectOutputStream(context.openFileOutput(
					context.getString(R.string.com_lespi_aki_data_matches), Context.MODE_PRIVATE));
			oos.writeObject(matches);
			oos.close();

		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "Could not remove match with user: " + userId + ".");
			e.printStackTrace();
		}
	}

	public static synchronized void wipeMatches(Context context) {

		Set<String> matches = retrieveMatches(context);
		for ( String userId : matches ){
			ParsePush.unsubscribeInBackground(AkiServerUtil.buildPrivateChatId(context, userId));
		}
		
		File file = new File(context.getFilesDir(), context.getString(R.string.com_lespi_aki_data_matches));
		file.delete();
	}

	public static synchronized void clearVolatileStorage(Context context) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		sharedPref.edit().clear().commit();
	}

	@SuppressWarnings("unchecked")
	public static synchronized Set<String> getCurrentChatMembers(Context context) {

		Set<String> memberIds = new HashSet<String>();
		String currentChat = getCurrentChatRoom(context);
		if ( currentChat == null ){
			return memberIds;
		}

		try {

			ObjectInputStream ois = new ObjectInputStream(context.openFileInput(
					context.getString(R.string.com_lespi_aki_data_chat_members) + currentChat));
			memberIds = (Set<String>) ois.readObject();
			ois.close();
		} catch (FileNotFoundException e) {
			Log.i(AkiApplication.TAG, "There are no saved chat memberIds.");
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "Could not retrieve saved chat memberIds.");
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			Log.e(AkiApplication.TAG, "Could not retrieve saved chat memberIds.");
			e.printStackTrace();			
		}
		return memberIds;
	}

	public static void chatMemberHasJoined(Context context, String userId, boolean logMemberArrival) {

		String currentChat = getCurrentChatRoom(context);
		if ( currentChat == null ){
			return;
		}
		
		try {
			Set<String> memberIds = getCurrentChatMembers(context);
			memberIds.add(userId);

			ObjectOutputStream oos = new ObjectOutputStream(context.openFileOutput(
					context.getString(R.string.com_lespi_aki_data_chat_members) + currentChat, Context.MODE_PRIVATE));
			oos.writeObject(memberIds);
			oos.close();

			if ( logMemberArrival ){
				String currentUserId = getCurrentUser(context);
				String identifier = getCachedUserFullName(context, userId);
				if ( getAnonymousSetting(context, currentUserId) || getAnonymousSetting(context, userId) || identifier == null ){
					identifier = getCachedUserNickname(context, userId);
					if ( identifier == null ){
						identifier = userId;
					}
				}
				
				String currentChatRoom = getCurrentChatRoom(context);
				storeSystemMessage(context, currentChatRoom,
						identifier + " " + context.getString(R.string.com_lespi_aki_message_system_user_arrived_to_chat_room));
			}

		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "Could not add chat member of id: " + userId + ".");
			e.printStackTrace();
		}
	}

	public static synchronized void chatMemberHasLeft(Context context, String userId) {

		String currentChat = getCurrentChatRoom(context);
		if ( currentChat == null ){
			return;
		}
		
		try {
			Set<String> memberIds = getCurrentChatMembers(context);
			if ( memberIds.contains(userId) ){
				memberIds.remove(userId);
			}

			ObjectOutputStream oos = new ObjectOutputStream(context.openFileOutput(
					context.getString(R.string.com_lespi_aki_data_chat_members) + currentChat, Context.MODE_PRIVATE));
			oos.writeObject(memberIds);
			oos.close();

			String currentUserId = getCurrentUser(context);
			String identifier = getCachedUserFullName(context, userId);
			if ( getAnonymousSetting(context, currentUserId) || getAnonymousSetting(context, userId) || identifier == null ){
				identifier = getCachedUserNickname(context, userId);
				if ( identifier == null ){
					identifier = userId;
				}
			}
			
			String currentChatRoom = getCurrentChatRoom(context);
			storeSystemMessage(context, currentChatRoom,
					identifier + " " + context.getString(R.string.com_lespi_aki_message_system_user_left_chat_room));

		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "Could not remove chat member of id: " + userId + ".");
			e.printStackTrace();
		}
	}

	public static synchronized void wipeCurrentChatMembers(Context context) {

		String currentChat = getCurrentChatRoom(context);
		if ( currentChat == null ){
			return;
		}
		
		File file = new File(context.getFilesDir(), context.getString(R.string.com_lespi_aki_data_chat_members) + currentChat);
		file.delete();
	}
	
	public static int getPrivateChatRoomUnreadCounter(Context context, String chatRoom){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		return sharedPref.getInt(context.getString(R.string.com_lespi_aki_data_private_chat_room_unread_counter) + chatRoom, 0);
	}

	public static synchronized void setPrivateChatRoomUnreadCounter(Context context, String chatRoom, int counter) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putInt(context.getString(R.string.com_lespi_aki_data_private_chat_room_unread_counter) + chatRoom, counter);
		editor.commit();
	}
	
	public static boolean viewGetPrivateChatRoomAnonymousSetting(Context context, String chatRoom, String userId){
		Boolean bool = getPrivateChatRoomAnonymousSetting(context, chatRoom, userId);
		if ( bool != null ){
			return Boolean.valueOf(bool);
		}
		return true;
	}
	
	public static Boolean getPrivateChatRoomAnonymousSetting(Context context, String chatRoom, String userId){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		String bool = sharedPref.getString(context.getString(R.string.com_lespi_aki_data_private_chat_room_anonymous_setting) + chatRoom + "_" + userId, "null");
		if ( bool.equals("true") || bool.equals("false") ){
			return Boolean.valueOf(bool);
		}
		return null;
	}
	
	public static synchronized void setPrivateChatRoomAnonymousSetting(Context context, String chatRoom, String userId, boolean anonymous){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_volatile_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_private_chat_room_anonymous_setting) + chatRoom + "_" + userId, Boolean.toString(anonymous));
		editor.commit();
	}

	public static String getLastPrivateMessageSender(Context context){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_persistent_preferences), Context.MODE_PRIVATE);
		return sharedPref.getString(context.getString(R.string.com_lespi_aki_data_private_chat_room_last_seen), null);
	}
	
	public static void setLastPrivateMessageSender(Context context, String userId) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_persistent_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_private_chat_room_last_seen), userId);
		editor.commit();
	}
}