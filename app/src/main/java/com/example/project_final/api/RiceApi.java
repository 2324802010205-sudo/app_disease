package com.example.project_final.api;

import com.example.project_final.model.*;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.*;

/** Toàn bộ endpoint của backend (/api/v1). Mỗi nhóm khớp một controller phía Spring Boot. */
public interface RiceApi {

    // ---- Xác thực
    @POST("auth/register") Call<AuthResponse> register(@Body Requests.Register body);
    @POST("auth/login") Call<AuthResponse> login(@Body Requests.Login body);
    @POST("auth/google") Call<AuthResponse> google(@Body Requests.Google body);
    @POST("auth/refresh") Call<AuthResponse> refresh(@Body Requests.Refresh body);
    @POST("auth/forgot-password") Call<Map<String, String>> forgotPassword(@Body Requests.Forgot body);
    @POST("auth/change-password") Call<Void> changePassword(@Body Requests.ChangePassword body);
    @POST("auth/logout") Call<Void> logout();

    // ---- Hồ sơ
    @GET("users/me") Call<User> me();
    @PUT("users/me") Call<User> updateProfile(@Body Requests.Profile body);
    @Multipart @POST("users/me/avatar") Call<User> uploadAvatar(@Part MultipartBody.Part file);
    @PUT("users/me/settings") Call<User> updateSettings(@Body Requests.Settings body);
    @POST("users/me/devices") Call<Void> addDevice(@Body Requests.Device body);
    @HTTP(method = "DELETE", path = "users/me/devices", hasBody = true) Call<Void> removeDevice(@Body Requests.Device body);
    @DELETE("users/me") Call<Void> deleteAccount();

    // ---- Ruộng lúa
    @GET("fields") Call<List<RiceField>> fields(@Query("sort") String sort);
    @POST("fields") Call<RiceField> createField(@Body Requests.FieldBody body);
    @GET("fields/{id}") Call<RiceField.Detail> field(@Path("id") String id);
    @PUT("fields/{id}") Call<RiceField> updateField(@Path("id") String id, @Body Requests.FieldBody body);
    @Multipart @POST("fields/{id}/photo") Call<RiceField> uploadFieldPhoto(@Path("id") String id, @Part MultipartBody.Part file);
    @DELETE("fields/{id}") Call<Void> deleteField(@Path("id") String id);

    // ---- Quét bệnh
    @Multipart @POST("scans") Call<Scan> createScan(@Part MultipartBody.Part image, @PartMap Map<String, RequestBody> fields);
    @GET("scans") Call<Page<Scan>> scans(@Query("range") String range, @Query("risk") String risk,
                                         @Query("fieldId") String fieldId, @Query("cursor") String cursor,
                                         @Query("limit") Integer limit);
    @GET("scans/compare") Call<Scan.Comparison> compare(@Query("first") String first, @Query("second") String second);
    @GET("scans/{id}") Call<Scan> scan(@Path("id") String id);
    @POST("scans/{id}/diagnose") Call<Scan> diagnose(@Path("id") String id);
    @PUT("scans/{id}/note") Call<Scan> updateScanNote(@Path("id") String id, @Body Requests.Note body);
    @DELETE("scans/{id}") Call<Void> deleteScan(@Path("id") String id);
    @POST("scans/{id}/feedback") Call<Map<String, String>> feedback(@Path("id") String id, @Body Requests.Feedback body);

    // ---- Thư viện bệnh
    @GET("diseases") Call<List<Disease>> diseases(@Query("part") String part, @Query("cause") String cause,
                                                  @Query("common") Boolean common, @Query("q") String q);
    @GET("diseases/{id}") Call<Disease> disease(@Path("id") String id);
    @PUT("diseases/{id}/save") Call<Void> saveDisease(@Path("id") String id);
    @DELETE("diseases/{id}/save") Call<Void> unsaveDisease(@Path("id") String id);
    @GET("users/me/saved-diseases") Call<List<Disease>> savedDiseases();

    // ---- Nhật ký
    @GET("diary") Call<Page<DiaryEntry>> diary(@Query("fieldId") String fieldId, @Query("days") Integer days,
                                               @Query("cursor") String cursor, @Query("limit") Integer limit);
    @POST("diary") Call<DiaryEntry> createDiary(@Body Requests.DiaryBody body);
    @GET("diary/{id}") Call<DiaryEntry> diaryEntry(@Path("id") String id);
    @PUT("diary/{id}") Call<DiaryEntry> updateDiary(@Path("id") String id, @Body Requests.DiaryBody body);
    @DELETE("diary/{id}") Call<Void> deleteDiary(@Path("id") String id);

