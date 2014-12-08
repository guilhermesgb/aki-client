package com.lespi.aki;

import java.util.List;
import java.util.PriorityQueue;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphUser;
import com.facebook.widget.LoginButton;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiHttpUtil;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiServerUtil;
import com.parse.internal.AsyncCallback;

public class AkiChatFragment extends SherlockFragment {

	private boolean seenSplash = false;
	 

	public void setSeenSplash(boolean seenSplash) {
		this.seenSplash = seenSplash;
	}

	private UiLifecycleHelper uiHelper;
	
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
	
	private Session.StatusCallback callback = new Session.StatusCallback() {
		@Override
		public void call(Session session, SessionState state, Exception exception) {
			onSessionStateChange(session, state, exception);
		}
	};

	private class WebViewMonitor extends WebViewClient {

		@Override
		public void onPageFinished(WebView view, String url) {
			super.onPageFinished(view, url);
			view.setBackgroundColor(0x00000000);
			view.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
		}
	}

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	public View onCreateView(LayoutInflater inflater,
			ViewGroup container, Bundle savedInstanceState){

		View view = inflater.inflate(R.layout.aki_chat_fragment, container, false);

		LoginButton authButton = (LoginButton) view.findViewById(R.id.com_lespi_aki_main_login_auth_btn);
		authButton.setFragment(this);
		authButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);

		WebView webView = new WebView(getActivity().getApplicationContext());
		webView.setClickable(true);
		webView.getSettings().setJavaScriptEnabled(true);
		webView.setWebViewClient(new WebViewMonitor());
		webView.setBackgroundColor(0x00000000);
		webView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
		webView.loadUrl("file:///android_asset/aki.html");
		webView.setId(R.id.com_lespi_aki_main_login_webview);

