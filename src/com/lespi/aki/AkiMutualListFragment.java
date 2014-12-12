package com.lespi.aki;


import java.util.Set;

import com.lespi.aki.utils.AkiInternalStorageUtil;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

public class AkiMutualListFragment extends android.support.v4.app.ListFragment {

	@SuppressLint("InflateParams")
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.aki_mutual_interests, null);
	}

	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
        
        String[] noValue = new String[] { "No matches as of yet" };
        Set<String> values = AkiInternalStorageUtil.retrieveMatches(getActivity().getApplicationContext());
        
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this.getActivity(),
          android.R.layout.simple_list_item_1, android.R.id.text1, values.size() > 0 ? values.toArray(new String[values.size()]) : noValue);
		setListAdapter(adapter);
	}
	
}