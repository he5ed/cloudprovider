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

package com.he5ed.lib.cloudprovider;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.he5ed.lib.cloudprovider.apis.BaseApi;
import com.he5ed.lib.cloudprovider.apis.BoxApi;
import com.he5ed.lib.cloudprovider.apis.CloudDriveApi;
import com.he5ed.lib.cloudprovider.apis.DropboxApi;
import com.he5ed.lib.cloudprovider.apis.OneDriveApi;
import com.he5ed.lib.cloudprovider.auth.Authenticator;
import com.he5ed.lib.cloudprovider.models.CloudAccount;
import com.he5ed.lib.cloudprovider.models.User;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The main class that controls the entire operations of this library. This is a
 * singleton class, only a single instance will be present across the entire lifecycle
 * of the app that used this library. This class is also thread save thus can be called
 * across different thread such as a Service.
 * <p>
 * To use this class for the first time its {@link #ACCOUNT_TYPE} must be override to
 * the host app's package name.
 * <p>
 * To interact with the cloud API backend service, build the respective cloud API class
 * instance via this class's instance method {@link #buildApi(CloudAccount)}.
 * <p>
 * Developer should only call the account related operation via this class and should
 * avoid direct interaction with the Android's {@link AccountManager}. All the account
 * related operations are available via this class.
 *
 */
public class CloudProvider {

    /**
     * Account type name must override with host package name
     * so that all account create can be easily filter out from the system's AccountManager
     */
    public static String ACCOUNT_TYPE = "com.he5ed.lib.cloudprovider";

    /**
     * Directory to temporary store the downloaded files and thumbnails
     * ignore for default implementation as below:
     * data/data/app package name/cache/cloudprovider
     */
    public static File CACHE_DIR;

    /**
     * At the moment only full access authentication type available for all the cloud APIs
     */
    public static final String AUTH_TYPE = "full_access";

    /**
     * Bundle key to pass the cloud API class's canonical name to facilitate account management.
     * The cloud API class name is very important for the CloudProvider to contract an instance
     * object so that the actual interaction with the cloud service backend can be carried out.
     */
    public static final String KEY_API_CLASS_NAME = "apiClassName";

    /**
     * @hide
     */
    public static final String TAG = "CloudProvider";

    private static CloudProvider instance;

    private Context mContext;
    private AccountManager mAccountManager;
    private OnAccountChangeListener mListener;
    private List<Class<?>> mApiList;

    /**
     * Get the singleton instance of CloudProvider
     *
     * @param context of any activity or service that need to use this class instance
     * @return the singleton instance of this class
     */
    public static CloudProvider getInstance(Context context) {
        // create singleton instance
        if(instance == null) {
            instance = new CloudProvider(context);
        }
        return instance;
    }

    private CloudProvider(Context context) {
        mContext = context;
        mAccountManager = AccountManager.get(mContext);

        // setup cache director
        if (CACHE_DIR == null) {
            File dir = new File(mContext.getCacheDir(), "cloudprovider");
            if (!dir.exists()) dir.mkdir();
            CACHE_DIR = dir;
        }

        // add API implementation classes to the provider
        mApiList = new ArrayList<>();
        addDefaultApis();
    }

    /**
     * Enable type of default APIs base on the client id and client secret non-null assignment
     */
    private void addDefaultApis() {
        // Cloud account config
        if (BoxApi.CLIENT_ID != null && BoxApi.CLIENT_SECRET != null) {
            BoxApi.ENABLE_API = true;
            addApi(BoxApi.class);
        }

        if (DropboxApi.CLIENT_ID != null && DropboxApi.CLIENT_SECRET != null) {
            DropboxApi.ENABLE_API = true;
            addApi(DropboxApi.class);
        }

        if (OneDriveApi.CLIENT_ID != null && OneDriveApi.CLIENT_SECRET != null) {
            OneDriveApi.ENABLE_API = true;
            addApi(OneDriveApi.class);
        }

        if (CloudDriveApi.CLIENT_ID != null && CloudDriveApi.CLIENT_SECRET != null) {
            CloudDriveApi.ENABLE_API = true;
            addApi(CloudDriveApi.class);
        }

    }

    /**
     * Add cloud APIs so that it can interact with the cloud services's backend server.
     * Developer can build his/her own cloud API and add it to this class. All external cloud
     * APIs must extend {@link BaseApi} and must be validated by the developer before adding in.
     *
     * @param clazz for the cloud API that extend {@link BaseApi} class
     */
    public void addApi(Class<?> clazz) throws IllegalArgumentException{
        if (BaseApi.class.isAssignableFrom(clazz)) {
            mApiList.add(clazz);
        } else {
            throw new IllegalArgumentException("Cloud API class did not extend BaseApi class");
        }
    }

    /**
     * Get all available cloud APIs class
     *
     * @return the classes of the available APIs as list
     */
    public List<Class<?>> getApiList() {
        return mApiList;
    }

    /**
     * Add account via {@link AccountManager}
     *
     * @param cloudApiName the canonical name of the cloud API class
     * @param activity The Activity context to use for launching a new authenticator-defined
     *                 sub-Activity to prompt the user to create an account; used only to call
     *                 {@link Activity#startActivity(Intent)}; if null, the prompt will not be
     *                 launched directly, but the necessary Intent will be returned to the
     *                 caller instead
     */
    public void addAccount(String cloudApiName, Activity activity) {
        Bundle options = new Bundle();
        options.putString(KEY_API_CLASS_NAME, cloudApiName);

        mAccountManager.addAccount(ACCOUNT_TYPE, AUTH_TYPE, null, options, activity,
                new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> future) {
                if (future.isDone()) {
                    try {
                        String accountId = (String) future.getResult().get(AccountManager.KEY_ACCOUNT_NAME);
                        mListener.onAccountAdded(getAccountById(accountId));
                    } catch (OperationCanceledException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (AuthenticatorException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, null);
    }

    /**
     * Get all accounts create by CloudProvider
     * Accounts are filtered by type base on the {@link #ACCOUNT_TYPE} string value.
     *
     * @return {@link CloudAccount}[] array of accounts
     */
    public CloudAccount[] getCloudAccounts() {
        //Account[] accounts = mAccountManager.getAccounts();
        Account[] accounts = mAccountManager.getAccountsByType(ACCOUNT_TYPE);
        CloudAccount[] cloudAccounts = new CloudAccount[accounts.length];
        for (int i = 0; i < accounts.length; i++) {
            cloudAccounts[i] = new CloudAccount();
            cloudAccounts[i].setAccount(accounts[i]);
            cloudAccounts[i].id = accounts[i].name;
            cloudAccounts[i].api = mAccountManager.getUserData(accounts[i], Authenticator.KEY_CLOUD_API);
            try {
                cloudAccounts[i].type = getApiName(Class.forName(cloudAccounts[i].api));
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

            User user = new User();
            user.name = mAccountManager.getUserData(accounts[i], Authenticator.KEY_USERNAME);
            user.email = mAccountManager.getUserData(accounts[i], Authenticator.KEY_EMAIL);
            user.avatarUrl = mAccountManager.getUserData(accounts[i], Authenticator.KEY_AVATAR_URL);
            cloudAccounts[i].setUser(user);
        }

        return cloudAccounts;
    }

    /**
     * Get the account specified by the account's unique id
     *
     * @param id for the targeted account
     * @return {@link CloudAccount} if successful else null
     */
    public CloudAccount getAccountById(String id) {
        Account targetAccount = null;
        Account[] accounts = mAccountManager.getAccountsByType(ACCOUNT_TYPE);

        for (Account account : accounts) {
            if (account.name.equals(id)) {
                targetAccount = account;
                break;
            }
        }
        // no account found return null
        if (targetAccount == null)
            return null;

        CloudAccount cloudAccount = new CloudAccount();
        cloudAccount.setAccount(targetAccount);
        cloudAccount.id = targetAccount.name;
        cloudAccount.api = mAccountManager.getUserData(targetAccount, Authenticator.KEY_CLOUD_API);
        try {
            cloudAccount.type = getApiName(Class.forName(cloudAccount.api));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        User user = new User();
        user.name = mAccountManager.getUserData(targetAccount, Authenticator.KEY_USERNAME);
        user.email = mAccountManager.getUserData(targetAccount, Authenticator.KEY_EMAIL);
        user.avatarUrl = mAccountManager.getUserData(targetAccount, Authenticator.KEY_AVATAR_URL);
        cloudAccount.setUser(user);

        return cloudAccount;
    }

    /**
     * Get user data via {@link AccountManager}
     *
     * @param account that host the user data
     * @param key string to retrieve the data
     * @return user data in String (all user data is stored as String)
     */
    public String getUserData(Account account, String key) {
        return mAccountManager.getUserData(account, key);
    }

    /**
     * Update the account auth token and user data
     *
     * @param account to be updated
     * @param data to store in the account (must be String object)
     */
    public void updateAccount(Account account, Map<String, String> data) {
        String accessToken = data.get(Authenticator.KEY_ACCESS_TOKEN);
        if (!TextUtils.isEmpty(accessToken))
            mAccountManager.setAuthToken(account, AUTH_TYPE, accessToken);

        String refreshToken = data.get(Authenticator.KEY_REFRESH_TOKEN);
        if (!TextUtils.isEmpty(refreshToken))
            mAccountManager.setUserData(account, Authenticator.KEY_REFRESH_TOKEN, refreshToken);

        String expiry = data.get(Authenticator.KEY_EXPIRY);
        if (!TextUtils.isEmpty(refreshToken))
            mAccountManager.setUserData(account, Authenticator.KEY_EXPIRY, expiry);
    }

    /**
     * Remove all accounts create by CloudProvider
     *
     * Use with care because all user's accounts data with be lost. Recommend to present
     * the user with the warning UI before proceeding.
     */
    public void removeAllAccount() {
        Account[] accounts = mAccountManager.getAccountsByType(ACCOUNT_TYPE);

        for (final Account account : accounts) {
            final CloudAccount oldAccount = getAccountById(account.name);
            mAccountManager.removeAccount(account, new AccountManagerCallback<Boolean>() {
                @Override
                public void run(AccountManagerFuture<Boolean> future) {
                    if (future.isDone()) {
                        try {
                            if (future.getResult()) mListener.onAccountRemoved(oldAccount);
                        } catch (OperationCanceledException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (AuthenticatorException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }, null);
        }
    }

    /**
     * Remove an individual account
     *
     * @param account to be removed
     */
    public void removeAccount(final Account account) {
        final CloudAccount oldAccount = getAccountById(account.name);
        mAccountManager.removeAccount(account, new AccountManagerCallback<Boolean>() {
            @Override
            public void run(AccountManagerFuture<Boolean> future) {
                if (future.isDone()) {
                    try {
                        if (future.getResult()) mListener.onAccountRemoved(oldAccount);
                    } catch (OperationCanceledException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (AuthenticatorException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, null);
    }

    /**
     * Build and return API instance base on type of account
     *
     * @param account that requires the API to be build
     * @return instance of class that extend {@link BaseApi}
     */
    public BaseApi buildApi(CloudAccount account) throws ExceptionInInitializerError {
        try {
            Class<?> clazz = Class.forName(account.api);
            Constructor constructor = clazz.getConstructor(Context.class, Account.class);
            return (BaseApi) constructor.newInstance(mContext, account.getAccount());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new ExceptionInInitializerError("Cloud API can not be found!");
        } catch (InstantiationException e) {
            e.printStackTrace();
            throw new ExceptionInInitializerError("Cloud API can not be initialized!");
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            throw new ExceptionInInitializerError("Cloud API constructor malformed!");
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get the human readable name of the cloud API
     *
     * @param clazz of the cloud API
     * @return name of the cloud API
     */
    private String getApiName(Class<?> clazz) {
        try {
            Field field = clazz.getField("NAME");
            return (String) field.get(null);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }
        return null;
    }

    /**
     * Interface for listener to listen to the account changes
     */
    public interface OnAccountChangeListener {
        /**
         * An account has been removed
         *
         * @param account that has been remove
         */
        void onAccountRemoved(CloudAccount account);

        /**
         * An account has been created and added
         *
         * @param account that has been added
         */
        void onAccountAdded(CloudAccount account);

    }

    /**
     * Convenient method to set the OnAccountsUpdatedListener
     *
     * @param listener to listen for account changes
     */
    public void setAccountChangeListener(@NonNull OnAccountChangeListener listener) {
        mListener = listener;
    }

    /**
     * Convenient method to remove the OnAccountsUpdatedListener
     *
     * @param listener to be removed
     */
    public void removeAccountChangeListener(@NonNull OnAccountChangeListener listener) {
        if (mListener != null) {
            mListener = null;
        }
    }
}
