package vn.dxlab.recruitment.common.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** So khớp chữ tiếng Việt do người dùng gõ tay (tiêu đề cột Excel, tên ngạch...). */
public final class VietnameseText {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private VietnameseText() {
    }

    /** "  Mã  SV " → "ma sv"; "Đặng" → "dang". Bỏ dấu, đổi đ thành d, chữ thường, gộp khoảng trắng và ký tự lạ. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String noMarks = COMBINING_MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        String lower = noMarks.replace('đ', 'd').replace('Đ', 'd').toLowerCase(Locale.ROOT);
        return NON_ALNUM.matcher(lower).replaceAll(" ").trim();
    }
}
