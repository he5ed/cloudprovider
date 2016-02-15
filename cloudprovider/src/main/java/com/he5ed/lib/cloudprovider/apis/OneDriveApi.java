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
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okio.BufferedSink;

/**
 * Microsoft OneDrive cloud service API implementation
 *
 * @hide
 */
public class OneDriveApi extends BaseApi {

    // API server constant values
    public static final String AUTH_URL = "https://login.live.com/oauth20_authorize.srf";
    public static final String TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    public static final String REVOKE_URL = "https://login.live.com/oauth20_logout.srf";
    public static final String LIVE_API_URL = "https://apis.live.net/v5.0";

    public static final String API_BASE_URL = "https://api.onedrive.com/v1.0";

    public static final String ROOT_ID = "root";
    public static final String ROOT_PATH = "/drive/root:";

    /**
     * Must override with the correct values
     */
    public static String CLIENT_ID = null;
    public static String CLIENT_SECRET = null;
    public static String REDIRECT_URL = null;

    // enable API
    public static boolean ENABLE_API = false;

    // class constant values
    public static final String NAME = "Microsoft OneDrive";
    public static final int ICON_RESOURCE = R.drawable.ic_onedrive_color_24dp;
    public static final String TAG = "OneDriveApi";
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
                .appendQueryParameter("scope", "wl.emails wl.offline_access onedrive.readwrite")
                .build();
    }

    /**
     * Get parameters to be passed to Volley request to get access token
     *
     * @param authCode code from authorization process
     * @return RequestBody
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
                .addEncoded("redirect_uri", REDIRECT_URL)
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
     * Get current user information uri
     *
     * @return Uri
     */
    public static Uri getUserInfoUri() {
        Uri uri = Uri.parse(LIVE_API_URL);
        return uri.buildUpon().appendEncodedPath("me").build();
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
        user.id = jsonObject.getString("id");
        user.name = jsonObject.getString("name");
        user.displayName = jsonObject.getString("name");
        JSONObject emails = jsonObject.getJSONObject("emails");
        user.email = emails.getString("account");

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
            map.put(CFolder.DATE_FORMAT, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            if (jsonObject.has("createdDateTime"))
                map.put(CFolder.CREATED, jsonObject.getString("createdDateTime"));
            if (jsonObject.has("lastModifiedDateTime"))
                map.put(CFolder.MODIFIED, jsonObject.getString("lastModifiedDateTime"));
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
            map.put(CFolder.DATE_FORMAT, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            if (jsonObject.has("createdDateTime"))
                map.put(CFolder.CREATED, jsonObject.getString("createdDateTime"));
            if (jsonObject.has("lastModifiedDateTime"))
                map.put(CFolder.MODIFIED, jsonObject.getString("lastModifiedDateTime"));
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

    /**
     * Constructor for OneDrive API
     *
     * @param account to be used must be OneDrive cloud type
     */
    public OneDriveApi(Context context, Account account) {
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
        Request request = getUserInfoRequest(mAccessToken);

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
                    if (mPrepareListener != null)
                        mPrepareListener.onPrepareSuccessful();
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

        List<Object> list = new ArrayList<>();

        Request request = new Request.Builder()
                .url(API_BASE_URL + "/drive/items/" + folder.getId() + "/children")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                JSONArray entries = jsonObject.getJSONArray("value");
                if (entries.length() > 0) {
                    list.addAll(createFilteredItemsList(entries, null));
                } else {
                    // return null if no item found
                    return null;
                }
                // pagination available
                if (jsonObject.has("@odata.nextLink")) {
                    list.addAll(exploreFolderContinue(jsonObject.getString("@odata.nextLink")));
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

    /**
     * Get continue folder items
     *
     * @param url for the next page of items
     * @return List that contains CFile and CFolder
     * @throws RequestFailException that content various error types
     */
    public synchronized List<Object> exploreFolderContinue(String url) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        List<Object> list = new ArrayList<>();

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
                    list.addAll(createFilteredItemsList(entries, null));
                } else {
                    // return null if no item found
                    return null;
                }
                // pagination available
                if (jsonObject.has("@odata.nextLink")) {
                    list.addAll(exploreFolderContinue(jsonObject.getString("@odata.nextLink")));
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
        root.setPath(ROOT_PATH);
        root.setName(mContext.getString(R.string.home_folder_title));
        root.setRoot(true);
        return root;
    }

    @Override
    public CFolder getFolderInfo(@NonNull String folderId) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        if (TextUtils.isEmpty(folderId)) return null;

        Request request = new Request.Builder()
                .url(API_BASE_URL + "/drive/items/" + folderId)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                // new file created
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

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("name", name);
            params.put("folder", new JSONObject());
            params.put("@name.conflictBehavior", "fail");
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
                .url(API_BASE_URL + "/drive/items/" +
                        (parent != null ? parent.getId() : getRoot().getId()) + "/children")
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

        Request request = new Request.Builder()
                .url(API_BASE_URL + "/drive/items/" + folder.getId())
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
            params.put("parentReference", new JSONObject()
                    .put("id", parent != null ? parent.getId() : getRoot().getId()));
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
                .url(API_BASE_URL + "/drive/items/" + folder.getId())
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

        Request request = new Request.Builder()
                .url(API_BASE_URL + "/drive/items/" + folder.getId())
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .delete()
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

        if (TextUtils.isEmpty(fileId)) return null;

        Request request = new Request.Builder()
                .url(API_BASE_URL + "/drive/items/" + fileId)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                // new file created
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
    public CFile uploadFile(@NonNull final File file, @Nullable CFolder parent) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("drive/items/" +
                        (parent != null ? parent.getId() : getRoot().getId()) + "/children/" +
                        file.getName() + "/content")
                .appendQueryParameter("@name.conflictBehavior", "fail")
                .build()
                .toString();

        RequestBody fileBody = new RequestBody() {
            @Override
            public MediaType contentType() {
                return null;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                // copy file into RequestBody
                FilesUtils.copyFile(new FileInputStream(file), sink.outputStream());
            }
        };

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .put(fileBody)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                // new file created
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
    public CFile updateFile(@NonNull CFile file, final File content) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("drive/items/" + file.getId() + "/content")
                .appendQueryParameter("@name.conflictBehavior", "replace")
                .build()
                .toString();

        RequestBody fileBody = new RequestBody() {
            @Override
            public MediaType contentType() {
                return null;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                // copy file into RequestBody
                FilesUtils.copyFile(new FileInputStream(content), sink.outputStream());
            }
        };

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .put(fileBody)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                // new file created
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

        Request request = new Request.Builder()
                .url(API_BASE_URL + "/drive/items/" + file.getId())
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
            params.put("parentReference", new JSONObject()
                    .put("id", folder != null ? folder.getId() : getRoot().getId()));
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
                .url(API_BASE_URL + "/drive/items/" + file.getId())
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

        // assign filename
        if (TextUtils.isEmpty(filename)) filename = file.getName();

        Request request = new Request.Builder()
                .url(API_BASE_URL + "/drive/items/" + file.getId() + "/content")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                switch (response.code()) {
                    case 200:
                        // redirect to url
                        return downloadFile(response.request(), filename);
                    case 302:
                        // redirect to url
                        return downloadFile(response.request(), filename);
                }
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
        return null;
    }

    /**
     * Download file from redirect request
     *
     * @param request for redirect
     * @return File
     * @throws RequestFailException
     */
    public synchronized File downloadFile(@NonNull Request request, String filename) throws RequestFailException {
        try {
            File file = new File(mContext.getFilesDir(),
                    TextUtils.isEmpty(filename) ? "Untitled" : filename);

            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                FilesUtils.copyFile(response.body().byteStream(), new FileOutputStream(file));
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
            return file;
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

        Request request = new Request.Builder()
                .url(API_BASE_URL + "/drive/items/" + file.getId())
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .delete()
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
        // check cast for search result
        List<CFile> output = new ArrayList<>();
        for (Object item : search(keyword, folder)) {
            if (CFile.class.isAssignableFrom(item.getClass()))
                output.add(CFile.class.cast(item));
        }
        return output;
    }

    @Override
    public List<CFolder> searchFolder(@NonNull String keyword, CFolder folder) throws RequestFailException {
        // check cast for search result
        List<CFolder> output = new ArrayList<>();
        for (Object item : search(keyword, folder)) {
            if (CFolder.class.isAssignableFrom(item.getClass()))
                output.add(CFolder.class.cast(item));
        }
        return output;
    }

    @Override
    public List<Object> search(@NonNull String keyword, CFolder folder) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        List<Object> list = new ArrayList<>();

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("drive/items/" + folder.getId() + "/view.search")
                .appendQueryParameter("q", keyword)
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
                JSONArray entries = jsonObject.getJSONArray("value");
                if (entries.length() > 0) {
                    list.addAll(createFilteredItemsList(entries, folder));
                } else {
                    // return null if no item found
                    return null;
                }
                // pagination available
                if (jsonObject.has("@odata.nextLink")) {
                    list.addAll(searchContinue(jsonObject.getString("@odata.nextLink"), folder));
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

    /**
     * Get continue search items
     *
     * @param url for the next page of items
     * @param folder where the search is looking at
     * @return List that contains CFile and CFolder
     * @throws RequestFailException that content various error types
     */
    public synchronized List<Object> searchContinue(String url, CFolder folder) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        List<Object> list = new ArrayList<>();

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
                    list.addAll(createFilteredItemsList(entries, folder));
                } else {
                    // return null if no item found
                    return null;
                }
                // pagination available
                if (jsonObject.has("@odata.nextLink")) {
                    list.addAll(searchContinue(jsonObject.getString("@odata.nextLink"), folder));
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
    public File getThumbnail(@NonNull CFile file) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Request request = new Request.Builder()
                .url(API_BASE_URL + "/drive/items/" + file.getId() + "/thumbnails/0/medium/content")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                switch (response.code()) {
                    case 200:
                        // redirect to url
                        return downloadFile(response.request(), file.getId() + ".jpg");
                    case 302:
                        // redirect to url
                        return downloadFile(response.request(), file.getId() + ".jpg");
                }
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
        return null;
    }

    /**
     * Create files or folders from the JSONArray search result
     *
     * @param jsonArray that contain files and folders information
     * @param parent folder that contain the items returned
     * @return list that contains CFile and CFolder that belong to the parent folder
     * @throws RequestFailException
     */
    private List<Object> createFilteredItemsList(JSONArray jsonArray, CFolder parent) throws RequestFailException {
        if (jsonArray == null || jsonArray.length() == 0) return null;

        List<Object> list = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                // filter parent
                JSONObject parentReference = jsonObject.getJSONObject("parentReference");
                if (parent != null &&
                        !parentReference.getString("id").equals(parent.getId()) &&
                        !parentReference.getString("path").equals(parent.getPath()))
                    continue;

                if (jsonObject.has("file")) {
                    list.add(buildFile(jsonObject));
                } else if (jsonObject.has("folder")) {
                    list.add(buildFolder(jsonObject));
                } else {
                    Log.e(TAG, "Unknown type found");
                }
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RequestFailException(e.getMessage());
            }
        }

        return list;
    }
}
