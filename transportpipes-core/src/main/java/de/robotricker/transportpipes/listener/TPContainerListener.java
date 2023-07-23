package de.robotricker.transportpipes.listener;

import de.robotricker.transportpipes.TransportPipes;
import de.robotricker.transportpipes.api.ContainerUpdateEvent;
import de.robotricker.transportpipes.api.TransportPipesContainer;
import de.robotricker.transportpipes.container.BlockContainer;
import de.robotricker.transportpipes.container.BrewingStandContainer;
import de.robotricker.transportpipes.container.FurnaceContainer;
import de.robotricker.transportpipes.container.SimpleInventoryContainer;
import de.robotricker.transportpipes.duct.Duct;
import de.robotricker.transportpipes.duct.DuctRegister;
import de.robotricker.transportpipes.duct.manager.DuctManager;
import de.robotricker.transportpipes.duct.manager.GlobalDuctManager;
import de.robotricker.transportpipes.duct.manager.PipeManager;
import de.robotricker.transportpipes.duct.pipe.Pipe;
import de.robotricker.transportpipes.location.BlockLocation;
import de.robotricker.transportpipes.location.TPDirection;
import de.robotricker.transportpipes.utils.WorldUtils;
import io.papermc.paper.event.block.BlockBreakBlockEvent;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.InventoryHolder;

import javax.inject.Inject;
import java.util.Objects;

public class TPContainerListener implements Listener {

    @Inject
    private DuctRegister ductRegister;

    @Inject
    private GlobalDuctManager globalDuctManager;

    @Inject
    private TransportPipes transportPipes;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        notifyBlockUpdate(event.getBlock(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        notifyBlockUpdate(event.getBlock(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakBlockEvent event) {
        notifyBlockUpdate(event.getBlock(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            notifyBlockUpdate(block, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            notifyBlockUpdate(block, false);
        }
    }

    private void notifyBlockUpdate(Block block, boolean place) {
        if (WorldUtils.isContainerBlock(block)) {
            updateContainerBlock(block, place, true);
        }
        if (!place) {
            //undo obfuscation
            Duct duct = globalDuctManager.getDuctAtLoc(block.getLocation());
            if (duct != null) {
                duct.obfuscatedWith(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        handleChunkLoadSync(event.getChunk(), false);
    }

    /**
     * Adds or removes a container and optionally updates connected pipes
     * @param block The container block to add or remove
     * @param add True to add a container if it does not already exist, False to remove a container if it exists
     * @param updateNeighborPipes True to update connected pipes, False to not update connected pipes
     */
    public void updateContainerBlock(Block block, boolean add, boolean updateNeighborPipes) {
        ContainerUpdateEvent event = new ContainerUpdateEvent(block, add, updateNeighborPipes);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        PipeManager pipeManager = (PipeManager) (DuctManager<? extends Duct>) ductRegister.baseDuctTypeOf("pipe").getDuctManager();

        BlockLocation blockLoc = new BlockLocation(block.getLocation());
        if (add) {
            if (pipeManager.getContainerAtLoc(block.getLocation()) == null) {
                TransportPipesContainer container = createContainerFromBlock(block);
                pipeManager.getContainers(block.getWorld()).put(blockLoc, container);

                // only update the neighbor pipes if this updateContainerBlock method call is because of a chunk load that was not issued inside the onEnable method
                doUpdateNeighborPipes(block, updateNeighborPipes, blockLoc);

                transportPipes.runTaskSync(() -> {
                    //checks for double chest neighbor and updates the neighbors TransportPipesContainer if present
                    Block neighborDoubleChestBlock = checkForDoubleChestNeighbor(block);
                    if (neighborDoubleChestBlock != null) {
                        TransportPipesContainer neighborContainer = pipeManager.getContainerAtLoc(neighborDoubleChestBlock.getWorld(), new BlockLocation(neighborDoubleChestBlock.getLocation()));
                        if (neighborContainer instanceof BlockContainer) {
                            ((BlockContainer) neighborContainer).updateBlock();
                        }
                        if (pipeManager.getContainers(block.getWorld()).get(blockLoc) == container) {
                            ((BlockContainer) container).updateBlock();
                        }
                    }
                });

            }

        } else {
            TransportPipesContainer container = pipeManager.getContainerAtLoc(block.getLocation());
            if (container != null) {
                pipeManager.getContainers(block.getWorld()).remove(blockLoc);

                // only update the neighbor pipes if this updateContainerBlock method call is because of a chunk load that was not issued inside the onEnable method
                doUpdateNeighborPipes(block, updateNeighborPipes, blockLoc);
            }
        }
    }

    private void doUpdateNeighborPipes(Block block, boolean updateNeighborPipes, BlockLocation blockLoc) {
        if (updateNeighborPipes) {
            for (TPDirection dir : TPDirection.values()) {
                Duct duct = globalDuctManager.getDuctAtLoc(block.getWorld(), blockLoc.getNeighbor(dir));
                if (duct instanceof Pipe) {
                    globalDuctManager.updateDuctConnections(duct);
                    globalDuctManager.updateDuctInRenderSystems(duct, true);
                }
            }
        }
    }

    public Block checkForDoubleChestNeighbor(Block block) {
        if (block.getState() instanceof InventoryHolder) {
            if (((InventoryHolder) block.getState()).getInventory().getHolder() instanceof DoubleChest) {
                for (TPDirection dir : TPDirection.values()) {
                    if (dir.isSide()) {
                        Block neighborBlock = block.getRelative(dir.getBlockFace());
                        if (neighborBlock.getState() instanceof InventoryHolder) {
                            if (((InventoryHolder) neighborBlock.getState()).getInventory().getHolder() instanceof DoubleChest neighborBlockChest) {
                                DoubleChest blockChest = (DoubleChest) ((InventoryHolder) block.getState()).getInventory().getHolder();
                                if (Objects.requireNonNull(blockChest).getLocation().equals(neighborBlockChest.getLocation())) {
                                    return neighborBlock;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public TransportPipesContainer createContainerFromBlock(Block block) {
        BlockState blockState = block.getState();
        if (blockState instanceof Furnace) {
            return new FurnaceContainer(block);
        } else if (blockState instanceof BrewingStand) {
            return new BrewingStandContainer(block);
        } else if (blockState instanceof InventoryHolder) {
            return new SimpleInventoryContainer(block);
        }
        return null;
    }

    public void handleChunkLoadSync(Chunk loadedChunk, boolean onServerStart) {
        PipeManager pipeManager = (PipeManager) (DuctManager<? extends Duct>) ductRegister.baseDuctTypeOf("pipe").getDuctManager();

        if (loadedChunk.getTileEntities() != null) {
            for (BlockState blockState : loadedChunk.getTileEntities()) {
                if (WorldUtils.isContainerBlock(blockState)) {

                    //automatically ignores this block if it is already registered as container block
                    updateContainerBlock(blockState.getBlock(), true, !onServerStart);

                    //if this block is already registered, update the block, because the blockState object changes after a chunk unload and load
                    TransportPipesContainer container = pipeManager.getContainerAtLoc(blockState.getLocation());
                    if (container instanceof BlockContainer) {
                        ((BlockContainer) container).updateBlock();
                    }
                }
            }
        }
    }


}
