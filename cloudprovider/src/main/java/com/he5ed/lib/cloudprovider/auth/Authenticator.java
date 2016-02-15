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

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.he5ed.lib.cloudprovider.CloudProvider;

import static android.accounts.AccountManager.KEY_BOOLEAN_RESULT;

/**
 * @hide
 */
public class Authenticator extends AbstractAccountAuthenticator {

    // bundle keys for user data
    public static final String KEY_USERNAME = "username";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_AVATAR_URL = "avatarUrl";
    public static final String KEY_CLOUD_API = "cloudApi";
    public static final String KEY_ACCESS_TOKEN = "accessToken";
    public static final String KEY_REFRESH_TOKEN = "refreshToken";
    public static final String KEY_EXPIRY = "expiry";

    private Context mContext;

    public Authenticator(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        return null;
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options)
            throws NetworkErrorException {
        // ignore if other account type
        if (!accountType.equals(CloudProvider.ACCOUNT_TYPE))
            return null;

        // start the OAuth2Activity to get the access token
        final Intent intent = new Intent(mContext, OAuth2Activity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        intent.putExtra(OAuth2Activity.EXTRA_ACCOUNT_TYPE, accountType);
        intent.putExtra(OAuth2Activity.EXTRA_AUTH_TYPE, authTokenType);
        intent.putExtra(OAuth2Activity.EXTRA_CLOUD_API, options.getString(CloudProvider.KEY_API_CLASS_NAME));

        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options)
            throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options)
            throws NetworkErrorException {

        // peek into AccountManager for AuthToken
        final AccountManager am = AccountManager.get(mContext);
        String authToken = am.peekAuthToken(account, authTokenType);

        // return the AuthToken
        if (!TextUtils.isEmpty(authToken)) {
            final Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
            result.putString(AccountManager.KEY_AUTHTOKEN, authToken);
            return result;
        }

        // fail to retrieve AuthToken initiate OAuth2Activity
        final Intent intent = new Intent(mContext, OAuth2Activity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        intent.putExtra(OAuth2Activity.EXTRA_ACCOUNT_NAME, account.name);
        intent.putExtra(OAuth2Activity.EXTRA_ACCOUNT_TYPE, account.type);
        intent.putExtra(OAuth2Activity.EXTRA_AUTH_TYPE, authTokenType);
        intent.putExtra(OAuth2Activity.EXTRA_CLOUD_API, options.getString(CloudProvider.KEY_API_CLASS_NAME));

        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        return authTokenType;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options)
            throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features)
            throws NetworkErrorException {
        final Bundle result = new Bundle();
        result.putBoolean(KEY_BOOLEAN_RESULT, false);
        return result;
    }

}
