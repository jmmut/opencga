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

package org.opencb.opencga.catalog.models;

import java.util.*;

/**
 * Created by jacobo on 11/09/14.
 */
public class Sample {

    private int id;
    private String name;
    private String source;
    private int individualId;
    private String description;

    private List<AnnotationSet> annotationSets;

    private Map<String, Object> attributes;

    public Sample() {
    }

    public Sample(int id, String name, String source, int individualId, String description) {
        this(id, name, source, individualId, description, new LinkedList<AnnotationSet>(), new HashMap<String, Object>());
    }

    public Sample(int id, String name, String source, int individualId, String description,
                  List<AnnotationSet> annotationSets, Map<String, Object> attributes) {
        this.id = id;
        this.name = name;
        this.source = source;
        this.individualId = individualId;
        this.description = description;
        this.annotationSets = annotationSets;
        this.attributes = attributes;
    }

    @Override
    public String toString() {
        return "Sample{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", source='" + source + '\'' +
                ", individualId=" + individualId +
                ", description='" + description + '\'' +
                ", annotationSets=" + annotationSets +
                ", attributes=" + attributes +
                '}';
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getIndividualId() {
        return individualId;
    }

    public void setIndividualId(int individualId) {
        this.individualId = individualId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<AnnotationSet> getAnnotationSets() {
        return annotationSets;
    }

    public void setAnnotationSets(List<AnnotationSet> annotationSets) {
        this.annotationSets = annotationSets;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
