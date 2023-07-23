package de.robotricker.transportpipes.items;

import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import de.robotricker.transportpipes.TransportPipes;
import de.robotricker.transportpipes.config.GeneralConf;
import de.robotricker.transportpipes.config.LangConf;
import de.robotricker.transportpipes.duct.Duct;
import de.robotricker.transportpipes.duct.DuctRegister;
import de.robotricker.transportpipes.duct.types.BaseDuctType;
import de.robotricker.transportpipes.duct.types.DuctType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlastFurnace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Smoker;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.inject.Inject;
import java.util.*;

public class ItemService {

    private ItemStack wrench;
    private final YamlConfiguration tempConf;
    private final TransportPipes transportPipes;
    private static List<Recipe> recipeList;

    @Inject
    public ItemService(GeneralConf generalConf, TransportPipes transportPipes) {
        Material wrenchMaterial = Material.getMaterial(generalConf.getWrenchItem().toUpperCase(Locale.ENGLISH));
        Objects.requireNonNull(wrenchMaterial, "The material for the wrench item set in the config file is not valid.");

        wrench = generalConf.getWrenchGlowing() ? createGlowingItem(wrenchMaterial) : new ItemStack(wrenchMaterial);
        wrench = changeDisplayNameAndLoreConfig(wrench, LangConf.Key.WRENCH.getLines());
        ItemMeta meta = wrench.getItemMeta();
        Objects.requireNonNull(meta).setCustomModelData(133744);
        wrench.setItemMeta(meta);
        tempConf = new YamlConfiguration();

        Iterator<Recipe> recipeIterator = Bukkit.recipeIterator();
        
        while(recipeIterator.hasNext()) {
            Recipe recipe = null;

            try {
                recipe = recipeIterator.next();
            }
            catch(Exception e) { }

            if (recipe != null)
                recipeList.add(recipe);
        }

        this.transportPipes = transportPipes;
    }

    public ItemStack getWrench() {
        return wrench;
    }

