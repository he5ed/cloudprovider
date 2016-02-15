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

package com.he5ed.app.cloudprovider.ui.adapters;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.he5ed.app.cloudprovider.R;
import com.he5ed.lib.cloudprovider.CloudProvider;
import com.he5ed.lib.cloudprovider.models.CloudAccount;
import com.squareup.picasso.Picasso;

public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.ViewHolder>
        implements CloudProvider.OnAccountChangeListener{

    private Context mContext;
    private CloudProvider mCloudProvider;
    private CloudAccount[] mAccounts;
    private ItemInteractionListener mItemInteractionListener;

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnLongClickListener {

        private ItemInteractionListener mListener;
        ImageView mAvatar;
        TextView mUsername;
        TextView mAccountType;

        public ViewHolder(View itemView, ItemInteractionListener listener) {
            super(itemView);
            mListener = listener;
            mAvatar = (ImageView) itemView.findViewById(R.id.avatar_image_view);
            mUsername = (TextView) itemView.findViewById(R.id.username_text_view);
            mAccountType = (TextView) itemView.findViewById(R.id.account_type_text_view);
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

    public AccountAdapter(Context context) {
        mContext = context;
        mCloudProvider = CloudProvider.getInstance(mContext);
        mCloudProvider.setAccountChangeListener(this);
        mAccounts = mCloudProvider.getCloudAccounts();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View view = inflater.inflate(R.layout.list_item_account, parent, false);
        return new ViewHolder(view, mItemInteractionListener);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        CloudAccount account = mAccounts[position];
        // download avatar for user
        Picasso.with(mContext)
                .load(account.getUser().avatarUrl)
                .placeholder(R.drawable.account_circle_grey)
                .error(R.drawable.account_circle_grey)
                .into(holder.mAvatar);
        holder.mUsername.setText(account.getUser().name);
        String cloudType = account.type;
        holder.mAccountType.setText(cloudType != null ? cloudType : "Unknown Cloud");
    }

    @Override
    public int getItemCount() {
        return mAccounts.length;
    }

    @Override
    public void onAccountRemoved(CloudAccount account) {
        for (int i = 0; i < getItemCount(); i++) {
            if (account.id.equals(getItem(i).id)) notifyItemRemoved(i);
        }
        mAccounts = mCloudProvider.getCloudAccounts();
    }

    @Override
    public void onAccountAdded(CloudAccount account) {
        notifyItemInserted(getItemCount());
        mAccounts = mCloudProvider.getCloudAccounts();
    }

    public CloudAccount getItem(int position) {
        return mAccounts[position];
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
        void onMoreButtonClick(View view, int position);
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
