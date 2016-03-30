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

package com.he5ed.lib.cloudprovider.auth;


import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.he5ed.lib.cloudprovider.CloudProvider;
import com.he5ed.lib.cloudprovider.R;
import com.he5ed.lib.cloudprovider.models.User;
import com.he5ed.lib.cloudprovider.utils.AuthHelper;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

/**
 * @hide
 */
public class OAuth2Fragment extends Fragment implements OAuthWebView.OnAuthEndListener {

    public static final String EXTRA_CONFIG = "config";
    public static final MediaType FORM = MediaType.parse("application/x-www-form-urlencoded; charset=utf-8");

    private OnAuthEventsListener mListener;
    private OkHttpClient mHttpClient;
    private FrameLayout mRootView;
    private View mLoginView;
    private Map<String, String> mTokenInfo;
    private String mCloudApi;

    public OAuth2Fragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain web view state during rotation
        setRetainInstance(true);
        // get extra from intent
        Intent intent = getActivity().getIntent();
        if (intent != null) {
            mCloudApi = intent.getStringExtra(OAuth2Activity.EXTRA_CLOUD_API);
        } else {
            // end activity
            getActivity().finish();
        }
        mHttpClient = new OkHttpClient();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.cp_fragment_oauth, container, false);
        mRootView = (FrameLayout) rootView;
        boolean nativeUI = getActivity().getIntent()
                .getBooleanExtra(OAuth2Activity.EXTRA_NATIVE_UI, true);
        // choose to add WebView or native view
        if (nativeUI && mLoginView == null) {
            mLoginView = inflater.inflate(R.layout.cp_login_register, mRootView, false);
            TextInputLayout usernameWrapper = (TextInputLayout) mLoginView.findViewById(R.id.username_text_wrapper);
            usernameWrapper.setHint(getString(R.string.text_username));
            TextInputLayout passwordWrapper = (TextInputLayout) mLoginView.findViewById(R.id.password_text_wrapper);
            passwordWrapper.setHint(getString(R.string.text_password));

            Button loginButton = (Button) mLoginView.findViewById(R.id.login_button);
            loginButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // verify user inputs
                    if (verifyNativeUiLogin()) {
                        nativeUiLogin();
                    } else {
                        // show error message
                    }
                }
            });
        } else {
            if (mLoginView == null) {
                OAuthWebView webView = new OAuthWebView(getActivity());
                webView.setOnAuthEndListener(this);
                webView.authenticate(mCloudApi);
                webView.setFocusable(true);
                webView.setFocusableInTouchMode(true);
                webView.requestFocus(View.FOCUS_DOWN);
                mLoginView = webView;
            }
        }

        mRootView.addView(mLoginView, FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);

        return rootView;
    }

    @Override
    public void onDetach () {
        super.onDetach();
        // remove old web view, so that it can be added later
        mRootView.removeAllViews();
    }

    @Override
    public void onDestroy () {
        super.onDestroy();

    }

    @Override
    public void onAuthEnded(String authCode, String error) {
        if (authCode != null) {
            // continue to get access token
            getAccessToken(AuthHelper.getAccessTokenBody(mCloudApi, authCode));
        } else {
            // show error message
            showErrorDialog();
        }
    }

    /**
     * Verify that the user key in the correct information before submit login form
     *
     * @return true if successful
     */
    private boolean verifyNativeUiLogin() {
        // exit if view not exist or view is of OAuthWebView
        if (mLoginView == null || mLoginView instanceof OAuthWebView) return false;

        EditText username = (EditText) mLoginView.findViewById(R.id.username_edit_text);
        EditText password = (EditText) mLoginView.findViewById(R.id.password_edit_text);
        // exit if view items not found
        if (username == null || password == null) return false;
        // exit if any of the value is empty
        if (TextUtils.isEmpty(username.getText().toString()) ||
                TextUtils.isEmpty(password.getText().toString())) return false;

        return true;
    }

    /**
     * Login user via native UI
     */
    private void nativeUiLogin() {
        String username = ((EditText) mLoginView.findViewById(R.id.username_edit_text)).getText().toString();
        String password = ((EditText) mLoginView.findViewById(R.id.password_edit_text)).getText().toString();

        Request request = AuthHelper.getUserLoginRequest(mCloudApi, new String[]{username, password});
        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                showErrorDialog();
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        mTokenInfo = AuthHelper.extractAccessToken(mCloudApi, jsonObject);
                        if (mTokenInfo != null) {
                            getUserInfo(mTokenInfo.get(Authenticator.KEY_ACCESS_TOKEN));
                        } else {
                            // register event to listener
                            if (mListener != null)
                                mListener.onAuthError(getString(R.string.auth_error_access_token_fail));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        // register event to listener
                        if (mListener != null)
                            mListener.onAuthError(getString(R.string.auth_error_access_token_fail));
                    }

                } else {
                    // register event to listener
                    if (mListener != null)
                        mListener.onAuthError(getString(R.string.auth_error_access_token_fail));
                }
            }
        });

    }

    /**
     * Show authentication error message
     */
    private void showErrorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.dialog_message_auth_error);
        // Create the AlertDialog
        Dialog dialog = builder.create();
        dialog.show();
    }



    /**
     * Get API access token by sending POST request with params build from AuthHelper
     *
     * @param body parameter to be passed with the request
     */
    private void getAccessToken(RequestBody body) {
        String url;
        try {
            url = AuthHelper.getAccessTokenUri(mCloudApi).toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            // register event to listener
            if (mListener != null)
                mListener.onAuthError(getString(R.string.auth_error_malformed_url));
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                // register event to listener
                if (mListener != null)
                    mListener.onAuthError(getString(R.string.auth_error_access_token_fail));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        mTokenInfo = AuthHelper.extractAccessToken(mCloudApi, jsonObject);
                        if (mTokenInfo != null) {
                            getUserInfo(mTokenInfo.get(Authenticator.KEY_ACCESS_TOKEN));
                        } else {
                            // register event to listener
                            if (mListener != null)
                                mListener.onAuthError(getString(R.string.auth_error_access_token_fail));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        // register event to listener
                        if (mListener != null)
                            mListener.onAuthError(getString(R.string.auth_error_access_token_fail));
                    }

                } else {
                    // register event to listener
                    if (mListener != null)
                        mListener.onAuthError(getString(R.string.auth_error_access_token_fail));
                }
            }
        });

    }

    private void getUserInfo(final String accessToken) {

        Request request = AuthHelper.getUserInfoRequest(mCloudApi, accessToken);

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                // register event to listener
                if (mListener != null)
                    mListener.onAuthError(getString(R.string.auth_error_user_info_fail));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());

                        addAccount(AuthHelper.extractUser(mCloudApi, jsonObject));
                    } catch (JSONException e) {
                        e.printStackTrace();
                        // register event to listener
                        if (mListener != null)
                            mListener.onAuthError(getString(R.string.auth_error_user_info_fail));
                    }
                } else {
                    // register event to listener
                    if (mListener != null)
                        mListener.onAuthError(getString(R.string.auth_error_user_info_fail));
                }
            }
        });
    }

    /**
     * Create a new user account or update the current user account
     *
     * @param user user information returned from server
     */
    private void addAccount(User user) {
        boolean accountExist = false;
        AccountManager am = AccountManager.get(getActivity());
        // check if account already exist in AccountManager
        Account[] accounts = am.getAccountsByType(CloudProvider.ACCOUNT_TYPE);
        for (Account account : accounts) {
            if (account.name.equals(user.id)) {
                accountExist = true;
                break;
            }
        }

        Account account = new Account(user.id, CloudProvider.ACCOUNT_TYPE);
        Bundle userData = new Bundle(); // must be string value
        String accessToken = mTokenInfo.get(Authenticator.KEY_ACCESS_TOKEN);
        String refreshToken = mTokenInfo.get(Authenticator.KEY_REFRESH_TOKEN);
        String expiryDuration = mTokenInfo.get(Authenticator.KEY_EXPIRY);
        if (accountExist) {
            // update current account access token
            am.setAuthToken(account, CloudProvider.AUTH_TYPE, accessToken);
            if (refreshToken != null)
                am.setUserData(account,Authenticator.KEY_REFRESH_TOKEN, refreshToken);
            if (expiryDuration != null)
                am.setUserData(account,Authenticator.KEY_EXPIRY, expiryDuration);
        } else {
            // add new account into AccountManager
            if (refreshToken != null)
                userData.putString(Authenticator.KEY_REFRESH_TOKEN, refreshToken);
            if (expiryDuration != null)
                userData.putString(Authenticator.KEY_EXPIRY, expiryDuration);
            userData.putString(Authenticator.KEY_CLOUD_API, AuthHelper.getCloudApi(mCloudApi));
            userData.putString(Authenticator.KEY_USERNAME, user.name);
            userData.putString(Authenticator.KEY_EMAIL, user.email);
            userData.putString(Authenticator.KEY_AVATAR_URL, user.avatarUrl);

            am.addAccountExplicitly(account, null, userData);
            am.setAuthToken(account, CloudProvider.AUTH_TYPE, accessToken);
        }

        // send result back to AccountManager
        Bundle result = new Bundle();
        result.putString(AccountManager.KEY_ACCOUNT_NAME, user.id);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, CloudProvider.ACCOUNT_TYPE);
        ((AccountAuthenticatorActivity) getActivity()).setAccountAuthenticatorResult(result);

        getActivity().finish();
    }

    /**
     * Listen to the authentication events
     */
    public interface OnAuthEventsListener {

        void onAccessTokenReceived(JSONObject jsonToken);

        void onUserInfoReceived(JSONObject jsonToken);

        void onAuthError(String error);
    }

    /**
     * Convenient method to set the OnAuthEventListener
     *
     * @param listener to listen for auth event
     */
    public void setOnAuthEventListener(OnAuthEventsListener listener) {
        mListener = listener;
    }
}
