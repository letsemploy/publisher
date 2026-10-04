package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;

/** The settings page (spec 7.26): what is chosen, and what may be changed. */
public record SettingsView(
        /** Null: the browser's language. */
        String language,
        /** auto, light or dark. */
        String theme,
        /** Null: the server's zone. */
        String timeZone,
        boolean mailInvitations,
        /** Whether the weekly summary is mailed to them (spec 7.28). */
        boolean mailSummary,
        String name,
        String email,
        /** The current picture or initials (spec 7.27). */
        Avatar avatar,
        /** Whether the picture is one they uploaded, rather than the provider's or none. */
        boolean pictureUploaded,
        /** The largest file accepted, for the form to refuse before sending. */
        int pictureMaxBytes,
        /** A local account names itself; a provider's account is named by the provider. */
        boolean local,
        /** Whether invitation and summary mail can go out at all here. */
        boolean mailConfigured,
        List<String> languages,
        List<String> themes,
        List<String> zones,
        /** The zone timestamps use until one is chosen. */
        String serverZone) {
}
