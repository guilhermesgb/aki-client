package com.lespi.aki;

import android.app.Activity;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public abstract class AkiFragment extends SherlockFragment{

	public abstract void attached(Activity activity);
	
	public abstract void createOptions(Menu menu);
	
	public abstract boolean menuItemSelected(int featureId, MenuItem item);

	public boolean isAttachedToActivity() {
		return !isDetached() && getActivity() != null;
	}
}