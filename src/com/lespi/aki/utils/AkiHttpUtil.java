package com.lespi.aki.utils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.parse.internal.AsyncCallback;

public abstract class AkiHttpUtil {

	private static final String API_LOCATION = "lespi-server.herokuapp.com";

	private static class HttpRequestExecutor extends AsyncTask<String, Void, JsonObject>{

		private final AsyncCallback callback;
		public HttpRequestExecutor(AsyncCallback callback){
			this.callback = callback;
		}
		
		@SuppressLint("DefaultLocale")
		@Override
		protected JsonObject doInBackground(String... params) {

			try {
				
				String method = params[0];
				URL url = new URL(params[1]);
				URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(),
						url.getPort(), url.getPath(), url.getQuery(), url.getRef());
				url = uri.toURL();
				String headers = params[2];
				String payload = params[3];
			
				HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
				try{
					
					if ( headers != null ){

						JsonObject headersJSON = JsonValue.readFrom(headers).asObject();
						for ( String key : headersJSON.names() ){
							String value = headersJSON.get(key).asString();
							urlConn.setRequestProperty(key, value);
						}
					}
					
					if ( method.toUpperCase().equals("POST") ){
						urlConn.setRequestMethod("POST");
						if ( payload != null ){
							urlConn.setDoOutput(true);
							OutputStream out = new BufferedOutputStream(urlConn.getOutputStream(), payload.length());
							out.write(payload.getBytes());
						}
					}
					
					urlConn.connect();
					
					int responseCode = urlConn.getResponseCode();
					String responseBody = "";

					try {
						BufferedReader in = new BufferedReader(
						        new InputStreamReader(urlConn.getInputStream()));
						String inputLine;
						StringBuffer response = new StringBuffer();
				 
						while ((inputLine = in.readLine()) != null) {
							response.append(inputLine);
						}
						in.close();
						responseBody = response.toString();
					} catch(IOException e){
						e.printStackTrace();
					}
					
					JsonObject response = new JsonObject();
					response.add("code", responseCode);
					if ( responseCode == 200 ){
						response.add("content", JsonValue.readFrom(responseBody));
					}
					else{
						response.add("content", responseBody);
					}
					return response;
				}
				finally{
					urlConn.disconnect();
				}
				
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e){
				e.printStackTrace();
			} catch (Exception e){
				e.printStackTrace();
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(JsonObject response) {
			if ( response == null ){
				if ( callback != null ){
					callback.onCancel();
				}
				Log.d(AkiApplication.TAG, "No internet connection available.");
				return;
			}
			JsonValue responseCode = response.get("code");
			if ( responseCode != null && responseCode.asInt() != 200 ){
				Log.e(AkiApplication.TAG, "HTTP Request fail.");
				if ( callback != null ){
					callback.onCancel();
				}
				return;
			}
			JsonObject content = response.get("content").asObject();
			if ( content != null ){
				String endpointResponseCode = content.get("code").asString();
				if ( endpointResponseCode.equals("ok") ){
					Log.i(AkiApplication.TAG, "HTTP Request success. Server Endpoint success: " + content.get("server").asString());
					if ( callback != null ){
						callback.onSuccess(content);
					}
					return;
				}
				else if ( endpointResponseCode.equals("error") ){
					Log.e(AkiApplication.TAG, "HTTP Request success. Server Endpoint error: " + content.get("server").asString());
				}
				else{
					Log.e(AkiApplication.TAG, "HTTP Request success. Server Endpoint problem: " + endpointResponseCode);
				}
			}
			if ( callback != null ){
				callback.onFailure(new Exception("The HTTP Request was successful, but the Server Endpoint faced issues."));
			}
		}
	}
	
	private static void doHttpRequest(Context context, String method, String url, JsonObject headers, JsonObject payload, AsyncCallback callback){

		if ( !isConnectedToTheInternet(context) ){
			if ( callback != null ){
				callback.onCancel();
			}
			return;
		}
		
		HttpRequestExecutor executor = (HttpRequestExecutor) new HttpRequestExecutor(callback);
		executor.execute(method, "https://" + API_LOCATION + url,
				headers != null ? headers.toString().trim() : null,
				payload != null ? payload.toString().trim() : null);
	}
	
	public static void doGETHttpRequest(Context context, String url, AsyncCallback callback){
		Log.i(AkiApplication.TAG, "GET " + url);
		doHttpRequest(context, "GET", url, getBasicHeaders(), null, callback);
	}
	
	public static void doPOSTHttpRequest(Context context, String url, AsyncCallback callback){
		Log.i(AkiApplication.TAG, "POST " + url);
		doHttpRequest(context, "POST", url, getBasicHeaders(), null, callback);
	}

	public static void doPOSTHttpRequest(Context context, String url, JsonObject payload, AsyncCallback callback){
		Log.i(AkiApplication.TAG, "POST " + url + " " + payload.toString());
		doHttpRequest(context, "POST", url, getBasicHeaders(), payload, callback);
	}
	
	private static JsonObject getBasicHeaders(){
		JsonObject headers = new JsonObject();
		headers.add("Host", API_LOCATION);
		headers.add("Content-Type", "application/json; charset=utf-8");
		headers.add("Accept", "application/json");		
		return headers;
	}

	public static boolean isConnectedToTheInternet(Context context) {
	    final ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
	    final NetworkInfo activeNetwork = connManager.getActiveNetworkInfo();
	    return (activeNetwork != null && activeNetwork.getState() == NetworkInfo.State.CONNECTED);
	}
}