package org.letsemploy.ojobpub_publisher.user;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Account pictures (spec 7.27): served from here, so a page never names another
 * origin (spec 7.2), and changed from Settings.
 *
 * <p>The address carries the picture's version, so it is cached for good and a
 * new picture is a new address. Cached privately: who may see a picture depends
 * on who asks.
 */
@Controller
public class PictureController {

    private final PictureService pictures;
    private final CurrentUserService currentUserService;

    public PictureController(PictureService pictures, CurrentUserService currentUserService) {
        this.pictures = pictures;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/pictures/{userId}")
    public ResponseEntity<byte[]> picture(@PathVariable UUID userId) {
        UserPicture picture = pictures.find(userId, currentUserService.current());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(picture.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(picture.getContent());
    }

    @PostMapping("/settings/picture")
    public String upload(@RequestParam(required = false) MultipartFile picture, RedirectAttributes flash) {
        try {
            pictures.upload(self(), picture == null ? InputStream.nullInputStream() : picture.getInputStream());
        } catch (PictureProcessor.Rejected e) {
            flash.addFlashAttribute("errorMsg", e.reason().key());
            return "redirect:/settings";
        } catch (IOException e) {
            flash.addFlashAttribute("errorMsg", PictureProcessor.Reason.UNREADABLE.key());
            return "redirect:/settings";
        }
        flash.addFlashAttribute("successMsg", "settings.picture.saved");
        return "redirect:/settings";
    }

    @PostMapping("/settings/picture/remove")
    public String remove(RedirectAttributes flash) {
        pictures.remove(self());
        flash.addFlashAttribute("successMsg", "settings.picture.removed");
        return "redirect:/settings";
    }

    /** One's own, never while viewing as someone: ImpersonationGuard refuses these POSTs first (spec 2.9). */
    private Actor self() {
        if (currentUserService.isImpersonating()) {
            throw new NotFoundException("Not found.");
        }
        return currentUserService.current();
    }
}
