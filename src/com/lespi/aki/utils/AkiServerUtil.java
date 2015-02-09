package com.lespi.aki.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.lespi.aki.AkiApplication;
import com.lespi.aki.AkiChatAdapter;
import com.lespi.aki.AkiChatFragment;
import com.lespi.aki.AkiMainActivity;
import com.lespi.aki.AkiMutualAdapter;
import com.lespi.aki.AkiPrivateChatAdapter;
import com.lespi.aki.R;
import com.lespi.aki.json.JsonArray;
import com.lespi.aki.json.JsonObject;
import com.lespi.aki.json.JsonValue;
import com.lespi.aki.utils.AkiInternalStorageUtil.AkiLocation;
import com.parse.ParseInstallation;
import com.parse.PushService;
import com.parse.internal.AsyncCallback;

public class AkiServerUtil {

	private static boolean activeOnServer = false;

	public static synchronized boolean isActiveOnServer(){
		return activeOnServer;
	}

	public static synchronized void setActiveOnServer(boolean active){
		AkiServerUtil.activeOnServer = active;
		if ( active ){
			ParseInstallation installation = ParseInstallation.getCurrentInstallation();
			installation.put("inactive", false);
			installation.saveInBackground();
		}
		else {
			ParseInstallation installation = ParseInstallation.getCurrentInstallation();
			installation.put("inactive", true);
			installation.saveInBackground();
		}
	}

