package com.example.project_final;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.example.project_final.api.ApiClient;
import com.example.project_final.api.Requests;
import com.example.project_final.api.Session;
import com.example.project_final.model.RiceField;
import com.example.project_final.model.Scan;
import com.example.project_final.model.User;
import com.example.project_final.ui.common.BaseActivity;
import com.example.project_final.ui.community.CommunityActivity;
import com.example.project_final.ui.field.FieldDetailActivity;
import com.example.project_final.ui.field.FieldsActivity;
import com.example.project_final.ui.scan.ReviewPhotoActivity;
import com.example.project_final.ui.scan.ScanActivity;
import com.example.project_final.util.Uploads;
import com.google.gson.GsonBuilder;

import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Response;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/** Explicitly opt in with liveFarmScanDemo=true. Creates private demo fields and real AI scans in the active account. */
@RunWith(AndroidJUnit4.class)
public class LiveFarmScanDemoTest {
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<RiceField> fields = new ArrayList<>();
    private final List<Map<String, Object>> scans = new ArrayList<>();
    private Context context;

    /** Read-only follow-up: refresh screenshots and verify unknown risk does not mean no scan attempts. */
    @Test
    public void previewExistingDemoFieldsAndPortraits() throws Exception {
        Assume.assumeTrue(Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("liveFarmPreview", "false")));
        assertTrue(Session.isLoggedIn());
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        User before = body(ApiClient.api().me());
        RiceField field = body(ApiClient.api().fields("updated")).stream()
                .filter(f -> f.name.startsWith("DEMO -") && f.lastRisk == null)
                .findFirst().orElseThrow(() -> new AssertionError("No demo field with unknown risk"));
        assertFalse(body(ApiClient.api().scans("all", null, field.id, null, 10)).items.isEmpty());
        Intent detail = new Intent(context, FieldDetailActivity.class).putExtra(BaseActivity.EXTRA_ID, field.id);
        try (ActivityScenario<FieldDetailActivity> ignored = ActivityScenario.launch(detail)) {
            waitForView(allOf(withId(R.id.field_name), withText(field.name)), 20000);
            onView(withText("Chưa có kết quả")).check(matches(isDisplayed()));
            SystemClock.sleep(1500);
            capture("demo-field-detail.png");
        }
        try (ActivityScenario<FieldsActivity> ignored = ActivityScenario.launch(FieldsActivity.class)) {
            waitForView(withText("Danh sách ruộng (" + before.stats.fields + ")"), 20000);
            SystemClock.sleep(1000);
            capture("demo-fields-list.png");
        }
        try (ActivityScenario<CommunityActivity> ignored = ActivityScenario.launch(CommunityActivity.class)) {
            waitForView(withText("Phạm Quốc Tâm"), 20000);
            SystemClock.sleep(1500);
            capture("community-photo-avatars.png");
        }
        User after = body(ApiClient.api().me());
        assertEquals(before.stats.fields, after.stats.fields);
        assertEquals(before.stats.scans, after.stats.scans);
    }

    @Test
    public void createDetailedFieldsAndScanRealSamplePhotos() throws Exception {
        Assume.assumeTrue(Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("liveFarmScanDemo", "false")));
        assertTrue("Use the account already signed in on the emulator", Session.isLoggedIn());
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        User before = body(ApiClient.api().me());
        String runId = String.valueOf(System.currentTimeMillis());
        report.put("runId", runId);
        report.put("userId", before.id);
        report.put("userName", before.fullName);
        report.put("before", before.stats);
        report.put("fields", fields);
        report.put("scans", scans);
        report.put("status", "RUNNING");
        saveReport();

        String[][] specs = {
                {"DEMO - OM5451 Châu Phú", "An Giang", "Châu Phú", "0.8", "OM5451", "Đẻ nhánh", "2026-10-05", "paddy-overview.jpg"},
                {"DEMO - ST25 Ninh Kiều", "Cần Thơ", "Ninh Kiều", "1.2", "ST25", "Làm đòng", "2026-09-15", "paddy-sunrise.jpg"},
                {"DEMO - Đài Thơm 8 Cao Lãnh", "Đồng Tháp", "Cao Lãnh", "0.6", "Đài Thơm 8", "Trổ bông", "2026-08-28", "paddy-soft.png"}
        };
        try {
            for (int i = 0; i < specs.length; i++) {
                fields.add(createField(specs[i], runId, i));
                saveReport();
            }
            String[][] samples = {
                    {"than-la/uci-than-la-02.jpg", "than-la"},
                    {"dom-nau/mendeley-dom-nau-01.jpg", "dom-nau"},
                    {"bac-la/mendeley-bac-la-02.jpg", "bac-la"},
                    {"dao-on-la/mendeley-dao-on-la-03.jpg", "dao-on-la"},
                    {"chay-la/mendeley-chay-la-01.jpg", "chay-la"},
                    {"dom-nau-hep/mendeley-dom-nau-hep-01.jpg", "dom-nau-hep"},
                    {"la-khoe/mendeley-la-khoe-03.jpg", "healthy"}
            };
            for (int i = 0; i < samples.length; i++) {
                RiceField field = fields.get(i % fields.size());
                File source = new File(context.getExternalFilesDir("live-demo"), samples[i][0]);
                assertTrue("Missing sample " + source.getName(), source.isFile());
                File prepared = Uploads.prepareToCache(context, Uri.fromFile(source));
                String note = "DEMO_SCAN:" + runId + ":" + i + " | Nguồn ảnh: " + samples[i][1];
                Intent intent = new Intent(context, ReviewPhotoActivity.class)
                        .putExtra(ReviewPhotoActivity.EXTRA_PATH, prepared.getAbsolutePath())
                        .putExtra(ScanActivity.EXTRA_FIELD_ID, field.id);
                try (ActivityScenario<ReviewPhotoActivity> ignored = ActivityScenario.launch(intent)) {
                    waitForView(allOf(withId(R.id.field_name), withText(field.name)), 20000);
                    onView(withId(R.id.input_note)).perform(replaceText(note), closeSoftKeyboard());
                    onView(withId(R.id.btn_analyze)).perform(click());
                    waitForView(withId(R.id.disease_name), 120000);
                    Scan scan = body(ApiClient.api().scans("all", null, field.id, null, 50)).items.stream()
                            .filter(s -> note.equals(s.note)).findFirst().orElseThrow(() -> new AssertionError("Scan missing from history"));
                    assertEquals(field.id, scan.fieldId);
                    assertEquals(field.province, scan.province);
                    assertEquals(field.district, scan.district);
                    assertNotNull(scan.imageUrl);
                    assertTrue("Scan image must be saved on Cloudinary", scan.imageUrl.startsWith("https://res.cloudinary.com/"));
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("sourceFile", samples[i][0]);
                    result.put("sourceLabel", samples[i][1]);
                    result.put("scan", scan);
                    scans.add(result);
                    saveReport();
                    capture("scan-demo-" + (i + 1) + ".png");
                }
            }
            for (int i = 0; i < fields.size(); i++) fields.set(i, body(ApiClient.api().field(fields.get(i).id)).field);
            User after = body(ApiClient.api().me());
            assertEquals(before.id, after.id);
            assertEquals(before.stats.fields + 3, after.stats.fields);
            assertEquals(before.stats.scans + 7, after.stats.scans);
            assertEquals(before.province, after.province);
            assertEquals(before.district, after.district);
            report.put("after", after.stats);
            report.put("status", "PASSED");
            saveReport();
            try (ActivityScenario<FieldsActivity> ignored = ActivityScenario.launch(FieldsActivity.class)) {
                waitForView(withText("Danh sách ruộng (" + after.stats.fields + ")"), 20000);
                capture("demo-fields-list.png");
            }
            Intent detail = new Intent(context, FieldDetailActivity.class).putExtra(BaseActivity.EXTRA_ID, fields.get(0).id);
            try (ActivityScenario<FieldDetailActivity> ignored = ActivityScenario.launch(detail)) {
                waitForView(allOf(withId(R.id.field_name), withText(fields.get(0).name)), 20000);
                capture("demo-field-detail.png");
            }
            try (ActivityScenario<CommunityActivity> ignored = ActivityScenario.launch(CommunityActivity.class)) {
                waitForView(withText("Phạm Quốc Tâm"), 20000);
                SystemClock.sleep(1500);
                capture("community-photo-avatars.png");
            }
        } catch (Exception | AssertionError e) {
            report.put("status", "FAILED");
            report.put("error", e.toString());
            saveReport();
            throw e;
        }
    }

    private RiceField createField(String[] spec, String runId, int index) throws Exception {
        String note = "DEMO " + runId + "; ruộng mẫu kiểm thử admin, đất phù sa, chủ động tưới tiêu; ảnh minh họa.";
        try (ActivityScenario<FieldsActivity> ignored = ActivityScenario.launch(FieldsActivity.class)) {
            waitForView(withId(R.id.btn_add), 20000);
            onView(withId(R.id.btn_add)).perform(click());
            onView(withId(R.id.input_name)).perform(replaceText(spec[0]), closeSoftKeyboard());
            choose(R.id.dd_variety, spec[4]);
            onView(withId(R.id.input_area)).perform(scrollTo(), replaceText(spec[3]), closeSoftKeyboard());
            onView(withId(R.id.dd_date)).perform(scrollTo(), click());
            String[] date = spec[6].split("-");
            onView(isAssignableFrom(DatePicker.class)).perform(new ViewAction() {
                public Matcher<View> getConstraints() { return isAssignableFrom(DatePicker.class); }
                public String getDescription() { return "Set the sample planting date"; }
                public void perform(UiController controller, View view) {
                    ((DatePicker) view).updateDate(Integer.parseInt(date[0]), Integer.parseInt(date[1]) - 1, Integer.parseInt(date[2]));
                }
            });
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
            choose(R.id.dd_stage, spec[5]);
            choose(R.id.btn_location, spec[1]);
            onView(allOf(isAssignableFrom(EditText.class), isDisplayed())).inRoot(isDialog())
                    .perform(replaceText(spec[2]), closeSoftKeyboard());
            onView(withText("Xong")).inRoot(isDialog()).perform(click());
            onView(withId(R.id.input_note)).perform(scrollTo(), replaceText(note), closeSoftKeyboard());
            onView(withId(R.id.btn_save)).perform(click());
            waitForView(allOf(withId(R.id.name), withText(spec[0])), 30000);
        }
        RiceField field = body(ApiClient.api().fields("updated")).stream().filter(f -> note.equals(f.note) && spec[0].equals(f.name))
                .findFirst().orElseThrow(() -> new AssertionError("Field not saved"));
        assertEquals(spec[1], field.province);
        assertEquals(spec[2], field.district);
        assertEquals(spec[4], field.variety);
        assertEquals(spec[5], field.stage);
        assertEquals(spec[6], field.plantingDate);
        Requests.FieldBody full = Requests.FieldBody.from(field);
        full.plot = "Thửa mẫu " + (index + 1);
        full.address = "Địa chỉ minh họa phục vụ kiểm thử, không phải vị trí thực của người dùng";
        full.nextCheckDate = "2026-11-09";
        body(ApiClient.api().updateField(field.id, full));
        File image = new File(context.getExternalFilesDir("live-demo"), spec[7]);
        RiceField withPhoto = body(ApiClient.api().uploadFieldPhoto(field.id, Uploads.imagePart(context, Uri.fromFile(image), "file")));
        assertNotNull(withPhoto.photoUrl);
        assertEquals("2026-11-09", withPhoto.nextCheckDate);
        return withPhoto;
    }

    private static void choose(int id, String label) {
        onView(withId(id)).perform(scrollTo(), click());
        onData(equalTo(label)).inRoot(isDialog()).perform(click());
    }

    private static void waitForView(Matcher<View> matcher, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        Throwable last = null;
        do {
            try { onView(matcher).check(matches(isDisplayed())); return; }
            catch (AssertionError | RuntimeException e) { last = e; SystemClock.sleep(200); }
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Expected UI did not appear", last);
    }

    private static <T> T body(Call<T> call) throws Exception {
        Response<T> response = call.execute();
        assertTrue("HTTP " + response.code(), response.isSuccessful());
        assertNotNull(response.body());
        return response.body();
    }

    private void saveReport() throws Exception {
        File file = new File(context.getExternalFilesDir("ui-test"), "farm-scan-demo-report.json");
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report, writer);
        }
    }

    private void capture(String name) {
        CountDownLatch done = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                Collection<Activity> resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED);
                Activity activity = resumed.iterator().next();
                View root = activity.getWindow().getDecorView();
                Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
                PixelCopy.request(activity.getWindow(), bitmap, result -> {
                    try {
                        if (result != PixelCopy.SUCCESS) throw new IllegalStateException("PixelCopy " + result);
                        try (FileOutputStream out = new FileOutputStream(new File(context.getExternalFilesDir("ui-test"), name))) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        }
                    } catch (Exception e) { report.put("captureWarning", e.toString()); }
                    finally { bitmap.recycle(); done.countDown(); }
                }, new Handler(Looper.getMainLooper()));
            } catch (Exception e) {
                report.put("captureWarning", e.toString());
                done.countDown();
            }
        });
        try { if (!done.await(10, TimeUnit.SECONDS)) report.put("captureWarning", "Screenshot timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
