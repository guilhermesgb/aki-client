package com.lespi.aki;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.PriorityQueue;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiServerUtil;
import com.parse.internal.AsyncCallback;

public class AkiPrivateChatActivity extends SherlockActivity {

	public static final String TAG = "__AkiPrivateChatActivity__";
	public static final String KEY_USER_ID = "user-id";
	public static final String API_LOCATION = "https://lespi-server.herokuapp.com/upload/";
	
	public String matchUserId = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		Log.v(AkiPrivateChatActivity.TAG, "AkiPrivateChatActivity$onCreate");
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.aki_private_chat_activity);
        
        Bundle extras = getIntent().getExtras();
        if ( extras == null ){
        	return;
        }
        final String userId = extras.getString(KEY_USER_ID);
        if ( userId == null ){
        	return;
        }
        
        this.matchUserId = userId;
        
        AkiApplication.setCurrentPrivateId(userId);
		AkiApplication.isNowInForeground();
        
        AkiInternalStorageUtil.setPrivateChatRoomUnreadCounter(getApplicationContext(),
        		AkiServerUtil.buildPrivateChatId(getApplicationContext(), userId), 0);
        
        final Context context = getApplicationContext();

        String userFullName = AkiInternalStorageUtil.getCachedUserFullName(context, userId);
        TextView headerView = (TextView) findViewById(R.id.com_lespi_aki_private_chat_header);
        if ( userFullName != null ){
        	headerView.setText(String.format(context.getString(R.string.com_lespi_aki_private_chat_header_pattern), userFullName));
        }
        else{
        	headerView.setText(String.format(context.getString(R.string.com_lespi_aki_private_chat_header_pattern), "Match"));
        	
        }
        final ImageView userPictureView = (ImageView) findViewById(R.id.com_lespi_aki_private_chat_user_picture);
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
						Log.e(AkiPrivateChatActivity.TAG, "A problem happened while trying to query a user picture from our server.");
						e.printStackTrace();
						return null;
					} catch (IOException e) {
						Log.e(AkiPrivateChatActivity.TAG, "A problem happened while trying to query user picture from our server.");
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
						Log.e(AkiPrivateChatActivity.TAG, "A problem happened while trying to query user picture from our server.");
					}
				}
			}.execute();
        }
        final ImageButton sendMessageBtn = (ImageButton) findViewById(R.id.com_lespi_aki_private_chat_send_btn);
        sendMessageBtn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View view) {

				if ( !AkiMainActivity.isLocationProviderEnabled(getApplicationContext()) ){
					CharSequence toastText = getApplicationContext().getText(R.string.com_lespi_aki_toast_please_enable_gps);
					Toast toast = Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_SHORT);
					toast.show();
					onResume();
					return;
				}

				final EditText chatBox = (EditText) findViewById(R.id.com_lespi_aki_private_chat_input);
				final String message = chatBox.getText().toString().trim();
				if ( !message.isEmpty() ){
					chatBox.setText("");
					AkiServerUtil.sendPrivateMessage(getApplicationContext(), message, userId, new AsyncCallback() {

						@Override
						public void onSuccess(Object response) {
							Log.v(AkiPrivateChatActivity.TAG, "Message: " + message + " sent!");
							ListView listView = (ListView) findViewById(R.id.com_lespi_aki_private_messages_list);
							AkiPrivateChatAdapter chatAdapter = AkiPrivateChatAdapter.getInstance(context);
							listView.setAdapter(chatAdapter);
							listView.setSelection(chatAdapter.getCount() - 1);
							listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
							listView.setWillNotCacheDrawing(true);
					        AkiServerUtil.restartGettingPrivateMessages(context, userId);
						}

						@Override
						public void onFailure(Throwable failure) {
							Log.e(AkiPrivateChatActivity.TAG, "You could not send message!");
							failure.printStackTrace();
							CharSequence toastText = getApplicationContext().getText(R.string.com_lespi_aki_toast_message_not_sent);
							Toast toast = Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_SHORT);
							toast.show();
						}

						@Override
						public void onCancel() {
							CharSequence toastText;
							if ( AkiApplication.SERVER_DOWN ){
								toastText = getApplicationContext().getText(R.string.com_lespi_aki_toast_server_down);
							}
							else{
								toastText = getApplicationContext().getText(R.string.com_lespi_aki_toast_no_internet_connection);
							}
							Toast toast = Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_SHORT);
							toast.show();
							Log.e(AkiPrivateChatActivity.TAG, "Endpoint:sendMessage callback canceled.");
							onResume();
						}
					});
				}
			}
		});
        refreshReceivedMessages(userId);
        AkiServerUtil.getPrivateMessages(context, userId);
    }
    
    private void refreshReceivedMessages(final String userId) {
    	final Activity activity = this;
		final Context context = activity.getApplicationContext();

		final AkiPrivateChatAdapter chatAdapter = AkiPrivateChatAdapter.getInstance(activity.getApplicationContext());
		final ListView listView = (ListView) activity.findViewById(R.id.com_lespi_aki_private_messages_list);
		listView.setSelection(chatAdapter.getCount() - 1);
		listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
		listView.setWillNotCacheDrawing(true);

		new AsyncTask<Void, Void, List<JsonObject>>(){

			@Override
			protected List<JsonObject> doInBackground(Void... params) {

				PriorityQueue<JsonObject> messages = AkiInternalStorageUtil.retrieveMessages(context, AkiServerUtil.buildPrivateChatId(context, userId));
				return AkiChatAdapter.toJsonObjectList(messages);
			}

			@Override
			public void onPostExecute(List<JsonObject> messages){

				chatAdapter.clear();
				if ( messages != null ){
					chatAdapter.addAll(messages);
				}
				ListView listView = (ListView) activity.findViewById(R.id.com_lespi_aki_private_messages_list);
				listView.setAdapter(chatAdapter);
				listView.setSelection(chatAdapter.getCount() - 1);
				listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
				listView.setWillNotCacheDrawing(true);

			}
		}.execute();
	}
    
	@Override
	protected void onStart(){
		Log.v(AkiPrivateChatActivity.TAG, "AkiPrivateChatActivity$onStart");
		AkiApplication.setCurrentPrivateId(this.matchUserId);
		AkiApplication.isNowInForeground();
		super.onResume();
	}

	@Override
	protected void onPause(){
		Log.v(AkiPrivateChatActivity.TAG, "AkiPrivateChatActivity$onPause");
		AkiServerUtil.stopGettingPrivateMessages(getApplicationContext());
		AkiApplication.setCurrentPrivateId(null);
		AkiApplication.isNowInBackground();
		super.onPause();
	}

    @Override
    protected void onStop() {
		Log.v(AkiPrivateChatActivity.TAG, "AkiPrivateChatActivity$onStop");
    	AkiServerUtil.stopGettingPrivateMessages(getApplicationContext());
		AkiApplication.setCurrentPrivateId(null);
		AkiApplication.isNowInBackground();
		super.onStop();
    }

    @Override
    protected void onDestroy() {
		Log.v(AkiPrivateChatActivity.TAG, "AkiPrivateChatActivity$onDestroy");
    	AkiServerUtil.stopGettingPrivateMessages(getApplicationContext());
		AkiApplication.setCurrentPrivateId(null);
		AkiApplication.isNowInBackground();
		super.onDestroy();
    }

}