package org.letsemploy.ojobpub_publisher.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Initials where there is no picture (spec 7.27). No Spring. */
class AvatarTest {

    @Test
    void firstAndLastWord() {
        assertThat(Avatar.initials("Ada Lovelace")).isEqualTo("AL");
        assertThat(Avatar.initials("  ada  augusta   king ")).isEqualTo("AK");
        assertThat(Avatar.initials("Mara")).isEqualTo("M");
    }

    @Test
    void anAddressIsItsFirstLetter() {
        assertThat(Avatar.initials("dev@localhost")).isEqualTo("D");
    }

    @Test
    void scriptsWithoutSpacesAndCharactersBeyondTheBasicPlane() {
        assertThat(Avatar.initials("山田太郎")).isEqualTo("山");
        assertThat(Avatar.initials("𝒜da Byron")).isEqualTo("𝒜B");
        assertThat(Avatar.initials("Élodie Ørsted")).isEqualTo("ÉØ");
    }

    @Test
    void nobodyIsAQuestionMark() {
        assertThat(Avatar.initials(null)).isEqualTo("?");
        assertThat(Avatar.initials("  ")).isEqualTo("?");
    }
}
