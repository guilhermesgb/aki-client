package com.lespi.aki;

import java.util.List;
import java.util.PriorityQueue;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageButton;
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
	public static final String API_LOCATION = "https://lespi-server.herokuapp.com/upload/";

	public String matchUserId = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(AkiPrivateChatActivity.TAG, "AkiPrivateChatActivity$onCreate");

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.aki_private_chat_activity);

		String userId = AkiInternalStorageUtil.getLastPrivateMessageSender(getApplicationContext());
		refreshPrivateChat(userId);
	}

	public void refreshPrivateChat(final String userId){

		final Context context = getApplicationContext();
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(AkiApplication.INCOMING_PRIVATE_MESSAGE_NOTIFICATION_ID);
		
		AkiMutualAdapter mutualAdapter = AkiMutualAdapter.getInstance(context);
		mutualAdapter.notifyDataSetChanged();

		this.matchUserId = userId;

		AkiApplication.setCurrentPrivateId(userId);
		AkiApplication.isNowInForeground();

		final String currentUserId = AkiInternalStorageUtil.getCurrentUser(getApplicationContext());
		
		final String privateChatRoom = AkiServerUtil.buildPrivateChatId(context, userId);

		AkiInternalStorageUtil.setPrivateChatRoomUnreadCounter(context, privateChatRoom, 0);

		ImageButton backBtn = (ImageButton) findViewById(R.id.com_lespi_aki_private_chat_back_btn);
		backBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				finish();
				overridePendingTransition(R.anim.hold, R.anim.fade_out);
			}
		});

		final ImageButton anonymousBtn = (ImageButton) findViewById(R.id.com_lespi_aki_private_chat_anonymous_btn);
		if ( AkiInternalStorageUtil.viewGetPrivateChatRoomAnonymousSetting(context, privateChatRoom, currentUserId) ){
			anonymousBtn.setImageDrawable(context.getResources().getDrawable(R.drawable.icon_anonymous));
		}
		else {
			anonymousBtn.setImageDrawable(context.getResources().getDrawable(R.drawable.icon_identified));
		}

		String headerName = AkiInternalStorageUtil.getCachedUserFullName(context, userId);
		if ( AkiInternalStorageUtil.viewGetPrivateChatRoomAnonymousSetting(context, privateChatRoom, userId) ){
			headerName = AkiInternalStorageUtil.getCachedUserNickname(context, userId);
		}
		TextView headerView = (TextView) findViewById(R.id.com_lespi_aki_private_chat_header);
		TextView status = (TextView) findViewById(R.id.com_lespi_aki_private_chat_status);
		if ( headerName != null ){
			headerView.setText(headerName);
			status.setText(String.format(context.getString(R.string.com_lespi_aki_private_chat_status_pattern), headerName));
		}
		else{
			headerView.setText(context.getString(R.string.com_lespi_aki_private_chat_header_text));
			status.setText(context.getString(R.string.com_lespi_aki_private_chat_status_pattern_unknown));
		}

		refreshReceivedMessages(userId, privateChatRoom);
		
		AkiServerUtil.sendStealthPresenceToServer(context, currentUserId, new AsyncCallback() {
			@Override
			public void onSuccess(Object response) {
				anonymousBtn.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View view) {
						if ( AkiInternalStorageUtil.viewGetPrivateChatRoomAnonymousSetting(context, privateChatRoom, currentUserId) ){
							anonymousBtn.setImageDrawable(context.getResources().getDrawable(R.drawable.icon_identified));
							AkiInternalStorageUtil.setPrivateChatRoomAnonymousSetting(context, privateChatRoom, currentUserId, false);
							AkiServerUtil.sendPrivateMessage(context, null, userId, null);
						}
						else {
							anonymousBtn.setImageDrawable(context.getResources().getDrawable(R.drawable.icon_anonymous));
							AkiInternalStorageUtil.setPrivateChatRoomAnonymousSetting(context, privateChatRoom, currentUserId, true);
							AkiServerUtil.sendPrivateMessage(context, null, userId, null);
						}
						AkiPrivateChatAdapter chatAdapter = AkiPrivateChatAdapter.getInstance(context, privateChatRoom);
						chatAdapter.notifyDataSetChanged();
					}
				});


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
									AkiPrivateChatAdapter chatAdapter = AkiPrivateChatAdapter.getInstance(context, privateChatRoom);
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
				AkiServerUtil.getPrivateMessages(context, userId);				
			}
			@Override
			public void onFailure(Throwable failure) {
				CharSequence toastText = getApplicationContext().getText(R.string.com_lespi_aki_toast_server_down);
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
			}
		});
	}

	private void refreshReceivedMessages(final String userId, final String privateChatRoom) {
		final Activity activity = this;
		final Context context = activity.getApplicationContext();

		final AkiPrivateChatAdapter chatAdapter = AkiPrivateChatAdapter.getInstance(activity.getApplicationContext(), privateChatRoom);
		final ListView listView = (ListView) activity.findViewById(R.id.com_lespi_aki_private_messages_list);
		listView.setSelection(chatAdapter.getCount() - 1);
		listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
		listView.setWillNotCacheDrawing(true);

		new AsyncTask<Void, Void, List<JsonObject>>(){
			@Override
			protected List<JsonObject> doInBackground(Void... params) {
				PriorityQueue<JsonObject> messages = AkiInternalStorageUtil.retrieveMessages(context, privateChatRoom);
				return AkiChatAdapter.toJsonObjectList(messages);
			}
			@Override
			public void onPostExecute(List<JsonObject> messages){
				chatAdapter.clear();
				if ( messages != null ){
					chatAdapter.addAll(messages);
				}
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

		String userId = AkiInternalStorageUtil.getLastPrivateMessageSender(getApplicationContext());
		if ( userId != null ){
			refreshPrivateChat(userId);
		}
		
		super.onStart();
	}

	@Override
	protected void onResume(){
		Log.v(AkiPrivateChatActivity.TAG, "AkiPrivateChatActivity$onResume");
		AkiApplication.setCurrentPrivateId(this.matchUserId);
		AkiApplication.isNowInForeground();

		String userId = AkiInternalStorageUtil.getLastPrivateMessageSender(getApplicationContext());
		if ( userId != null ){
			refreshPrivateChat(userId);
		}
		
		super.onStart();
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

	@Override
	public void onBackPressed() {
		finish();
		overridePendingTransition(R.anim.hold, R.anim.fade_out);
	}
}