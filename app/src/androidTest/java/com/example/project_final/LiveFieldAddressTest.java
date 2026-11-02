package com.example.project_final;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.project_final.api.ApiClient;
import com.example.project_final.api.Requests;
import com.example.project_final.api.Session;
import com.example.project_final.model.RiceField;
import com.example.project_final.model.User;
import com.example.project_final.ui.community.CommunityActivity;
import com.example.project_final.ui.field.FieldsActivity;

import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

/**
 * Opt-in integration check against the account currently signed in on the device.
 * Run only with instrumentation argument liveAccountFieldTest=true.
 * A successful run leaves one clearly labeled sample field for inspection.
 */
@RunWith(AndroidJUnit4.class)
public class LiveFieldAddressTest {
    private static final String NAME = "TEST - Địa chỉ ruộng";
    private static final String NOTE = "Ruộng kiểm thử địa chỉ; không phải vị trí thực của tài khoản.";

    @Test
    public void currentAccountCanSaveAndChangeFieldLocation() throws Exception {
        Assume.assumeTrue(Boolean.parseBoolean(InstrumentationRegistry.getArguments()
                .getString("liveAccountFieldTest", "false")));
        assertTrue("Sign in on the device before running this check", Session.isLoggedIn());
        User profileBefore = body(ApiClient.api().me());
        int beforeCount = body(ApiClient.api().fields("updated")).size();
        String createdId = null;
        boolean passed = false;

        try (ActivityScenario<FieldsActivity> scenario = ActivityScenario.launch(FieldsActivity.class)) {
            waitForView(withId(R.id.btn_add));
            onView(withId(R.id.btn_add)).perform(click());
            onView(withId(R.id.input_name)).perform(replaceText(NAME), closeSoftKeyboard());
            onView(withId(R.id.input_area)).perform(scrollTo(), replaceText("0.5"), closeSoftKeyboard());
            onView(withId(R.id.input_note)).perform(scrollTo(), replaceText(NOTE), closeSoftKeyboard());
            chooseProvince("Cần Thơ");
            district().perform(replaceText("Ninh Kiều"), closeSoftKeyboard());
            onView(withText("Xong")).inRoot(isDialog()).perform(click());
            onView(withId(R.id.location_desc)).check(matches(withText("Ninh Kiều, Cần Thơ")));
            onView(withId(R.id.btn_save)).perform(click());
            waitForView(allOf(withId(R.id.name), withText(NAME)));

            List<RiceField> afterCreate = body(ApiClient.api().fields("updated"));
            assertEquals(beforeCount + 1, afterCreate.size());
            RiceField created = afterCreate.stream().filter(f -> NAME.equals(f.name) && NOTE.equals(f.note))
                    .findFirst().orElseThrow(() -> new AssertionError("Created field missing from backend"));
            createdId = created.id;
            assertEquals("Cần Thơ", created.province);
            assertEquals("Ninh Kiều", created.district);
            assertEquals(0.5, created.areaHa, 0.001);

            // Values not exposed by the edit form must survive an unrelated edit.
            Requests.FieldBody extra = Requests.FieldBody.from(created);
            extra.nextCheckDate = "2026-11-09";
            extra.plot = "Thửa kiểm thử";
            extra.address = "Địa chỉ chi tiết kiểm thử";
            extra.lat = 10.0;
            extra.lng = 105.0;
            body(ApiClient.api().updateField(createdId, extra));

            onView(allOf(withId(R.id.name), withText(NAME))).perform(click());
            waitForView(withId(R.id.act_edit));
            onView(withId(R.id.act_edit)).perform(click());
            waitForView(allOf(withId(R.id.input_name), withText(NAME)));
            chooseProvince("An Giang");
            district().check(matches(withText("")));
            onView(withText("Hủy")).inRoot(isDialog()).perform(click());
            onView(withId(R.id.location_desc)).check(matches(withText("Ninh Kiều, Cần Thơ")));
            chooseProvince("An Giang");
            pressBack();
            onView(withId(R.id.location_desc)).check(matches(withText("Ninh Kiều, Cần Thơ")));
            onView(withId(R.id.btn_save)).perform(click());
            waitForView(withId(R.id.act_edit));

            RiceField unchanged = body(ApiClient.api().field(createdId)).field;
            assertEquals("Ninh Kiều", unchanged.district);
            assertEquals("Cần Thơ", unchanged.province);
            assertEquals(extra.nextCheckDate, unchanged.nextCheckDate);
            assertEquals(extra.plot, unchanged.plot);
            assertEquals(extra.address, unchanged.address);
            assertEquals(extra.lat, unchanged.lat);
            assertEquals(extra.lng, unchanged.lng);

            // Confirming a different province without a district clears the old district and GPS.
            onView(withId(R.id.act_edit)).perform(click());
            waitForView(allOf(withId(R.id.input_name), withText(NAME)));
            chooseProvince("An Giang");
            district().check(matches(withText("")));
            onView(withText("Xong")).inRoot(isDialog()).perform(click());
            onView(withId(R.id.location_desc)).check(matches(withText("An Giang")));
            onView(withId(R.id.btn_save)).perform(click());
            waitForView(withId(R.id.act_edit));
            RiceField moved = body(ApiClient.api().field(createdId)).field;
            assertEquals("An Giang", moved.province);
            assertNull(moved.district);
            assertNull(moved.address);
            assertNull(moved.lat);
            assertNull(moved.lng);
            assertEquals(extra.nextCheckDate, moved.nextCheckDate);

            onView(withId(R.id.act_edit)).perform(click());
            waitForView(allOf(withId(R.id.input_name), withText(NAME)));
            chooseProvince("An Giang");
            district().perform(replaceText("Châu Phú"), closeSoftKeyboard());
            onView(withText("Xong")).inRoot(isDialog()).perform(click());
            onView(withId(R.id.btn_save)).perform(click());
            waitForView(withId(R.id.act_edit));
            RiceField finished = body(ApiClient.api().field(createdId)).field;
            assertEquals("Châu Phú", finished.district);
            assertEquals("An Giang", finished.province);
            assertEquals(extra.nextCheckDate, finished.nextCheckDate);
            assertEquals("Thửa kiểm thử, Châu Phú, An Giang", FieldsActivity.place(finished));
            waitForView(allOf(withId(R.id.field_place), withText(FieldsActivity.place(finished))));

            User profileAfter = body(ApiClient.api().me());
            assertEquals(profileBefore.id, profileAfter.id);
            assertEquals(profileBefore.province, profileAfter.province);
            assertEquals(profileBefore.district, profileAfter.district);
            assertEquals(beforeCount + 1, profileAfter.stats.fields);
            passed = true;
        } finally {
            if (!passed && createdId != null) ApiClient.api().deleteField(createdId).execute();
        }

        try (ActivityScenario<CommunityActivity> community = ActivityScenario.launch(CommunityActivity.class)) {
            waitForView(withText("Phạm Quốc Tâm"));
            onView(withContentDescription("Ảnh đại diện Phạm Quốc Tâm")).check(matches(isDisplayed()));
            onView(withContentDescription("Ảnh đại diện Nguyễn Văn Hải")).check(matches(isDisplayed()));
            capture(community, "community-avatars.png");
        }
    }