	public static synchronized void isServerUp(final Context context, final AsyncCallback callback){

		AkiHttpRequestUtil.doGETHttpRequest(context, "/", new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				callback.onSuccess(response);
			}

			@Override
			public void onFailure(Throwable failure) {
				AkiApplication.serverDown();
				callback.onFailure(failure);
			}

			@Override
			public void onCancel() {
				if ( AkiApplication.SERVER_DOWN ){
					callback.onFailure(new Exception("Server is down!"));
				}
				else{
					callback.onSuccess(null);
				}
			}
		});
	}

	public static synchronized void getPresenceFromServer(final Context context, final AsyncCallback callback){

		AkiHttpRequestUtil.doGETHttpRequest(context, "/presence", new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				JsonObject responseJSON = (JsonObject) response;
				if ( responseJSON.get("user_id") != null && !responseJSON.get("user_id").isNull() ){
					setActiveOnServer(true);
					callback.onSuccess(response);
				}
				else{
					setActiveOnServer(false);
					callback.onFailure(null);
				}
			}

			@Override
			public void onFailure(Throwable failure) {
				setActiveOnServer(false);
				callback.onFailure(failure);
			}

			@Override
			public void onCancel() {
				callback.onCancel();
			}
		});
	}

	public static synchronized void sendPresenceToServer(final Context context, final String userId, final AsyncCallback callback){

		JsonObject payload = new JsonObject();
		String firstName = AkiInternalStorageUtil.getCachedUserFirstName(context, userId);
		if ( firstName != null ){
			payload.add("first_name", firstName);
		}

		String fullName = AkiInternalStorageUtil.getCachedUserFullName(context, userId);
		if ( fullName != null ){
			payload.add("full_name", fullName);
		}

		String gender = AkiInternalStorageUtil.getCachedUserGender(context, userId);
		if ( gender != null ){
			payload.add("gender", gender);
		}

		String nickname = AkiInternalStorageUtil.getCachedUserNickname(context, userId);
		if ( nickname != null ){
			payload.add("nickname", nickname);
		}

		boolean anonymous = AkiInternalStorageUtil.getAnonymousSetting(context, userId);
		payload.add("anonymous", anonymous);
		AkiLocation location = AkiInternalStorageUtil.getCachedUserLocation(context, userId);
		if ( location != null ){
			JsonObject locationJSON = new JsonObject();
			locationJSON.add("lat", location.latitude);
			locationJSON.add("long", location.longitude);
			payload.add("location", locationJSON);
		}
		else{
			payload.add("location", "unknown");
		}

		AkiHttpRequestUtil.doPOSTHttpRequest(context, "/presence/"+userId, payload, new AsyncCallback(){

			@Override
			public void onSuccess(Object response) {
				setActiveOnServer(true);
				JsonObject responseJSON = (JsonObject) response;
				JsonValue nT = responseJSON.get("timestamp");
				if ( nT != null ){
					String nextTimestamp = nT.asString();
					AkiInternalStorageUtil.setLastServerTimestamp(context, nextTimestamp);
				}
				JsonValue updateMutualInterests = responseJSON.get("update_mutual_interests");
				if ( updateMutualInterests != null ){
					getMutualInterests(context);
				}
				if ( callback != null ){
					callback.onSuccess(response);
				}
			}

			@Override
			public void onFailure(Throwable failure) {
				if ( callback != null ){
					callback.onFailure(failure);
				}
			}

			@Override
			public void onCancel() {
				if ( callback != null ){
					callback.onCancel();
				}
			}
		});
	}

	public static synchronized void sendLikeToServer(final Context context, final String userId){

		AkiHttpRequestUtil.doPOSTHttpRequest(context, "/like/"+userId, new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				Log.e(AkiApplication.TAG, "User Liked "+userId);				
			}

			@Override
			public void onFailure(Throwable failure) {
				Log.e(AkiApplication.TAG, "Could not like user");
				failure.printStackTrace();
			}

			@Override
			public void onCancel() {
				Log.e(AkiApplication.TAG, "Endpoint:sendLikeToServer callback canceled.");
			}
		});
	}

	public static synchronized void sendDislikeToServer(Context context, final String userId) {

		AkiHttpRequestUtil.doPOSTHttpRequest(context, "/dislike/"+userId, new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				Log.e(AkiApplication.TAG, "User disliked "+userId);		
			}

			@Override
			public void onFailure(Throwable failure) {
				Log.e(AkiApplication.TAG, "Could not dislike user");
				failure.printStackTrace();
			}

			@Override
			public void onCancel() {
				Log.e(AkiApplication.TAG, "Endpoint:sendDislikeToServer callback canceled.");
			}
		});
	}

	public static synchronized void sendInactiveToServer(final Context context){

		AkiHttpRequestUtil.doPOSTHttpRequest(context, "/inactive", new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				JsonObject responseJSON = (JsonObject) response;
				String responseCode = responseJSON.get("code").asString();
				if ( responseCode.equals("ok") ){
					setActiveOnServer(false);
				}				
			}

			@Override
			public void onFailure(Throwable failure) {
				Log.e(AkiApplication.TAG, "Could not send inactive.");
				failure.printStackTrace();
			}

			@Override
			public void onCancel() {
				Log.e(AkiApplication.TAG, "Endpoint:sendInactiveToServer callback canceled.");
			}
		});
	}

	public static synchronized void sendExitToServer(final Context context, final AsyncCallback callback){

		AkiHttpRequestUtil.doPOSTHttpRequest(context, "/exit", new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				JsonObject responseJSON = (JsonObject) response;
				String responseCode = responseJSON.get("code").asString();
				if ( responseCode.equals("ok") ){
					setActiveOnServer(false);
					callback.onSuccess(response);
				}
			}

			@Override
			public void onFailure(Throwable failure) {
				Log.e(AkiApplication.TAG, "Could not send exit.");
				callback.onFailure(failure);
			}

			@Override
			public void onCancel() {
				Log.e(AkiApplication.TAG, "Endpoint:sendExitToServer callback canceled.");
				callback.onCancel();
			}
		});
	}

	public static synchronized void sendSkipToServer(final Context context, final AsyncCallback callback){

		AkiHttpRequestUtil.doPOSTHttpRequest(context, "/skip", new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				JsonObject responseJSON = (JsonObject) response;
				String responseCode = responseJSON.get("code").asString();
				if ( responseCode.equals("ok") ){
					callback.onSuccess(response);
				}
			}

			@Override
			public void onFailure(Throwable failure) {
				Log.e(AkiApplication.TAG, "Could not send exit.");
				callback.onFailure(failure);
			}

			@Override
			public void onCancel() {
				Log.e(AkiApplication.TAG, "Endpoint:sendExitToServer callback canceled.");
				callback.onCancel();
			}
		});
	}

	public static synchronized void enterChatRoom(Context context, String currentUserId,
			String newChatRoom, boolean shouldBeAnonymous) {

		Log.w(AkiApplication.TAG, "GOT INTO ENTER CHAT");
		
		boolean redirectedToNewChat = false;
		String currentChatRoom = AkiInternalStorageUtil.getCurrentChatRoom(context);
		if ( currentChatRoom == null ){
			Log.i(AkiApplication.TAG, "No current chat room address set.");
		}
		else if ( currentChatRoom.equals(newChatRoom) ){
			Log.i(AkiApplication.TAG, "No need to update current chat room, which" +
					" has address {" + currentChatRoom + "}.");
			PushService.subscribe(context, newChatRoom, AkiMainActivity.class);
			Log.i(AkiApplication.TAG, "Subscribed to chat room address {" + newChatRoom + "}.");
			return;
		}
		else{
			leaveChatRoom(context, currentUserId);
			redirectedToNewChat = true;
			Log.i(AkiApplication.TAG, "Had to leave current chat room address {" +
					currentChatRoom + "} because will be assigned to new chat room " +
					"address {" + newChatRoom + "}.");
			CharSequence toastText = context.getText(R.string.com_lespi_aki_toast_kicked_chat);
			Toast toast = Toast.makeText(context, toastText, Toast.LENGTH_LONG);
			toast.show();
		}

		if ( !PushService.getSubscriptions(context).contains(newChatRoom) ){
			PushService.subscribe(context, newChatRoom, AkiMainActivity.class);
			Log.i(AkiApplication.TAG, "Subscribed to chat room address {" + newChatRoom + "}.");
		}
		AkiInternalStorageUtil.setCurrentChatRoom(context, newChatRoom);
		Log.i(AkiApplication.TAG, "Current chat room set to chat room address {" + newChatRoom + "}.");

		if ( shouldBeAnonymous ){
			AkiInternalStorageUtil.setAnonymousSetting(context, currentUserId, true);
		}
		else {
			AkiInternalStorageUtil.setAnonymousSetting(context, currentUserId, false);
		}
		AkiInternalStorageUtil.wipeCachedGeofenceCenter(context);
		AkiInternalStorageUtil.cacheGeofenceRadius(context, -1);
		AkiInternalStorageUtil.willUpdateGeofence(context);
		AkiInternalStorageUtil.clearUserLikes(context);
		AkiInternalStorageUtil.cacheLikeMutualInterests(context);
		getMembersList(context, null);
		
		if ( redirectedToNewChat ){
			Log.w(AkiApplication.TAG, "Just gave a 'you've been redirected' message");
			AkiInternalStorageUtil.storeSystemMessage(context, newChatRoom,
					context.getResources().getString(R.string.com_lespi_aki_message_system_redirected_to_new_chat_room));
		}
		else{
			Log.w(AkiApplication.TAG, "Just gave a 'you've joined chat' message");
			AkiInternalStorageUtil.storeSystemMessage(context, newChatRoom,
					context.getResources().getString(R.string.com_lespi_aki_message_system_joined_new_chat_room));
		}

		AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(context);
			List<JsonObject> messages = AkiChatAdapter
					.toJsonObjectList(AkiInternalStorageUtil.retrieveMessages(context, newChatRoom));
			chatAdapter.clear();
			if ( messages != null ){
				chatAdapter.addAll(messages);
			}
		chatAdapter.notifyDataSetChanged();
	}

	public static synchronized void leaveChatRoom(Context context, String currentUserId) {

		if ( currentUserId != null ){
			AkiInternalStorageUtil.setAnonymousSetting(context, currentUserId, true);
		}

		String currentChatRoom = AkiInternalStorageUtil.getCurrentChatRoom(context);
		if ( currentChatRoom == null ){
			Log.i(AkiApplication.TAG, "No need to unsubscribe as no current chat room address is set.");
		}
		else{
			AkiInternalStorageUtil.wipeCurrentChatMembers(context);
			AkiInternalStorageUtil.clearTimestamps(context, currentChatRoom);
			PushService.unsubscribe(context, currentChatRoom);
			AkiInternalStorageUtil.setCurrentChatRoom(context, null);
			Log.i(AkiApplication.TAG, "Unsubscribed from chat room address {" + currentChatRoom + "}.");
			AkiInternalStorageUtil.removeCachedMessages(context, currentChatRoom);
		}
		
		Set<String> privateChatIds = new HashSet<String>();
		for ( String userId : AkiInternalStorageUtil.retrieveMatches(context) ){
			privateChatIds.add(buildPrivateChatId(context, userId));
		}
		
		for ( String remainingChatRoom : PushService.getSubscriptions(context) ){
			if ( !privateChatIds.contains(remainingChatRoom) ){
				PushService.unsubscribe(context, remainingChatRoom);
				Log.e(AkiApplication.TAG, "Cleanup -> unsubscribing from chat room address: {" + remainingChatRoom + "}.");
			}
		}
	}

	public static synchronized void getMembersList(final Context context, final AsyncCallback callback) {
		
		if ( AkiInternalStorageUtil.getCurrentChatRoom(context) == null ){
			return;
		}
		
		AkiHttpRequestUtil.doGETHttpRequest(context, "/members", new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				
				JsonObject members = ((JsonObject) response).get("members").asObject();

				boolean shouldRefreshMessages = true;
				boolean thereAreNewMessages = false;
				
				Set<String> currentMembers = AkiInternalStorageUtil.getCurrentChatMembers(context);
				if ( currentMembers.size() == 0 ){
					shouldRefreshMessages = false;
				}
				
				List<String> memberIds = new ArrayList<String>();
				for ( String memberId : members.names() ){
					memberIds.add(memberId);
					
					String currentUserId = AkiInternalStorageUtil.getCurrentUser(context);
					
					if ( !currentUserId.equals(memberId) ){
						JsonObject memberInfo = members.get(memberId).asObject();
						if ( memberInfo.get("full_name") != null && !memberInfo.get("full_name").isNull() ){
							AkiInternalStorageUtil.cacheUserFullName(context, memberId,
									memberInfo.get("full_name").asString());
						}
						if ( memberInfo.get("first_name") != null && !memberInfo.get("first_name").isNull() ){
							AkiInternalStorageUtil.cacheUserFirstName(context, memberId,
									memberInfo.get("first_name").asString());
						}
						if ( memberInfo.get("nickname") != null && !memberInfo.get("nickname").isNull() ){
							AkiInternalStorageUtil.cacheUserNickname(context, memberId,
									memberInfo.get("nickname").asString());
						}
						if ( memberInfo.get("gender") != null && !memberInfo.get("gender").isNull() ){
							AkiInternalStorageUtil.cacheUserGender(context, memberId,
									memberInfo.get("gender").asString());
						}
						if ( memberInfo.get("anonymous") != null && !memberInfo.get("anonymous").isNull() ){
							AkiInternalStorageUtil.setAnonymousSetting(context, memberId,
									memberInfo.get("anonymous").asBoolean());
						}						
					}
					
					if ( currentMembers.contains(memberId) ){
						currentMembers.remove(memberId);
					}
					else{
						AkiInternalStorageUtil.chatMemberHasJoined(context, memberId, shouldRefreshMessages);
						thereAreNewMessages = true;
					}
				}
				
				for ( String oldMemberId : currentMembers ){
					AkiInternalStorageUtil.chatMemberHasLeft(context, oldMemberId);
					thereAreNewMessages = true;
				}
				
				AkiMutualAdapter mutualAdapter = AkiMutualAdapter.getInstance(context);
				mutualAdapter.notifyDataSetChanged();
				AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(context);
				if ( shouldRefreshMessages && thereAreNewMessages ){
					String currentChatRoom = AkiInternalStorageUtil.getCurrentChatRoom(context);
					List<JsonObject> messages = AkiChatAdapter
							.toJsonObjectList(AkiInternalStorageUtil.retrieveMessages(context, currentChatRoom));
					chatAdapter.clear();
					if ( messages != null ){
						chatAdapter.addAll(messages);
					}
				}
				chatAdapter.notifyDataSetChanged();
				
				if ( callback != null ){
					callback.onSuccess(memberIds);
				}
			}

			@Override
			public void onFailure(Throwable failure) {
				if ( callback != null ){
					callback.onFailure(failure);
				}
			}

			@Override
			public void onCancel() {
				if ( callback != null ){
					callback.onCancel();
				}
			}
		});
	}
	
	public static String buildPrivateChatId(Context context,String secondId ){
		String currentId = AkiInternalStorageUtil.getCurrentUser(context);
		String chatId = "chat-" + (currentId.compareTo(secondId) <= 0 ? currentId + secondId : secondId + currentId);
		
		
		return chatId;
	}

	public static synchronized void getMutualInterests(final Context context) {

		AkiHttpRequestUtil.doGETHttpRequest(context, "/mutual", new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				Set<String> oldMutualInterests = AkiInternalStorageUtil.retrieveMatches(context);
				AkiInternalStorageUtil.wipeMatches(context);
				JsonArray mutualInterests = ((JsonObject) response).get("mutuals").asArray();
				for ( JsonValue interest : mutualInterests ){
					JsonObject interestJSON = interest.asObject();
					String userId = interestJSON.get("uid").asString();
					boolean notify = !oldMutualInterests.contains(userId);
					if ( !notify ){
						oldMutualInterests.remove(userId);
					}
					if ( interestJSON.get("nickname") != null && !interestJSON.get("nickname").isNull() ){
						String nickname = interestJSON.get("nickname").asString();
						AkiInternalStorageUtil.cacheUserNickname(context, userId, nickname);
					}
					if ( interestJSON.get("gender") != null && !interestJSON.get("gender").isNull() ){
						String gender = interestJSON.get("gender").asString();
						AkiInternalStorageUtil.cacheUserGender(context, userId, gender);
					}
					if ( interestJSON.get("first_name") != null && !interestJSON.get("first_name").isNull() ){
						String firstName = interestJSON.get("first_name").asString();
						AkiInternalStorageUtil.cacheUserFirstName(context, userId, firstName);
					}
					if ( interestJSON.get("full_name") != null && !interestJSON.get("full_name").isNull() ){
						String fullName = interestJSON.get("full_name").asString();
						AkiInternalStorageUtil.cacheUserFullName(context, userId, fullName);
					}
					AkiInternalStorageUtil.storeNewMatch(context, userId, notify);
				}
				for ( String userId : oldMutualInterests ){
					AkiServerUtil.sendDislikeToServer(context, userId);
					AkiInternalStorageUtil.cacheDislikeUser(context, userId);
				}
				AkiInternalStorageUtil.cacheLikeMutualInterests(context);
				
				AkiMutualAdapter mutualAdapter = AkiMutualAdapter.getInstance(context);
				mutualAdapter.clear();
				Set<String> values = AkiInternalStorageUtil.retrieveMatches(context);
				mutualAdapter.addAll(values);
				mutualAdapter.notifyDataSetChanged();
				
				AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(context);
				chatAdapter.notifyDataSetChanged();
			}

			@Override
			public void onFailure(Throwable failure) {
				Log.e(AkiApplication.TAG, "Could not get mutual interests list.");
				failure.printStackTrace();
			}

			@Override
			public void onCancel() {
				Log.e(AkiApplication.TAG, "Endpoint:getMutualInterests canceled.");
			}
		});
	}
	
	public static synchronized void removeMutualInterest(final Context context, final String userId) {

		AkiHttpRequestUtil.doDELETEHttpRequest(context, "/mutual/" + userId, new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				AkiInternalStorageUtil.removeMatch(context, userId);
				AkiServerUtil.sendDislikeToServer(context, userId);
				AkiInternalStorageUtil.cacheDislikeUser(context, userId);
				
				AkiMutualAdapter mutualAdapter = AkiMutualAdapter.getInstance(context);
				mutualAdapter.clear();
				Set<String> values = AkiInternalStorageUtil.retrieveMatches(context);
				mutualAdapter.addAll(values);
				mutualAdapter.notifyDataSetChanged();
				
				AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(context);
				chatAdapter.notifyDataSetChanged();
			}

			@Override
			public void onFailure(Throwable failure) {
				Log.e(AkiApplication.TAG, "Could not remove mutual interest with " + userId + "!");
				failure.printStackTrace();
			}

			@Override
			public void onCancel() {
				Log.e(AkiApplication.TAG, "Endpoint:removeMutualInterest canceled.");
			}
		});
	}
	
	public static synchronized void sendMessage(final Context context, String message, final AsyncCallback callback) {

		final String chatRoom = AkiInternalStorageUtil.getCurrentChatRoom(context);
		String currentUser = AkiInternalStorageUtil.getCurrentUser(context);
		if ( chatRoom == null || currentUser == null ){
			Log.e(AkiApplication.TAG, "Could not send message: no current_user_id or no current chat_room found.");
			callback.onCancel();
			return;
		}

		BigInteger temporaryTimestamp = new BigInteger(AkiInternalStorageUtil.getMostRecentTimestamp(context));
		temporaryTimestamp = temporaryTimestamp.multiply(BigInteger.TEN).multiply(BigInteger.TEN);
		temporaryTimestamp = temporaryTimestamp.add(new BigInteger(Integer.toString(new Random().nextInt(100))));

		final JsonObject temporaryMessage = AkiInternalStorageUtil.storeTemporaryMessage(context, chatRoom, currentUser,
				message, temporaryTimestamp.toString());

		AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(context);
		List<JsonObject> messages = AkiChatAdapter.toJsonObjectList(AkiInternalStorageUtil.retrieveMessages(context, chatRoom));

		chatAdapter.clear();
		if ( messages != null ){
			chatAdapter.addAll(messages);
		}
		chatAdapter.notifyDataSetChanged();

		AkiChatFragment.getInstance().externalRefreshAll();

		JsonObject payload = new JsonObject();
		payload.add("message", message);

		AkiHttpRequestUtil.doPOSTHttpRequest(context, "/message", payload, new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				AkiInternalStorageUtil.resetTimeout(context);
				restartGettingMessages(context);
				AkiInternalStorageUtil.removeTemporaryMessage(context, chatRoom, temporaryMessage);
				callback.onSuccess(response);
			}

			@Override
			public void onFailure(Throwable failure) {
				AkiInternalStorageUtil.removeTemporaryMessage(context, chatRoom, temporaryMessage);
				AkiChatFragment.getInstance().externalRefreshAll();
				callback.onFailure(failure);
			}

			@Override
			public void onCancel() {
				AkiInternalStorageUtil.removeTemporaryMessage(context, chatRoom, temporaryMessage);
				AkiChatFragment.getInstance().externalRefreshAll();
				callback.onCancel();
			}
		});
	}
	public static synchronized void sendPrivateMessage(final Context context, String message,final String userId, final AsyncCallback callback) {

		final String chatRoom = buildPrivateChatId(context, userId);
		String currentUser = AkiInternalStorageUtil.getCurrentUser(context);
		if ( chatRoom == null || currentUser == null ){
			Log.e(AkiApplication.TAG, "Could not send message: no current_user_id or no current chat_room found.");
			callback.onCancel();
			return;
		}

		BigInteger temporaryTimestamp = new BigInteger(AkiInternalStorageUtil.getMostRecentTimestamp(context));
		temporaryTimestamp = temporaryTimestamp.multiply(BigInteger.TEN).multiply(BigInteger.TEN);
		temporaryTimestamp = temporaryTimestamp.add(new BigInteger(Integer.toString(new Random().nextInt(100))));

		final JsonObject temporaryMessage = AkiInternalStorageUtil.storeTemporaryMessage(context, chatRoom, currentUser,
				message, temporaryTimestamp.toString());

		AkiPrivateChatAdapter chatAdapter = AkiPrivateChatAdapter.getInstance(context);
		List<JsonObject> messages = AkiPrivateChatAdapter.toJsonObjectList(AkiInternalStorageUtil.retrieveMessages(context, chatRoom));

		chatAdapter.clear();
		if ( messages != null ){
			chatAdapter.addAll(messages);
		}
		chatAdapter.notifyDataSetChanged();


		JsonObject payload = new JsonObject();
		payload.add("message", message);

		AkiHttpRequestUtil.doPOSTHttpRequest(context, "/private_message/"+userId, payload, new AsyncCallback() {

			@Override
			public void onSuccess(Object response) {
				AkiInternalStorageUtil.resetTimeout(context,chatRoom);
				AkiInternalStorageUtil.removeTemporaryMessage(context, chatRoom, temporaryMessage);
				getPrivateMessages(context, userId);
				callback.onSuccess(response);
			}

			@Override
			public void onFailure(Throwable failure) {
				AkiInternalStorageUtil.removeTemporaryMessage(context, chatRoom, temporaryMessage);
				callback.onFailure(failure);
			}

			@Override
			public void onCancel() {
				AkiInternalStorageUtil.removeTemporaryMessage(context, chatRoom, temporaryMessage);
				callback.onCancel();
			}
		});
	}

	public static class GetMessages implements Runnable {

		private final Context context;
		private final Handler handler;
		private int tolerance = 0;

		public GetMessages(Context context, Handler handler){
			this.context = context;
			this.handler = handler;
		}

		@Override
		public void run() {

			final String chatRoom = AkiInternalStorageUtil.getCurrentChatRoom(context);
			final String currentUser = AkiInternalStorageUtil.getCurrentUser(context);

			if ( chatRoom == null || currentUser == null ){
				Log.e(AkiApplication.TAG, "GetMessages runnable stopped as either the current chat_room or current_user is missing!");
				return;
			}

			String lastServerTimestamp = AkiInternalStorageUtil.getLastServerTimestamp(context);
			String targetEndpoint = "/message/2?next=" + lastServerTimestamp;

			final Runnable self = this;
			AkiHttpRequestUtil.doGETHttpRequest(context, targetEndpoint, new AsyncCallback() {

				@Override
				public void onSuccess(Object response) {

					JsonObject responseJSON = ((JsonObject) response);
					JsonValue nT = responseJSON.get("next");
					if ( !nT.isNull() ){
						String nextTimestamp = nT.asString();
						AkiInternalStorageUtil.setLastServerTimestamp(context, nextTimestamp);
					}

					boolean isFinished = responseJSON.get("finished").asBoolean();
					if ( !isFinished ){
						AkiInternalStorageUtil.resetTimeout(context);
					}

					JsonArray messages = responseJSON.get("messages").asArray();
					for ( JsonValue message : messages ){
						String sender = message.asObject().get("sender").asString();
						String content = message.asObject().get("message").asString();
						String timestamp = message.asObject().get("timestamp").asString();
						AkiInternalStorageUtil.storePulledMessage(context, chatRoom, sender, content, timestamp);
					}
					if ( messages.size() > 0 ){

						AkiChatAdapter chatAdapter = AkiChatAdapter.getInstance(context);
						List<JsonObject> messagesList = AkiChatAdapter.toJsonObjectList(
								AkiInternalStorageUtil.retrieveMessages(context, chatRoom)
						);

						chatAdapter.clear();
						if ( messagesList != null ){
							chatAdapter.addAll(messagesList);
						}
						chatAdapter.notifyDataSetChanged();

						AkiChatFragment.getInstance().externalRefreshAll();
						AkiInternalStorageUtil.resetTimeout(context);
					}

					JsonValue updateMutualInterests = responseJSON.get("update_mutual_interests");
					if ( updateMutualInterests != null ){
						getMutualInterests(context);
					}
					
					int timeout = AkiInternalStorageUtil.getNextTimeout(context);
					handler.postDelayed(self, timeout * 1000);
				}

				@Override
				public void onFailure(Throwable failure) {

					if ( tolerance >= 3 ){
						Log.e(AkiApplication.TAG, "GetMessages runnable canceled due to failing more than 3 consecutive times!");
						AkiInternalStorageUtil.resetTimeout(context);
						handler.removeCallbacks(self);
						return;
					}

					int timeout = AkiInternalStorageUtil.getNextTimeout(context);
					handler.postDelayed(self, timeout * 1000);
					tolerance++;
				}

				@Override
				public void onCancel() {
					Log.e(AkiApplication.TAG, "GetMessages runnable canceled!");
					AkiInternalStorageUtil.resetTimeout(context);
					handler.removeCallbacks(self);
					return;
				}
			});
		}
	}
	public static class GetPrivateMessages implements Runnable {

		private final Context context;
		private final Handler handler;
		private int tolerance = 0;
		private String userId;
		private String chatRoom;

		public GetPrivateMessages(Context context, Handler handler, String userId){
			this.context = context;
			this.handler = handler;
			this.userId= userId;
			this.chatRoom = buildPrivateChatId(context, userId);
		}

		@Override
		public void run() {

			final String currentUser = AkiInternalStorageUtil.getCurrentUser(context);

			if ( chatRoom == null || currentUser == null ){
				Log.e(AkiApplication.TAG, "GetMessages runnable stopped as either the current chat_room or current_user is missing!");
				return;
			}

			String lastServerTimestamp = AkiInternalStorageUtil.getLastServerTimestamp(context,chatRoom);
			String targetEndpoint = "/private_message/"+userId+"?next=" + lastServerTimestamp;

			final Runnable self = this;
			AkiHttpRequestUtil.doGETHttpRequest(context, targetEndpoint, new AsyncCallback() {

				@Override
				public void onSuccess(Object response) {

					JsonObject responseJSON = ((JsonObject) response);
					JsonValue nT = responseJSON.get("next");
					if ( !nT.isNull() ){
						String nextTimestamp = nT.asString();
						AkiInternalStorageUtil.setLastServerTimestamp(context, nextTimestamp, chatRoom);
					}

					boolean isFinished = responseJSON.get("finished").asBoolean();
					if ( !isFinished ){
						AkiInternalStorageUtil.resetTimeout(context,chatRoom);
					}

					JsonArray messages = responseJSON.get("messages").asArray();
					for ( JsonValue message : messages ){
						String sender = message.asObject().get("sender").asString();
						String content = message.asObject().get("message").asString();
						String timestamp = message.asObject().get("timestamp").asString();
						AkiInternalStorageUtil.storePulledMessage(context, chatRoom, sender, content, timestamp);
					}
					if ( messages.size() > 0 ){

						AkiPrivateChatAdapter chatAdapter = AkiPrivateChatAdapter.getInstance(context);
						List<JsonObject> messagesList = AkiPrivateChatAdapter.toJsonObjectList(
								AkiInternalStorageUtil.retrieveMessages(context, chatRoom)
						);

						chatAdapter.clear();
						if ( messagesList != null ){
							chatAdapter.addAll(messagesList);
						}
						chatAdapter.notifyDataSetChanged();

						AkiInternalStorageUtil.resetTimeout(context,chatRoom);
					}

					
					int timeout = AkiInternalStorageUtil.getNextTimeout(context,chatRoom);
					handler.postDelayed(self, timeout * 1000);
				}

				@Override
				public void onFailure(Throwable failure) {

					if ( tolerance >= 3 ){
						Log.e(AkiApplication.TAG, "GetMessages runnable canceled due to failing more than 3 consecutive times!");
						AkiInternalStorageUtil.resetTimeout(context,chatRoom);
						handler.removeCallbacks(self);
						return;
					}

					int timeout = AkiInternalStorageUtil.getNextTimeout(context,chatRoom);
					handler.postDelayed(self, timeout * 1000);
					tolerance++;
				}

				@Override
				public void onCancel() {
					Log.e(AkiApplication.TAG, "GetMessages runnable canceled!");
					AkiInternalStorageUtil.resetTimeout(context,chatRoom);
					handler.removeCallbacks(self);
					return;
				}
			});
		}
	}

	public static GetMessages getMessages;
	public static Handler handler;
	
	public static GetPrivateMessages getPrivateMessages;
	public static Handler handlerPrivate;

	public static synchronized void getMessages(final Context context){

		if ( handler == null ){
			handler = new Handler();
		}
		if ( getMessages == null ){
			getMessages = new GetMessages(context, handler);
		}
		else {
			AkiInternalStorageUtil.resetTimeout(context);
			handler.removeCallbacks(getMessages);
		}
		handler.post(getMessages);
	}

	public static synchronized void stopGettingMessages(final Context context){

		if ( handler != null ){
			AkiInternalStorageUtil.resetTimeout(context);
			handler.removeCallbacks(getMessages);
		}
	}

	public static synchronized void restartGettingMessages(final Context context){
		stopGettingMessages(context);
		getMessages(context);
	}
	
	public static synchronized void getPrivateMessages(final Context context, String userId){

		if ( handlerPrivate == null ){
			handlerPrivate = new Handler();
		}
		if ( getPrivateMessages == null ){
			getPrivateMessages = new GetPrivateMessages(context, handlerPrivate, userId);
		}
		else {
			AkiInternalStorageUtil.resetTimeout(context,buildPrivateChatId(context, userId));
			handlerPrivate.removeCallbacks(getPrivateMessages);
		}
		handlerPrivate.post(getPrivateMessages);
	}

	public static synchronized void stopGettingPrivateMessages(final Context context){

		if ( handlerPrivate != null && getPrivateMessages!=null){
			AkiInternalStorageUtil.resetTimeout(context, getPrivateMessages.chatRoom);
			handlerPrivate.removeCallbacks(getPrivateMessages);
			getPrivateMessages=null;
		}
	}

	public static synchronized void restartGettingPrivateMessages(final Context context, String userId){
		stopGettingPrivateMessages(context);
		getPrivateMessages(context, userId);
	}
	
	public static synchronized void uploadCoverPhoto(final Context context, final String userId, AsyncCallback callback){
		Bitmap imageBitmap = AkiInternalStorageUtil.getCachedUserCoverPhoto(context, userId);
		AkiHttpUploadUtil.doHttpUpload(context, context.getString(R.string.com_lespi_aki_data_user_coverphoto)+userId, imageBitmap, callback);
	}
	
	public static synchronized void makeSureCoverPhotoIsUploaded(final Context context, final String userId){
		String url = "/upload/" + context.getString(R.string.com_lespi_aki_data_user_coverphoto) + userId + ".png";
		AkiHttpRequestUtil.doHEADHttpRequest(context, url, new AsyncCallback() {
			@Override
			public void onSuccess(Object response) {
				Log.i(AkiApplication.TAG, "Cover photo of user {" + userId + "} is already uploaded to server!");
			}
			
			@Override
			public void onFailure(Throwable error) {
				AkiServerUtil.uploadCoverPhoto(context, userId, new AsyncCallback() {
					@Override
					public void onSuccess(Object response) {
						Log.i(AkiApplication.TAG, "Cover photo of user {" + userId + "} uploaded to server!");
					}
					
					@Override
					public void onFailure(Throwable error) {
						Log.e(AkiApplication.TAG, "Error while trying to upload cover photo of user {" + userId + "} to server.");
					}
					
					@Override
					public void onCancel() {
						Log.e(AkiApplication.TAG, "Could not upload cover photo of user {" + userId + "} to server.");
					}
				});
			}
			
			@Override
			public void onCancel() {
				AkiServerUtil.uploadCoverPhoto(context, userId, new AsyncCallback() {
					@Override
					public void onSuccess(Object response) {
						Log.i(AkiApplication.TAG, "Cover photo of user {" + userId + "} uploaded to server!");
					}
					
					@Override
					public void onFailure(Throwable error) {
						Log.e(AkiApplication.TAG, "Error while trying to upload cover photo of user {" + userId + "} to server.");
					}
					
					@Override
					public void onCancel() {
						Log.e(AkiApplication.TAG, "Could not upload cover photo of user {" + userId + "} to server.");
					}
				});
			}
		});
	}

	public static synchronized void uploadUserPicture(final Context context, final String userId, AsyncCallback callback){
		Bitmap imageBitmap = AkiInternalStorageUtil.getCachedUserPicture(context, userId);
		AkiHttpUploadUtil.doHttpUpload(context, context.getString(R.string.com_lespi_aki_data_user_picture)+userId, imageBitmap, callback);
	}
	
	public static synchronized void makeSureUserPictureIsUploaded(final Context context, final String userId){
		String url = "/upload/" + context.getString(R.string.com_lespi_aki_data_user_picture) + userId + ".png";
		AkiHttpRequestUtil.doHEADHttpRequest(context, url, new AsyncCallback() {
			@Override
			public void onSuccess(Object response) {
				Log.i(AkiApplication.TAG, "Picture of user {" + userId + "} is already uploaded to server!");
			}
			
			@Override
			public void onFailure(Throwable error) {
				AkiServerUtil.uploadUserPicture(context, userId, new AsyncCallback() {
					@Override
					public void onSuccess(Object response) {
						Log.i(AkiApplication.TAG, "Picture of user {" + userId + "} uploaded to server!");
					}
					
					@Override
					public void onFailure(Throwable error) {
						Log.e(AkiApplication.TAG, "Error while trying to upload picture of user {" + userId + "} to server.");
					}
					
					@Override
					public void onCancel() {
						Log.e(AkiApplication.TAG, "Could not upload picture of user {" + userId + "} to server.");
					}
				});
			}
			
			@Override
			public void onCancel() {
				AkiServerUtil.uploadUserPicture(context, userId, new AsyncCallback() {
					@Override
					public void onSuccess(Object response) {
						Log.i(AkiApplication.TAG, "Picture of user {" + userId + "} uploaded to server!");
					}
					
					@Override
					public void onFailure(Throwable error) {
						Log.e(AkiApplication.TAG, "Error while trying to upload picture of user {" + userId + "} to server.");
					}
					
					@Override
					public void onCancel() {
						Log.e(AkiApplication.TAG, "Could not upload picture of user {" + userId + "} to server.");
					}
				});
			}
		});
	}
	
}