// StewMeEventHandler.java
package com.ddd.stewme.handlers;

import com.ddd.stewme.StewMe;
import com.ddd.stewme.data.StewMeDataManager;
import com.ddd.stewme.data.CauldronData;
import com.ddd.stewme.item.MysteryStewItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 核心事件处理器，处理炼药锅效果收集系统的所有事件
 * 包括玩家与炼药锅的交互、效果收集、炖菜制作等
 * 更新：使用Lore存储效果数据，不再使用碗数据管理器
 * 更新：锅有数据时持续产生粒子效果
 * 更新：当玩家没有效果时停止处理，优化性能
 * 更新：移除所有Logger，聊天栏消息使用国际化翻译
 */
public class StewMeEventHandler {

    private int tickCounter = 0;
    private final Map<UUID, Long> playerCooldowns = new HashMap<>();
    private static final long COOLDOWN_TICKS = 60; // 3秒 = 60tick

    /**
     * 世界tick事件处理，用于执行自然衰减和粒子效果
     * 更新：锅有数据时持续产生粒子效果
     * @param event 世界tick事件
     */
    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            tickCounter++;

            // 每10tick执行一次自然衰减和粒子效果
            if (tickCounter >= 10) {
                tickCounter = 0;
                StewMeDataManager manager = StewMeDataManager.get(serverLevel);
                manager.tick();

                // 为所有有数据的锅产生粒子效果
                generateParticlesForAllCauldrons(serverLevel, manager);
            }
        }
    }

    /**
     * 为所有有数据的锅产生粒子效果
     * @param serverLevel 服务器世界
     * @param manager 数据管理器
     */
    private void generateParticlesForAllCauldrons(ServerLevel serverLevel, StewMeDataManager manager) {
        // 获取所有锅数据
        for (CauldronData data : manager.getAllCauldronData()) {
            // 如果锅有数据，产生粒子效果
            if (!data.getEffects().isEmpty()) {
                generateCauldronParticles(serverLevel, data.getPos());
            }
        }
    }

    /**
     * 玩家tick事件处理，检测玩家是否在水炼药锅中并处理效果收集
     * 添加了3秒冷却机制优化性能
     * 更新：玩家在锅内每tick减少30tick效果
     * 更新：当玩家没有效果时停止处理，优化性能
     * @param event 玩家tick事件
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level() instanceof ServerLevel serverLevel) {
            UUID playerId = player.getUUID();
            long currentTick = serverLevel.getGameTime();

            // 检查冷却时间
            if (playerCooldowns.containsKey(playerId)) {
                long cooldownUntil = playerCooldowns.get(playerId);
                if (currentTick < cooldownUntil) {
                    // 还在冷却期内，跳过处理
                    return;
                } else {
                    // 冷却期结束，移除记录
                    playerCooldowns.remove(playerId);
                }
            }

            // 获取玩家所在的位置
            BlockPos playerPos = player.blockPosition();

            // 获取玩家所在位置的方块状态
            BlockState playerBlockState = serverLevel.getBlockState(playerPos);

            // 检查玩家是否在水炼药锅中
            if (playerBlockState.getBlock() == Blocks.WATER_CAULDRON) {
                int waterLevel = playerBlockState.getValue(LayeredCauldronBlock.LEVEL);

                // 炼药锅必须水位为3
                if (waterLevel == 3) {
                    BlockPos belowCauldron = playerPos.below();
                    BlockState belowState = serverLevel.getBlockState(belowCauldron);

                    StewMeDataManager manager = StewMeDataManager.get(serverLevel);
                    CauldronData cauldronData = manager.getCauldronData(playerPos);

                    // 锅有数据时产生粒子效果（无论下方是什么方块）
                    if (cauldronData != null && !cauldronData.getEffects().isEmpty()) {
                        generateCauldronParticles(serverLevel, playerPos);
                    }

                    // 检查玩家是否有效果，如果没有则跳过处理
                    if (player.getActiveEffects().isEmpty()) {
                        // 玩家没有效果，设置冷却时间后返回
                        playerCooldowns.put(playerId, currentTick + COOLDOWN_TICKS);
                        return;
                    }

                    // 每tick执行一次效果处理
                    if (belowState.getBlock() == Blocks.CAMPFIRE && belowState.getValue(CampfireBlock.LIT)) {
                        // 下方为营火：减少玩家身上debuff时间
                        reduceDebuffTime(player);
                    } else if (belowState.getBlock() == Blocks.MAGMA_BLOCK || belowState.getBlock() == Blocks.LAVA) {
                        // 下方为岩浆块或岩浆：减少玩家身上所有效果时间并累加到锅数据中
                        if (cauldronData == null) {
                            cauldronData = new CauldronData(playerPos);
                            manager.putCauldronData(cauldronData);
                        }

                        reduceAndAccumulateEffects(player, cauldronData);
                    }
                }
            } else {
                // 玩家不在锅内，设置3秒冷却时间
                playerCooldowns.put(playerId, currentTick + COOLDOWN_TICKS);
            }
        }
    }

    /**
     * 为有数据的锅产生粒子效果
     * 生成三种粒子：electric_spark、effect、bubble
     * @param serverLevel 服务器世界
     * @param cauldronPos 锅的位置
     */
    private void generateCauldronParticles(ServerLevel serverLevel, BlockPos cauldronPos) {
        // 在锅的位置周围产生三种粒子效果
        for (int i = 0; i < 3; i++) {
            double xOffset = (serverLevel.random.nextDouble() - 0.5) * 0.8;
            double yOffset = serverLevel.random.nextDouble() * 0.5;
            double zOffset = (serverLevel.random.nextDouble() - 0.5) * 0.8;

            // 生成 electric_spark 粒子
            serverLevel.sendParticles(
                    ParticleTypes.FISHING,
                    cauldronPos.getX() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.8,
                    cauldronPos.getY() + 1.15,
                    cauldronPos.getZ() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.8,
                    1, 0, 0, 0, 0
            );

            // 生成 effect 粒子
            serverLevel.sendParticles(
                    ParticleTypes.EFFECT,
                    cauldronPos.getX() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.8,
                    cauldronPos.getY() + 0.3 + yOffset,
                    cauldronPos.getZ() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.8,
                    1, 0, 0, 0, 0
            );

            // 生成 bubble 粒子
            serverLevel.sendParticles(
                    ParticleTypes.EFFECT,
                    cauldronPos.getX() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.8,
                    cauldronPos.getY() + 0.3 + yOffset,
                    cauldronPos.getZ() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.8,
                    1, 0, 0, 0, 0
            );
        }
    }

    /**
     * 右键点击方块事件处理，处理碗右键炼药锅和防止取水
     * 更新：创建炖菜时直接将效果数据存储在Lore中
     * 更新：聊天栏消息使用国际化翻译
     * @param event 右键点击方块事件
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            BlockPos pos = event.getPos();
            BlockState state = serverLevel.getBlockState(pos);

            // 检查点击的是水炼药锅
            if (state.getBlock() == Blocks.WATER_CAULDRON) {
                StewMeDataManager manager = StewMeDataManager.get(serverLevel);
                CauldronData data = manager.getCauldronData(pos);

                // 防止取水：有数据的锅不能被取水
                if (data != null) {
                    BlockPos belowPos = pos.below();
                    BlockState belowState = serverLevel.getBlockState(belowPos);

                    if (belowState.getBlock() == Blocks.MAGMA_BLOCK || belowState.getBlock() == Blocks.LAVA) {
                        ItemStack item = event.getItemStack();
                        if (item.getItem() == Items.GLASS_BOTTLE || item.getItem() == Items.BUCKET) {
                            // 使用国际化翻译的聊天栏消息
                            event.getEntity().displayClientMessage(
                                    Component.translatable("message.stew_me.use_bowl_instead"),
                                    false
                            );
                            event.setCanceled(true);
                            return;
                        }
                    }
                }

                // 处理碗右键炼药锅
                ItemStack heldItem = event.getItemStack();
                if (heldItem.getItem() == Items.BOWL && !event.getEntity().isCrouching()) {
                    int waterLevel = state.getValue(LayeredCauldronBlock.LEVEL);

                    // 必须有水和效果数据
                    if (waterLevel == 3 && data != null && !data.getEffects().isEmpty()) {
                        // 创建谜之炖菜，效果数据直接存储在Lore中
                        ItemStack mysteryStew = MysteryStewItem.createMysteryStew(data.getEffects());

                        // 移除锅数据并消耗水和碗
                        manager.removeCauldronData(pos);
                        serverLevel.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);

                        // 给予玩家炖菜并消耗碗
                        if (!event.getEntity().getInventory().add(mysteryStew)) {
                            event.getEntity().drop(mysteryStew, false);
                        }
                        heldItem.shrink(1);

                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    /**
     * 方块破坏事件处理，水炼药锅被破坏时移除数据
     * @param event 方块破坏事件
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            // 检查破坏的是水炼药锅
            if (event.getState().getBlock() == Blocks.WATER_CAULDRON) {
                StewMeDataManager manager = StewMeDataManager.get(serverLevel);
                manager.removeCauldronData(event.getPos());
            }
        }
    }

    /**
     * 减少玩家身上debuff的时间（30tick）
     * 更新：如果玩家没有debuff，则跳过处理
     * @param player 要处理的玩家
     */
    private void reduceDebuffTime(Player player) {
        // 检查玩家是否有debuff，如果没有则直接返回
        boolean hasDebuff = false;
        for (MobEffectInstance effect : player.getActiveEffects()) {
            MobEffect mobEffect = effect.getEffect().value();
            if (mobEffect.getCategory() == MobEffectCategory.HARMFUL) {
                hasDebuff = true;
                break;
            }
        }

        if (!hasDebuff) {
            return; // 玩家没有debuff，跳过处理
        }

        // 创建一个列表来存储需要更新的效果，避免在迭代时修改集合
        java.util.List<MobEffectInstance> effectsToUpdate = new java.util.ArrayList<>();

        // 首先收集所有需要更新的效果
        for (MobEffectInstance effect : player.getActiveEffects()) {
            MobEffect mobEffect = effect.getEffect().value();

            // 使用 MobEffectCategory 来检测有害效果
            if (mobEffect.getCategory() == MobEffectCategory.HARMFUL) {
                effectsToUpdate.add(effect);
            }
        }

        // 然后更新这些效果
        for (MobEffectInstance effect : effectsToUpdate) {
            int remainingTime = effect.getDuration() - 30; // 减少30tick

            if (remainingTime > 0) {
                // 先移除旧效果，再添加新效果
                player.removeEffect(effect.getEffect());
                player.addEffect(new MobEffectInstance(
                        effect.getEffect(),
                        remainingTime,
                        effect.getAmplifier(),
                        effect.isAmbient(),
                        effect.isVisible(),
                        effect.showIcon()
                ));
            } else {
                player.removeEffect(effect.getEffect());
            }
        }
    }

    /**
     * 减少玩家效果时间并累加到锅数据中（30tick）
     * 更新：如果玩家没有效果，则跳过处理
     * @param player 要处理的玩家
     * @param cauldronData 要累加到的锅数据
     */
    private void reduceAndAccumulateEffects(Player player, CauldronData cauldronData) {
        // 检查玩家是否有效果，如果没有则直接返回
        if (player.getActiveEffects().isEmpty()) {
            return; // 玩家没有效果，跳过处理
        }

        // 创建一个列表来存储需要更新的效果，避免在迭代时修改集合
        java.util.List<MobEffectInstance> effectsToProcess = new java.util.ArrayList<>();
        effectsToProcess.addAll(player.getActiveEffects());

        for (MobEffectInstance effect : effectsToProcess) {
            int remainingTime = effect.getDuration() - 30; // 减少30tick
            if (remainingTime > 0) {
                // 先移除旧效果，再添加新效果
                player.removeEffect(effect.getEffect());
                player.addEffect(new MobEffectInstance(
                        effect.getEffect(),
                        remainingTime,
                        effect.getAmplifier(),
                        effect.isAmbient(),
                        effect.isVisible(),
                        effect.showIcon()
                ));

                // 累加到锅数据中
                cauldronData.addEffect(effect.getEffect(), effect.getAmplifier(), 30);
            } else {
                player.removeEffect(effect.getEffect());
                cauldronData.addEffect(effect.getEffect(), effect.getAmplifier(), effect.getDuration());
            }
        }
    }
}