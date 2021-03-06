package com.lespi.aki;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

import org.json.JSONObject;

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
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.facebook.AccessToken;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiInternalStorageUtil.AkiLocation;
import com.lespi.aki.utils.AkiServerUtil;

public class AkiChatAdapter extends ArrayAdapter<JsonObject> {


	private final Context context;
	private final List<JsonObject> messages;
	private JSONObject currentUser = null;
	private AccessToken currentSession = null;
	private FragmentActivity activity = null;

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

	private static AkiChatAdapter instance;

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

	public static AkiChatAdapter getInstance(Context context) {
		if (instance == null) {
			List<JsonObject> messages = new ArrayList<JsonObject>();
			instance = new AkiChatAdapter(context, messages);
		}
		return instance;
	}

	private AkiChatAdapter(Context context, List<JsonObject> messages) {
		super(context, R.layout.aki_chat_message_you, messages);
		this.context = context;
		this.messages = messages;
		userToColorMapping = new HashMap<String, Integer>();
	}

	public void setCurrentUser(JSONObject currentUser) {
		this.currentUser = currentUser;
		if ( userToColorMapping.get(currentUser.optString("id")) == null ) {
			userToColorMapping.put(currentUser.optString("id"), new Random().nextInt(COLORS.length));
		}
	}

	public void setCurrentSession(AccessToken currentSession) {
		this.currentSession = currentSession;
	}

	public void setActivity(FragmentActivity activity) {
		this.activity = activity;
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
		//		public ImageView senderDistance;
		public ImageView senderGender;
		public ImageView senderLiked;
	}

