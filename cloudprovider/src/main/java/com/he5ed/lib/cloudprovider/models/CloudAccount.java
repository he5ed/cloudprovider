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

package com.he5ed.lib.cloudprovider.models;

import android.accounts.Account;

/**
 * Value type that represents a cloud account in the {@link com.he5ed.lib.cloudprovider.CloudProvider}.
 * It contain {@link User} detail information and linked to the {@link Account} that is stored in
 * {@link android.accounts.AccountManager}
 */
public class CloudAccount {

    /**
     * The unique id of the cloud account. This is usually query from the user's cloud service
     * account id. DO NOT use user's email as the id as the user might be using the same email
     * to register for other cloud services.
     */
    public String id;

    /**
     *
     */
    public String type;

    /**
     * This is the full c
     */
    public String api;
    private User mUser;
    private Account mAccount;

    public CloudAccount() {
        super();
    }

    public User getUser() {
        return mUser;
    }

    public void setUser(User user) {
        mUser = user;
    }

    public Account getAccount() {
        return mAccount;
    }

    public void setAccount(Account account) {
        mAccount = account;
    }
}
