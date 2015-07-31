package com.lespi.aki.utils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.parse.internal.AsyncCallback;

public abstract class AkiHttpUploadUtil {

	private static final String API_LOCATION = "lespi-server.herokuapp.com";

	public static class Upload {

		public String imageName;
		public Bitmap imageBitmap;
		public Context context;
		
		public boolean processed = false;
		public List<byte[]> buffers = new LinkedList<byte[]>();
		public int lengthInBytes;
		
		private static final int MAX_BUFFER_SIZE = 16384;
		
		public Upload(Context context, String imageName, Bitmap imageBitmap){
			this.imageName = imageName + ".png";
			this.imageBitmap = imageBitmap;
			this.context = context;
		}
		
		public void process(){

			String temporaryName = "temporary_" + this.imageName;
			
			try {
				FileOutputStream fos = context.openFileOutput(temporaryName, Context.MODE_PRIVATE);
				processed = imageBitmap.compress(CompressFormat.PNG, 100, fos);
				fos.close();
				
				if ( processed ){
					FileInputStream fis = context.openFileInput(temporaryName);
					
					int bytesAvailable = fis.available();
					int bufferSize = Math.min(bytesAvailable, MAX_BUFFER_SIZE);
					byte[] currentBuffer = new byte[bufferSize];
					
					int bytesRead = fis.read(currentBuffer, 0, bufferSize);
					lengthInBytes += bytesRead;

					while ( bytesRead > 0 ){
						buffers.add(currentBuffer);

						bytesAvailable = fis.available();
						bufferSize = Math.min(bytesAvailable, MAX_BUFFER_SIZE);
						currentBuffer = new byte[bufferSize];
						
						bytesRead = fis.read(currentBuffer, 0, bufferSize);
						lengthInBytes += bytesRead;
					}
					fis.close();
					File file = new File(context.getFilesDir(), temporaryName);
					file.delete();
				}
				
			} catch (FileNotFoundException e) {
				processed = false;
				e.printStackTrace();
			} catch (IOException e) {
				processed = false;
				e.printStackTrace();
			}
		}
	}
	
	private static class HttpUploadExecutor extends AsyncTask<Upload, Void, Void>{

		private final AsyncCallback callback;
		public HttpUploadExecutor(AsyncCallback callback){
			this.callback = callback;
		}

		@SuppressLint("DefaultLocale")
		@Override
		protected Void doInBackground(Upload... params) {

			try {

				String lineEnd = "\r\n";
				String twoHyphens = "--";
				String uuid = UUID.randomUUID().toString();
				uuid = uuid.split("-")[uuid.split("-").length-1];
				String boundary = "----------------------------" + uuid;
				
				Upload upload = params[0]; upload.process();
				if ( !upload.processed ){
					Log.e(AkiApplication.TAG, "Upload image must be processed before upload!");
					return null;
				}
				
				URL url = new URL("https://" + API_LOCATION + "/upload");
				URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(),
						url.getPort(), url.getPath(), url.getQuery(), url.getRef());
				url = uri.toURL();

				HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
				try{
					urlConn.setDoInput(true);
					urlConn.setDoOutput(true);
					urlConn.setUseCaches(false);
					urlConn.setRequestMethod("PUT");

					StringBuilder uploadHeader = new StringBuilder();
					uploadHeader.append(twoHyphens + boundary + lineEnd);
					uploadHeader.append("Content-Disposition: form-data; name=\"filename\"; filename=\"" + upload.imageName + "\"" + lineEnd);
					uploadHeader.append("Content-Type: image/png" + lineEnd);
					uploadHeader.append("Content-Transfer-Encoding: binary" + lineEnd);
					uploadHeader.append(lineEnd);

					StringBuilder uploadFooter = new StringBuilder();
					uploadFooter.append(lineEnd + twoHyphens + boundary + twoHyphens + lineEnd);

					int uploadLength = uploadHeader.toString().getBytes().length
							+ upload.lengthInBytes + uploadFooter.toString().getBytes().length;
					urlConn.setRequestProperty("Accept", "application/json");
					urlConn.setRequestProperty("Content-Length", Integer.toString(uploadLength));
					urlConn.setRequestProperty("Expect", "100-continue");
					urlConn.setRequestProperty("Content-Type", "multipart/form-data; boundary="+boundary);
					urlConn.connect();

					DataOutputStream dos = new DataOutputStream(urlConn.getOutputStream());

					dos.write(uploadHeader.toString().getBytes());
					for ( byte[] buffer : upload.buffers ){
						dos.write(buffer);
					}
					dos.write(uploadFooter.toString().getBytes());
					dos.flush();
					dos.close();
					
					urlConn.setConnectTimeout(5000);
					int responseCode = urlConn.getResponseCode();
					
					String responseBody = "";
					if ( responseCode == 200 ){
						try {
							BufferedReader in = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
							String inputLine;
							StringBuffer response = new StringBuffer();
							
							while ((inputLine = in.readLine()) != null) {
								response.append(inputLine);
							}
							in.close();
							responseBody = response.toString();
						} catch(IOException e){
							e.printStackTrace();
							return null;
						}

						JsonObject response = JsonValue.readFrom(responseBody).asObject();
						JsonValue endpointResponseCode = response.get("code");
						if ( endpointResponseCode.asString().equals("ok") ){
							Log.i(AkiApplication.TAG, "HTTP Upload Request success. Server Endpoint success: " + response.get("server").asString());
							if ( callback != null ){
								callback.onSuccess(response);
							}
							return null;
						}
						else if ( endpointResponseCode.asString().equals("error") ){
							Log.e(AkiApplication.TAG, "HTTP Upload Request success. Server Endpoint error: " + response.get("server").asString());
						}
						else{
							Log.e(AkiApplication.TAG, "HTTP Upload Request success. Server Endpoint problem: " + endpointResponseCode.asString());
						}
					}
					Log.e(AkiApplication.TAG, "HTTP Upload Request fail with code: " + responseCode);
					callback.onFailure(null);
					return null;
				}
				finally{
					urlConn.disconnect();
				}
			} catch (MalformedURLException e) {
				Log.e(AkiApplication.TAG, "HTTP Upload Request fail.");
				e.printStackTrace();
			} catch (IOException e){
				Log.e(AkiApplication.TAG, "HTTP Upload Request fail.");
				e.printStackTrace();
			} catch (Exception e){
				Log.e(AkiApplication.TAG, "HTTP Upload Request fail.");
				e.printStackTrace();
			}
			return null;
		}
	}

	public static void doHttpUpload(Context context, final String filename, final Bitmap imageBitmap, AsyncCallback callback){
		Log.i(AkiApplication.TAG, "UPLOAD " + filename);
		if ( !isConnectedToTheInternet(context) ){
			if ( callback != null ){
				callback.onCancel();
			}
			return;
		}
		HttpUploadExecutor executor = (HttpUploadExecutor) new HttpUploadExecutor(callback);
		executor.execute(new Upload(context, filename, imageBitmap));
	}

	public static boolean isConnectedToTheInternet(Context context) {
		final ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		final NetworkInfo activeNetwork = connManager.getActiveNetworkInfo();
		return (activeNetwork != null && activeNetwork.getState() == NetworkInfo.State.CONNECTED);
	}
}