package com.lespi.aki;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphUser;
import com.facebook.widget.LoginButton;
import com.lespi.aki.json.JsonArray;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiServerUtil;
import com.parse.internal.AsyncCallback;

public class AkiChatFragment extends AkiFragment{

	private UiLifecycleHelper uiHelper;

	private Session.StatusCallback callback = new Session.StatusCallback() {
		@Override
		public void call(Session session, SessionState state, Exception exception) {
			onSessionStateChange(session, state, exception);
		}
	};

	@Override
	public View onCreateView(LayoutInflater inflater,
			ViewGroup container, Bundle savedInstanceState){

		View view = inflater.inflate(R.layout.aki_main_frame, container, false);
		LoginButton authButton = (LoginButton) view.findViewById(R.id.com_lespi_aki_main_login_auth_btn);
		authButton.setFragment(this);
		authButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
		return view;
	}

	private void onSessionStateChange(final Session session, SessionState state, Exception exception) {
		if (state.isOpened()) {

			Request.newMeRequest(session, new Request.GraphUserCallback() {

				@Override
				public void onCompleted(final GraphUser user, Response response) {
					if ( user != null ){

						switchToChatArea();
						AkiServerUtil.sendPresenceToServer(getActivity().getApplicationContext(), user.getId(), new AsyncCallback() {
							
							@Override
							public void onSuccess(Object response) {
								JsonObject responseJSON = (JsonObject) response;
								AkiServerUtil.enterChatRoom(getActivity().getApplicationContext(), responseJSON.get("chat_room").asString());
								refreshReceivedMessages(getActivity().getApplicationContext(), session, user);
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

			switchToLoginArea();
			if ( AkiServerUtil.isActiveOnServer() ){
				AkiServerUtil.leaveServer(getActivity().getApplicationContext());
			}
		}
	}

	public void switchToLoginArea(){
		AkiServerUtil.leaveChatRoom(getActivity().getApplicationContext());
		final LinearLayout chatArea = (LinearLayout) this.getActivity().findViewById(R.id.com_lespi_aki_main_chat);
		final LinearLayout loginArea = (LinearLayout) this.getActivity().findViewById(R.id.com_lespi_aki_main_login);
		chatArea.setVisibility(View.GONE);
		loginArea.setVisibility(View.VISIBLE);
	}

	public void switchToChatArea(){
		final LinearLayout chatArea = (LinearLayout) this.getActivity().findViewById(R.id.com_lespi_aki_main_chat);
		final LinearLayout loginArea = (LinearLayout) this.getActivity().findViewById(R.id.com_lespi_aki_main_login);
		chatArea.setVisibility(View.VISIBLE);
		loginArea.setVisibility(View.GONE);
	}

	public void sendMessage() {

		final EditText chatBox = (EditText) getActivity().findViewById(R.id.com_lespi_aki_main_chat_input);
		if ( !chatBox.getText().toString().trim().isEmpty() ){
			AkiServerUtil.sendMessage(getActivity().getApplicationContext(), chatBox.getText().toString(), new AsyncCallback() {
				
				@Override
				public void onSuccess(Object response) {
					chatBox.setText("");
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

	public void refreshReceivedMessages(final Context context, final Session session, final GraphUser currentUser) {

		new AsyncTask<Void, Void, List<JsonObject>>(){

			@Override
			protected List<JsonObject> doInBackground(Void... params) {
				try {
					JsonArray messages = AkiInternalStorageUtil.retrieveMessages(context,
							AkiInternalStorageUtil.getCurrentChatRoom(context));
					return AkiChatAdapter.toJsonObjectList(messages);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
					Log.e(AkiApplication.TAG, "No current chat room address is set, so could not retrieve messages!");
				} catch (IOException e) {
					Log.e(AkiApplication.TAG, "A problem happened while trying to retrieve current chat room address!");
					e.printStackTrace();
				}
				return null;
			}
			
			@Override
			public void onPostExecute(List<JsonObject> messages){
				
				AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(getActivity().getApplicationContext());
				chatAdapter.setCurrentUser(currentUser);
				chatAdapter.setCurrentSession(session);
				chatAdapter.clear();
				if ( messages != null ){
					chatAdapter.addAll(messages);
				}
				ListView listView = (ListView) getActivity().findViewById(R.id.com_lespi_aki_main_messages_list);
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

		session = Session.getActiveSession();
		if (session == null || !(session.isOpened() || session.isClosed()) ) {
			AkiServerUtil.getPresenceFromServer(getActivity().getApplicationContext(), new AsyncCallback() {
				
				@Override
				public void onSuccess(Object response) {
					AkiServerUtil.leaveServer(getActivity().getApplicationContext());
				}
				
				@Override
				public void onFailure(Throwable failure) {
					Log.e(AkiApplication.TAG, "Could not get presence from server.");
					failure.printStackTrace();
				}
				
				@Override
				public void onCancel() {
					Log.e(AkiApplication.TAG, "Endpoint:getPresenceFromServer callback canceled.");
				}
			});
			switchToLoginArea();
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

	@Override
	public void attached(Activity activity){
		/* Do something? */
	}
	
	@Override
	public void createOptions(Menu menu) {
		if ( !isAttachedToActivity() ){
			return;
		}
		/*
		 * Show options?
		 */
	}

	@Override
	public boolean menuItemSelected(int featureId, MenuItem item) {
		return false;
	}
}