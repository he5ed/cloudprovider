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

package com.he5ed.lib.cloudprovider.exceptions;

public class InvalidUrlException extends Exception {
    public InvalidUrlException() {
        super();
    }

    public InvalidUrlException(String detailMessage) {
        super(detailMessage);
    }

    public InvalidUrlException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public InvalidUrlException(Throwable throwable) {
        super(throwable);
    }
}
