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
 * Yandex Disk cloud service API implementation
 *
 * @hide
 */
public class YandexApi extends BaseApi {

    // API server constant values
    public static final String AUTH_URL = "https://oauth.yandex.com/authorize";
    public static final String TOKEN_URL = "https://oauth.yandex.com/token";

    public static final String API_BASE_URL = "https://cloud-api.yandex.net/v1";
    public static final String API_USER_URL = "https://login.yandex.ru/info";

    private static final String ROOT_PATH = "disk:";

    /**
     * Must override with the correct values
     */
    public static String CLIENT_ID = null;
    public static String CLIENT_SECRET = null;
    public static String REDIRECT_URL = null;

    // enable API
    public static boolean ENABLE_API = false;

    // class constant values
    public static final String NAME = "Yandex Disk";
    public static final int ICON_RESOURCE = R.drawable.ic_yandex_color_24dp;
    public static final String TAG = "YandexApi";
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
        return map;
    }

    /**
     * Create request to get user information
     *
     * @param accessToken access token for authorization
     * @return Request
     */
    public static Request getUserInfoRequest(String accessToken) {
        return new Request.Builder()
                .url(API_USER_URL)
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
        user.name = jsonObject.getString("real_name");
        user.displayName = jsonObject.getString("real_name");
        user.email = jsonObject.getString("default_email");
        user.avatarUrl = String.format("https://avatars.yandex.net/get-yapic/%s/islands-200", user.id);
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
            map.put(CFolder.ID, jsonObject.getString("path"));
            map.put(CFolder.NAME, jsonObject.getString("name"));
            map.put(CFolder.PATH, jsonObject.getString("path"));

            if (jsonObject.has("size"))
                map.put(CFolder.SIZE, (long) jsonObject.getInt("size"));

            map.put(CFolder.DATE_FORMAT, "yyyy-MM-dd'T'HH:mm:ssZ");

            if (jsonObject.has("created"))
                map.put(CFolder.CREATED, jsonObject.getString("created"));
            if (jsonObject.has("modified"))
                map.put(CFolder.MODIFIED, jsonObject.getString("modified"));
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
            map.put(CFile.ID, jsonObject.getString("path"));
            map.put(CFile.NAME, jsonObject.getString("name"));
            map.put(CFolder.PATH, jsonObject.getString("path"));

            if (jsonObject.has("size"))
                map.put(CFolder.SIZE, (long) jsonObject.getInt("size"));

            map.put(CFile.DATE_FORMAT, "yyyy-MM-dd'T'HH:mm:ssZ");

            if (jsonObject.has("created"))
                map.put(CFile.CREATED, jsonObject.getString("created"));
            if (jsonObject.has("modified"))
                map.put(CFile.MODIFIED, jsonObject.getString("modified"));
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
    public YandexApi(Context context, Account account) {
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
    public synchronized void logout(@NonNull Callback callback) {
        RequestBody body = new FormEncodingBuilder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("token", mAccessToken)
                .build();

        Request request = new Request.Builder()
                .url("")
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
        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources")
                .appendQueryParameter("path", folder.getPath())
                .appendQueryParameter("fields", "type,name,created,modified,path,size,_embedded")
                .appendQueryParameter("limit", "100")
                .appendQueryParameter("offset", String.valueOf(offset))
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string())
                        .getJSONObject("_embedded");
                int total = jsonObject.getInt("total");
                // return null if no item found
                if (total == 0) return null;

                JSONArray entries = jsonObject.getJSONArray("items");
                list.addAll(createItemList(entries));

                // suspect search result over 100 items
                if (total > 100 && total > list.size()) {
                    list.addAll(exploreFolder(folder, list.size()));
                }
                return list;
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public CFolder getRoot() {
        // create root folder manually
        CFolder root = new CFolder(null);
        root.setPath(ROOT_PATH);
        root.setName(mContext.getString(R.string.home_folder_title));
        root.setRoot(true);
        return root;
    }

    @Override
    public synchronized CFolder getFolderInfo(@NonNull String folderPath) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources")
                .appendQueryParameter("path", folderPath)
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .get()
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
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public synchronized CFolder createFolder(@NonNull String name, @Nullable CFolder parent) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources")
                .appendQueryParameter("path",
                        (parent != null ? parent.getPath() : getRoot().getPath()) + "/" + name)
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

        // need to create blank body to use put method
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
                .url(url)
                .header("Authorization", mAccessToken)
                .put(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                String metadataUrl = jsonObject.getString("href");
                // return folder object
                return buildFolder(metadataUrl);
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            throw new RequestFailException(e.getMessage());
        }
    }

    /**
     * Build folder by requesting url
     * @param url link to folder's metadata
     * @return CFolder
     * @throws RequestFailException
     */
    private CFolder buildFolder(String url) throws RequestFailException {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .get()
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
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
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

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources/move")
                .appendQueryParameter("from", folder.getPath())
                .appendQueryParameter("path", renameLastPathSegment(folder.getPath(), name))
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

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
                .url(url)
                .header("Authorization", mAccessToken)
                .post(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                String metadataUrl = jsonObject.getString("href");
                // return folder object
                return buildFolder(metadataUrl);
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
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

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources/move")
                .appendQueryParameter("from", folder.getPath())
                .appendQueryParameter("path", (parent != null ? parent.getPath() :
                        getRoot().getPath()) + "/" + folder.getName())
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

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
                .url(url)
                .header("Authorization", mAccessToken)
                .post(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                String metadataUrl = jsonObject.getString("href");
                // return folder object
                return buildFolder(metadataUrl);
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
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
                .appendEncodedPath("disk/resources")
                .appendQueryParameter("path", folder.getPath())
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .delete()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                Log.d(TAG, "Folder with the id: " + folderId + " deleted");
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (IOException e) {
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public synchronized CFile getFileInfo(@NonNull String filePath) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources")
                .appendQueryParameter("path", filePath)
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .get()
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
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            throw new RequestFailException(e.getMessage());
        }
    }

    /**
     * Build file by requesting url
     * @param url link to file's metadata
     * @return CFile
     * @throws RequestFailException
     */
    private CFile buildFile(String url) throws RequestFailException {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .get()
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
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
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

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources/download")
                .appendQueryParameter("path", file.getPath())
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                String downloadUrl = jsonObject.getString("href");
                // redirect to url
                return downloadFile(downloadUrl, filename);
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (IOException e) {
            throw new RequestFailException(e.getMessage());
        } catch (JSONException e) {
            throw new RequestFailException(e.getMessage());
        }
    }

    /**
     * Download file from redirect request
     *
     * @param url for redirect
     * @param filename for downloaded file
     * @return File
     * @throws RequestFailException
     */
    private File downloadFile(@NonNull String url, @NonNull String filename) throws RequestFailException {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                if (FilesUtils.getInternalAvailableBytes() < response.body().contentLength()) {
                    // insufficient storage space throw exception
                    throw new RequestFailException("Insufficient storage");
                } else {
                    File file = new File(CloudProvider.CACHE_DIR, filename);
                    FilesUtils.copyFile(response.body().byteStream(), new FileOutputStream(file));
                    return file;
                }
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (IOException e) {
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public synchronized CFile uploadFile(@NonNull File file, @Nullable CFolder parent) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String path = parent.getPath() + "/" + file.getName();
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources/upload")
                .appendQueryParameter("path", path)
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                String uploadUrl = jsonObject.getString("href");
                // redirect to url
                return uploadFile(uploadUrl, file, path);
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            throw new RequestFailException(e.getMessage());
        }
    }

    /**
     * Upload file from redirect request
     *
     * @param url for redirect
     * @param file to be uploaded
     * @param filePath of the uploaded file relative to the cloud storage's root
     * @return CFile
     * @throws RequestFailException
     */
    private CFile uploadFile(@NonNull String url, @NonNull File file, String filePath) throws RequestFailException {
        // create multipart body
        MediaType fileType = MediaType.parse(FilesUtils.getFileType(file));
        RequestBody multipart = new MultipartBuilder()
                .type(MultipartBuilder.FORM)
                //.addFormDataPart("attributes", params.toString())
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(fileType, file))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .put(multipart)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                // response body is empty!

                // return file object
                return getFileInfo(filePath);
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (IOException e) {
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public synchronized CFile updateFile(@NonNull CFile file, File content) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String path = file.getPath();
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources/upload")
                .appendQueryParameter("path", path)
                .appendQueryParameter("overwrite", "true")
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .get()
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                String uploadUrl = jsonObject.getString("href");
                // redirect to url
                return uploadFile(uploadUrl, content, path);
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
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

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources/move")
                .appendQueryParameter("from", file.getPath())
                .appendQueryParameter("path", renameLastPathSegment(file.getPath(), name))
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

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
                .url(url)
                .header("Authorization", mAccessToken)
                .post(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                String metadataUrl = jsonObject.getString("href");
                // return file object
                return buildFile(metadataUrl);
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public synchronized CFile moveFile(@NonNull CFile file, @Nullable CFolder folder) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources/move")
                .appendQueryParameter("from", file.getPath())
                .appendQueryParameter("path", (folder != null ? folder.getPath() :
                        getRoot().getPath()) + "/" + file.getName())
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

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
                .url(url)
                .header("Authorization", mAccessToken)
                .post(body)
                .build();

        try {
            Response response = mHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                String metadataUrl = jsonObject.getString("href");
                // return file object
                return buildFile(metadataUrl);
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public synchronized void deleteFile(@NonNull CFile file) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        String fileId = file.getId();
        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources")
                .appendQueryParameter("path", file.getPath())
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
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
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public synchronized List<CFile> searchFile(@NonNull String keyword, CFolder folder)
            throws RequestFailException {

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
    public synchronized List<CFolder> searchFolder(@NonNull String keyword, CFolder folder)
            throws RequestFailException {

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
    public synchronized List<Object> search(@NonNull String keyword, CFolder folder)
            throws RequestFailException {

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
    public synchronized File getThumbnail(@NonNull CFile file) throws RequestFailException {
        if (TextUtils.isEmpty(mAccessToken)) {
            throw new RequestFailException("Access token not available");
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources")
                .appendQueryParameter("path", file.getPath())
                .appendQueryParameter("preview_size", "S")
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
                String thumbnailUrl = jsonObject.getString("preview");
                // redirect to url
                return downloadFile(thumbnailUrl, file.getId() + ".png");
            } else {
                throw new RequestFailException(response.message(), response.code());
            }
        } catch (JSONException e) {
            throw new RequestFailException(e.getMessage());
        } catch (IOException e) {
            throw new RequestFailException(e.getMessage());
        }
    }

    @Override
    public void getFolderInfoAsync(@NonNull String folderPath, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
            return;
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources")
                .appendQueryParameter("path", folderPath)
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
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
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // redirect to url
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    String metadataUrl = jsonObject.getString("href");
                    buildFolderAsync(metadataUrl, callback);
                } catch (JSONException e) {
                    callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                }
            }
        });
    }

    /**
     * Async build folder by requesting url
     * @param url link to folder's metadata
     * @param callback to return the request result back
     */
    private void buildFolderAsync(String url, final ApiCallback callback) {
        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .get()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // return folder object
                try {
                    CFolder folder = buildFolder(new JSONObject(response.body().string()));
                    callback.onReceiveItems(request, Arrays.asList(folder));
                } catch (JSONException e) {
                    callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                }
            }
        });
    }

    @Override
    public void exploreFolderAsync(@NonNull final CFolder folder, int offset, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
            return;
        }

        final ArrayList list = new ArrayList<>();
        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources")
                .appendQueryParameter("path", folder.getPath())
                .appendQueryParameter("fields", "type,name,created,modified,path,size,_embedded")
                .appendQueryParameter("limit", "100")
                .appendQueryParameter("offset", String.valueOf(offset))
                .build()
                .toString();

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .get()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                try {
                    JSONObject jsonObject = new JSONObject(response.body().string())
                            .getJSONObject("_embedded");
                    int total = jsonObject.getInt("total");
                    // return null if no item found
                    if (total == 0) {
                        callback.onReceiveItems(request, null);
                        // need to return in current thread
                        return;
                    }

                    JSONArray entries = jsonObject.getJSONArray("items");
                    list.addAll(createItemList(entries));
                    // check bundle for existing list
                    List<Parcelable> previousList = callback.bundle.getParcelableArrayList("previous_list");
                    if (previousList != null && previousList.size() > 0)
                        list.addAll(previousList);

                    // suspect search result over 100 items
                    if (total > 100 && total > list.size()) {
                        // update previous list
                        callback.bundle.putParcelableArrayList("previous_list", list);
                        exploreFolderAsync(folder, list.size(), callback);
                    } else {
                        callback.onReceiveItems(request, list);
                    }
                } catch (JSONException e) {
                    // send exception to callback
                    callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                }
            }
        });
    }

    @Override
    public void createFolderAsync(@NonNull String name, @Nullable CFolder parent, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
            return;
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources")
                .appendQueryParameter("path",
                        (parent != null ? parent.getPath() : getRoot().getPath()) + "/" + name)
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

        // need to create blank body to use put method
        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return null;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {

            }
        };

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .put(body)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                // send exception to callback
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // redirect to url
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    String metadataUrl = jsonObject.getString("href");
                    buildFolderAsync(metadataUrl, callback);
                } catch (JSONException e) {
                    callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                }
            }
        });
    }

    @Override
    public void renameFolderAsync(@NonNull CFolder folder, String name, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
            return;
        }

        // exit if root or same name
        if (folder.isRoot() || folder.getName().equals(name)) {
            callback.onReceiveItems(null, Arrays.asList(folder));
            return;
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources/move")
                .appendQueryParameter("from", folder.getPath())
                .appendQueryParameter("path", renameLastPathSegment(folder.getPath(), name))
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

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

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .post(body)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // redirect to url
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    String metadataUrl = jsonObject.getString("href");
                    buildFolderAsync(metadataUrl, callback);
                } catch (JSONException e) {
                    callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                }
            }
        });
    }

    @Override
    public void moveFolderAsync(@NonNull CFolder folder, @Nullable CFolder parent, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
            return;
        }

        // exit if root or same name
        if (folder.isRoot() || parent != null && folder.getId().equals(parent.getId())) {
            callback.onReceiveItems(null, Arrays.asList(folder));
            return;
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources/move")
                .appendQueryParameter("from", folder.getPath())
                .appendQueryParameter("path", (parent != null ? parent.getPath() :
                        getRoot().getPath()) + "/" + folder.getName())
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

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

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .post(body)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // redirect to url
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    String metadataUrl = jsonObject.getString("href");
                    buildFolderAsync(metadataUrl, callback);
                } catch (JSONException e) {
                    callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                }
            }
        });
    }

    @Override
    public void deleteFolderAsync(@NonNull CFolder folder, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
            return;
        }

        final String folderId = folder.getId();
        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources")
                .appendQueryParameter("path", folder.getPath())
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .delete()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // log successful response
                Log.d(TAG, "Folder with the id: " + folderId + " deleted");
                callback.onReceiveItems(request, null);
            }
        });
    }

    @Override
    public void getFileInfoAsync(@NonNull String filePath, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
            return;
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources")
                .appendQueryParameter("path", filePath)
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .get()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // return file object
                try {
                    CFile file = buildFile(new JSONObject(response.body().string()));
                    callback.onReceiveItems(request, Arrays.asList(file));
                } catch (JSONException e) {
                    callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                }
            }
        });
    }

    @Override
    public void uploadFileAsync(@NonNull final File file, @Nullable CFolder parent, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
            return;
        }

        Uri uri = Uri.parse(API_BASE_URL);
        final String path = parent.getPath() + "/" + file.getName();
        final String url = uri.buildUpon()
                .appendEncodedPath("disk/resources/upload")
                .appendQueryParameter("path", path)
                .build()
                .toString();

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .get()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // redirect to url
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    String uploadUrl = jsonObject.getString("href");
                    uploadFileAsync(uploadUrl, file, path, callback);
                } catch (JSONException e) {
                    callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                }
            }
        });
    }

    /**
     * Async upload file from redirect request
     *
     * @param url for redirect
     * @param file to be uploaded
     * @param filePath of the uploaded file relative to the cloud storage's root
     * @param callback to return the request result back
     */
    private void uploadFileAsync(@NonNull String url, @NonNull File file, final String filePath, final ApiCallback callback) {
        // create multipart body
        MediaType fileType = MediaType.parse(FilesUtils.getFileType(file));
        RequestBody multipart = new MultipartBuilder()
                .type(MultipartBuilder.FORM)
                //.addFormDataPart("attributes", params.toString())
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(fileType, file))
                .build();

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .put(multipart)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // response body is empty!

                // return file object
                getFileInfoAsync(filePath, callback);
            }
        });
    }

    @Override
    public void updateFileAsync(@NonNull CFile file, final File content, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
            return;
        }

        Uri uri = Uri.parse(API_BASE_URL);
        final String path = file.getPath();
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources/upload")
                .appendQueryParameter("path", path)
                .appendQueryParameter("overwrite", "true")
                .build()
                .toString();

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .get()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // redirect to url
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    String uploadUrl = jsonObject.getString("href");
                    uploadFileAsync(uploadUrl, content, path, callback);
                } catch (JSONException e) {
                    callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                }
            }
        });
    }

    @Override
    public void renameFileAsync(@NonNull CFile file, String name, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
            return;
        }

        // exit if same filename
        if (file.getName().equals(name)) {
            callback.onUiReceiveItems(null, Arrays.asList(file));
            return;
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources/move")
                .appendQueryParameter("from", file.getPath())
                .appendQueryParameter("path", renameLastPathSegment(file.getPath(), name))
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

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

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .post(body)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // redirect to url
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    String metadataUrl = jsonObject.getString("href");
                    buildFileAsync(metadataUrl, callback);
                } catch (JSONException e) {
                    callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                }
            }
        });
    }

    /**
     * Async build file by requesting url
     * @param url link to file's metadata
     * @param callback to return the request result back
     */
    private void buildFileAsync(String url, final ApiCallback callback) {
        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .get()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // return file object
                try {
                    CFile file = buildFile(new JSONObject(response.body().string()));
                    callback.onReceiveItems(request, Arrays.asList(file));
                } catch (JSONException e) {
                    callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                }
            }
        });
    }

    @Override
    public void moveFileAsync(@NonNull CFile file, @Nullable CFolder folder, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
            return;
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources/move")
                .appendQueryParameter("from", file.getPath())
                .appendQueryParameter("path", (folder != null ? folder.getPath() :
                        getRoot().getPath()) + "/" + file.getName())
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

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

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .post(body)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // redirect to url
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    String metadataUrl = jsonObject.getString("href");
                    buildFileAsync(metadataUrl, callback);
                } catch (JSONException e) {
                    callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
                }
            }
        });
    }

    @Override
    public void downloadFileAsync(@NonNull final CFile file, @Nullable final String filename, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
            return;
        }

        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources/download")
                .appendQueryParameter("path", file.getPath())
                .build()
                .toString();

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .get()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // redirect to url
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    String downloadUrl = jsonObject.getString("href");
                    downloadFileAsync(downloadUrl,
                            TextUtils.isEmpty(filename) ? file.getName() : filename, callback);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Async download file from redirect request
     *
     * @param url for redirect
     * @param filename for downloaded file
     * @param callback to return the request result back
     */
    private void downloadFileAsync(@NonNull String url, @NonNull final String filename, final ApiCallback callback) {
        final Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // save file to storage
                if (FilesUtils.getInternalAvailableBytes() < response.body().contentLength()) {
                    // insufficient storage space throw exception
                    callback.onRequestFailure(request, new RequestFailException("Insufficient storage"));
                } else {
                    File file = new File(CloudProvider.CACHE_DIR, filename);
                    FilesUtils.copyFile(response.body().byteStream(), new FileOutputStream(file));
                    callback.onReceiveItems(request, Arrays.asList(file));
                }
            }
        });
    }

    @Override
    public void deleteFileAsync(@NonNull CFile file, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
            return;
        }

        final String fileId = file.getId();
        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("disk/resources")
                .appendQueryParameter("path", file.getPath())
                .appendQueryParameter("fields", "type,id,name,created,modified,path,size")
                .build()
                .toString();

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .delete()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // log successful response
                Log.d(TAG, "File with the id: " + fileId + " deleted");
                callback.onReceiveItems(request, null);
            }
        });
    }

    @Override
    public void searchFileAsync(@NonNull final String keyword, CFolder folder, final ApiCallback callback) {
        exploreFolderAsync(folder, 0, new ApiCallback() {
            @Override
            public void onUiRequestFailure(Request request, RequestFailException exception) {
                // echo exception to callback
                callback.onUiRequestFailure(request, exception);
            }

            @Override
            public void onUiReceiveItems(Request request, List items) {
                List<CFile> resultList = new ArrayList<>();
                // search through every item's name for keyword match in case insensitive mode
                for (Object item : items) {
                    if (CFile.class.isAssignableFrom(item.getClass())) {
                        if (((CFile) item).getName().toLowerCase().contains(keyword.toLowerCase()))
                            resultList.add((CFile) item);
                    }
                }
                callback.onUiReceiveItems(request, resultList);
            }
        });
    }

    @Override
    public void searchFolderAsync(@NonNull final String keyword, CFolder folder, final ApiCallback callback) {
        exploreFolderAsync(folder, 0, new ApiCallback() {
            @Override
            public void onUiRequestFailure(Request request, RequestFailException exception) {
                // echo exception to callback
                callback.onUiRequestFailure(request, exception);
            }

            @Override
            public void onUiReceiveItems(Request request, List items) {
                List<CFolder> resultList = new ArrayList<>();
                // search through every item's name for keyword match in case insensitive mode
                for (Object item : items) {
                    if (CFolder.class.isAssignableFrom(item.getClass())) {
                        if (((CFolder) item).getName().toLowerCase().contains(keyword.toLowerCase()))
                            resultList.add((CFolder) item);
                    }
                }
                callback.onUiReceiveItems(request, resultList);
            }
        });
    }

    @Override
    public void searchAsync(@NonNull final String keyword, CFolder folder, final ApiCallback callback) {
        exploreFolderAsync(folder, 0, new ApiCallback() {
            @Override
            public void onUiRequestFailure(Request request, RequestFailException exception) {
                // echo exception to callback
                callback.onUiRequestFailure(request, exception);
            }

            @Override
            public void onUiReceiveItems(Request request, List items) {
                List<Object> resultList = new ArrayList<>();
                // search through every item's name for keyword match in case insensitive mode
                for (Object item : items) {
                    if (CFolder.class.isAssignableFrom(item.getClass())) {
                        if (((CFolder) item).getName().toLowerCase().contains(keyword.toLowerCase()))
                            resultList.add(item);
                    } else if (CFile.class.isAssignableFrom(item.getClass())) {
                        if (((CFile) item).getName().toLowerCase().contains(keyword.toLowerCase()))
                            resultList.add(item);
                    }
                }
                callback.onUiReceiveItems(request, resultList);
            }
        });
    }

    @Override
    public void getThumbnailAsync(@NonNull final CFile file, final ApiCallback callback) {
        if (TextUtils.isEmpty(mAccessToken)) {
            callback.onRequestFailure(null, new RequestFailException("Access token not available"));
            return;
        }

        /*
        TODO: Business logic to get thumbnail
        Get thumbnail is not working at the moment because the preview link is tie to
        the user login session's cookie i.e. Session_id. It is tedious to just implement
        cookie manager just for Yandex. Hope Yandex can update their API to use the
        access token to get the thumbnail.
         */
        Uri uri = Uri.parse(API_BASE_URL);
        String url = uri.buildUpon()
                .appendEncodedPath("files/" + file.getId() + "/thumbnail.png")
                .appendQueryParameter("min_height", "100")
                .build()
                .toString();

        final Request request = new Request.Builder()
                .url(url)
                .header("Authorization", mAccessToken)
                .get()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                callback.onRequestFailure(request, new RequestFailException(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                // send failed response to callback
                if (!response.isSuccessful()) {
                    callback.onRequestFailure(request,
                            new RequestFailException(response.message(), response.code()));
                    return;
                }

                // redirect to url
                /*
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    String downloadUrl = jsonObject.getString("href");
                    downloadFileAsync(downloadUrl,
                            TextUtils.isEmpty(filename) ? file.getName() : filename, callback);
                } catch (JSONException e) {
                    e.printStackTrace();
                }*/
            }
        });
    }

    /**
     * Create files or folders from the JSONArray search result
     *
     * @param jsonArray that contain files and folders information
     * @return list that contains CFile and CFolder
     * @throws JSONException
     */
    private List createItemList(JSONArray jsonArray) throws JSONException {
        if (jsonArray == null || jsonArray.length() == 0) return null;

        List<Object> list = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            String type = jsonObject.getString("type");
            switch (type.toLowerCase()) {
                case "file":
                    list.add(buildFile(jsonObject));
                    break;
                case "dir":
                    list.add(buildFolder(jsonObject));
                    break;
                default:
                    Log.e(TAG, "Unknown type found");
                    break;
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
