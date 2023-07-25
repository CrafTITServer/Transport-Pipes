package de.robotricker.transportpipes.listener;

import de.robotricker.transportpipes.PlayerSettingsService;
import de.robotricker.transportpipes.ThreadService;
import de.robotricker.transportpipes.TransportPipes;
import de.robotricker.transportpipes.api.DuctBreakEvent;
import de.robotricker.transportpipes.api.DuctPlaceEvent;
import de.robotricker.transportpipes.config.GeneralConf;
import de.robotricker.transportpipes.config.LangConf;
import de.robotricker.transportpipes.duct.Duct;
import de.robotricker.transportpipes.duct.DuctRegister;
import de.robotricker.transportpipes.duct.manager.GlobalDuctManager;
import de.robotricker.transportpipes.duct.types.DuctType;
import de.robotricker.transportpipes.items.ItemService;
import de.robotricker.transportpipes.location.BlockLocation;
import de.robotricker.transportpipes.location.TPDirection;
import de.robotricker.transportpipes.utils.HitboxUtils;
import de.robotricker.transportpipes.utils.ProtectionUtils.ProtectionUtils;
import de.robotricker.transportpipes.utils.WorldUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class DuctListener implements Listener {

    private final List<Material> interactables = new ArrayList<>();

    //makes sure that "callInteraction" is called with the mainHand and with the offHand every single time
    private final Map<Player, Interaction> interactions = new HashMap<>();
    private final Set<UUID> noClick = new HashSet<>();

    private final ItemService itemService;
    private final DuctRegister ductRegister;
    private final GlobalDuctManager globalDuctManager;
    private final TPContainerListener tpContainerListener;
    private final GeneralConf generalConf;
    private final TransportPipes transportPipes;
    private final ThreadService threadService;
    private final PlayerSettingsService playerSettingsService;
    private final ProtectionUtils protectionUtils;

    @Inject
    public DuctListener(ItemService itemService, JavaPlugin plugin, DuctRegister ductRegister, GlobalDuctManager globalDuctManager, TPContainerListener tpContainerListener, GeneralConf generalConf, TransportPipes transportPipes, ThreadService threadService, PlayerSettingsService playerSettingsService, ProtectionUtils protectionUtils) {
        this.itemService = itemService;
        this.ductRegister = ductRegister;
        this.globalDuctManager = globalDuctManager;
        this.tpContainerListener = tpContainerListener;
        this.generalConf = generalConf;
        this.transportPipes = transportPipes;
        this.threadService = threadService;
        this.playerSettingsService = playerSettingsService;
        this.protectionUtils = protectionUtils;

        for (Material m : Material.values()) {
            if (m.isInteractable()) {
                if (m != Material.PUMPKIN && m != Material.REDSTONE_ORE && m != Material.MOVING_PISTON && m != Material.TNT) interactables.add(m);
            }
        }

        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::updateInteractSet, 0L, 1L);
    }

    private void updateInteractSet() {
        Iterator<Player> events = interactions.keySet().iterator();
        while (events.hasNext()) {
            Player p = events.next();
            if (interactions.get(p) != null)
                callInteraction(interactions.get(p));
            events.remove();
        }
    }
    
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        noClick.add(uuid);
        new BukkitRunnable() {
            @Override
            public void run() {
                noClick.remove(uuid);
            }
        }.runTaskLater(transportPipes, 2L);
    }
    
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityClick(PlayerInteractEntityEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        noClick.add(uuid);
        new BukkitRunnable() {
            @Override
            public void run() {
                noClick.remove(uuid);
            }
        }.runTaskLater(transportPipes, 2L);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        UUID uuid = player.getUniqueId();

        if (event.getAction() == Action.PHYSICAL) {
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_AIR) {
            if (noClick.contains(uuid)) {
                return;
            }
        }
        
        noClick.add(uuid);
        new BukkitRunnable() {
            @Override
            public void run() {
                noClick.remove(uuid);
            }
        }.runTaskLater(transportPipes, 3L);
        
        if (event.getHand() == EquipmentSlot.HAND) {
            Interaction offHandInteraction = new Interaction(player, EquipmentSlot.OFF_HAND, player.getInventory().getItemInOffHand(), clickedBlock, event.getBlockFace(), event.getAction());
            interactions.put(player, offHandInteraction);

            Interaction mainHandInteraction = new Interaction(player, EquipmentSlot.HAND, player.getInventory().getItemInMainHand(), clickedBlock, event.getBlockFace(), event.getAction());
            callInteraction(mainHandInteraction);
            if (mainHandInteraction.cancel) event.setCancelled(true);
            if (mainHandInteraction.denyBlockUse) event.setUseInteractedBlock(Event.Result.DENY);
            if (mainHandInteraction.successful) {
                interactions.put(player, null);
            }
        }
        else if (event.getHand() == EquipmentSlot.OFF_HAND) {
            if (interactions.containsKey(player) && interactions.get(player) == null) {
                event.setCancelled(true);
                return;
            }
            if (interactions.containsKey(player)) {
                interactions.remove(player);
            }
            else {
                Interaction mainHandInteraction = new Interaction(player, EquipmentSlot.HAND, player.getInventory().getItemInMainHand(), clickedBlock, event.getBlockFace(), event.getAction());
                callInteraction(mainHandInteraction);
                if (mainHandInteraction.successful) {
                    return;
                }
            }
            Interaction offHandInteraction = new Interaction(player, EquipmentSlot.OFF_HAND, player.getInventory().getItemInOffHand(), clickedBlock, event.getBlockFace(), event.getAction());
            callInteraction(offHandInteraction);
            if (offHandInteraction.cancel) event.setCancelled(true);
            if (offHandInteraction.denyBlockUse) event.setUseInteractedBlock(Event.Result.DENY);
        }

    }

    private void callInteraction(Interaction interaction) {
        if (interaction.action == Action.RIGHT_CLICK_AIR || interaction.action == Action.RIGHT_CLICK_BLOCK) {
            if (interaction.item != null) {
                // clickedDuct is the duct that was clicked, if a duct was clicked, otherwise null
                Duct clickedDuct = HitboxUtils.getDuctLookingTo(globalDuctManager, interaction.player, interaction.clickedBlock);
                // itemDuctType is the duct type in the player's hand when they click, if they have one, otherwise null
                DuctType itemDuctType = itemService.readDuctTags(interaction.item, ductRegister);
                // manualPlaceable is true if the item in the player's hand can be placed, ie the item is a block or a duct
                boolean manualPlaceable = itemDuctType != null || interaction.item.getType().isSolid();
                
                // ********************** CLICKING AIR WITHOUT A WRENCH ****************************
                if (interaction.action == Action.RIGHT_CLICK_AIR && clickedDuct == null && !itemService.isWrench(interaction.item)) {
                	return;
                }

                // ********************** CLICKING BLOCK WITH NO PIPE OR PIPE ITEM WITHOUT A WRENCH ****************************
                if (clickedDuct == null && itemDuctType == null && interaction.clickedBlock != null && !itemService.isWrench(interaction.item) &&
                        globalDuctManager.getDuctAtLoc(interaction.clickedBlock.getRelative(interaction.blockFace).getLocation()) == null) {
                    return;
                }

                // ********************** WRENCH SNEAK DUCT CLICK ****************************
                if (clickedDuct != null && itemService.isWrench(interaction.item) && interaction.player.isSneaking()) {
                    // Wrench sneak click
                    Block ductBlock = clickedDuct.getBlockLoc().toBlock(interaction.player.getWorld());
                    
                    if (protectionUtils.canBreak(interaction.player, ductBlock)) {
                        Block relativeBlock = HitboxUtils.getRelativeBlockOfDuct(globalDuctManager, interaction.player, ductBlock);
                        TPDirection clickedDir = TPDirection.fromBlockFace(ductBlock.getFace(Objects.requireNonNull(relativeBlock)));
                        Duct relativeDuct = clickedDuct.getDuctConnections().get(clickedDir);
                        if (clickedDuct.getBlockedConnections().contains(clickedDir)) {
                            clickedDuct.getBlockedConnections().remove(clickedDir);
                            LangConf.Key.CONNECTION_UNBLOCKED.sendMessage(interaction.player, Objects.requireNonNull(clickedDir).toString());
                        }
                        else {
                            clickedDuct.getBlockedConnections().add(clickedDir);
                            LangConf.Key.CONNECTION_BLOCKED.sendMessage(interaction.player, Objects.requireNonNull(clickedDir).toString());
                        }
                        globalDuctManager.updateDuctConnections(clickedDuct);
                        globalDuctManager.updateDuctInRenderSystems(clickedDuct, true);
                        relativeDuct = relativeDuct != null ? relativeDuct : clickedDuct.getDuctConnections().get(clickedDir);
                        if (relativeDuct != null) {
                            globalDuctManager.updateDuctConnections(relativeDuct);
                            globalDuctManager.updateDuctInRenderSystems(relativeDuct, true);
                        }
                    }
                    
                    interaction.cancel = true;
                    interaction.successful = true;
                    return;
                }

                // ********************** WRENCH DUCT CLICK ****************************
                if (clickedDuct != null && !manualPlaceable &&
                        (itemService.isWrench(interaction.item) || (!generalConf.getWrenchRequired() && !canBeUsedToObfuscate(interaction.item.getType())))) {
                    //wrench click

                    Block clickedBlock = clickedDuct.getBlockLoc().toBlock(interaction.player.getWorld());
                    
                    if (protectionUtils.canBreak(interaction.player, clickedBlock)) {
                        clickedDuct.notifyClick(interaction.player, interaction.player.isSneaking());
                    }

                    interaction.cancel = true;
                    interaction.successful = true;
                    return;
                }

                // ********************** SNEAK WRENCH NON DUCT CLICK ****************************
                if (clickedDuct == null && itemService.isWrench(interaction.item) && interaction.player.isSneaking()) {
                    //wrench click

                    WorldUtils.startShowHiddenDuctsProcess(interaction.player, globalDuctManager, threadService, transportPipes, generalConf, playerSettingsService);

                    interaction.cancel = true;
                    interaction.successful = true;
                    return;
                }

                // ********************** DUCT OBFUSCATION ****************************
                if (!interaction.player.isSneaking() && canBeUsedToObfuscate(interaction.item.getType())) {
                    // block can be used to obfuscate and player is not sneaking
                    // this block will be used to obfuscate the duct
                    Duct relativeDuct = null;
                    Block ductBlock;
                    if (clickedDuct != null) {
                        ductBlock = clickedDuct.getBlockLoc().toBlock(interaction.player.getWorld());
                    }
                    else if (interaction.clickedBlock != null) {
                        ductBlock = interaction.clickedBlock.getRelative(interaction.blockFace);
                        relativeDuct = globalDuctManager.getDuctAtLoc(interaction.clickedBlock.getRelative(interaction.blockFace).getLocation());
                    }
                    else {
                        return;
                    }
                    if (protectionUtils.canBuild(interaction.player, ductBlock, interaction.item, interaction.hand)) {

                        BlockData oldBlockData = ductBlock.getBlockData().clone();
                        BlockData blockData = interaction.item.getType().createBlockData();
                        setDirectionalBlockFace(
                                ductBlock,
                                clickedDuct != null ? clickedDuct.getBlockLoc().toBlock(ductBlock.getWorld()) : null,
                                interaction.player,
                                interaction.blockFace
                        );
                        ductBlock.setBlockData(blockData, true);

                        BlockPlaceEvent event = new BlockPlaceEvent(ductBlock, ductBlock.getState(), ductBlock.getRelative(BlockFace.DOWN), interaction.item, interaction.player, true, interaction.hand);
                        Bukkit.getPluginManager().callEvent(event);

                        if (!event.isCancelled()) {
                            if (clickedDuct != null) {
                                clickedDuct.obfuscatedWith(blockData);
                                decreaseHandItem(interaction.player, interaction.hand);
                            }
                            else if (relativeDuct != null) {
                                relativeDuct.obfuscatedWith(blockData);
                                decreaseHandItem(interaction.player, interaction.hand);
                            }
                            else {
                                ductBlock.setBlockData(oldBlockData, true);
                            }
                        }
                        else {
                            ductBlock.setBlockData(oldBlockData, true);
                        }
                    }

                    interaction.cancel = true;
                    interaction.successful = true;
                    return;
                }

                // ********************** PREPARATIONS FOR DUCT / BLOCK PLACE ****************************
                Block relativeBlock = null;
                // If duct clicked, get block relative to clicked duct
                if (clickedDuct != null) {
                    relativeBlock = HitboxUtils.getRelativeBlockOfDuct(globalDuctManager, interaction.player, clickedDuct.getBlockLoc().toBlock(interaction.player.getWorld()));
                }
                // Otherwise, if block clicked, get block relative to clicked block
                else if (interaction.clickedBlock != null) {
                    relativeBlock = interaction.clickedBlock.getRelative(interaction.blockFace);
                }
                // If hand item is not a duct, duct was not clicked, and there's a relative block and the relative block is not a duct
                if (itemDuctType == null && clickedDuct == null && (relativeBlock != null && globalDuctManager.getDuctAtLoc(relativeBlock.getLocation()) == null)) {
                	return;
                }
                // If there's a relative block, and it's either solid or a duct
                if (relativeBlock != null && (relativeBlock.getType().isSolid() || globalDuctManager.getDuctAtLoc(relativeBlock.getLocation()) != null)) {
                    relativeBlock = null;
                }
                // If duct was not clicked, a block was clicked, the clicked block is interactable, and the player is not sneaking
                if (clickedDuct == null && interaction.clickedBlock != null && interactables.contains(interaction.clickedBlock.getType()) && !interaction.player.isSneaking()) {
                	// Stairs are considered interactable for some weird reason so ignore those
                    if (!(interaction.clickedBlock.getBlockData() instanceof Stairs)) {
                    	return;
                    }
                }
                // Otherwise, if there is no relative block or a duct was clicked and hand item is not a duct or solid block
                else if (relativeBlock == null || (clickedDuct != null && !manualPlaceable)) {
                    // Don't prevent players from eating or bone mealing
                    if (!interaction.item.getType().isEdible() && interaction.item.getType() != Material.BONE_MEAL) {
                        interaction.denyBlockUse = true;
                    }
                    return;
                }

                // ********************** DUCT AND BLOCK PLACE ****************************
                if (manualPlaceable) {
                    // DUCT PLACEMENT
                    if (itemDuctType != null) {
                        // At this point, relativeBlock is only null if we can't place a block there
                        if (relativeBlock != null && protectionUtils.canBuild(interaction.player, relativeBlock, interaction.item, interaction.hand)) {
                            boolean lwcAllowed = true;
                            for (TPDirection dir : TPDirection.values()) {
                                if (WorldUtils.lwcProtection(relativeBlock.getRelative(dir.getBlockFace()))) {
                                    lwcAllowed = false;
                                }
                            }
                            if (lwcAllowed) {
                                Duct duct = globalDuctManager.createDuctObject(itemDuctType, new BlockLocation(relativeBlock.getLocation()), relativeBlock.getWorld(), relativeBlock.getChunk());
                                globalDuctManager.registerDuct(duct);
                                globalDuctManager.updateDuctConnections(duct);
                                globalDuctManager.registerDuctInRenderSystems(duct, true);
                                globalDuctManager.updateNeighborDuctsConnections(duct);
                                globalDuctManager.updateNeighborDuctsInRenderSystems(duct, true);

                                decreaseHandItem(interaction.player, interaction.hand);
                                
                                DuctPlaceEvent event = new DuctPlaceEvent(interaction.player, duct.getBlockLoc());
                                Bukkit.getPluginManager().callEvent(event);
                            } else {
                                LangConf.Key.PROTECTED_BLOCK.sendMessage(interaction.player);
                            }
                        }

                        interaction.cancel = true;
                        interaction.successful = true;
                    }
                    // BLOCK PLACEMENT NEXT TO DUCT
                    else if (clickedDuct != null) {
                        // Create new blockdata from the player's item in hand
                        BlockData blockData = interaction.item.getType().createBlockData();

                        // Create a fake block from new blockdata to test build permissions
                        /*Block fakeBlock;
                        try {
                            fakeBlock = transportPipes.getFakeBlock(relativeBlock.getWorld(), relativeBlock.getLocation(), interaction.item.getType());
                        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                            e.printStackTrace();
                            return;
                        }

                        fakeBlock.setBlockData(blockData, false);*/

                        if (protectionUtils.canBuild(interaction.player, clickedDuct.getBlockLoc().toBlock(relativeBlock.getWorld()), interaction.item, interaction.hand)) {
                            // Copy the original blockdata from placed block's location (typically air)
                            BlockData oldBlockData = relativeBlock.getBlockData().clone();

                            // Set placed block's blockdata to new blockdata
                            relativeBlock.setBlockData(blockData, true);

                            // Update the block's direction if possible
                            setDirectionalBlockFace(
                                    relativeBlock,
                                    clickedDuct.getBlockLoc().toBlock(relativeBlock.getWorld()),
                                    interaction.player,
                                    interaction.blockFace
                            );

                            // Call BlockPlaceEvent for compatibility
                            BlockPlaceEvent event = new BlockPlaceEvent(
                                    relativeBlock,
                                    relativeBlock.getState(),
                                    clickedDuct.getBlockLoc().toBlock(relativeBlock.getWorld()),
                                    interaction.item,
                                    interaction.player,
                                    true,
                                    interaction.hand
                            );
                            Bukkit.getPluginManager().callEvent(event);

                            // This is probably redundant, but might as well check the event's cancellation state
                            if (!event.isCancelled()) {
                                Material blockType = relativeBlock.getType();

                                // If the block to place is not solid, slab, stair, impermeable, glowstone, and container
                                // Reset blockdata to original data
                                if (!blockType.isOccluding() && !Tag.SLABS.isTagged(blockType) && !Tag.STAIRS.isTagged(blockType)
                                        && !Tag.IMPERMEABLE.isTagged(blockType) && blockType != Material.GLOWSTONE &&
                                        !WorldUtils.isContainerBlock(relativeBlock)) {
                                    relativeBlock.setBlockData(oldBlockData, true);
                                } else {
                                    // Add inventory contents for container blocks
                                    if (relativeBlock.getState() instanceof Container blockContainer
                                            && interaction.item.hasItemMeta()
                                            && interaction.item.getItemMeta() instanceof BlockStateMeta itemBlockStateMeta
                                            && itemBlockStateMeta.getBlockState() instanceof Container itemContainer) {
                                        Inventory itemInventory = itemContainer.getSnapshotInventory();
                                        Inventory blockInventory = blockContainer.getSnapshotInventory();
                                        blockInventory.setContents(itemInventory.getContents());
                                        blockContainer.update(true);

                                        // create TransportPipesContainer from placed block if it is such
                                        tpContainerListener.updateContainerBlock(relativeBlock, true, true);
                                    }

                                    decreaseHandItem(interaction.player, interaction.hand);
                                }
                            } else {
                                // Event was cancelled, reset the block data
                                relativeBlock.setBlockData(oldBlockData, true);
                            }
                        }

                        interaction.cancel = true;
                        interaction.successful = true;
                    }
                }

            }
        } else if (interaction.action == Action.LEFT_CLICK_AIR || interaction.action == Action.LEFT_CLICK_BLOCK) {
            Duct clickedDuct = HitboxUtils.getDuctLookingTo(globalDuctManager, interaction.player, interaction.clickedBlock);
            // duct destruction
            if (clickedDuct != null) {
                BlockLocation clickedDuctLocation = clickedDuct.getBlockLoc();
                Block clickedBlock = clickedDuctLocation.toBlock(interaction.player.getWorld());
                
                if (protectionUtils.canBreak(interaction.player, clickedBlock)) {
                    globalDuctManager.unregisterDuct(clickedDuct);
                    globalDuctManager.unregisterDuctInRenderSystem(clickedDuct, true);
                    globalDuctManager.updateNeighborDuctsConnections(clickedDuct);
                    globalDuctManager.updateNeighborDuctsInRenderSystems(clickedDuct, true);
                    globalDuctManager.playDuctDestroyActions(clickedDuct, interaction.player);
                    
                    DuctBreakEvent event = new DuctBreakEvent(interaction.player, clickedDuctLocation);
                    Bukkit.getPluginManager().callEvent(event);
                }

                interaction.cancel = true;
                interaction.successful = true;
            }
        }
    }

    private boolean canBeUsedToObfuscate(Material type) {
        return (type.isOccluding() || Tag.IMPERMEABLE.isTagged(type) || type == Material.GLOWSTONE) &&
                !type.isInteractable() && !type.hasGravity();
    }

    private void setDirectionalBlockFace(Block placedBlock, Block clickedBlock, Player player, BlockFace interactedFace) {
        BlockData blockData = placedBlock.getBlockData();
        Location location = placedBlock.getLocation();

        // Calculate the direction from player to block location
        Vector dir = new Vector(location.getX() + 0.5d, location.getY() + 0.5d, location.getZ() + 0.5d);
        dir.subtract(player.getEyeLocation().toVector());
        double absX = Math.abs(dir.getX());
        double absY = Math.abs(dir.getY());
        double absZ = Math.abs(dir.getZ());

        BlockFace newFace = clickedBlock != null ? placedBlock.getFace(clickedBlock) : interactedFace;
        // For some idiotic reason, Shulker Boxes face the other direction
        if (Tag.SHULKER_BOXES.isTagged(placedBlock.getType()) && newFace != null) {
            newFace = newFace.getOppositeFace();
        }

        // If direction depends on clicked face, set it if possible
        if (transportPipes.getProtocolProvider().isClickedFaceDirectional(blockData)) {
            if ((blockData instanceof Directional directional && directional.getFaces().contains(newFace))
            || (blockData instanceof Orientable orientable && orientable.getAxes().contains(convertBlockFaceToAxis(newFace)))
            || (blockData instanceof Rotatable)) {
                setFacing(placedBlock, newFace);
                return;
            }
        }

        // Otherwise, first check if we have vertical direction
        if ((blockData instanceof Directional directional && (placedBlock.getType() == Material.HOPPER
                || directional.getFaces().contains(BlockFace.UP) && directional.getFaces().contains(BlockFace.DOWN)))
                || (blockData instanceof Orientable orientable && orientable.getAxes().contains(Axis.Z))
                || (blockData instanceof Rotatable)) {
            if (absX >= absY && absX >= absZ) {
                newFace = dir.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
            } else if (absY >= absX && absY >= absZ) {
                newFace = dir.getY() > 0 ? BlockFace.DOWN : BlockFace.UP;
            } else {
                newFace = dir.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
            }
        } else {
            // No vertical direction, so limit direction to horizontal
            if (absX >= absZ) {
                newFace = dir.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
            } else {
                newFace = dir.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
            }
        }

        setFacing(placedBlock, newFace);
    }

    private void setFacing(Block block, BlockFace blockFace) {
        BlockData blockData = block.getBlockData();
        if (blockData instanceof Directional directional) {
            if (directional.getFaces().contains(blockFace)) {
                directional.setFacing(blockFace);
                block.setBlockData(directional);
            }
        }
        if (blockData instanceof Orientable orientable) {
            orientable.setAxis(convertBlockFaceToAxis(blockFace));
            block.setBlockData(orientable);
        }
        if (blockData instanceof Rotatable rotatable) {
            rotatable.setRotation(blockFace);
            block.setBlockData(rotatable);
        }
    }

    private static Axis convertBlockFaceToAxis(BlockFace face) {
        return switch (face) {
            case NORTH, SOUTH -> Axis.Z;
            case UP, DOWN -> Axis.Y;
            default -> Axis.X;
        };
    }

    private void decreaseHandItem(Player p, EquipmentSlot hand) {
        if (p.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack item = hand == EquipmentSlot.HAND ? p.getInventory().getItemInMainHand() : p.getInventory().getItemInOffHand();
        if (item.getAmount() <= 1) {
            item = null;
        } else {
            item.setAmount(item.getAmount() - 1);
        }
        if (hand == EquipmentSlot.HAND) p.getInventory().setItemInMainHand(item);
        else p.getInventory().setItemInOffHand(item);
    }

    private static class Interaction {
        final Player player;
        final EquipmentSlot hand;
        final ItemStack item;
        final Block clickedBlock;
        final BlockFace blockFace;
        final Action action;
        boolean cancel;
        boolean denyBlockUse;
        boolean successful = false;

        Interaction(Player player, EquipmentSlot hand, ItemStack item, Block clickedBlock, BlockFace blockFace, Action action) {
            this.player = player;
            this.hand = hand;
            this.item = item;
            this.clickedBlock = clickedBlock;
            this.blockFace = blockFace;
            this.action = action;
        }
    }

}
