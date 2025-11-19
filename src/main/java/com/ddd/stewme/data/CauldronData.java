// CauldronData.java
package com.ddd.stewme.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * 炼药锅数据类，存储锅的位置和效果信息
 * 更新：移除所有Logger输出
 */
public class CauldronData {
    private final BlockPos pos;
    private final Map<Holder<MobEffect>, EffectData> effects = new HashMap<>();

    public CauldronData(BlockPos pos) {
        this.pos = pos;
    }

    public BlockPos getPos() {
        return pos;
    }

    public Map<Holder<MobEffect>, EffectData> getEffects() {
        return effects;
    }

    /**
     * 添加效果到锅数据中
     * 同一效果取最高等级，累计时间不超过72000tick
     */
    public void addEffect(Holder<MobEffect> effect, int level, int time) {
        EffectData existing = effects.get(effect);
        if (existing != null) {
            int newLevel = Math.max(existing.level, level);
            int newTime = Math.min(existing.time + time, 72000);
            effects.put(effect, new EffectData(newLevel, newTime));
        } else {
            effects.put(effect, new EffectData(level, Math.min(time, 72000)));
        }
    }

    /**
     * 每10tick减少效果时间1tick
     */
    public void tickEffects() {
        effects.entrySet().removeIf(entry -> {
            EffectData data = entry.getValue();
            data.time -= 1;
            return data.time <= 0;
        });
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());

        ListTag effectsList = new ListTag();
        for (Map.Entry<Holder<MobEffect>, EffectData> entry : effects.entrySet()) {
            CompoundTag effectTag = new CompoundTag();
            effectTag.putString("effect", BuiltInRegistries.MOB_EFFECT.getKey(entry.getKey().value()).toString());
            effectTag.putInt("level", entry.getValue().level);
            effectTag.putInt("time", entry.getValue().time);
            effectsList.add(effectTag);
        }
        tag.put("effects", effectsList);

        return tag;
    }

    public static CauldronData load(CompoundTag tag) {
        BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        CauldronData data = new CauldronData(pos);

        if (tag.contains("effects")) {
            ListTag effectsList = tag.getList("effects", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < effectsList.size(); i++) {
                CompoundTag effectTag = effectsList.getCompound(i);
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.parse(effectTag.getString("effect")));
                if (effect != null) {
                    // 获取效果的 Holder
                    Holder<MobEffect> effectHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
                    data.effects.put(effectHolder, new EffectData(effectTag.getInt("level"), effectTag.getInt("time")));
                }
            }
        }

        return data;
    }

    /**
     * 效果数据内部类
     */
    public static class EffectData {
        public final int level;
        public int time;

        public EffectData(int level, int time) {
            this.level = level;
            this.time = time;
        }
    }
}