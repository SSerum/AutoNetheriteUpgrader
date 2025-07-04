package com.example.addon;

import org.slf4j.Logger;

import com.example.addon.modules.AutoChestLoot;
import com.example.addon.modules.AutoNetheriteUpgrade;
import com.example.addon.modules.AutoRenamer;
import com.mojang.logging.LogUtils;

import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    // Rename category here to "SerumWare"
    public static final Category CATEGORY = new Category("SerumWare");
    public static final HudGroup HUD_GROUP = new HudGroup("SerumWare");

    @Override
    public void onInitialize() {
        LOG.info("Initializing SerumWare Addon");

        // Add your modules here
        Modules.get().add(new AutoNetheriteUpgrade());
        Modules.get().add(new AutoRenamer());
        Modules.get().add(new AutoChestLoot());

        // You can register commands or HUD elements here if you want
        // Commands.add(new CommandExample());
        // Hud.get().register(HudExample.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("YourGitHubUsername", "YourRepoName"); // Replace with your actual GitHub repo info
    }
}
