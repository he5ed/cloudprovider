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

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.he5ed.lib.cloudprovider.R;
import com.he5ed.lib.cloudprovider.models.CFile;
import com.he5ed.lib.cloudprovider.models.CFolder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Implement {@link RecyclerView.Adapter} to show every cloud item that contain inside
 * a cloud folder in the {@link RecyclerView}
 *
 * @hide
 */
public class PickerAdapter extends RecyclerView.Adapter<PickerAdapter.ViewHolder> {

    private Context mContext;
    private List<Comparable> mList;
    private ItemInteractionListener mItemInteractionListener;

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnLongClickListener {

        private ItemInteractionListener mListener;

        ImageView icon;
        TextView primaryText;
        TextView secondaryText;
        ImageButton info;

        public ViewHolder(View itemView, ItemInteractionListener listener) {
            super(itemView);
            mListener = listener;
            icon = (ImageView) itemView.findViewById(R.id.icon_image_view);
            primaryText = (TextView) itemView.findViewById(R.id.primary_text_view);
            secondaryText = (TextView) itemView.findViewById(R.id.secondary_text_view);
            info = (ImageButton) itemView.findViewById(R.id.info_image_button);
            info.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mListener != null)
                        mListener.onInfoButtonClick(v, getAdapterPosition());
                }
            });
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (mListener != null)
                mListener.onItemClick(v, getAdapterPosition());
        }

        @Override
        public boolean onLongClick(View v) {
            if (mListener != null)
                mListener.onItemLongClick(v, getAdapterPosition());
            return true;
        }
    }

    public PickerAdapter(Context context) {
        mContext = context;
        mList = new ArrayList<>();
    }

    @Override
    public PickerAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View view = inflater.inflate(R.layout.cp_list_item_picker, parent, false);
        return new ViewHolder(view, mItemInteractionListener);
    }

    @Override
    public void onBindViewHolder(PickerAdapter.ViewHolder holder, int position) {

        if (mList.get(position) instanceof CFile) {
            // cast to CFile
            CFile file = ((CFile) mList.get(position));
            holder.primaryText.setText(file.getName());
            SimpleDateFormat sdf = new SimpleDateFormat("dd/M/yyyy");
            Date modified = file.getModified();
            if (modified != null) {
                if (holder.secondaryText.getVisibility() != View.VISIBLE) {
                    holder.secondaryText.setVisibility(View.VISIBLE);
                }
                holder.secondaryText.setText(sdf.format(modified));
            } else {
                if (holder.secondaryText.getVisibility() != View.GONE){
                    holder.secondaryText.setVisibility(View.GONE);
                }
            }

            holder.icon.setImageResource(R.drawable.ic_insert_drive_file_black_24dp);
        } else if (mList.get(position) instanceof CFolder) {
            // cast to CFolder
            CFolder folder = ((CFolder) mList.get(position));
            holder.primaryText.setText(folder.getName());
            SimpleDateFormat sdf = new SimpleDateFormat("dd/M/yyyy");
            Date modified = folder.getModified();
            if (modified != null) {
                if (holder.secondaryText.getVisibility() != View.VISIBLE){
                    holder.secondaryText.setVisibility(View.VISIBLE);
                }
                holder.secondaryText.setText(sdf.format(modified));
            } else {
                if (holder.secondaryText.getVisibility() != View.GONE){
                    holder.secondaryText.setVisibility(View.GONE);
                }
            }

            holder.icon.setImageResource(R.drawable.ic_folder_black_24dp);
        }

    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    /**
     * Get either folder or filez object from the adapter
     *
     * @param position of item in adapter
     * @return folder or filez receiver to verify before cast
     */
    public Object getItem(int position) {
        return mList.get(position);
    }

    /**
     * Reset the adapter list with new list item usually after getting update from API
     *
     * @param list with latest data to replace the old list
     */
    public void setItemList(List<Comparable> list) {
        mList.clear();
        if (list != null) mList.addAll(list);
        Collections.sort(mList);
        notifyDataSetChanged();
    }

    /**
     * Interface for listeners to listen to ViewHolder items interaction
     */
    public interface ItemInteractionListener {
        /**
         * Adapter's item was clicked by user
         *
         * @param view of the item
         * @param position of the item in adapter
         */
        void onItemClick(View view, int position);

        /**
         * User performs long click on the adapter's item
         *
         * @param view of the item
         * @param position of the item in adapter
         * @return true if the callback consumed the long click, false otherwise
         */
        boolean onItemLongClick(View view, int position);

        /**
         * User click on the adapter's item more button (if applicable)
         *
         * @param view of the item
         * @param position of the item in adapter
         */
        void onInfoButtonClick(View view, int position);
    }

    /**
     * Convenient method to set the ItemInteractionListener
     *
     * @param listener to listen to ViewHolder items interaction
     */
    public void setOnItemInteractionListener(ItemInteractionListener listener) {
        mItemInteractionListener = listener;
    }
}
