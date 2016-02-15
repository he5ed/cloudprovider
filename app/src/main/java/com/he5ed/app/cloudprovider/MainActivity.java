package com.he5ed.app.cloudprovider;

import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.he5ed.app.cloudprovider.ui.adapters.AccountAdapter;
import com.he5ed.app.cloudprovider.ui.widgets.RecyclerListView;
import com.he5ed.lib.cloudprovider.CloudProvider;
import com.he5ed.lib.cloudprovider.apis.BoxApi;
import com.he5ed.lib.cloudprovider.apis.CloudDriveApi;
import com.he5ed.lib.cloudprovider.apis.DropboxApi;
import com.he5ed.lib.cloudprovider.apis.OneDriveApi;
import com.he5ed.lib.cloudprovider.auth.AddAccountDialogFragment;
import com.he5ed.lib.cloudprovider.models.CFile;
import com.he5ed.lib.cloudprovider.models.CFolder;
import com.he5ed.lib.cloudprovider.picker.CloudPickerActivity;

public class MainActivity extends AppCompatActivity
        implements AccountAdapter.ItemInteractionListener {

    private static final int REQUEST_PICK_FILE = 1;
    private static final int REQUEST_PICK_FOLDER = 2;

    private CloudProvider mCloudProvider;
    private AccountAdapter mAccountAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // must setup APIs first before instantiate CloudProvider
        setupApis();
        CloudProvider.ACCOUNT_TYPE = "com.he5ed.app.cloudprovider";
        mCloudProvider = CloudProvider.getInstance(this);

        mAccountAdapter = new AccountAdapter(this);
        LinearLayout emptyView = (LinearLayout) findViewById(android.R.id.empty);
        RecyclerListView accountListView = (RecyclerListView) findViewById(R.id.recycler_view);
        accountListView.setEmptyView(emptyView);
        accountListView.setLayoutManager(new LinearLayoutManager(this));
        accountListView.setAdapter(mAccountAdapter);
        mAccountAdapter.setOnItemInteractionListener(this);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addAccount();
                Snackbar.make(view, "Add cloud account", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // end if failure
        if (resultCode == Activity.RESULT_CANCELED)
            return;

        if (requestCode == REQUEST_PICK_FILE) {
            CFile filez = data.getParcelableExtra(CloudPickerActivity.EXTRA_PICK_RESULT);
            String filename = filez.getName();
        } else if (requestCode == REQUEST_PICK_FOLDER) {
            CFolder folder = data.getParcelableExtra(CloudPickerActivity.EXTRA_PICK_RESULT);
            String foldername = folder.getName();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.action_pick_file:
                // pick file
                Intent fileIntent = new Intent(this, CloudPickerActivity.class);
                startActivityForResult(fileIntent, REQUEST_PICK_FILE);
                break;
            case R.id.action_pick_folder:
                // pick folder
                Intent folderIntent = new Intent(this, CloudPickerActivity.class);
                folderIntent.putExtra(CloudPickerActivity.EXTRA_PICK_FOLDER, true);
                startActivityForResult(folderIntent, REQUEST_PICK_FOLDER);
                break;
            case R.id.action_remove_all:
                // remove all account
                mCloudProvider.removeAllAccount();
                break;
            case R.id.action_settings:
                // do nothing
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        //mCloudProvider.removeAccountChangeListener(this);

        super.onPause();
    }

    private void setupApis() {
        // Cloud accounts configuration
        BoxApi.CLIENT_ID = BuildConfig.BOX_CLIENT_ID;
        BoxApi.CLIENT_SECRET = BuildConfig.BOX_CLIENT_SECRET;
        BoxApi.REDIRECT_URL = BuildConfig.BOX_REDIRECT_URL;

        DropboxApi.CLIENT_ID = BuildConfig.DROPBOX_CLIENT_ID;
        DropboxApi.CLIENT_SECRET = BuildConfig.DROPBOX_CLIENT_SECRET;
        DropboxApi.REDIRECT_URL = BuildConfig.DROPBOX_REDIRECT_URL;

        OneDriveApi.CLIENT_ID = BuildConfig.ONECLOUD_CLIENT_ID;
        OneDriveApi.CLIENT_SECRET = BuildConfig.ONECLOUD_CLIENT_SECRET;
        OneDriveApi.REDIRECT_URL = BuildConfig.ONECLOUD_REDIRECT_URL;

        CloudDriveApi.CLIENT_ID = BuildConfig.CLOUDDRIVE_CLIENT_ID;
        CloudDriveApi.CLIENT_SECRET = BuildConfig.CLOUDDRIVE_CLIENT_SECRET;
        CloudDriveApi.REDIRECT_URL = BuildConfig.CLOUDDRIVE_REDIRECT_URL;
    }

    private void addAccount() {
        new AddAccountDialogFragment()
                .show(getSupportFragmentManager(), AddAccountDialogFragment.class.getSimpleName());
    }

    @Override
    public void onItemClick(View view, int position) {
        Account account = mAccountAdapter.getItem(position).getAccount();
        Intent intent = new Intent(this, ApiActivity.class);
        intent.putExtra(ApiActivity.EXTRA_ACCOUNT, account);
        startActivity(intent);
    }

    @Override
    public boolean onItemLongClick(View view, int position) {
        return false;
    }

    @Override
    public void onMoreButtonClick(View view, int position) {

    }

}
