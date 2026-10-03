package org.letsemploy.ojobpub_publisher.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.letsemploy.ojobpub_publisher.security.Impersonation;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account pictures on screen (spec 7.27): uploaded from Settings, served from the
 * application, shown in the user menu, on People and on Users - and initials
 * where there is none.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class PictureScreenTest {

    /** The seeded editor of Acme, and Acme itself. */
    private static final UUID MEMBER = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private UserPictureRepo pictureRepo;
    @Autowired
    private PictureService pictures;

    @MockitoBean
    private CurrentUserService currentUserService;

    private UserEntity person;
    private byte[] png;

    @BeforeEach
    void aPerson() throws IOException {
        UserEntity user = new UserEntity();
        user.setIssuer("test");
        user.setSubject("picture-" + UUID.randomUUID());
        user.setEmail("pia@example.com");
        user.setDisplayName("Pia Picture");
        person = userRepo.save(user);
        actAs(Actor.user(person.getId(), "Pia Picture", person.getEmail(), false, Map.of()));
        png = PictureProcessorTest.encode(new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB), "png");
    }

    private void actAs(Actor actor) {
        given(currentUserService.isDevMode()).willReturn(true);
        given(currentUserService.current()).willReturn(actor);
        given(currentUserService.realActor()).willReturn(actor);
    }

    private String render(String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void anUploadIsShownInSettingsAndTheMenu_andServedFromHere() throws Exception {
        assertThat(render("/settings")).contains(">PP<").doesNotContain("/pictures/");

        mvc.perform(multipart("/settings/picture").file(new MockMultipartFile("picture", "me.png", "image/png", png)))
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attribute("successMsg", "settings.picture.saved"));

        String url = pictures.urlFor(person.getId()).orElseThrow();
        String page = render("/settings");
        // The menu and the Settings preview, both from this application.
        assertThat(page.split(java.util.regex.Pattern.quote("src=\"" + url.replace("&", "&amp;") + "\""), -1))
                .hasSizeGreaterThanOrEqualTo(3);
        assertThat(page).contains("/settings/picture/remove");

        byte[] served = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("private"),
                        org.hamcrest.Matchers.containsString("immutable"))))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(javax.imageio.ImageIO.read(new ByteArrayInputStream(served)).getWidth())
                .isEqualTo(PictureProcessor.SIZE);
    }

    @Test
    void whatIsNotAPictureIsRefusedWithAReason() throws Exception {
        mvc.perform(multipart("/settings/picture").file(new MockMultipartFile("picture", "cv.pdf",
                        "application/pdf", "%PDF-1.7 not a picture".getBytes(StandardCharsets.US_ASCII))))
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attribute("errorMsg", "validation.picture.unsupported"));
        mvc.perform(multipart("/settings/picture"))
                .andExpect(flash().attribute("errorMsg", "validation.picture.empty"));
        assertThat(pictureRepo.existsById(person.getId())).isFalse();
    }

    @Test
    void removing() throws Exception {
        pictures.upload(currentUserService.current(), new ByteArrayInputStream(png));
        mvc.perform(post("/settings/picture/remove"))
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attribute("successMsg", "settings.picture.removed"));
        assertThat(pictureRepo.existsById(person.getId())).isFalse();
    }

    @Test
    void aStrangersPictureIsNotFound() throws Exception {
        pictures.upload(Actor.user(MEMBER, "Mara Member", null, false, Map.of(ACME, MembershipRole.EDITOR)),
                new ByteArrayInputStream(png));
        mvc.perform(get(pictures.urlFor(MEMBER).orElseThrow())).andExpect(status().isNotFound());
    }

    @Test
    void notWhileViewingAsSomeone() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(Impersonation.class.getName(), new Impersonation.State(UUID.randomUUID(), person.getId()));
        given(currentUserService.isImpersonating()).willReturn(true);

        mvc.perform(multipart("/settings/picture").file(new MockMultipartFile("picture", "me.png", "image/png", png))
                        .session(session))
                .andExpect(status().isSeeOther());
        assertThat(pictureRepo.existsById(person.getId())).isFalse();
    }

    @Test
    void coMembersSeeEachOthersPicturesOnPeople() throws Exception {
        pictures.upload(Actor.user(MEMBER, "Mara Member", null, false, Map.of(ACME, MembershipRole.EDITOR)),
                new ByteArrayInputStream(png));
        actAs(Actor.user(person.getId(), "Pia Picture", person.getEmail(), false, Map.of(ACME, MembershipRole.OWNER)));

        String page = render("/employers/" + ACME + "/people");

        assertThat(page).contains("src=\"" + pictures.urlFor(MEMBER).orElseThrow().replace("&", "&amp;") + "\"");
        // The seeded admin owner has none: initials instead.
        assertThat(page).contains(">D<");
    }

    @Test
    void adminsSeePicturesOnUsers() throws Exception {
        pictures.upload(currentUserService.current(), new ByteArrayInputStream(png));
        actAs(Actor.user(UUID.randomUUID(), "Ada Admin", null, true, Map.of()));

        assertThat(render("/users?q=Pia")).contains("src=\"" + pictures.urlFor(person.getId()).orElseThrow() + "\"");
    }
}
