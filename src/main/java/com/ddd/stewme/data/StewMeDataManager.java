// StewMeDataManager.java
package com.ddd.stewme.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;

/**
 * 锅数据管理器，负责锅数据的存储与持久化
 * 不再管理碗数据，所有效果数据直接存储在物品Lore中
 * 更新：移除所有Logger输出
 */
public class StewMeDataManager extends SavedData {
    private static final String DATA_NAME = "stew_me_data";
    private final Map<String, CauldronData> cauldronData = new HashMap<>();

    /**
     * 根据位置获取锅数据
     * @param pos 炼药锅的位置
     * @return 对应的锅数据，如果不存在则返回null
     */
    public CauldronData getCauldronData(BlockPos pos) {
        String key = posToString(pos);
        return cauldronData.get(key);
    }

    /**
     * 添加或更新锅数据
     * @param data 要添加的锅数据
     */
    public void putCauldronData(CauldronData data) {
        String key = posToString(data.getPos());
        cauldronData.put(key, data);
        setDirty();
    }

    /**
     * 移除指定位置的锅数据
     * @param pos 要移除的炼药锅位置
     */
    public void removeCauldronData(BlockPos pos) {
        String key = posToString(pos);
        cauldronData.remove(key);
        setDirty();
    }

    /**
     * 每10tick执行一次自然衰减 - 只对锅列表中的效果进行衰减
     */
    public void tick() {
        // 使用迭代器来安全地移除空数据
        var iterator = cauldronData.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            CauldronData data = entry.getValue();
            data.tickEffects();

            // 如果效果列表为空，移除这个锅数据
            if (data.getEffects().isEmpty()) {
                iterator.remove();
            }
        }

        setDirty();
    }

    /**
     * 保存数据到NBT标签
     */
    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        // 保存锅数据
        ListTag cauldronList = new ListTag();
        for (CauldronData data : cauldronData.values()) {
            cauldronList.add(data.save());
        }
        tag.put("cauldrons", cauldronList);

        return tag;
    }

    /**
     * 从NBT标签加载数据
     */
    public static StewMeDataManager load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        StewMeDataManager manager = new StewMeDataManager();

        // 加载锅数据
        if (tag.contains("cauldrons")) {
            ListTag cauldronList = tag.getList("cauldrons", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < cauldronList.size(); i++) {
                CompoundTag cauldronTag = cauldronList.getCompound(i);
                CauldronData data = CauldronData.load(cauldronTag);
                manager.cauldronData.put(posToString(data.getPos()), data);
            }
        }

        return manager;
    }

    /**
     * 获取或创建数据管理器实例
     * @param level 服务器世界
     * @return 数据管理器实例
     */
    public static StewMeDataManager get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(
                        StewMeDataManager::new,
                        StewMeDataManager::load,
                        null
                ),
                DATA_NAME
        );
    }

    /**
     * 获取所有锅数据的集合
     * @return 所有锅数据的集合
     */
    public java.util.Collection<CauldronData> getAllCauldronData() {
        return java.util.Collections.unmodifiableCollection(cauldronData.values());
    }

    /**
     * 将位置转换为字符串键
     */
    private static String posToString(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}