    public boolean isWrench(ItemStack item) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (Objects.requireNonNull(meta).hasDisplayName() && meta.getDisplayName().equals(LangConf.Key.WRENCH.getLines().get(0))) {
                if (!meta.hasCustomModelData() || meta.getCustomModelData() != 133744) {
                    meta.setCustomModelData(133744);
                    item.setItemMeta(meta);
                }
                return true;
            }
        }
        return false;
    }

    public ItemStack createModelledItem(int damage) {
        ItemStack woodenPickaxe = new ItemStack(Material.WOODEN_PICKAXE);
        ItemMeta meta = woodenPickaxe.getItemMeta();
        
        Objects.requireNonNull(meta).setCustomModelData(133700 + damage);

        ((Damageable) meta).setDamage(damage);
        meta.setUnbreakable(true);
        woodenPickaxe.setItemMeta(meta);

        return woodenPickaxe;
    }

    public ItemStack createGlowingItem(Material material) {
        ItemStack is = new ItemStack(material);
        ItemMeta im = is.getItemMeta();
        Objects.requireNonNull(im).addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, true);
        im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        is.setItemMeta(im);
        return is;
    }

    public ItemStack changeDisplayName(ItemStack is, String displayName) {
        ItemMeta im = is.getItemMeta();
        Objects.requireNonNull(im).setDisplayName(displayName);
        is.setItemMeta(im);
        return is;
    }

    public ItemStack changeDisplayNameAndLore(ItemStack is, String... displayNameAndLore) {
        ItemMeta meta = is.getItemMeta();
        if (displayNameAndLore.length > 0)
            Objects.requireNonNull(meta).setDisplayName(displayNameAndLore[0]);
        if (displayNameAndLore.length > 1)
            meta.setLore(Arrays.asList(displayNameAndLore).subList(1, displayNameAndLore.length));
        is.setItemMeta(meta);
        return is;
    }

    public ItemStack changeDisplayNameAndLoreConfig(ItemStack is, String displayName, List<String> lore) {
        ItemMeta meta = is.getItemMeta();
        Objects.requireNonNull(meta).setDisplayName(displayName);
        meta.setLore(lore);
        is.setItemMeta(meta);
        return is;
    }

    public ItemStack changeDisplayNameAndLoreConfig(ItemStack is, List<String> displayNameAndLore) {
        ItemMeta meta = is.getItemMeta();
        if (displayNameAndLore.size() > 0)
            Objects.requireNonNull(meta).setDisplayName(displayNameAndLore.get(0));
        if (displayNameAndLore.size() > 1)
            meta.setLore(displayNameAndLore.subList(1, displayNameAndLore.size()));
        is.setItemMeta(meta);
        return is;
    }

    public ItemStack createHeadItem(String uuid, String textureValue, String textureSignature) {
        WrappedGameProfile wrappedProfile = new WrappedGameProfile(UUID.fromString(uuid), uuid);
        wrappedProfile.getProperties().put("textures", new WrappedSignedProperty("textures", textureValue, textureSignature));

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        Objects.requireNonNull(skullMeta).setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(uuid)));

        FieldAccessor accessor = Accessors.getFieldAccessorOrNull(skullMeta.getClass(), "profile", wrappedProfile.getHandle().getClass());
        if (accessor != null) {
            accessor.set(skullMeta, wrappedProfile.getHandle());
        }

        skull.setItemMeta(skullMeta);
        return skull;
    }

    public ItemStack setDuctTags(DuctType dt, ItemStack item) {
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta != null) {
            PersistentDataContainer container = itemMeta.getPersistentDataContainer();
            container.set(new NamespacedKey(transportPipes, "basicDuctType"), PersistentDataType.STRING, dt.getBaseDuctType().getName());
            container.set(new NamespacedKey(transportPipes, "ductType"), PersistentDataType.STRING, dt.getName());
            item.setItemMeta(itemMeta);
        }
        return item;
    }

    public DuctType readDuctTags(ItemStack item, DuctRegister ductRegister) {
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta != null) {
            String basicDuctTypeSerialized = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(transportPipes, "basicDuctType"), PersistentDataType.STRING);
            BaseDuctType<? extends Duct> bdt = ductRegister.baseDuctTypeOf(basicDuctTypeSerialized);
            String ductTypeSerialized = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(transportPipes, "ductType"), PersistentDataType.STRING);
            if (ductTypeSerialized != null && !ductTypeSerialized.isEmpty()) {
                return bdt.ductTypeOf(ductTypeSerialized);
            }
        }
        return null;
    }

    public void populateInventoryLine(Inventory inv, int row, ItemStack... items) {
        for (int i = 0; i < 9; i++) {
            if (items.length > i && items[i] != null) {
                ItemStack is = items[i];
                inv.setItem(row * 9 + i, is);
            }
        }
    }

    public ItemStack createWildcardItem(Material material) {
        ItemStack glassPane = new ItemStack(material);
        ItemMeta meta = glassPane.getItemMeta();
        Objects.requireNonNull(meta).getPersistentDataContainer().set(new NamespacedKey(transportPipes, "wildcard"), PersistentDataType.INTEGER, 1);
        glassPane.setItemMeta(meta);
        return changeDisplayNameAndLore(glassPane, ChatColor.RESET.toString());
    }

    public ItemStack createBarrierItem() {
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        Objects.requireNonNull(meta).getPersistentDataContainer().set(new NamespacedKey(transportPipes, "barrier"), PersistentDataType.INTEGER, 1);
        barrier.setItemMeta(meta);
        return changeDisplayNameAndLore(barrier, ChatColor.RESET.toString());
    }

    public boolean isItemWildcardOrBarrier(ItemStack item) {
        if (item != null) {
            switch (item.getType()) {
                case GRAY_STAINED_GLASS_PANE:
                case BLACK_STAINED_GLASS_PANE:
                case RED_STAINED_GLASS_PANE:
                case BLUE_STAINED_GLASS_PANE:
                case LIME_STAINED_GLASS_PANE:
                case WHITE_STAINED_GLASS_PANE:
                case YELLOW_STAINED_GLASS_PANE:
                case BROWN_STAINED_GLASS_PANE:
                case CYAN_STAINED_GLASS_PANE:
                case GREEN_STAINED_GLASS_PANE:
                case LIGHT_BLUE_STAINED_GLASS_PANE:
                case LIGHT_GRAY_STAINED_GLASS_PANE:
                case MAGENTA_STAINED_GLASS_PANE:
                case ORANGE_STAINED_GLASS_PANE:
                case PINK_STAINED_GLASS_PANE:
                case PURPLE_STAINED_GLASS_PANE:
                case BARRIER:
                    if (item.hasItemMeta() && Objects.requireNonNull(item.getItemMeta()).hasDisplayName()) {
                        return item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(transportPipes, "wildcard"), PersistentDataType.INTEGER)
                                || item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(transportPipes,  "barrier"), PersistentDataType.INTEGER);
                    }
                default:
                    return false;
            }
        }
        return false;
    }

    public String serializeItemStack(ItemStack itemStack) {
        tempConf.set("itemStack", itemStack);
        String string = tempConf.saveToString();
        tempConf.set("itemStack", null);
        return string;
    }

    public ItemStack deserializeItemStack(String string) {
        try {
            tempConf.loadFromString(string);
        } catch (InvalidConfigurationException e) {
            e.printStackTrace();
        }
        ItemStack itemStack = tempConf.getItemStack("itemStack");
        tempConf.set("itemStack", null);
        return itemStack;
    }

    @SuppressWarnings("unchecked")
    public ShapedRecipe createShapedRecipe(TransportPipes transportPipes, String recipeKey, ItemStack resultItem, String[] shape, Object... ingredientMap) {
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(transportPipes, recipeKey), resultItem);
        recipe.shape(shape);
        for (int i = 0; i < ingredientMap.length; i += 2) {
            char c = (char) ingredientMap[i];
            if (ingredientMap[i + 1] instanceof Material) {
                recipe.setIngredient(c, (Material) ingredientMap[i + 1]);
            } else {
                recipe.setIngredient(c, new RecipeChoice.MaterialChoice(new ArrayList<>(((Collection<Material>) ingredientMap[i + 1]))));
            }
        }
        return recipe;
    }

    public ShapelessRecipe createShapelessRecipe(TransportPipes transportPipes, String recipeKey, ItemStack resultItem, Material... ingredients) {
        ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(transportPipes, recipeKey), resultItem);
        for (int i = 0; i < ingredients.length; i += 2) {
            recipe.addIngredient(ingredients[i]);
        }
        return recipe;
    }

    public static boolean isFurnaceFuelItem(ItemStack item) {
        return (item.getType().isFuel());
    }

    public static boolean isFurnaceBurnableItem(BlockState blockState, ItemStack item) {
        for (Recipe recipe : recipeList) {
            if (blockState instanceof BlastFurnace) {
                if (!(recipe instanceof BlastingRecipe)) continue;
                if (!((BlastingRecipe) recipe).getInputChoice().test(item)) continue;
            }
            else if (blockState instanceof Smoker) {
                if (!(recipe instanceof SmokingRecipe)) continue;
                if (!((SmokingRecipe) recipe).getInputChoice().test(item)) continue;
            }
            else {
                if (!(recipe instanceof FurnaceRecipe)) continue;
                if(!((FurnaceRecipe) recipe).getInputChoice().test(item)) continue;
            }
            return true;
        }

        return false;
    }

}
