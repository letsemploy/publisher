package org.letsemploy.ojobpub_publisher.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditRepo;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.letsemploy.ojobpub_publisher.token.TokenScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account pictures (spec 7.27): one's own upload, the provider's copy, and who
 * may see whose - oneself, an admin, and the people one shares an employer with.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
@RecordApplicationEvents
@Transactional
class PictureServiceTest {

    /** The seeded editor of Acme, and Acme itself. */
    private static final UUID MEMBER = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");

    @Autowired
    private PictureService pictures;
    @Autowired
    private UserPictureRepo pictureRepo;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private AuditRepo auditRepo;
    @Autowired
    private ApplicationEvents events;

    private UserEntity person;
    private Actor self;
    private byte[] png;

    @BeforeEach
    void aPerson() throws IOException {
        UserEntity user = new UserEntity();
        user.setIssuer("test");
        user.setSubject("picture-" + UUID.randomUUID());
        user.setDisplayName("Pia Picture");
        person = userRepo.save(user);
        self = Actor.user(person.getId(), "Pia Picture", null, false, Map.of());
        png = PictureProcessorTest.encode(new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "png");
    }

    private void upload(Actor actor) {
        pictures.upload(actor, new ByteArrayInputStream(png));
    }

    @Test
    void anUploadIsStoredReencodedAndRecordedInOnesOwnLog() {
        upload(self);

        UserPicture stored = pictureRepo.findById(person.getId()).orElseThrow();
        assertThat(stored.getSource()).isEqualTo(UserPicture.Source.UPLOAD);
        assertThat(stored.getContentType()).isEqualTo("image/jpeg");
        assertThat(stored.getContent()).startsWith((byte) 0xFF, (byte) 0xD8);
        assertThat(pictures.hasUpload(person.getId())).isTrue();
        assertThat(pictures.urlFor(person.getId())).hasValueSatisfying(url ->
                assertThat(url).startsWith("/pictures/" + person.getId() + "?v="));
        assertThat(auditRepo.findAll()).anySatisfy(e -> {
            assertThat(e.getAction()).isEqualTo(AuditAction.PICTURE_CHANGED);
            assertThat(e.getSubjectUserId()).isEqualTo(person.getId());
            assertThat(e.getEmployerId()).isNull();
        });
    }

    @Test
    void removingDeletesItAndIsRecorded() {
        upload(self);
        pictures.remove(self);
        assertThat(pictureRepo.existsById(person.getId())).isFalse();
        assertThat(pictures.urlFor(person.getId())).isEmpty();
        assertThat(auditRepo.findAll()).anySatisfy(e -> {
            assertThat(e.getAction()).isEqualTo(AuditAction.PICTURE_REMOVED);
            assertThat(e.getSubjectUserId()).isEqualTo(person.getId());
        });
    }

    @Test
    void aTokenHasNoPicture() {
        Actor token = Actor.serviceToken(UUID.randomUUID(), "ci", ACME, MembershipRole.OWNER,
                java.util.Set.of(TokenScope.values()));
        assertThatThrownBy(() -> upload(token)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> pictures.find(person.getId(), token)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void oneselfAnAdminAndCoMembersMaySeeAPicture_nobodyElse() {
        upload(Actor.user(MEMBER, "Mara Member", "member@example.com", false, Map.of(ACME, MembershipRole.EDITOR)));

        Actor itself = Actor.user(MEMBER, "Mara Member", null, false, Map.of(ACME, MembershipRole.EDITOR));
        Actor coOwner = Actor.user(UUID.randomUUID(), "Olga Owner", null, false, Map.of(ACME, MembershipRole.OWNER));
        Actor admin = Actor.user(UUID.randomUUID(), "Ada Admin", null, true, Map.of());
        Actor stranger = Actor.user(UUID.randomUUID(), "Sid Stranger", null, false, Map.of());
        Actor otherEmployer = Actor.user(UUID.randomUUID(), "Otto Other", null, false,
                Map.of(UUID.randomUUID(), MembershipRole.OWNER));

        assertThat(pictures.find(MEMBER, itself).getContent()).isNotEmpty();
        assertThat(pictures.find(MEMBER, coOwner).getContent()).isNotEmpty();
        assertThat(pictures.find(MEMBER, admin).getContent()).isNotEmpty();
        // Refused exactly as a person with no picture is: nothing is disclosed.
        assertThatThrownBy(() -> pictures.find(MEMBER, stranger)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> pictures.find(MEMBER, otherEmployer)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> pictures.find(MEMBER, Actor.anonymous())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> pictures.find(person.getId(), self)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aNewProviderAddressIsAnnounced_aKnownOneIsNot() {
        pictures.providerPictureSeen(person.getId(), "https://idp.example/p/1.jpg");
        assertThat(announced()).containsExactly(
                new PictureService.ProviderPictureChanged(person.getId(), "https://idp.example/p/1.jpg"));

        pictures.storeProviderPicture(person.getId(), "https://idp.example/p/1.jpg", new byte[] {1});
        pictures.providerPictureSeen(person.getId(), "https://idp.example/p/1.jpg");
        assertThat(announced()).hasSize(1);

        pictures.providerPictureSeen(person.getId(), "https://idp.example/p/2.jpg");
        assertThat(announced()).hasSize(2);
    }

    @Test
    void aProviderThatDropsItsPictureIsAnnouncedOnlyIfACopyExists() {
        pictures.providerPictureSeen(person.getId(), null);
        assertThat(announced()).isEmpty();

        pictures.storeProviderPicture(person.getId(), "https://idp.example/p/1.jpg", new byte[] {1});
        pictures.providerPictureSeen(person.getId(), null);
        assertThat(announced()).containsExactly(new PictureService.ProviderPictureChanged(person.getId(), null));

        pictures.dropProviderPicture(person.getId());
        assertThat(pictureRepo.existsById(person.getId())).isFalse();
    }

    @Test
    void anUploadIsNeverReplacedByTheProvider() {
        upload(self);
        byte[] uploaded = pictureRepo.findById(person.getId()).orElseThrow().getContent();

        pictures.providerPictureSeen(person.getId(), "https://idp.example/p/1.jpg");
        pictures.storeProviderPicture(person.getId(), "https://idp.example/p/1.jpg", new byte[] {1});
        pictures.dropProviderPicture(person.getId());

        assertThat(announced()).isEmpty();
        UserPicture stored = pictureRepo.findById(person.getId()).orElseThrow();
        assertThat(stored.getSource()).isEqualTo(UserPicture.Source.UPLOAD);
        assertThat(stored.getContent()).isEqualTo(uploaded);
    }

    @Test
    void thePictureGoesWithTheAccount() {
        upload(self);
        pictureRepo.flush();
        userRepo.deleteWithEverything(person.getId());
        assertThat(pictureRepo.existsById(person.getId())).isFalse();
    }

    @Test
    void urlsForAListComeFromOneQuery_andOnlyForThoseWithAPicture() {
        upload(self);
        Map<UUID, String> urls = pictures.urlsFor(java.util.List.of(person.getId(), MEMBER));
        assertThat(urls).containsOnlyKeys(person.getId());
    }

    private java.util.List<PictureService.ProviderPictureChanged> announced() {
        return events.stream(PictureService.ProviderPictureChanged.class).toList();
    }
}
