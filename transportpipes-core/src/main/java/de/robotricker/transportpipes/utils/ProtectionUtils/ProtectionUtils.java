package de.robotricker.transportpipes.utils.ProtectionUtils;

import de.robotricker.transportpipes.TransportPipes;
import de.robotricker.transportpipes.config.GeneralConf;
import de.robotricker.transportpipes.listener.TPContainerListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredListener;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public class ProtectionUtils implements Listener {

    private final GeneralConf generalConf;
    private final TPContainerListener tpContainerListener;
    private final TransportPipes transportPipes;

    @Inject
    public ProtectionUtils(GeneralConf generalConf, TPContainerListener tpContainerListener, TransportPipes transportPipes) {
        this.generalConf = generalConf;
        this.tpContainerListener = tpContainerListener;
        this.transportPipes = transportPipes;
    }

    public boolean canBuild(Player player, Block block, ItemStack item, EquipmentSlot hand) {
        if (generalConf.getDisabledWorlds().contains(block.getWorld().getName())) {
            return false;
        }

        /*Block fakeBlock;
        try {
            fakeBlock = transportPipes.getFakeBlock(block.getWorld(), block.getLocation(), Material.HOPPER);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            return false;
        }*/

        BuildPermissionEvent event = new BuildPermissionEvent(block, block.getState(), block.getRelative(BlockFace.DOWN), item, player, true, hand);
        callEventWithoutAntiCheat(event);

        return event.isAllowed();
    }

    public boolean canBreak(Player player, Block block) {
        if (generalConf.getDisabledWorlds().contains(block.getWorld().getName())) {
            return false;
        }

        /*Block fakeBlock;
        try {
            fakeBlock = transportPipes.getFakeBlock(block.getWorld(), block.getLocation(), Material.HOPPER);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            return false;
        }*/

        BreakPermissionEvent event = new BreakPermissionEvent(block, player);
        callEventWithoutAntiCheat(event);

        return event.isAllowed();
    }

    private void callEventWithoutAntiCheat(Event event) {
        // unregister anticheat listeners
        List<RegisteredListener> unregisterListeners = new ArrayList<>();
        for (RegisteredListener rl : event.getHandlers().getRegisteredListeners()) {
            for (String antiCheat : generalConf.getAnticheatPlugins()) {
                if (rl.getPlugin().getName().equalsIgnoreCase(antiCheat)) {
                    unregisterListeners.add(rl);
                }
            }
            if (rl.getListener().equals(tpContainerListener)) {
                unregisterListeners.add(rl);
            }
        }
        for (RegisteredListener rl : unregisterListeners) {
            event.getHandlers().unregister(rl);
        }

        Bukkit.getPluginManager().callEvent(event);

        // register anticheat listeners
        event.getHandlers().registerAll(unregisterListeners);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPermissionBuild(BlockPlaceEvent event) {
        if (event instanceof BuildPermissionEvent permissionEvent) {
            if (event.isCancelled()) {
                permissionEvent.setAllowed(false);
            }
            else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPermissionBreak(BlockBreakEvent event) {
        if (event instanceof BreakPermissionEvent permissionEvent) {
            if (event.isCancelled()) {
                permissionEvent.setAllowed(false);
            }
            else {
                event.setCancelled(true);
            }
        }
    }

}
