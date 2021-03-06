package com.supermomonga.rukkit;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.net.URL;
import java.net.URI;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.URLDecoder;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.configuration.file.FileConfiguration;
import org.jruby.RubyObject;
import org.jruby.embed.ScriptingContainer;
import org.jruby.embed.EvalFailedException;


public class JRubyPlugin extends JavaPlugin implements Listener {
  private ScriptingContainer jruby;
  private HashMap<String, Object> eventHandlers = new HashMap<String, Object>();
  private Object rubyTrue, rubyFalse, rubyNil, rubyModule;
  private FileConfiguration config;

  private void initializeJRuby() {
    jruby = new ScriptingContainer();
    jruby.setClassLoader(getClass().getClassLoader());
    jruby.setCompatVersion(org.jruby.CompatVersion.RUBY2_0);

    // Because of no compatibility with Java's one
    rubyTrue = jruby.runScriptlet("true");
    rubyFalse = jruby.runScriptlet("false");
    rubyNil = jruby.runScriptlet("nil");
    rubyModule = jruby.runScriptlet("Module");
  }

  private boolean isRubyMethodExists(Object eventHandler, String method) {
    if (jruby.callMethod(eventHandler, "respond_to?", method).equals(rubyTrue)) {
      return true;
    } else {
      return false;
    }
  }

  private void callJRubyMethodIfExists(String method, Object arg1) {
    for (Object eventHandler : eventHandlers.values())
      if (isRubyMethodExists(eventHandler, method))
        jruby.callMethod(eventHandler, method, arg1);
  }

  private void callJRubyMethodIfExists(String method, Object arg1, Object arg2) {
    for (Object eventHandler : eventHandlers.values())
      if (isRubyMethodExists(eventHandler, method))
        jruby.callMethod(eventHandler, method, arg1, arg2);
  }

  private void callJRubyMethodIfExists(String method, Object arg1, Object arg2, Object arg3) {
    for (Object eventHandler : eventHandlers.values())
      if (isRubyMethodExists(eventHandler, method))
        jruby.callMethod(eventHandler, method, arg1, arg2, arg3);
  }

  private void callJRubyMethodIfExists(String method, Object arg1, Object arg2, Object arg3, Object arg4) {
    for (Object eventHandler : eventHandlers.values())
      if (isRubyMethodExists(eventHandler, method))
        jruby.callMethod(eventHandler, method, arg1, arg2, arg3, arg4);
  }


  private void loadConfig() {
    config = getConfig();
  }

  private Object evalRuby(String script) {
    try {
      return jruby.runScriptlet(script);
    } catch (EvalFailedException e) {
      return rubyNil;
    } finally {
      return rubyNil;
    }
  }

