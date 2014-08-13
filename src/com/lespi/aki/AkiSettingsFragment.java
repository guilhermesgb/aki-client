package com.lespi.aki;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.actionbarsherlock.app.SherlockFragment;
import com.facebook.widget.LoginButton;

public class AkiSettingsFragment extends SherlockFragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		
		View view = inflater.inflate(R.layout.aki_settings_frame, container, false);
		LoginButton logoutButton = (LoginButton) view.findViewById(R.id.com_lespi_aki_main_settings_logout_btn);
		logoutButton.setFragment(this);
		return view;
	}
}