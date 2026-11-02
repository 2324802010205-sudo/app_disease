package com.riceguard.api.dataset;

/**
 * Một ảnh người dùng quét, xếp theo nhãn AI để quản trị viên đối chiếu và dùng huấn luyện lại best.pt.
 * label: thư mục AI đoán (mã bệnh | healthy | unknown | pending). reviewStatus: PENDING | CONFIRMED | CORRECTED | REJECTED.
 */
public record DatasetImage(
        String id,              // trùng id lượt quét
        String userId,
        String label,
        String diseaseName,
        String status,          // DIAGNOSED | NEEDS_RETAKE | PENDING
        double confidence,
        String severity,
        String engine,
        String imageUrl,
        String imagePublicId,
        String thumbUrl,
        String province,
        String district,
        String stage,
        String reviewStatus,
        String trueLabel,       // nhãn đúng sau khi duyệt
        String reviewNote,
        String reviewedBy,
        Long reviewedAt,
        boolean reportedWrong,  // người dùng phản hồi "kết quả sai"
        String suggestedLabel,  // bệnh người dùng đề xuất
        long createdAt) {}
