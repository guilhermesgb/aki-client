package com.lespi.aki;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.lespi.aki.utils.AkiInternalStorageUtil;

public class AkiMatchProfileActivity extends SherlockActivity {

	public static final String KEY_USER_ID = "user-id";
	
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
        String userId = extras.getString(KEY_USER_ID);
        if ( userId == null ){
        	return;
        }
        Context context = getApplicationContext();

        String userFullName = AkiInternalStorageUtil.getCachedUserFullName(context, userId);
        TextView headerView = (TextView) findViewById(R.id.com_lespi_aki_match_profile_header);
        headerView.setText(String.format(context.getString(R.string.com_lespi_aki_match_profile_header_pattern), userFullName.split(" ")[0]));
        TextView userFullNameView = (TextView) findViewById(R.id.com_lespi_aki_match_profile_fullname);
        userFullNameView.setText(userFullName);

		String nickname = AkiInternalStorageUtil.getCachedUserNickname(context, userId);
		TextView userNickView = (TextView) findViewById(R.id.com_lespi_aki_match_profile_nickname);
		if ( nickname != null ){
			userNickView.setText(nickname);
		}
		else{
			userNickView.setVisibility(View.GONE);
		}
        
		ImageView userCoverView = (ImageView) findViewById(R.id.com_lespi_aki_match_profile_cover);
		userCoverView.setAdjustViewBounds(false);
		userCoverView.setMinimumWidth(851);
		userCoverView.setMinimumHeight(315);
		userCoverView.setScaleType(ImageView.ScaleType.CENTER_CROP);
		Bitmap coverPlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_cover);
		userCoverView.setImageBitmap(coverPlaceholder);
        
		String gender = AkiInternalStorageUtil.getCachedUserGender(context, userId);
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
		
		ImageView userPictureView = (ImageView) findViewById(R.id.com_lespi_aki_match_profile_picture);
        Bitmap userPicture = AkiInternalStorageUtil.getCachedUserPicture(context, userId);
        if ( userPicture != null ){
        	userPictureView.setImageBitmap(userPicture);
        }
        else {
			Bitmap picturePlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_unknown_gender);
			userPictureView.setImageBitmap(AkiChatAdapter.getRoundedBitmap(picturePlaceholder));
			if (gender.equals("male")) {
				picturePlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_male);
				userPictureView.setImageBitmap(AkiChatAdapter.getRoundedBitmap(picturePlaceholder));
			} else if (gender.equals("female")) {
				picturePlaceholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_female);
				userPictureView.setImageBitmap(AkiChatAdapter.getRoundedBitmap(picturePlaceholder));
			}        	
        }
    }
     
}