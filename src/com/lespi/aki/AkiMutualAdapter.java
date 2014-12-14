package com.lespi.aki;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.lespi.aki.utils.AkiInternalStorageUtil;

public class AkiMutualAdapter extends ArrayAdapter<String> {

	private final Context context;
	private final List<String> interests;

	private static AkiMutualAdapter instance;

	public static AkiMutualAdapter getInstance(Context context) {
		if (instance == null) {
			List<String> interests = new ArrayList<String>();
			instance = new AkiMutualAdapter(context, interests);
		}
		return instance;
	}

	public AkiMutualAdapter(Context context, List<String> interests) {
		super(context, R.layout.aki_chat_message_you, interests);
		this.context = context;
		this.interests = interests;
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

	static class ViewHolder {
		public TextView senderId;
		public TextView senderName;
		public ImageView senderPicture;
		public TextView message;
		public ImageView senderGender;
	}

	@SuppressLint("CutPasteId")
	@Override
	public View getView(int position, View convertView, final ViewGroup parent) {

		final String senderId = interests.get(position);

		View rowView = convertView;
		boolean canReuse = false;

		int rowLayout = R.layout.aki_chat_message_you;

		if (rowView != null) {

			TextView senderIdView = (TextView) rowView
					.findViewById(R.id.com_lespi_aki_message_sender_id);

			if (senderIdView.getText() != null
					&& senderIdView.getText().equals(senderId)) {
				canReuse = true;
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
			rowView.setTag(viewHolder);
		}

		final ViewHolder viewHolder = (ViewHolder) rowView.getTag();

		viewHolder.senderId.setText(senderId);
		viewHolder.message.setText("This user has interest in you!");

		String firstName = AkiInternalStorageUtil.getCachedUserFirstName(context, senderId);
		viewHolder.senderName.setText(firstName);

		String gender = AkiInternalStorageUtil.getCachedUserGender(context, senderId);
		Bitmap genderPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_unknown_gender);

		if (gender != null) {
			if (gender.equals("male")) {
				genderPlaceholder = BitmapFactory.decodeResource(
						context.getResources(), R.drawable.icon_male);
			} else if (gender.equals("female")) {
				genderPlaceholder = BitmapFactory.decodeResource(
						context.getResources(), R.drawable.icon_female);
			}
		}
		viewHolder.senderGender.setImageBitmap(genderPlaceholder);
		viewHolder.senderGender.setImageAlpha(255);

		Bitmap picture = AkiInternalStorageUtil.getCachedUserPicture(context, senderId);
		viewHolder.senderPicture.setImageBitmap(picture);
		viewHolder.senderPicture.setImageAlpha(255);
		return rowView;
	}
}