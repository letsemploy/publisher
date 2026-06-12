package io.letsemploy.publisher.web.form;

import java.util.ArrayList;
import java.util.List;

public class UserForm {
    private String displayName;
    private String externalSubject;
    private String email;
    private List<String> tenantIds = new ArrayList<>();

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getExternalSubject() { return externalSubject; }
    public void setExternalSubject(String externalSubject) { this.externalSubject = externalSubject; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public List<String> getTenantIds() { return tenantIds; }
    public void setTenantIds(List<String> tenantIds) { this.tenantIds = tenantIds; }
}
