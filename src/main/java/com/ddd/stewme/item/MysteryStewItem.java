// MysteryStewItem.java
package com.ddd.stewme.item;

import com.ddd.stewme.data.CauldronData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义谜之炖菜物品，食用时应用存储的效果
 * 效果数据直接存储在Lore中，使用注册名进行解析
 * 更新：移除所有Logger输出
 */
public class MysteryStewItem extends Item {

    public MysteryStewItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (!level.isClientSide && livingEntity instanceof Player player) {
            // 从Lore中获取效果数据并应用
            Map<Holder<MobEffect>, EffectData> effects = getEffectsFromLore(stack);

            if (!effects.isEmpty()) {
                // 应用所有效果给玩家
                for (var entry : effects.entrySet()) {
                    EffectData effectData = entry.getValue();
                    player.addEffect(new MobEffectInstance(
                            entry.getKey(),
                            effectData.time,
                            effectData.level
                    ));
                }
            }
        }

        // 返回碗
        return new ItemStack(Items.BOWL);
    }

    /**
     * 从Lore中解析效果数据
     * 使用注册名来查找效果，避免本地化名称的问题
     */
    private Map<Holder<MobEffect>, EffectData> getEffectsFromLore(ItemStack stack) {
        Map<Holder<MobEffect>, EffectData> effects = new HashMap<>();

        net.minecraft.world.item.component.ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            List<Component> lines = lore.lines();

            for (Component line : lines) {
                String lineText = line.getString();

                // 解析效果行，格式: "效果名称 §8(等级:等级, 时间:时间秒) §7[注册名]"
                if (lineText.contains("§8(等级:") && lineText.contains("§7[")) {
                    // 提取注册名（在方括号中）
                    int startIndex = lineText.lastIndexOf("§7[") + 3;
                    int endIndex = lineText.lastIndexOf("]");
                    if (startIndex > 2 && endIndex > startIndex) {
                        String regName = lineText.substring(startIndex, endIndex);

                        // 解析等级和时间
                        int level = 0;
                        int time = 0;

                        // 提取等级
                        int levelStart = lineText.indexOf("等级:") + 3;
                        int levelEnd = lineText.indexOf(",", levelStart);
                        if (levelStart > 2 && levelEnd > levelStart) {
                            try {
                                // 等级0显示为1，所以解析时减1
                                level = Integer.parseInt(lineText.substring(levelStart, levelEnd).trim()) - 1;
                                if (level < 0) level = 0; // 确保不会出现负数
                            } catch (NumberFormatException e) {
                                // 等级解析失败，跳过这个效果
                                continue;
                            }
                        }

                        // 提取时间
                        int timeStart = lineText.indexOf("时间:") + 3;
                        int timeEnd = lineText.indexOf("秒", timeStart);
                        if (timeStart > 2 && timeEnd > timeStart) {
                            try {
                                time = Integer.parseInt(lineText.substring(timeStart, timeEnd).trim()) * 20; // 秒转换为tick
                            } catch (NumberFormatException e) {
                                // 时间解析失败，跳过这个效果
                                continue;
                            }
                        }

                        // 使用注册名查找效果
                        try {
                            ResourceLocation effectId = ResourceLocation.parse(regName);
                            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(effectId);
                            if (effect != null) {
                                Holder<MobEffect> effectHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
                                effects.put(effectHolder, new EffectData(level, time));
                            }
                        } catch (Exception e) {
                            // 注册名解析失败，跳过这个效果
                        }
                    }
                }
            }
        }

        return effects;
    }

    /**
     * 创建炖菜物品并设置显示名称和Lore（包含完整效果数据和注册名）
     * 等级0显示为1
     */
    public static ItemStack createMysteryStew(Map<Holder<MobEffect>, CauldronData.EffectData> effects) {
        ItemStack stack = new ItemStack(com.ddd.stewme.Registry.MYSTERY_STEW_ITEM.get());

        // 设置显示名称
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.stew_me.mystery_stew"));

        // 设置Lore，显示所有效果信息
        List<Component> loreLines = new ArrayList<>();

        // 标题
        loreLines.add(Component.translatable("item.stew_me.mystery_stew.lore.title"));

        // 添加每个效果的详细信息
        for (var entry : effects.entrySet()) {
            Holder<MobEffect> effect = entry.getKey();
            CauldronData.EffectData effectData = entry.getValue();

            // 获取效果的本地化名称
            String effectName = effect.value().getDisplayName().getString();

            // 获取效果的注册名
            String effectRegName = BuiltInRegistries.MOB_EFFECT.getKey(effect.value()).toString();

            // 计算时间（秒）
            int timeInSeconds = effectData.time / 20;

            // 等级0显示为1
            int displayLevel = effectData.level + 1;

            // 添加效果信息到Lore，包含注册名
            // 格式: "效果名称 §8(等级:等级, 时间:时间秒) §7[注册名]"
            String effectLine = String.format("§7%s §8(等级:%d, 时间:%d秒) §7[%s]",
                    effectName, displayLevel, timeInSeconds, effectRegName);
            loreLines.add(Component.literal(effectLine));
        }

        // 创建 ItemLore 对象
        net.minecraft.world.item.component.ItemLore lore = new net.minecraft.world.item.component.ItemLore(loreLines);
        stack.set(DataComponents.LORE, lore);

        return stack;
    }

    /**
     * 效果数据内部类
     */
    public static class EffectData {
        public final int level;
        public final int time;

        public EffectData(int level, int time) {
            this.level = level;
            this.time = time;
        }
    }
}