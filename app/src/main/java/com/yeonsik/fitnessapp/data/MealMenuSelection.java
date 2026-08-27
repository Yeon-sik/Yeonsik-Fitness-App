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
    /** Selected dining-out options that describe the component snapshots for this menu item. */
    public final List<DiningOutOption> diningOutOptions;

    private MealMenuSelection(
            MealCompositionItem menu,
            List<MealCompositionItem> components,
            List<DiningOutOption> diningOutOptions
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
        List<DiningOutOption> copiedOptions = new ArrayList<>();
        if (diningOutOptions != null) {
            for (DiningOutOption option : diningOutOptions) {
                if (option == null) {
                    throw new IllegalArgumentException("Dining-out menu contains an empty option.");
                }
                copiedOptions.add(option);
            }
        }
        if (!copiedOptions.isEmpty() && copiedOptions.size() != copied.size()) {
            throw new IllegalArgumentException(
                    "Dining-out options must match the menu component count."
            );
        }
        this.diningOutOptions = Collections.unmodifiableList(copiedOptions);
    }

    /** Treats a catalog food as one menu in this meal, regardless of its catalog classification. */
    public static MealMenuSelection standalone(MealCompositionItem menu) {
        return new MealMenuSelection(menu, Collections.emptyList(), Collections.emptyList());
    }

    /** Creates a homemade or saved recipe menu with its ingredient-level snapshot source. */
    public static MealMenuSelection composed(
            MealCompositionItem menu,
            List<MealCompositionItem> components
    ) {
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("A composed menu needs at least one ingredient.");
        }
        return new MealMenuSelection(menu, components, Collections.emptyList());
    }

    /** Creates one dining-out menu and keeps each selected option's group metadata. */
    public static MealMenuSelection diningOut(
            MealCompositionItem menu,
            String ownerId,
            String restaurantName,
            List<DiningOutOption> options
    ) {
        List<MealCompositionItem> components = new ArrayList<>();
        if (options != null) {
            for (DiningOutOption option : options) {
                if (option == null) {
                    throw new IllegalArgumentException("Dining-out menu contains an empty option.");
                }
                components.add(option.asMealCompositionItem(ownerId, restaurantName));
            }
        }
        return new MealMenuSelection(menu, components, options);
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
        return diningOutOptions.isEmpty()
                ? composed(resizedMenu, resizedComponents)
                : new MealMenuSelection(resizedMenu, resizedComponents, diningOutOptions);
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
