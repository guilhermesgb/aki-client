package com.lespi.aki;

import java.util.List;
import java.util.PriorityQueue;

import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.facebook.AccessToken;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.lespi.aki.AkiApplication.GroupChatMode;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiHttpRequestUtil;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiServerUtil;
import com.parse.ParseInstallation;
import com.parse.internal.AsyncCallback;

public class AkiChatFragment extends SherlockFragment {

	public static final String TAG = "__AkiChatFragment__";
	public static final String KEY_USER_ID = "user-id";

	private boolean seenSplash = false;


	public void setSeenSplash(boolean seenSplash) {
		this.seenSplash = seenSplash;
	}

	private static AkiChatFragment instance;

	public AkiChatFragment(){
		instance = this;
	}

	public static AkiChatFragment getInstance(){
		if ( instance == null ){
			instance = new AkiChatFragment();
		}
		return instance;
	}

	private void onSessionStateChange(final AccessToken session) {
		Log.v(AkiChatFragment.TAG, "AkiChatFragment$onSessionStateChange");
		final AkiMainActivity activity = (AkiMainActivity) getActivity();
		if ( activity == null ){
			Log.d(AkiChatFragment.TAG, "Facebook session callback called but there is no MainActivity alive, so ignored session change event.");
			return;
		}

		if ( !AkiHttpRequestUtil.isConnectedToTheInternet(activity.getApplicationContext()) ){
			CharSequence toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_no_internet_connection);
			Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
			toast.show();
			activity.onResume();
			return;
		}
		else if ( !AkiMainActivity.isLocationProviderEnabled(activity.getApplicationContext()) ){
			CharSequence toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_cannot_determine_location);
			Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
			toast.show();
			activity.onResume();
			return;
		}

		AkiApplication.isNowInForeground();

		GraphRequest.newMeRequest(session, new GraphRequest.GraphJSONObjectCallback() {

			@Override
			public void onCompleted(final JSONObject currentUser, GraphResponse response) {
				if ( currentUser != null ){

					switchToChatArea(activity, currentUser.optString("id"));
					if ( AkiInternalStorageUtil.getCurrentChatRoom(activity.getApplicationContext()) != null ){
						refreshReceivedMessages(activity, session, currentUser);
					}
					final ImageButton sendMessageBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_chat_send_btn);
					sendMessageBtn.setEnabled(false);

					final OnClickListener exitBtnClickListener = new OnClickListener() {

						@Override
						public void onClick(View view) {

							new AlertDialog.Builder(activity)
							.setIcon(R.drawable.icon_exit)
							.setTitle(R.string.com_lespi_aki_main_chat_exit_confirm_title)
							.setMessage(R.string.com_lespi_aki_main_chat_exit_confirm_text)
							.setPositiveButton(R.string.com_lespi_aki_confirm_yes, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									AkiServerUtil.sendExitToServer(activity.getApplicationContext(), new AsyncCallback() {

										@Override
										public void onSuccess(Object response) {
											AkiServerUtil.leaveChatRoom(activity.getApplicationContext(), currentUser.optString("id"));
											activity.stopPeriodicLocationUpdates();
											activity.removeGeofence();

											AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(activity.getApplicationContext());
											chatAdapter.clear();

											clearMembersList(activity);
											AkiInternalStorageUtil.setAloneSetting(activity.getApplicationContext(), false);

											String contentTitle = activity.getApplicationContext().getString(R.string.com_lespi_aki_notif_exit_title);
											String contentText = activity.getApplicationContext().getString(R.string.com_lespi_aki_notif_exit_text);

											AkiApplication.isNotLoggedIn();

											Notification.Builder notifyBuilder = new Notification.Builder(activity.getApplicationContext())
											.setSmallIcon(R.drawable.notification_icon)
											.setContentTitle(contentTitle)
											.setContentText(contentText)
											.setTicker(contentTitle)
											.setContentIntent(PendingIntent.getActivity(getActivity(), 0, new Intent(), 0))
											.setOnlyAlertOnce(true)
											.setAutoCancel(true);
											Notification.InboxStyle notifyBigBuilder = new Notification.InboxStyle(notifyBuilder);
											String[] contentLines = contentText.split("\n");
											for ( int i=0; i<contentLines.length; i++ ){
												notifyBigBuilder.addLine(contentLines[i]);
											}
											notifyBigBuilder.setBigContentTitle(contentTitle);

											NotificationManager notificationManager = (NotificationManager)
													activity.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
											notificationManager.notify(AkiApplication.EXITED_ROOM_NOTIFICATION_ID, notifyBigBuilder.build());

											Intent intent = new Intent(Intent.ACTION_MAIN);
											intent.addCategory(Intent.CATEGORY_HOME);
											getActivity().startActivity(intent);
											ParseInstallation installation = ParseInstallation.getCurrentInstallation();
											installation.put("uid", "not_logged");
											installation.saveInBackground();
											getActivity().finish();
										}

										@Override
										public void onFailure(Throwable failure) {
											Log.e(AkiChatFragment.TAG, "A problem happened while exiting chat room!");
											failure.printStackTrace();
										}

										@Override
										public void onCancel() {
											CharSequence toastText;
											if ( AkiApplication.SERVER_DOWN ){
												toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_server_down);
											}
											else{
												toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_no_internet_connection);
											}
											Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
											toast.show();
											activity.onResume();
											Log.e(AkiChatFragment.TAG, "Exiting chat room canceled.");
										}
									});
								}
							})
							.setNegativeButton(R.string.com_lespi_aki_confirm_no, new DialogInterface.OnClickListener(){
								@Override
								public void onClick(DialogInterface dialog, int which) {}
							})
							.show();
						}
					};

					final OnClickListener skipBtnClickListener = new OnClickListener() {

						@Override
						public void onClick(View view) {

							new AlertDialog.Builder(activity)
							.setIcon(R.drawable.icon_skip)
							.setTitle(R.string.com_lespi_aki_main_chat_skip_confirm_title)
							.setMessage(R.string.com_lespi_aki_main_chat_skip_confirm_text)
							.setPositiveButton(R.string.com_lespi_aki_confirm_yes, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									AkiServerUtil.sendSkipToServer(activity.getApplicationContext(), new AsyncCallback() {

										@Override
										public void onSuccess(Object response) {

											AkiInternalStorageUtil.setAnonymousSetting(activity.getApplicationContext(), currentUser.optString("id"), true);
											AkiServerUtil.leaveChatRoom(activity.getApplicationContext(), currentUser.optString("id"));
											activity.removeGeofence();

											AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(activity.getApplicationContext());
											chatAdapter.clear();

											clearMembersList(activity);
											AkiInternalStorageUtil.setAloneSetting(activity.getApplicationContext(), false);

											CharSequence toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_skipped_chat);
											Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
											toast.show();

											TextView status = (TextView) activity.findViewById(R.id.com_lespi_aki_main_chat_status);
											status.setText(activity.getApplicationContext().getString(R.string.com_lespi_aki_main_chat_status_entering));

											activity.onResume();
										}

										@Override
										public void onFailure(Throwable failure) {
											Log.e(AkiChatFragment.TAG, "A problem happened while skipping chat room!");
											failure.printStackTrace();
										}

										@Override
										public void onCancel() {
											CharSequence toastText;
											if ( AkiApplication.SERVER_DOWN ){
												toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_server_down);
											}
											else{
												toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_no_internet_connection);
											}
											Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
											toast.show();
											Log.e(AkiChatFragment.TAG, "Skipping chat room canceled.");
											activity.onResume();
										}
									});
								}
							})
							.setNegativeButton(R.string.com_lespi_aki_confirm_no, new DialogInterface.OnClickListener(){
								@Override
								public void onClick(DialogInterface dialog, int which) {}
							})
							.show();
						}
					};

					final GroupChatMode currentChatMode = AkiApplication.getChatMode(activity.getApplicationContext());
					final GroupChatMode otherChatMode = currentChatMode == GroupChatMode.LOCAL ? GroupChatMode.GLOBAL : GroupChatMode.LOCAL;

					final OnClickListener globalBtnClickListener = new OnClickListener() {

						@Override
						public void onClick(View view) {

							Context context = activity.getApplicationContext();

							if ( !AkiInternalStorageUtil.getAloneSetting(context) && AkiApplication.getChatMode(context) == GroupChatMode.LOCAL ){
								CharSequence toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_cannot_be_global);
								Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
								toast.show();									
								return;
							}

							new AlertDialog.Builder(activity)
							.setIcon(otherChatMode.icon)
							.setTitle(String.format(getResources().getString(R.string.com_lespi_aki_main_chat_global_confirm_title), otherChatMode.toString()))
							.setMessage(String.format(getResources().getString(R.string.com_lespi_aki_main_chat_global_confirm_text), otherChatMode.toString()))
							.setPositiveButton(R.string.com_lespi_aki_confirm_yes, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {

									if ( !AkiHttpRequestUtil.isConnectedToTheInternet(activity.getApplicationContext()) ){

										activity.getSlidingMenu().showContent();
										activity.getSlidingMenu().setSlidingEnabled(false);
										CharSequence toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_no_internet_connection);
										if ( AkiApplication.SERVER_DOWN ){
											toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_server_down);
										}
										Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
										toast.show();
										activity.onResume();
										return;
									}

									switchGroupChatState(activity);

									AkiInternalStorageUtil.setAnonymousSetting(activity.getApplicationContext(), currentUser.optString("id"), true);
									AkiServerUtil.leaveChatRoom(activity.getApplicationContext(), currentUser.optString("id"));
									activity.removeGeofence();

									AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(activity.getApplicationContext());
									chatAdapter.clear();

									clearMembersList(activity);

									CharSequence toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_kicked_chat);
									Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
									toast.show();

									TextView status = (TextView) activity.findViewById(R.id.com_lespi_aki_main_chat_status);
									status.setText(activity.getApplicationContext().getString(R.string.com_lespi_aki_main_chat_status_entering));

									activity.onResume();
								}
							})
							.setNegativeButton(R.string.com_lespi_aki_confirm_no, new DialogInterface.OnClickListener(){
								@Override
								public void onClick(DialogInterface dialog, int which) {}
							})
							.show();
						}
					};

					final ImageButton exitChatBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_chat_exit_btn);
					exitChatBtn.setEnabled(false);
					exitChatBtn.setOnClickListener(exitBtnClickListener);
					final ImageButton skipChatBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_chat_skip_btn);
					skipChatBtn.setEnabled(false);
					skipChatBtn.setOnClickListener(skipBtnClickListener);
					final ImageButton globalChatBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_chat_global_btn);
					globalChatBtn.setEnabled(false);
					globalChatBtn.setImageDrawable(getResources().getDrawable(otherChatMode.icon));
					globalChatBtn.setImageAlpha(128);
					globalChatBtn.setOnClickListener(globalBtnClickListener);

					sendMessageBtn.setEnabled(false);
					sendMessageBtn.setOnClickListener(new OnClickListener() {

						@Override
						public void onClick(View view) {

							if ( !AkiMainActivity.isLocationProviderEnabled(activity.getApplicationContext()) ){
								CharSequence toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_please_enable_gps);
								Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
								toast.show();
								activity.onResume();
								return;
							}

							final EditText chatBox = (EditText) activity.findViewById(R.id.com_lespi_aki_main_chat_input);
							final String message = chatBox.getText().toString().trim();
							if ( !message.isEmpty() ){
								chatBox.setText("");
								AkiServerUtil.sendMessage(activity.getApplicationContext(), message, new AsyncCallback() {

									@Override
									public void onSuccess(Object response) {
										Log.i(AkiChatFragment.TAG, "Message: " + message + " sent!");
									}

									@Override
									public void onFailure(Throwable failure) {
										Log.e(AkiChatFragment.TAG, "You could not send message!");
										failure.printStackTrace();
										CharSequence toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_message_not_sent);
										Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
										toast.show();
									}

									@Override
									public void onCancel() {
										CharSequence toastText;
										if ( AkiApplication.SERVER_DOWN ){
											toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_server_down);
										}
										else{
											toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_no_internet_connection);
										}
										Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
										toast.show();
										Log.e(AkiChatFragment.TAG, "Endpoint:sendMessage callback canceled.");
										activity.onResume();
									}
								});
							}
						}
					});

					refreshSettings(activity, session, currentUser, new AsyncCallback(){

						@Override
						public void onSuccess(Object response) {

							ImageButton openSettingsBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_chat_settings_btn);
							openSettingsBtn.setEnabled(true);
							openSettingsBtn.setOnClickListener(new OnClickListener() {

								@Override
								public void onClick(View view) {

									if ( !AkiMainActivity.isLocationProviderEnabled(activity.getApplicationContext()) ){
										activity.onResume();
										return;
									}
									activity.getSlidingMenu().showMenu(true);
								}
							});

							ImageButton openMutualBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_chat_mutual_btn);
							openMutualBtn.setEnabled(true);
							openMutualBtn.setOnClickListener(new OnClickListener() {

								@Override
								public void onClick(View view) {

									if ( !AkiMainActivity.isLocationProviderEnabled(activity.getApplicationContext()) ){
										activity.onResume();
										return;
									}
									activity.getSlidingMenu().showSecondaryMenu(true);
								}
							});

							final AkiMutualAdapter mutualAdapter = AkiMutualAdapter.getInstance(activity.getApplicationContext());
							mutualAdapter.setActivity(getActivity());
							mutualAdapter.setNotifyOnChange(false);

							activity.getSlidingMenu().setSlidingEnabled(true);

							AkiServerUtil.sendPresenceToServer(activity.getApplicationContext(), currentUser.optString("id"), new AsyncCallback() {

								@Override
								public void onSuccess(Object response) {

									AkiServerUtil.makeSureUserPictureIsUploaded(activity.getApplicationContext(), currentUser.optString("id"));
									AkiServerUtil.makeSureCoverPhotoIsUploaded(activity.getApplicationContext(), currentUser.optString("id"));

									AkiServerUtil.getMutualInterests(activity.getApplicationContext());
									mutualAdapter.setNotifyOnChange(true);

									exitChatBtn.setEnabled(true);
									skipChatBtn.setEnabled(true);
									if ( AkiApplication.getChatMode(activity.getApplicationContext()) == GroupChatMode.GLOBAL ){
										globalChatBtn.setEnabled(true);
										globalChatBtn.setImageAlpha(255);
									}

									JsonObject responseJSON = (JsonObject) response;
									final JsonValue chatRoomId = responseJSON.get("chat_room");
									if ( chatRoomId != null && !chatRoomId.isNull() ){
										AkiServerUtil.enterChatRoom(activity, currentUser.optString("id"), chatRoomId.asString(), responseJSON.get("should_not_be_anonymous") == null);
										final ImageButton anonymousCheck = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_settings_anonymous_btn);
										final TextView anonymousInfo = (TextView) activity.findViewById(R.id.com_lespi_aki_main_settings_anonymous_text);
										if ( anonymousCheck != null ){
											if ( AkiInternalStorageUtil.getAnonymousSetting(activity.getApplicationContext(), currentUser.optString("id")) ){
												anonymousCheck.setImageDrawable(activity.getApplicationContext().getResources().getDrawable(R.drawable.icon_anonymous));
												anonymousInfo.setText(activity.getApplicationContext().getString(R.string.com_lespi_aki_main_settings_privacy_identify_yourself));
											}
											else {
												anonymousCheck.setImageDrawable(activity.getApplicationContext().getResources().getDrawable(R.drawable.icon_identified));
												anonymousInfo.setText(activity.getApplicationContext().getString(R.string.com_lespi_aki_main_settings_privacy_no_longer_anonymous));
											}
										}
										activity.setGeofence();
										refreshReceivedMessages(activity, session, currentUser);
										sendMessageBtn.setEnabled(true);
										AkiServerUtil.getMessages(activity.getApplicationContext());
									}

									if ( activity.locationServicesConnected()
											&& AkiApplication.getChatMode(activity.getApplicationContext()) == GroupChatMode.LOCAL ){
										activity.startPeriodicLocationUpdates();
									}
								}

								@Override
								public void onFailure(Throwable failure) {
									Log.e(AkiChatFragment.TAG, "Could not send presence to server.");
									failure.printStackTrace();
									refreshReceivedMessages(activity, session, currentUser);
								}

								@Override
								public void onCancel() {
									CharSequence toastText;
									if ( AkiApplication.SERVER_DOWN ){
										toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_server_down);
									}
									else{
										toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_no_internet_connection);
									}
									Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
									toast.show();
									Log.e(AkiChatFragment.TAG, "Endpoint:sendPresenceToServer callback canceled.");
									activity.onResume();
								}
							});
						}

						@Override
						public void onFailure(Throwable failure) {
							Log.e(AkiChatFragment.TAG, "Could not refresh settings.");
							failure.printStackTrace();
							refreshReceivedMessages(activity, session, currentUser);
						}

						@Override
						public void onCancel() {
							Log.e(AkiChatFragment.TAG, "Refresh of settings canceled.");
							refreshReceivedMessages(activity, session, currentUser);
						}
					});
				}


			}

		}).executeAsync();
	}

	private void switchToLoginArea(final AkiMainActivity activity, final String currentUserId, boolean showSplash){
		if ( showSplash && (!seenSplash) ){
			Intent intent = new Intent(activity, AkiSplashActivity.class);
			activity.startActivity(intent);
			activity.overridePendingTransition(R.anim.hold, R.anim.fade_in);
			activity.finish();
			activity.overridePendingTransition(R.anim.hold, R.anim.fade_out);
			return;
		}

		if ( activity.locationServicesConnected() ){
			activity.stopPeriodicLocationUpdates();
			activity.removeGeofence();
		}

		AkiServerUtil.stopGettingMessages(activity.getApplicationContext());

		final Context context = activity.getApplicationContext();

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

	private void switchToChatArea(AkiMainActivity activity, String currentUserId){
		AkiApplication.isLoggedIn();

		ParseInstallation installation = ParseInstallation.getCurrentInstallation();
		installation.put("uid", currentUserId);
		installation.saveInBackground();

		if ( activity.locationServicesConnected()
				&& AkiApplication.getChatMode(activity.getApplicationContext()) == GroupChatMode.LOCAL ){
			activity.startPeriodicLocationUpdates();
		}

		AkiInternalStorageUtil.setCurrentUser(activity.getApplicationContext(), currentUserId);

		LinearLayout currentMemberIcons = (LinearLayout) activity.findViewById(R.id.com_lespi_aki_main_chat_members_list);
		currentMemberIcons.setVisibility(View.VISIBLE);

		SlidingMenu slidingMenu = activity.getSlidingMenu();
		slidingMenu.setSlidingEnabled(true);
	}

	private void switchGroupChatState(AkiMainActivity activity){
		final ImageButton globalChatBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_chat_global_btn);
		if ( AkiApplication.getChatMode(activity.getApplicationContext()) == GroupChatMode.LOCAL ){
			AkiApplication.setGroupChatModeToGlobal(activity.getApplicationContext());
			activity.stopPeriodicLocationUpdates();
			globalChatBtn.setImageDrawable(getResources().getDrawable(R.drawable.icon_local));
		}
		else {
			AkiApplication.setGroupChatModeToLocal(activity.getApplicationContext());
			if ( activity.locationServicesConnected() ){
				activity.startPeriodicLocationUpdates();
				globalChatBtn.setImageDrawable(getResources().getDrawable(R.drawable.icon_global));
			}
		}
	}

	private void refreshSettings(final AkiMainActivity activity, final AccessToken currentSession, 
			final JSONObject currentUser, final AsyncCallback callback) {

		AkiSettingsFragment settingsFragment = activity.getSettingsFragment();
		if ( settingsFragment != null ){
			settingsFragment.refreshSettings(activity, currentSession, currentUser, callback);
		}
	}

	private void refreshReceivedMessages(final AkiMainActivity activity, final AccessToken session, final JSONObject currentUser) {

		final Context context = activity.getApplicationContext();

		final AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(activity.getApplicationContext());
		chatAdapter.setCurrentUser(currentUser);
		chatAdapter.setCurrentSession(session);
		chatAdapter.setActivity(activity);

		new AsyncTask<Void, Void, List<JsonObject>>(){

			@Override
			protected List<JsonObject> doInBackground(Void... params) {

				PriorityQueue<JsonObject> messages = AkiInternalStorageUtil.retrieveMessages(context,
						AkiInternalStorageUtil.getCurrentChatRoom(context));
				return AkiChatAdapter.toJsonObjectList(messages);
			}

			@Override
			public void onPostExecute(List<JsonObject> messages){

				chatAdapter.clear();
				if ( messages != null ){
					chatAdapter.addAll(messages);
				}
				ListView listView = (ListView) activity.findViewById(R.id.com_lespi_aki_main_messages_list);
				listView.setAdapter(chatAdapter);
				listView.setSelection(chatAdapter.getCount() - 1);
				listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
				listView.setWillNotCacheDrawing(true);

				refreshMembersList(activity, currentUser.optString("id"));
			}
		}.execute();
	}

	private void clearMembersList(final AkiMainActivity activity){

		SparseIntArray posToResourceId = new SparseIntArray();
		posToResourceId.put(0, R.id.com_lespi_aki_main_chat_members_list_first);
		posToResourceId.put(1, R.id.com_lespi_aki_main_chat_members_list_second);
		posToResourceId.put(2, R.id.com_lespi_aki_main_chat_members_list_third);
		posToResourceId.put(3, R.id.com_lespi_aki_main_chat_members_list_fourth);
		posToResourceId.put(4, R.id.com_lespi_aki_main_chat_members_list_fifth);
		posToResourceId.put(5, R.id.com_lespi_aki_main_chat_members_list_sixth);
		posToResourceId.put(6, R.id.com_lespi_aki_main_chat_members_list_seventh);

		for ( int i=0; i<=6; i++ ){
			ImageView memberPicture = (ImageView) activity.findViewById(posToResourceId.get(i));
			memberPicture.setVisibility(View.GONE);
		}
	}

	private void refreshMembersList(final AkiMainActivity activity, final String currentUserId){

		final Context context = activity.getApplicationContext();

		AkiServerUtil.getMembersList(context, new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				@SuppressWarnings("unchecked")
				List<String> memberIds = (List<String>) response;

				TextView status = (TextView) activity.findViewById(R.id.com_lespi_aki_main_chat_status);
				if ( memberIds.size() == 1 ){
					status.setText(context.getString(R.string.com_lespi_aki_main_chat_status_alone));
					AkiInternalStorageUtil.setAloneSetting(context, true);
					final ImageButton globalChatBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_chat_global_btn);
					globalChatBtn.setEnabled(true);
					globalChatBtn.setImageAlpha(255);

				}
				else {
					AkiInternalStorageUtil.setAloneSetting(context, false);
					status.setText(String.format(context.getString(R.string.com_lespi_aki_main_chat_status_pattern),
							memberIds.size() == 2
							? context.getString(R.string.com_lespi_aki_main_chat_status_subpattern_single_person)
									: String.format(context.getString(R.string.com_lespi_aki_main_chat_status_subpattern_more_people), memberIds.size() - 1)
							));
				}

				SparseIntArray posToResourceId = new SparseIntArray();
				posToResourceId.put(1, R.id.com_lespi_aki_main_chat_members_list_first);
				posToResourceId.put(2, R.id.com_lespi_aki_main_chat_members_list_second);
				posToResourceId.put(3, R.id.com_lespi_aki_main_chat_members_list_third);
				posToResourceId.put(4, R.id.com_lespi_aki_main_chat_members_list_fourth);
				posToResourceId.put(5, R.id.com_lespi_aki_main_chat_members_list_fifth);
				posToResourceId.put(6, R.id.com_lespi_aki_main_chat_members_list_sixth);
				posToResourceId.put(7, R.id.com_lespi_aki_main_chat_members_list_seventh);

				for ( int i=1; i<=7; i++ ){
					ImageView memberPicture = (ImageView) activity.findViewById(posToResourceId.get(i));
					memberPicture.setVisibility(View.GONE);
				}

				boolean currentUserIsAnonymous = AkiInternalStorageUtil.getAnonymousSetting(context, currentUserId);

				int pos = 2;
				for ( int i=0; i<memberIds.size(); i++ ){
					if ( pos > 7 ){
						break;
					}

					String memberId = memberIds.get(i);

					ImageView memberPicture = (memberId.equals(currentUserId) ?
							(ImageView) activity.findViewById(posToResourceId.get(1)) :
								(ImageView) activity.findViewById(posToResourceId.get(pos++)));
					memberPicture.setVisibility(View.VISIBLE);

					boolean memberIsAnonymous = AkiInternalStorageUtil.getAnonymousSetting(context, memberId);
					String gender = AkiInternalStorageUtil.getCachedUserGender(context, memberId);

					Bitmap picture = AkiInternalStorageUtil.getCachedUserPicture(context, memberId);
					if ( memberId.equals(currentUserId) ){
						if ( currentUserIsAnonymous ){
							memberPicture.setImageAlpha(128);
						}
						else{
							memberPicture.setImageAlpha(255);
						}
						if ( picture != null ){
							memberPicture.setImageBitmap(AkiChatAdapter.getRoundedBitmap(picture));
							continue;
						}
						else{
							Log.e(AkiChatFragment.TAG, "Refreshing members list: picture of current user (id " + memberId + ") not cached!");
						}
					}

					memberPicture.setImageAlpha(255);

					if ( !currentUserIsAnonymous && !memberIsAnonymous ){
						if ( picture != null ){
							memberPicture.setImageBitmap(AkiChatAdapter.getRoundedBitmap(picture));
							continue;
						}
						else{
							Log.e(AkiChatFragment.TAG, "Refreshing members list: picture of user (id " + memberId + ") not cached!");
						}
					}

					if ( gender.equals("male") ){
						picture = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_male);
					}
					else if ( gender.equals("female") ){
						picture = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_female);
					}
					else{
						picture = BitmapFactory.decodeResource(context.getResources(), R.drawable.no_picture_unknown_gender);								
					}

					memberPicture.setImageBitmap(AkiChatAdapter.getRoundedBitmap(picture));

				}

				LinearLayout currentMemberIcons = (LinearLayout) activity.findViewById(R.id.com_lespi_aki_main_chat_members_list);
				currentMemberIcons.setVisibility(View.VISIBLE);
			}

			@Override
			public void onFailure(Throwable failure) {
				Log.e(AkiChatFragment.TAG, "Tried to retrieve members list but currently not in a chat_room!");
				failure.printStackTrace();
			}

			@Override
			public void onCancel() {
				CharSequence toastText;
				if ( AkiApplication.SERVER_DOWN ){
					toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_server_down);
				}
				else{
					toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_no_internet_connection);
				}
				Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
				toast.show();
				activity.onResume();
				Log.e(AkiChatFragment.TAG, "Exiting chat room canceled.");						
			}
		});
	}

	public void externalRefreshAll(){

		AkiMainActivity activity = (AkiMainActivity) getActivity();
		if ( activity == null ){
			Log.i(AkiChatFragment.TAG, "Not supposed to refresh externally!");
			return;
		}

		String currentUserId = AkiInternalStorageUtil.getCurrentUser(activity.getApplicationContext());
		if ( currentUserId == null ){
			Log.i(AkiChatFragment.TAG, "Not supposed to refresh externally!");
			return;
		}

		AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(activity.getApplicationContext());

		ListView listView = (ListView) activity.findViewById(R.id.com_lespi_aki_main_messages_list);
		listView.setAdapter(chatAdapter);
		listView.setSelection(chatAdapter.getCount() - 1);
		listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
		listView.setWillNotCacheDrawing(true);

		refreshMembersList(activity, currentUserId);

		if ( !AkiInternalStorageUtil.getAloneSetting(activity.getApplicationContext())
				&& AkiApplication.getChatMode(activity.getApplicationContext()) == GroupChatMode.LOCAL ) {
			final ImageButton globalChatBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_chat_global_btn);
			globalChatBtn.setEnabled(false);
			globalChatBtn.setImageAlpha(128);			
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(AkiChatFragment.TAG, "AkiChatFragment$onCreate");
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.v(AkiChatFragment.TAG, "AkiChatFragment$onResume");

		final AkiMainActivity activity = (AkiMainActivity) getActivity();
		if ( activity == null ){
			Log.e(AkiChatFragment.TAG, "onResume event called but there is no MainActivity alive, so ignored session change event.");
			return;
		}

		final ImageView background = (ImageView) activity.findViewById(R.id.com_lespi_aki_main_background);
		final ImageView backgroundLogo = (ImageView) activity.findViewById(R.id.com_lespi_aki_main_background_logo);
		final TextView backgroundWarningText = (TextView) activity.findViewById(R.id.com_lespi_aki_main_background_text);
		if ( !AkiHttpRequestUtil.isConnectedToTheInternet(activity.getApplicationContext()) ||
				!AkiMainActivity.isLocationProviderEnabled(activity.getApplicationContext()) ){

			background.setVisibility(View.VISIBLE);
			backgroundLogo.setVisibility(View.VISIBLE);
			backgroundWarningText.setVisibility(View.VISIBLE);
			backgroundWarningText.setText(activity.getResources().getString(R.string.com_lespi_aki_main_chat_warning_internet_and_gps_needed));

			activity.getSlidingMenu().showContent();
			activity.getSlidingMenu().setSlidingEnabled(false);
			return;
		}
		else{
			AkiServerUtil.isServerUp(activity.getApplicationContext(), new AsyncCallback() {

				@Override
				public void onSuccess(Object response) {

					Log.w(AkiChatFragment.TAG, "Server is up and running!");
					AccessToken session = AccessToken.getCurrentAccessToken();
					if (session != null ) {
						background.setVisibility(View.GONE);
						backgroundLogo.setVisibility(View.GONE);
						backgroundWarningText.setVisibility(View.GONE);
						onSessionStateChange(session);
					}
					else {
						AkiServerUtil.getPresenceFromServer(activity.getApplicationContext(), new AsyncCallback() {

							@Override
							public void onSuccess(Object response) {
								AkiServerUtil.sendInactiveToServer(activity.getApplicationContext());
							}

							@Override
							public void onFailure(Throwable failure) {
								Log.i(AkiChatFragment.TAG, "Could not get presence from server.");
								if ( failure != null ){
									failure.printStackTrace();
								}
							}

							@Override
							public void onCancel() {
								Log.e(AkiChatFragment.TAG, "Endpoint:getPresenceFromServer callback canceled.");
							}
						});
						String currentUserId = AkiInternalStorageUtil.getCurrentUser(activity.getApplicationContext());
						switchToLoginArea(activity, currentUserId, true);
					}
				}

				@Override
				public void onFailure(Throwable failure) {
					Log.wtf(AkiChatFragment.TAG, "Server is down!!!");
					failure.printStackTrace();

					background.setVisibility(View.VISIBLE);
					backgroundLogo.setVisibility(View.VISIBLE);
					backgroundWarningText.setVisibility(View.VISIBLE);
					backgroundWarningText.setText(activity.getResources().getString(R.string.com_lespi_aki_main_chat_warning_server_down));

					activity.getSlidingMenu().showContent();
					activity.getSlidingMenu().setSlidingEnabled(false);
				}

				@Override
				public void onCancel() {
					Log.w(AkiChatFragment.TAG, "Canceled checking if Server is up and running!");
				}
			});
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		Log.v(AkiChatFragment.TAG, "AkiChatFragment$onActivityResult");
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.v(AkiChatFragment.TAG, "AkiChatFragment$onPause");
		SlidingMenu slidingMenu = ((AkiMainActivity) getActivity()).getSlidingMenu();
		slidingMenu.showContent();
		slidingMenu.setSlidingEnabled(true);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.v(AkiChatFragment.TAG, "AkiChatFragment$onDestroy");
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Log.v(AkiChatFragment.TAG, "AkiChatFragment$onSaveInstanceState");
	}

}