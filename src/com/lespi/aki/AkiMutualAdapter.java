package com.lespi.aki;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiServerUtil;

public class AkiMutualAdapter extends ArrayAdapter<String> {

	private final Context context;
	private final List<String> interests;
	private Session currentSession = null;
	private FragmentActivity activity = null;

	private static AkiMutualAdapter instance;

	public static AkiMutualAdapter getInstance(Context context) {
		if (instance == null) {
			List<String> interests = new ArrayList<String>();
			instance = new AkiMutualAdapter(context, interests);
		}
		return instance;
	}

	public AkiMutualAdapter(Context context, List<String> interests) {
		super(context, R.layout.aki_mutual_interest, interests);
		this.context = context;
		this.interests = interests;
	}

	public void setCurrentSession(Session currentSession) {
		this.currentSession = currentSession;
	}

	public void setActivity(FragmentActivity activity) {
		this.activity = activity;
	}

	public static Bitmap getRoundedBitmap(Bitmap bitmap) {
		Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
				bitmap.getHeight(), Bitmap.Config.ARGB_8888);
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

	public void addAll(Collection<? extends String> collection) {
		if ( collection.size() == 0 ){
			super.addAll(Collections.singleton(AkiApplication.SYSTEM_EMPTY_ID));
		}
		else{
			super.addAll(collection);
		}
    }
	
	static class ViewHolder {
		public TextView userId;
		public TextView userName;
		public TextView userNick;
		public ImageView userPicture;
		public ImageView userGender;
		public ImageButton userRemoveMatch;
	}
	
