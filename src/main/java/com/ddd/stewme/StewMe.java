package com.ddd.stewme;

import com.ddd.stewme.handlers.StewMeEventHandler;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * 炼药锅效果收集模组主类
 * 负责模组的初始化和事件监听器的注册
 */
@Mod(StewMe.MODID)
public class StewMe {
    public static final String MODID = "stew_me";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 模组构造函数，在模组加载时调用
     * @param modEventBus 模组事件总线，用于注册模组相关事件
     * @param modContainer 模组容器，包含模组的基本信息
     */
    public StewMe(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[StewMe] 开始初始化炼药锅效果收集模组");

        // 注册物品
        Registry.ITEMS.register(modEventBus);
        LOGGER.info("[StewMe] 物品注册完成");

        // 注册事件监听器
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(new StewMeEventHandler());
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("[StewMe] 事件处理器注册完成");

        LOGGER.info("[StewMe] 模组初始化成功");
    }

    /**
     * 通用设置方法，在模组加载的通用阶段调用
     * @param event 通用设置事件
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("[StewMe] 执行通用设置");
        LOGGER.info("[StewMe] 通用设置完成");
    }

    /**
     * 服务器启动事件处理方法
     * @param event 服务器启动事件
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[StewMe] 服务器启动，模组准备就绪");
    }
}