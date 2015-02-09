package com.lespi.aki;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.lespi.aki.json.JsonObject;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiInternalStorageUtil.AkiLocation;

public class AkiPrivateChatAdapter extends ArrayAdapter<JsonObject> {


	private final Context context;
	private final List<JsonObject> messages;

	private final int[] COLORS = new int[] {
			R.color.com_lespi_aki_message_text_color_0,
			R.color.com_lespi_aki_message_text_color_1,
			R.color.com_lespi_aki_message_text_color_2,
			R.color.com_lespi_aki_message_text_color_3,
			R.color.com_lespi_aki_message_text_color_4,
			R.color.com_lespi_aki_message_text_color_5,
			R.color.com_lespi_aki_message_text_color_6
	};
	private final Map<String, Integer> userToColorMapping;

	private static AkiPrivateChatAdapter instance;

	public static List<JsonObject> toJsonObjectList(PriorityQueue<JsonObject> values) {

		if (values == null) {
			return null;
		}
		List<JsonObject> toReturn = new ArrayList<JsonObject>();
		while ( !values.isEmpty() ) {
			toReturn.add(values.poll());
		}
		return toReturn;
	}

	public static AkiPrivateChatAdapter getInstance(Context context) {
		if (instance == null) {
			List<JsonObject> messages = new ArrayList<JsonObject>();
			instance = new AkiPrivateChatAdapter(context, messages);
		}
		return instance;
	}

	private AkiPrivateChatAdapter(Context context, List<JsonObject> messages) {
		super(context, R.layout.aki_chat_message_you, messages);
		this.context = context;
		this.messages = messages;
		userToColorMapping = new HashMap<String, Integer>();
		String currentUserId = AkiInternalStorageUtil.getCurrentUser(context);
		if ( currentUserId != null ){
			if ( userToColorMapping.get(currentUserId) == null ) {
				userToColorMapping.put(currentUserId, new Random().nextInt(COLORS.length));
			}
		}
	}

	private void assignColor(String userId, String currentUserId) {
		if ( userToColorMapping.get(userId) == null ) {
			boolean couldFindUnusedColor = false;
			int idx = -1;
			for ( int i=0; i<7; i++ ){
				idx = new Random().nextInt(COLORS.length);
				if ( userToColorMapping.containsValue(idx) ){
					idx = new Random().nextInt(COLORS.length);
				}
				else{
					couldFindUnusedColor = true;
					break;
				}
			}
			if ( couldFindUnusedColor ){
				userToColorMapping.put(userId, idx);
			}
			else{
				idx = userToColorMapping.get(currentUserId);
				userToColorMapping.clear();
				userToColorMapping.put(currentUserId, idx);
				assignColor(userId, currentUserId);
			}
		}
	}

