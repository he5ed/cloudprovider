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

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * Value type that represents a cloud file in the {@link com.he5ed.lib.cloudprovider.CloudProvider}.
 * This object is {@link Parcelable} so that it can be passed around in {@link android.os.Bundle}
 * mapping. It is also {@link Comparable} so that it can be sorted.
 */
public class CFile implements Parcelable, Comparable {
    /**
     * Map key for cloud file id string
     */
    public static final String ID = "id";

    /**
     * Map key for cloud file name. File name include extension.
     */
    public static final String NAME = "name";

    /**
     * Map key for cloud file path in reference to the cloud storage root directory.
     */
    public static final String PATH = "path";

    /**
     * Map key for the date format that the cloud server is using. Refer to the cloud service
     * provider for details. Failure to obtain the correct date format will cause all file to
     * lose the data information.
     */
    public static final String DATE_FORMAT = "dateFormat";

    /**
     * Map key for the date this cloud file is created. Some cloud service may not provide this
     * information
     */
    public static final String CREATED = "created";

    /**
     * Map key for the date this cloud file is last modified.
     */
    public static final String MODIFIED = "modified";

    private String mId;
    private String mName;
    private String mPath;
    private String mType;
    private Date mCreated;
    private Date mModified;
    private boolean mOffline;
    private boolean mNew;
    private long mSize;

    /**
     * Create file from the information in the map
     *
     * @param map that contain key value pairs of the cloud file information
     */
    public CFile(Map<String, Object> map) {
        // create empty CFile object
        if (map == null)
            return;

        try {
            mId = (String) map.get(ID);
            mName = (String) map.get(NAME);
            mPath = (String) map.get(PATH);
            // format date
            SimpleDateFormat df = new SimpleDateFormat((String) map.get(DATE_FORMAT));
            String created = (String) map.get(CREATED);
            String modified = (String) map.get(MODIFIED);
            if (!TextUtils.isEmpty(created))
                mCreated = df.parse(created);
            if (!TextUtils.isEmpty(modified))
                mModified = df.parse(modified);
        } catch (ParseException e) {
            e.printStackTrace();
            mCreated = mModified = new Date();
        }
    }

    public String getId() {
        return mId;
    }

    public void setId(String id) {
        mId = id;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public String getPath() {
        return mPath;
    }

    public void setPath(String path) {
        mPath = path;
    }

    public String getType() {
        return mType;
    }

    public void setType(String type) {
        mType = type;
    }

    public Date getCreated() {
        return mCreated;
    }

    public void setCreated(Date created) {
        mCreated = created;
    }

    public Date getModified() {
        return mModified;
    }

    public void setModified(Date modified) {
        mModified = modified;
    }

    public long getSize() {
        return mSize;
    }

    public void setSize(long size) {
        mSize = size;
    }

    /**
     * @hide
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @hide
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
        dest.writeString(mName);
        dest.writeString(mPath);
        dest.writeString(mType);
        dest.writeLong(mCreated != null ? mCreated.getTime() : -1);
        dest.writeLong(mModified != null ? mModified.getTime() : -1);
        dest.writeByte(mOffline ? (byte) 1 : (byte) 0);
        dest.writeByte(mNew ? (byte) 1 : (byte) 0);
    }

    /**
     * @hide
     */
    protected CFile(Parcel in) {
        mId = in.readString();
        mName = in.readString();
        mPath = in.readString();
        mType = in.readString();
        long tmpMCreated = in.readLong();
        mCreated = tmpMCreated == -1 ? null : new Date(tmpMCreated);
        long tmpMModified = in.readLong();
        mModified = tmpMModified == -1 ? null : new Date(tmpMModified);
        mOffline = in.readByte() != 0;
        mNew = in.readByte() != 0;
    }

    /**
     * @hide
     */
    public static final Parcelable.Creator<CFile> CREATOR = new Parcelable.Creator<CFile>() {
        @Override
        public CFile createFromParcel(Parcel source) {
            return new CFile(source);
        }

        @Override
        public CFile[] newArray(int size) {
            return new CFile[size];
        }
    };

    /**
     * @hide
     */
    @Override
    public int compareTo(Object another) {
        if (another instanceof CFolder) {
            // folder always on top
            return 1;
        } else if (another instanceof CFile) {
            // check for alphanumeric order in case insensitive
            String anotherName = ((CFile) another).getName();
            return mName.toLowerCase().compareTo(anotherName.toLowerCase());
        } else {
            // if same or just ignore comparison
            return 0;
        }
    }
}
