package de.robotricker.transportpipes.config;

import de.robotricker.transportpipes.TransportPipes;
import de.robotricker.transportpipes.duct.Duct;
import de.robotricker.transportpipes.duct.types.BaseDuctType;
import de.robotricker.transportpipes.rendersystems.RenderSystem;
import org.bukkit.entity.Player;

public class PlayerSettingsConf extends Conf {

    private final GeneralConf generalConf;

    public PlayerSettingsConf(TransportPipes transportPipes, GeneralConf generalConf, Player p) {
        super(transportPipes, "playerconfig.yml", "playersettings/" + p.getUniqueId() + ".yml", false);
        this.generalConf = generalConf;
    }

    public int getRenderDistance() {
        if (!getYamlConf().contains("render_distance")) {
            setRenderDistance(generalConf.getDefaultRenderDistance());
        }
        return (int) read("render_distance");
    }

    public void setRenderDistance(int renderDistance) {
        overrideSync("render_distance", renderDistance);
    }

    public String getRenderSystemName() {
        if (!getYamlConf().contains("render_system")) {
            setRenderSystemName(generalConf.getDefaultRenderSystemName());
        }
        return (String) read("render_system");
    }

    public void setRenderSystemName(String name) {
        overrideSync("render_system", name);
    }

    public RenderSystem getRenderSystem(BaseDuctType<? extends Duct> baseDuctType) {
        return RenderSystem.getRenderSystem(getRenderSystemName(), baseDuctType);
    }

    public boolean isShowItems() {
        if (!getYamlConf().contains("show_items")) {
            setShowItems(generalConf.isDefaultShowItems());
        }
        return (int) read("show_items") == 1;
    }

    public void setShowItems(boolean showItems) {
        overrideSync("show_items", showItems ? 1 : 0);
    }

}