    private static void chooseProvince(String province) {
        onView(withId(R.id.btn_location)).perform(scrollTo(), click());
        onView(withText(province)).inRoot(isDialog()).perform(click());
    }

    private static androidx.test.espresso.ViewInteraction district() {
        return onView(allOf(isAssignableFrom(EditText.class), isDisplayed())).inRoot(isDialog());
    }

    private static void waitForView(Matcher<View> matcher) {
        long until = SystemClock.uptimeMillis() + 20000;
        Throwable last = null;
        do {
            try {
                onView(matcher).check(matches(isDisplayed()));
                return;
            } catch (AssertionError | RuntimeException e) {
                last = e;
                SystemClock.sleep(150);
            }
        } while (SystemClock.uptimeMillis() < until);
        throw new AssertionError("UI did not reach expected state", last);
    }

    private static <T> T body(Call<T> call) throws Exception {
        Response<T> response = call.execute();
        assertTrue("Backend returned HTTP " + response.code(), response.isSuccessful());
        assertNotNull(response.body());
        return response.body();
    }

    private static <A extends android.app.Activity> void capture(ActivityScenario<A> scenario, String name) {
        scenario.onActivity(activity -> {
            View root = activity.getWindow().getDecorView();
            Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(bitmap));
            File file = new File(activity.getExternalFilesDir("ui-test"), name);
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (Exception e) {
                throw new AssertionError("Cannot capture view", e);
            } finally {
                bitmap.recycle();
            }
        });
    }
}
