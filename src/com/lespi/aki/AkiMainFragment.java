package com.lespi.aki;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

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

import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphUser;
import com.facebook.widget.LoginButton;
import com.lespi.aki.json.JsonArray;
import com.lespi.aki.json.JsonObject;

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
		LoginButton authButton = (LoginButton) view.findViewById(R.id.authButton);
		authButton.setFragment(this);
		authButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
		return view;
	}

	private void onSessionStateChange(Session session, SessionState state, Exception exception) {
		if (state.isOpened()) {
		
			Request.newMeRequest(session, new Request.GraphUserCallback() {

				  @Override
				  public void onCompleted(GraphUser user, Response response) {
					  if ( user != null ){

                          switchToChatArea();
                          AkiServerCalls.sendPresenceToServer(getActivity().getApplicationContext(), user.getId());
                          AkiServerCalls.enterChatRoom(getActivity().getApplicationContext());
					  }
				  }
				}).executeAsync();
	    } else if (state.isClosed()) {
	    	
	    	switchToLoginArea();
			if ( AkiServerCalls.isActiveOnServer() ){
				AkiServerCalls.leaveServer(getActivity().getApplicationContext());
			}
	    }
	}
	
	public void switchToLoginArea(){
		AkiServerCalls.leaveChatRoom(getActivity().getApplicationContext());
		final LinearLayout chatArea = (LinearLayout) this.getActivity().findViewById(R.id.chatArea);
		final LinearLayout loginArea = (LinearLayout) this.getActivity().findViewById(R.id.loginArea);
		chatArea.setVisibility(View.GONE);
    	loginArea.setVisibility(View.VISIBLE);
	}
	
	public void switchToChatArea(){
		final LinearLayout chatArea = (LinearLayout) this.getActivity().findViewById(R.id.chatArea);
		final LinearLayout loginArea = (LinearLayout) this.getActivity().findViewById(R.id.loginArea);
		chatArea.setVisibility(View.VISIBLE);
    	loginArea.setVisibility(View.GONE);
	}

	public void sendMessage() {
		
		EditText chatBox = (EditText) getActivity().findViewById(R.id.chatBox);
		AkiServerCalls.sendMessage(getActivity().getApplicationContext(), chatBox.getText().toString());		
	}
	
	private JsonArray retrieveLastXMessages(Context context, String chat_room, int amount) {
		
		JsonArray lastXMessages = new JsonArray();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(context.openFileInput(chat_room)));
			String messageRaw;
			while ((messageRaw = reader.readLine()) != null && amount > 0) {
				String sender = messageRaw.substring(0, messageRaw.indexOf(":["));
				String content = messageRaw.substring(messageRaw.indexOf(":["), messageRaw.lastIndexOf("]"));
				JsonObject message = new JsonObject();
				message.add("sender", sender);
				message.add("message", content);
				lastXMessages.add(message);
				amount--;
			}
			reader.close();
		} catch (FileNotFoundException e) {
			Log.i(AkiApplication.TAG, "There are no messages in chat room " + chat_room + ".");
		} catch (IOException e) {
			Log.e(AkiApplication.TAG, "Could not retrieve last " + amount + " messages in chat room " + chat_room + ".");
			e.printStackTrace();
		}
		return lastXMessages;
	}
	
	public void refreshReceivedMessages() {

		Context context = getActivity().getApplicationContext();
		try {
			JsonArray messages = retrieveLastXMessages(context, AkiServerCalls.getCurrentChatRoom(context), 10);
			if ( messages.size() > 0 ){
				ListView listView = (ListView) getActivity().findViewById(R.id.listMessages);
				if (listView != null){
					/**
					 * DO STUFF WITH THE LIST VIEW AND THE LAST 10 INCOMING MESSAGES
					 */
				}
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
	    	if ( AkiServerCalls.getPresenceFromServer(getActivity().getApplicationContext()) ){
	    		AkiServerCalls.leaveServer(getActivity().getApplicationContext());
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