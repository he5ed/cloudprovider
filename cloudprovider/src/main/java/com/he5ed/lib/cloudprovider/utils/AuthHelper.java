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

package com.he5ed.lib.cloudprovider.utils;

import android.net.Uri;

import com.he5ed.lib.cloudprovider.models.User;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.Map;

/**
 * @hide
 */
public class AuthHelper {

    /**
     * Return cloud account type in human readable string
     *
     * @param cloudApi type of cloud account
     * @return String
     */
    public static String getCloudApi(String cloudApi) {
        // return cloud API class name
        return cloudApi;
    }

    /**
     * Build authorization url base on type of cloud service
     *
     * @param cloudApi type of cloud account
     * @return Uri
     */
    public static Uri buildAuthUri(String cloudApi, String stateString) {
        // use reflection for flexibility
        try {
            Class<?> clazz = Class.forName(cloudApi);
            Method buildAuthUri = clazz.getMethod("buildAuthUri", String.class);
            return (Uri) buildAuthUri.invoke(null, stateString);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get the API redirect uri
     *
     * @param cloudApi type of cloud account
     * @return Uri
     */
    public static Uri getRedirectUri(String cloudApi) {
        // use reflection for flexibility
        try {
            Class clazz = Class.forName(cloudApi);
            Field redirectUrl = clazz.getField("REDIRECT_URL");
            return Uri.parse((String) redirectUrl.get(null));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get the API access token request uri
     *
     * @param cloudApi type of cloud account
     * @return Uri
     */
    public static Uri getAccessTokenUri(String cloudApi) throws MalformedURLException {
        // use reflection for flexibility
        try {
            Class clazz = Class.forName(cloudApi);
            Field tokenUrl = clazz.getField("TOKEN_URL");
            return Uri.parse((String) tokenUrl.get(null));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        throw new MalformedURLException("No url or malformed url for request!");
    }

    /**
     * Get parameters to be passed to Volley request to get access token
     *
     * @param cloudApi type of cloud account
     * @param authCode code from authorization process
     * @return Map
     */
    public static RequestBody getAccessTokenBody(String cloudApi, String authCode) {
        // use reflection for flexibility
        try {
            Class<?> clazz = Class.forName(cloudApi);
            Method getAccessTokenBody = clazz.getMethod("getAccessTokenBody", String.class);
            return (RequestBody) getAccessTokenBody.invoke(null, authCode);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Extract access token from the JSONObject
     *
     * @param cloudApi type of cloud account
     * @param jsonObject JSONObject that contain access token
     * @return Map
     * @throws JSONException
     */
    public static Map<String, String> extractAccessToken(String cloudApi, JSONObject jsonObject)
            throws JSONException {
        // use reflection for flexibility
        try {
            Class<?> clazz = Class.forName(cloudApi);
            Method extractAccessToken = clazz.getMethod("extractAccessToken", JSONObject.class);
            return (Map) extractAccessToken.invoke(null, jsonObject);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            // catch exception throw by class method
            if (e.getCause() instanceof JSONException) {
                throw new JSONException(e.getMessage());
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Create OkHttp request to get user information
     *
     * @param cloudApi type of cloud account
     * @param accessToken access token for authorization
     * @return Request
     */
    public static Request getUserInfoRequest(String cloudApi, String accessToken) {
        // use reflection for flexibility
        try {
            Class<?> clazz = Class.forName(cloudApi);
            Method getUserInfoRequest = clazz.getMethod("getUserInfoRequest", String.class);
            return (Request) getUserInfoRequest.invoke(null, accessToken);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Extract user information from the JSONObject
     *
     * @param cloudApi type of cloud account
     * @param jsonObject JSONObject that contain user information
     * @return User
     * @throws JSONException
     */
    public static User extractUser(String cloudApi, JSONObject jsonObject) throws JSONException {
        // use reflection for flexibility
        try {
            Class<?> clazz = Class.forName(cloudApi);
            Method extractUser = clazz.getMethod("extractUser", JSONObject.class);
            return (User) extractUser.invoke(null, jsonObject);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            // catch exception throw by class method
            if (e.getCause() instanceof JSONException) {
                throw new JSONException(e.getMessage());
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return null;
    }


}
