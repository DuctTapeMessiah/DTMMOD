package com.dtmmod;

import com.dtmmod.commands.CommandExample;
import com.dtmmod.hud.HudExample;
import com.dtmmod.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class DTMMOD extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("DTMMOD");
    public static final HudGroup HUD_GROUP = new HudGroup("DTMMOD");

    @Override
    public void onInitialize() {
        LOG.info("Initializing DTMMOD");

        // Modules
        Modules.get().add(new DataLogger());
        Modules.get().add(new MapArtDownloader());
        Modules.get().add(new FarmingSimulator());

        // Commands
        // Commands.add(new CommandExample());

        // HUD
        // Hud.get().register(HudExample.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.dtmmod";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("DuctTapeMessiah", "DTMMOD");
    }
}
