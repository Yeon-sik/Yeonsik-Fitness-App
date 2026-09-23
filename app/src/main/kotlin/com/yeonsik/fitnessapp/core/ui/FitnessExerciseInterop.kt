package com.yeonsik.fitnessapp.core.ui

import android.app.Activity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.yeonsik.fitness.shared.feature.exercise.model.ExerciseFamilyIdentity
import com.yeonsik.fitnessapp.ui.ExerciseIllustrationPreview
import com.yeonsik.fitnessapp.ui.ExerciseMuscleModelRenderer
import com.yeonsik.fitnessapp.ui.FitnessUi
import java.util.function.BooleanSupplier

/**
 * Compose bridge for the existing preview renderer. The renderer and the ImageView are both
 * remembered, while a changed exercise/variant intentionally creates the corresponding new view.
 * The AndroidView update block is deliberately empty: rendering has no recomposition side effect.
 */
@Composable
fun FitnessExerciseIllustration(
    activity: Activity,
    exerciseId: String,
    modifier: Modifier = Modifier,
    dark: Boolean = isSystemInDarkTheme(),
    contentDescription: String? = null,
    fallback: (@Composable () -> Unit)? = null
) {
    val preview = rememberExerciseIllustrationPreview(activity, dark)
    val imageView = remember(preview, exerciseId) {
        preview.create(exerciseId)
    }
    FitnessIllustrationView(
        imageView = imageView,
        viewKey = listOf(preview, exerciseId),
        modifier = modifier,
        contentDescription = contentDescription,
        fallback = fallback
    )
}

/**
 * Compose bridge for family/preset/variant-aware illustration lookup. Exact lookup is opt-in so a
 * caller can distinguish an exact visual variant from the existing family-default fallback.
 */
@Composable
fun FitnessExerciseIllustration(
    activity: Activity,
    identity: ExerciseFamilyIdentity,
    exactVariant: Boolean = false,
    modifier: Modifier = Modifier,
    dark: Boolean = isSystemInDarkTheme(),
    contentDescription: String? = null,
    fallback: (@Composable () -> Unit)? = null
) {
    val preview = rememberExerciseIllustrationPreview(activity, dark)
    val identityKey = exerciseIdentityKey(identity)
    val imageView = remember(preview, identityKey, exactVariant) {
        if (exactVariant) preview.createExact(identity) else preview.create(identity)
    }
    FitnessIllustrationView(
        imageView = imageView,
        viewKey = listOf(preview, identityKey, exactVariant),
        modifier = modifier,
        contentDescription = contentDescription,
        fallback = fallback
    )
}

/** Shows only the representative illustration assigned to an exercise family. */
@Composable
fun FitnessExerciseFamilyIllustration(
    activity: Activity,
    familyId: String,
    modifier: Modifier = Modifier,
    dark: Boolean = isSystemInDarkTheme(),
    contentDescription: String? = null,
    fallback: (@Composable () -> Unit)? = null
) {
    val preview = rememberExerciseIllustrationPreview(activity, dark)
    val imageView = remember(preview, familyId) {
        preview.createFamilyDefault(familyId)
    }
    FitnessIllustrationView(
        imageView = imageView,
        viewKey = listOf(preview, familyId),
        modifier = modifier,
        contentDescription = contentDescription,
        fallback = fallback
    )
}

/**
 * Compose bridge for the existing front/back muscle model. The adapter accepts only the renderer's
 * existing primarySubPart keys; it does not infer anatomy or calculate training scores.
 */
@Composable
fun FitnessExerciseMuscleModel(
    activity: Activity,
    primarySubParts: Iterable<String> = emptyList(),
    modifier: Modifier = Modifier,
    dark: Boolean = isSystemInDarkTheme(),
    contentDescription: String? = null
) {
    val renderer = rememberExerciseMuscleModelRenderer(activity, dark)
    val normalizedSubParts = primarySubParts
        .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .distinct()
        .sorted()
    val selectionKey = normalizedSubParts.joinToString("\u001F")
    val modelView = remember(renderer, selectionKey) {
        renderer.render(normalizedSubParts)
    }
    key(renderer, selectionKey) {
        AndroidView(
            factory = { modelView },
            modifier = modifier.withVisualContentDescription(contentDescription),
            update = { }
        )
    }
}

@Composable
private fun rememberExerciseIllustrationPreview(
    activity: Activity,
    dark: Boolean
): ExerciseIllustrationPreview {
    val ui = remember(activity, dark) {
        FitnessUi(activity, BooleanSupplier { dark })
    }
    return remember(activity, ui) {
        ExerciseIllustrationPreview(activity, ui)
    }
}

@Composable
private fun rememberExerciseMuscleModelRenderer(
    activity: Activity,
    dark: Boolean
): ExerciseMuscleModelRenderer {
    val ui = remember(activity, dark) {
        FitnessUi(activity, BooleanSupplier { dark })
    }
    return remember(activity, ui) {
        ExerciseMuscleModelRenderer(activity, ui)
    }
}

@Composable
private fun FitnessIllustrationView(
    imageView: ImageView?,
    viewKey: Any,
    modifier: Modifier,
    contentDescription: String?,
    fallback: (@Composable () -> Unit)?
) {
    key(viewKey) {
        if (imageView == null) {
            if (fallback != null) {
                fallback()
            } else {
                Box(modifier = modifier.withVisualContentDescription(contentDescription))
            }
        } else {
            AndroidView(
                factory = { imageView },
                modifier = modifier.withVisualContentDescription(contentDescription),
                update = { }
            )
        }
    }
}

private fun Modifier.withVisualContentDescription(description: String?): Modifier =
    if (description.isNullOrBlank()) {
        this
    } else {
        semantics { contentDescription = description }
    }

private fun exerciseIdentityKey(identity: ExerciseFamilyIdentity): String = listOf(
    identity.legacyExerciseId,
    identity.familyId,
    identity.presetId,
    identity.canonicalPresetId,
    identity.defaultUiPart,
    identity.canonicalVariantKey,
    identity.visualVariantKey,
    identity.illustrationKey,
    identity.variantJson
).joinToString("\u001F")
