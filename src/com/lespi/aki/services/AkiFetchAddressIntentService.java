package com.lespi.aki.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.IntentService;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

public class AkiFetchAddressIntentService extends IntentService {

	public AkiFetchAddressIntentService(String name) {
		super(name);
	}
	
	public AkiFetchAddressIntentService() {
		super("AkiFetchAddressIntentService");
	}

	protected ResultReceiver receiver;
	
	@Override
	protected void onHandleIntent(Intent intent) {
	    String errorMessage = "";

	    receiver = intent.getParcelableExtra("receiver");
	    // Get the location passed to this service through an extra.
	    final Location location = intent.getParcelableExtra("location");
	    
		Geocoder geocoder = new Geocoder(this, Locale.US);
		List<Address> addresses = null;
		
		try {
			addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
		} catch (IOException exception) {
			errorMessage = "Service unavailable!";
			Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_SHORT).show();
			Log.e("AkiFetchAddressIntentService", errorMessage, exception);
		} catch (IllegalArgumentException exception) {
			errorMessage = "Invalid lat or long values: (" + location.getLatitude() + ", " + location.getLongitude() + "!" ;
			Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_SHORT).show();
			Log.e("AkiFetchAddressIntentService", errorMessage, exception);
		}
		
		if (addresses == null || addresses.isEmpty()) {
			if (errorMessage.isEmpty()) {
				errorMessage = "No address found!";
				Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_SHORT).show();
				Log.e("AkiFetchAddressIntentService", errorMessage);				
			}
			deliverResultToReceiver(1, errorMessage);
		} else {
			Address address = addresses.get(0);
			ArrayList<String> addressFragments = new ArrayList<String>();
			
	        // Fetch the address lines using getAddressLine,
	        // join them, and send them to the thread.
	        for(int i = 0; i < address.getMaxAddressLineIndex(); i++) {
	            addressFragments.add(address.getAddressLine(i));
	        }
	        Log.i("AkiFetchAddressIntentService", "Address found!");
	        deliverResultToReceiver(0, TextUtils.join(System.getProperty("line.separator"), addressFragments));
		}
	}
	
    private void deliverResultToReceiver(int resultCode, String message) {
        Bundle bundle = new Bundle();
        bundle.putString("address", message);
        receiver.send(resultCode, bundle);
    }
}