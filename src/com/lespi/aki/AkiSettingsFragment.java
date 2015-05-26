package com.lespi.aki;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.facebook.AccessToken;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.facebook.login.LoginManager;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiServerUtil;
import com.parse.ParseInstallation;
import com.parse.internal.AsyncCallback;

public class AkiSettingsFragment extends SherlockFragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.aki_settings_fragment, container, false);
		
		Button logoutButton = (Button) view.findViewById(R.id.com_lespi_aki_main_settings_logout_btn);
		logoutButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				
				new AlertDialog.Builder(getActivity())
				.setIcon(R.drawable.icon_exit)
				.setTitle(R.string.com_lespi_aki_main_chat_logout_confirm_title)
				.setMessage(R.string.com_lespi_aki_main_chat_logout_confirm_text)
				.setPositiveButton(R.string.com_lespi_aki_confirm_yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						LoginManager.getInstance().logOut();
						
						final AkiMainActivity activity = (AkiMainActivity) getActivity();
						
						if ( activity.locationServicesConnected() ){
							activity.stopPeriodicLocationUpdates();
							activity.removeGeofence();
						}

						Log.wtf("PULL MAN!", "Stopping getMessages runnable!");
						AkiServerUtil.stopGettingMessages(activity.getApplicationContext());

						final Context context = activity.getApplicationContext();
						final String currentUserId = AkiInternalStorageUtil.getCurrentUser(activity.getApplicationContext());
						
						if ( AkiApplication.LOGGED_IN ){

							AkiServerUtil.sendExitToServer(context, new AsyncCallback() {

								@Override
								public void onSuccess(Object response) {
									AkiServerUtil.leaveChatRoom(context, currentUserId);
									AkiInternalStorageUtil.wipeMatches(context);
									String chatId = AkiInternalStorageUtil.getCurrentChatRoom(context);
									if ( chatId != null ){
										AkiInternalStorageUtil.removeCachedMessages(context, chatId);
									}
									AkiInternalStorageUtil.clearVolatileStorage(context);
									AkiInternalStorageUtil.wipeCachedUserLocation(context, new AsyncCallback() {

										@Override
										public void onSuccess(Object response) {
											AkiInternalStorageUtil.setCurrentUser(context, null);
										}

										@Override
										public void onFailure(Throwable failure) {
											Log.e(AkiChatFragment.TAG, "Could not wipe cached user location.");
											failure.printStackTrace();
											AkiInternalStorageUtil.setCurrentUser(context, null);
										}

										@Override
										public void onCancel() {
											Log.e(AkiChatFragment.TAG, "Wipe cached user location callback canceled.");
										}
									});

									CharSequence toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_exited_chat);
									Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
									toast.show();
									AkiApplication.isNotLoggedIn();
								}

								@Override
								public void onFailure(Throwable failure) {
									Log.e(AkiChatFragment.TAG, "A problem happened while exiting chat room!");
									failure.printStackTrace();
								}

								@Override
								public void onCancel() {
									Log.e(AkiChatFragment.TAG, "Exiting chat room canceled.");
								}
							});
						}

						ParseInstallation installation = ParseInstallation.getCurrentInstallation();
						installation.put("uid", "not_logged");
						installation.saveInBackground();

						Intent intent = new Intent(activity, AkiLoginActivity.class);
						activity.startActivity(intent);
						activity.overridePendingTransition(R.anim.hold, R.anim.fade_in);
						activity.finish();
						activity.overridePendingTransition(R.anim.hold, R.anim.fade_out);
					}
				})
				.setNegativeButton(R.string.com_lespi_aki_confirm_no, new DialogInterface.OnClickListener(){
					@Override
					public void onClick(DialogInterface dialog, int which) {}
				})
				.show();
			}
		});
		final EditText nicknameBox = (EditText) view.findViewById(R.id.com_lespi_aki_main_settings_nickname);
		nicknameBox.clearFocus();
		return view;
	}

	public synchronized void refreshSettings(final AkiMainActivity activity, final AccessToken currentSession,
			final JSONObject currentUser, final AsyncCallback callback) {

		if ( activity == null || currentUser == null ){
			return;
		}
		
		final Context context = activity.getApplicationContext();
		
		try{
			AkiInternalStorageUtil.aMandatorySettingIsMissing(context, true);

			TextView settingsFullname = (TextView) activity.findViewById(R.id.com_lespi_aki_main_settings_fullname);
			settingsFullname.setText(currentUser.optString("name"));
			AkiInternalStorageUtil.cacheUserFullName(context, currentUser.optString("id"), currentUser.optString("name"));
			AkiInternalStorageUtil.cacheUserFirstName(context, currentUser.optString("id"), currentUser.optString("first_name"));
			
			final EditText nicknameBox = (EditText) activity.findViewById(R.id.com_lespi_aki_main_settings_nickname);
			String nickname = AkiInternalStorageUtil.getCachedUserNickname(context, currentUser.optString("id"));

			final LinearLayout anonymousSection = (LinearLayout) activity.findViewById(R.id.com_lespi_aki_main_settings_anonymous_section);
			final ImageButton anonymousCheck = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_settings_anonymous_btn);
			final TextView anonymousInfo = (TextView) activity.findViewById(R.id.com_lespi_aki_main_settings_anonymous_text);
			if ( AkiInternalStorageUtil.getAnonymousSetting(context, currentUser.optString("id")) ){
				anonymousCheck.setImageDrawable(activity.getApplicationContext().getResources().getDrawable(R.drawable.icon_anonymous));
				anonymousInfo.setText(activity.getApplicationContext().getString(R.string.com_lespi_aki_main_settings_privacy_identify_yourself));
			}
			else {
				anonymousCheck.setImageDrawable(activity.getApplicationContext().getResources().getDrawable(R.drawable.icon_identified));
				anonymousInfo.setText(activity.getApplicationContext().getString(R.string.com_lespi_aki_main_settings_privacy_no_longer_anonymous));
			}
			
			if ( ( nickname == null || nickname.trim().isEmpty() ) ){
				AkiInternalStorageUtil.aMandatorySettingIsMissing(context, true);
				anonymousCheck.setImageDrawable(context.getResources().getDrawable(R.drawable.icon_anonymous));
				anonymousInfo.setText(activity.getApplicationContext().getString(R.string.com_lespi_aki_main_settings_privacy_still_disabled));
				anonymousSection.setEnabled(false);
				anonymousCheck.setImageAlpha(128);
				anonymousSection.setAlpha(0.5f);
				SlidingMenu slidingMenu = activity.getSlidingMenu();
				slidingMenu.showMenu();
				slidingMenu.setSlidingEnabled(false);
				slidingMenu.setEnabled(false);
				CharSequence toastText = context.getText(R.string.com_lespi_aki_toast_nickname_required);
				Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
				toast.show();
				AkiInternalStorageUtil.wipeCachedUserLocation(context, currentUser.optString("id"));
			}
			else{
				AkiInternalStorageUtil.aMandatorySettingIsMissing(context, false);
				if ( !nicknameBox.isFocused() ){
					nicknameBox.setText(nickname);
				}
			}

			final ImageButton changeNicknameBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_settings_nickname_btn);
			changeNicknameBtn.setImageResource(R.drawable.icon_saved);
			
			changeNicknameBtn.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View view) {
					String newNickname = nicknameBox.getText().toString();
					String nickname = AkiInternalStorageUtil.getCachedUserNickname(context, currentUser.optString("id"));
					if ( nickname != null ){
						nicknameBox.setText(nickname);
					}

					newNickname = newNickname.trim();
					if ( newNickname.isEmpty() ){
						CharSequence toastText = context.getText(R.string.com_lespi_aki_toast_nickname_cannot_be_blank);
						Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
						toast.show();
						return;
					}
					AkiInternalStorageUtil.cacheUserNickname(context, currentUser.optString("id"), newNickname);
					nicknameBox.setText(newNickname);
					if ( AkiInternalStorageUtil.isMandatorySettingMissing(context) ){
						SlidingMenu slidingMenu = activity.getSlidingMenu();
						slidingMenu.setSlidingEnabled(true);
						slidingMenu.setEnabled(true);
						slidingMenu.showContent();
						anonymousCheck.setImageDrawable(context.getResources().getDrawable(R.drawable.icon_anonymous));
						anonymousSection.setEnabled(true);
						anonymousCheck.setImageAlpha(255);
						anonymousSection.setAlpha(1);
						anonymousInfo.setText(activity.getApplicationContext().getString(R.string.com_lespi_aki_main_settings_privacy_identify_yourself));
						callback.onSuccess(null);
						CharSequence toastText = context.getText(R.string.com_lespi_aki_toast_nickname_set);
						Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
						toast.show();
					}
					AkiInternalStorageUtil.aMandatorySettingIsMissing(context, false);
					changeNicknameBtn.setImageResource(R.drawable.icon_saved);
					AkiChatFragment.getInstance().externalRefreshAll();
				}
			});
			
			nicknameBox.setOnFocusChangeListener(new OnFocusChangeListener() {
				@Override
				public void onFocusChange(View view, boolean hasFocus) {
					changeNicknameBtn.setImageResource(R.drawable.icon_save);
				}
			});
			
			anonymousCheck.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View view) {
					if ( !AkiInternalStorageUtil.isMandatorySettingMissing(context) ){
						if ( AkiInternalStorageUtil.getAnonymousSetting(context, currentUser.optString("id")) ){
							new AlertDialog.Builder(activity)
							.setIcon(R.drawable.icon_identified)
							.setTitle(R.string.com_lespi_aki_main_settings_privacy_will_identify_title)
							.setMessage(R.string.com_lespi_aki_main_settings_privacy_will_identify_message)
							.setPositiveButton(R.string.com_lespi_aki_confirm_yes, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									anonymousCheck.setImageDrawable(context.getResources().getDrawable(R.drawable.icon_identified));
									anonymousInfo.setText(activity.getApplicationContext().getString(R.string.com_lespi_aki_main_settings_privacy_no_longer_anonymous));
									AkiInternalStorageUtil.setAnonymousSetting(context, currentUser.optString("id"), false);
									AkiServerUtil.sendPresenceToServer(context, currentUser.optString("id"), new AsyncCallback() {
										@Override
										public void onSuccess(Object response) {
											AkiChatFragment.getInstance().externalRefreshAll();
										}
										@Override
										public void onFailure(Throwable failure) {}
										@Override
										public void onCancel() {}
									});
								}
							})
							.setNegativeButton(R.string.com_lespi_aki_confirm_no, new DialogInterface.OnClickListener(){
								@Override
								public void onClick(DialogInterface dialog, int which) {}
							})
							.show();
						}
						else {
							anonymousCheck.setImageDrawable(context.getResources().getDrawable(R.drawable.icon_identified));
							anonymousInfo.setText(activity.getApplicationContext().getString(R.string.com_lespi_aki_main_settings_privacy_no_longer_anonymous));
							CharSequence toastText = context.getText(R.string.com_lespi_aki_toast_anonymous_cannot_be_set);
							Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
							toast.show();
						}
					}
					else{
						CharSequence toastText = context.getText(R.string.com_lespi_aki_toast_nickname_required);
						Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
						toast.show();
					}
				}
			});

			final ImageView settingsPicture = (ImageView) activity.findViewById(R.id.com_lespi_aki_main_settings_picture);

			Bitmap placeholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_unknown_gender);
			String gender = currentUser.optString("gender");
			if ( gender != null ){
				AkiInternalStorageUtil.cacheUserGender(context, currentUser.optString("id"), gender);
				if ( gender.equals("male") ){
					placeholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_male);
				}
				else if ( gender.equals("female") ){
					placeholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_female);				
				}
			}
			settingsPicture.setImageBitmap(AkiChatAdapter.getRoundedBitmap(placeholder));

			Bitmap cachedPicture = AkiInternalStorageUtil.getCachedUserPicture(context, currentUser.optString("id"));
			if ( cachedPicture != null ){
				settingsPicture.setImageBitmap(cachedPicture);
				AkiServerUtil.makeSureUserPictureIsUploaded(context, currentUser.optString("id"));
			}
			else{

				Bundle params = new Bundle();
				params.putBoolean("redirect", false);
				params.putString("width", "143");
				params.putString("height", "143");
				new GraphRequest(currentSession, "/"+currentUser.optString("id")+"/picture", params, HttpMethod.GET,
						new GraphRequest.Callback() {
					public void onCompleted(GraphResponse response) {
						if ( response.getError() != null ||
								JsonValue.readFrom(response.getRawResponse())
								.asObject().get("data") == null ){

							Log.e(AkiApplication.TAG, "A problem happened while trying to query user picture from Facebook.");
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
									Bitmap picture = AkiChatAdapter.getRoundedBitmap(BitmapFactory.
											decodeStream(picture_address.openConnection().getInputStream()));

									AkiInternalStorageUtil.cacheUserPicture(context, currentUser.optString("id"), picture);
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
									settingsPicture.setImageBitmap(picture);
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

			final ImageView settingsCover = (ImageView) activity.findViewById(R.id.com_lespi_aki_main_settings_cover);
			settingsCover.setAdjustViewBounds(false);
			settingsCover.setMinimumWidth(851);
			settingsCover.setMinimumHeight(315);
			settingsCover.setScaleType(ImageView.ScaleType.CENTER_CROP);

			Bitmap coverPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_cover);
			settingsCover.setImageBitmap(coverPlaceholder);

			Bitmap cachedCover = AkiInternalStorageUtil.getCachedUserCoverPhoto(context, currentUser.optString("id"));
			if ( cachedCover != null ){
				settingsCover.setImageBitmap(cachedCover);
				AkiServerUtil.makeSureCoverPhotoIsUploaded(context, currentUser.optString("id"));
			}
			else{
				Bundle params = new Bundle();
				params.putBoolean("redirect", false);
				params.putString("width", "851");
				params.putString("height", "315");
				params.putString("fields", "cover");
				new GraphRequest(currentSession, "/"+currentUser.optString("id"), params, HttpMethod.GET, new GraphRequest.Callback() {
					public void onCompleted(GraphResponse response) {
						if ( response.getError() != null ||
								JsonValue.readFrom(response.getRawResponse()).asObject().get("cover") == null ){

							Log.e(AkiApplication.TAG, "A problem happened while trying to query user "+
									"cover photo from Facebook.");
							return;
						}
						JsonObject information = JsonValue.readFrom(response.getRawResponse()).asObject().get("cover").asObject();

						new AsyncTask<String, Void, Bitmap>() {

							@Override
							protected Bitmap doInBackground(String... params) {

								try {
									URL picture_address = new URL(params[0]);
									Bitmap picture = BitmapFactory.decodeStream(picture_address.openConnection().getInputStream());

									AkiInternalStorageUtil.cacheUserCoverPhoto(context, currentUser.optString("id"), picture);
									return picture;

								} catch (MalformedURLException e) {
									Log.e(AkiApplication.TAG, "A problem happened while trying to query" +
											" user cover photo from Facebook.");
									e.printStackTrace();
									return null;
								} catch (IOException e) {
									Log.e(AkiApplication.TAG, "A problem happened while trying to query" +
											" user cover photo from Facebook.");
									e.printStackTrace();
									return null;
								}
							}

							@Override
							protected void onPostExecute(Bitmap picture) {

								if ( picture != null ){
									settingsCover.setImageBitmap(picture);
								}
								else{
									Log.e(AkiApplication.TAG, "A problem happened while trying to query user "+
											"cover photo from Facebook.");
								}
							}

						}.execute(information.get("source").asString());
					}
				}).executeAsync();
			}

			if ( !AkiInternalStorageUtil.isMandatorySettingMissing(context) ){
				callback.onSuccess(null);
			}			
		}
		catch(Exception any){
			callback.onFailure(any);
		}
	}

}