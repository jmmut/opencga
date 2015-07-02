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

package org.opencb.opencga.storage.core.config;

import java.util.List;
import java.util.Map;

/**
 * Created by imedina on 01/05/15.
 */
public class DatabaseCredentials {

    /**
     * host attribute includes port with this format 'host[:port]'
     */
    private List<String> hosts;

    private String user;
    private String password;

    /**
     * options parameter defines database-specific parameters
     */
//    private Map<String, String> options;


    public DatabaseCredentials() {

    }

    public DatabaseCredentials(List<String> hosts, String user, String password) {
        this.hosts = hosts;
        this.user = user;
        this.password = password;
    }

    public DatabaseCredentials(List<String> hosts, String user, String password, Map<String, String> options) {
        this.hosts = hosts;
        this.user = user;
        this.password = password;
//        this.options = options;
    }


    @Override
    public String toString() {
        return "DatabaseCredentials{" +
                "hosts=" + hosts +
                ", user='" + user + '\'' +
                ", password='" + password + '\'' +
//                ", options=" + options +
                '}';
    }


    public List<String> getHosts() {
        return hosts;
    }

    public void setHosts(List<String> hosts) {
        this.hosts = hosts;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

//    public Map<String, String> getOptions() {
//        return options;
//    }
//
//    public void setOptions(Map<String, String> options) {
//        this.options = options;
//    }

}
