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

package com.he5ed.lib.cloudprovider;

import com.he5ed.lib.cloudprovider.auth.Authenticator;
import com.he5ed.lib.cloudprovider.apis.BoxApi;
import com.he5ed.lib.cloudprovider.models.User;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@RunWith(PowerMockRunner.class)
public class BoxApiTest {

    private static final String TAG = "BoxApiTest";

    @Before
    public void setup() {
        // Mockito has a very convenient way to inject mocks by using the @Mock annotation. To
        // inject the mocks in the test the initMocks method needs to be called.
        MockitoAnnotations.initMocks(this);

        // setup dummy value for Box API
        BoxApi.CLIENT_ID = "dummy id";
        BoxApi.CLIENT_SECRET = "dummy secret";
        BoxApi.REDIRECT_URL = "https:www.example.com/redirect.html";
    }

    /**
     * Test to ensure that getAccessTokenBody not return null
     */
    @Test
    public void getAccessTokenBody_notNull() {
        assertNotNull(BoxApi.getAccessTokenBody("dummy state"));
    }

    /**
     * Test for extractAccessToken with exact match
     */
    @Test
    public void extractAccessToken_exactMatch() {
        // create expected result in Map
        Map<String, Object> jsonContent = new HashMap<>();
        jsonContent.put(Authenticator.KEY_ACCESS_TOKEN, "dummy access token");
        jsonContent.put(Authenticator.KEY_EXPIRY, "100");
        jsonContent.put(Authenticator.KEY_REFRESH_TOKEN, "dummy refresh token");
        // mock JSONObject
        JSONObject jsonObject = PowerMockito.mock(JSONObject.class);
        try {
            PowerMockito.when(jsonObject.getString("access_token")).thenReturn("dummy access token");
            PowerMockito.when(jsonObject.getLong("expires_in")).thenReturn(100L);
            PowerMockito.when(jsonObject.getString("refresh_token")).thenReturn("dummy refresh token");
        } catch (JSONException e) {
            e.printStackTrace();
            doThrow(new JSONException(e.getMessage()));
        }

        try {
            // ensure result match expectation
            assertEquals(jsonContent, BoxApi.extractAccessToken(jsonObject));
            // verify that the JSONObject method was called
            verify(jsonObject).getString("access_token");
            verify(jsonObject).getLong("expires_in");
            verify(jsonObject).getString("refresh_token");
        } catch (JSONException e) {
            e.printStackTrace();
            doThrow(new JSONException(e.getMessage()));
        }
    }

    /**
     * Test for extractUser with exact match
     */
    @Test
    public void extractUser_exactMatch() {
        // create expected User
        User user = new User();
        user.id = "dummy id";
        user.name = "John Smith";
        user.displayName = "John Smith";
        user.email = "john@smith.com";
        user.avatarUrl = null;

        // mock JSONObject
        JSONObject jsonObject = PowerMockito.mock(JSONObject.class);
        try {
            PowerMockito.when(jsonObject.getString("id")).thenReturn("dummy id");
            PowerMockito.when(jsonObject.getString("name")).thenReturn("John Smith");
            PowerMockito.when(jsonObject.getString("login")).thenReturn("john@smith.com");
            PowerMockito.when(jsonObject.getString("avatar_url")).thenReturn(null);
        } catch (JSONException e) {
            e.printStackTrace();
            doThrow(new JSONException(e.getMessage()));
        }

        try {
            // ensure result match expectation
            User actualUser = BoxApi.extractUser(jsonObject);
            assertEquals(user.id, actualUser.id);
            assertEquals(user.name, actualUser.name);
            assertEquals(user.displayName, actualUser.displayName);
            assertEquals(user.email, actualUser.email);
            assertEquals(user.avatarUrl, actualUser.avatarUrl);
            // verify that the JSONObject method was called
            verify(jsonObject).getString("id");
            verify(jsonObject, Mockito.atLeastOnce()).getString("name");
            verify(jsonObject).getString("login");
            verify(jsonObject).getString("avatar_url");
        } catch (JSONException e) {
            e.printStackTrace();
            doThrow(new JSONException(e.getMessage()));
        }
    }

}
