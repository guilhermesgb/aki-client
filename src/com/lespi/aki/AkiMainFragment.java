package com.lespi.aki;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
		final TextView chatRoom = (TextView) this.getActivity().findViewById(R.id.chatRoom);
		final TextView loginRoom = (TextView) this.getActivity().findViewById(R.id.loginRoom);

		if (state.isOpened()) {
		
			Request.newMeRequest(session, new Request.GraphUserCallback() {

				  // callback after Graph API response with user object
				  @Override
				  public void onCompleted(GraphUser user, Response response) {
					  if ( user != null ){
						  loginRoom.setVisibility(View.GONE);
						  chatRoom.setVisibility(View.VISIBLE);
						  AkiServerCalls.sendPresenceToServer(getActivity().getApplicationContext(), user.getId());
					  }
				  }
				}).executeAsync();
	    } else if (state.isClosed()) {
			loginRoom.setVisibility(View.VISIBLE);
			chatRoom.setVisibility(View.GONE);
			if ( AkiServerCalls.isActiveOnServer() ){
				AkiServerCalls.leaveServer(getActivity().getApplicationContext());
			}
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