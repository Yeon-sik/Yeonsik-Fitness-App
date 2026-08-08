package com.yeonsik.fitnessapp.config;

/** Independent persistence namespaces for the shared and Nutrition Supabase sessions. */
enum SupabaseStoreScope {
    SHARED(
            "fitnessapp:supabase-config:v1",
            "fitnessapp_supabase_session_v1",
            "fitnessapp:secure-session:v1"
    ),
    NUTRITION(
            "fitnessapp:nutrition-supabase-config:v1",
            "fitnessapp_supabase_nutrition_session_v1",
            "fitnessapp:secure-nutrition-session:v1"
    );

    final String configPreferencesName;
    final String tokenKeyAlias;
    final String tokenPreferencesName;

    SupabaseStoreScope(
            String configPreferencesName,
            String tokenKeyAlias,
            String tokenPreferencesName
    ) {
        this.configPreferencesName = configPreferencesName;
        this.tokenKeyAlias = tokenKeyAlias;
        this.tokenPreferencesName = tokenPreferencesName;
    }
}
