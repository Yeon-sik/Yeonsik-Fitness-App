package com.yeonsik.fitnessapp.feature.workout.api;

import com.yeonsik.fitnessapp.data.FleekCsvImporter;
import com.yeonsik.fitnessapp.data.WorkoutTransferCodec;
import com.yeonsik.fitnessapp.integration.workout.WorkoutInterchangeResult;

/** Feature-owned persistence boundary for workout transfer and FLEEK imports. */
public interface WorkoutInterchangeApi {
    WorkoutTransferCodec.Document exportTransfer(String ownerId);

    WorkoutInterchangeResult importTransfer(
            String ownerId,
            WorkoutTransferCodec.Document document
    );

    WorkoutInterchangeResult importFleek(
            String ownerId,
            FleekCsvImporter.ImportPlan plan
    );
}