	public Integer findColor(String userId) {
		return userToColorMapping.get(userId);
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

	public static double calculateDistance(AkiLocation currentLocation,
			AkiLocation senderLocation) {

		int R = 6371;
		double lat1 = Math.toRadians(currentLocation.latitude);
		double lat2 = Math.toRadians(senderLocation.latitude);
		double dLat = Math.toRadians(senderLocation.latitude
				- currentLocation.latitude);
		double dLong = Math.toRadians(senderLocation.longitude
				- currentLocation.longitude);

		double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(lat1)
				* Math.cos(lat2) * Math.pow(Math.sin(dLong / 2), 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return R * c;
	}

	static class ViewHolder {
		public TextView senderId;
		public TextView senderName;
		public ImageView senderPicture;
		public TextView message;
		public ImageView senderGender;
		public ImageView senderLiked;
	}

	@SuppressLint("CutPasteId")
	@Override
	public View getView(int position, View convertView, final ViewGroup parent) {

		final JsonObject newViewData = messages.get(position);

		View rowView = convertView;
		boolean canReuse = false;

		String currentUserId = AkiInternalStorageUtil.getCurrentUser(context);
		if ( currentUserId == null ){
			return rowView;
		}

		final String senderId = newViewData.get("sender").asString();
		assignColor(senderId, currentUserId);

		int rowLayout = R.layout.aki_chat_message_you;
		if (senderId.equals(currentUserId)) {
			rowLayout = R.layout.aki_chat_message_me;
		} else if (senderId.equals(AkiApplication.SYSTEM_SENDER_ID)) {
			rowLayout = R.layout.aki_chat_message_system;
		}

		if (rowView != null) {

			TextView senderIdView = (TextView) rowView
					.findViewById(R.id.com_lespi_aki_message_sender_id);

			if (senderIdView.getText() != null
					&& (senderIdView.getText().equals(
							AkiApplication.SYSTEM_SENDER_ID) || senderId
							.equals(AkiApplication.SYSTEM_SENDER_ID))) {
				canReuse = false;
			} else if (senderIdView.getText() != null
					&& senderIdView.getText().equals(currentUserId)) {
				if (senderId.equals(currentUserId)) {
					canReuse = true;
				} else {
					rowLayout = R.layout.aki_chat_message_you;
				}
			} else if (senderIdView.getText() != null
					&& !senderIdView.getText().equals(currentUserId)) {
				if (!senderId.equals(currentUserId)) {
					canReuse = true;
				} else {
					rowLayout = R.layout.aki_chat_message_me;
				}
			}
		}

		if (!canReuse) {

			LayoutInflater inflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(rowLayout, parent, false);

			ViewHolder viewHolder = new ViewHolder();
			viewHolder.senderId = (TextView) rowView
					.findViewById(R.id.com_lespi_aki_message_sender_id);
			viewHolder.senderName = (TextView) rowView
					.findViewById(R.id.com_lespi_aki_message_sender_name);
			viewHolder.senderName.setAlpha(1);
			viewHolder.senderPicture = (ImageView) rowView
					.findViewById(R.id.com_lespi_aki_message_sender_picture);
			viewHolder.message = (TextView) rowView
					.findViewById(R.id.com_lespi_aki_message_text_message);
			viewHolder.message.setAlpha(1);
			viewHolder.senderGender = (ImageView) rowView
					.findViewById(R.id.com_lespi_aki_message_sender_gender);
			viewHolder.senderGender.setImageAlpha(255);
			viewHolder.senderLiked = (ImageView) rowView.
					findViewById(R.id.com_lespi_aki_message_sender_like);
			rowView.setTag(viewHolder);
		}

		final ViewHolder viewHolder = (ViewHolder) rowView.getTag();

		viewHolder.senderId.setText(senderId);
		viewHolder.message.setText(newViewData.get("message").asString());

		if (senderId.equals(AkiApplication.SYSTEM_SENDER_ID)) {
			return rowView;
		}

		Integer color = findColor(senderId);
		color = COLORS[color != null ? color : android.R.color.white];
		if (senderId.equals(currentUserId)) {
			RelativeLayout rl = (RelativeLayout) rowView.findViewById(R.id.com_lespi_aki_message_me_layout);
			rl.setBackgroundColor(rowView.getResources().getColor(color));
			if ( newViewData.get("is_temporary").asString().equals("true") ){
				rl.setAlpha(0.5f);
			}
		} else if (!senderId.equals(AkiApplication.SYSTEM_SENDER_ID)) {
			RelativeLayout rl = (RelativeLayout) rowView.findViewById(R.id.com_lespi_aki_message_you_layout);
			rl.setBackgroundColor(rowView.getResources().getColor(color));
		}


		String senderFirstName = AkiInternalStorageUtil.getCachedUserFirstName(context, senderId);
		viewHolder.senderName.setText(senderFirstName);

		String gender = AkiInternalStorageUtil.getCachedUserGender(context, senderId);
		Bitmap picturePlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_unknown_gender);
		Bitmap genderPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_unknown_gender);
		if (gender != null) {
			if (gender.equals("male")) {
				picturePlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_male);
				genderPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_male);
			} else if (gender.equals("female")) {
				picturePlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_female);
				genderPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_female);
			}
		}
		viewHolder.senderPicture.setImageBitmap(getRoundedBitmap(picturePlaceholder));
		viewHolder.senderGender.setImageBitmap(genderPlaceholder);
		viewHolder.senderGender.setImageAlpha(255);

		Bitmap picture = AkiInternalStorageUtil.getCachedUserPicture(
				context, senderId);
		if (picture != null) {
			viewHolder.senderPicture.setImageBitmap(picture);
		}
		viewHolder.senderPicture.setImageAlpha(255);
		return rowView;
	}
}