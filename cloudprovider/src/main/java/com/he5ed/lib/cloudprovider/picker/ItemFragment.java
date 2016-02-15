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


import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.he5ed.lib.cloudprovider.R;
import com.he5ed.lib.cloudprovider.apis.BaseApi;
import com.he5ed.lib.cloudprovider.exceptions.RequestFailException;
import com.he5ed.lib.cloudprovider.models.CFile;
import com.he5ed.lib.cloudprovider.models.CFolder;
import com.he5ed.lib.cloudprovider.utils.GraphicUtils;

import java.io.File;
import java.text.SimpleDateFormat;

/**
 * This is the fragment that show the details information of a cloud file or folder,
 * when the user click on the information icon on the view item. It will opened as the
 * right drawer view in the {@link CloudPickerActivity}.
 *
 * @hide
 */
public class ItemFragment extends Fragment {

    private BaseApi mApi;
    private Object mItem;

    public ItemFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.cp_fragment_item, container, false);
        final Toolbar toolbar = (Toolbar) rootView.findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().onBackPressed();
            }
        });

        final TextView title = (TextView) rootView.findViewById(R.id.title_text_view);
        AppBarLayout appbar = (AppBarLayout) rootView.findViewById(R.id.app_bar);
        appbar.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                // detect collapsed mode
                if (Math.abs(verticalOffset) >= appBarLayout.getTotalScrollRange()) {
                    title.setMaxLines(1);
                } else {
                    title.setMaxLines(2);
                }
            }
        });

        updateView(rootView);
        return rootView;
    }

    /**
     * Set the cloud API for retrieving items
     *
     * @param api that is instantiated by host activity
     */
    public void setApi(BaseApi api) {
        mApi = api;
    }

    /**
     * Display file information
     * @param item
     */
    public void setItem(Object item) {
        mItem = item;
        // update UI
        updateView(getView());
    }

    /**
     * Update the view with item information
     *
     * @param rootView to be updated
     */
    private void updateView(View rootView) {

        ImageView headerImage = (ImageView) rootView.findViewById(R.id.header_image_view);
        TextView title = (TextView) rootView.findViewById(R.id.title_text_view);
        TextView type = (TextView) rootView.findViewById(R.id.type_text_view);
        TextView path = (TextView) rootView.findViewById(R.id.path_text_view);
        TextView created = (TextView) rootView.findViewById(R.id.created_text_view);
        TextView modified = (TextView) rootView.findViewById(R.id.modified_text_view);
        // setup simple date formatter
        SimpleDateFormat sdf = new SimpleDateFormat("dd/M/yyyy");
        if (mItem instanceof CFile) {
            CFile file = (CFile) mItem;
            title.setText(file.getName());
            type.setText(R.string.type_file);
            path.setText(file.getPath());
            if (file.getCreated() != null) created.setText(sdf.format(file.getCreated()));
            if (file.getModified() != null) modified.setText(sdf.format(file.getModified()));
            new GetThumbnailTask().execute(file);
            headerImage.setImageDrawable(GraphicUtils.setTint(getResources(),
                    R.drawable.ic_insert_drive_file_black_48dp,
                    Color.parseColor("#9E9E9E")));

        } else if (mItem instanceof CFolder) {
            CFolder folder = (CFolder) mItem;
            title.setText(folder.getName());
            type.setText(R.string.type_file);
            path.setText(folder.getPath());
            if (folder.getCreated() != null) created.setText(sdf.format(folder.getCreated()));
            if (folder.getModified() != null) modified.setText(sdf.format(folder.getModified()));

            headerImage.setImageDrawable(GraphicUtils.setTint(getResources(),
                    R.drawable.ic_folder_black_48dp,
                    Color.parseColor("#9E9E9E")));
        }
        // expand app bar
        AppBarLayout appbar = (AppBarLayout) rootView.findViewById(R.id.app_bar);
        appbar.setExpanded(true);
    }

    /**
     * Async task to retrieve thumbnail image for file
     */
    private class GetThumbnailTask extends AsyncTask<Object, Void, File> {

        @Override
        protected File doInBackground(Object... params) {
            try {
                return mApi.getThumbnail((CFile) params[0]);
            } catch (RequestFailException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(File file) {
            if (file != null) {
                ImageView headerImage = (ImageView) getView().findViewById(R.id.header_image_view);
                headerImage.setImageDrawable(BitmapDrawable.createFromPath(file.getPath()));
            }
        }
    }

}
