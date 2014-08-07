package com.lespi.aki.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.json.JsonArray;
import com.lespi.aki.json.JsonObject;

public class AkiInternalStorageUtil {

	public static String getCurrentChatRoom(Context context) throws FileNotFoundException, IOException{

		FileInputStream fis = context.openFileInput("current-chat-room");
		StringBuilder currentChatRoom = new StringBuilder();
		int content;
		while ( (content=fis.read()) != -1 ){
			currentChatRoom.append((char) content);
		}
		fis.close();
		return currentChatRoom.toString();
	}
	
	public static void setCurrentChatRoom(Context context, String newChatRoom) {
		
		try {
			FileOutputStream fos = context.openFileOutput("current-chat-room", Context.MODE_PRIVATE);
			fos.write(newChatRoom.getBytes());
			fos.close();
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "Could not set current chat room address to: " + newChatRoom + ".");
			e.printStackTrace();
		}
	}
	
	public static void unsetCurrentChatRoom(Context context) {
		
		File file = new File(context.getFilesDir(), "current-chat-room");
		file.delete();
	}

	public static JsonArray retrieveMessages(Context context, String chatRoom) {
		
		JsonArray allMessages = new JsonArray();
		try {
			
			ObjectInputStream ois = new ObjectInputStream(context.openFileInput("chat-room_"+chatRoom));
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
			
			ObjectOutputStream oos = new ObjectOutputStream(context.openFileOutput("chat-room_"+chatRoom, Context.MODE_PRIVATE));
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
	
	public static Bitmap getCachedUserPicture(Context context, String userId){

		Bitmap picture = null;
		try{
			ObjectInputStream ois = new ObjectInputStream(context.openFileInput("user-picture_"+userId));
		
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
			ObjectOutputStream oos = new ObjectOutputStream(context.openFileOutput("user-picture_"+userId, Context.MODE_PRIVATE));
			
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

		StringBuilder firstName = new StringBuilder();
		try{
			FileInputStream fis = context.openFileInput("user-firstname_"+userId);
			int content;
			while ( (content=fis.read()) != -1 ){
				firstName.append((char) content);
			}
			fis.close();
			return firstName.toString();
		}
		catch (FileNotFoundException e){
			Log.i(AkiApplication.TAG, "There is no cached name for this user "+userId+".");
			return null;
		}
		catch (IOException e){
			Log.e(AkiApplication.TAG, "A problem happened while trying to cache the first name of user "+userId+".");
			e.printStackTrace();
			return null;
		}
	}

	public static void cacheUserFirstName(Context context, String userId, String firstName) {
		try {
			FileOutputStream fos = context.openFileOutput("user-firstname_"+userId, Context.MODE_PRIVATE);
			fos.write(firstName.getBytes());
			fos.close();
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "Could not cache first name of user: " + userId + ", which is " + firstName + ".");
			e.printStackTrace();
		}
	}

}