package com.lespi.aki;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

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

public class AkiChatAdapter extends ArrayAdapter<JsonObject> {

	private final Context context;
	private final List<JsonObject> messages;
	private GraphUser currentUser = null;
	private Session currentSession = null;
	
	private static AkiChatAdapter instance;

	public static List<JsonObject> toJsonObjectList(JsonArray values){
		
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
		super(context, R.layout.aki_message_from_others, messages);
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
	}

	@SuppressLint("CutPasteId")
	@Override
	public View getView(int position, View convertView, ViewGroup parent){

		final JsonObject newViewData = messages.get(position);
		
		View rowView = convertView;
		boolean canReuse = false;

		final String senderId = newViewData.get("sender").asString();

		int rowLayout = R.layout.aki_message_from_others;
		if ( senderId.equals(currentUser.getId()) ){
			rowLayout = R.layout.aki_message_from_me;
		}
		
		if ( rowView != null ){
			
			TextView senderIdView = (TextView) rowView.findViewById(R.id.com_lespi_aki_message_sender_id);
			
			if ( senderIdView.getText() != null && senderIdView.getText().equals(currentUser.getId()) ){
				if ( senderId.equals(currentUser.getId()) ){
					canReuse = true;
				}
				else{
					rowLayout = R.layout.aki_message_from_others;
				}
			}
			else if ( senderIdView.getText() != null && !senderIdView.getText().equals(currentUser.getId()) ) {
				if ( !senderId.equals(currentUser.getId()) ){
					canReuse = true;
				}
				else{
					rowLayout = R.layout.aki_message_from_me;
				}
			}
		}

		
		if ( !canReuse ){

			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(rowLayout, parent, false);
			ViewHolder viewHolder = new ViewHolder();
			viewHolder.senderId = (TextView) rowView.findViewById(R.id.com_lespi_aki_message_sender_id);
			viewHolder.senderName = (TextView) rowView.findViewById(R.id.com_lespi_aki_message_sender_name);
			viewHolder.senderPicture = (ImageView) rowView.findViewById(R.id.com_lespi_aki_message_sender_picture);
			viewHolder.message = (TextView) rowView.findViewById(R.id.com_lespi_aki_message_text_message);
			rowView.setTag(viewHolder);
		}

		final ViewHolder viewHolder = (ViewHolder) rowView.getTag();
		
		Bitmap placeholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture);
		viewHolder.senderPicture.setImageBitmap(getRoundedBitmap(placeholder));
		
		if ( senderId.equals(currentUser.getId()) ){
			viewHolder.senderId.setText(currentUser.getId());
			viewHolder.senderName.setText(currentUser.getFirstName());
			viewHolder.message.setText(newViewData.get("message").asString());
		}
		else{

			String firstName = AkiInternalStorageUtil.getCachedUserFirstName(context, senderId);
			
			if ( firstName != null ){
				viewHolder.senderId.setText(senderId);
				viewHolder.senderName.setText(firstName);
				viewHolder.message.setText(newViewData.get("message").asString());
			}
			else{
				
				new Request(currentSession, "/"+senderId, null,	HttpMethod.GET,
					new Request.Callback() {
						public void onCompleted(Response response) {
							if ( response.getError() == null ){

								JsonObject information = JsonValue.readFrom(response.getRawResponse()).asObject();
								String firstName = information.get("first_name").asString();
								viewHolder.senderName.setText(firstName);
								AkiInternalStorageUtil.cacheUserFirstName(context, senderId, firstName);
							}
							else{
								Log.e(AkiApplication.TAG, "A problem happened while trying to query user "+
										"first name from Facebook.");
								viewHolder.senderName.setText(senderId);
							}
							viewHolder.senderId.setText(senderId);
							viewHolder.message.setText(newViewData.get("message").asString());
						}
					}
				).executeAsync();
			}
		}

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
						
						try {
							Bitmap picture = new AsyncTask<String, Void, Bitmap>() {

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

							}.execute(information.get("url").asString()).get();
							if ( picture != null ){
								viewHolder.senderPicture.setImageBitmap(picture);
							}
							else{
								Log.e(AkiApplication.TAG, "A problem happened while trying to query user "+
										"picture from Facebook.");
							}
						} catch (InterruptedException e) {
							Log.e(AkiApplication.TAG, "A problem happened while trying to query user "+
									"picture from Facebook.");
							e.printStackTrace();
						} catch (ExecutionException e) {
							Log.e(AkiApplication.TAG, "A problem happened while trying to query user "+
									"picture from Facebook.");
							e.printStackTrace();
						}
					}
				}
			).executeAsync();
		}
		return rowView;
	}
}