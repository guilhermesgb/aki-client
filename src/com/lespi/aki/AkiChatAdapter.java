package com.lespi.aki;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.model.GraphUser;
import com.lespi.aki.json.JsonArray;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiInternalStorageUtil.AkiLocation;

public class AkiChatAdapter extends ArrayAdapter<JsonObject> {

	private final Context context;
	private final List<JsonObject> messages;
	private GraphUser currentUser = null;
	private Session currentSession = null;

	private static AkiChatAdapter instance;

	public static List<JsonObject> toJsonObjectList(JsonArray values){

		if ( values == null ){
			return null;
		}
		List<JsonObject> toReturn = new ArrayList<JsonObject>();
		for (JsonValue value : values){
			toReturn.add(value.asObject());
		}
		return toReturn;
	}

	public static AkiChatAdapter getInstance(Context context){
		if ( instance == null ){
			List<JsonObject> messages = new ArrayList<JsonObject>();
			instance = new AkiChatAdapter(context, messages);
		}
		return instance;
	}

	private AkiChatAdapter(Context context, List<JsonObject> messages){
		super(context, R.layout.aki_chat_message_you, messages);
		this.context = context;
		this.messages = messages;
	}

	public void setCurrentUser(GraphUser currentUser){
		this.currentUser = currentUser;
	}

	public void setCurrentSession(Session currentSession) {
		this.currentSession = currentSession;
	}

	public static Bitmap getRoundedBitmap(Bitmap bitmap) {
		Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap
				.getHeight(), Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(output);

		final int color = 0xff424242;
		final Paint paint = new Paint();
		final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
		final RectF rectF = new RectF(rect);

		paint.setAntiAlias(true);
		canvas.drawARGB(0, 0, 0, 0);
		paint.setColor(color);
		canvas.drawOval(rectF, paint);

		paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
		canvas.drawBitmap(bitmap, rect, rect, paint);

		return output;
	}

	static class ViewHolder{
		public TextView senderId;
		public TextView senderName;
		public ImageView senderPicture;
		public TextView message;
		public ImageView senderDistance;
	}

