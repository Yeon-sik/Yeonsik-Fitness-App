package com.yeonsik.fitnessapp.feature.nutrition.data;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.data.CompositionGroup;
import com.yeonsik.fitnessapp.data.CompositionMember;
import com.yeonsik.fitnessapp.data.CompositionTemplate;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.NutritionUnit;
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogRepositoryApi;
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionTemplateRepositoryApi;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** Verifies that the nutrition catalog and template owners are reachable through public ports. */
@RunWith(AndroidJUnit4.class)
public final class NutritionCatalogPublicApiTest {
    private static final String OWNER = "nutrition-api-owner";
    private static final String OTHER_OWNER = "other-owner";

    @Test
    public void templateApiRoundTripsOwnerScopedDefinitionWithoutMealSnapshot() {
        Context context = ApplicationProvider.getApplicationContext();
        FitnessRoomDatabase database = Room.inMemoryDatabaseBuilder(
                context,
                FitnessRoomDatabase.class
        ).allowMainThreadQueries().build();
        try {
            NutritionCatalogRepositoryApi catalog = new NutritionCatalogRepository(database, OWNER);
            NutritionTemplateRepositoryApi templates = new NutritionTemplateRepository(
                    database,
                    catalog,
                    OWNER
            );
            CompositionMember member = new CompositionMember(
                    "template-member",
                    null,
                    "무가당 음료",
                    "테스트 브랜드",
                    1,
                    NutritionUnit.SERVING,
                    true,
                    0,
                    "catalog://drink",
                    NutritionProfile.ofMacros(0, 0, 0, 0)
            );
            CompositionTemplate template = new CompositionTemplate(
                    "template-api-roundtrip",
                    OWNER,
                    "테스트 세트",
                    CompositionTemplate.KIND_DINING_OUT,
                    null,
                    "template-source",
                    1,
                    Collections.singletonList(new CompositionGroup(
                            "template-group",
                            "drink",
                            CompositionGroup.GROUP_TYPE_BEVERAGE,
                            "음료",
                            CompositionGroup.MODE_EXACTLY_ONE,
                            1,
                            1,
                            0,
                            Collections.singletonList(member)
                    ))
            );

            assertEquals(template.id, templates.saveTemplate(template));
            CompositionTemplate loaded = templates.findTemplate(template.id);
            assertNotNull(loaded);
            assertEquals("drink", loaded.groups.get(0).key);
            assertEquals("template-member", loaded.groups.get(0).members.get(0).id);
            assertEquals(1, templates.listTemplates(CompositionTemplate.KIND_DINING_OUT).size());

            templates.setUserId(OTHER_OWNER);
            assertNull(templates.findTemplate(template.id));
        } finally {
            database.close();
        }
    }
}
