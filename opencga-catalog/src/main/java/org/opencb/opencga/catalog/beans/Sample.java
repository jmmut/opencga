package org.opencb.opencga.catalog.beans;

import java.util.*;

/**
 * Created by jacobo on 11/09/14.
 */
public class Sample {

    private int id;
    private String name;
    private String source;
    private Individual individual;
    private String description;

    private List<AnnotationSet> annotationSets;

    private Map<String, Object> attributes;

    public Sample() {
    }

    public Sample(int id, String name, String source, Individual individual, String description) {
        this(id, name, source, individual, description, new LinkedList<AnnotationSet>(), new HashMap<String, Object>());
    }

    public Sample(int id, String name, String source, Individual individual, String description,
                  List<AnnotationSet> annotationSets, Map<String, Object> attributes) {
        this.id = id;
        this.name = name;
        this.source = source;
        this.individual = individual;
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
                ", individual=" + individual +
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

    public Individual getIndividual() {
        return individual;
    }

    public void setIndividual(Individual individual) {
        this.individual = individual;
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
