/*
 * Copyright 2015 OpenCB
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

package org.opencb.opencga.catalog.exceptions;

/**
 * Created by imedina on 11/09/14.
 */
public class CatalogDBException extends CatalogException {

    public CatalogDBException(String msg) {
        super(msg);
    }

    public CatalogDBException(String message, Throwable cause) {
        super(message, cause);
    }

    public CatalogDBException(Throwable cause) {
        super(cause);
    }

    public static CatalogDBException idNotFound(String name, String id) {
        return new CatalogDBException(name + " { id: \"" + id + "\" } not found.");
    }

    public static CatalogDBException idNotFound(String name, int id) {
        return new CatalogDBException(name + " { id: " + id + " } not found.");
    }

    public static CatalogDBException alreadyExists(String name, String key, String value) {
        return new CatalogDBException(name + " { " + key + ":\"" + value + "\"} already exists");
    }

    public static CatalogDBException alreadyExists(String name, int id) {
        return new CatalogDBException(name + " { id:" + id + "} already exists");
    }
}
