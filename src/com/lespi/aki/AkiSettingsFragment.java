package com.lespi.aki;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class AkiSettingsFragment extends AkiFragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		
		View view = inflater.inflate(R.layout.aki_settings, container, false);
		return view;
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