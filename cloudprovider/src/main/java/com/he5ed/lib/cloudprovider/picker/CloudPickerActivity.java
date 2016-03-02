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

package com.he5ed.lib.cloudprovider.picker;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.he5ed.lib.cloudprovider.CloudProvider;
import com.he5ed.lib.cloudprovider.R;
import com.he5ed.lib.cloudprovider.apis.BaseApi;
import com.he5ed.lib.cloudprovider.models.CFile;
import com.he5ed.lib.cloudprovider.models.CFolder;
import com.he5ed.lib.cloudprovider.models.CloudAccount;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

/**
 * Activity that provide UI for user to select a cloud file or folder from the cloud storage.<p>
 * Must pass in the following extra values into the activity's bundle:
 * <ul>
 *     <li> {@link #EXTRA_PICK_ACCOUNT_ID} key for the account unique ID string
 *     <li> {@link #EXTRA_PICK_FOLDER} key for enabling folder selection if value
 *     is true. Default value is false for file selection.
 * </ul>
 * <p>
 * To obtain the selected file or folder after user selection,
 * use {@link android.app.Activity#startActivityForResult(Intent, int, Bundle)} and query the
 * value of the {@link #EXTRA_PICK_RESULT} key from the activity's bundle.
 * <p>
 * Example code:
 * <pre>
 * Intent intent = new Intent(getBaseContext(), CloudPickerActivity.class);
 * fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_ACCOUNT_ID, mAccount.id);
 * fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_FOLDER, true);
 * startActivityForResult(intent, 1);
 * </pre>
 *
 */
public class CloudPickerActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, BaseApi.OnPrepareListener {

    /**
     * @hide
     */
    public static final String TAG = "CloudPickerActivity";

    /**
     * Extra key to query the selected cloud file or folder from the return activity's bundle
     */
    public static final String EXTRA_PICK_RESULT = "com.he5ed.lib.cloudprovider.PICK_RESULT";

    /**
     * Extra key to select folder instead of file. Pass in the targeted {@link CFolder}
     * into the activity's intent
     */
    public static final String EXTRA_PICK_FOLDER = "com.he5ed.lib.cloudprovider.PICK_FOLDER";

    /**
     * Extra key to explore only individual account. Pass in the targeted {@link CloudAccount}
     * into the activity's intent.
     */
    public static final String EXTRA_PICK_ACCOUNT_ID = "com.he5ed.lib.cloudprovider.PICK_ACCOUNT";

    private CloudAccount[] mAccounts;
    private BaseApi mApi;
    private FragmentManager mFragmentManager;
    private ProgressBar mProgressBar;
    private LinearLayout mErrorView;
    private ItemFragment mItemFragment;