		RelativeLayout loginLayout = (RelativeLayout) container.findViewById(R.id.com_lespi_aki_main_login);
		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		loginLayout.addView(webView, 0, params);
		return view;
	}

	private void onSessionStateChange(final Session session, SessionState state, Exception exception) {
		Log.v(AkiApplication.TAG, "AkiChatFragment$onSessionStateChange");

		final AkiMainActivity activity = (AkiMainActivity) getActivity();
		if ( activity == null ){
			Log.d(AkiApplication.TAG, "Facebook session callback called but there is no MainActivity alive, so ignored session change event.");
			return;
		}

		final ProgressBar loadingIcon = (ProgressBar) activity.findViewById(R.id.com_lespi_aki_main_chat_progress_bar);
		loadingIcon.setVisibility(View.VISIBLE);
		final RelativeLayout membersList = (RelativeLayout) activity.findViewById(R.id.com_lespi_aki_main_chat_members_list);
		membersList.setVisibility(View.GONE);
		
		if ( !AkiHttpUtil.isConnectedToTheInternet(activity.getApplicationContext()) ){
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

		if ( state.isOpened() ) {

			Request.newMeRequest(session, new Request.GraphUserCallback() {

				@Override
				public void onCompleted(final GraphUser currentUser, Response response) {
					if ( currentUser != null ){

						switchToChatArea(activity, currentUser.getId());
						if ( AkiInternalStorageUtil.getCurrentChatRoom(activity.getApplicationContext()) != null ){
							refreshReceivedMessages(activity, session, currentUser);
						}
						final ImageButton sendMessageBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_chat_send_btn);
						sendMessageBtn.setEnabled(false);

						final OnClickListener exitBtnClickListener = new OnClickListener() {

							@Override
							public void onClick(View view) {

								if ( !AkiMainActivity.isLocationProviderEnabled(activity.getApplicationContext()) ){
									CharSequence toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_please_enable_gps);
									Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
									toast.show();
									activity.onResume();
									return;
								}

								AkiServerUtil.sendExitToServer(activity.getApplicationContext(), new AsyncCallback() {

									@Override
									public void onSuccess(Object response) {
										AkiServerUtil.leaveChatRoom(activity.getApplicationContext(), currentUser.getId());
										activity.stopPeriodicLocationUpdates();
										activity.removeGeofence();

										AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(activity.getApplicationContext());
										chatAdapter.clear();
										loadingIcon.setVisibility(View.VISIBLE);
										membersList.setVisibility(View.GONE);

										String contentTitle = activity.getApplicationContext().getString(R.string.com_lespi_aki_notif_exit_title);
										String contentText = activity.getApplicationContext().getString(R.string.com_lespi_aki_notif_exit_text);

										Notification.Builder notifyBuilder = new Notification.Builder(activity.getApplicationContext())
										.setSmallIcon(R.drawable.notification_icon)
										.setContentTitle(contentTitle)
										.setContentText(contentText)
										.setAutoCancel(true);

										NotificationManager notificationManager = (NotificationManager) activity.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
										notificationManager.notify(AkiApplication.EXITED_ROOM_NOTIFICATION_ID, notifyBuilder.build());

										Intent intent = new Intent(Intent.ACTION_MAIN);
										intent.addCategory(Intent.CATEGORY_HOME);
										getActivity().startActivity(intent);
										AkiApplication.isNotLoggedIn();
										getActivity().finish();
									}

									@Override
									public void onFailure(Throwable failure) {
										Log.e(AkiApplication.TAG, "A problem happened while exiting chat room!");
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
										Log.e(AkiApplication.TAG, "Exiting chat room canceled.");
									}
								});
							}
						};

						final OnClickListener skipBtnClickListener = new OnClickListener() {

							@Override
							public void onClick(View view) {

								if ( !AkiMainActivity.isLocationProviderEnabled(activity.getApplicationContext()) ){
									CharSequence toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_please_enable_gps);
									Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
									toast.show();
									activity.onResume();
									return;
								}

								AkiServerUtil.sendSkipToServer(activity.getApplicationContext(), new AsyncCallback() {

									@Override
									public void onSuccess(Object response) {

										AkiInternalStorageUtil.setAnonymousSetting(activity.getApplicationContext(), currentUser.getId(), true);
										AkiServerUtil.leaveChatRoom(activity.getApplicationContext(), currentUser.getId());
										activity.removeGeofence();

										AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(activity.getApplicationContext());
										chatAdapter.clear();
										loadingIcon.setVisibility(View.VISIBLE);
										membersList.setVisibility(View.GONE);

										CharSequence toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_skipped_chat);
										Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
										toast.show();

										activity.onResume();
									}

									@Override
									public void onFailure(Throwable failure) {
										Log.e(AkiApplication.TAG, "A problem happened while skipping chat room!");
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
										Log.e(AkiApplication.TAG, "Skipping chat room canceled.");
										activity.onResume();
									}
								});
							}
						};

						final ImageButton exitChatBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_chat_exit_btn);
						exitChatBtn.setEnabled(false);
						exitChatBtn.setOnClickListener(exitBtnClickListener);
						final Button exitChatBtnText = (Button) activity.findViewById(R.id.com_lespi_aki_main_chat_exit_btn_text);
						exitChatBtnText.setEnabled(false);
						exitChatBtnText.setOnClickListener(exitBtnClickListener);
						final ImageButton skipChatBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_chat_skip_btn);
						skipChatBtn.setEnabled(false);
						skipChatBtn.setOnClickListener(skipBtnClickListener);
						final Button skipChatBtnText = (Button) activity.findViewById(R.id.com_lespi_aki_main_chat_skip_btn_text);
						skipChatBtnText.setEnabled(false);
						skipChatBtnText.setOnClickListener(skipBtnClickListener);

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
											Log.i(AkiApplication.TAG, "Message: " + message + " sent!");
											/*final JsonObject msg = new JsonObject();
											msg.add("message", message);
											msg.add("sender", currentUser.getId());
											AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(activity.getApplicationContext());
											chatAdapter.add(msg);
											chatAdapter.notifyDataSetChanged();*/
										}

										@Override
										public void onFailure(Throwable failure) {
											Log.e(AkiApplication.TAG, "You could not send message!");
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
											Log.e(AkiApplication.TAG, "Endpoint:sendMessage callback canceled.");
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
										activity.getSlidingMenu().showSecondaryMenu(true);
									}
								});
								activity.getSlidingMenu().setSlidingEnabled(true);

								AkiServerUtil.sendPresenceToServer(activity.getApplicationContext(), currentUser.getId(), new AsyncCallback() {

									@Override
									public void onSuccess(Object response) {

										exitChatBtn.setEnabled(true);
										exitChatBtnText.setEnabled(true);
										skipChatBtn.setEnabled(true);
										skipChatBtnText.setEnabled(true);

										JsonObject responseJSON = (JsonObject) response;
										final JsonValue chatRoomId = responseJSON.get("chat_room");
										if ( chatRoomId != null ){
											JsonValue nT = responseJSON.get("timestamp");
											if ( nT != null ){
												String nextTimestamp = nT.asString();
												AkiInternalStorageUtil.setLastServerTimestamp(activity.getApplicationContext(), nextTimestamp);
												Log.wtf("PULL MAN!", "(just got into a room so) SETTING LAST SERVER TT TO: " + nextTimestamp + "!");
											}
											AkiServerUtil.enterChatRoom(activity, currentUser.getId(), chatRoomId.asString());
											final CheckBox anonymousCheck = (CheckBox) activity.findViewById(R.id.com_lespi_aki_main_settings_anonymous);
											anonymousCheck.setChecked(AkiInternalStorageUtil.getAnonymousSetting(activity.getApplicationContext(), currentUser.getId()));
											activity.setGeofence();
											refreshReceivedMessages(activity, session, currentUser);
											sendMessageBtn.setEnabled(true);
											AkiServerUtil.getMessages(activity.getApplicationContext());
										}
									}

									@Override
									public void onFailure(Throwable failure) {
										Log.e(AkiApplication.TAG, "Could not send presence to server.");
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
										Log.e(AkiApplication.TAG, "Endpoint:sendPresenceToServer callback canceled.");
										activity.onResume();
									}
								});
							}

							@Override
							public void onFailure(Throwable failure) {
								Log.e(AkiApplication.TAG, "Could not refresh settings.");
								failure.printStackTrace();
								refreshReceivedMessages(activity, session, currentUser);
							}

							@Override
							public void onCancel() {
								Log.e(AkiApplication.TAG, "Refresh of settings canceled.");
								refreshReceivedMessages(activity, session, currentUser);
							}
						});
					}
				}
			}).executeAsync();
		} else if (state.isClosed()) {

			String currentUserId = AkiInternalStorageUtil.getCurrentUser(activity.getApplicationContext());
			switchToLoginArea(activity, currentUserId, false);
		}
	}

	private void switchToLoginArea(final AkiMainActivity activity, final String currentUserId, boolean showSplash){
		if ( showSplash && (!seenSplash) ){
			Intent intent = new Intent(activity, AkiSplashActivity.class);
			startActivity(intent);
			activity.finish();
			return;
		}
		
		if ( AkiApplication.LOGGED_IN ){
			WebView webView = (WebView) activity.findViewById(R.id.com_lespi_aki_main_login_webview);
			webView.loadUrl("javascript:show_login_screen();");
		}

		final RelativeLayout membersList = (RelativeLayout) activity.findViewById(R.id.com_lespi_aki_main_chat_members_list);
		membersList.setVisibility(View.GONE);
		
		RelativeLayout background = (RelativeLayout) activity.findViewById(R.id.com_lespi_aki_main_background);
		background.setVisibility(View.GONE);

		final LinearLayout chatArea = (LinearLayout) activity.findViewById(R.id.com_lespi_aki_main_chat);
		final RelativeLayout loginArea = (RelativeLayout) activity.findViewById(R.id.com_lespi_aki_main_login);
		chatArea.setVisibility(View.GONE);
		loginArea.setVisibility(View.VISIBLE);

		SlidingMenu slidingMenu = activity.getSlidingMenu();
		slidingMenu.showContent();
		slidingMenu.setSlidingEnabled(false);

		AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(activity.getApplicationContext());
		chatAdapter.clear();
		ProgressBar loadingIcon = (ProgressBar) activity.findViewById(R.id.com_lespi_aki_main_chat_progress_bar);
		loadingIcon.setVisibility(View.VISIBLE);

		if ( activity.locationServicesConnected() ){
			activity.stopPeriodicLocationUpdates();
			activity.removeGeofence();
		}

		Log.wtf("PULL MAN!", "Stopping getMessages runnable!");
		AkiServerUtil.stopGettingMessages(activity.getApplicationContext());
		
		final Context context = activity.getApplicationContext();

		if ( AkiApplication.LOGGED_IN ){

			AkiServerUtil.sendExitToServer(context, new AsyncCallback() {

				@Override
				public void onSuccess(Object response) {
					AkiServerUtil.leaveChatRoom(context, currentUserId);

					AkiInternalStorageUtil.wipeCachedUserLocation(context, new AsyncCallback() {

						@Override
						public void onSuccess(Object response) {
							AkiInternalStorageUtil.setCurrentUser(context, null);
						}

						@Override
						public void onFailure(Throwable failure) {
							Log.e(AkiApplication.TAG, "Could not wipe cached user location.");
							failure.printStackTrace();
							AkiInternalStorageUtil.setCurrentUser(context, null);
						}

						@Override
						public void onCancel() {
							Log.e(AkiApplication.TAG, "Wipe cached user location callback canceled.");
						}
					});
					
					CharSequence toastText = activity.getApplicationContext().getText(R.string.com_lespi_aki_toast_exited_chat);
					Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
					toast.show();
				}

				@Override
				public void onFailure(Throwable failure) {
					Log.e(AkiApplication.TAG, "A problem happened while exiting chat room!");
					failure.printStackTrace();
				}

				@Override
				public void onCancel() {
					Log.e(AkiApplication.TAG, "Exiting chat room canceled.");
				}
			});
		}

		AkiApplication.isNotLoggedIn();
	}

	private void switchToChatArea(AkiMainActivity activity, String currentUserId){
		AkiApplication.isLoggedIn();
		if ( activity.locationServicesConnected() ){
			activity.startPeriodicLocationUpdates();
		}

		final RelativeLayout membersList = (RelativeLayout) activity.findViewById(R.id.com_lespi_aki_main_chat_members_list);
		membersList.setVisibility(View.GONE);
		
		AkiInternalStorageUtil.setCurrentUser(activity.getApplicationContext(), currentUserId);

		RelativeLayout background = (RelativeLayout) activity.findViewById(R.id.com_lespi_aki_main_background);
		background.setVisibility(View.GONE);

		final LinearLayout chatArea = (LinearLayout) activity.findViewById(R.id.com_lespi_aki_main_chat);
		final RelativeLayout loginArea = (RelativeLayout) activity.findViewById(R.id.com_lespi_aki_main_login);
		chatArea.setVisibility(View.VISIBLE);
		loginArea.setVisibility(View.GONE);

		SlidingMenu slidingMenu = activity.getSlidingMenu();
		slidingMenu.setSlidingEnabled(true);
	}

	private void refreshSettings(final AkiMainActivity activity, final Session currentSession, 
			final GraphUser currentUser, final AsyncCallback callback) {

		AkiSettingsFragment settingsFragment = activity.getSettingsFragment();
		if ( settingsFragment != null ){
			settingsFragment.refreshSettings(activity, currentSession, currentUser, callback);
		}
	}

	private void refreshReceivedMessages(final AkiMainActivity activity, final Session session, final GraphUser currentUser) {

		final Context context = activity.getApplicationContext();

		final AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(activity.getApplicationContext());
		chatAdapter.setCurrentUser(currentUser);
		chatAdapter.setCurrentSession(session);

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

				refreshMembersList(activity, currentUser.getId());
			}
		}.execute();
	}

	private void refreshMembersList(final AkiMainActivity activity, final String currentUserId){

		final Context context = activity.getApplicationContext();

		AkiServerUtil.getMembersList(context, new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				@SuppressWarnings("unchecked")
				List<String> memberIds = (List<String>) response;

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
							Log.e(AkiApplication.TAG, "Refreshing members list: picture of current user (id " + memberId + ") not cached!");
						}
					}

					memberPicture.setImageAlpha(255);

					if ( !currentUserIsAnonymous && !memberIsAnonymous ){
						if ( picture != null ){
							memberPicture.setImageBitmap(AkiChatAdapter.getRoundedBitmap(picture));
							continue;
						}
						else{
							Log.e(AkiApplication.TAG, "Refreshing members list: picture of user (id " + memberId + ") not cached!");
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
				
				final ProgressBar loadingIcon = (ProgressBar) activity.findViewById(R.id.com_lespi_aki_main_chat_progress_bar);
				loadingIcon.setVisibility(View.GONE);

				final RelativeLayout membersList = (RelativeLayout) activity.findViewById(R.id.com_lespi_aki_main_chat_members_list);
				membersList.setVisibility(View.VISIBLE);
			}

			@Override
			public void onFailure(Throwable failure) {
				Log.e(AkiApplication.TAG, "Tried to retrieve members list but currently not in a chat_room!");
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
				Log.e(AkiApplication.TAG, "Exiting chat room canceled.");						
			}
		});
	}

	public void externalRefreshAll(){
		
		AkiMainActivity activity = (AkiMainActivity) getActivity();
		if ( activity == null ){
			Log.i(AkiApplication.TAG, "Not supposed to refresh externally!");
			return;
		}
		
		String currentUserId = AkiInternalStorageUtil.getCurrentUser(activity.getApplicationContext());
		if ( currentUserId == null ){
			Log.i(AkiApplication.TAG, "Not supposed to refresh externally!");
			return;
		}
		
		AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(activity.getApplicationContext());
		
		ListView listView = (ListView) activity.findViewById(R.id.com_lespi_aki_main_messages_list);
		listView.setAdapter(chatAdapter);
		listView.setSelection(chatAdapter.getCount() - 1);
		listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
		listView.setWillNotCacheDrawing(true);

		refreshMembersList(activity, currentUserId);
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(AkiApplication.TAG, "AkiChatFragment$onCreate");
		uiHelper = new UiLifecycleHelper(getActivity(), callback);
		uiHelper.onCreate(savedInstanceState);
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.v(AkiApplication.TAG, "AkiChatFragment$onResume");

		final AkiMainActivity activity = (AkiMainActivity) getActivity();
		if ( activity == null ){
			Log.e(AkiApplication.TAG, "onResume event called but there is no MainActivity alive, so ignored session change event.");
			return;
		}

		if ( !AkiHttpUtil.isConnectedToTheInternet(activity.getApplicationContext()) ){

			LinearLayout chatArea = (LinearLayout) activity.findViewById(R.id.com_lespi_aki_main_chat);
			RelativeLayout loginArea = (RelativeLayout) activity.findViewById(R.id.com_lespi_aki_main_login);
			chatArea.setVisibility(View.GONE);
			loginArea.setVisibility(View.GONE);

			ProgressBar loadingIcon = (ProgressBar) activity.findViewById(R.id.com_lespi_aki_main_background_loading);
			loadingIcon.setVisibility(View.GONE);
			TextView backgroundWarningText = (TextView) activity.findViewById(R.id.com_lespi_aki_main_background_text);
			backgroundWarningText.setText(activity.getResources().getString(R.string.com_lespi_aki_main_chat_warning_internet_needed_to_start_using));
			backgroundWarningText.setVisibility(View.VISIBLE);

			RelativeLayout background = (RelativeLayout) activity.findViewById(R.id.com_lespi_aki_main_background);
			background.setVisibility(View.VISIBLE);

			activity.getSlidingMenu().showContent();
			activity.getSlidingMenu().setSlidingEnabled(false);
			return;
		}
		else if ( !AkiMainActivity.isLocationProviderEnabled(activity.getApplicationContext()) ){

			LinearLayout chatArea = (LinearLayout) activity.findViewById(R.id.com_lespi_aki_main_chat);
			RelativeLayout loginArea = (RelativeLayout) activity.findViewById(R.id.com_lespi_aki_main_login);
			chatArea.setVisibility(View.GONE);
			loginArea.setVisibility(View.GONE);

			ProgressBar loadingIcon = (ProgressBar) activity.findViewById(R.id.com_lespi_aki_main_background_loading);
			loadingIcon.setVisibility(View.GONE);
			TextView backgroundWarningText = (TextView) activity.findViewById(R.id.com_lespi_aki_main_background_text);
			backgroundWarningText.setText(activity.getResources().getString(R.string.com_lespi_aki_main_chat_warning_gps_needed_to_start_using));
			backgroundWarningText.setVisibility(View.VISIBLE);

			RelativeLayout background = (RelativeLayout) activity.findViewById(R.id.com_lespi_aki_main_background);
			background.setVisibility(View.VISIBLE);

			activity.getSlidingMenu().showContent();
			activity.getSlidingMenu().setSlidingEnabled(false);
			return;
		}
		else{
			AkiServerUtil.isServerUp(activity.getApplicationContext(), new AsyncCallback() {

				@Override
				public void onSuccess(Object response) {
					
					ProgressBar loadingIcon = (ProgressBar) activity.findViewById(R.id.com_lespi_aki_main_background_loading);
					loadingIcon.setVisibility(View.VISIBLE);
					
					Log.w(AkiApplication.TAG, "Server is up and running!");
					Session session = Session.getActiveSession();
					if (session != null &&
							(session.isOpened() || session.isClosed()) ) {
						onSessionStateChange(session, session.getState(), null);
					}

					uiHelper.onResume();

					session = Session.getActiveSession();
					if (session == null || !(session.isOpened() || session.isClosed()) ) {
						AkiServerUtil.getPresenceFromServer(activity.getApplicationContext(), new AsyncCallback() {

							@Override
							public void onSuccess(Object response) {
								AkiServerUtil.sendInactiveToServer(activity.getApplicationContext());
							}

							@Override
							public void onFailure(Throwable failure) {
								Log.i(AkiApplication.TAG, "Could not get presence from server.");
								if ( failure != null ){
									failure.printStackTrace();
								}
							}

							@Override
							public void onCancel() {
								Log.e(AkiApplication.TAG, "Endpoint:getPresenceFromServer callback canceled.");
							}
						});
						String currentUserId = AkiInternalStorageUtil.getCurrentUser(activity.getApplicationContext());
						switchToLoginArea(activity, currentUserId, true);
					}
				}

				@Override
				public void onFailure(Throwable failure) {
					Log.wtf(AkiApplication.TAG, "Server is down!!!");
					failure.printStackTrace();

					LinearLayout chatArea = (LinearLayout) activity.findViewById(R.id.com_lespi_aki_main_chat);
					RelativeLayout loginArea = (RelativeLayout) activity.findViewById(R.id.com_lespi_aki_main_login);
					chatArea.setVisibility(View.GONE);
					loginArea.setVisibility(View.GONE);

					ProgressBar loadingIcon = (ProgressBar) activity.findViewById(R.id.com_lespi_aki_main_background_loading);
					loadingIcon.setVisibility(View.GONE);
					TextView backgroundWarningText = (TextView) activity.findViewById(R.id.com_lespi_aki_main_background_text);
					backgroundWarningText.setText(activity.getResources().getString(R.string.com_lespi_aki_main_chat_warning_internet_needed_to_start_using));
					backgroundWarningText.setVisibility(View.VISIBLE);

					RelativeLayout background = (RelativeLayout) activity.findViewById(R.id.com_lespi_aki_main_background);
					background.setVisibility(View.VISIBLE);

					activity.getSlidingMenu().showContent();
					activity.getSlidingMenu().setSlidingEnabled(false);
				}

				@Override
				public void onCancel() {
					Log.w(AkiApplication.TAG, "Canceled checking if Server is up and running!");
				}
			});
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		Log.v(AkiApplication.TAG, "AkiChatFragment$onActivityResult");
		uiHelper.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.v(AkiApplication.TAG, "AkiChatFragment$onPause");
		uiHelper.onPause();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.v(AkiApplication.TAG, "AkiChatFragment$onDestroy");
		uiHelper.onDestroy();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Log.v(AkiApplication.TAG, "AkiChatFragment$onSaveInstanceState");
		uiHelper.onSaveInstanceState(outState);
	}

	
}
