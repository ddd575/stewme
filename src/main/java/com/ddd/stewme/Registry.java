// Registry.java
package com.ddd.stewme;

import com.ddd.stewme.item.MysteryStewItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品注册表，注册自定义物品
 */
public class Registry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StewMe.MODID);

    // 注册谜之炖菜物品
    public static final DeferredItem<Item> MYSTERY_STEW_ITEM = ITEMS.register("mystery_stew",
            () -> new MysteryStewItem(new Item.Properties()
                    .food(new FoodProperties.Builder()
                            .nutrition(6)
                            .saturationModifier(0.6f)
                            .alwaysEdible()
                            .build())
                    .stacksTo(1)));
}