package com.yeonsik.fitnessapp.config;

import android.content.Context;

import com.yeonsik.fitnessapp.BuildConfig;

/** Independent, anonymous read-only connection to the PriceTrace product catalog. */
public final class PriceTraceSupabaseConfigStore extends SupabaseConfigStore {
    public PriceTraceSupabaseConfigStore(Context context) {
        super(
                context,
                SupabaseStoreScope.PRICETRACE,
                AppSurfacePolicy.allowsManagedSupabaseDefaults(),
                BuildConfig.PRICETRACE_SUPABASE_URL,
                BuildConfig.PRICETRACE_SUPABASE_ANON_KEY
        );
    }
}
