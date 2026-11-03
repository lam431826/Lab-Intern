package vn.dxlab.recruitment.common.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VietnameseTextTest {

    @Test
    void removesAccentsAndNormalizesSpacing() {
        assertThat(VietnameseText.normalize("  Mã  SV ")).isEqualTo("ma sv");
        assertThat(VietnameseText.normalize("Họ và tên")).isEqualTo("ho va ten");
        assertThat(VietnameseText.normalize("Đặng Văn Bình")).isEqualTo("dang van binh");
        assertThat(VietnameseText.normalize("Graphic\nDesigners")).isEqualTo("graphic designers");
        assertThat(VietnameseText.normalize("GAME_DEV")).isEqualTo("game dev");
        assertThat(VietnameseText.normalize("Link CV / Portfolio")).isEqualTo("link cv portfolio");
        assertThat(VietnameseText.normalize(null)).isEmpty();
    }
}
