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

package com.he5ed.lib.cloudprovider.apis;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.he5ed.lib.cloudprovider.exceptions.RequestFailException;
import com.he5ed.lib.cloudprovider.models.CFile;
import com.he5ed.lib.cloudprovider.models.CFolder;
import com.squareup.okhttp.Callback;

import java.io.File;
import java.util.List;

/**
 * Base class for implementing cloud API functionality. All cloud API that
 * would like to be added into the {@link com.he5ed.lib.cloudprovider.CloudProvider}
 * must extend this class.
 * <p>
 * There are additional fields and class methods that are not captured by
 * extending this class, which the developer must manually added in. All these
 * additional fields and methods will be accessed by reflection and exceptions
 * will be thrown if they are not found. The additional requirements are listed
 * below:
 * <p>
 * Required public fields:
 * <ul>
 *     <li>public static String CLIENT_SECRET
 *     <li>public static String REDIRECT_URL
 *     <li>public static final String TOKEN_URL
 *     <li>public static boolean ENABLE_API
 *     <li>public static final String NAME
 *     <li>public static final int ICON_RESOURCE
 * </ul>
 * <p>
 * Required class methods:
 * <ul>
 *     <li>Build and return authorization uri that bring user to the login
 *     page<p>
 *         <pre>public static Uri buildAuthUri(String stateString) {
 *             // implementation codes here
 *         }</pre>
 *     <li>Return com.squareup.okhttp.RequestBody that contains payload to
 *     request for access token<p>
 *         <pre>public static RequestBody getAccessTokenBody(String authCode) {
 *             // implementation codes here
 *         }</pre>
 *     <li>Extract access token information from the JSONObject. The returned
 *     Map must contain at least the key and value pair of
 *     Authenticator.KEY_ACCESS_TOKEN and the valid access token string.<p>
 *         <pre>public static Map<String, String> extractAccessToken(JSONObject jsonObject) throws JSONException {
 *             // implementation codes here
 *         }</pre>
 *     <li>Return com.squareup.okhttp.Request to request for user information
 *     <p>
 *         <pre>public static Request getUserInfoRequest(String accessToken) {
 *             // implementation codes here
 *         }</pre>
 *     <li>Extract user information from the JSONObject<p>
 *         <pre>public static User extractUser(JSONObject jsonObject) throws JSONException {
 *             // implementation codes here
 *         }</pre>
 * </ul>
 */
public abstract class BaseApi {

    /**
     * Prepare API
     *
     * @param prepareListener listens to preparation result
     */
    public abstract void prepareApi(OnPrepareListener prepareListener);

    /**
     * Logout user and revoke all access token
     */
    public abstract void logout(@NonNull Callback callback);

    /**
     *  Get root folder
     *
     * @return CFolder as root
     */
    public abstract CFolder getRoot();

    /**
     *  Async get file information from id
     *
     * @param folderId to retrieve the folder details
     * @param callback to return the request result back
     */
    public abstract void getFolderInfoAsync(@NonNull String folderId, IApiCallback callback);

    /**
     * Async get folder items
     *
     * @param folder to explore
     * @param callback to return the request result back
     */
    public abstract void exploreFolderAsync(@NonNull CFolder folder, int offset, ApiCallback callback);

    /**
     * Async create folder
     *
     * @param name of the new folder
     * @param parent folder that the new folder will reside, use null for root folder
     * @param callback to return the request result back
     */
    public abstract void createFolderAsync(@NonNull String name, @Nullable CFolder parent, ApiCallback callback);

    /**
     * Async rename folder
     *
     * @param folder to be renamed
     * @param name for the folder
     * @param callback to return the request result back
     */
    public abstract void renameFolderAsync(@NonNull CFolder folder, String name, ApiCallback callback);

    /**
     * Async move folder
     *
     * @param folder to be moved
     * @param parent folder that will contain the folder, use null for root
     * @param callback to return the request result back
     */
    public abstract void moveFolderAsync(@NonNull CFolder folder, @Nullable CFolder parent, ApiCallback callback);