    /**
     * @hide
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cp_activity_picker);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        // lock right drawer from been swiped open
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setItemIconTintList(null); // remove the auto grey tint to keep icon color
        Menu menu = navigationView.getMenu();
        CloudProvider cloudProvider = CloudProvider.getInstance(this);
        // check whether individual account is assigned
        String accountId = getIntent().getStringExtra(EXTRA_PICK_ACCOUNT_ID);
        if (!TextUtils.isEmpty(accountId)) {
            mAccounts = new CloudAccount[1];
            mAccounts[0] = cloudProvider.getAccountById(accountId);
        } else {
            mAccounts = cloudProvider.getCloudAccounts();
        }
        // populate navigation menu
        for (int i = 0; i < mAccounts.length; i++) {
            CloudAccount account = mAccounts[i];
            MenuItem item = menu.add(0, i, 0, account.getUser().email);
            // use cloud API class reflection
            try {
                Class clazz = Class.forName(account.api);
                Field iconResource = clazz.getField("ICON_RESOURCE");
                item.setIcon(iconResource.getInt(null));
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
                item.setIcon(R.drawable.ic_cloud_black_24dp);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

            item.setCheckable(true);
        }

        mFragmentManager = getSupportFragmentManager();
        mItemFragment = (ItemFragment) mFragmentManager.findFragmentById(R.id.right_drawer_view);
        if (mItemFragment == null) {
            mItemFragment = new ItemFragment();
            mFragmentManager.beginTransaction().add(R.id.right_drawer_view, mItemFragment).commit();
        }

        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        mErrorView = (LinearLayout) findViewById(R.id.error_view);
        if (savedInstanceState != null) {
            switch (savedInstanceState.getInt("empty_view_visibility")) {
                case View.VISIBLE:
                    mErrorView.setVisibility(View.VISIBLE);
                    ImageView icon = (ImageView) mErrorView.findViewById(R.id.empty_icon_image_view);
                    TextView title = (TextView) mErrorView.findViewById(R.id.empty_title_text_view);
                    TextView detail = (TextView) mErrorView.findViewById(R.id.empty_detail_text_view);
                    Bitmap bitmap = savedInstanceState.getParcelable("icon_drawable");
                    icon.setImageBitmap(bitmap);
                    title.setText(savedInstanceState.getCharSequence("title_text"));
                    detail.setText(savedInstanceState.getCharSequence("detail_text"));
                    break;
                case View.INVISIBLE:
                    mErrorView.setVisibility(View.INVISIBLE);
                    break;
                case View.GONE:
                    mErrorView.setVisibility(View.GONE);
                    break;
            }
        } else {
            // open drawer on first load
            drawer.openDrawer(GravityCompat.START);
        }
    }

    /**
     * @hide
     */
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END);
        } else {
            super.onBackPressed();
        }
    }

    /**
     * @hide
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // save empty view state skip if not visible
        LinearLayout emptyView = (LinearLayout) findViewById(R.id.error_view);
        outState.putInt("empty_view_visibility", emptyView.getVisibility());
        if (emptyView.getVisibility() != View.VISIBLE) return;

        ImageView icon = (ImageView) findViewById(R.id.empty_icon_image_view);
        TextView title = (TextView) findViewById(R.id.empty_title_text_view);
        TextView detail = (TextView) findViewById(R.id.empty_detail_text_view);

        Bitmap bitmap = ((BitmapDrawable) icon.getDrawable()).getBitmap();
        outState.putParcelable("icon_drawable", bitmap);
        outState.putCharSequence("title_text", title.getText());
        outState.putCharSequence("detail_text", detail.getText());
    }

    /**
     * @hide
     */
    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        // prepare cloud API
        setupApi(mAccounts[id]);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * @hide
     */
    @Override
    public void onPrepareSuccessful() {
        // start to retrieve all items from cloud
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                openPickerFragment();
                mItemFragment.setApi(mApi);
                mErrorView.setVisibility(View.GONE);
                mProgressBar.setVisibility(View.GONE);
            }
        });
    }

    /**
     * @hide
     */
    @Override
    public void onPrepareFail(final Exception e) {
        // preparation failed
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateEmptyView(e.getMessage());
                mErrorView.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.GONE);
            }
        });
    }

    /**
     * Setup the relevant API instance to interact with server
     *
     * @param account of the service API
     */
    private void setupApi(CloudAccount account) {
        // use cloud API class reflection
        try {
            Class<?> clazz = Class.forName(account.api);
            Constructor constructor = clazz.getConstructor(Context.class, Account.class);
            mApi = (BaseApi) constructor.newInstance(this, account.getAccount());
            mApi.prepareApi(this);
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
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        mProgressBar.setVisibility(View.VISIBLE);
    }

    /**
     * Notify user the empty view is due to error
     *
     * @param error message to be shown
     */
    private void updateEmptyView(String error) {
        // find ui items
        TextView title = (TextView) mErrorView.findViewById(R.id.empty_title_text_view);
        TextView detail = (TextView) mErrorView.findViewById(R.id.empty_detail_text_view);

        title.setText(R.string.error_empty_title);
        detail.setText(error);
    }

    /**
     * Open new back stack of fragments and create the fragment to show folder content
     *
     * @hide
     */
    private void openPickerFragment() {
        // clear all previous fragment
        mFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        PickerFragment pickerFragment = new PickerFragment();
        pickerFragment.setApi(mApi);
        pickerFragment.exploreFolder(mApi.getRoot());
        mFragmentManager.beginTransaction().replace(R.id.fragment, pickerFragment).commit();
    }

    /**
     * Show the item detail information in the right drawer
     *
     * @param item of file or folder instance
     *
     * @hide
     */
    public void showItemInfo(Object item) {
        if (item instanceof CFile || item instanceof CFolder) {
            mItemFragment.setItem(item);
        } else {
            return;
        }

        // open drawer
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.openDrawer(GravityCompat.END);
    }
}
