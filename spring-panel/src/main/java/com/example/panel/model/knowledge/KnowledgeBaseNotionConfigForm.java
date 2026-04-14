package com.example.panel.model.knowledge;

public class KnowledgeBaseNotionConfigForm {

    private boolean enabled;
    private String sourceUrl;
    private String token;
    private String authors;
    private String titleProperty;
    private String authorProperty;
    private String summaryProperty;
    private String departmentProperty;
    private String articleTypeProperty;
    private String directionProperty;
    private String directionSubtypeProperty;
    private String statusProperty;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getAuthors() {
        return authors;
    }

    public void setAuthors(String authors) {
        this.authors = authors;
    }

    public String getTitleProperty() {
        return titleProperty;
    }

    public void setTitleProperty(String titleProperty) {
        this.titleProperty = titleProperty;
    }

    public String getAuthorProperty() {
        return authorProperty;
    }

    public void setAuthorProperty(String authorProperty) {
        this.authorProperty = authorProperty;
    }

    public String getSummaryProperty() {
        return summaryProperty;
    }

    public void setSummaryProperty(String summaryProperty) {
        this.summaryProperty = summaryProperty;
    }

    public String getDepartmentProperty() {
        return departmentProperty;
    }

    public void setDepartmentProperty(String departmentProperty) {
        this.departmentProperty = departmentProperty;
    }

    public String getArticleTypeProperty() {
        return articleTypeProperty;
    }

    public void setArticleTypeProperty(String articleTypeProperty) {
        this.articleTypeProperty = articleTypeProperty;
    }

    public String getDirectionProperty() {
        return directionProperty;
    }

    public void setDirectionProperty(String directionProperty) {
        this.directionProperty = directionProperty;
    }

    public String getDirectionSubtypeProperty() {
        return directionSubtypeProperty;
    }

    public void setDirectionSubtypeProperty(String directionSubtypeProperty) {
        this.directionSubtypeProperty = directionSubtypeProperty;
    }

    public String getStatusProperty() {
        return statusProperty;
    }

    public void setStatusProperty(String statusProperty) {
        this.statusProperty = statusProperty;
    }
}