    /**
     * Async delete folder
     *
     * @param folder to be deleted
     * @param callback to return the request result back
     */
    public abstract void deleteFolderAsync(@NonNull CFolder folder, ApiCallback callback);

    /**
     * Async get file information from id
     *
     * @param fileId to retrieve the file details
     * @param callback to return the request result back
     */
    public abstract void getFileInfoAsync(@NonNull String fileId, ApiCallback callback);

    /**
     * Async upload file
     * If file with the same filename already exist an exception will be thrown
     *
     * @param file to upload
     * @param parent folder that the uploaded file will reside, use null for root
     * @param callback to return the request result back
     */
    public abstract void uploadFileAsync(@NonNull File file, @Nullable CFolder parent, ApiCallback callback);

    /**
     * Async update file
     * If no matching file was found the file will be uploaded
     *
     * @param file to be updated
     * @param content folder that the uploaded file will reside
     * @param callback to return the request result back
     */
    public abstract void updateFileAsync(@NonNull CFile file, File content, ApiCallback callback);

    /**
     * Async rename file
     *
     * @param file to be renamed
     * @param name for the file
     * @param callback to return the request result back
     */
    public abstract void renameFileAsync(@NonNull CFile file, String name, ApiCallback callback);

    /**
     * Async move file to other folder
     *
     * @param file to be moved
     * @param  folder folder that will contain the file, use null for root
     * @param callback to return the request result back
     */
    public abstract void moveFileAsync(@NonNull CFile file, @Nullable CFolder folder, ApiCallback callback);


    /**
     * Async download file
     * Files saved in temp folder under CloudProvider dir
     * Developer must move the file to permanent storage location after acquired the file
     *
     * @param file to be downloaded
     * @param filename for the downloaded file, use null for original filename
     * @param callback to return the request result back
     */
    public abstract void downloadFileAsync(@NonNull CFile file, @Nullable String filename, ApiCallback callback);

    /**
     * Async delete file
     *
     * @param file to be deleted
     * @param callback to return the request result back
     */
    public abstract void deleteFileAsync(@NonNull CFile file, ApiCallback callback);

    /**
     * Async search the cloud for files
     *
     * @param keyword to search for
     * @param folder where the search is looking at
     * @param callback to return the request result back
     */
    public abstract void searchFileAsync(@NonNull String keyword, CFolder folder, ApiCallback callback);

    /**
     * Async search the cloud for folders
     *
     * @param keyword to search for
     * @param folder where the search is looking at
     * @param callback to return the request result back
     */
    public abstract void searchFolderAsync(@NonNull String keyword, CFolder folder, ApiCallback callback);

    /**
     * Async search the cloud for all contents
     *
     * @param keyword to search for
     * @param folder where the search is looking at
     * @param callback to return the request result back
     */
    public abstract void searchAsync(@NonNull String keyword, CFolder folder, ApiCallback callback)
    ;

    /**
     * Async get thumbnail from the file that if applicable
     * Thumbnails saved in cache folder under CloudProvider dir, file will be clear by system
     *
     * @param file to retrieve the thumbnail
     * @param callback to return the request result back
     */
    public abstract void getThumbnailAsync(@NonNull CFile file, ApiCallback callback);

    /**
     *  Get file information from id
     *
     * @param folderId to retrieve the folder details
     * @return CFolder
     * @throws RequestFailException
     */
    public abstract CFolder getFolderInfo(@NonNull String folderId) throws RequestFailException;

    /**
     * Get folder items
     *
     * @param folder to explore
     * @return List that contains CFile and CFolder
     * @throws RequestFailException that content various error types
     */
    public abstract List<Object> exploreFolder(@NonNull CFolder folder, int offset) throws RequestFailException;

    /**
     * Create folder
     *
     * @param name of the new folder
     * @param parent folder that the new folder will reside, use null for root folder
     * @return CFolder
     * @throws RequestFailException that content various error types
     */
    public abstract CFolder createFolder(@NonNull String name, @Nullable CFolder parent) throws RequestFailException;

    /**
     * Rename folder
     *
     * @param name for the folder
     * @param folder to be renamed
     * @return CFolder
     * @throws RequestFailException that content various error types
     */
    public abstract CFolder renameFolder(@NonNull CFolder folder, String name) throws RequestFailException;