  private Object loadJRubyScript(InputStream io, String path) {
    try {
      return jruby.runScriptlet(io, path);
    } finally {
      try {
        if (io != null) {
          io.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private void loadRukkitBundledScript(String script) {
    getLogger().info("Loading script: [" + script + "]");
    InputStream is = null;
    BufferedReader br = null;
    try {
      is = this.getClass().getClassLoader().getResource("scripts/" + script + ".rb").openStream();
      br = new BufferedReader(new InputStreamReader(is));

      String scriptBuffer =
        br.lines().collect(Collectors.joining("\n"));

      RubyObject eventHandler = (RubyObject)jruby.runScriptlet(scriptBuffer);
      getLogger().info("Script loaded: [" + script + "]");

    } catch (Exception e) {
      getLogger().info("Failed to load script: [" + script + "]");
      e.printStackTrace();
    } finally {
      if (is != null) try { is.close(); } catch (IOException e) {}
      if (br != null) try { br.close(); } catch (IOException e) {}
    }
  }

  private void loadRukkitBundledScripts(List<String> scripts) {
    for (String script : scripts) {
      loadRukkitBundledScript(script);
    }
  }

  private void loadRukkitScript(String scriptDir, String script) {
    getLogger().info("Loading script: [" + script + "]");
    String scriptPath = scriptDir + script + ".rb";
    try {
      // Define module
      String moduleName = snakeToCamel(script);
      String scriptBuffer =
        Files.readAllLines(Paths.get(scriptPath)).stream().collect(Collectors.joining("\n"));
      jruby.runScriptlet(scriptBuffer);
      getLogger().info("Script loaded: [" + script + "]");
    } catch (Exception e) {
      getLogger().info("Failed to load script: [" + script + "]");
      e.printStackTrace();
    }
  }

  private void loadRukkitScripts(String scriptDir, List<String> scripts) {
    for (String script : scripts) {
      loadRukkitScript(scriptDir, script);
    }
  }

  private void loadRukkitPlugin(String pluginDir, String plugin) {
    getLogger().info("Loading plugin: [" + plugin + "]");
    String pluginPath = pluginDir + plugin + ".rb";
    try {
      String moduleName = snakeToCamel(plugin);

      // Add script dir to $LOAD_PATH automatically
      String userScriptsPath = config.getString("rukkit.script_dir");
      String loadPathStatement = "";
      if (userScriptsPath != null)
        loadPathStatement = "$LOAD_PATH << '" + userScriptsPath + "'\n";

      // Add resource ruby loader
      String resourceLoader =
        "import 'com.supermomonga.rukkit.RukkitLoader'\n" +
        "def require_resource(name)\n" +
        "  buffer = RukkitLoader.new.get_resource_as_string %`#{name}.rb`\n" +
        "  eval buffer unless buffer.nil?\n" +
        "end\n";

      String pluginBuffer =
        "# encoding: utf-8\n"
        + loadPathStatement
        + resourceLoader
        + Files.readAllLines(Paths.get(pluginPath)).stream().collect(Collectors.joining("\n"))
        + "\n"
        + "nil.tap{\n"
        +   "break " + moduleName + " if defined? " + moduleName + "\n"
        + "}";
      /* getLogger().info(pluginBuffer); */
      RubyObject eventHandler = (RubyObject)jruby.runScriptlet(pluginBuffer);

      // Add Module to event handler list
      if (eventHandler != rubyNil && eventHandler.getType() == rubyModule) {
        eventHandlers.put(plugin, eventHandler);
        getLogger().info("Plugin loaded: [" + plugin + "]");
      } else {
        getLogger().warning("Plugin loaded but module not defined: [" + plugin + "]");
      }
    } catch (Exception e) {
      getLogger().warning("Failed to load plugin: [" + plugin + "]");
      e.printStackTrace();
    }
  }

  private void loadRukkitPlugins(String pluginDir, List<String> plugins) {
    for (String plugin : plugins) {
      loadRukkitPlugin(pluginDir, plugin);
    }
  }

  private boolean isModuleDefined(String moduleName) {
    return isDefined(moduleName, "constant");
  }

  private boolean isDefined(String objectName, String type) {
    return type.equals(jruby.runScriptlet("defined? " + objectName));
  }

  private String snakeToCamel(String snake) {
    return Arrays.asList(snake.split("_")).stream().map(
        w -> w.substring(0,1).toUpperCase() + w.substring(1)
        ).collect(Collectors.joining(""));
  }

  private void loadCoreScripts() {
    List<String> scripts = new ArrayList<String>();
    scripts.add("util");

    loadRukkitBundledScripts( scripts );
  }

  private void loadUserScripts() {
    if (config.getString("rukkit.script_dir") != null &&
        config.getStringList("rukkit.scripts") != null)
      loadRukkitScripts(
          config.getString("rukkit.script_dir"),
          config.getStringList("rukkit.scripts")
          );
  }

  private void loadUserPlugins() {
    if (config.getString("rukkit.plugin_dir") != null &&
        config.getStringList("rukkit.plugins") != null)
      loadRukkitPlugins(
          config.getString("rukkit.plugin_dir"),
          config.getStringList("rukkit.plugins")
          );
  }

  private void applyEventHandler() {
    getServer().getPluginManager().registerEvents(this, this);
  }

  @Override
  public void onEnable() {
    initializeJRuby();
    loadConfig();

    loadCoreScripts();
    loadUserScripts();
    loadUserPlugins();
    getLogger().info("Rukkit enabled!");

    applyEventHandler();
  }

  @Override
  public void onDisable() {
    getLogger().info("Rukkit disabled!");
  }

  @Override
  public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
    callJRubyMethodIfExists("on_command", sender, command, label, args);
    return true;
  }

  // EventHandler mappings
  // TODO: I want to generate all event handler mappings automatically,
  //       but it must be painful to parse JavaDoc...
  //       @ujm says that "use jruby repl and ruby reflection to list them up."
  // TODO: Following eventhandlers are copied from mckokoro source, so it might be not a latest handlers.
  @EventHandler
  public void onAsyncPlayerPreLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent event) {
    callJRubyMethodIfExists("on_async_player_pre_login", event);
  }
  @EventHandler
  public void onBlockBurn(org.bukkit.event.block.BlockBurnEvent event) {
    callJRubyMethodIfExists("on_block_burn", event);
  }
  @EventHandler
  public void onBlockCanBuild(org.bukkit.event.block.BlockCanBuildEvent event) {
    callJRubyMethodIfExists("on_block_can_build", event);
  }
  @EventHandler
  public void onBlockDamage(org.bukkit.event.block.BlockDamageEvent event) {
    callJRubyMethodIfExists("on_block_damage", event);
  }
  @EventHandler
  public void onBlockDispense(org.bukkit.event.block.BlockDispenseEvent event) {
    callJRubyMethodIfExists("on_block_dispense", event);
  }
  @EventHandler
  public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
    callJRubyMethodIfExists("on_block_break", event);
  }
  @EventHandler
  public void onFurnaceExtract(org.bukkit.event.inventory.FurnaceExtractEvent event) {
    callJRubyMethodIfExists("on_furnace_extract", event);
  }
  @EventHandler
  public void onBlockFade(org.bukkit.event.block.BlockFadeEvent event) {
    callJRubyMethodIfExists("on_block_fade", event);
  }
  @EventHandler
  public void onBlockFromTo(org.bukkit.event.block.BlockFromToEvent event) {
    callJRubyMethodIfExists("on_block_from_to", event);
  }
  @EventHandler
  public void onBlockForm(org.bukkit.event.block.BlockFormEvent event) {
    callJRubyMethodIfExists("on_block_form", event);
  }
  @EventHandler
  public void onBlockSpread(org.bukkit.event.block.BlockSpreadEvent event) {
    callJRubyMethodIfExists("on_block_spread", event);
  }
  @EventHandler
  public void onEntityBlockForm(org.bukkit.event.block.EntityBlockFormEvent event) {
    callJRubyMethodIfExists("on_entity_block_form", event);
  }
  @EventHandler
  public void onBlockIgnite(org.bukkit.event.block.BlockIgniteEvent event) {
    callJRubyMethodIfExists("on_block_ignite", event);
  }
  @EventHandler
  public void onBlockPhysics(org.bukkit.event.block.BlockPhysicsEvent event) {
    callJRubyMethodIfExists("on_block_physics", event);
  }
  @EventHandler
  public void onBlockPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent event) {
    callJRubyMethodIfExists("on_block_piston_extend", event);
  }
  @EventHandler
  public void onBlockPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent event) {
    callJRubyMethodIfExists("on_block_piston_retract", event);
  }
  @EventHandler
  public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
    callJRubyMethodIfExists("on_block_place", event);
  }
  @EventHandler
  public void onBlockRedstone(org.bukkit.event.block.BlockRedstoneEvent event) {
    callJRubyMethodIfExists("on_block_redstone", event);
  }
  @EventHandler
  public void onBrew(org.bukkit.event.inventory.BrewEvent event) {
    callJRubyMethodIfExists("on_brew", event);
  }
  @EventHandler
  public void onFurnaceBurn(org.bukkit.event.inventory.FurnaceBurnEvent event) {
    callJRubyMethodIfExists("on_furnace_burn", event);
  }
  @EventHandler
  public void onFurnaceSmelt(org.bukkit.event.inventory.FurnaceSmeltEvent event) {
    callJRubyMethodIfExists("on_furnace_smelt", event);
  }
  @EventHandler
  public void onLeavesDecay(org.bukkit.event.block.LeavesDecayEvent event) {
    callJRubyMethodIfExists("on_leaves_decay", event);
  }
  @EventHandler
  public void onNotePlay(org.bukkit.event.block.NotePlayEvent event) {
    callJRubyMethodIfExists("on_note_play", event);
  }
  @EventHandler
  public void onSignChange(org.bukkit.event.block.SignChangeEvent event) {
    callJRubyMethodIfExists("on_sign_change", event);
  }
  @EventHandler
  public void onCreatureSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
    callJRubyMethodIfExists("on_creature_spawn", event);
  }
  @EventHandler
  public void onCreeperPower(org.bukkit.event.entity.CreeperPowerEvent event) {
    callJRubyMethodIfExists("on_creeper_power", event);
  }
  @EventHandler
  public void onEntityChangeBlock(org.bukkit.event.entity.EntityChangeBlockEvent event) {
    callJRubyMethodIfExists("on_entity_change_block", event);
  }
  @EventHandler
  public void onEntityBreakDoor(org.bukkit.event.entity.EntityBreakDoorEvent event) {
    callJRubyMethodIfExists("on_entity_break_door", event);
  }
  @EventHandler
  public void onEntityCombust(org.bukkit.event.entity.EntityCombustEvent event) {
    callJRubyMethodIfExists("on_entity_combust", event);
  }
  @EventHandler
  public void onEntityCombustByBlock(org.bukkit.event.entity.EntityCombustByBlockEvent event) {
    callJRubyMethodIfExists("on_entity_combust_by_block", event);
  }
  @EventHandler
  public void onEntityCombustByEntity(org.bukkit.event.entity.EntityCombustByEntityEvent event) {
    callJRubyMethodIfExists("on_entity_combust_by_entity", event);
  }
  @EventHandler
  public void onEntityCreatePortal(org.bukkit.event.entity.EntityCreatePortalEvent event) {
    callJRubyMethodIfExists("on_entity_create_portal", event);
  }
  @EventHandler
  public void onEntityDamageByBlock(org.bukkit.event.entity.EntityDamageEvent event) {
    callJRubyMethodIfExists("on_entity_damage", event);
  }
  @EventHandler
  public void onEntityDamageByBlock(org.bukkit.event.entity.EntityDamageByBlockEvent event) {
    callJRubyMethodIfExists("on_entity_damage_by_block", event);
  }
  @EventHandler
  public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
    callJRubyMethodIfExists("on_entity_damage_by_entity", event);
  }
  @EventHandler
  public void onEntityDeath(org.bukkit.event.entity.EntityDeathEvent event) {
    callJRubyMethodIfExists("on_entity_death", event);
  }
  @EventHandler
  public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
    callJRubyMethodIfExists("on_player_death", event);
  }
  @EventHandler
  public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
    callJRubyMethodIfExists("on_entity_explode", event);
  }
  @EventHandler
  public void onEntityInteract(org.bukkit.event.entity.EntityInteractEvent event) {
    callJRubyMethodIfExists("on_entity_interact", event);
  }
  @EventHandler
  public void onEntityRegainHealth(org.bukkit.event.entity.EntityRegainHealthEvent event) {
    callJRubyMethodIfExists("on_entity_regain_health", event);
  }
  @EventHandler
  public void onEntityShootBow(org.bukkit.event.entity.EntityShootBowEvent event) {
    callJRubyMethodIfExists("on_entity_shoot_bow", event);
  }
  @EventHandler
  public void onEntityTame(org.bukkit.event.entity.EntityTameEvent event) {
    callJRubyMethodIfExists("on_entity_tame", event);
  }
  @EventHandler
  public void onEntityTarget(org.bukkit.event.entity.EntityTargetEvent event) {
    callJRubyMethodIfExists("on_entity_target", event);
  }
  @EventHandler
  public void onEntityTargetLivingEntity(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
    callJRubyMethodIfExists("on_entity_target_living_entity", event);
  }
  @EventHandler
  public void onEntityTeleport(org.bukkit.event.entity.EntityTeleportEvent event) {
    callJRubyMethodIfExists("on_entity_teleport", event);
  }
  @EventHandler
  public void onExplosionPrime(org.bukkit.event.entity.ExplosionPrimeEvent event) {
    callJRubyMethodIfExists("on_explosion_prime", event);
  }
  @EventHandler
  public void onFoodLevelChange(org.bukkit.event.entity.FoodLevelChangeEvent event) {
    callJRubyMethodIfExists("on_food_level_change", event);
  }
  @EventHandler
  public void onItemDespawn(org.bukkit.event.entity.ItemDespawnEvent event) {
    callJRubyMethodIfExists("on_item_despawn", event);
  }
  @EventHandler
  public void onItemSpawn(org.bukkit.event.entity.ItemSpawnEvent event) {
    callJRubyMethodIfExists("on_item_spawn", event);
  }
  @EventHandler
  public void onPigZap(org.bukkit.event.entity.PigZapEvent event) {
    callJRubyMethodIfExists("on_pig_zap", event);
  }
  @EventHandler
  public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
    callJRubyMethodIfExists("on_projectile_hit", event);
  }
  @EventHandler
  public void onExpBottle(org.bukkit.event.entity.ExpBottleEvent event) {
    callJRubyMethodIfExists("on_exp_bottle", event);
  }
  @EventHandler
  public void onPotionSplash(org.bukkit.event.entity.PotionSplashEvent event) {
    callJRubyMethodIfExists("on_potion_splash", event);
  }
  @EventHandler
  public void onProjectileLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
    callJRubyMethodIfExists("on_projectile_launch", event);
  }
  @EventHandler
  public void onSheepDyeWool(org.bukkit.event.entity.SheepDyeWoolEvent event) {
    callJRubyMethodIfExists("on_sheep_dye_wool", event);
  }
  @EventHandler
  public void onSheepRegrowWool(org.bukkit.event.entity.SheepRegrowWoolEvent event) {
    callJRubyMethodIfExists("on_sheep_regrow_wool", event);
  }
  @EventHandler
  public void onSlimeSplit(org.bukkit.event.entity.SlimeSplitEvent event) {
    callJRubyMethodIfExists("on_slime_split", event);
  }
  @EventHandler
  public void onHangingBreak(org.bukkit.event.hanging.HangingBreakEvent event) {
    callJRubyMethodIfExists("on_hanging_break", event);
  }
  @EventHandler
  public void onHangingBreakByEntity(org.bukkit.event.hanging.HangingBreakByEntityEvent event) {
    callJRubyMethodIfExists("on_hanging_break_by_entity", event);
  }
  @EventHandler
  public void onHangingPlace(org.bukkit.event.hanging.HangingPlaceEvent event) {
    callJRubyMethodIfExists("on_hanging_place", event);
  }
  @EventHandler
  public void onEnchantItem(org.bukkit.event.enchantment.EnchantItemEvent event) {
    callJRubyMethodIfExists("on_enchant_item", event);
  }
  @EventHandler
  public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
    callJRubyMethodIfExists("on_inventory_click", event);
  }
  @EventHandler
  public void onCraftItem(org.bukkit.event.inventory.CraftItemEvent event) {
    callJRubyMethodIfExists("on_craft_item", event);
  }
  @EventHandler
  public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
    callJRubyMethodIfExists("on_inventory_close", event);
  }
  @EventHandler
  public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
    callJRubyMethodIfExists("on_inventory_open", event);
  }
  @EventHandler
  public void onPrepareItemCraft(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
    callJRubyMethodIfExists("on_prepare_item_craft", event);
  }
  @EventHandler
  public void onPrepareItemEnchant(org.bukkit.event.enchantment.PrepareItemEnchantEvent event) {
    callJRubyMethodIfExists("on_prepare_item_enchant", event);
  }
  @EventHandler
  public void onAsyncPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
    callJRubyMethodIfExists("on_async_player_chat", event);
  }
  @EventHandler
  public void onPlayerAnimation(org.bukkit.event.player.PlayerAnimationEvent event) {
    callJRubyMethodIfExists("on_player_animation", event);
  }
  @EventHandler
  public void onPlayerBedEnter(org.bukkit.event.player.PlayerBedEnterEvent event) {
    callJRubyMethodIfExists("on_player_bed_enter", event);
  }
  @EventHandler
  public void onPlayerBedLeave(org.bukkit.event.player.PlayerBedLeaveEvent event) {
    callJRubyMethodIfExists("on_player_bed_leave", event);
  }
  @EventHandler
  public void onPlayerBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
    callJRubyMethodIfExists("on_player_bucket_empty", event);
  }
  @EventHandler
  public void onPlayerBucketFill(org.bukkit.event.player.PlayerBucketFillEvent event) {
    callJRubyMethodIfExists("on_player_bucket_fill", event);
  }
  @EventHandler
  public void onPlayerChangedWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
    callJRubyMethodIfExists("on_player_changed_world", event);
  }
  @EventHandler
  public void onPlayerRegisterChannel(org.bukkit.event.player.PlayerRegisterChannelEvent event) {
    callJRubyMethodIfExists("on_player_register_channel", event);
  }
  @EventHandler
  public void onPlayerUnregisterChannel(org.bukkit.event.player.PlayerUnregisterChannelEvent event) {
    callJRubyMethodIfExists("on_player_unregister_channel", event);
  }
  @EventHandler
  public void onPlayerChatTabComplete(org.bukkit.event.player.PlayerChatTabCompleteEvent event) {
    callJRubyMethodIfExists("on_player_chat_tab_complete", event);
  }
  @EventHandler
  public void onPlayerCommandPreprocess(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
    callJRubyMethodIfExists("on_player_command_preprocess", event);
  }
  @EventHandler
  public void onPlayerDropItem(org.bukkit.event.player.PlayerDropItemEvent event) {
    callJRubyMethodIfExists("on_player_drop_item", event);
  }
  @EventHandler
  public void onPlayerEggThrow(org.bukkit.event.player.PlayerEggThrowEvent event) {
    callJRubyMethodIfExists("on_player_egg_throw", event);
  }
  @EventHandler
  public void onPlayerExpChange(org.bukkit.event.player.PlayerExpChangeEvent event) {
    callJRubyMethodIfExists("on_player_exp_change", event);
  }
  @EventHandler
  public void onPlayerFish(org.bukkit.event.player.PlayerFishEvent event) {
    callJRubyMethodIfExists("on_player_fish", event);
  }
  @EventHandler
  public void onPlayerGameModeChange(org.bukkit.event.player.PlayerGameModeChangeEvent event) {
    callJRubyMethodIfExists("on_player_game_mode_change", event);
  }
  @EventHandler
  public void onPlayerInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
    callJRubyMethodIfExists("on_player_interact_entity", event);
  }
  @EventHandler
  public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
    callJRubyMethodIfExists("on_player_interact", event);
  }
  @EventHandler
  public void onPlayerItemBreak(org.bukkit.event.player.PlayerItemBreakEvent event) {
    callJRubyMethodIfExists("on_player_item_break", event);
  }
  @EventHandler
  public void onPlayerItemHeld(org.bukkit.event.player.PlayerItemHeldEvent event) {
    callJRubyMethodIfExists("on_player_item_held", event);
  }
  @EventHandler
  public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
    callJRubyMethodIfExists("on_player_join", event);
  }
  @EventHandler
  public void onPlayerKick(org.bukkit.event.player.PlayerKickEvent event) {
    callJRubyMethodIfExists("on_player_kick", event);
  }
  @EventHandler
  public void onPlayerLevelChange(org.bukkit.event.player.PlayerLevelChangeEvent event) {
    callJRubyMethodIfExists("on_player_level_change", event);
  }
  @EventHandler
  public void onPlayerLogin(org.bukkit.event.player.PlayerLoginEvent event) {
    callJRubyMethodIfExists("on_player_login", event);
  }
  @EventHandler
  public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
    callJRubyMethodIfExists("on_player_move", event);
  }
  @EventHandler
  public void onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
    callJRubyMethodIfExists("on_player_teleport", event);
  }
  @EventHandler
  public void onPlayerPortal(org.bukkit.event.player.PlayerPortalEvent event) {
    callJRubyMethodIfExists("on_player_portal", event);
  }
  @EventHandler
  public void onPlayerPickupItem(org.bukkit.event.player.PlayerPickupItemEvent event) {
    callJRubyMethodIfExists("on_player_pickup_item", event);
  }
  @EventHandler
  public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
    callJRubyMethodIfExists("on_player_quit", event);
  }
  @EventHandler
  public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
    callJRubyMethodIfExists("on_player_respawn", event);
  }
  @EventHandler
  public void onPlayerShearEntity(org.bukkit.event.player.PlayerShearEntityEvent event) {
    callJRubyMethodIfExists("on_player_shear_entity", event);
  }
  @EventHandler
  public void onPlayerToggleFlight(org.bukkit.event.player.PlayerToggleFlightEvent event) {
    callJRubyMethodIfExists("on_player_toggle_flight", event);
  }
  @EventHandler
  public void onPlayerToggleSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
    callJRubyMethodIfExists("on_player_toggle_sneak", event);
  }
  @EventHandler
  public void onPlayerToggleSprint(org.bukkit.event.player.PlayerToggleSprintEvent event) {
    callJRubyMethodIfExists("on_player_toggle_sprint", event);
  }
  @EventHandler
  public void onPlayerVelocity(org.bukkit.event.player.PlayerVelocityEvent event) {
    callJRubyMethodIfExists("on_player_velocity", event);
  }
  @EventHandler
  public void onMapInitialize(org.bukkit.event.server.MapInitializeEvent event) {
    callJRubyMethodIfExists("on_map_initialize", event);
  }
  @EventHandler
  public void onPluginDisable(org.bukkit.event.server.PluginDisableEvent event) {
    callJRubyMethodIfExists("on_plugin_disable", event);
  }
  @EventHandler
  public void onPluginEnable(org.bukkit.event.server.PluginEnableEvent event) {
    callJRubyMethodIfExists("on_plugin_enable", event);
  }
  @EventHandler
  public void onServerCommand(org.bukkit.event.server.ServerCommandEvent event) {
    callJRubyMethodIfExists("on_server_command", event);
  }
  @EventHandler
  public void onRemoteServerCommand(org.bukkit.event.server.RemoteServerCommandEvent event) {
    callJRubyMethodIfExists("on_remote_server_command", event);
  }
  @EventHandler
  public void onServerListPing(org.bukkit.event.server.ServerListPingEvent event) {
    callJRubyMethodIfExists("on_server_list_ping", event);
  }
  @EventHandler
  public void onServiceRegister(org.bukkit.event.server.ServiceRegisterEvent event) {
    callJRubyMethodIfExists("on_service_register", event);
  }
  @EventHandler
  public void onServiceUnregister(org.bukkit.event.server.ServiceUnregisterEvent event) {
    callJRubyMethodIfExists("on_service_unregister", event);
  }
  @EventHandler
  public void onVehicleBlockCollision(org.bukkit.event.vehicle.VehicleBlockCollisionEvent event) {
    callJRubyMethodIfExists("on_vehicle_block_collision", event);
  }
  @EventHandler
  public void onVehicleEntityCollision(org.bukkit.event.vehicle.VehicleEntityCollisionEvent event) {
    callJRubyMethodIfExists("on_vehicle_entity_collision", event);
  }
  @EventHandler
  public void onVehicleCreate(org.bukkit.event.vehicle.VehicleCreateEvent event) {
    callJRubyMethodIfExists("on_vehicle_create", event);
  }
  @EventHandler
  public void onVehicleDamage(org.bukkit.event.vehicle.VehicleDamageEvent event) {
    callJRubyMethodIfExists("on_vehicle_damage", event);
  }
  @EventHandler
  public void onVehicleDestroy(org.bukkit.event.vehicle.VehicleDestroyEvent event) {
    callJRubyMethodIfExists("on_vehicle_destroy", event);
  }
  @EventHandler
  public void onVehicleEnter(org.bukkit.event.vehicle.VehicleEnterEvent event) {
    callJRubyMethodIfExists("on_vehicle_enter", event);
  }
  @EventHandler
  public void onVehicleExit(org.bukkit.event.vehicle.VehicleExitEvent event) {
    callJRubyMethodIfExists("on_vehicle_exit", event);
  }
  @EventHandler
  public void onVehicleMove(org.bukkit.event.vehicle.VehicleMoveEvent event) {
    callJRubyMethodIfExists("on_vehicle_move", event);
  }
  @EventHandler
  public void onVehicleUpdate(org.bukkit.event.vehicle.VehicleUpdateEvent event) {
    callJRubyMethodIfExists("on_vehicle_update", event);
  }
  @EventHandler
  public void onLightningStrike(org.bukkit.event.weather.LightningStrikeEvent event) {
    callJRubyMethodIfExists("on_lightning_strike", event);
  }
  @EventHandler
  public void onThunderChange(org.bukkit.event.weather.ThunderChangeEvent event) {
    callJRubyMethodIfExists("on_thunder_change", event);
  }
  @EventHandler
  public void onWeatherChange(org.bukkit.event.weather.WeatherChangeEvent event) {
    callJRubyMethodIfExists("on_weather_change", event);
  }
  @EventHandler
  public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
    callJRubyMethodIfExists("on_chunk_load", event);
  }
  @EventHandler
  public void onChunkPopulate(org.bukkit.event.world.ChunkPopulateEvent event) {
    callJRubyMethodIfExists("on_chunk_populate", event);
  }
  @EventHandler
  public void onChunkUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
    callJRubyMethodIfExists("on_chunk_unload", event);
  }
  @EventHandler
  public void onPortalCreate(org.bukkit.event.world.PortalCreateEvent event) {
    callJRubyMethodIfExists("on_portal_create", event);
  }
  @EventHandler
  public void onSpawnChange(org.bukkit.event.world.SpawnChangeEvent event) {
    callJRubyMethodIfExists("on_spawn_change", event);
  }
  @EventHandler
  public void onStructureGrow(org.bukkit.event.world.StructureGrowEvent event) {
    callJRubyMethodIfExists("on_structure_grow", event);
  }
  @EventHandler
  public void onWorldInit(org.bukkit.event.world.WorldInitEvent event) {
    callJRubyMethodIfExists("on_world_init", event);
  }
  @EventHandler
  public void onWorldLoad(org.bukkit.event.world.WorldLoadEvent event) {
    callJRubyMethodIfExists("on_world_load", event);
  }
  @EventHandler
  public void onWorldSave(org.bukkit.event.world.WorldSaveEvent event) {
    callJRubyMethodIfExists("on_world_save", event);
  }
  @EventHandler
  public void onWorldUnload(org.bukkit.event.world.WorldUnloadEvent event) {
    callJRubyMethodIfExists("on_world_unload", event);
  }
}
