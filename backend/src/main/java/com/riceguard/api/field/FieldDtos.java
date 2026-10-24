package com.riceguard.api.field;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class FieldDtos {
    private FieldDtos() {}

    public record FieldRequest(
            @NotBlank(message = "Nhập tên ruộng") @Size(max = 80, message = "Tên ruộng tối đa 80 ký tự") String name,
            @Size(max = 60) String plot,
            @Size(max = 60) String province,
            @Size(max = 60) String district,
            @Size(max = 200) String address,
            @DecimalMin(value = "-90", message = "Vĩ độ không hợp lệ") @DecimalMax(value = "90", message = "Vĩ độ không hợp lệ") Double lat,
            @DecimalMin(value = "-180", message = "Kinh độ không hợp lệ") @DecimalMax(value = "180", message = "Kinh độ không hợp lệ") Double lng,
            @DecimalMin(value = "0.01", message = "Diện tích phải lớn hơn 0") @DecimalMax(value = "100000", message = "Diện tích quá lớn") Double areaHa,
            @Size(max = 60) String variety,
            @Size(max = 40) String stage,
            LocalDate plantingDate,
            @Size(max = 200, message = "Ghi chú tối đa 200 ký tự") String note,
            LocalDate nextCheckDate) {}

    /** status: HEALTHY | WATCH | ALERT | UNKNOWN (suy ra từ lần quét gần nhất). */
    public record FieldDto(@JsonUnwrapped Field field, String status, Integer daysUntilCheck) {}
}
