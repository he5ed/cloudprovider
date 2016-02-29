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

import com.he5ed.lib.cloudprovider.exceptions.RequestFailException;
import com.squareup.okhttp.Request;

import java.util.List;

/**
 * Callback interface for delivering http responses
 *
 * @hide
 */
public interface IApiCallback {

    /**
     * Callback method that the request has failed and an exception has occurred with the
     * provided error code and optional user-readable message.
     *
     * @param request that initiate this response
     * @param exception due to http request failure
     */
    void onRequestFailure(Request request, RequestFailException exception);

    /**
     * Callback method that the request is successful and containing the optional parsed
     * result in the form of object list.
     *
     * @param request initiate this response
     * @param items of any type. Must type test to ensure the returned object is correct
     *              before use it to avoid type cast exception. Might be null if no result
     *              return from a successful request.
     */
    void onReceiveItems(Request request, List items);
}