	@SuppressLint("CutPasteId")
	@Override
	public View getView(int position, View convertView, final ViewGroup parent) {

		final JsonObject newViewData = messages.get(position);

		View rowView = convertView;
		boolean canReuse = false;

		final String senderId = newViewData.get("sender").asString();
		assignColor(senderId, currentUser.optString("id"));

		int rowLayout = R.layout.aki_chat_message_you;
		if (senderId.equals(currentUser.optString("id"))) {
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
					&& senderIdView.getText().equals(currentUser.optString("id"))) {
				if (senderId.equals(currentUser.optString("id"))) {
					canReuse = true;
				} else {
					rowLayout = R.layout.aki_chat_message_you;
				}
			} else if (senderIdView.getText() != null
					&& !senderIdView.getText().equals(currentUser.optString("id"))) {
				if (!senderId.equals(currentUser.optString("id"))) {
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
			//			viewHolder.senderDistance = (ImageView) rowView
			//					.findViewById(R.id.com_lespi_aki_message_sender_distance);
			//			viewHolder.senderDistance.setImageAlpha(255);
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
		if (senderId.equals(currentUser.optString("id"))) {
			RelativeLayout rl = (RelativeLayout) rowView.findViewById(R.id.com_lespi_aki_message_me_layout);
			rl.setBackgroundColor(rowView.getResources().getColor(color));
			if ( newViewData.get("is_temporary").asString().equals("true") ){
				rl.setAlpha(0.5f);
			}
			// if it is not the current user, and not the system, it is other user
		} else if (!senderId.equals(AkiApplication.SYSTEM_SENDER_ID)) {
			RelativeLayout rl = (RelativeLayout) rowView.findViewById(R.id.com_lespi_aki_message_you_layout);
			rl.setBackgroundColor(rowView.getResources().getColor(color));
		}

		if (senderId.equals(currentUser.optString("id"))) {

			if (AkiInternalStorageUtil.getAnonymousSetting(context, senderId)) {
				String nickname = AkiInternalStorageUtil.getCachedUserNickname(
						context, senderId);
				if (nickname != null) {
					viewHolder.senderName.setText(nickname);
				} else {
					Log.e(AkiApplication.TAG, "Privacy setting for user "
							+ senderId
							+ " is anonymous but he has no nickname set.");
					viewHolder.senderName.setText(senderId);
				}
			} else {
				viewHolder.senderName.setText(currentUser.optString("first_name"));
			}
			AkiInternalStorageUtil.cacheUserFirstName(context, senderId,
					currentUser.optString("first_name"));
			AkiInternalStorageUtil.cacheUserFullName(context,
					currentUser.optString("id"), currentUser.optString("name"));
		} else {

			String firstName = AkiInternalStorageUtil.getCachedUserFirstName(
					context, senderId);
			if (firstName != null) {
				if (AkiInternalStorageUtil.getAnonymousSetting(context,
						senderId)
						|| AkiInternalStorageUtil.getAnonymousSetting(context,
								currentUser.optString("id"))) {

					String nickname = AkiInternalStorageUtil
							.getCachedUserNickname(context, senderId);
					if (nickname != null) {
						viewHolder.senderName.setText(nickname);
					} else {
						Log.e(AkiApplication.TAG, "Privacy setting for user "
								+ senderId
								+ " is anonymous but he has no nickname set.");
						viewHolder.senderName.setText(senderId);
					}
				} else {
					viewHolder.senderName.setText(firstName);
				}
			} else {

				new GraphRequest(currentSession, "/" + senderId, null,
						HttpMethod.GET, new GraphRequest.Callback() {
					public void onCompleted(GraphResponse response) {
						if (response.getError() == null) {

							JsonObject information = JsonValue
									.readFrom(response.getRawResponse())
									.asObject();
							JsonValue firstNameJ = information.get("first_name");
							String firstName=senderId;
							if(firstNameJ!=null){
								firstName=firstNameJ.asString();
							}
							AkiInternalStorageUtil.cacheUserFirstName(
									context, senderId, firstName);

							if (AkiInternalStorageUtil
									.getAnonymousSetting(context,
											senderId)
											|| AkiInternalStorageUtil
											.getAnonymousSetting(
													context,
													currentUser.optString("id"))) {

								String nickname = AkiInternalStorageUtil
										.getCachedUserNickname(context,
												senderId);
								if (nickname != null) {
									viewHolder.senderName
									.setText(nickname);
								} else {
									Log.e(AkiApplication.TAG,
											"Privacy setting for user "
													+ senderId
													+ " is anonymous but he has no nickname set.");
									viewHolder.senderName
									.setText(senderId);
								}
							} else {
								viewHolder.senderName
								.setText(firstName);
							}

							String fullName = information.get("name")
									.asString();
							AkiInternalStorageUtil.cacheUserFullName(
									context, senderId, fullName);
						} else {
							if (AkiInternalStorageUtil
									.getAnonymousSetting(context,
											senderId)
											|| AkiInternalStorageUtil
											.getAnonymousSetting(
													context,
													currentUser.optString("id"))) {

								String nickname = AkiInternalStorageUtil
										.getCachedUserNickname(context,
												senderId);
								if (nickname != null) {
									viewHolder.senderName
									.setText(nickname);
								} else {
									Log.e(AkiApplication.TAG,
											"Privacy setting for user "
													+ senderId
													+ " is anonymous but he has no nickname set.");
									viewHolder.senderName
									.setText(senderId);
								}
							} else {
								Log.e(AkiApplication.TAG,
										"A problem happened while trying to query user Name from Facebook.");
								viewHolder.senderName.setText(senderId);
							}
						}
					}
				}).executeAsync();
			}

			//			AkiLocation senderLocation = AkiInternalStorageUtil
			//					.getCachedUserLocation(context, senderId);
			//			if (senderLocation == null) {
			//				Log.d(AkiApplication.TAG, "Cannot calculate distance to "
			//						+ senderId + " because its location isn't available.");
			//				viewHolder.senderDistance
			//						.setImageResource(R.drawable.indicator_far);
			//				viewHolder.senderDistance.setImageAlpha(0);
			//			} else {
			//
			//				AkiLocation currentLocation = AkiInternalStorageUtil
			//						.getCachedUserLocation(context, currentUser.optString("id"));
			//				if (currentLocation == null) {
			//					Log.d(AkiApplication.TAG, "Cannot calculate distance to "
			//							+ senderId
			//							+ " because current location isn't available.");
			//					viewHolder.senderDistance
			//							.setImageResource(R.drawable.indicator_far);
			//					viewHolder.senderDistance.setImageAlpha(0);
			//				} else {
			//					double distance = calculateDistance(currentLocation,
			//							senderLocation);
			//
			//					Log.d(AkiApplication.TAG,
			//							"Distance to "
			//									+ AkiInternalStorageUtil
			//											.getCachedUserNickname(context,
			//													senderId) + ": " + distance);
			//
			//					double proportion = (distance / (AkiApplication.MIN_RADIUS * 2));
			//
			//					if (proportion >= 1) {
			//						viewHolder.senderDistance
			//								.setImageResource(R.drawable.indicator_far);
			//						viewHolder.senderDistance.setImageAlpha(255);
			//					} else if (proportion >= 0.65) {
			//						viewHolder.senderDistance
			//								.setImageResource(R.drawable.indicator_far);
			//						viewHolder.senderDistance
			//								.setImageAlpha((int) (255 * (proportion % 0.65 + (proportion % 0.65) * 1.8)));
			//					} else if (proportion >= 0.35) {
			//						viewHolder.senderDistance
			//								.setImageResource(R.drawable.indicator_close);
			//						viewHolder.senderDistance
			//								.setImageAlpha((int) (255 * (1 - (proportion % 0.35 + (proportion % 0.35) * 1.8))));
			//					} else {
			//						viewHolder.senderDistance
			//								.setImageResource(R.drawable.indicator_very_close);
			//						int opacity = (int) (255 * (1 - (proportion % 0.35 + (proportion % 0.35) * 1.8)));
			//						if (opacity < 128) {
			//							opacity = 128;
			//						}
			//						viewHolder.senderDistance.setImageAlpha(opacity);
			//					}
			//				}
			//			}
		}

		String gender = AkiInternalStorageUtil.getCachedUserGender(context,
				senderId);
		if (gender == null) {
			new GraphRequest(currentSession, "/" + senderId, null, HttpMethod.GET,
					new GraphRequest.Callback() {
				public void onCompleted(GraphResponse response) {
					if (response.getError() == null) {

						JsonObject information = JsonValue.readFrom(
								response.getRawResponse()).asObject();
						JsonValue gender = information.get("gender");
						if (gender != null) {
							AkiInternalStorageUtil.cacheUserGender(
									context, senderId,
									gender.asString());
						} else {
							AkiInternalStorageUtil.cacheUserGender(
									context, senderId, "unknown");
						}
					} else {
						System.out.println(response.getError());
						Log.e(AkiApplication.TAG,
								"A problem happened while trying to query user "
										+ "gender from Facebook.");
					}
				}
			}).executeAsync();
			gender = AkiInternalStorageUtil.getCachedUserGender(context,
					senderId);
		}

		Bitmap picturePlaceholder = BitmapFactory.decodeResource(
				context.getResources(), R.drawable.no_picture_unknown_gender);
		Bitmap genderPlaceholder = BitmapFactory.decodeResource(
				context.getResources(), R.drawable.icon_unknown_gender);
		if (gender != null) {
			if (gender.equals("male")) {
				picturePlaceholder = BitmapFactory.decodeResource(
						context.getResources(), R.drawable.no_picture_male);
				genderPlaceholder = BitmapFactory.decodeResource(
						context.getResources(), R.drawable.icon_male);
			} else if (gender.equals("female")) {
				picturePlaceholder = BitmapFactory.decodeResource(
						context.getResources(), R.drawable.no_picture_female);
				genderPlaceholder = BitmapFactory.decodeResource(
						context.getResources(), R.drawable.icon_female);
			}
		}
		viewHolder.senderPicture
		.setImageBitmap(getRoundedBitmap(picturePlaceholder));
		viewHolder.senderGender.setImageBitmap(genderPlaceholder);
		if (AkiInternalStorageUtil.getAnonymousSetting(context, senderId)
				|| AkiInternalStorageUtil.getAnonymousSetting(context,
						currentUser.optString("id"))) {
			viewHolder.senderGender.setImageAlpha(0);
		} else {
			viewHolder.senderGender.setImageAlpha(255);
		}

		if (!(AkiInternalStorageUtil.getAnonymousSetting(context, senderId) || AkiInternalStorageUtil
				.getAnonymousSetting(context, currentUser.optString("id")))
				|| currentUser.optString("id").equals(senderId)) {

			Bitmap picture = AkiInternalStorageUtil.getCachedUserPicture(
					context, senderId);
			if (picture != null) {

				viewHolder.senderPicture.setImageBitmap(picture);
			} else {

				Bundle params = new Bundle();
				params.putBoolean("redirect", false);
				params.putString("width", "143");
				params.putString("height", "143");
				new GraphRequest(currentSession, "/" + senderId + "/picture",
						params, HttpMethod.GET, new GraphRequest.Callback() {
					public void onCompleted(GraphResponse response) {
						if (response.getError() != null
								|| JsonValue
								.readFrom(
										response.getRawResponse())
										.asObject().get("data") == null) {

							Log.e(AkiApplication.TAG,
									"A problem happened while trying to query user "
											+ "picture from Facebook.");
							return;
						}
						JsonObject information = JsonValue
								.readFrom(response.getRawResponse())
								.asObject().get("data").asObject();

						if (information.get("is_silhouette")
								.asBoolean()) {
							Log.i(AkiApplication.TAG,
									"User does not have a picture from Facebook.");
							return;
						}

						new AsyncTask<String, Void, Bitmap>() {

							@Override
							protected Bitmap doInBackground(
									String... params) {

								try {
									URL picture_address = new URL(
											params[0]);
									Bitmap picture = getRoundedBitmap(BitmapFactory
											.decodeStream(picture_address
													.openConnection()
													.getInputStream()));

									AkiInternalStorageUtil
									.cacheUserPicture(context,
											senderId, picture);
									return picture;

								} catch (MalformedURLException e) {
									Log.e(AkiApplication.TAG,
											"A problem happened while trying to query"
													+ " user picture from Facebook.");
									e.printStackTrace();
									return null;
								} catch (IOException e) {
									Log.e(AkiApplication.TAG,
											"A problem happened while trying to query"
													+ " user picture from Facebook.");
									e.printStackTrace();
									return null;
								}
							}

							@Override
							protected void onPostExecute(Bitmap picture) {
								if (picture != null) {
									viewHolder.senderPicture
									.setImageBitmap(picture);
								} else {
									Log.e(AkiApplication.TAG,
											"A problem happened while trying to query user "
													+ "picture from Facebook.");
								}
							}

						}.execute(information.get("url").asString());
					}
				}).executeAsync();
			}
			if (AkiInternalStorageUtil.getAnonymousSetting(context,
					currentUser.optString("id"))) {
				viewHolder.senderPicture.setImageAlpha(128);
			} else {
				viewHolder.senderPicture.setImageAlpha(255);
			}
		}

		if ( currentUser.optString("id").equals(senderId) || senderId.equals(AkiApplication.SYSTEM_SENDER_ID) ){
			viewHolder.senderLiked.setVisibility(View.GONE);
		}
		else {

			final GestureDetector mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
				@Override
				public boolean onDoubleTap(MotionEvent e) {
					if ( AkiInternalStorageUtil.cacheHasLikedUser(context, senderId) ){
						if ( !AkiInternalStorageUtil.hasMatch(context, senderId) ){
							AkiInternalStorageUtil.cacheDislikeUser(context, senderId);
							AkiServerUtil.sendDislikeToServer(context, senderId);
							AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(context);
							chatAdapter.notifyDataSetChanged();							
						}
						else{
							final StringBuilder message = new StringBuilder()
							.append(context.getString(R.string.com_lespi_aki_mutual_interest_delete_match_confirm_text));
							String fullName = AkiInternalStorageUtil.getCachedUserFullName(context, senderId);
							if ( fullName != null ){
								message.append(" " + fullName + "?");
							}
							else{
								String nickname = AkiInternalStorageUtil.getCachedUserNickname(context, senderId);
								if ( nickname != null ){
									message.append(" " + nickname + "?");
								}
								else{
									message.append(" " + context.getString(R.string.com_lespi_aki_mutual_interest_delete_match_confirm_text_unknown_user) + "?");
								}
							}
							
							if ( activity != null ){
								new AlertDialog.Builder(activity)
								.setIcon(R.drawable.icon_remove)
								.setTitle(R.string.com_lespi_aki_mutual_interest_delete_match_confirm_title)
								.setMessage(message.toString())
								.setPositiveButton(R.string.com_lespi_aki_confirm_yes, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										AkiServerUtil.removeMutualInterest(context, senderId);
									}
								})
								.setNegativeButton(R.string.com_lespi_aki_confirm_no, new DialogInterface.OnClickListener(){
									@Override
									public void onClick(DialogInterface dialog, int which) {}
								})
								.show();
							}
						}
					}
					else{
						AkiInternalStorageUtil.cacheLikeUser(context, senderId);
						AkiServerUtil.sendLikeToServer(context, senderId);
						AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(context);
						chatAdapter.notifyDataSetChanged();
						
						String privateChatId = AkiServerUtil.buildPrivateChatId(context, senderId);
						AkiInternalStorageUtil.setPrivateChatRoomAnonymousSetting(context, privateChatId,
								currentUser.optString("id"), AkiInternalStorageUtil.getAnonymousSetting(context, currentUser.optString("id")));
						AkiServerUtil.sendPrivateMessage(context, null, senderId, null);
					}
					return true;
				}
				@Override
				public boolean onDown(MotionEvent e) {
					return true;
				}
			});
			viewHolder.senderPicture.setOnTouchListener(new OnTouchListener() {

				@Override
				public boolean onTouch(View v, MotionEvent event) {
					v.performClick();
					return mGestureDetector.onTouchEvent(event);
				}

			});