	@SuppressLint("CutPasteId")
	@Override
	public View getView(int position, View convertView, final ViewGroup parent) {

		final String userId = interests.get(position);

		View rowView = convertView;
		boolean canReuse = false;

		int rowLayout = R.layout.aki_mutual_interest;

		if (rowView != null) {

			TextView senderIdView = (TextView) rowView.findViewById(R.id.com_lespi_aki_mutual_interest_user_id);

			if (senderIdView.getText() != null
					&& senderIdView.getText().equals(userId)) {
				canReuse = true;
			}
		}

		if (!canReuse) {

			LayoutInflater inflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(rowLayout, parent, false);

			ViewHolder viewHolder = new ViewHolder();
			viewHolder.userId = (TextView) rowView
					.findViewById(R.id.com_lespi_aki_mutual_interest_user_id);
			viewHolder.userName = (TextView) rowView
					.findViewById(R.id.com_lespi_aki_mutual_interest_user_name);
			viewHolder.userNick = (TextView) rowView
					.findViewById(R.id.com_lespi_aki_mutual_interest_user_nick);
			viewHolder.userPicture = (ImageView) rowView
					.findViewById(R.id.com_lespi_aki_mutual_interest_user_picture);
			viewHolder.userGender = (ImageView) rowView
					.findViewById(R.id.com_lespi_aki_mutual_interest_user_gender);
			viewHolder.userRemoveMatch = (ImageButton) rowView
					.findViewById(R.id.com_lespi_aki_mutual_interest_delete_match_btn);
			rowView.setTag(viewHolder);
		}

		final ViewHolder viewHolder = (ViewHolder) rowView.getTag();

		viewHolder.userId.setText(userId);
		
		if ( userId.equals(AkiApplication.SYSTEM_EMPTY_ID) ){
			viewHolder.userNick.setText(context.getString(R.string.com_lespi_aki_mutual_interest_no_matches_info));
			Bitmap genderPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_unknown_gender);
			viewHolder.userGender.setImageBitmap(genderPlaceholder);
			Bitmap picturePlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_unknown_gender);
			viewHolder.userPicture.setImageBitmap(getRoundedBitmap(picturePlaceholder));
			viewHolder.userName.setVisibility(View.GONE);
			viewHolder.userRemoveMatch.setVisibility(View.GONE);
			return rowView;
		}

		final String fullName = AkiInternalStorageUtil.getCachedUserFullName(context, userId);
		if ( fullName != null ){
			viewHolder.userName.setText(fullName);
		}
		else{
			new Request(currentSession, "/" + userId, null,
					HttpMethod.GET, new Request.Callback() {
				public void onCompleted(Response response) {
					if (response.getError() == null) {
						JsonObject information = JsonValue.readFrom(response.getRawResponse()).asObject();

						final JsonValue fullName = information.get("name");
						if ( fullName != null ){
							AkiInternalStorageUtil.cacheUserFullName(context, userId, fullName.asString());
							viewHolder.userName.setText(fullName.asString());
						}

						JsonValue firstName = information.get("first_name");
						if ( firstName != null ){
							AkiInternalStorageUtil.cacheUserFirstName(context, userId, firstName.asString());
						}

						JsonValue gender = information.get("gender");
						if (gender != null) {
							AkiInternalStorageUtil.cacheUserGender(context, userId, gender.asString());
							Bitmap genderPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_unknown_gender);
							viewHolder.userGender.setImageBitmap(genderPlaceholder);
							if (gender.asString().equals("male")) {
								genderPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_male);
							} else if (gender.asString().equals("female")) {
								genderPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_female);
							}
							viewHolder.userGender.setImageAlpha(255);
						}
					}
					else{
						viewHolder.userName.setVisibility(View.GONE);
					}
				}
			}).executeAsync();
		}

		String nickname = AkiInternalStorageUtil.getCachedUserNickname(context, userId);
		if ( nickname != null ){
			viewHolder.userNick.setText(nickname);
		}
		else{
			viewHolder.userNick.setVisibility(View.GONE);
		}

		String gender = AkiInternalStorageUtil.getCachedUserGender(context, userId);
		Bitmap genderPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_unknown_gender);
		viewHolder.userGender.setImageBitmap(genderPlaceholder);
		if (gender.equals("male")) {
			genderPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_male);
			viewHolder.userGender.setImageBitmap(genderPlaceholder);
		} else if (gender.equals("female")) {
			genderPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_female);
			viewHolder.userGender.setImageBitmap(genderPlaceholder);
		}
		viewHolder.userGender.setImageAlpha(255);

		Bitmap picture = AkiInternalStorageUtil.getCachedUserPicture(context, userId);

		if ( picture != null ){
			viewHolder.userPicture.setImageBitmap(picture);
			viewHolder.userPicture.setImageAlpha(255);
		}
		else{
			Bitmap picturePlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_unknown_gender);
			viewHolder.userPicture.setImageBitmap(getRoundedBitmap(picturePlaceholder));
			if (gender.equals("male")) {
				picturePlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_male);
				viewHolder.userPicture.setImageBitmap(getRoundedBitmap(picturePlaceholder));
			} else if (gender.equals("female")) {
				picturePlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_female);
				viewHolder.userPicture.setImageBitmap(getRoundedBitmap(picturePlaceholder));
			}

			Bundle params = new Bundle();
			params.putBoolean("redirect", false);
			params.putString("width", "143");
			params.putString("height", "143");
			new Request(currentSession, "/" + userId + "/picture",
					params, HttpMethod.GET, new Request.Callback() {
				public void onCompleted(Response response) {
					if (response.getError() != null || JsonValue.readFrom(response.getRawResponse()).asObject().get("data") == null) {
						Log.e(AkiApplication.TAG, "A problem happened while trying to query user picture from Facebook.");
						return;
					}

					JsonObject information = JsonValue.readFrom(response.getRawResponse()).asObject().get("data").asObject();

					if (information.get("is_silhouette").asBoolean()) {
						Log.i(AkiApplication.TAG, "User does not have a picture from Facebook.");
						return;
					}

					new AsyncTask<String, Void, Bitmap>() {

						@Override
						protected Bitmap doInBackground(String... params) {

							try {
								URL picture_address = new URL(params[0]);
								Bitmap picture = getRoundedBitmap(BitmapFactory.decodeStream(picture_address.openConnection().getInputStream()));
								AkiInternalStorageUtil.cacheUserPicture(context, userId, picture);
								return picture;

							} catch (MalformedURLException e) {
								Log.e(AkiApplication.TAG, "A problem happened while trying to query user picture from Facebook.");
								e.printStackTrace();
								return null;
							} catch (IOException e) {
								Log.e(AkiApplication.TAG, "A problem happened while trying to query user picture from Facebook.");
								e.printStackTrace();
								return null;
							}
						}

						@Override
						protected void onPostExecute(Bitmap picture) {
							if (picture != null) {
								viewHolder.userPicture.setImageBitmap(picture);
								viewHolder.userPicture.setImageAlpha(255);
							} else {
								Log.e(AkiApplication.TAG, "A problem happened while trying to query user picture from Facebook.");
							}
						}

					}.execute(information.get("url").asString());
				}
			}).executeAsync();
		}

		if ( activity != null ){
			
			final StringBuilder message = new StringBuilder()
			.append(context.getString(R.string.com_lespi_aki_mutual_interest_delete_match_confirm_text));
			if ( fullName != null ){
				message.append(" " + fullName + "?");
			}
			else{
				if ( nickname != null ){
					message.append(" " + nickname + "?");
				}
				else{
					message.append(" " + context.getString(R.string.com_lespi_aki_mutual_interest_delete_match_confirm_text_unknown_user) + "?");
				}
			}

			viewHolder.userRemoveMatch.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View view) {
					new AlertDialog.Builder(activity)
					.setIcon(R.drawable.icon_remove)
					.setTitle(R.string.com_lespi_aki_mutual_interest_delete_match_confirm_title)
					.setMessage(message.toString())
					.setPositiveButton(R.string.com_lespi_aki_confirm_yes, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							AkiServerUtil.removeMutualInterest(context, userId);
						}
					})
					.setNegativeButton(R.string.com_lespi_aki_confirm_no, new DialogInterface.OnClickListener(){
						@Override
						public void onClick(DialogInterface dialog, int which) {}
					})
					.show();
				}
			});
		}
		return rowView;
	}
}