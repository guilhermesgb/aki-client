package com.lespi.aki;

import java.util.List;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
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
import com.lespi.aki.json.JsonArray;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiServerUtil;
import com.parse.internal.AsyncCallback;

public class AkiChatFragment extends SherlockFragment{

	private boolean seenSplash = false;

	public void setSeenSplash(boolean seenSplash) {
		this.seenSplash = seenSplash;
	}

	private UiLifecycleHelper uiHelper;

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

		final AkiMainActivity activity = (AkiMainActivity) getActivity();
		if ( activity == null ){
			Log.d(AkiApplication.TAG, "Facebook session callback called but there is no MainActivity alive, so ignored session change event.");
			return;
		}

		if ( state.isOpened() ) {

			Request.newMeRequest(session, new Request.GraphUserCallback() {

				@Override
				public void onCompleted(final GraphUser user, Response response) {
					if ( user != null ){

						switchToChatArea(activity, user.getId());
						refreshReceivedMessages(activity, session, user);
						final ImageButton sendMessageBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_chat_send_btn);
						sendMessageBtn.setEnabled(false);
						ImageButton openSettingsBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_chat_settings_btn);
						openSettingsBtn.setEnabled(true);
						openSettingsBtn.setOnClickListener(new OnClickListener() {

							@Override
							public void onClick(View view) {
								activity.getSlidingMenu().showMenu(true);
							}
						});
						
						OnClickListener exitBtnClickListener = new OnClickListener() {

							@Override
							public void onClick(View view) {
								
								AkiServerUtil.sendExitToServer(activity.getApplicationContext(), new AsyncCallback() {

									@Override
									public void onSuccess(Object response) {
										AkiInternalStorageUtil.setAnonymousSetting(activity.getApplicationContext(), user.getId(), true);
										AkiServerUtil.leaveChatRoom(activity.getApplicationContext());
										activity.stopPeriodicLocationUpdates();
										activity.removeGeofence();

										String contentTitle = activity.getApplicationContext().getString(R.string.com_lespi_aki_notif_exit_title);
										String contentText = "You've left a chat room manually.";
										
										NotificationCompat.Builder notifyBuilder = new NotificationCompat.Builder(activity.getApplicationContext())
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
										Log.e(AkiApplication.TAG, "Could not cancel exiting chat room.");
									}
								});
							}
						};
						
						ImageButton exitChatBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_chat_exit_btn);
						exitChatBtn.setEnabled(true);
						exitChatBtn.setOnClickListener(exitBtnClickListener);
						Button exitChatBtnText = (Button) activity.findViewById(R.id.com_lespi_aki_main_chat_exit_btn_text);
						exitChatBtnText.setEnabled(true);
						exitChatBtnText.setOnClickListener(exitBtnClickListener);
						
						OnClickListener skipBtnClickListener = new OnClickListener() {

							@Override
							public void onClick(View view) {
								AkiServerUtil.sendSkipToServer(activity.getApplicationContext(), new AsyncCallback() {

									@Override
									public void onSuccess(Object response) {
										AkiInternalStorageUtil.setAnonymousSetting(activity.getApplicationContext(), user.getId(), true);
										AkiServerUtil.leaveChatRoom(activity.getApplicationContext());
										activity.removeGeofence();

										CharSequence toastText = "You have skipped into another chat room!";
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
										Log.e(AkiApplication.TAG, "Could not cancel skipping chat room.");
									}
								});
							}
						};
						
						ImageButton skipChatBtn = (ImageButton) activity.findViewById(R.id.com_lespi_aki_main_chat_skip_btn);
						skipChatBtn.setEnabled(true);
						skipChatBtn.setOnClickListener(skipBtnClickListener);
						Button skipChatBtnText = (Button) activity.findViewById(R.id.com_lespi_aki_main_chat_skip_btn_text);
						skipChatBtnText.setEnabled(true);
						skipChatBtnText.setOnClickListener(skipBtnClickListener);
						
						refreshSettings(activity, session, user, new AsyncCallback(){

							@Override
							public void onSuccess(Object response) {

								AkiServerUtil.sendPresenceToServer(activity.getApplicationContext(), user.getId(), new AsyncCallback() {

									@Override
									public void onSuccess(Object response) {

										JsonObject responseJSON = (JsonObject) response;
										final JsonValue chatRoomId = responseJSON.get("chat_room");
										if ( chatRoomId != null ){
											AkiServerUtil.enterChatRoom(activity, user.getId(), chatRoomId.asString());
											activity.setGeofence();
											refreshReceivedMessages(activity, session, user);
											sendMessageBtn.setEnabled(true);
											sendMessageBtn.setOnClickListener(new OnClickListener() {
												
												@Override
												public void onClick(View view) {
													
													final EditText chatBox = (EditText) activity.findViewById(R.id.com_lespi_aki_main_chat_input);
													final String message = chatBox.getText().toString().trim();
													if ( !message.isEmpty() ){
														chatBox.setText("");
														AkiServerUtil.sendMessage(activity.getApplicationContext(), message, new AsyncCallback() {
															
															@Override
															public void onSuccess(Object response) {
																CharSequence toastText = "Message sent! :)";
																Toast toast = Toast.makeText(activity.getApplicationContext(), toastText, Toast.LENGTH_SHORT);
																toast.show();
															}
															
															@Override
															public void onFailure(Throwable failure) {
																Log.e(AkiApplication.TAG, "You could not send message!");
																failure.printStackTrace();
															}
															
															@Override
															public void onCancel() {
																Log.e(AkiApplication.TAG, "Endpoint:sendMessage callback canceled.");
															}
														});
													}
												}
											});
										}
									}

									@Override
									public void onFailure(Throwable failure) {
										Log.e(AkiApplication.TAG, "Could not refresh settings.");
										failure.printStackTrace();
										refreshReceivedMessages(activity, session, user);
									}

									@Override
									public void onCancel() {
										Log.e(AkiApplication.TAG, "Refresh of settings canceled.");
										refreshReceivedMessages(activity, session, user);
									}
								});
							}

							@Override
							public void onFailure(Throwable failure) {
								Log.e(AkiApplication.TAG, "Could not send presence to server.");
								failure.printStackTrace();
							}

							@Override
							public void onCancel() {
								Log.e(AkiApplication.TAG, "Endpoint:sendPresenceToServer callback canceled.");
							}
						});
					}
				}
			}).executeAsync();
		} else if (state.isClosed()) {

			switchToLoginArea(activity, false);
			if ( AkiServerUtil.isActiveOnServer() ){
				AkiServerUtil.sendInactiveToServer(activity.getApplicationContext());
			}
		}
	}

	private void switchToLoginArea(final AkiMainActivity activity, boolean showSplash){
		if ( AkiApplication.LOGGED_IN ){
			WebView webView = (WebView) activity.findViewById(R.id.com_lespi_aki_main_login_webview);
			webView.loadUrl("javascript:show_login_screen();");
		}
		if ( activity.locationServicesConnected() ){
			activity.stopPeriodicLocationUpdates();
			activity.removeGeofence();
		}

		final Context context = activity.getApplicationContext();

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

		if ( AkiApplication.LOGGED_IN ){

			AkiServerUtil.sendExitToServer(context, new AsyncCallback() {

				@Override
				public void onSuccess(Object response) {
					AkiServerUtil.leaveChatRoom(context);

					CharSequence toastText = "You exited a chat room!";
					Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
					toast.show();
					onResume();
				}

				@Override
				public void onFailure(Throwable failure) {
					Log.e(AkiApplication.TAG, "A problem happened while exiting chat room!");
					failure.printStackTrace();
				}

				@Override
				public void onCancel() {
					Log.e(AkiApplication.TAG, "Could not cancel exiting chat room.");
				}
			});
		}

		final LinearLayout chatArea = (LinearLayout) activity.findViewById(R.id.com_lespi_aki_main_chat);
		final RelativeLayout loginArea = (RelativeLayout) activity.findViewById(R.id.com_lespi_aki_main_login);
		chatArea.setVisibility(View.GONE);
		loginArea.setVisibility(View.VISIBLE);

		SlidingMenu slidingMenu = activity.getSlidingMenu();
		slidingMenu.showContent();
		slidingMenu.setSlidingEnabled(false);

		AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(activity.getApplicationContext());
		chatAdapter.clear();

		AkiApplication.isNotLoggedIn();

		if ( showSplash && (!seenSplash) ){
			Intent intent = new Intent(activity, AkiSplashActivity.class);
			startActivity(intent);
			activity.finish();
		}
	}

	private void switchToChatArea(AkiMainActivity activity, String currentUserId){
		AkiApplication.isLoggedIn();
		if ( activity.locationServicesConnected() ){
			activity.startPeriodicLocationUpdates();
		}

		AkiInternalStorageUtil.setCurrentUser(activity.getApplicationContext(), currentUserId);

		final LinearLayout chatArea = (LinearLayout) activity.findViewById(R.id.com_lespi_aki_main_chat);
		final RelativeLayout loginArea = (RelativeLayout) activity.findViewById(R.id.com_lespi_aki_main_login);
		chatArea.setVisibility(View.VISIBLE);
		loginArea.setVisibility(View.GONE);

		SlidingMenu slidingMenu = activity.getSlidingMenu();
		slidingMenu.setSlidingEnabled(true);
	}

	private void refreshSettings(final AkiMainActivity activity, final Session currentSession, 
			final GraphUser currentUser, final AsyncCallback callback) {

		activity.getSettingsFragment().refreshSettings(activity, currentSession, currentUser, callback);
	}

	private void refreshReceivedMessages(final AkiMainActivity activity, final Session session, final GraphUser currentUser) {

		final Context context = activity.getApplicationContext();

		final AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(activity.getApplicationContext());
		chatAdapter.setCurrentUser(currentUser);
		chatAdapter.setCurrentSession(session);

		new AsyncTask<Void, Void, List<JsonObject>>(){

			@Override
			protected List<JsonObject> doInBackground(Void... params) {
				JsonArray messages = AkiInternalStorageUtil.retrieveMessages(context,
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
			}
		}.execute();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		uiHelper = new UiLifecycleHelper(getActivity(), callback);
		uiHelper.onCreate(savedInstanceState);
	}

	@Override
	public void onResume() {
		super.onResume();

		Session session = Session.getActiveSession();
		if (session != null &&
				(session.isOpened() || session.isClosed()) ) {
			onSessionStateChange(session, session.getState(), null);
		}

		uiHelper.onResume();

		final AkiMainActivity activity = (AkiMainActivity) getActivity();
		if ( activity == null ){
			Log.e(AkiApplication.TAG, "onResume event called but there is no MainActivity alive, so ignored session change event.");
			return;
		}

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
			switchToLoginArea(activity, true);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		uiHelper.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onPause() {
		super.onPause();
		uiHelper.onPause();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		uiHelper.onDestroy();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		uiHelper.onSaveInstanceState(outState);
	}
}