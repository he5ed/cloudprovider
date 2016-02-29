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
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okio.BufferedSink;

/**
 * Box cloud service API implementation
 *
 * @hide
 */
public class BoxApi extends BaseApi {

    // API server constant values
    public static final String AUTH_URL = "https://app.box.com/api/oauth2/authorize";
    public static final String TOKEN_URL = "https://api.box.com/oauth2/token";
    public static final String REVOKE_URL = "https://api.box.com/oauth2/revoke";

    public static final String API_BASE_URL = "https://api.box.com/2.0";
    public static final String API_UPLOAD_URL = "https://upload.box.com/api/2.0";

    public static final String ROOT_ID = "0";

    /**
     * Must override with the correct values
     */
    public static String CLIENT_ID = null;
    public static String CLIENT_SECRET = null;
    public static String REDIRECT_URL = null;

    // enable API
    public static boolean ENABLE_API = false;

    // class constant values
    public static final String NAME = "Box";
    public static final int ICON_RESOURCE = R.drawable.ic_box_color_24dp;
    public static final String TAG = "BoxApi";
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
    public static Map<String, String> extractAccessToken(JSONObject jsonObject)
            throws JSONException {
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
        Uri uri = Uri.parse(API_BASE_URL);
        return uri.buildUpon().appendEncodedPath("users/me").build();
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
        user.email = jsonObject.getString("login");
        user.avatarUrl = jsonObject.getString("avatar_url");

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
            if (jsonObject.has("path_collection")) {
                JSONArray paths = jsonObject.getJSONObject("path_collection").getJSONArray("entries");

                StringBuilder pathBuilder = new StringBuilder();
                for (int i = 0; i < paths.length(); i++) {
                    JSONObject path = paths.getJSONObject(i);
                    pathBuilder.append(path.getString("name")).append("/");
                }

                map.put(CFolder.PATH, pathBuilder.toString());
            }

            if (jsonObject.has("size"))
                map.put(CFolder.SIZE, (long) jsonObject.getInt("size"));

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
     * @return CFile
     */
    public static CFile buildFile(JSONObject jsonObject) {
        Map<String, Object> map = new HashMap<>();
        try {
            map.put(CFile.ID, jsonObject.getString("id"));
            map.put(CFile.NAME, jsonObject.getString("name"));
            if (jsonObject.has("path_collection")) {
                JSONArray paths = jsonObject.getJSONObject("path_collection").getJSONArray("entries");

                StringBuilder pathBuilder = new StringBuilder();
                for (int i = 0; i < paths.length(); i++) {
                    JSONObject path = paths.getJSONObject(i);
                    pathBuilder.append(path.getString("name")).append("/");
                }

                map.put(CFolder.PATH, pathBuilder.toString());
            }

            if (jsonObject.has("size"))
                map.put(CFolder.SIZE, (long) jsonObject.getInt("size"));

            map.put(CFile.DATE_FORMAT, "yyyy-MM-dd'T'HH:mm:ssZ");

            if (jsonObject.has("created_at"))
                map.put(CFile.CREATED, jsonObject.getString("created_at"));
            if (jsonObject.has("modified_at"))
                map.put(CFile.MODIFIED, jsonObject.getString("modified_at"));
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
     * Constructor for Box API
     *
     * @param account Box account to be used must be Box cloud type
     */
    public BoxApi(Context context, Account account) {
        mContext = context;
        mAccount = account;
        mCloudProvider = CloudProvider.getInstance(mContext);
        mHttpClient = new OkHttpClient();
    }

    @Override
    public synchronized void prepareApi(OnPrepareListener prepareListener) {
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
                            // no remedy
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
    public synchronized void logout(@NonNull Callback callback) {
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
        String folderId = folder.getId();
        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("folders/" + folderId + "/items")
                .appendQueryParameter("fields", "type,id,name,created_at,modified_at,path_collection,size")
                .appendQueryParameter("limit", "500")
                .appendQueryParameter("offset", String.valueOf(offset))
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
                int total = jsonObject.getInt("total_count");
                // return null if no item found
                if (total == 0) return null;

                JSONArray entries = jsonObject.getJSONArray("entries");
                list.addAll(createFilteredItemsList(entries, folder));
                // suspect search result over 500 items
                if (total > 500 && total > list.size()) {
                    list.addAll(exploreFolder(folder, list.size()));
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
    public synchronized CFolder getFolderInfo(@NonNull String folderId) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        if (TextUtils.isEmpty(folderId)) return null;

        Request request = new Request.Builder()
                .url(API_BASE_URL + "/folders/" + folderId)
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
    public synchronized CFolder createFolder(@NonNull String name, @Nullable CFolder parent) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }
        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("name", name);
            params.put("parent",
                    new JSONObject().put("id", parent != null ? parent.getId() : getRoot().getId()));
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
                .url(API_BASE_URL + "/folders")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful() && response.code() == 201) {
                // new folder created
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
    public synchronized CFolder renameFolder(@NonNull CFolder folder, String name) throws RequestFailException {
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
                .url(API_BASE_URL + "/folders/" + folder.getId())
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .put(body)
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
    public synchronized CFolder moveFolder(@NonNull CFolder folder, @Nullable CFolder parent) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        // exit if root or same name
        if (folder.isRoot() || parent != null && folder.getId().equals(parent.getId())) return folder;

        // create parameter as json
        final JSONObject params = new JSONObject();
        try {
            params.put("parent", new JSONObject()
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
                .url(API_BASE_URL + "/folders/" + folder.getId())
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .put(body)
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
    public synchronized void deleteFolder(@NonNull CFolder folder) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        String folderId = folder.getId();
        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("folders/" + folderId)
                .appendQueryParameter("recursive", "true")
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .delete()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                Log.d(TAG, "CFolder with the id: " + folderId + " deleted");
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public synchronized CFile getFileInfo(@NonNull String fileId) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        if (TextUtils.isEmpty(fileId)) return null;

        Request request = new Request.Builder()
                .url(API_BASE_URL + "/files/" + fileId)
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
    public synchronized File downloadFile(@NonNull CFile file, @Nullable String filename) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        // assign filename
        if (TextUtils.isEmpty(filename)) filename = file.getName();

        String fileId = file.getId();
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/files/" + fileId + "/content")
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
                    case 202:
                        // retry after due to file just uploaded
                        delayDownloadFile(file, filename);
                        break;
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
     * Retry download operation due to file just uploaded
     *
     * @param file file to be downloaded
     * @param filename for the downloaded file, null for original filename
     */
    private void delayDownloadFile(final CFile file, final String filename) {
        Handler delayHandler = new Handler();
        delayHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    downloadFile(file, filename);
                } catch (RequestFailException e) {
                    // no remedy
                    e.printStackTrace();
                    Log.e(TAG, e.getMessage());
                }
            }
        }, 500);
    }

    /**
     * Download file from redirect request
     *
     * @param request for redirect
     * @return File
     * @throws RequestFailException
     */
    private File downloadFile(@NonNull Request request, @NonNull String filename) throws RequestFailException {
        try {
            File file = new File(CloudProvider.CACHE_DIR, filename);

            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                if (FilesUtils.getInternalAvailableBytes() < response.body().contentLength()) {
                    // insufficient storage space throw exception
                    throw new RequestFailException("Insufficient storage");
                } else {
                    FilesUtils.copyFile(response.body().byteStream(), new FileOutputStream(file));
                }
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
    public synchronized CFile uploadFile(@NonNull File file, @Nullable CFolder parent) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("name", file.getName());
            params.put("parent",
                    new JSONObject().put("id", parent != null ? parent.getId() : getRoot().getId()));
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }

        // create multipart body
        MediaType fileType = MediaType.parse(FilesUtils.getFileType(file));
        RequestBody multipart = new MultipartBuilder()
                .type(MultipartBuilder.FORM)
                .addFormDataPart("attributes", params.toString())
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(fileType, file))
                .build();

        Request request = new Request.Builder()
                .url(API_UPLOAD_URL + "/files/content")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(multipart)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                // new file created
                JSONObject jsonObject = new JSONObject(response.body().string());
                JSONArray entries = jsonObject.getJSONArray("entries");
                return buildFile(entries.getJSONObject(0));
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
    public synchronized CFile updateFile(@NonNull CFile file, File content) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        // create multipart body
        MediaType fileType = MediaType.parse(FilesUtils.getFileType(content));
        RequestBody multipart = new MultipartBuilder()
                .type(MultipartBuilder.FORM)
                .addFormDataPart("file", content.getName(),
                        RequestBody.create(fileType, content))
                .build();

        Request request = new Request.Builder()
                .url(API_UPLOAD_URL + "/files/" + file.getId() + "/content")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(multipart)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                // new file created
                JSONObject jsonObject = new JSONObject(response.body().string());
                JSONArray entries = jsonObject.getJSONArray("entries");
                return buildFile(entries.getJSONObject(0));
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
    public synchronized CFile renameFile(@NonNull CFile file, String name) throws RequestFailException {
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
                .url(API_BASE_URL + "/files/" + file.getId())
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .put(body)
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
    public synchronized CFile moveFile(@NonNull CFile file, @Nullable CFolder folder) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        // create parameter as json
        final JSONObject params = new JSONObject();
        try {
            params.put("parent", new JSONObject()
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
                .url(API_BASE_URL + "/files/" + file.getId())
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .put(body)
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
    public synchronized void deleteFile(@NonNull CFile file) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        String fileId = file.getId();
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/files/" + fileId)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .delete()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                Log.d(TAG, "File with the id: " + fileId + " deleted");
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public synchronized List<CFile> searchFile(@NonNull String keyword, CFolder folder)
            throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("query", keyword);
        params.put("type", "file");

        // check cast for search result
        List<CFile> output = new ArrayList<>();
        for (Object item : search(params, folder)) {
            if (CFile.class.isAssignableFrom(item.getClass()))
                output.add(CFile.class.cast(item));
        }

        return output;
    }

    @Override
    public synchronized List<CFolder> searchFolder(@NonNull String keyword, CFolder folder)
            throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("query", keyword);
        params.put("type", "folder");

        // check cast for search result
        List<CFolder> output = new ArrayList<>();
        for (Object item : search(params, folder)) {
            if (CFolder.class.isAssignableFrom(item.getClass()))
                output.add(CFolder.class.cast(item));
        }

        return output;
    }

    @Override
    public synchronized List<Object> search(@NonNull String keyword, CFolder folder)
            throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("query", keyword);

        return search(params, folder);
    }

    /**
     * Search the cloud for all contents
     *
     * @param params for search query
     * @param parent folder wto search for
     * @return  list of files and folders that match search criteria
     * @throws RequestFailException
     */
    public synchronized List<Object> search(@NonNull Map<String, Object> params, CFolder parent) throws RequestFailException {
        List<Object> list = new ArrayList<>();

        Uri uri = Uri.parse(API_BASE_URL);
        Uri.Builder urlBuilder = uri.buildUpon().appendEncodedPath("search");
        // pre-defined parameters
        urlBuilder.appendQueryParameter("limit", "100");
        urlBuilder.appendQueryParameter("scope", "user_content");
        // add the rest of the user defined parameters
        params.put("ancestor_folder_ids", parent.getId());
        for (Map.Entry<String, Object> param : params.entrySet()) {
            urlBuilder.appendQueryParameter(param.getKey(), (String) param.getValue());
        }

        Request request = new Request.Builder()
                .url(urlBuilder.toString())
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                int total = jsonObject.getInt("total_count");
                // return null if no item found
                if (total == 0) return null;

                JSONArray entries = jsonObject.getJSONArray("entries");
                list.addAll(createFilteredItemsList(entries, parent));
                // suspect search result over 100 items
                if (total > 100 && total - list.size() > 0) {
                    params.put("offset", "100");
                    list.addAll(search(params, parent));
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
    public synchronized File getThumbnail(@NonNull CFile file) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("files/" + file.getId() + "/thumbnail.png")
                .appendQueryParameter("min_height", "100")
                .appendQueryParameter("min_width", "100")
                .appendQueryParameter("max_height", "256")
                .appendQueryParameter("max_width", "256")
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
                switch (response.code()) {
                    case 200:
                        // redirect to url
                        return downloadFile(response.request(), file.getId() + ".png");
                    case 202:
                        // retry after due to file just uploaded
                        delayDownloadFile(file, file.getId() + ".png");
                        break;
                    case 302:
                        // redirect to url
                        return downloadFile(response.request(), file.getId() + ".png");
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

    @Override
    public void getFolderInfoAsync(@NonNull String folderId, final IApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }

        if (TextUtils.isEmpty(folderId)) return;

        final Request request = new Request.Builder()
                .url(API_BASE_URL + "/folders/" + folderId)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                // send exception to callback
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    // create folder and send to callback
                    callback.onReceiveItems(request, Arrays.asList(buildFolder(jsonObject)));
                } catch (JSONException e) {
                    // send exception to callback
                    callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                }
            }
        });
    }

    @Override
    public void exploreFolderAsync(@NonNull final CFolder folder, int offset, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }

        final ArrayList<Parcelable> list = new ArrayList<>();
        String folderId = folder.getId();
        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("folders/" + folderId + "/items")
                .appendQueryParameter("fields", "type,id,name,created_at,modified_at,path_collection,size")
                .appendQueryParameter("limit", "500")
                .appendQueryParameter("offset", String.valueOf(offset))
                .build()
                .toString();

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    // create folder and send to callback
                    int total = jsonObject.getInt("total_count");
                    // return null if no item found
                    if (total == 0) {
                        callback.onReceiveItems(request, null);
                        // need to return in current thread
                        return;
                    }

                    JSONArray entries = jsonObject.getJSONArray("entries");
                    list.addAll(createFilteredItemsList(entries, folder));
                    // check bundle for existing list
                    List<Parcelable> previousList = callback.bundle.getParcelableArrayList("previous_list");
                    if (previousList != null && previousList.size() > 0)
                        list.addAll(previousList);

                    // suspect search result over 500 items
                    if (total > 500 && total > list.size()) {
                        // update previous list
                        callback.bundle.putParcelableArrayList("previous_list", list);
                        exploreFolderAsync(folder, list.size(), callback);
                    } else {
                        callback.onReceiveItems(request, list);
                    }
                } catch (JSONException e) {
                    // send exception to callback
                    callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                } catch (RequestFailException e) {
                    e.printStackTrace();
                    // send exception to callback
                    callback.onRequestFailure(request, e);
                }
            }
        });
    }

    @Override
    public void createFolderAsync(@NonNull String name, @Nullable CFolder parent, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }
        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("name", name);
            params.put("parent",
                    new JSONObject().put("id", parent != null ? parent.getId() : getRoot().getId()));
        } catch (JSONException e) {
            e.printStackTrace();
            callback.onRequestFailure(null, new RequestFailException(e.getMessage()));
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

        final Request request = new Request.Builder()
                .url(API_BASE_URL + "/folders")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(body)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                // send exception to callback
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    // create folder and send to callback
                    callback.onReceiveItems(request, Arrays.asList(buildFolder(jsonObject)));
                } catch (JSONException e) {
                    // send exception to callback
                    callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                }
            }
        });
    }

    @Override
    public void renameFolderAsync(@NonNull CFolder folder, String name, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }

        // exit if root or same name
        if (folder.isRoot() || folder.getName().equals(name)) {
            callback.onReceiveItems(null, Arrays.asList(folder));
            return;
        }

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("name", name);
        } catch (JSONException e) {
            e.printStackTrace();
            callback.onRequestFailure(null, new RequestFailException(e.getMessage()));
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

        final Request request = new Request.Builder()
                .url(API_BASE_URL + "/folders/" + folder.getId())
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .put(body)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    // return folder object
                    try {
                        CFolder newFolder = buildFolder(new JSONObject(response.body().string()));
                        callback.onReceiveItems(request, Arrays.asList(newFolder));
                    } catch (JSONException e) {
                        e.printStackTrace();
                        callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                    }
                } else {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                }

            }
        });
    }

    @Override
    public void moveFolderAsync(@NonNull CFolder folder, @Nullable CFolder parent, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }

        // exit if root or same name
        if (folder.isRoot() || parent != null && folder.getId().equals(parent.getId())) {
            callback.onReceiveItems(null, Arrays.asList(folder));
            return;
        }

        // create parameter as json
        final JSONObject params = new JSONObject();
        try {
            params.put("parent", new JSONObject()
                    .put("id", parent != null ? parent.getId() : getRoot().getId()));
        } catch (JSONException e) {
            e.printStackTrace();
            callback.onRequestFailure(null, new RequestFailException(e.getMessage()));
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

        final Request request = new Request.Builder()
                .url(API_BASE_URL + "/folders/" + folder.getId())
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .put(body)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    // return folder object
                    try {
                        CFolder newFolder = buildFolder(new JSONObject(response.body().string()));
                        callback.onReceiveItems(request, Arrays.asList(newFolder));
                    } catch (JSONException e) {
                        e.printStackTrace();
                        callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                    }
                } else {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                }
            }
        });
    }

    @Override
    public void deleteFolderAsync(@NonNull CFolder folder, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }

        final String folderId = folder.getId();
        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("folders/" + folderId)
                .appendQueryParameter("recursive", "true")
                .build()
                .toString();

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .delete()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Folder with the id: " + folderId + " deleted");
                    callback.onReceiveItems(request, null);
                } else {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                }
            }
        });
    }

    @Override
    public void getFileInfoAsync(@NonNull String fileId, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }

        if (TextUtils.isEmpty(fileId)) {
            return;
        }

        final Request request = new Request.Builder()
                .url(API_BASE_URL + "/files/" + fileId)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    // new file created
                    CFile file = null;
                    try {
                        file = buildFile(new JSONObject(response.body().string()));
                        callback.onReceiveItems(request, Arrays.asList(file));
                    } catch (JSONException e) {
                        e.printStackTrace();
                        callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                    }

                } else {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                }
            }
        });
    }

    @Override
    public void uploadFileAsync(@NonNull File file, @Nullable CFolder parent, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("name", file.getName());
            params.put("parent",
                    new JSONObject().put("id", parent != null ? parent.getId() : getRoot().getId()));
        } catch (JSONException e) {
            e.printStackTrace();
            callback.onRequestFailure(null, new RequestFailException(e.getMessage()));
        }

        // create multipart body
        MediaType fileType = MediaType.parse(FilesUtils.getFileType(file));
        RequestBody multipart = new MultipartBuilder()
                .type(MultipartBuilder.FORM)
                .addFormDataPart("attributes", params.toString())
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(fileType, file))
                .build();

        final Request request = new Request.Builder()
                .url(API_UPLOAD_URL + "/files/content")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(multipart)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        // new file created
                        JSONObject jsonObject = null;
                        jsonObject = new JSONObject(response.body().string());
                        JSONArray entries = jsonObject.getJSONArray("entries");
                        callback.onReceiveItems(request, Arrays.asList(buildFile(entries.getJSONObject(0))));
                    } catch (JSONException e) {
                        e.printStackTrace();
                        callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                    }
                } else {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                }
            }
        });
    }

    @Override
    public void updateFileAsync(@NonNull CFile file, File content, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }

        // create multipart body
        MediaType fileType = MediaType.parse(FilesUtils.getFileType(content));
        RequestBody multipart = new MultipartBuilder()
                .type(MultipartBuilder.FORM)
                .addFormDataPart("file", content.getName(),
                        RequestBody.create(fileType, content))
                .build();

        final Request request = new Request.Builder()
                .url(API_UPLOAD_URL + "/files/" + file.getId() + "/content")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .post(multipart)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    // new file created
                    JSONObject jsonObject = null;
                    try {
                        jsonObject = new JSONObject(response.body().string());
                        JSONArray entries = jsonObject.getJSONArray("entries");
                        callback.onReceiveItems(request, Arrays.asList(buildFile(entries.getJSONObject(0))));
                    } catch (JSONException e) {
                        e.printStackTrace();
                        callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                    }
                } else {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));                }
            }
        });
    }

    @Override
    public void renameFileAsync(@NonNull CFile file, String name, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }

        // exist if same filename
        if (file.getName().equals(name)) {
            return;
        }

        // create parameter as json
        final JSONObject params= new JSONObject();
        try {
            params.put("name", name);
        } catch (JSONException e) {
            e.printStackTrace();
            callback.onRequestFailure(null, new RequestFailException(e.getMessage()));
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

        final Request request = new Request.Builder()
                .url(API_BASE_URL + "/files/" + file.getId())
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .put(body)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    // return file object
                    try {
                        CFile newFile = buildFile(new JSONObject(response.body().string()));
                        callback.onReceiveItems(request, Arrays.asList(newFile));
                    } catch (JSONException e) {
                        e.printStackTrace();
                        callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                    }
                } else {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                }
            }
        });
    }

    @Override
    public void moveFileAsync(@NonNull CFile file, @Nullable CFolder folder, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }

        // create parameter as json
        final JSONObject params = new JSONObject();
        try {
            params.put("parent", new JSONObject()
                    .put("id", folder != null ? folder.getId() : getRoot().getId()));
        } catch (JSONException e) {
            e.printStackTrace();
            callback.onRequestFailure(null, new RequestFailException(e.getMessage()));
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

        final Request request = new Request.Builder()
                .url(API_BASE_URL + "/files/" + file.getId())
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .put(body)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    // return file object
                    try {
                        CFile newFile = buildFile(new JSONObject(response.body().string()));
                        callback.onReceiveItems(request, Arrays.asList(newFile));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));                }
            }
        });
    }

    @Override
    public void downloadFileAsync(@NonNull final CFile file, @Nullable final String filename, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }

        String fileId = file.getId();
        final Request request = new Request.Builder()
                .url(API_BASE_URL + "/files/" + fileId + "/content")
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String finalFilename = TextUtils.isEmpty(filename) ? file.getName() : filename;

                        switch (response.code()) {
                            case 200:
                                // redirect to url
                                downloadFileAsync(response.request(), finalFilename, callback);
                                break;
                            case 202:
                                // retry after due to file just uploaded
                                delayDownloadFileAsync(file, finalFilename, callback);
                                break;
                            case 302:
                                // redirect to url
                                downloadFileAsync(response.request(), finalFilename, callback);
                                break;
                        }
                    } catch (RequestFailException e) {
                        e.printStackTrace();
                        callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                    }
                } else {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                }
            }
        });
    }

    /**
     * Async retry download operation due to file just uploaded
     *
     * @param file file to be downloaded
     * @param filename for the downloaded file, null for original filename
     * @param callback to return the request result back
     */
    private void delayDownloadFileAsync(final CFile file, final String filename, final ApiCallback callback) {
        // async mode run on non-ui thread, must setup new looper for handler
        Looper.prepare();
        Looper.loop();

        Handler delayHandler = new Handler();
        delayHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                downloadFileAsync(file, filename, callback);
            }
        }, 500);
    }

    /**
     * Async download file from redirect request
     *
     * @param request for redirect
     * @param callback to return the request result back
     * @throws RequestFailException
     */
    private void downloadFileAsync(@NonNull final Request request, @NonNull String filename, final ApiCallback callback)
            throws RequestFailException {
        final File file = new File(CloudProvider.CACHE_DIR, filename);

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    if (FilesUtils.getInternalAvailableBytes() < response.body().contentLength()) {
                        // insufficient storage space throw exception
                        callback.onRequestFailure(request, new RequestFailException("Insufficient storage"));
                    } else {
                        FilesUtils.copyFile(response.body().byteStream(), new FileOutputStream(file));
                        callback.onReceiveItems(request, Arrays.asList(file));
                    }
                } else {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                }
            }
        });
    }

    @Override
    public void deleteFileAsync(@NonNull CFile file, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }

        final String fileId = file.getId();
        final Request request = new Request.Builder()
                .url(API_BASE_URL + "/files/" + fileId)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .delete()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "File with the id: " + fileId + " deleted");
                    callback.onReceiveItems(request, null);
                } else {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                }
            }
        });
    }

    @Override
    public void searchFileAsync(@NonNull String keyword, CFolder folder, ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }

        Map<String, Object> params = new HashMap<>();
        params.put("query", keyword);
        params.put("type", "file");

        searchAsync(params, folder, callback);
    }

    @Override
    public void searchFolderAsync(@NonNull String keyword, CFolder folder, ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }

        Map<String, Object> params = new HashMap<>();
        params.put("query", keyword);
        params.put("type", "folder");

        searchAsync(params, folder, callback);
    }

    @Override
    public void searchAsync(@NonNull String keyword, CFolder folder, ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }

        Map<String, Object> params = new HashMap<>();
        params.put("query", keyword);

        searchAsync(params, folder, callback);
    }

    /**
     * Search the cloud for all contents
     *
     * @param params for search query
     * @param parent folder wto search for
     * @param callback to return the request result back
     */
    public synchronized void searchAsync(@NonNull final Map<String, Object> params,
                                         final CFolder parent, final ApiCallback callback) {
        final ArrayList<Parcelable> list = new ArrayList<>();

        Uri uri = Uri.parse(API_BASE_URL);
        Uri.Builder urlBuilder = uri.buildUpon().appendEncodedPath("search");
        // pre-defined parameters
        urlBuilder.appendQueryParameter("limit", "100");
        urlBuilder.appendQueryParameter("scope", "user_content");
        // add the rest of the user defined parameters
        params.put("ancestor_folder_ids", parent.getId());
        for (Map.Entry<String, Object> param : params.entrySet()) {
            urlBuilder.appendQueryParameter(param.getKey(), (String) param.getValue());
        }

        final Request request = new Request.Builder()
                .url(urlBuilder.toString())
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        int total = jsonObject.getInt("total_count");
                        // return null if no item found
                        if (total == 0) {
                            callback.onReceiveItems(request, null);
                            return;
                        }

                        JSONArray entries = jsonObject.getJSONArray("entries");
                        list.addAll(createFilteredItemsList(entries, parent));
                        // check bundle for existing list
                        List<Parcelable> previousList = callback.bundle.getParcelableArrayList("previous_list");
                        if (previousList != null && previousList.size() > 0)
                            list.addAll(previousList);
                        // suspect search result over 100 items
                        if (total > 100 && total > list.size()) {
                            params.put("offset", String.valueOf(list.size()));
                            // update previous list
                            callback.bundle.putParcelableArrayList("previous_list", list);
                            searchAsync(params, parent, callback);
                        } else {
                            callback.onReceiveItems(request, list);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                    } catch (RequestFailException e) {
                        e.printStackTrace();
                        callback.onRequestFailure(request, e);
                    }
                } else {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                }
            }
        });
    }

    @Override
    public void getThumbnailAsync(@NonNull final CFile file, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("files/" + file.getId() + "/thumbnail.png")
                .appendQueryParameter("min_height", "100")
                .appendQueryParameter("min_width", "100")
                .appendQueryParameter("max_height", "256")
                .appendQueryParameter("max_width", "256")
                .build()
                .toString();

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("Bearer %s", mAccessToken))
                .get()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        switch (response.code()) {
                            case 200:
                                // redirect to url
                                downloadFileAsync(response.request(), file.getId() + ".png", callback);
                                break;
                            case 202:
                                // retry after due to file just uploaded
                                delayDownloadFileAsync(file, file.getId() + ".png", callback);
                                break;
                            case 302:
                                // redirect to url
                                downloadFileAsync(response.request(), file.getId() + ".png", callback);
                                break;
                        }
                    } catch (RequestFailException e) {
                        e.printStackTrace();
                        callback.onRequestFailure(request, e);
                    }
                } else {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                }
            }
        });
    }

    /**
     * Create files or folders from the JSONArray search result
     *
     * @param jsonArray that contain files and folders information
     * @param parent folder that contain the items returned
     * @return list that contains CFile and CFolder that belong to the parent folder
     * @throws RequestFailException
     */
    private synchronized List createFilteredItemsList(JSONArray jsonArray, CFolder parent) throws RequestFailException {
        if (jsonArray == null || jsonArray.length() == 0) return null;

        List<Object> list = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                // filter parent
                if (parent != null && jsonObject.has("parent") &&
                        !jsonObject.getJSONObject("parent").getString("id").equals(parent.getId()))
                    continue;

                String type = jsonObject.getString("type");
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

}