    // ---- Cộng đồng
    @GET("posts") Call<Page<Post>> posts(@Query("topic") String topic, @Query("q") String q,
                                         @Query("cursor") String cursor, @Query("limit") Integer limit);
    @POST("posts") Call<Post> createPost(@Body Requests.PostBody body);
    @GET("posts/{id}") Call<Post> post(@Path("id") String id);
    @PUT("posts/{id}") Call<Post> updatePost(@Path("id") String id, @Body Requests.PostBody body);
    @DELETE("posts/{id}") Call<Void> deletePost(@Path("id") String id);
    @PUT("posts/{id}/like") Call<Post.LikeResult> likePost(@Path("id") String id);
    @DELETE("posts/{id}/like") Call<Post.LikeResult> unlikePost(@Path("id") String id);
    @PUT("posts/{id}/save") Call<Void> savePost(@Path("id") String id);
    @DELETE("posts/{id}/save") Call<Void> unsavePost(@Path("id") String id);
    @POST("posts/{id}/report") Call<Void> reportPost(@Path("id") String id, @Body Requests.Report body);
    @GET("posts/{id}/comments") Call<List<Comment>> comments(@Path("id") String id, @Query("sort") String sort);
    @POST("posts/{id}/comments") Call<Comment> addComment(@Path("id") String id, @Body Requests.CommentBody body);
    @DELETE("posts/{id}/comments/{cid}") Call<Void> deleteComment(@Path("id") String id, @Path("cid") String commentId);
    @PUT("posts/{id}/comments/{cid}/like") Call<Void> likeComment(@Path("id") String id, @Path("cid") String commentId);
    @DELETE("posts/{id}/comments/{cid}/like") Call<Void> unlikeComment(@Path("id") String id, @Path("cid") String commentId);
    @POST("posts/{id}/comments/{cid}/report") Call<Void> reportComment(@Path("id") String id, @Path("cid") String commentId, @Body Requests.Report body);
    @GET("users/me/posts") Call<Page<Post>> myPosts(@Query("cursor") String cursor, @Query("limit") Integer limit);
    @GET("users/me/saved-posts") Call<Page<Post>> savedPosts(@Query("cursor") String cursor, @Query("limit") Integer limit);
    @GET("users/me/drafts") Call<Page<Post.Draft>> drafts(@Query("cursor") String cursor, @Query("limit") Integer limit);
    @POST("users/me/drafts") Call<Post.Draft> createDraft(@Body Requests.PostBody body);
    @PUT("users/me/drafts/{id}") Call<Post.Draft> updateDraft(@Path("id") String id, @Body Requests.PostBody body);
    @DELETE("users/me/drafts/{id}") Call<Void> deleteDraft(@Path("id") String id);
    @POST("users/me/drafts/{id}/publish") Call<Post> publishDraft(@Path("id") String id);

    // ---- Thông báo
    @GET("notifications") Call<Page<Notification>> notifications(@Query("filter") String filter, @Query("cursor") String cursor,
                                                                 @Query("limit") Integer limit);
    @GET("notifications/unread-count") Call<Map<String, Long>> unreadCount();
    @PUT("notifications/{id}/read") Call<Void> markRead(@Path("id") String id);
    @PUT("notifications/read-all") Call<Map<String, Integer>> markAllRead();
    @DELETE("notifications/{id}") Call<Void> deleteNotification(@Path("id") String id);

    // ---- Thời tiết, nguy cơ, thống kê, danh mục
    @GET("weather") Call<Weather> weather(@Query("province") String province, @Query("lat") Double lat, @Query("lng") Double lng);
    @GET("risk/areas") Call<List<Risk.Area>> riskAreas(@Query("province") String province, @Query("disease") String disease,
                                                       @Query("days") int days, @Query("sort") String sort);
    @GET("risk/areas/info") Call<Risk.AreaInfo> riskInfo(@Query("province") String province, @Query("district") String district,
                                                         @Query("days") int days);
    @GET("stats") Call<Stats> stats(@Query("range") String range);
    @GET("meta/options") Call<MetaOptions> options();

    // ---- Ảnh dùng chung (đính kèm bài viết, nhật ký, phản hồi)
    @Multipart @POST("media/upload") Call<ImageRef> uploadImage(@Part MultipartBody.Part file, @Query("kind") String kind);
}
