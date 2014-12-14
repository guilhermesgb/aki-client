package com.lespi.aki;


import java.util.Set;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockFragment;
import com.lespi.aki.utils.AkiInternalStorageUtil;

public class AkiMutualFragment extends SherlockFragment {

	@SuppressLint("InflateParams")
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.aki_mutual_interests, container, false);
	}

	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
        
		final AkiMutualAdapter mutualAdapter = AkiMutualAdapter.getInstance(getActivity().getApplicationContext());
		Set<String> values = AkiInternalStorageUtil.retrieveMatches(getActivity().getApplicationContext());
		mutualAdapter.addAll(values);
		
		ListView listView = (ListView) getActivity().findViewById(R.id.com_lespi_aki_main_mutual_list);
		listView.setAdapter(mutualAdapter);
		listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
		listView.setWillNotCacheDrawing(true);
	}
	
}