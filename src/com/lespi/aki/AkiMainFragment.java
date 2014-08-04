package com.lespi.aki;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphUser;
import com.facebook.widget.LoginButton;

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