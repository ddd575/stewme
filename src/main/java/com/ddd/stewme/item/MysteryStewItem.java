// MysteryStewItem.java
package com.ddd.stewme.item;

import com.ddd.stewme.data.CauldronData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
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
 * 更新：Lore显示格式为"效果名字 罗马数字 时间"，颜色根据效果分类区分
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
     * 新格式：效果名字 罗马数字 时间 §7[注册名]
     */
    private Map<Holder<MobEffect>, EffectData> getEffectsFromLore(ItemStack stack) {
        Map<Holder<MobEffect>, EffectData> effects = new HashMap<>();

        net.minecraft.world.item.component.ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            List<Component> lines = lore.lines();

            for (Component line : lines) {
                String lineText = line.getString();

                // 解析效果行，格式: "效果名字 罗马数字 时间 §7[注册名]"
                if (lineText.contains("§7[") && lineText.contains("]")) {
                    // 提取注册名（在方括号中）
                    int startIndex = lineText.lastIndexOf("§7[") + 3;
                    int endIndex = lineText.lastIndexOf("]");
                    if (startIndex > 2 && endIndex > startIndex) {
                        String regName = lineText.substring(startIndex, endIndex);

                        // 解析罗马数字等级和时间
                        int level = 0;
                        int time = 0;

                        try {
                            // 使用注册名查找效果
                            ResourceLocation effectId = ResourceLocation.parse(regName);
                            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(effectId);
                            if (effect != null) {
                                // 提取罗马数字并转换为等级
                                String[] parts = lineText.split(" ");
                                if (parts.length >= 3) {
                                    // 寻找罗马数字部分（通常在效果名称后面）
                                    for (int i = 0; i < parts.length; i++) {
                                        if (isRomanNumeral(parts[i])) {
                                            level = romanToInt(parts[i]) - 1; // 罗马数字从I开始，等级从0开始
                                            if (level < 0) level = 0;
                                            break;
                                        }
                                    }

                                    // 提取时间（MM:SS格式）
                                    for (int i = 0; i < parts.length; i++) {
                                        if (parts[i].contains(":") && parts[i].length() == 5) {
                                            String[] timeParts = parts[i].split(":");
                                            if (timeParts.length == 2) {
                                                try {
                                                    int minutes = Integer.parseInt(timeParts[0]);
                                                    int seconds = Integer.parseInt(timeParts[1]);
                                                    time = (minutes * 60 + seconds) * 20; // 转换为tick
                                                    break;
                                                } catch (NumberFormatException e) {
                                                    // 时间解析失败，使用默认值
                                                }
                                            }
                                        }
                                    }

                                    Holder<MobEffect> effectHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
                                    effects.put(effectHolder, new EffectData(level, time));
                                }
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
     * 创建炖菜物品并设置显示名称和Lore（新格式：效果名字 罗马数字 时间）
     * 颜色根据效果分类：有益-绿色，有害-红色，中性-灰色
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

            // 获取效果的本地化名称（使用翻译键）
            String effectName = Component.translatable(effect.value().getDescriptionId()).getString();

            // 获取效果的注册名
            String effectRegName = BuiltInRegistries.MOB_EFFECT.getKey(effect.value()).toString();

            // 转换为罗马数字（等级+1，因为等级0显示为I）
            String romanNumeral = intToRoman(effectData.level + 1);

            // 格式化时间（MM:SS）
            int totalSeconds = effectData.time / 20;
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            String timeFormatted = String.format("%02d:%02d", minutes, seconds);

            // 根据效果分类选择颜色
            String colorCode = getColorForEffect(effect.value());

            // 构建效果行：颜色 + 效果名字 + 空格 + 罗马数字 + 空格 + 时间 + §7[注册名]
            String effectLine = String.format("%s%s %s %s §7[%s]",
                    colorCode, effectName, romanNumeral, timeFormatted, effectRegName);
            loreLines.add(Component.literal(effectLine));
        }

        // 创建 ItemLore 对象
        net.minecraft.world.item.component.ItemLore lore = new net.minecraft.world.item.component.ItemLore(loreLines);
        stack.set(DataComponents.LORE, lore);

        return stack;
    }

    /**
     * 根据效果分类返回颜色代码
     * 有益效果：§a（绿色）
     * 有害效果：§c（红色）
     * 中性效果：§7（灰色）
     */
    private static String getColorForEffect(MobEffect effect) {
        MobEffectCategory category = effect.getCategory();
        switch (category) {
            case BENEFICIAL:
                return "§a"; // 绿色
            case HARMFUL:
                return "§c"; // 红色
            case NEUTRAL:
            default:
                return "§7"; // 灰色
        }
    }

    /**
     * 将整数转换为罗马数字
     */
    private static String intToRoman(int num) {
        if (num < 1 || num > 10) {
            return String.valueOf(num); // 超出范围返回数字
        }

        String[] romanNumerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return romanNumerals[num - 1];
    }

    /**
     * 将罗马数字转换为整数
     */
    private static int romanToInt(String roman) {
        switch (roman) {
            case "I": return 1;
            case "II": return 2;
            case "III": return 3;
            case "IV": return 4;
            case "V": return 5;
            case "VI": return 6;
            case "VII": return 7;
            case "VIII": return 8;
            case "IX": return 9;
            case "X": return 10;
            default: return 1; // 默认值
        }
    }

    /**
     * 检查字符串是否为罗马数字
     */
    private static boolean isRomanNumeral(String str) {
        return str.matches("^[IVX]+$");
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