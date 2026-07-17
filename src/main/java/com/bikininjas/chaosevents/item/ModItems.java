package com.bikininjas.chaosevents.item;

import com.bikininjas.chaosevents.ChaosEventsMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Central registry of all Chaos Events items.
 */
public final class ModItems {

    public static final DeferredRegister.Items MOD_ITEMS =
            DeferredRegister.createItems(ChaosEventsMod.MOD_ID);

    private ModItems() {
    }

    // -- Items -----------------------------------------------------------------

    /** Item: right-click to trigger a random chaos event at the player's location. */
    public static final DeferredItem<Item> CHAOS_ORB = MOD_ITEMS.registerItem("chaos_orb",
            props -> new ChaosOrbItem(props.stacksTo(1)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
}
