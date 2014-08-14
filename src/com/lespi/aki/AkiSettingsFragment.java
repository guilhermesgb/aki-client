package com.lespi.aki;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutionException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.actionbarsherlock.app.SherlockFragment;
import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.model.GraphUser;
import com.facebook.widget.LoginButton;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiInternalStorageUtil;

public class AkiSettingsFragment extends SherlockFragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.aki_settings_fragment, container, false);
		LoginButton logoutButton = (LoginButton) view.findViewById(R.id.com_lespi_aki_main_settings_logout_btn);
		logoutButton.setFragment(this);
		return view;
	}

	public void refreshSettings(final Context context, final Session currentSession, final GraphUser currentUser) {

		final ImageView settingsPicture = (ImageView) getActivity().findViewById(R.id.com_lespi_aki_main_settings_picture);
		settingsPicture.setImageBitmap(AkiChatAdapter
				.getRoundedBitmap(BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture)));

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
						
						try {
							Bitmap picture = new AsyncTask<String, Void, Bitmap>() {

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

							}.execute(information.get("url").asString()).get();
							if ( picture != null ){
								settingsPicture.setImageBitmap(picture);
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

		final ImageView settingsCover = (ImageView) getActivity().findViewById(R.id.com_lespi_aki_main_settings_cover);
		settingsCover.setImageBitmap(BitmapFactory.decodeResource(context.getResources(), R.drawable.no_cover));
		settingsCover.setAdjustViewBounds(false);
		settingsCover.setMinimumWidth(851);
		settingsCover.setMinimumHeight(315);
		settingsCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
		
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

					try {
						Bitmap picture = new AsyncTask<String, Void, Bitmap>() {

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

						}.execute(information.get("source").asString()).get();
						if ( picture != null ){
							settingsCover.setImageBitmap(picture);
						}
						else{
							Log.e(AkiApplication.TAG, "A problem happened while trying to query user "+
									"cover photo from Facebook.");
						}
					} catch (InterruptedException e) {
						Log.e(AkiApplication.TAG, "A problem happened while trying to query user "+
								"cover photo from Facebook.");
						e.printStackTrace();
					} catch (ExecutionException e) {
						Log.e(AkiApplication.TAG, "A problem happened while trying to query user "+
								"cover photo from Facebook.");
						e.printStackTrace();
					}
				}
			}).executeAsync();
		}
	}
}