	@SuppressLint("CutPasteId")
	@Override
	public View getView(int position, View convertView, ViewGroup parent){

		final JsonObject newViewData = messages.get(position);

		View rowView = convertView;
		boolean canReuse = false;

		final String senderId = newViewData.get("sender").asString();

		int rowLayout = R.layout.aki_chat_message_you;
		if ( senderId.equals(currentUser.getId()) ){
			rowLayout = R.layout.aki_chat_message_me;
		}
		else if ( senderId.equals(AkiApplication.SYSTEM_SENDER_ID) ){
			rowLayout = R.layout.aki_chat_message_system;
		}

		if ( rowView != null ){

			TextView senderIdView = (TextView) rowView.findViewById(R.id.com_lespi_aki_message_sender_id);

			if ( senderIdView.getText() != null && 
					( senderIdView.getText().equals(AkiApplication.SYSTEM_SENDER_ID) || senderId.equals(AkiApplication.SYSTEM_SENDER_ID) ) ){
				canReuse = false;
			}
			else if ( senderIdView.getText() != null && senderIdView.getText().equals(currentUser.getId()) ){
				if ( senderId.equals(currentUser.getId()) ){
					canReuse = true;
				}
				else{
					rowLayout = R.layout.aki_chat_message_you;
				}
			}
			else if ( senderIdView.getText() != null && !senderIdView.getText().equals(currentUser.getId()) ) {
				if ( !senderId.equals(currentUser.getId()) ){
					canReuse = true;
				}
				else{
					rowLayout = R.layout.aki_chat_message_me;
				}
			}
		}


		if ( !canReuse ){

			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(rowLayout, parent, false);
			ViewHolder viewHolder = new ViewHolder();
			viewHolder.senderId = (TextView) rowView.findViewById(R.id.com_lespi_aki_message_sender_id);
			viewHolder.senderName = (TextView) rowView.findViewById(R.id.com_lespi_aki_message_sender_name);
			viewHolder.senderName.setAlpha(1);
			viewHolder.senderPicture = (ImageView) rowView.findViewById(R.id.com_lespi_aki_message_sender_picture);
			viewHolder.message = (TextView) rowView.findViewById(R.id.com_lespi_aki_message_text_message);
			viewHolder.message.setAlpha(1);
			viewHolder.senderDistance = (ImageView) rowView.findViewById(R.id.com_lespi_aki_message_sender_distance);
			viewHolder.senderDistance.setImageAlpha(255);
			rowView.setTag(viewHolder);
		}

		final ViewHolder viewHolder = (ViewHolder) rowView.getTag();

		viewHolder.senderId.setText(senderId);
		viewHolder.message.setText(newViewData.get("message").asString());

		if ( senderId.equals(AkiApplication.SYSTEM_SENDER_ID) ){
			return rowView;
		}

		if ( senderId.equals(currentUser.getId()) ){

			if ( AkiInternalStorageUtil.getAnonymousSetting(context, senderId) ){
				String nickname = AkiInternalStorageUtil.getCachedNickname(context, senderId);
				if ( nickname != null ){
					viewHolder.senderName.setText(nickname);
				}
				else{
					Log.e(AkiApplication.TAG, "Privacy setting for user " + senderId + " is anonymous but he has no nickname set.");
					viewHolder.senderName.setText(senderId);
				}
			}
			else{
				viewHolder.senderName.setText(currentUser.getFirstName());
			}
			AkiInternalStorageUtil.cacheUserFirstName(context, senderId, currentUser.getFirstName());
			AkiInternalStorageUtil.cacheUserFirstName(context, currentUser.getId(), currentUser.getName());
		}
		else{

			String firstName = AkiInternalStorageUtil.getCachedUserFirstName(context, senderId);
			if ( firstName != null ){
				if ( AkiInternalStorageUtil.getAnonymousSetting(context, senderId)
						|| AkiInternalStorageUtil.getAnonymousSetting(context, currentUser.getId()) ){

					String nickname = AkiInternalStorageUtil.getCachedNickname(context, senderId);
					if ( nickname != null ){
						viewHolder.senderName.setText(nickname);
					}
					else{
						Log.e(AkiApplication.TAG, "Privacy setting for user " + senderId + " is anonymous but he has no nickname set.");
						viewHolder.senderName.setText(senderId);
					}
				}
				else{
					viewHolder.senderName.setText(firstName);
				}
			}
			else{

				new Request(currentSession, "/"+senderId, null,	HttpMethod.GET,
						new Request.Callback() {
					public void onCompleted(Response response) {
						if ( response.getError() == null ){

							JsonObject information = JsonValue.readFrom(response.getRawResponse()).asObject();
							String firstName = information.get("first_name").asString();
							AkiInternalStorageUtil.cacheUserFirstName(context, senderId, firstName);

							if ( AkiInternalStorageUtil.getAnonymousSetting(context, senderId)
									|| AkiInternalStorageUtil.getAnonymousSetting(context, currentUser.getId()) ){

								String nickname = AkiInternalStorageUtil.getCachedNickname(context, senderId);
								if ( nickname != null ){
									viewHolder.senderName.setText(nickname);
								}
								else{
									Log.e(AkiApplication.TAG, "Privacy setting for user " + senderId + " is anonymous but he has no nickname set.");
									viewHolder.senderName.setText(senderId);
								}
							}
							else{
								viewHolder.senderName.setText(firstName);
							}
							
							String fullName = information.get("name").asString();
							AkiInternalStorageUtil.cacheUserFullName(context, senderId, fullName);
						}
						else{
							if ( AkiInternalStorageUtil.getAnonymousSetting(context, senderId)
									|| AkiInternalStorageUtil.getAnonymousSetting(context, currentUser.getId()) ){

								String nickname = AkiInternalStorageUtil.getCachedNickname(context, senderId);
								if ( nickname != null ){
									viewHolder.senderName.setText(nickname);
								}
								else{
									Log.e(AkiApplication.TAG, "Privacy setting for user " + senderId + " is anonymous but he has no nickname set.");
									viewHolder.senderName.setText(senderId);
								}
							}
							else{
								Log.e(AkiApplication.TAG, "A problem happened while trying to query user Name from Facebook.");
								viewHolder.senderName.setText(senderId);
							}
						}
					}
				}).executeAsync();
			}
			
			AkiLocation senderLocation = AkiInternalStorageUtil.getCachedUserLocation(context, senderId);
			if ( senderLocation == null ){
				viewHolder.senderDistance.setImageResource(R.drawable.indicator_far);
				viewHolder.senderDistance.setImageAlpha(114);
			}
			else {
				//CALCULATE DISTANCE TO CURRENT USER AND UPDATE INDICATOR ACCORDINGLY
			}
		}

		String gender = AkiInternalStorageUtil.getCachedUserGender(context, senderId);
		if ( gender == null ){
			new Request(currentSession, "/"+senderId, null,	HttpMethod.GET,
					new Request.Callback() {
				public void onCompleted(Response response) {
					if ( response.getError() == null ){

						JsonObject information = JsonValue.readFrom(response.getRawResponse()).asObject();
						JsonValue gender = information.get("gender");
						if ( gender != null ){
							AkiInternalStorageUtil.cacheUserGender(context, senderId, gender.asString());
						}
						else{
							AkiInternalStorageUtil.cacheUserGender(context, senderId, "unknown");									
						}
					}
					else{
						System.out.println(response.getError());
						Log.e(AkiApplication.TAG, "A problem happened while trying to query user "+
								"gender from Facebook.");
					}
				}
			}).executeAsync();
			gender = AkiInternalStorageUtil.getCachedUserGender(context, senderId);
		}

		Bitmap placeholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_unknown);
		if ( gender != null ){
			if ( gender.equals("male") ){
				placeholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_male);
			}
			else if ( gender.equals("female") ){
				placeholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_female);				
			}
		}
		viewHolder.senderPicture.setImageBitmap(getRoundedBitmap(placeholder));

		if ( !( AkiInternalStorageUtil.getAnonymousSetting(context, senderId)
				|| AkiInternalStorageUtil.getAnonymousSetting(context, currentUser.getId()) )
				|| currentUser.getId().equals(senderId) ){

			Bitmap picture = AkiInternalStorageUtil.getCachedUserPicture(context, senderId);
			if ( picture != null ){

				viewHolder.senderPicture.setImageBitmap(picture);
			}
			else{

				Bundle params = new Bundle();
				params.putBoolean("redirect", false);
				params.putString("width", "143");
				params.putString("height", "143");
				new Request(currentSession, "/"+senderId+"/picture", params, HttpMethod.GET,
						new Request.Callback() {
					public void onCompleted(Response response) {
						if ( response.getError() != null ||
								JsonValue.readFrom(response.getRawResponse())
								.asObject().get("data") == null ){

							Log.e(AkiApplication.TAG, "A problem happened while trying to query user "+
									"picture from Facebook.");
							return;
						}
						JsonObject information = JsonValue.readFrom(response.getRawResponse())
								.asObject().get("data").asObject();

						if ( information.get("is_silhouette").asBoolean() ){
							Log.i(AkiApplication.TAG, "User does not have a picture from Facebook.");
							return;
						}

						new AsyncTask<String, Void, Bitmap>() {

							@Override
							protected Bitmap doInBackground(String... params) {

								try {
									URL picture_address = new URL(params[0]);
									Bitmap picture = getRoundedBitmap(BitmapFactory.
											decodeStream(picture_address.openConnection().getInputStream()));

									AkiInternalStorageUtil.cacheUserPicture(context, senderId, picture);
									return picture;

								} catch (MalformedURLException e) {
									Log.e(AkiApplication.TAG, "A problem happened while trying to query" +
											" user picture from Facebook.");
									e.printStackTrace();
									return null;
								} catch (IOException e) {
									Log.e(AkiApplication.TAG, "A problem happened while trying to query" +
											" user picture from Facebook.");
									e.printStackTrace();
									return null;
								}
							}

							@Override
							protected void onPostExecute(Bitmap picture){
								if ( picture != null ){
									viewHolder.senderPicture.setImageBitmap(picture);
								}
								else{
									Log.e(AkiApplication.TAG, "A problem happened while trying to query user "+
											"picture from Facebook.");
								}									
							}

						}.execute(information.get("url").asString());
					}
				}).executeAsync();
			}
			if ( AkiInternalStorageUtil.getAnonymousSetting(context, currentUser.getId()) ){
				viewHolder.senderPicture.setImageAlpha(128);
			}
			else{
				viewHolder.senderPicture.setImageAlpha(255);
			}
		}
		return rowView;
	}
}