package net.nayrus.noteblockmaster.event;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.nayrus.noteblockmaster.item.TunerItem;
import net.nayrus.noteblockmaster.render.ANBInfoRender;
import net.nayrus.noteblockmaster.util.Registry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public class RenderLevelStage {

    @SubscribeEvent
    public static void renderBlockOverlays(RenderLevelStageEvent e){
        if(e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Player player = Minecraft.getInstance().player;
        if(player == null) return;
        ItemStack item = getTunerItem(player);
        if(item.is(Registry.NOTETUNER))
            ANBInfoRender.renderNoteInfo(e, player);
    }

    public static ItemStack getTunerItem(Player player){
        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof TunerItem)) {
            heldItem = player.getOffhandItem();
            if (!(heldItem.getItem() instanceof TunerItem)) {
                return ItemStack.EMPTY;
            }
        }
        return heldItem;
    }

}
