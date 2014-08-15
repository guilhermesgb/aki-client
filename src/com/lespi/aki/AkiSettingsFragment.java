package com.lespi.aki;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.model.GraphUser;
import com.facebook.widget.LoginButton;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.parse.internal.AsyncCallback;

public class AkiSettingsFragment extends SherlockFragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.aki_settings_fragment, container, false);
		LoginButton logoutButton = (LoginButton) view.findViewById(R.id.com_lespi_aki_main_settings_logout_btn);
		logoutButton.setFragment(this);
		return view;
	}

	public void refreshSettings(final Context context, final Session currentSession, final GraphUser currentUser, final AsyncCallback callback) {

		try{
			AkiInternalStorageUtil.setMandatorySettingsMissing(context, false);

			TextView settingsFullname = (TextView) getActivity().findViewById(R.id.com_lespi_aki_main_settings_fullname);
			settingsFullname.setText(currentUser.getName());

			final EditText nicknameBox = (EditText) getActivity().findViewById(R.id.com_lespi_aki_main_settings_nickname);
			String nickname = AkiInternalStorageUtil.getCachedNickname(context, currentUser.getId());

			final CheckBox anonymousCheck = (CheckBox) getActivity().findViewById(R.id.com_lespi_aki_main_settings_anonymous);

			anonymousCheck.setChecked(AkiInternalStorageUtil.anonymousSetting(context, currentUser.getId()));

			if ( nickname == null || nickname.trim().isEmpty() ){
				AkiInternalStorageUtil.setMandatorySettingsMissing(context, true);
				anonymousCheck.setChecked(true);
				anonymousCheck.setEnabled(false);
				SlidingMenu slidingMenu = ((AkiMainActivity) getActivity()).getSlidingMenu();
				slidingMenu.showMenu();
				slidingMenu.setSlidingEnabled(false);
				slidingMenu.setEnabled(false);
				CharSequence toastText = "You must choose a nickname! :)";
				Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
				toast.show();
			}
			else{
				nicknameBox.setText(nickname);
			}

			Button changeNicknameBtn = (Button) getActivity().findViewById(R.id.com_lespi_aki_main_settings_nickname_btn);
			if ( !changeNicknameBtn.hasOnClickListeners() ){

				changeNicknameBtn.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View view) {
						String newNickname = nicknameBox.getText().toString();
						String nickname = AkiInternalStorageUtil.getCachedNickname(context, currentUser.getId());
						if ( nickname != null ){
							nicknameBox.setText(nickname);
						}

						if ( newNickname.trim().isEmpty() ){
							CharSequence toastText = "Nickname cannot be blank!";
							Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
							toast.show();
							return;
						}
						AkiInternalStorageUtil.setCachedNickname(context, currentUser.getId(), newNickname);
						nicknameBox.setText(newNickname);
						if ( AkiInternalStorageUtil.mandatorySettingsMissing(context) ){

							SlidingMenu slidingMenu = ((AkiMainActivity) getActivity()).getSlidingMenu();
							slidingMenu.setSlidingEnabled(true);
							slidingMenu.setEnabled(true);
							slidingMenu.showContent();
							anonymousCheck.setEnabled(true);
							callback.onSuccess(null);
							CharSequence toastText = "Thanks for setting your nickname!";
							Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
							toast.show();
						}
						else{
							CharSequence toastText = "Nickname updated to " + newNickname + "!";
							Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
							toast.show();
						}
					}
				});
			}

			if ( !anonymousCheck.hasOnClickListeners() ){
				anonymousCheck.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View view) {
						AkiInternalStorageUtil.setAnonymousSetting(context, currentUser.getId(), anonymousCheck.isChecked());
					}
				});
			}

			final ImageView settingsPicture = (ImageView) getActivity().findViewById(R.id.com_lespi_aki_main_settings_picture);

			Bitmap picturePlaceholder = AkiChatAdapter.getRoundedBitmap(BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture));
			settingsPicture.setImageBitmap(picturePlaceholder);

			Bitmap cachedPicture = AkiInternalStorageUtil.getCachedUserPicture(context, currentUser.getId());
			if ( cachedPicture != null ){
				settingsPicture.setImageBitmap(cachedPicture);
			}
			else{

				Bundle params = new Bundle();
				params.putBoolean("redirect", false);
				params.putString("width", "143");
				params.putString("height", "143");
				new Request(currentSession, "/"+currentUser.getId()+"/picture", params, HttpMethod.GET,
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

						new AsyncTask<String, Void, Bitmap>() {

							@Override
							protected Bitmap doInBackground(String... params) {

								try {
									URL picture_address = new URL(params[0]);
									Bitmap picture = AkiChatAdapter.getRoundedBitmap(BitmapFactory.
											decodeStream(picture_address.openConnection().getInputStream()));

									AkiInternalStorageUtil.cacheUserPicture(context, currentUser.getId(), picture);
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
				}
						).executeAsync();
			}

			final ImageView settingsCover = (ImageView) getActivity().findViewById(R.id.com_lespi_aki_main_settings_cover);
			settingsCover.setAdjustViewBounds(false);
			settingsCover.setMinimumWidth(851);
			settingsCover.setMinimumHeight(315);
			settingsCover.setScaleType(ImageView.ScaleType.CENTER_CROP);

			Bitmap coverPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_cover);
			settingsCover.setImageBitmap(coverPlaceholder);

			Bitmap cachedCover = AkiInternalStorageUtil.getCachedUserCoverPhoto(context, currentUser.getId());
			if ( cachedCover != null ){
				settingsCover.setImageBitmap(cachedCover);
			}
			else{
				Bundle params = new Bundle();
				params.putBoolean("redirect", false);
				params.putString("width", "851");
				params.putString("height", "315");
				params.putString("fields", "cover");
				new Request(currentSession, "/"+currentUser.getId(), params, HttpMethod.GET, new Request.Callback() {
					public void onCompleted(Response response) {
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

									AkiInternalStorageUtil.cacheUserCoverPhoto(context, currentUser.getId(), picture);
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

			if ( !AkiInternalStorageUtil.mandatorySettingsMissing(context) ){
				callback.onSuccess(null);
			}			
		}
		catch(Exception any){
			callback.onFailure(any);
		}
	}
}