package com.yeonsik.fitnessapp.config;

import android.content.Context;

/** Separate Supabase connection and session storage for the nutrition catalog. */
public final class NutritionSupabaseConfigStore extends SupabaseConfigStore {
    public NutritionSupabaseConfigStore(Context context) {
        super(
                context,
                SupabaseStoreScope.NUTRITION,
                false,
                "",
                ""
        );
    }
}
