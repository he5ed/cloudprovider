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
 * Dropbox cloud service API implementation
 *
 * @hide
 */
public class DropboxApi extends BaseApi {

    // API server constant values
    public static final String AUTH_URL = "https://www.dropbox.com/1/oauth2/authorize";
    public static final String TOKEN_URL = "https://api.dropboxapi.com/1/oauth2/token";
    public static final String REVOKE_URL = "https://api.dropboxapi.com/1/disable_access_token";

    public static final String API_BASE_URL = "https://api.dropboxapi.com/2";
    public static final String API_CONTENT_URL = "https://content.dropboxapi.com/2";

    public static final String ROOT_ID = "";

    /**
     * Must override with the correct values
     */
    public static String CLIENT_ID = null;
    public static String CLIENT_SECRET = null;
    public static String REDIRECT_URL = null;

    // enable API
    public static boolean ENABLE_API = false;

    // class constant values
    public static final String NAME = "Dropbox";
    public static final int ICON_RESOURCE = R.drawable.ic_dropbox_color_24dp;
    public static final String TAG = "DropboxApi";
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
                .build();
    }

    /**
     * Build form body to be passed to access token
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
     * Extract access token from the JSONObject
     *
     * @param jsonObject JSONObject that contain access token
     * @return Map
     * @throws JSONException
     */
    public static Map<String, String> extractAccessToken(JSONObject jsonObject) throws JSONException {
        Map<String, String> map = new HashMap<>();
        map.put(Authenticator.KEY_ACCESS_TOKEN, jsonObject.getString("access_token"));

        return map;
    }

    /**
     * Get current user information uri
     *
     * @return Uri
     */
    public static Uri getUserInfoUri() {
        Uri uri = Uri.parse(API_BASE_URL);
        return uri.buildUpon().appendEncodedPath("users/get_current_account").build();
    }

    /**
     * Create request to get user information
     *
     * @param accessToken access token for authorization
     * @return Request
     */
    public static Request getUserInfoRequest(String accessToken) {
        // need to create blank body to use post method
        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return null;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {

            }
        };

        return new Request.Builder()
                .post(body)
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
        user.id = jsonObject.getString("account_id");
        JSONObject nameObject = jsonObject.getJSONObject("name");
        user.name = nameObject.getString("familiar_name");
        user.displayName = nameObject.getString("display_name");
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
            map.put(CFolder.PATH, jsonObject.getString("path_lower"));
            map.put(CFolder.DATE_FORMAT, "yyyy-MM-dd'T'HH:mm:ss'Z'");
            if (jsonObject.has("created_at"))
                map.put(CFolder.CREATED, jsonObject.getString("created_at"));
            if (jsonObject.has("client_modified"))
                map.put(CFolder.MODIFIED, jsonObject.getString("client_modified"));
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
            map.put(CFolder.PATH, jsonObject.getString("path_lower"));
            map.put(CFolder.DATE_FORMAT, "yyyy-MM-dd'T'HH:mm:ss'Z'");
            if (jsonObject.has("created_at"))
                map.put(CFolder.CREATED, jsonObject.getString("created_at"));
            if (jsonObject.has("client_modified"))
                map.put(CFolder.MODIFIED, jsonObject.getString("client_modified"));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        return new CFile(map);
    }

    private Context mContext;
    private CloudProvider mCloudProvider;
    private Account mAccount;
    private OnPrepareListener mPrepareListener;
    private OkHttpClient mHttpClient;
    private String mAccessToken;

    /**
     * Constructor for Dropbox API
     *
     * @param account Box account to be used must be Box cloud type
     */
    public DropboxApi(Context context, Account account) {
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
                            resetAccount();
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

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("path", folder.getPath());
            params.put("recursive", false);
            params.put("include_media_info", false);
            params.put("include_deleted", false);
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
                .url(API_BASE_URL + "/files/list_folder")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                JSONArray entries = jsonObject.getJSONArray("entries");
                if (entries.length() > 0) {
                    list.addAll(createItemList(entries));
                } else {
                    // return null if no item found
                    return null;
                }
                // expect more search result
                if (jsonObject.getBoolean("has_more")) {
                    list.addAll(exploreFolderContinue(jsonObject.getString("cursor")));
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
     * @param cursor id from the previous request
     * @return List that contains CFile and CFolder
     * @throws RequestFailException that content various error types
     */
    public synchronized List<Object> exploreFolderContinue(String cursor) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        List<Object> list = new ArrayList<>();

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("cursor", cursor);
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
                .url(API_BASE_URL + "/files/list_folder/continue")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                JSONArray entries = jsonObject.getJSONArray("entries");
                if (entries.length() > 0) {
                    list.addAll(createItemList(entries));
                } else {
                    // return null if no item found
                    return null;
                }
                // expect more search result
                if (jsonObject.getBoolean("has_more")) {
                    list.addAll(exploreFolderContinue(jsonObject.getString("cursor")));
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
        root.setPath("");
        root.setRoot(true);
        return root;
    }

    @Override
    public CFolder getFolderInfo(@NonNull String folderId) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("path", folderId);
            params.put("include_media_info", false);
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
                .url(API_BASE_URL + "/files/get_metadata")
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
    public CFolder createFolder(@NonNull String name, @Nullable CFolder parent) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("path", (parent != null ? parent.getPath() : getRoot().getPath()) + "/" + name);
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
                .url(API_BASE_URL + "/files/create_folder")
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
            params.put("from_path", folder.getPath());
            params.put("to_path", renameLastPathSegment(folder.getPath(), name));
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
                .url(API_BASE_URL + "/files/move")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(body)
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
            params.put("from_path", folder.getPath());
            params.put("to_path", renameLastPathSegment((parent != null ?
                    parent.getPath() : getRoot().getPath()), folder.getName()));
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
                .url(API_BASE_URL + "/files/move")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(body)
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

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("path", folder.getPath());
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
                .url(API_BASE_URL + "/files/delete")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(body)
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

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("path", fileId);
            params.put("include_media_info", false);
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
                .url(API_BASE_URL + "/files/get_metadata")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(body)
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
    public CFile uploadFile(@NonNull final File file, @Nullable CFolder parent) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }
        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("path", (parent != null ? parent.getPath() : getRoot().getPath()) + "/" + file.getName());
            params.put("mode", "add");
            params.put("autorename", false);
            params.put("mute", false);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }

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
                .url(API_CONTENT_URL + "/files/upload ")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .header("Dropbox-API-Arg", params.toString())
                .header("Content-Type", "application/octet-stream")
                .post(fileBody)
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
    public CFile updateFile(@NonNull CFile file, final File content) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }
        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("path", file.getPath());
            params.put("mode", "overwrite");
            params.put("autorename", false);
            params.put("mute", false);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }

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
                .url(API_CONTENT_URL + "/files/upload ")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .header("Dropbox-API-Arg", params.toString())
                .header("Content-Type", "application/octet-stream")
                .post(fileBody)
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
            params.put("from_path", file.getPath());
            params.put("to_path", renameLastPathSegment(file.getPath(), name));
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
                .url(API_BASE_URL + "/files/move")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                // return folder object
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
            params.put("from_path", file.getPath());
            params.put("to_path", (folder != null ? folder.getPath() : getRoot().getPath()) + "/" + file.getName());
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
                .url(API_BASE_URL + "/files/move")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                // return folder object
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
        }    }

    @Override
    public File downloadFile(@NonNull CFile file, String filename) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("path", file.getId());
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }

        // need to create blank body to use post method
        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return null;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {

            }
        };

        Request request = new Request.Builder()
                .url(API_CONTENT_URL + "/files/download")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .header("Dropbox-API-Arg", params.toString())
                .post(body)
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

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("path", file.getPath());
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
                .url(API_BASE_URL + "/files/delete")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(body)
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

        Map<String, Object> params = new HashMap<>();
        params.put("path", folder.getPath());
        params.put("query", keyword);

        return search(params);
    }

    /**
     * Search the cloud for folder
     *
     * @param params for search query
     * @return  list of files and folders that match search criteria
     * @throws RequestFailException
     */
    public List<Object> search(@NonNull Map<String, Object> params) throws RequestFailException {
        List<Object> list = new ArrayList<>();

        // create parameter as json
        final JSONObject jsonParams= new JSONObject();
        try {
            jsonParams.put("start", 0);
            jsonParams.put("max_results", 100);
            jsonParams.put("mode", "filename");
            for (Map.Entry<String, Object> param : params.entrySet()) {
                jsonParams.put(param.getKey(), param.getValue());
            }
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
                sink.writeUtf8(jsonParams.toString());
            }
        };

        Request request = new Request.Builder()
                .url(API_BASE_URL + "/files/search")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                JSONArray entries = jsonObject.getJSONArray("matches");
                JSONArray filterEntries = new JSONArray();
                for (int i = 0; i < entries.length(); i++) {
                    filterEntries.put(entries.getJSONObject(i).getJSONObject("metadata"));
                }
                if (filterEntries.length() > 0) {
                    list.addAll(createItemList(filterEntries));
                } else {
                    // return null if no item found
                    return null;
                }
                // expect more search result
                if (jsonObject.getBoolean("more")) {
                    int start = jsonObject.getInt("start");
                    params.put("start", start);
                    list.addAll(search(params));
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

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("path", file.getId());
            params.put("format", "jpeg");
            params.put("size", "w128h128");
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }

        // need to create blank body to use post method
        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return null;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {

            }
        };

        Request request = new Request.Builder()
                .url(API_CONTENT_URL + "/files/get_thumbnail")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .header("Dropbox-API-Arg", params.toString())
                .post(body)
                .build();

        try {
            File localFile = new File(mContext.getFilesDir(), file.getId() + ".jpg");

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
                String type = jsonObject.getString(".tag");
                switch (type) {
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
     * Rename last path segment to new name
     *
     * @param oldPath to be changed
     * @param name for the new folder
     * @return String
     */
    private String renameLastPathSegment(String oldPath, String name) {
        int lastSegmentPos;
        if ((lastSegmentPos = oldPath.lastIndexOf('/')) > -1) {
            return oldPath.substring(0, lastSegmentPos + 1) + name;
        } else {
            // return old path if fail
            return oldPath;
        }
    }
}
