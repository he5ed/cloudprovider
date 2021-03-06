/*
 * Copyright 2015 HE5ED.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.he5ed.lib.cloudprovider.apis;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.he5ed.lib.cloudprovider.CloudProvider;
import com.he5ed.lib.cloudprovider.R;
import com.he5ed.lib.cloudprovider.auth.Authenticator;
import com.he5ed.lib.cloudprovider.exceptions.RequestFailException;
import com.he5ed.lib.cloudprovider.models.CFile;
import com.he5ed.lib.cloudprovider.models.CFolder;
import com.he5ed.lib.cloudprovider.models.User;
import com.he5ed.lib.cloudprovider.utils.FilesUtils;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okio.BufferedSink;

/**
 * Amazon CloudDrive cloud service API implementation
 *
 * @hide
 */
public class CloudDriveApi extends BaseApi {

    // API server constant values
    public static final String AUTH_URL = "https://www.amazon.com/ap/oa";
    public static final String TOKEN_URL = "https://api.amazon.com/auth/o2/token";
    public static final String REVOKE_URL = null;

    public static final String API_BASE_URL = "https://drive.amazonaws.com/drive/v1";

    public static String ROOT_ID = "";

    /**
     * Must override with the correct values
     */
    public static String CLIENT_ID = null;
    public static String CLIENT_SECRET = null;
    public static String REDIRECT_URL = null;

    // enable API
    public static boolean ENABLE_API = false;

    // class constant values
    public static final String NAME = "Amazon Cloud Drive";
    public static final int ICON_RESOURCE = R.drawable.ic_amazon_color_24dp;
    public static final String TAG = "CloudDriveApi";
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /**
     * Build authorization url base on type of cloud service
     *
     * @return Uri
     */
    public static Uri buildAuthUri(String stateString) {
        Uri uri = Uri.parse(AUTH_URL);
        return uri.buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("redirect_uri", REDIRECT_URL)
                .appendQueryParameter("state", stateString)
                .appendQueryParameter("scope", "profile clouddrive:read_all clouddrive:write")
                .build();
    }

    /**
     * Get parameters to be passed to Volley request to get access token
     *
     * @param authCode code from authorization process
     * @return Map
     */
    public static RequestBody getAccessTokenBody(String authCode) {
        return new FormEncodingBuilder()
                .addEncoded("grant_type", "authorization_code")
                .addEncoded("code", authCode)
                .addEncoded("client_id", CLIENT_ID)
                .addEncoded("client_secret", CLIENT_SECRET)
                .addEncoded("redirect_uri", REDIRECT_URL)
                .build();
    }

    /**
     * Build form body to be passed to access token
     *
     * @param refreshToken code from authorization process
     * @return RequestBody
     */
    public static RequestBody getRefreshTokenBody(String refreshToken) {
        return new FormEncodingBuilder()
                .addEncoded("grant_type", "refresh_token")
                .addEncoded("refresh_token", refreshToken)
                .addEncoded("client_id", CLIENT_ID)
                .addEncoded("client_secret", CLIENT_SECRET)
                .build();
    }

    /**
     * Extract access token from the JSONObject
     *
     * @param jsonObject JSONObject that contain access token
     * @return Map
     * @throws JSONException
     */
    public static Map<String, String> extractAccessToken(JSONObject jsonObject) throws JSONException {
        Map<String, String> map = new HashMap<>();
        map.put(Authenticator.KEY_ACCESS_TOKEN, jsonObject.getString("access_token"));
        map.put(Authenticator.KEY_EXPIRY, String.valueOf(jsonObject.getLong("expires_in")));
        map.put(Authenticator.KEY_REFRESH_TOKEN, jsonObject.getString("refresh_token"));
        return map;
    }

    /**
     * Get current user end point url
     *
     * @return Uri
     */
    public static Uri getEndPointUri() {
        Uri uri = Uri.parse(API_BASE_URL);
        return uri.buildUpon().appendEncodedPath("account/endpoint").build();
    }

