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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.he5ed.lib.cloudprovider.CloudProvider;
import com.he5ed.lib.cloudprovider.R;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Convenient implementation of DialogFragment that presents a dialog box to user to select
 * the available cloud services that he/she choose to login into.
 *
 * @hide
 */
public class AddAccountDialogFragment extends DialogFragment
        implements DialogInterface.OnClickListener {

    private List mApiList;

    @Override
    public void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        mApiList = CloudProvider.getInstance(getContext()).getApiList();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getString(R.string.account_dialog_title));
        ListAdapter listAdapter = new AccountTypeAdapter(getContext(), R.layout.cp_list_item_dialog);
        builder.setAdapter(listAdapter, this);

        // Create the AlertDialog object and return it
        return builder.create();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {


        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onPause() {
        super.onResume();
    }

    @Override
    public void onDestroyView() {
        // solution for bug in compatible lib
        if (getDialog() != null && getRetainInstance())
            getDialog().setDismissMessage(null);
        super.onDestroyView();
    }

    @Override
    public void onDismiss (DialogInterface dialog) {
        // update listener

        super.onDismiss(dialog);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        Class clazz = (Class) mApiList.get(which);
        // add new cloud account
        CloudProvider.getInstance(getContext()).addAccount(clazz.getCanonicalName(), getActivity());
    }


    private class AccountTypeAdapter extends ArrayAdapter {

        private Context mContext;
        private int mResource;

        public AccountTypeAdapter(Context context, int resource) {
            super(context, resource);
            mContext = context;
            mResource = resource;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService
                    (Context.LAYOUT_INFLATER_SERVICE);
            View view;
            // inflate default view if not convert view is provided
            if (convertView == null) {
                view = inflater.inflate(mResource, parent, false);
            } else {
                view = convertView;
            }
            // populate view
            TextView name = (TextView) view.findViewById(R.id.primary_text_view);
            ImageView icon = (ImageView) view.findViewById(R.id.icon_image_view);
            if (name != null){
                Class clazz = (Class) mApiList.get(position);
                try {
                    Field apiName = clazz.getField("NAME");
                    Field apiIconRes = clazz.getField("ICON_RESOURCE");
                    name.setText((CharSequence) apiName.get(null));
                    icon.setImageResource(apiIconRes.getInt(null));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                }
            }

            return view;
        }

        @Override
        public int getCount () {
            return mApiList.size();
        }
    }
}
