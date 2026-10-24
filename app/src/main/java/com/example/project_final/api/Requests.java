package com.example.project_final.api;

import com.example.project_final.model.ImageRef;

import java.util.List;

/** Thân yêu cầu gửi lên backend. Trường null sẽ không được gửi (Gson bỏ qua null). */
public final class Requests {
    private Requests() {}

    public static class Register {
        public String fullName, email, password, phone, province, role;

        public Register(String fullName, String email, String password, String phone, String province, String role) {
            this.fullName = fullName;
            this.email = email;
            this.password = password;
            this.phone = phone;
            this.province = province;
            this.role = role;
        }
    }

    public static class Login {
        public String email, password;

        public Login(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    /** mode: LOGIN (chỉ tài khoản đã có) | REGISTER (tạo nếu chưa có). */
    public static class Google {
        public String idToken, mode;

        public Google(String idToken, String mode) {
            this.idToken = idToken;
            this.mode = mode;
        }
    }

    public static class Refresh {
        public String refreshToken;

        public Refresh(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    public static class Forgot {
        public String email;

        public Forgot(String email) {
            this.email = email;
        }
    }

    public static class ChangePassword {
        public String currentPassword, newPassword;

        public ChangePassword(String currentPassword, String newPassword) {
            this.currentPassword = currentPassword;
            this.newPassword = newPassword;
        }
    }

    public static class Profile {
        public String fullName, phone, province, district, role, bio;
    }

    public static class Settings {
        public String theme, language;
        public Boolean notifyAlert, notifyReminder, notifyCommunity;
    }

    public static class Device {
        public String token;

        public Device(String token) {
            this.token = token;
        }
    }

    /** Ngày dùng định dạng yyyy-MM-dd. */
    public static class FieldBody {
        public String name, plot, province, district, address, variety, stage, note;
        public String plantingDate, nextCheckDate;
        public Double lat, lng, areaHa;

        /** Bản sao đầy đủ của ruộng hiện có, dùng khi chỉ đổi một vài trường (backend PUT cần đủ dữ liệu). */
        public static FieldBody from(com.example.project_final.model.RiceField f) {
            FieldBody b = new FieldBody();
            b.name = f.name;
            b.plot = f.plot;
            b.province = f.province;
            b.district = f.district;
            b.address = f.address;
            b.variety = f.variety;
            b.stage = f.stage;
            b.note = f.note;
            b.plantingDate = f.plantingDate;
            b.nextCheckDate = f.nextCheckDate;
            b.lat = f.lat;
            b.lng = f.lng;
            b.areaHa = f.areaHa > 0 ? f.areaHa : null;
            return b;
        }
    }

    public static class DiaryBody {
        public String fieldId, title, type, content, date, weather;
        public List<ImageRef> images;
    }

    public static class PostBody {
        public String topic, title, content;
        public List<ImageRef> images;
    }

    public static class CommentBody {
        public String text, parentId;
    }

    public static class Report {
        public String reason, detail;

        public Report(String reason, String detail) {
            this.reason = reason;
            this.detail = detail;
        }
    }

    public static class Feedback {
        public String reason, suggestedDiseaseCode, description;
        public List<ImageRef> attachments;
    }

    public static class Note {
        public String note;

        public Note(String note) {
            this.note = note;
        }
    }
}
