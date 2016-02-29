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

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.he5ed.lib.cloudprovider.exceptions.RequestFailException;
import com.squareup.okhttp.Request;

import java.util.List;

/**
 * Callback implementation for delivering http responses.
 * <p>
 * This implementation enable callback to happen on the Ui thread, so that the callback can
 * easily interact with the UI elements without causing exception. Do not run any
 * time-consuming operation inside any of these callback methods.
 */
public abstract class ApiCallback implements IApiCallback {

    /**
     * Public bundle to store additional payload
     */
    public Bundle bundle = new Bundle();

    private Handler mHandler = new Handler(Looper.getMainLooper());

    /**
     * Callback method that the request has failed and an exception has occurred with the
     * provided error code and optional user-readable message. This method run on UI thread.
     *
     * @param request that initiate this response
     * @param exception due to http request failure
     */
    public abstract void onUiRequestFailure(Request request, RequestFailException exception);

    /**
     * Callback method that the request is successful and containing the optional parsed
     * result in the form of object list. This method run on UI thread.
     *
     * @param request initiate this response
     * @param items of any type. Must type test to ensure the returned object is correct
     *              before use it to avoid type cast exception. Might be null if no result
     *              return from a successful request.
     */
    public abstract void onUiReceiveItems(Request request, List items);

    @Override
    public void onRequestFailure(final Request request, final RequestFailException exception) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onUiRequestFailure(request, exception);
            }
        });
    }

    @Override
    public void onReceiveItems(final Request request, final List items) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onUiReceiveItems(request, items);
            }
        });
    }

    /**
     * Runs the specified action on the UI thread.
     *
     * @param action the action to run on the UI thread
     */
    private void runOnUiThread(Runnable action) {
        mHandler.post(action);
    }
}