    /**
     * Move folder
     *
     * @param folder to be moved
     * @param parent folder that will contain the folder, use null for root
     * @return CFolder
     * @throws RequestFailException that content various error types
     */
    public abstract CFolder moveFolder(@NonNull CFolder folder, @Nullable CFolder parent) throws RequestFailException;

    /**
     * Delete folder
     *
     * @param folder to be deleted
     * @throws RequestFailException that content various error types
     */
    public abstract void deleteFolder(@NonNull CFolder folder) throws RequestFailException;

    /**
     *  Get file information from id
     *
     * @param fileId to retrieve the file details
     * @return CFile
     * @throws RequestFailException
     */
    public abstract CFile getFileInfo(@NonNull String fileId) throws RequestFailException;

    /**
     * Upload file
     * If file with the same filename already exist an exception will be thrown
     *
     * @param file to upload
     * @param parent folder that the uploaded file will reside, use null for root
     * @return CFile
     * @throws RequestFailException
     */
    public abstract CFile uploadFile(@NonNull File file, @Nullable CFolder parent) throws RequestFailException;

    /**
     * Update file
     * If no matching file was found the file will be uploaded
     *
     * @param file to be updated
     * @param content folder that the uploaded file will reside
     * @return CFile
     * @throws RequestFailException
     */
    public abstract CFile updateFile(@NonNull CFile file, File content) throws RequestFailException;

    /**
     * Rename file
     *
     * @param name for the file
     * @param file to be renamed
     * @return CFile
     * @return RequestFailException that content various error types
     */
    public abstract CFile renameFile(@NonNull CFile file, String name) throws RequestFailException;

    /**
     * Move file to other folder
     *
     * @param file to be moved
     * @param  folder folder that will contain the file, use null for root
     * @return CFile
     * @return RequestFailException that content various error types
     */
    public abstract CFile moveFile(@NonNull CFile file, @Nullable CFolder folder) throws RequestFailException;


    /**
     * Download file
     * Files saved in temp folder under CloudProvider dir
     * Developer must move the file to permanent storage location after acquired the file
     *
     * @param file to be downloaded
     * @param filename for the downloaded file, use null for original filename
     * @return File
     * @throws RequestFailException
     */
    public abstract File downloadFile(@NonNull CFile file, @Nullable String filename) throws RequestFailException;

    /**
     * Delete file
     *
     * @param file to be deleted
     * @throws RequestFailException
     */
    public abstract void deleteFile(@NonNull CFile file) throws RequestFailException;

    /**
     * Search the cloud for files
     *
     * @param keyword to search for
     * @param folder where the search is looking at
     * @return  list of files that match search criteria
     * @throws RequestFailException
     */
    public abstract List<CFile> searchFile(@NonNull String keyword, CFolder folder) throws RequestFailException;

    /**
     * Search the cloud for folders
     *
     * @param keyword to search for
     * @param folder where the search is looking at
     * @return list of folders that match search criteria
     * @throws RequestFailException
     */
    public abstract List<CFolder> searchFolder(@NonNull String keyword, CFolder folder) throws RequestFailException;

    /**
     * Search the cloud for all contents
     *
     * @param keyword to search for
     * @param folder where the search is looking at
     * @return list of files and folders that match search criteria
     * @throws RequestFailException
     */
    public abstract List<Object> search(@NonNull String keyword, CFolder folder) throws RequestFailException;

    /**
     * Get thumbnail from the file that if applicable
     * Thumbnails saved in cache folder under CloudProvider dir, file will be clear by system
     *
     * @param file to retrieve the thumbnail
     * @return File in binary format
     * @throws RequestFailException
     */
    public abstract File getThumbnail(@NonNull CFile file) throws RequestFailException;

    /**
     * Listen to the API preparation event
     */
    public interface OnPrepareListener {
        /**
         * API has been successfully prepared
         */
        void onPrepareSuccessful();

        /**
         * Failed to prepare API
         *
         * @param e exception details to pass on to interface
         */
        void onPrepareFail(Exception e);
    }
}
