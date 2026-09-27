package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;
import lombok.Value;

/** Everything the shell needs: identity, employer context, navigation, theme (spec 7.3). */
@Value
public class UiContext {
    String userName;
    /** Acting with admin reach right now: an admin in admin mode (spec 2.10). */
    boolean admin;
    boolean devMode;
    String theme;
    Ref activeEmployer;
    List<Ref> employers;
    List<NavItem> navItems;
    List<String> languages;
    /** Drives the sidebar badge; 0 hides it (spec 7.3). */
    long pendingInvitations;
    /** The footer (spec 7.3): the running version, and where the source is. */
    String version;
    String projectUrl;
    /** Whom an admin is viewing the application as (spec 2.9), or null. */
    String viewingAsName;
    String viewingAsEmail;
    /** May switch admin mode on or off: an admin, acting as themselves (spec 2.10). */
    boolean adminModeAvailable;

    public boolean isViewingAs() {
        return viewingAsName != null;
    }
}
