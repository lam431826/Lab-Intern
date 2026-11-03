package vn.dxlab.recruitment.candidate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Quy tắc sàng lọc theo "Tài liệu Sàng lọc ứng viên", viết thành hàm thuần để test riêng.
 */
public final class ScreeningRules {

    /** Cờ lọc nhanh: gợi ý cho Lead, không tự loại hồ sơ. */
    public enum Flag {
        /** Không có link CV. */
        MISSING_CV,
        /** Link CV không phải http(s), không mở được từ trình duyệt. */
        INVALID_CV_LINK,
        /** Ngạch bắt buộc Portfolio (Graphic Design, Game Design) nhưng không có. */
        MISSING_PORTFOLIO,
        INVALID_PORTFOLIO_LINK
    }

    private static final Pattern HTTP_URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);

    private ScreeningRules() {
    }

    public static List<Flag> flags(String cvLink, String portfolioLink, boolean requiresPortfolio) {
        List<Flag> flags = new ArrayList<>();
        if (isBlank(cvLink)) {
            flags.add(Flag.MISSING_CV);
        } else if (!HTTP_URL.matcher(cvLink.trim()).matches()) {
            flags.add(Flag.INVALID_CV_LINK);
        }
        if (isBlank(portfolioLink)) {
            if (requiresPortfolio) {
                flags.add(Flag.MISSING_PORTFOLIO);
            }
        } else if (!HTTP_URL.matcher(portfolioLink.trim()).matches()) {
            flags.add(Flag.INVALID_PORTFOLIO_LINK);
        }
        return flags;
    }

    /** Trung bình các mục CV đã chấm, làm tròn 1 chữ số; chưa chấm mục nào thì null. */
    public static BigDecimal cvAverage(Integer... scores) {
        List<Integer> given = Stream.of(scores).filter(Objects::nonNull).toList();
        if (given.isEmpty()) {
            return null;
        }
        int sum = given.stream().mapToInt(Integer::intValue).sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(given.size()), 1, RoundingMode.HALF_UP);
    }

    public static Instant slaDeadline(Instant assignedAt, int slaHours) {
        return assignedAt.plus(Duration.ofHours(slaHours));
    }

    /** Chỉ hồ sơ còn chờ duyệt mới bị tính quá hạn. */
    public static boolean overdue(ScreeningStatus status, Instant assignedAt, int slaHours, Instant now) {
        return status == ScreeningStatus.PENDING && now.isAfter(slaDeadline(assignedAt, slaHours));
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
