package com.lespi.aki;

import java.io.FileNotFoundException;
import java.io.IOException;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphUser;
import com.facebook.widget.LoginButton;
import com.lespi.aki.json.JsonArray;
import com.lespi.aki.utils.AkiInternalStorageUtil;
import com.lespi.aki.utils.AkiServerUtil;

public class AkiMainFragment extends Fragment{

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

		View view = inflater.inflate(R.layout.aki_main, container, false);
		LoginButton authButton = (LoginButton) view.findViewById(R.id.com_lespi_aki_main_login_auth_btn);
		authButton.setFragment(this);
		authButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
		return view;
	}

	private void onSessionStateChange(final Session session, SessionState state, Exception exception) {
		if (state.isOpened()) {
		
			Request.newMeRequest(session, new Request.GraphUserCallback() {

				  @Override
				  public void onCompleted(GraphUser user, Response response) {
					  if ( user != null ){

                          switchToChatArea();
                          AkiServerUtil.sendPresenceToServer(getActivity().getApplicationContext(), user.getId());
                          AkiServerUtil.enterChatRoom(getActivity().getApplicationContext());
                          refreshReceivedMessages(getActivity().getApplicationContext(), session, user);
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
		
		EditText chatBox = (EditText) getActivity().findViewById(R.id.com_lespi_aki_main_chat_input);
		AkiServerUtil.sendMessage(getActivity().getApplicationContext(), chatBox.getText().toString());
	}
	
	public void refreshReceivedMessages(Context context, Session session, GraphUser currentUser) {

		try {
			JsonArray messages = AkiInternalStorageUtil.retrieveMessages(context,
					AkiInternalStorageUtil.getCurrentChatRoom(context));
			if ( messages.size() > 0 ){

				if ( AkiApplication.DEBUG_MODE ){
					CharSequence toastText = messages.size() + " messages in this chat room!!";
					Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
					toast.show();
				}

				ListView listView = (ListView) getActivity().findViewById(R.id.com_lespi_aki_main_messages_list);

				AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(getActivity().getApplicationContext());
				chatAdapter.setCurrentUser(currentUser);
				chatAdapter.setCurrentSession(session);
				chatAdapter.clear();
				chatAdapter.addAll(AkiChatAdapter.toJsonObjectList(messages));

				listView.setAdapter(chatAdapter);
			}
			else {
				/**
				 * SHOW A GOOD "WELCOME TO THIS CHAT ROOM" MESSAGE!
				 */
				CharSequence toastText = "No messages yet in this chat room!!";
				Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_SHORT);
				toast.show();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			Log.e(AkiApplication.TAG, "No current chat room address is set, so could not retrieve messages!");
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "A problem happened while trying to retrieve current chat room address!");
			e.printStackTrace();
		}
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
	    if (session == null || 
	           !(session.isOpened() || session.isClosed()) ) {
	    	if ( AkiServerUtil.getPresenceFromServer(getActivity().getApplicationContext()) ){
	    		AkiServerUtil.leaveServer(getActivity().getApplicationContext());
	    	}
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

}