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


import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.he5ed.lib.cloudprovider.R;
import com.he5ed.lib.cloudprovider.apis.BaseApi;
import com.he5ed.lib.cloudprovider.exceptions.RequestFailException;
import com.he5ed.lib.cloudprovider.models.CFile;
import com.he5ed.lib.cloudprovider.models.CFolder;

import java.util.List;

/**
 * @hide
 */
public class PickerFragment extends Fragment
        implements PickerAdapter.ItemInteractionListener {

    /**
     * Type of empty causes
     */
    private static final int CAUSE_EMPTY = 1;
    private static final int CAUSE_ERROR = 2;
    private static final int CAUSE_SEARCH = 3;

    private PickerAdapter mAdapter;
    private BaseApi mApi;
    private LinearLayout mEmptyView;
    private CFolder mFolder;
    private boolean mFolderOption;
    private Snackbar mSelectFolderBar;
    private boolean mSearchViewExpand;
    private String mSearchQuery;
    private SearchView mSearchView;

    public PickerFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
        mAdapter = new PickerAdapter(getContext());
        mFolderOption = getActivity().getIntent()
                .getBooleanExtra(CloudPickerActivity.EXTRA_PICK_FOLDER, false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.cp_fragment_picker, container, false);
        mEmptyView = (LinearLayout) rootView.findViewById(android.R.id.empty);
        if (savedInstanceState != null) {
            switch (savedInstanceState.getInt("empty_view_visibility")) {
                case View.VISIBLE:
                    mEmptyView.setVisibility(View.VISIBLE);
                    ImageView icon = (ImageView) mEmptyView.findViewById(R.id.empty_icon_image_view);
                    TextView title = (TextView) mEmptyView.findViewById(R.id.empty_title_text_view);
                    TextView detail = (TextView) mEmptyView.findViewById(R.id.empty_detail_text_view);
                    Bitmap bitmap = savedInstanceState.getParcelable("icon_drawable");
                    icon.setImageBitmap(bitmap);
                    title.setText(savedInstanceState.getCharSequence("title_text"));
                    detail.setText(savedInstanceState.getCharSequence("detail_text"));
                    break;
                case View.INVISIBLE:
                    mEmptyView.setVisibility(View.INVISIBLE);
                    break;
                case View.GONE:
                    mEmptyView.setVisibility(View.GONE);
                    break;
            }
        }
        RecyclerListView recyclerView = (RecyclerListView) rootView.findViewById(R.id.recyler_view);
        recyclerView.setEmptyView(mEmptyView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(mAdapter);

        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.picker, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        // listen for search view closed
        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                mSearchViewExpand = true;
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                // reset adapter
                mSearchViewExpand = false;
                mSearchQuery = "";
                // restore folder list
                exploreFolder(mFolder);
                return true;
            }
        });

        mSearchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        mSearchView.setQueryHint(getString(R.string.hint_search_folder));
        // listen for search query submission
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                mSearchQuery = s;
                // perform search
                search(s);
                mSearchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                return true;
            }
        });

        if (mSearchViewExpand) {
            MenuItemCompat.expandActionView(searchItem);
            if (!mSearchQuery.equals("")) {
                mSearchView.setQuery(mSearchQuery, false);
                mSearchView.clearFocus();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            if (!mSearchViewExpand) exploreFolder(mFolder);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        mAdapter.setOnItemInteractionListener(this);
        // update toolbar title
        if (!TextUtils.isEmpty(mFolder.getName())) getActivity().setTitle(mFolder.getName());
        // enable folder selection
        if (mFolderOption) {
            showSelectFolderBar();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mAdapter.setOnItemInteractionListener(null);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (getView() == null) return;
        // save empty view state skip if not visible
        LinearLayout emptyView = (LinearLayout) getView().findViewById(android.R.id.empty);
        outState.putInt("empty_view_visibility", emptyView.getVisibility());
        if (emptyView.getVisibility() != View.VISIBLE) return;

        ImageView icon = (ImageView) getView().findViewById(R.id.empty_icon_image_view);
        TextView title = (TextView) getView().findViewById(R.id.empty_title_text_view);
        TextView detail = (TextView) getView().findViewById(R.id.empty_detail_text_view);

        Bitmap bitmap = ((BitmapDrawable) icon.getDrawable()).getBitmap();
        outState.putParcelable("icon_drawable", bitmap);
        outState.putCharSequence("title_text", title.getText());
        outState.putCharSequence("detail_text", detail.getText());
    }

    @Override
    public void onItemClick(View view, int position) {
        // get item
        if (mAdapter.getItem(position) instanceof CFile && !mFolderOption) {
            CFile filez = ((CFile) mAdapter.getItem(position));
            // return filez as result to calling activity
            Intent result = new Intent();
            result.putExtra(CloudPickerActivity.EXTRA_PICK_RESULT, filez);
            getActivity().setResult(Activity.RESULT_OK, result);
            getActivity().finish();
        } else if (mAdapter.getItem(position) instanceof CFolder) {
            final CFolder folder = ((CFolder) mAdapter.getItem(position));
            // create new PickerFragment and transact
            PickerFragment newFragment = new PickerFragment();
            newFragment.setApi(mApi);
            newFragment.exploreFolder(folder);
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.replace(getId(), newFragment);
            transaction.addToBackStack(null);
            transaction.commit();
        }
    }

    @Override
    public boolean onItemLongClick(View view, int position) {
        return false;
    }

    @Override
    public void onInfoButtonClick(View view, int position) {
        ((CloudPickerActivity) getActivity()).showItemInfo(mAdapter.getItem(position));
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
     * Explore all the items inside a folder
     *
     * @param folder to explore
     */
    public void exploreFolder(CFolder folder) {
        mFolder = folder;
        // must run on other thread
        new AsyncTask<String, Void, List>() {

            String error;

            @Override
            protected List doInBackground(String... params) {
                try {
                    return mApi.exploreFolder(mFolder, 0);
                } catch (RequestFailException e) {
                    e.printStackTrace();
                    error = e.getMessage();
                    Log.e(CloudPickerActivity.TAG, e.getMessage());
                }

                return null;
            }

            @Override
            protected void onPostExecute(List list) {
                mAdapter.setItemList(list);
                if (error != null) {
                    // error occur print the error message
                    updateEmptyView(CAUSE_ERROR, error);
                } else {
                    // it is just an empty folder
                    updateEmptyView(CAUSE_EMPTY, null);
                }
            }
        }.execute();
    }

    /**
     * Search for items inside a folder that match the keyword
     *
     * @param keyword to search for
     */
    public void search(String keyword) {
        // must run on other thread
        new AsyncTask<String, Void, List>() {

            String error;

            @Override
            protected List doInBackground(String... params) {
                try {
                    return mApi.search(params[0], mFolder);
                } catch (RequestFailException e) {
                    e.printStackTrace();
                    error = e.getMessage();
                    Log.e(CloudPickerActivity.TAG, e.getMessage());
                }

                return null;
            }

            @Override
            protected void onPostExecute(List list) {
                mAdapter.setItemList(list);
                if (error != null) {
                    // error occur print the error message
                    updateEmptyView(CAUSE_ERROR, error);
                } else {
                    // no result found
                    updateEmptyView(CAUSE_SEARCH, null);
                }
            }
        }.execute(keyword);
    }

    /**
     * Notify user the empty view is due to various causes
     *
     * @param cause of empty, whether empty folder, error or search no result
     * @param error message to be shown, optional only applicable for error cause
     */
    private void updateEmptyView(int cause, String error) {
        // find ui items
        ImageView icon = (ImageView) mEmptyView.findViewById(R.id.empty_icon_image_view);
        TextView title = (TextView) mEmptyView.findViewById(R.id.empty_title_text_view);
        TextView detail = (TextView) mEmptyView.findViewById(R.id.empty_detail_text_view);

        switch (cause) {
            case CAUSE_EMPTY:
                icon.setImageResource(R.drawable.ic_folder_open_black_48dp);
                title.setText(R.string.folder_empty_title);
                detail.setText(R.string.folder_empty_detail);
                break;
            case CAUSE_ERROR:
                icon.setImageResource(R.drawable.ic_report_problem_black_48dp);
                title.setText(R.string.error_empty_title);
                detail.setText(error);
                break;
            case CAUSE_SEARCH:
                icon.setImageResource(R.drawable.ic_search_black_48dp);
                title.setText(R.string.search_empty_title);
                detail.setText(R.string.search_empty_detail);
                break;
        }
    }

    private View swapOutEmptyViewDetails(View oldView, View newView) {
        if (oldView == null && newView == null) {
            return null;
        } else if (oldView == null) {
            return newView;
        } else if (newView == null) {
            return oldView;
        }

        ImageView oldIcon = (ImageView) oldView.findViewById(R.id.empty_icon_image_view);
        TextView oldTitle = (TextView) oldView.findViewById(R.id.empty_title_text_view);
        TextView oldDetail = (TextView) oldView.findViewById(R.id.empty_detail_text_view);

        ImageView newIcon = (ImageView) newView.findViewById(R.id.empty_icon_image_view);
        TextView newTitle = (TextView) newView.findViewById(R.id.empty_title_text_view);
        TextView newDetail = (TextView) newView.findViewById(R.id.empty_detail_text_view);
        newIcon.setImageDrawable(oldIcon.getDrawable());
        newTitle.setText(oldTitle.getText());
        newDetail.setText(oldDetail.getText());

        return newView;
    }

    private void showSelectFolderBar() {
        // if Snackbar already shown
        if (mSelectFolderBar != null && mSelectFolderBar.isShown()) {
            mSelectFolderBar.dismiss();
        }
        mSelectFolderBar = Snackbar.make(
                getView(),
                getString(R.string.select_folder_title, mFolder.getName()),
                Snackbar.LENGTH_INDEFINITE);
        mSelectFolderBar.setAction(getString(R.string.select_folder_action),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // return folder as result to calling activity
                        Intent result = new Intent();
                        result.putExtra(CloudPickerActivity.EXTRA_PICK_RESULT, mFolder);
                        getActivity().setResult(Activity.RESULT_OK, result);
                        getActivity().finish();
                    }
                }).show();
    }
}
