package com.lespi.aki;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.lespi.aki.utils.AkiInternalStorageUtil;

public class AkiMatchProfileActivity extends SherlockActivity {

	public static final String KEY_USER_ID = "user-id";
	public static final String API_LOCATION = "https://lespi-server.herokuapp.com/upload/";
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
        setContentView(R.layout.aki_match_profile_activity);
        
        Bundle extras = getIntent().getExtras();
        if ( extras == null ){
        	return;
        }
        final String userId = extras.getString(KEY_USER_ID);
        if ( userId == null ){
        	return;
        }
        final Context context = getApplicationContext();

        String userFullName = AkiInternalStorageUtil.getCachedUserFullName(context, userId);
        TextView headerView = (TextView) findViewById(R.id.com_lespi_aki_match_profile_header);
        TextView userFullNameView = (TextView) findViewById(R.id.com_lespi_aki_match_profile_fullname);
        if ( userFullName != null ){
        	headerView.setText(String.format(context.getString(R.string.com_lespi_aki_match_profile_header_pattern), userFullName.split(" ")[0]));
        	userFullNameView.setText(userFullName);
        }
        else{
        	headerView.setText(String.format(context.getString(R.string.com_lespi_aki_match_profile_header_pattern), "Match"));
        	userFullNameView.setVisibility(View.GONE);
        }

		String nickname = AkiInternalStorageUtil.getCachedUserNickname(context, userId);
		TextView userNickView = (TextView) findViewById(R.id.com_lespi_aki_match_profile_nickname);
		if ( nickname != null ){
			userNickView.setText(nickname);
		}
		else{
			userNickView.setVisibility(View.GONE);
		}
        
		final ImageView userCoverView = (ImageView) findViewById(R.id.com_lespi_aki_match_profile_cover);
		userCoverView.setAdjustViewBounds(false);
		userCoverView.setMinimumWidth(851);
		userCoverView.setMinimumHeight(315);
		userCoverView.setScaleType(ImageView.ScaleType.CENTER_CROP);
		Bitmap coverPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_cover);
		userCoverView.setImageBitmap(coverPlaceholder);
		Bitmap cachedCoverPhoto = AkiInternalStorageUtil.getCachedUserCoverPhoto(context, userId);
		if ( cachedCoverPhoto != null ){
			userCoverView.setImageBitmap(cachedCoverPhoto);
		}
		else {
			new AsyncTask<Void, Void, Bitmap>() {
				@Override
				protected Bitmap doInBackground(Void... params) {
					try {
						URL picture_address = new URL(API_LOCATION + context.getString(R.string.com_lespi_aki_data_user_coverphoto) + userId + ".png");
						Bitmap picture = BitmapFactory.decodeStream(picture_address.openConnection().getInputStream());
						AkiInternalStorageUtil.cacheUserCoverPhoto(context, userId, picture);
						return picture;
					} catch (MalformedURLException e) {
						Log.e(AkiApplication.TAG, "A problem happened while trying to query a user cover photo from our server.");
						e.printStackTrace();
						return null;
					} catch (IOException e) {
						Log.e(AkiApplication.TAG, "A problem happened while trying to query user cover photo from our server.");
						e.printStackTrace();
						return null;
					}
				}
				@Override
				protected void onPostExecute(Bitmap picture) {
					if ( picture != null ){
						userCoverView.setImageBitmap(picture);
					}
					else{
						Log.e(AkiApplication.TAG, "A problem happened while trying to query user cover photo from our server.");
					}
				}
			}.execute();
		}
		
		final String gender = AkiInternalStorageUtil.getCachedUserGender(context, userId);
		Bitmap genderPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_unknown_gender);
		ImageView userGenderView = (ImageView) findViewById(R.id.com_lespi_aki_match_profile_gender);
		userGenderView.setImageBitmap(genderPlaceholder);
		if (gender.equals("male")) {
			genderPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_male);
			userGenderView.setImageBitmap(genderPlaceholder);
		} else if (gender.equals("female")) {
			genderPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_female);
			userGenderView.setImageBitmap(genderPlaceholder);
		}
		userGenderView.setImageAlpha(255);
		
		final ImageView userPictureView = (ImageView) findViewById(R.id.com_lespi_aki_match_profile_picture);
    	Bitmap picturePlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_unknown_gender);
		userPictureView.setImageBitmap(AkiChatAdapter.getRoundedBitmap(picturePlaceholder));
		if (gender.equals("male")) {
			picturePlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_male);
			userPictureView.setImageBitmap(AkiChatAdapter.getRoundedBitmap(picturePlaceholder));
		} else if (gender.equals("female")) {
			picturePlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_female);
			userPictureView.setImageBitmap(AkiChatAdapter.getRoundedBitmap(picturePlaceholder));
		}
		
		Bitmap userPicture = AkiInternalStorageUtil.getCachedUserPicture(context, userId);
        if ( userPicture != null ){
        	userPictureView.setImageBitmap(userPicture);
        }
        else {
			new AsyncTask<Void, Void, Bitmap>() {
				@Override
				protected Bitmap doInBackground(Void... params) {
					try {
						URL picture_address = new URL(API_LOCATION + context.getString(R.string.com_lespi_aki_data_user_picture) + userId + ".png");
						Bitmap picture = BitmapFactory.decodeStream(picture_address.openConnection().getInputStream());
						AkiInternalStorageUtil.cacheUserPicture(context, userId, picture);
						return picture;
					} catch (MalformedURLException e) {
						Log.e(AkiApplication.TAG, "A problem happened while trying to query a user picture from our server.");
						e.printStackTrace();
						return null;
					} catch (IOException e) {
						Log.e(AkiApplication.TAG, "A problem happened while trying to query user picture from our server.");
						e.printStackTrace();
						return null;
					}
				}
				@Override
				protected void onPostExecute(Bitmap picture) {
					if ( picture != null ){
						userPictureView.setImageBitmap(picture);
					}
					else{
						Log.e(AkiApplication.TAG, "A problem happened while trying to query user picture from our server.");
					}
				}
			}.execute();
        }
        
        Button talkThroughMessengerBtn = (Button) findViewById(R.id.com_lespi_aki_match_profile_talk_through_messenger_btn);
        talkThroughMessengerBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				Log.wtf("FACEBOOK", "Trying to start Facebook Messenger chat with user " + userId + "!");
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("fb-messenger://user/" + userId))); //"100003279799340")));
				Log.wtf("FACEBOOK", "Surely didn't work!");
				//TODO take this out
			}
		});
    }
     
}