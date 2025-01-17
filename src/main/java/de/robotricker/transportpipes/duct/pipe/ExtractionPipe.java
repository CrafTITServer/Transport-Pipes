package de.robotricker.transportpipes.duct.pipe;

import de.robotricker.transportpipes.TransportPipes;
import de.robotricker.transportpipes.api.TransportPipesContainer;
import de.robotricker.transportpipes.duct.Duct;
import de.robotricker.transportpipes.duct.manager.DuctManager;
import de.robotricker.transportpipes.duct.manager.GlobalDuctManager;
import de.robotricker.transportpipes.duct.manager.PipeManager;
import de.robotricker.transportpipes.duct.pipe.extractionpipe.ExtractAmount;
import de.robotricker.transportpipes.duct.pipe.extractionpipe.ExtractCondition;
import de.robotricker.transportpipes.duct.pipe.extractionpipe.ExtractMode;
import de.robotricker.transportpipes.duct.pipe.filter.ItemDistributorService;
import de.robotricker.transportpipes.duct.pipe.filter.ItemFilter;
import de.robotricker.transportpipes.duct.pipe.items.PipeItem;
import de.robotricker.transportpipes.duct.types.DuctType;
import de.robotricker.transportpipes.inventory.DuctSettingsInventory;
import de.robotricker.transportpipes.items.ItemService;
import de.robotricker.transportpipes.location.BlockLocation;
import de.robotricker.transportpipes.location.TPDirection;
import net.querz.nbt.tag.CompoundTag;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ExtractionPipe extends Pipe {

    private TPDirection extractDirection;
    private ExtractCondition extractCondition;
    private ExtractAmount extractAmount;
    private ExtractMode extractMode;
    private ItemFilter itemFilter;

    public ExtractionPipe(DuctType ductType, BlockLocation blockLoc, World world, Chunk chunk, DuctSettingsInventory settingsInv, GlobalDuctManager globalDuctManager, ItemDistributorService itemDistributor) {
        super(ductType, blockLoc, world, chunk, settingsInv, globalDuctManager, itemDistributor);
        this.extractDirection = null;
        this.extractCondition = ExtractCondition.ALWAYS_EXTRACT;
        this.extractAmount = ExtractAmount.EXTRACT_1;
        this.extractMode = ExtractMode.ROUND;
        this.itemFilter = new ItemFilter();
    }

    @Override
    public void syncBigTick(DuctManager<? extends Duct> ductManager) {
        super.syncBigTick(ductManager);

        PipeManager pipeManager = (PipeManager) ductManager;

        if (extractDirection == null || extractCondition == ExtractCondition.NEVER_EXTRACT) {
            return;
        }

        //extract item
        TransportPipesContainer container = pipeManager.getContainerAtLoc(getWorld(), getBlockLoc().getNeighbor(extractDirection));
        if (container != null) {
            if (extractCondition == ExtractCondition.NEEDS_REDSTONE) {
                Block block = getBlockLoc().toBlock(getWorld());
                if (!block.isBlockIndirectlyPowered() && !block.isBlockPowered()) {
                    return;
                }
            }
            ItemStack item = container.extractItem(extractDirection, extractAmount.getAmount(), itemFilter);
            if (item != null) {
                PipeItem pipeItem = new PipeItem(item, getWorld(), getBlockLoc(), extractDirection.getOpposite(), getBlockLoc(), extractMode);
                pipeManager.spawnPipeItem(pipeItem);
                pipeManager.putPipeItemInPipe(pipeItem);
            }
        }

    }

    public void updateExtractDirection(boolean cycle) {
        TPDirection oldExtractDirection = getExtractDirection();
        Map<TPDirection, TransportPipesContainer> containerConnections = getContainerConnections();
        if (containerConnections.isEmpty()) {
            extractDirection = null;
        } else if (cycle || extractDirection == null || !containerConnections.containsKey(extractDirection)) {
            do {
                if (extractDirection == null) {
                    extractDirection = TPDirection.NORTH;
                } else {
                    extractDirection = extractDirection.next();
                }
            } while (!containerConnections.containsKey(extractDirection));
        }
        if (oldExtractDirection != getExtractDirection()) {
            globalDuctManager.updateDuctInRenderSystems(this, true);
            settingsInv.populate();
        }
    }

    public TPDirection getExtractDirection() {
        return extractDirection;
    }

    public void setExtractDirection(TPDirection extractDirection) {
        this.extractDirection = extractDirection;
    }

    public ExtractCondition getExtractCondition() {
        return extractCondition;
    }

    public void setExtractCondition(ExtractCondition extractCondition) {
        this.extractCondition = extractCondition;
    }

    public ExtractAmount getExtractAmount() {
        return extractAmount;
    }

    public void setExtractAmount(ExtractAmount extractAmount) {
        this.extractAmount = extractAmount;
    }
    
    public ExtractMode getExtractMode() {
        return extractMode;
    }
    
    public void setExtractMode(ExtractMode extractMode) {
        this.extractMode = extractMode;
    }

    public ItemFilter getItemFilter() {
        return itemFilter;
    }

    public void setItemFilter(ItemFilter itemFilter) {
        this.itemFilter = itemFilter;
    }

    @Override
    public Material getBreakParticleData() {
        return Material.OAK_PLANKS;
    }
    
    @Override
    protected Map<TPDirection, Integer> calculateItemDistribution(PipeItem pipeItem, TPDirection movingDir, List<TPDirection> dirs, TransportPipes transportPipes) {

        // If there's a container to insert into, make sure we don't insert into containers that we didn't pull from
    	Iterator<TPDirection> iterator = dirs.iterator();
    	while (iterator.hasNext()) {
    	    TPDirection direction = iterator.next();
            TransportPipesContainer transportPipesContainer = getContainerConnections().get(direction);
            if (transportPipesContainer != null) {
                BlockLocation sourceLoc = pipeItem.getSourceLoc();
                if (sourceLoc != null && sourceLoc != getBlockLoc()) {
                    iterator.remove();
                }
            }
    	}
    	
    	// If we have more than one direction option, make sure we remove the opposite direction to prevent backtracking when possible
    	if (dirs.contains(movingDir.getOpposite()) && dirs.size() > 1) {
    	    dirs.remove(movingDir.getOpposite());
    	}
    	
		Map<TPDirection, Integer> absWeights = new HashMap<>();
		dirs.forEach(dir -> absWeights.put(dir, 1));
		return itemDistributor.splitPipeItem(pipeItem, absWeights, this);
	}

    @Override
    public List<ItemStack> destroyed(TransportPipes transportPipes, DuctManager<? extends Duct> ductManager, Player destroyer) {
        List<ItemStack> drop = super.destroyed(transportPipes, ductManager, destroyer);
        if (destroyer != null) {
            drop.addAll(itemFilter.getAsItemStacks());
        }
        return drop;
    }

    @Override
    public void notifyConnectionChange() {
        super.notifyConnectionChange();
        updateExtractDirection(false);
    }

    @Override
    public void saveToNBTTag(CompoundTag compoundTag, ItemService itemService) {
        super.saveToNBTTag(compoundTag, itemService);

        compoundTag.putInt("extractDir", extractDirection != null ? extractDirection.ordinal() : -1);
        compoundTag.putInt("extractCondition", extractCondition.ordinal());
        compoundTag.putInt("extractAmount", extractAmount.ordinal());
        compoundTag.putInt("extractMode", extractMode.ordinal());
        CompoundTag itemFilterTag = new CompoundTag();
        itemFilter.saveToNBTTag(itemFilterTag, itemService);
        compoundTag.put("itemFilter", itemFilterTag);

    }

    @Override
    public void loadFromNBTTag(CompoundTag compoundTag, ItemService itemService) {
        super.loadFromNBTTag(compoundTag, itemService);

        extractDirection = compoundTag.getInt("extractDir") != -1 ? TPDirection.values()[compoundTag.getInt("extractDir")] : null;
        extractCondition = ExtractCondition.values()[compoundTag.getInt("extractCondition")];
        extractAmount = ExtractAmount.values()[compoundTag.getInt("extractAmount")];
        extractMode = ExtractMode.values()[compoundTag.getInt("extractMode")];
        itemFilter = new ItemFilter();
        itemFilter.loadFromNBTTag(compoundTag.getCompoundTag("itemFilter"), itemService);

        settingsInv.populate();
    }
}
