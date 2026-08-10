package com.yeonsik.fitnessapp.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One top-level menu eaten in a meal and the optional ingredients that composed it.
 *
 * <p>The relationship defines the role: an egg can be a standalone menu in one meal and an
 * ingredient of fried rice in another. Catalog {@link NutritionFood#kind} remains useful for
 * search and presentation, but it does not replace this meal-specific hierarchy.</p>
 */
public final class MealMenuSelection {
    public final MealCompositionItem menu;
    public final List<MealCompositionItem> components;

    private MealMenuSelection(
            MealCompositionItem menu,
            List<MealCompositionItem> components
    ) {
        if (menu == null || menu.food == null) {
            throw new IllegalArgumentException("Menu is required.");
        }
        this.menu = menu;
        List<MealCompositionItem> copied = new ArrayList<>();
        if (components != null) {
            for (MealCompositionItem component : components) {
                if (component == null || component.food == null) {
                    throw new IllegalArgumentException("Menu contains an empty ingredient.");
                }
                copied.add(component);
            }
        }
        this.components = Collections.unmodifiableList(copied);
    }

    /** Treats a catalog food as one menu in this meal, regardless of its catalog classification. */
    public static MealMenuSelection standalone(MealCompositionItem menu) {
        return new MealMenuSelection(menu, Collections.emptyList());
    }

    /** Creates a homemade or saved recipe menu with its ingredient-level snapshot source. */
    public static MealMenuSelection composed(
            MealCompositionItem menu,
            List<MealCompositionItem> components
    ) {
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("A composed menu needs at least one ingredient.");
        }
        return new MealMenuSelection(menu, components);
    }

    /** Returns the same menu at another quantity and scales every ingredient by the same ratio. */
    public MealMenuSelection withQuantity(double quantity) {
        MealCompositionItem resizedMenu = MealCompositionItem.from(menu.food, quantity);
        if (components.isEmpty()) {
            return standalone(resizedMenu);
        }
        double scale = resizedMenu.quantity / menu.quantity;
        List<MealCompositionItem> resizedComponents = new ArrayList<>();
        for (MealCompositionItem component : components) {
            resizedComponents.add(MealCompositionItem.from(
                    component.food,
                    component.quantity * scale
            ));
        }
        return composed(resizedMenu, resizedComponents);
    }

    public static List<MealCompositionItem> menuItems(List<MealMenuSelection> menus) {
        List<MealCompositionItem> items = new ArrayList<>();
        if (menus == null) {
            return items;
        }
        for (MealMenuSelection menu : menus) {
            if (menu != null) {
                items.add(menu.menu);
            }
        }
        return items;
    }
}
