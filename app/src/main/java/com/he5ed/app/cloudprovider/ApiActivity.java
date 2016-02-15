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

package com.he5ed.app.cloudprovider;

import android.accounts.Account;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.he5ed.lib.cloudprovider.CloudProvider;
import com.he5ed.lib.cloudprovider.apis.BaseApi;
import com.he5ed.lib.cloudprovider.exceptions.RequestFailException;
import com.he5ed.lib.cloudprovider.models.CFile;
import com.he5ed.lib.cloudprovider.models.CFolder;
import com.he5ed.lib.cloudprovider.models.CloudAccount;
import com.he5ed.lib.cloudprovider.models.User;
import com.he5ed.lib.cloudprovider.picker.CloudPickerActivity;
import com.he5ed.lib.cloudprovider.utils.FilesUtils;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ApiActivity extends AppCompatActivity implements BaseApi.OnPrepareListener {

    public static final String TAG = "ApiActivity";
    public static final String EXTRA_ACCOUNT = "com.he5ed.app.cloudprovider.ACCOUNT";

    private static final int REQUEST_UPLOAD_FILE = 1;
    private static final int REQUEST_DOWNLOAD_FILE = 2;
    private static final int REQUEST_DELETE_FILE = 3;
    private static final int REQUEST_UPDATE_FILE = 4;
    private static final int REQUEST_CREATE_FOLDER = 5;
    private static final int REQUEST_DELETE_FOLDER = 6;
    private static final int REQUEST_RENAME_FOLDER = 7;
    private static final int REQUEST_RENAME_FILE = 8;
    private static final int REQUEST_MOVE_FILE = 9;
    private static final int REQUEST_UPLOAD_PHOTO = 10;

    private BaseApi mApi;
    private CloudAccount mAccount;
    private EditText mNameInput;
    private CFile mSeletedFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // get intent information
        Account account = getIntent().getParcelableExtra(EXTRA_ACCOUNT);
        if (account == null)
            finish();

        CloudProvider cloudProvider = CloudProvider.getInstance(this);
        mAccount = cloudProvider.getAccountById(account.name);
        mApi = cloudProvider.buildApi(mAccount);
        mApi.prepareApi(this);

        // setup UI
        ImageView avatar = (ImageView) findViewById(R.id.avatar_image_view);
        TextView name = (TextView) findViewById(R.id.name_text_view);
        TextView email = (TextView) findViewById(R.id.email_text_view);
        User user = mAccount.getUser();
        name.setText(user.name);
        email.setText(user.email);
        // download avatar for user
        Picasso.with(this)
                .load(user.avatarUrl)
                .placeholder(R.drawable.account_circle_grey)
                .error(R.drawable.account_circle_grey)
                .into(avatar);

        Button createFolder = (Button) findViewById(R.id.create_folder_button);
        createFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // pick folder
                Intent fileIntent = new Intent(getBaseContext(), CloudPickerActivity.class);
                fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_ACCOUNT_ID, mAccount.id);
                fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_FOLDER, true);
                startActivityForResult(fileIntent, REQUEST_CREATE_FOLDER);
            }
        });

        Button deleteFolder = (Button) findViewById(R.id.delete_folder_button);
        deleteFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // pick folder
                Intent fileIntent = new Intent(getBaseContext(), CloudPickerActivity.class);
                fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_ACCOUNT_ID, mAccount.id);
                fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_FOLDER, true);
                startActivityForResult(fileIntent, REQUEST_DELETE_FOLDER);
            }
        });

        Button uploadFile = (Button) findViewById(R.id.upload_file_button);
        uploadFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // pick folder
                Intent fileIntent = new Intent(getBaseContext(), CloudPickerActivity.class);
                fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_ACCOUNT_ID, mAccount.id);
                fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_FOLDER, true);
                startActivityForResult(fileIntent, REQUEST_UPLOAD_FILE);
            }
        });

        Button deleteFile = (Button) findViewById(R.id.delete_file_button);
        deleteFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // pick file
                Intent fileIntent = new Intent(getBaseContext(), CloudPickerActivity.class);
                fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_ACCOUNT_ID, mAccount.id);
                startActivityForResult(fileIntent, REQUEST_DELETE_FILE);
            }
        });

        Button downloadFile = (Button) findViewById(R.id.download_file_button);
        downloadFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // pick file
                Intent fileIntent = new Intent(getBaseContext(), CloudPickerActivity.class);
                fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_ACCOUNT_ID, mAccount.id);
                startActivityForResult(fileIntent, REQUEST_DOWNLOAD_FILE);
            }
        });

        Button renameFolder = (Button) findViewById(R.id.rename_folder_button);
        renameFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // pick folder
                Intent fileIntent = new Intent(getBaseContext(), CloudPickerActivity.class);
                fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_ACCOUNT_ID, mAccount.id);
                fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_FOLDER, true);
                startActivityForResult(fileIntent, REQUEST_RENAME_FOLDER);
            }
        });

        Button renameFile = (Button) findViewById(R.id.rename_file_button);
        renameFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // pick file
                Intent fileIntent = new Intent(getBaseContext(), CloudPickerActivity.class);
                fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_ACCOUNT_ID, mAccount.id);
                startActivityForResult(fileIntent, REQUEST_RENAME_FILE);
            }
        });

        Button updateFile = (Button) findViewById(R.id.update_file_button);
        updateFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // pick file
                Intent fileIntent = new Intent(getBaseContext(), CloudPickerActivity.class);
                fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_ACCOUNT_ID, mAccount.id);
                startActivityForResult(fileIntent, REQUEST_UPDATE_FILE);
            }
        });

        Button moveFile = (Button) findViewById(R.id.move_file_button);
        moveFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // pick file
                Intent fileIntent = new Intent(getBaseContext(), CloudPickerActivity.class);
                fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_ACCOUNT_ID, mAccount.id);
                startActivityForResult(fileIntent, REQUEST_MOVE_FILE);
            }
        });

        Button uploadPhoto = (Button) findViewById(R.id.upload_photo_button);
        uploadPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // pick folder
                Intent fileIntent = new Intent(getBaseContext(), CloudPickerActivity.class);
                fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_ACCOUNT_ID, mAccount.id);
                fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_FOLDER, true);
                startActivityForResult(fileIntent, REQUEST_UPLOAD_PHOTO);
            }
        });

        Button logout = (Button) findViewById(R.id.logout_button);
        logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mApi.logout(new Callback() {
                    @Override
                    public void onFailure(Request request, IOException e) {

                    }

                    @Override
                    public void onResponse(Response response) throws IOException {

                    }
                });
                finish();
            }
        });
        
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // do nothing if fail
        if (resultCode != Activity.RESULT_OK)
            return;

        switch (requestCode) {
            case REQUEST_UPLOAD_FILE:
                final CFolder folder0 = data.getParcelableExtra(CloudPickerActivity.EXTRA_PICK_RESULT);
                new UploadFileTask().execute(folder0);
                break;
            case REQUEST_DOWNLOAD_FILE:
                CFile file2 = data.getParcelableExtra(CloudPickerActivity.EXTRA_PICK_RESULT);
                new DownloadFileTask().execute(file2, null);
                break;
            case REQUEST_DELETE_FILE:
                CFile file3 = data.getParcelableExtra(CloudPickerActivity.EXTRA_PICK_RESULT);
                new DeleteFileTask().execute(file3);
                break;
            case REQUEST_UPDATE_FILE:
                CFile file4 = data.getParcelableExtra(CloudPickerActivity.EXTRA_PICK_RESULT);
                new UpdateFileTask().execute(file4);
                break;
            case REQUEST_CREATE_FOLDER:
                final CFolder folder1 = data.getParcelableExtra(CloudPickerActivity.EXTRA_PICK_RESULT);
                showEnterName(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new CreateFolderTask().execute(mNameInput.getText().toString(), folder1);
                    }
                });
                break;
            case REQUEST_DELETE_FOLDER:
                CFolder folder2 = data.getParcelableExtra(CloudPickerActivity.EXTRA_PICK_RESULT);
                new DeleteFolderTask().execute(folder2);
                break;
            case REQUEST_RENAME_FOLDER:
                final CFolder folder3 = data.getParcelableExtra(CloudPickerActivity.EXTRA_PICK_RESULT);
                showEnterName(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new RenameFolderTask().execute(folder3, mNameInput.getText().toString());
                    }
                });
                break;
            case REQUEST_RENAME_FILE:
                final CFile file5 = data.getParcelableExtra(CloudPickerActivity.EXTRA_PICK_RESULT);
                showEnterName(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new RenameFileTask().execute(file5, mNameInput.getText().toString());
                    }
                });
                break;
            case REQUEST_MOVE_FILE:
                Parcelable result = data.getParcelableExtra(CloudPickerActivity.EXTRA_PICK_RESULT);
                if (result instanceof CFile) {
                    mSeletedFile = (CFile) result;
                    // pick folder
                    Intent fileIntent = new Intent(getBaseContext(), CloudPickerActivity.class);
                    fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_ACCOUNT_ID, mAccount.id);
                    fileIntent.putExtra(CloudPickerActivity.EXTRA_PICK_FOLDER, true);
                    startActivityForResult(fileIntent, REQUEST_MOVE_FILE);
                } else if (result instanceof CFolder) {
                    new MoveFileTask().execute(mSeletedFile, result);
                }
                break;
            case REQUEST_UPLOAD_PHOTO:
                final CFolder folder6 = data.getParcelableExtra(CloudPickerActivity.EXTRA_PICK_RESULT);
                new UploadPhotoTask().execute(folder6);
                break;
        }
    }

    /**
     * Create new folder
     */
    private class CreateFolderTask extends AsyncTask<Object, Void, Void> {

        @Override
        protected Void doInBackground(Object... params) {
            try {
                mApi.createFolder((String) params[0], (CFolder) params[1]);
            } catch (RequestFailException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Delete the selected folder
     */
    private class DeleteFolderTask extends AsyncTask<Object, Void, Void> {

        @Override
        protected Void doInBackground(Object... params) {
            try {
                mApi.deleteFolder((CFolder) params[0]);
            } catch (RequestFailException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Upload a sample file in the assets dir
     */
    private class UploadFileTask extends AsyncTask<Object, Void, Void> {

        @Override
        protected Void doInBackground(Object... params) {
            try {
                File file = new File(getFilesDir(), "Sample_Text_File.txt");
                if (!file.exists()) {
                    if (file.createNewFile()) {
                        FilesUtils.copyFile(getAssets().open("Sample_Text_File.txt"),
                                new FileOutputStream(file));
                    }
                }
                mApi.uploadFile(file, (CFolder) params[0]);
            } catch (RequestFailException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }
    }

    /**
     * Upload a sample photo in the assets dir
     */
    private class UploadPhotoTask extends AsyncTask<Object, Void, Void> {

        @Override
        protected Void doInBackground(Object... params) {
            try {
                File file = new File(getFilesDir(), "Sample_Photo.jpg");
                if (!file.exists()) {
                    if (file.createNewFile()) {
                        FilesUtils.copyFile(getAssets().open("Sample_Photo.jpg"),
                                new FileOutputStream(file));
                    }
                }
                mApi.uploadFile(file, (CFolder) params[0]);
            } catch (RequestFailException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }
    }

    /**
     * Delete the selected file
     */
    private class DeleteFileTask extends AsyncTask<Object, Void, Void> {

        @Override
        protected Void doInBackground(Object... params) {
            try {
                mApi.deleteFile((CFile) params[0]);
            } catch (RequestFailException e) {
                e.printStackTrace();
            }

            return null;
        }
    }

    /**
     * Download the selected file into the cache dir
     */
    private class DownloadFileTask extends AsyncTask<Object, Void, Void> {

        @Override
        protected Void doInBackground(Object... params) {
            try {
                mApi.downloadFile((CFile) params[0], (String) params[1]);
            } catch (RequestFailException e) {
                e.printStackTrace();
            }

            return null;
        }
    }

    /**
     * Rename the selected folder to a new name
     */
    private class RenameFolderTask extends AsyncTask<Object, Void, Void> {

        @Override
        protected Void doInBackground(Object... params) {
            try {
                mApi.renameFolder((CFolder) params[0], (String) params[1]);
            } catch (RequestFailException e) {
                e.printStackTrace();
            }

            return null;
        }
    }

    /**
     * Rename the selected file to a new name
     */
    private class RenameFileTask extends AsyncTask<Object, Void, Void> {

        @Override
        protected Void doInBackground(Object... params) {
            try {
                mApi.renameFile((CFile) params[0], (String) params[1]);
            } catch (RequestFailException e) {
                e.printStackTrace();
            }

            return null;
        }
    }

    /**
     * Move file to the selected folder
     */
    private class MoveFileTask extends AsyncTask<Object, Void, Void> {

        @Override
        protected Void doInBackground(Object... params) {
            try {
                mApi.moveFile((CFile) params[0], (CFolder) params[1]);
            } catch (RequestFailException e) {
                e.printStackTrace();
            }

            return null;
        }
    }

    /**
     * Update the selected file with new content
     */
    private class UpdateFileTask extends AsyncTask<Object, Void, Void> {

        @Override
        protected Void doInBackground(Object... params) {
            try {
                File file = new File(getFilesDir(), "Updated_Text_File.txt");
                if (!file.exists()) {
                    if (file.createNewFile()) {
                        FilesUtils.copyFile(getAssets().open("Updated_Text_File.txt"),
                                new FileOutputStream(file));
                    }
                }
                mApi.updateFile((CFile) params[0], file);
            } catch (RequestFailException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onPrepareSuccessful() {

    }

    @Override
    public void onPrepareFail(Exception e) {
        finish();
    }

    /**
     * Enter new name for file or folder rename task
     *
     * @param listener to check whether user click the Ok button
     */
    private void showEnterName(DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        mNameInput = new EditText(this);
        builder.setTitle("Enter name").setView(mNameInput);

        builder.setPositiveButton("Ok", listener);

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builder.create().show();
    }
}