			if(AkiInternalStorageUtil.cacheHasLikedUser(context, senderId)){
				if ( viewHolder.senderLiked.getVisibility() != View.VISIBLE ){
					Animation jumpAnimation = AnimationUtils.loadAnimation(context, R.anim.jump_in);
					jumpAnimation.setAnimationListener(new AnimationListener() {

						@Override
						public void onAnimationStart(Animation animation) {}

						@Override
						public void onAnimationRepeat(Animation animation) {}

						@Override
						public void onAnimationEnd(Animation animation) {}
					});
					viewHolder.senderLiked.setVisibility(View.VISIBLE);
					viewHolder.senderLiked.startAnimation(jumpAnimation);
				}
			}else{
//				if ( viewHolder.senderLiked.getVisibility() != View.INVISIBLE ){
//					Animation fadeOutAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_out);
//					fadeOutAnimation.setAnimationListener(new AnimationListener() {
//
//						@Override
//						public void onAnimationStart(Animation animation) {}
//
//						@Override
//						public void onAnimationRepeat(Animation animation) {}
//
//						@Override
//						public void onAnimationEnd(Animation animation) {
//							viewHolder.senderLiked.setVisibility(View.INVISIBLE);
//						}
//					});
//					viewHolder.senderLiked.setVisibility(View.VISIBLE);
//					viewHolder.senderLiked.startAnimation(fadeOutAnimation);
//				}
				/*
				 * Replacing all commented above with:
				 */
				viewHolder.senderLiked.setVisibility(View.INVISIBLE);
			}
		}
		return rowView;
	}
}