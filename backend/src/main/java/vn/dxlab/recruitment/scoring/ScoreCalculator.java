package vn.dxlab.recruitment.scoring;

import vn.dxlab.recruitment.track.CriterionGroup;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Công thức điểm, dùng chung cho phiếu điểm, bảng kết quả và file xuất:
 * <ol>
 *   <li>Điểm tiêu chí = trung bình điểm các giám khảo đã nộp phiếu.</li>
 *   <li>Điểm khối (chung / chuyên môn) = trung bình các điểm tiêu chí trong khối, mỗi tiêu chí nặng như nhau.</li>
 *   <li>Tổng = trọng số chung × điểm chung + trọng số chuyên môn × điểm chuyên môn.</li>
 * </ol>
 * Tính ở độ chính xác cao, chỉ làm tròn 2 chữ số khi trả ra, để không cộng dồn sai số làm tròn.
 */
public final class ScoreCalculator {

    private static final int WORK_SCALE = 10;
    private static final int OUT_SCALE = 2;

    private ScoreCalculator() {
    }

    public record CriterionRef(Long id, CriterionGroup group) {
    }

    /** {@code criterionAverages}: chỉ chứa tiêu chí có ít nhất một điểm. {@code total = null} khi thiếu một khối. */
    public record Result(Map<Long, BigDecimal> criterionAverages, Map<Long, Integer> criterionCounts,
                         BigDecimal generalAverage, BigDecimal professionalAverage, BigDecimal total) {

        public boolean meets(BigDecimal threshold) {
            return total != null && total.compareTo(threshold) >= 0;
        }
    }

    /**
     * @param criteria tiêu chí của phiếu chấm
     * @param sheets   mỗi phần tử là một phiếu đã nộp: criterionId → điểm
     */
    public static Result calculate(List<CriterionRef> criteria, Collection<Map<Long, Integer>> sheets,
                                   BigDecimal weightGeneral, BigDecimal weightProfessional) {
        Map<Long, BigDecimal> exact = new LinkedHashMap<>();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (CriterionRef c : criteria) {
            List<Integer> values = sheets.stream().map(s -> s.get(c.id())).filter(Objects::nonNull).toList();
            if (!values.isEmpty()) {
                exact.put(c.id(), BigDecimal.valueOf(values.stream().mapToInt(Integer::intValue).sum())
                        .divide(BigDecimal.valueOf(values.size()), WORK_SCALE, RoundingMode.HALF_UP));
                counts.put(c.id(), values.size());
            }
        }
        BigDecimal general = groupMean(criteria, exact, CriterionGroup.GENERAL);
        BigDecimal professional = groupMean(criteria, exact, CriterionGroup.PROFESSIONAL);
        BigDecimal total = general == null || professional == null ? null
                : weightGeneral.multiply(general).add(weightProfessional.multiply(professional));

        Map<Long, BigDecimal> rounded = new LinkedHashMap<>();
        exact.forEach((id, v) -> rounded.put(id, round(v)));
        return new Result(rounded, counts, round(general), round(professional), round(total));
    }

    private static BigDecimal groupMean(List<CriterionRef> criteria, Map<Long, BigDecimal> averages, CriterionGroup group) {
        List<BigDecimal> values = criteria.stream().filter(c -> c.group() == group)
                .map(c -> averages.get(c.id())).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), WORK_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal round(BigDecimal v) {
        return v == null ? null : v.setScale(OUT_SCALE, RoundingMode.HALF_UP);
    }
}
