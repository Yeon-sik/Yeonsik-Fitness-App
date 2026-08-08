package com.yeonsik.fitnessapp.config;

import android.content.Context;

import com.yeonsik.fitnessapp.BuildConfig;

/** Separate Supabase connection and session storage for the nutrition catalog. */
public final class NutritionSupabaseConfigStore extends SupabaseConfigStore {
    public NutritionSupabaseConfigStore(Context context) {
        super(
                context,
                SupabaseStoreScope.NUTRITION,
                true,
                BuildConfig.NUTRITION_SUPABASE_URL,
                BuildConfig.NUTRITION_SUPABASE_ANON_KEY
        );
    }
}
