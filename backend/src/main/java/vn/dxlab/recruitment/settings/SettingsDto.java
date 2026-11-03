package vn.dxlab.recruitment.settings;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * @param weightGeneral      trọng số điểm chung (0–1)
 * @param weightProfessional trọng số điểm chuyên môn (0–1); hai trọng số cộng lại phải bằng 1
 * @param passThreshold      điểm sàn gợi ý Đạt (1–10)
 * @param slaHours           thời hạn Lead duyệt CV, tính bằng giờ
 * @param slotMinutes        độ dài một slot phỏng vấn
 */
public record SettingsDto(
        @NotNull @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal weightGeneral,
        @NotNull @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal weightProfessional,
        @NotNull @DecimalMin("1.00") @DecimalMax("10.00") BigDecimal passThreshold,
        @Min(1) @Max(720) int slaHours,
        @Min(5) @Max(60) int slotMinutes) {

    static SettingsDto from(ScoringSettings s) {
        return new SettingsDto(s.getWeightGeneral(), s.getWeightProfessional(), s.getPassThreshold(),
                s.getSlaHours(), s.getSlotMinutes());
    }
}
