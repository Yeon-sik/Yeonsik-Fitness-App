package com.yeonsik.fitnessapp.feature.nutrition.data;

import com.yeonsik.fitnessapp.core.database.CompositionGroupsRoomEntity;
import com.yeonsik.fitnessapp.core.database.CompositionMembersRoomEntity;
import com.yeonsik.fitnessapp.core.database.CompositionTemplateRoomDao;
import com.yeonsik.fitnessapp.core.database.CompositionTemplatesRoomEntity;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.data.CompositionGroup;
import com.yeonsik.fitnessapp.data.CompositionMember;
import com.yeonsik.fitnessapp.data.CompositionTemplate;
import com.yeonsik.fitnessapp.data.NutritionCalculator;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogRepositoryApi;
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionTemplateRepositoryApi;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Room-backed owner-scoped repository for mutable nutrition/menu templates.
 *
 * <p>This repository owns only catalog definitions. A meal write must resolve these definitions
 * into the Meal-owned snapshot tables before a historical record is created.</p>
 */
public final class NutritionTemplateRepository implements NutritionTemplateRepositoryApi {
    private static final String DEVICE_ID = "android-local";

    private final FitnessRoomDatabase roomDatabase;
    private final CompositionTemplateRoomDao templateDao;
    private final NutritionCatalogRepositoryApi nutritionCatalog;
    private volatile String userId;

    public NutritionTemplateRepository(
            FitnessRoomDatabase roomDatabase,
            NutritionCatalogRepositoryApi nutritionCatalog,
            String userId
    ) {
        if (roomDatabase == null || nutritionCatalog == null) {
            throw new IllegalArgumentException("Nutrition template dependencies are required.");
        }
        this.roomDatabase = roomDatabase;
        this.templateDao = roomDatabase.compositionTemplateRoomDao();
        this.nutritionCatalog = nutritionCatalog;
        this.userId = normalizeUserId(userId);
    }

    @Override
    public void setUserId(String userId) {
        this.userId = normalizeUserId(userId);
    }

    @Override
    public CompositionTemplate findTemplate(String templateId) {
        String normalizedId = normalizeNullable(templateId);
        if (normalizedId == null) {
            return null;
        }
        CompositionTemplatesRoomEntity entity = templateDao.visibleTemplate(normalizedId, userId);
        return entity == null ? null : readTemplate(entity);
    }

    @Override
    public List<CompositionTemplate> listTemplates(String kind) {
        String normalizedKind = normalizeNullable(kind);
        List<CompositionTemplate> templates = new ArrayList<>();
        for (CompositionTemplatesRoomEntity entity : templateDao.visibleTemplates(userId, normalizedKind)) {
            templates.add(readTemplate(entity));
        }
        return Collections.unmodifiableList(templates);
    }

    @Override
    public String saveTemplate(CompositionTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("Composition template is required.");
        }
        if (!userId.equals(template.userId)) {
            throw new IllegalArgumentException("Composition template belongs to another user.");
        }
        String now = OffsetDateTime.now().toString();
        roomDatabase.runInTransaction(() -> {
            templateDao.tombstoneMembers(template.id, userId, now, now);
            templateDao.tombstoneGroups(template.id, userId, now, now);
            templateDao.tombstoneTemplate(template.id, userId, now, now);
            templateDao.replaceTemplate(new CompositionTemplatesRoomEntity(
                    template.id,
                    userId,
                    template.name,
                    template.kind,
                    template.rootFoodId,
                    template.sourceReference,
                    (long) template.revision,
                    now,
                    now,
                    null,
                    DEVICE_ID
            ));
            for (CompositionGroup group : template.groups) {
                templateDao.replaceGroup(new CompositionGroupsRoomEntity(
                        group.id,
                        userId,
                        template.id,
                        group.key,
                        group.groupType,
                        group.label,
                        group.selectionMode,
                        (long) group.minSelected,
                        (long) group.maxSelected,
                        (long) group.orderIndex,
                        now,
                        now,
                        null,
                        DEVICE_ID
                ));
                for (CompositionMember member : group.members) {
                    templateDao.replaceMember(new CompositionMembersRoomEntity(
                            member.id,
                            userId,
                            template.id,
                            group.id,
                            member.nutritionFoodId,
                            member.name,
                            member.brand,
                            member.quantity,
                            member.unit,
                            member.defaultSelected ? 1L : 0L,
                            (long) member.orderIndex,
                            member.sourceReference,
                            now,
                            now,
                            null,
                            DEVICE_ID
                    ));
                }
            }
        });
        return template.id;
    }

    @Override
    public void deleteTemplate(String templateId) {
        String normalizedId = normalizeNullable(templateId);
        if (normalizedId == null) {
            return;
        }
        String now = OffsetDateTime.now().toString();
        roomDatabase.runInTransaction(() -> {
            templateDao.tombstoneMembers(normalizedId, userId, now, now);
            templateDao.tombstoneGroups(normalizedId, userId, now, now);
            templateDao.tombstoneTemplate(normalizedId, userId, now, now);
        });
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    private CompositionTemplate readTemplate(CompositionTemplatesRoomEntity entity) {
        List<CompositionGroup> groups = new ArrayList<>();
        for (CompositionGroupsRoomEntity group : templateDao.visibleGroups(entity.getId(), userId)) {
            List<CompositionMember> members = new ArrayList<>();
            for (CompositionMembersRoomEntity member : templateDao.visibleMembers(group.getId(), userId)) {
                NutritionFood food = member.getNutritionFoodId() == null
                        ? null
                        : nutritionCatalog.findFoodById(member.getNutritionFoodId());
                NutritionProfile profile = food == null
                        ? NutritionProfile.empty()
                        : NutritionCalculator.forQuantity(food, member.getQuantity());
                members.add(new CompositionMember(
                        member.getId(),
                        member.getNutritionFoodId(),
                        member.getNameSnapshot(),
                        member.getBrandSnapshot(),
                        member.getQuantity(),
                        member.getUnit(),
                        member.getDefaultSelected() != 0,
                        safeInt(member.getOrderIndex()),
                        member.getSourceReferenceSnapshot(),
                        profile
                ));
            }
            groups.add(new CompositionGroup(
                    group.getId(),
                    group.getGroupKey(),
                    group.getGroupType(),
                    group.getLabel(),
                    group.getSelectionMode(),
                    safeInt(group.getMinSelected()),
                    safeInt(group.getMaxSelected()),
                    safeInt(group.getOrderIndex()),
                    members
            ));
        }
        return new CompositionTemplate(
                entity.getId(),
                entity.getUserId(),
                entity.getName(),
                entity.getTemplateKind(),
                entity.getRootFoodId(),
                entity.getSourceReference(),
                safePositiveInt(entity.getRevision()),
                groups
        );
    }

    private static int safeInt(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }

    private static int safePositiveInt(long value) {
        return Math.max(1, safeInt(value));
    }

    private static String normalizeUserId(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "local-user" : normalized;
    }

    private static String normalizeNullable(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
