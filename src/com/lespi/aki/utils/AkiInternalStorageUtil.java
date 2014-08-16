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
import android.util.Log;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.R;
import com.lespi.aki.json.JsonArray;
import com.lespi.aki.json.JsonObject;

public class AkiInternalStorageUtil {

	public static String getCurrentChatRoom(Context context){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		return sharedPref.getString(context.getString(R.string.com_lespi_aki_data_current_chat_room), null);
	}

	public static void setCurrentChatRoom(Context context, String newChatRoom) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_current_chat_room), newChatRoom);
		editor.commit();
	}

	public static void unsetCurrentChatRoom(Context context) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_current_chat_room), null);
		editor.commit();
	}

	public static JsonArray retrieveMessages(Context context, String chatRoom) {

		JsonArray allMessages = new JsonArray();
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

	public static void storeNewMessage(Context context, String chatRoom, String from, String content) {

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

	public static void removeCachedMessages(Context context, String chatRoom) {

		File file = new File(context.getFilesDir(), "chat-room_"+chatRoom);
		file.delete();
	}

	protected static class AkiBitmapDataObject implements Serializable {
		private static final long serialVersionUID = 222707456230422059L;
		public byte[] imageByteArray;
	}

	public static String getCachedNickname(Context context, String userId){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		return sharedPref.getString(context.getString(R.string.com_lespi_aki_data_user_nickname)+userId, null);
	}

	public static void cacheNickname(Context context, String userId, String nickname) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_user_nickname)+userId, nickname);
		editor.commit();
	}

	public static void unsetCachedNickname(Context context, String userId, String nickname) {

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

	public static void cacheUserPicture(Context context, String userId, Bitmap picture){

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

	public static void cacheUserFirstName(Context context, String userId, String firstName) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString(context.getString(R.string.com_lespi_aki_data_user_firstname)+userId, firstName);
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

	public static void cacheUserCoverPhoto(Context context, String userId, Bitmap picture) {

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
	
	public static boolean mandatorySettingsMissing(Context context){
		
		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		return sharedPref.getBoolean(context.getString(R.string.com_lespi_aki_data_mandatory_setting_missing), false);
	}
	
	public static void setMandatorySettingsMissing(Context context, boolean missing){

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putBoolean(context.getString(R.string.com_lespi_aki_data_mandatory_setting_missing), missing);
		editor.commit();
	}

	public static boolean getAnonymousSetting(Context context, String userId) {

		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		return sharedPref.getBoolean(context.getString(R.string.com_lespi_aki_data_anonymous_setting)+userId, true);
	}
	
	public static void setAnonymousSetting(Context context, String userId, boolean checked) {
		
		SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.com_lespi_aki_preferences), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putBoolean(context.getString(R.string.com_lespi_aki_data_anonymous_setting)+userId, checked);
		editor.commit();
	}
}