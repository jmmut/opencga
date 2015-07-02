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

package org.opencb.opencga.catalog.db.api;

import org.opencb.datastore.core.QueryResult;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public abstract class CatalogDBAdaptor {

    public interface FilterOption {
        enum Type {
            /**
             * Accepts a list of comma separated numerical conditions, where the value must match in, at least, one of this.
             * The accepted operators are: [<, <=, >, >=, =, , !=]
             *
             * Example:
             *      getAllFiles( {diskUsage : "<200000" } )
             *      getAllFiles( {jobId : "32,33,34" } )
             */
            NUMERICAL,
            /**
             * Accepts a list of comma separated text conditions, where the value must match in, at least, one of this.
             * The accepted operators are: [<, <=, >, >=, =, , !=, ~, =~, !=~],
             * where [~,=~] implements a "LIKE" with regular expression and [!=~, !~] implements a "NOT LIKE"
             * and [<, <=, >, >=] are lexicographical operations
             *
             * Example:
             *      getAllFiles ( { bioformat : "VARIANT," } )
             *      getAllSamples ( { name : "~SAMP_00[0-9]*"} )
             */
            TEXT,
            /**
             * Accepts a boolean condition
             *
             * Example:
             *      getAllFiles ( { acl.userId : "user1", acl.write : "false" } )
             */
            BOOLEAN
        }
        String getKey();
        Type getType();
        String getDescription();
    }

    protected Logger logger;

    protected long startQuery(){
        return System.currentTimeMillis();
    }

    protected <T> QueryResult<T> endQuery(String queryId, long startTime, List<T> result) throws CatalogDBException {
        return endQuery(queryId, startTime, result, null, null);
    }

    protected <T> QueryResult<T> endQuery(String queryId, long startTime) throws CatalogDBException {
        return endQuery(queryId, startTime, Collections.<T>emptyList(), null, null);
    }

    protected <T> QueryResult<T> endQuery(String queryId, long startTime, QueryResult<T> result)
            throws CatalogDBException {
        long end = System.currentTimeMillis();
        result.setId(queryId);
        result.setDbTime((int)(end-startTime));
        if(result.getErrorMsg() != null && !result.getErrorMsg().isEmpty()){
            throw new CatalogDBException(result.getErrorMsg());
        }
        return result;
    }

    protected <T> QueryResult<T> endQuery(String queryId, long startTime, List<T> result,
                                          String errorMessage, String warnMessage) throws CatalogDBException {
        long end = System.currentTimeMillis();
        if(result == null){
            result = new LinkedList<>();
        }
        int numResults = result.size();
        QueryResult<T> queryResult = new QueryResult<>(queryId, (int) (end - startTime), numResults, numResults,
                warnMessage, errorMessage, result);
        if(errorMessage != null && !errorMessage.isEmpty()){
            throw new CatalogDBException(queryResult.getErrorMsg());
        }
        return queryResult;
    }

    protected void checkParameter(Object param, String name) throws CatalogDBException {
        if (param == null) {
            throw new CatalogDBException("Error: parameter '" + name + "' is null");
        }
        if(param instanceof String) {
            if(param.equals("") || param.equals("null")) {
                throw new CatalogDBException("Error: parameter '" + name + "' is empty or it values 'null");
            }
        }
    }

}
