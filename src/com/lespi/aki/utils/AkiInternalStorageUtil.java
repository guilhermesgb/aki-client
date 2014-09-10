package com.lespi.aki.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.util.Log;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.R;
import com.lespi.aki.json.JsonArray;
import com.lespi.aki.json.JsonObject;
import com.parse.internal.AsyncCallback;

public class AkiInternalStorageUtil {

	public static String getCurrentChatRoom(Context context){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		return sharedPref.getString(context.getString(R.string.com_lespi_aki_data_current_chat_room), null);
	}

	public static synchronized void setCurrentChatRoom(Context context, String newChatRoom) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_current_chat_room), newChatRoom);
		editor.commit();
	}

	public static String getCurrentUser(Context context){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		return sharedPref.getString(context.getString(R.string.com_lespi_aki_data_current_user), null);
	}
	
	public static synchronized void setCurrentUser(Context context, String currentUserId) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_current_user), currentUserId);
		editor.commit();
	}
	
	public static JsonArray retrieveMessages(Context context, String chatRoom) {

		JsonArray allMessages = new JsonArray();
		if ( chatRoom == null ){
			return allMessages;
		}
		try {

			ObjectInputStream ois = new ObjectInputStream(context.openFileInput(
					context.getString(R.string.com_lespi_aki_data_chat_messages)+chatRoom));
			allMessages = (JsonArray) ois.readObject();
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
		return allMessages;
	}

	public static synchronized void storeNewMessage(Context context, String chatRoom, String from, String content) {

		try {

			JsonArray allMessages = retrieveMessages(context, chatRoom);

			JsonObject newMessage = new JsonObject();
			newMessage.add("sender", from);
			newMessage.add("message", content);

			allMessages.add(newMessage);

			ObjectOutputStream oos = new ObjectOutputStream(context.openFileOutput(
					context.getString(R.string.com_lespi_aki_data_chat_messages)+chatRoom, Context.MODE_PRIVATE));
			oos.writeObject(allMessages);
			oos.close();
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "Message received from " + from + " could not be stored.");
			e.printStackTrace();
		}
	}

	public static synchronized void removeCachedMessages(Context context, String chatRoom) {

		File file = new File(context.getFilesDir(), context.getString(R.string.com_lespi_aki_data_chat_messages)+chatRoom);
		file.delete();
	}

	protected static class AkiBitmapDataObject implements Serializable {
		private static final long serialVersionUID = 222707456230422059L;
		public byte[] imageByteArray;
	}

	public static String getCachedUserNickname(Context context, String userId){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		return sharedPref.getString(context.getString(R.string.com_lespi_aki_data_user_nickname)+userId, null);
	}

	public static synchronized void cacheUserNickname(Context context, String userId, String nickname) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_user_nickname)+userId, nickname);
		editor.commit();
	}

	public static synchronized void unsetCachedUserNickname(Context context, String userId, String nickname) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_user_nickname)+userId, null);
		editor.commit();
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

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		String firstName = sharedPref.getString(context.getString(R.string.com_lespi_aki_data_user_firstname)+userId, null);
		return firstName;
	}

	public static synchronized void cacheUserFirstName(Context context, String userId, String firstName) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_user_firstname)+userId, firstName);
		editor.commit();
	}

	public static String getCachedUserFullName(Context context, String userId) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		String fullName = sharedPref.getString(context.getString(R.string.com_lespi_aki_data_user_fullname)+userId, null);
		return fullName;
	}
	
	public static synchronized void cacheUserFullName(Context context, String userId, String fullName) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_user_fullname)+userId, fullName);
		editor.commit();
	}

	public static String getCachedUserGender(Context context, String userId) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		String fullName = sharedPref.getString(context.getString(R.string.com_lespi_aki_data_user_gender)+userId, "unknown");
		return fullName;
	}
	
	public static synchronized void cacheUserGender(Context context, String userId, String gender) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
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

	public static synchronized void cacheUserCoverPhoto(Context context, String userId, Bitmap picture) {

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
		
		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		return sharedPref.getBoolean(context.getString(R.string.com_lespi_aki_data_mandatory_setting_missing), false);
	}
	
	public static synchronized void aMandatorySettingIsMissing(Context context, boolean missing){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putBoolean(context.getString(R.string.com_lespi_aki_data_mandatory_setting_missing), missing);
		editor.commit();
	}

	public static boolean getAnonymousSetting(Context context, String userId) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		return sharedPref.getBoolean(context.getString(R.string.com_lespi_aki_data_anonymous_setting)+userId, true);
	}
	
	public static synchronized void setAnonymousSetting(Context context, String userId, boolean checked) {
		
		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putBoolean(context.getString(R.string.com_lespi_aki_data_anonymous_setting)+userId, checked);
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
		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		return sharedPref.getFloat(context.getString(R.string.com_lespi_aki_data_geofence_radius), -1);
	}
	
	public static synchronized void cacheGeofenceRadius(Context context, float radius) {
		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putFloat(context.getString(R.string.com_lespi_aki_data_geofence_radius), radius);
		editor.commit();
	}

	public static boolean shouldUpdateGeofence(Context context) {
		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		return sharedPref.getBoolean(context.getString(R.string.com_lespi_aki_data_geofence_should_update), false);
	}

	public static synchronized void willUpdateGeofence(Context context) {
		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putBoolean(context.getString(R.string.com_lespi_aki_data_geofence_should_update), true);
		editor.commit();
	}
	
	public static synchronized void willNotUpdateGeofence(Context context) {
		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putBoolean(context.getString(R.string.com_lespi_aki_data_geofence_should_update), false);
		editor.commit();
	}
}