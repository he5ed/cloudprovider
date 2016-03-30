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

import android.os.Bundle;
import android.support.v4.app.FragmentManager;

import com.he5ed.lib.cloudprovider.R;

/**
 * @hide
 */
public class OAuth2Activity extends AccountAuthenticatorActivity {

    public static final String EXTRA_ACCOUNT_NAME = "com.he5ed.lib.cloudprovider.ACCOUNT_NAME";
    public static final String EXTRA_ACCOUNT_TYPE = "com.he5ed.lib.cloudprovider.ACCOUNT_TYPE";
    public static final String EXTRA_CLOUD_API = "com.he5ed.lib.cloudprovider.CLOUD_API";
    public static final String EXTRA_AUTH_TYPE = "com.he5ed.lib.cloudprovider.AUTH_TYPE";
    public static final String EXTRA_NATIVE_UI = "com.he5ed.lib.cloudprovider.AUTH_NATIVE_UI";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cp_activity_oauth);

        // update fragment container with the current fragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        OAuth2Fragment fragment = (OAuth2Fragment) fragmentManager.findFragmentById(R.id.fragment);
        if (fragment == null) {
            fragment = new OAuth2Fragment();
            fragmentManager.beginTransaction().add(R.id.fragment, fragment).commit();
        }

    }

}
