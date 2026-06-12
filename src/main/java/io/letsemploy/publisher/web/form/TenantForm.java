package io.letsemploy.publisher.web.form;

import java.util.ArrayList;
import java.util.List;

public class TenantForm {
    private String name;
    private String slug;
    private String description;
    private List<String> userIds = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getUserIds() { return userIds; }
    public void setUserIds(List<String> userIds) { this.userIds = userIds; }
}
