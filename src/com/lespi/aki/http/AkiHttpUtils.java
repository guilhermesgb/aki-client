package com.lespi.aki.http;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutionException;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.util.Log;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;

public abstract class AkiHttpUtils {

	private static final String API_LOCATION = "lespi-server.herokuapp.com";

	private static class HttpRequestExecutor extends AsyncTask<String, Void, JsonObject>{

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
							urlConn.setChunkedStreamingMode(payload.length());
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
			JsonValue responseCode = response.get("code");
			if ( responseCode != null && responseCode.asInt() != 200 ){
				Log.e(AkiApplication.TAG, "HTTP Request fail.");
			}
			JsonObject content = response.get("content").asObject();
			String endpointResponseCode = content.get("code").asString();
			if ( endpointResponseCode.equals("ok") ){
				Log.i(AkiApplication.TAG, "HTTP Request success. Server Endpoint success: " + content.get("server").asString());
			}
			else if ( endpointResponseCode.equals("error") ){
				Log.i(AkiApplication.TAG, "HTTP Request success. Server Endpoint error: " + content.get("server").asString());
			}
			else{
				Log.i(AkiApplication.TAG, "HTTP Request success. Server Endpoint problem: " + endpointResponseCode);
			}
		}
	}
	
	private static JsonObject doHttpRequest(String method, String url, JsonObject headers, JsonObject payload){

		HttpRequestExecutor executor = (HttpRequestExecutor) new HttpRequestExecutor();
		executor.execute(method, "https://" + API_LOCATION + url,
				headers != null ? headers.toString().trim() : null,
				payload != null ? payload.toString().trim() : null);

		try {

			JsonObject response = executor.get();
			JsonValue responseCode = response.get("code");
			if ( responseCode != null && responseCode.asInt() == 200 ){
				JsonObject content = response.get("content").asObject();
				return content;
			}
			return null;
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static JsonObject doGETHttpRequest(String url){
		Log.i(AkiApplication.TAG, "GET " + url);
		return doHttpRequest("GET", url, getBasicHeaders(), null);
	}
	
	public static JsonObject doPOSTHttpRequest(String url){
		Log.i(AkiApplication.TAG, "POST " + url);
		return doHttpRequest("POST", url, getBasicHeaders(), null);
	}

	public static JsonObject doPOSTHttpRequest(String url, JsonObject payload){
		Log.i(AkiApplication.TAG, "POST " + url);
		return doHttpRequest("POST", url, getBasicHeaders(), payload);
	}
	
	private static JsonObject getBasicHeaders(){
		JsonObject headers = new JsonObject();
		headers.add("Host", API_LOCATION);
		headers.add("Content-Type", "application/json; charset=utf-8");
		headers.add("Accept", "application/json");		
		return headers;
	}
}