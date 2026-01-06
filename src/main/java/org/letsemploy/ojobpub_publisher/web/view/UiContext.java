package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;
import lombok.Value;

/** Everything the shell needs: identity, employer context, navigation, theme (spec 7.3). */
@Value
public class UiContext {
    String userName;
    boolean admin;
    boolean devMode;
    String theme;
    Ref activeEmployer;
    List<Ref> employers;
    List<NavItem> navItems;
    List<String> languages;
}