    /**
     * Get current user information uri
     *
     * @return Uri
     */
    public static Uri getUserInfoUri() {
        return Uri.parse("https://api.amazon.com/user/profile");
    }

    /**
     * Create request to get user endpoint
     *
     * @param accessToken access token for authorization
     * @return Request
     */
    public static Request getEndPointRequest(String accessToken) {
        return new Request.Builder()
                .url(getEndPointUri().toString())
                .addHeader("Authorization", String.format("Bearer %s", accessToken))
                .build();
    }

    /**
     * Create request to get user information
     *
     * @param accessToken access token for authorization
     * @return Request
     */
    public static Request getUserInfoRequest(String accessToken) {
        return new Request.Builder()
                .url(getUserInfoUri().toString())
                .addHeader("Authorization", String.format("Bearer %s", accessToken))
                .build();
    }

    /**
     * Extract user information from the JSONObject
     *
     * @param jsonObject JSONObject that contain user information
     * @return User
     * @throws JSONException
     */
    public static User extractUser(JSONObject jsonObject) throws JSONException {
        User user = new User();

        user.id = jsonObject.getString("user_id");
        user.name = jsonObject.getString("name");
        user.displayName = jsonObject.getString("name");
        user.email = jsonObject.getString("email");

        return user;
    }

    /**
     * Build folder from JSONObject
     *
     * @param jsonObject from http response
     * @return CFolder
     */
    public static CFolder buildFolder(JSONObject jsonObject) {
        Map<String, Object> map = new HashMap<>();
        try {
            map.put(CFolder.ID, jsonObject.getString("id"));
            map.put(CFolder.NAME, jsonObject.getString("name"));
            map.put(CFolder.DATE_FORMAT, "yyyy-MM-dd'T'HH:mm:ssZ");
            if (jsonObject.has("created_at"))
                map.put(CFolder.CREATED, jsonObject.getString("created_at"));
            if (jsonObject.has("modified_at"))
                map.put(CFolder.MODIFIED, jsonObject.getString("modified_at"));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        return new CFolder(map);
    }

    /**
     * Build file from JSONObject
     *
     * @param jsonObject from http response
     * @return CFolder
     */
    public static CFile buildFile(JSONObject jsonObject) {
        Map<String, Object> map = new HashMap<>();
        try {
            map.put(CFolder.ID, jsonObject.getString("id"));
            map.put(CFolder.NAME, jsonObject.getString("name"));
            map.put(CFolder.DATE_FORMAT, "yyyy-MM-dd'T'HH:mm:ssZ");
            if (jsonObject.has("created_at"))
                map.put(CFolder.CREATED, jsonObject.getString("created_at"));
            if (jsonObject.has("modified_at"))
                map.put(CFolder.MODIFIED, jsonObject.getString("modified_at"));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        return new CFile(map);
    }

    private Context mContext;
    private CloudProvider mCloudProvider;
    private Account mAccount;
    private BaseApi.OnPrepareListener mPrepareListener;
    private OkHttpClient mHttpClient;
    private String mAccessToken;
    private String mContentUrl;
    private String mMetadataUrl;

    /**
     * Constructor for Cloud Drive API
     *
     * @param account to be used must be Cloud Drive type
     */
    public CloudDriveApi(Context context, Account account) {
        mContext = context;
        mAccount = account;
        mCloudProvider = CloudProvider.getInstance(mContext);
        mHttpClient = new OkHttpClient();
    }

    @Override
    public synchronized void prepareApi(BaseApi.OnPrepareListener prepareListener) {
        mPrepareListener = prepareListener;

        AccountManager.get(mContext).getAuthToken(mAccount, CloudProvider.AUTH_TYPE, false,
                new AccountManagerCallback<Bundle>() {
                    @Override
                    public void run(AccountManagerFuture<Bundle> future) {
                        try {
                            mAccessToken = future.getResult().getString(AccountManager.KEY_AUTHTOKEN);

                            validateAccessToken();
                        } catch (OperationCanceledException e) {
                            e.printStackTrace();
                            Log.e(TAG, e.getMessage());
                            if (mPrepareListener != null)
                                mPrepareListener.onPrepareFail(e);
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e(TAG, e.getMessage());
                            if (mPrepareListener != null)
                                mPrepareListener.onPrepareFail(e);
                        } catch (AuthenticatorException e) {
                            e.printStackTrace();
                            Log.e(TAG, e.getMessage());
                            if (mPrepareListener != null)
                                mPrepareListener.onPrepareFail(e);
                        }
                    }
                }, null);
    }

    /**
     * Ensure that the access token is still valid
     * Access token can be expired or revoked by user
     * Try to refresh the access token if it is expired
     */
    private void validateAccessToken() {
        Request request = getEndPointRequest(mAccessToken);

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                e.printStackTrace();
                if (mPrepareListener != null)
                    mPrepareListener.onPrepareFail(e);
                Log.e(TAG, e.getMessage());
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful() && response.code() == 200) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        mContentUrl = jsonObject.getString("contentUrl");
                        mMetadataUrl = jsonObject.getString("metadataUrl");

                        if (mPrepareListener != null)
                            mPrepareListener.onPrepareSuccessful();
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.e(TAG, e.getMessage());
                        if (mPrepareListener != null)
                            mPrepareListener.onPrepareFail(e);
                    }
                } else {
                    switch (response.code()) {
                        case 401:
                            // unauthorized
                            refreshAccessToken();
                            break;
                        default:
                            break;
                    }
                    Log.e(TAG, response.code() + ": " + response.body().string());
                }
            }
        });
    }

    /**
     * Try to get a fresh access token using the refresh token
     */
    private void refreshAccessToken() {
        final String refreshToken = mCloudProvider.getUserData(mAccount, Authenticator.KEY_REFRESH_TOKEN);
        if (!TextUtils.isEmpty(refreshToken)) {
            Request request = new Request.Builder()
                    .url(TOKEN_URL)
                    .post(getRefreshTokenBody(refreshToken))
                    .build();

            mHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, e.getMessage());

                    resetAccount();
                }

                @Override
                public void onResponse(Response response) throws IOException {
                    if (response.isSuccessful()) {
                        // convert string into json
                        try {
                            JSONObject jsonObject = new JSONObject(response.body().string());
                            Map<String, String> tokenInfo = extractAccessToken(jsonObject);
                            mCloudProvider.updateAccount(mAccount, tokenInfo);
                            mAccessToken = tokenInfo.get(Authenticator.KEY_ACCESS_TOKEN);
                            // validate again
                            validateAccessToken();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Log.e(TAG, e.getMessage());
                        }
                    } else {
                        Log.e(TAG, response.code() + ": " + response.body().string());
                        resetAccount();
                    }

                }
            });
        } else {
            resetAccount();
        }
    }

    /**
     * Remove the staled account and add a new one
     */
    private void resetAccount() {
        logout(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {

            }

            @Override
            public void onResponse(Response response) throws IOException {

            }
        });
        // use Authenticator to update account
        mCloudProvider.removeAccount(mAccount);
        mCloudProvider.addAccount(getClass().getCanonicalName(), (Activity) mContext);
    }

    @Override
    public void logout(@NonNull Callback callback) {
        RequestBody body = new FormEncodingBuilder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("token", mAccessToken)
                .build();

        Request request = new Request.Builder()
                .url(REVOKE_URL)
                .post(body)
                .build();

        mHttpClient.newCall(request).enqueue(callback);
    }

    @Override
    public synchronized List<Object> exploreFolder(@NonNull CFolder folder, int offset) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        // if folder id is empty set it to root id
        if (TextUtils.isEmpty(folder.getId())) folder.setId(getRootId());

        List<Object> list = new ArrayList<>();
        String folderId = folder.getId();
        Uri uri = Uri.parse(mMetadataUrl);
        String url = uri.buildUpon()
                .appendEncodedPath("nodes/" + folderId + "/children")
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                int total = jsonObject.getInt("count");
                // return null if no item found
                if (total == 0) return null;

                JSONArray entries = jsonObject.getJSONArray("data");
                list.addAll(createItemList(entries));
                // pagination available
                if (jsonObject.has("nextToken")) {
                    list.addAll(exploreFolderContinue(folderId, jsonObject.getString("nextToken")));
                }
                return list;
            } else {
                switch (response.code()) {
                    case 404:
                        // no item found
                        throw new RequestFailException("No item found");
                    case 401:
                        // unauthorized
                        throw new RequestFailException("Unauthorized request");
                    default:
                        break;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }

        return null;
    }

    /**
     * Get continue folder items
     *
     * @param folderId of the folder to explore
     * @param startToken nextToken from previous request for access more content
     * @return List that contains CFile and CFolder
     * @throws RequestFailException that content various error types
     */
    public synchronized List<Object> exploreFolderContinue(String folderId, String startToken) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        List<Object> list = new ArrayList<>();

        Uri uri = Uri.parse(mMetadataUrl);
        String url = uri.buildUpon()
                .appendEncodedPath("nodes/" + folderId + "/children")
                .appendQueryParameter("startToken", startToken)
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                JSONArray entries = jsonObject.getJSONArray("data");
                if (entries.length() > 0) {
                    list.addAll(createItemList(entries));
                } else {
                    // return null if no item found
                    return null;
                }
                // pagination available
                if (jsonObject.has("nextToken")) {
                    list.addAll(exploreFolderContinue(folderId, jsonObject.getString("nextToken")));
                }
                return list;
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public CFolder getRoot() {
        // create root folder manually
        CFolder root = new CFolder(null);
        root.setId(ROOT_ID);
        root.setName(mContext.getString(R.string.home_folder_title));
        root.setRoot(true);
        return root;
    }

    @Override
    public CFolder getFolderInfo(@NonNull String folderId) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(mMetadataUrl);
        String url = uri.buildUpon()
                .appendEncodedPath("nodes/" + folderId)
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                return buildFolder(jsonObject);
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public CFolder createFolder(@NonNull String name, @Nullable CFolder parent) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(mMetadataUrl);
        String url = uri.buildUpon()
                .appendEncodedPath("nodes/")
                .build()
                .toString();

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("name", name);
            params.put("kind", "FOLDER");
            ArrayList<String> parentList = new ArrayList<>();
            parentList.add(parent != null ? parent.getId() : getRoot().getId());
            params.put("parents", new JSONArray(parentList));
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }

        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return JSON;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.writeUtf8(params.toString());
            }
        };

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                return buildFolder(jsonObject);
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public CFolder renameFolder(@NonNull CFolder folder, String name) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        // exit if root or same name
        if (folder.isRoot() || folder.getName().equals(name)) return folder;

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("name", name);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }

        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return JSON;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.writeUtf8(params.toString());
            }
        };

        Uri uri = Uri.parse(mMetadataUrl);
        String url = uri.buildUpon()
                .appendEncodedPath("nodes/" + folder.getId())
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .patch(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                // return folder object
                return buildFolder(new JSONObject(response.body().string()));
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public CFolder moveFolder(@NonNull CFolder folder, @Nullable CFolder parent) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        // exit if root or same name
        if (folder.isRoot() || parent != null && folder.getId().equals(parent.getId())) return folder;

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            ArrayList<String> parentList = new ArrayList<>();
            parentList.add(parent != null ? parent.getId() : getRoot().getId());
            params.put("parents", new JSONArray(parentList));
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }

        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return JSON;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.writeUtf8(params.toString());
            }
        };

        Uri uri = Uri.parse(mMetadataUrl);
        String url = uri.buildUpon()
                .appendEncodedPath("nodes/" + folder.getId())
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .patch(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                // return folder object
                return buildFolder(new JSONObject(response.body().string()));
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public void deleteFolder(@NonNull CFolder folder) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(mMetadataUrl);
        String url = uri.buildUpon()
                .appendEncodedPath("trash/" + folder.getId())
                .build()
                .toString();

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("kind", "FOLDER");
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }

        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return JSON;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.writeUtf8(params.toString());
            }
        };

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .put(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                Log.d(TAG, "CFolder with the id: " + folder.getName() + " deleted");
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public CFile getFileInfo(@NonNull String fileId) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(mMetadataUrl);
        String url = uri.buildUpon()
                .appendEncodedPath("nodes/" + fileId)
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                return buildFile(jsonObject);
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public CFile uploadFile(@NonNull File file, @Nullable CFolder parent) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(mContentUrl);
        String url = uri.buildUpon()
                .appendEncodedPath("nodes")
                .build()
                .toString();

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("name", file.getName());
            params.put("kind", "FILE");
            ArrayList<String> parentList = new ArrayList<>();
            parentList.add(parent != null ? parent.getId() : getRoot().getId());
            params.put("parents", new JSONArray(parentList));
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }

        // create multipart body
        MediaType fileType = MediaType.parse(FilesUtils.getFileType(file));
        RequestBody multipart = new MultipartBuilder()
                .type(MultipartBuilder.FORM)
                .addFormDataPart("metadata", params.toString())
                .addFormDataPart("content", file.getName(),
                        RequestBody.create(fileType, file))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(multipart)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                return buildFile(jsonObject);
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public CFile updateFile(@NonNull CFile file, File content) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(mContentUrl);
        String url = uri.buildUpon()
                .appendEncodedPath("nodes/" + file.getId() + "/content")
                .build()
                .toString();

        // create multipart body
        MediaType fileType = MediaType.parse(FilesUtils.getFileType(content));
        RequestBody multipart = new MultipartBuilder()
                .type(MultipartBuilder.FORM)
                .addFormDataPart("content", file.getName(),
                        RequestBody.create(fileType, content))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .put(multipart)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                return buildFile(jsonObject);
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public CFile renameFile(@NonNull CFile file, String name) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        // exist if same filename
        if (file.getName().equals(name)) return file;

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("name", name);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }

        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return JSON;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.writeUtf8(params.toString());
            }
        };

        Uri uri = Uri.parse(mMetadataUrl);
        String url = uri.buildUpon()
                .appendEncodedPath("nodes/" + file.getId())
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .patch(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                // return file object
                return buildFile(new JSONObject(response.body().string()));
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public CFile moveFile(@NonNull CFile file, @Nullable CFolder folder) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            ArrayList<String> parentList = new ArrayList<>();
            parentList.add(folder != null ? folder.getId() : getRoot().getId());
            params.put("parents", new JSONArray(parentList));
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }

        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return JSON;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.writeUtf8(params.toString());
            }
        };

        Uri uri = Uri.parse(mMetadataUrl);
        String url = uri.buildUpon()
                .appendEncodedPath("nodes/" + file.getId())
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .patch(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                // return file object
                return buildFile(new JSONObject(response.body().string()));
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public File downloadFile(@NonNull CFile file, String filename) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(mContentUrl);
        String url = uri.buildUpon()
                .appendEncodedPath("nodes/" + file.getId() + "/content")
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        try {
            File localFile = new File(mContext.getFilesDir(),
                    TextUtils.isEmpty(filename) ? file.getName() : filename);

            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                FilesUtils.copyFile(response.body().byteStream(), new FileOutputStream(localFile));
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
            return localFile;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public void deleteFile(@NonNull CFile file) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(mMetadataUrl);
        String url = uri.buildUpon()
                .appendEncodedPath("trash/" + file.getId())
                .build()
                .toString();

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("kind", "FOLDER");
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }

        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return JSON;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.writeUtf8(params.toString());
            }
        };

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .put(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                Log.d(TAG, "File with the id: " + file.getName() + " deleted");
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public List<CFile> searchFile(@NonNull String keyword, CFolder folder) throws RequestFailException {

        List<Object> list = exploreFolder(folder, 0);
        List<CFile> resultList = new ArrayList<>();

        // search through every item's name for keyword match in case insensitive mode
        for (Object item : list) {
            if (CFile.class.isAssignableFrom(item.getClass())) {
                if (((CFile) item).getName().toLowerCase().contains(keyword.toLowerCase()))
                    resultList.add((CFile) item);
            }
        }

        return resultList;
    }

    @Override
    public List<CFolder> searchFolder(@NonNull String keyword, CFolder folder) throws RequestFailException {

        List<Object> list = exploreFolder(folder, 0);
        List<CFolder> resultList = new ArrayList<>();

        // search through every item's name for keyword match in case insensitive mode
        for (Object item : list) {
            if (CFolder.class.isAssignableFrom(item.getClass())) {
                if (((CFolder) item).getName().toLowerCase().contains(keyword.toLowerCase()))
                    resultList.add((CFolder) item);
            }
        }

        return resultList;
    }

    @Override
    public List<Object> search(@NonNull String keyword, CFolder folder) throws RequestFailException {

        List<Object> list = exploreFolder(folder, 0);
        List<Object> resultList = new ArrayList<>();

        // search through every item's name for keyword match in case insensitive mode
        for (Object item : list) {
            if (CFolder.class.isAssignableFrom(item.getClass())) {
                if (((CFolder) item).getName().toLowerCase().contains(keyword.toLowerCase()))
                    resultList.add(item);
            } else if (CFile.class.isAssignableFrom(item.getClass())) {
                if (((CFile) item).getName().toLowerCase().contains(keyword.toLowerCase()))
                    resultList.add(item);
            }
        }

        return resultList;
    }

    @Override
    public File getThumbnail(@NonNull CFile file) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(mMetadataUrl);
        String url = uri.buildUpon()
                .appendEncodedPath("nodes/" + file.getId())
                .appendQueryParameter("asset", "ALL")
                .appendQueryParameter("tempLink", "true")
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                if (jsonObject.has("assets")) {
                    JSONArray assets = jsonObject.getJSONArray("assets");
                    JSONObject asset = assets.getJSONObject(0);
                    if (asset.has("tempLink")) {
                        String fileUrl = asset.getString("tempLink");
                        String filename = asset.getString("name");
                        return downloadThumbnail(fileUrl, filename);
                    }
                    return null;
                } else {
                    return null;
                }
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Download thumbnail via the temperary url
     *
     * @param url
     * @param filename
     * @return
     * @throws RequestFailException
     */
    private File downloadThumbnail(@NonNull String url, String filename) throws RequestFailException {

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try {
            File localFile = new File(mContext.getFilesDir(), filename);

            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                FilesUtils.copyFile(response.body().byteStream(), new FileOutputStream(localFile));
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
            return localFile;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
    }

    /**
     * Create files or folders from the JSONArray search result
     *
     * @param jsonArray that contain files and folders information
     * @return list that contains CFile and CFolder
     * @throws RequestFailException
     */
    private List<Object> createItemList(JSONArray jsonArray) throws RequestFailException {
        if (jsonArray == null || jsonArray.length() == 0) return null;

        List<Object> list = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String type = jsonObject.getString("kind");
                switch (type.toLowerCase()) {
                    case "file":
                        list.add(buildFile(jsonObject));
                        break;
                    case "folder":
                        list.add(buildFolder(jsonObject));
                        break;
                    default:
                        Log.e(TAG, "Unknown type found");
                        break;
                }

            } catch (JSONException e) {
                e.printStackTrace();
                throw new RequestFailException(e.getMessage());
            }
        }

        return list;
    }

    /**
     * Get root folder id
     *
     * @return root id as String
     */
    private String getRootId() {
        Uri uri = Uri.parse(mMetadataUrl);
        String url = uri.buildUpon()
                .appendEncodedPath("nodes")
                .appendQueryParameter("filters", "kind:FOLDER AND isRoot:true")
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                JSONArray entries = jsonObject.getJSONArray("data");
                if (entries.length() > 0) {
                    JSONObject root = entries.getJSONObject(0);
                    return ROOT_ID = root.getString("id");
                }
            } else {
                Log.e(TAG, response.message());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
