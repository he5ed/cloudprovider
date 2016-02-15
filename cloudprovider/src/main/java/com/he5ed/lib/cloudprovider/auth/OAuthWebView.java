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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.he5ed.lib.cloudprovider.exceptions.InvalidUrlException;
import com.he5ed.lib.cloudprovider.utils.AuthHelper;

import java.security.SecureRandom;

/**
 * Custom WebView that facilitate the OAuth 2.0 process
 *
 * @hide
 */
public class OAuthWebView extends WebView {

    private String mCloudType;
    private String mStateString;
    private OnAuthEndListener mListener;
    private boolean mLayoutChangedOnce;

    private WebViewClient mWebViewClient = new WebViewClient() {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {

            // check for redirect url
            Uri referenceUri = AuthHelper.getRedirectUri(mCloudType);
            Uri uri = Uri.parse(url);

            String error = uri.getQueryParameter("error");
            String code = uri.getQueryParameter("code");

            if (!TextUtils.isEmpty(error)) {
                // end if error occur
                mListener.onAuthEnded(null, error);
            }

            if (!TextUtils.isEmpty(code)) {
                // code retrieved check if valid
                try {
                    if (validateRedirectUrl(referenceUri, uri, mStateString)) {
                        // pass the result back to listener
                        if (mListener != null)
                            mListener.onAuthEnded(code, null);
                    }
                } catch (InvalidUrlException e) {
                    e.printStackTrace();
                    mListener.onAuthEnded(null, e.getMessage());
                }
            }

        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            super.onReceivedSslError(view, handler, error);
        }

    };

    public OAuthWebView(Context context) {
        this(context, null, 0, 0);
    }

    public OAuthWebView(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }

    public OAuthWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public OAuthWebView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        // call super constructor
        super(context, attrs, defStyleAttr);

        setWebViewClient(mWebViewClient);
        getSettings().setJavaScriptEnabled(true);

        // delete stored cookies to enable multiple accounts login for same service
        CookieSyncManager.createInstance(context);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookie();
    }

    /*
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (!mLayoutChangedOnce)
        {
            super.onLayout(changed, l, t, r, b);
            mLayoutChangedOnce = true;
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(true, direction, previouslyFocusedRect);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }*/

    /**
     * Start the authentication process
     *
     * @param cloudType type of cloud account
     */
    public void authenticate(String cloudType) {
        mCloudType = cloudType;
        // using secure random to generate 16 byte value
        SecureRandom sr = new SecureRandom();
        byte[] random = new byte[16];
        sr.nextBytes(random);
        mStateString = Base64.encodeToString(random, Base64.NO_WRAP); // use no wrap to prevent new line

        String authUrl = AuthHelper.buildAuthUri(mCloudType, mStateString).toString();
        loadUrl(authUrl);
    }

    /**
     * Validate the returned url whether it matches the redirect url and the state string
     *
     * @param uri uri return by the server
     * @param redirectUri reference redirect uri
     * @param state secure random state string
     * @return true if validation is successful, false otherwise
     * @throws InvalidUrlException
     */
    private boolean validateRedirectUrl(Uri uri, Uri redirectUri, String state) throws InvalidUrlException {
        // check for redirect url
        if (redirectUri.getScheme() == null || !redirectUri.getScheme().equals(uri.getScheme())
                || !redirectUri.getAuthority().equals(uri.getAuthority())) {
            throw new InvalidUrlException("Redirect Url mismatch!");
        }
        // check for state string in the return url
        String returnState = redirectUri.getQueryParameter("state");
        if (returnState == null || !returnState.equals(state)) {
            throw new InvalidUrlException("State string mismatch!");
        }

        return true;
    }

    /**
     * Listen to the authentication end event
     */
    public interface OnAuthEndListener {
        void onAuthEnded(String authCode, String error);
    }

    /**
     * Convenient method to set the OnAuthEndListener
     *
     * @param listener listener for authorization process
     */
    public void setOnAuthEndListener(OnAuthEndListener listener) {
        mListener = listener;
    }

}
