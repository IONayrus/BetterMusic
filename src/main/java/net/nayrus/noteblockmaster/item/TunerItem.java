package net.nayrus.noteblockmaster.item;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.nayrus.noteblockmaster.block.AdvancedNoteBlock;
import net.nayrus.noteblockmaster.network.data.ComposeData;
import net.nayrus.noteblockmaster.network.data.TunerData;
import net.nayrus.noteblockmaster.screen.NoteTunerScreen;
import net.nayrus.noteblockmaster.screen.TempoTunerScreen;
import net.nayrus.noteblockmaster.setup.Registry;
import net.nayrus.noteblockmaster.utils.Utils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;


public class TunerItem extends Item {
    public TunerItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack itemStack) {
        return itemStack.copy();
    }

    public static TunerData getTunerData(ItemStack stack){
        TunerData data = stack.get(Registry.TUNER_DATA);
        if(data == null) {
            data = TunerData.of(1, false, false);
            stack.set(Registry.TUNER_DATA, data);
        }
        return data;
    }

    public InteractionResult useOn(UseOnContext context, boolean doOffHandSwing) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        ItemStack tuner = context.getItemInHand();
        if(player==null) return InteractionResult.FAIL;
        ItemStack composer = player.getOffhandItem();
        if(!tuner.is(this)){
            tuner = composer;
            composer = context.getItemInHand();
        }
        TunerData data = getTunerData(tuner);
        if(!(state.getBlock() instanceof AdvancedNoteBlock block)){
            if(!data.isSetmode() && !composer.is(Registry.COMPOSER)) return InteractionResult.PASS;
            Inventory inv = player.getInventory();
            if(inv.contains(item -> item.is(Registry.ADVANCED_NOTEBLOCK.asItem())) && !doOffHandSwing){
                placeAdvancedNoteBlock(level, tuner, pos, data, composer, player, inv);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        if(!player.isShiftKeyDown()) {
            if (tuner.is(Registry.TEMPOTUNER)) return changeSubtickOn(level, block, composer, data, state, player, pos, doOffHandSwing);
            if (tuner.is(Registry.NOTETUNER)) return changeNoteOn(level, state, block, data, player, pos);
        }
        return InteractionResult.PASS;
    }

    private static void placeAdvancedNoteBlock(Level level, ItemStack tuner, BlockPos pos, TunerData data, ItemStack composer, Player player, Inventory inv) {
        if(!level.isClientSide()){
            AdvancedNoteBlock block = (AdvancedNoteBlock) Registry.ADVANCED_NOTEBLOCK.get();
            if(tuner.is(Registry.NOTETUNER)) {
                BlockState state = block.defaultBlockState().setValue(AdvancedNoteBlock.NOTE, data.value() + AdvancedNoteBlock.MIN_NOTE_VAL);
                if(composer.is(Registry.TEMPOTUNER)) state = state.setValue(AdvancedNoteBlock.SUBTICK, getTunerData(composer).value());
                level.setBlockAndUpdate(pos.above(), block.setInstrument(level, pos.above(), state));
            }
            else{
                if(composer.is(Registry.COMPOSER)){
                    ComposeData cData = ComposeData.getComposeData(composer);
                    if(!player.isShiftKeyDown()){
                        Tuple<Integer, Integer> next = ComposersNote.subtickAndPauseOnBeat(cData.beat() + 1, cData.bpm());
                        ComposeData new_ = new ComposeData(cData.beat() + 1, next.getA(), next.getB(), cData.bpm());
                        composer.set(Registry.COMPOSE_DATA, new_);
                        cData = new_;
                    }
                    level.setBlockAndUpdate(pos.above(), block.setInstrument(level, pos.above(), block.defaultBlockState())
                            .setValue(AdvancedNoteBlock.SUBTICK, cData.subtick()));
                }
                else{
                    BlockState state = block.defaultBlockState().setValue(AdvancedNoteBlock.SUBTICK, data.value());
                    if(composer.is(Registry.NOTETUNER)) state = state.setValue(AdvancedNoteBlock.NOTE, getTunerData(composer).value() + AdvancedNoteBlock.MIN_NOTE_VAL);
                    level.setBlockAndUpdate(pos.above(), block.setInstrument(level, pos.above(), state));
                }
            }
            level.playSound(null, pos, SoundType.WOOD.getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
            if (!player.isCreative()) Utils.removeItemsFromInventory(inv, Registry.ADVANCED_NOTEBLOCK.asItem(), 1);
        }
    }

    private static InteractionResult changeNoteOn(Level level, BlockState state, AdvancedNoteBlock block, TunerData data, Player player, BlockPos pos) {
        if(!level.isClientSide()) {
            int new_val = data.isSetmode() ? data.value() + AdvancedNoteBlock.MIN_NOTE_VAL : block.changeNoteValueBy(state, data.value());
            return block.onNoteChange(level, player, state, pos, new_val);
        }
        return InteractionResult.CONSUME;
    }

    private static InteractionResult changeSubtickOn(Level level, AdvancedNoteBlock block, ItemStack composer, TunerData data, BlockState state, Player player, BlockPos pos, boolean doOffHandSwing) {
        if(!level.isClientSide()) {
            int new_val;
            if (!composer.is(Registry.COMPOSER))
                new_val = (data.isSetmode() ? data.value() : state.getValue(AdvancedNoteBlock.SUBTICK) + data.value()) % AdvancedNoteBlock.SUBTICKS;
            else {
                ComposeData cData = ComposeData.getComposeData(composer);
                new_val = cData.subtick();

                Tuple<Integer, Integer> next = ComposersNote.subtickAndPauseOnBeat(cData.beat() + 1, cData.bpm());
                composer.set(Registry.COMPOSE_DATA, new ComposeData(cData.beat() + 1, next.getA(), next.getB(), cData.bpm()));
            }
            if (!doOffHandSwing) return block.onSubtickChange(level, player, state, pos, new_val, true);
            else {
                block.onSubtickChange(level, player, state, pos, new_val, true);
                return InteractionResult.CONSUME;
            }
        }
        if(doOffHandSwing) player.swing(InteractionHand.OFF_HAND);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return useOn(context, false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemstack = player.getItemInHand(usedHand);
        if(level.isClientSide()) openTunerGUI(
                itemstack,
                player.getItemInHand(InteractionHand.values()[(usedHand.ordinal() + 1) % 2]),
                usedHand == InteractionHand.OFF_HAND);
        return new InteractionResultHolder<>(InteractionResult.SUCCESS, itemstack);
    }

    @OnlyIn(Dist.CLIENT)
    public static void openTunerGUI(ItemStack tuner, ItemStack second, boolean tunerInOffhand){
        if(tuner.is(Registry.TEMPOTUNER))
            Minecraft.getInstance().setScreen(new TempoTunerScreen(tuner, second, tunerInOffhand));
        if(tuner.is(Registry.NOTETUNER))
            Minecraft.getInstance().setScreen(new NoteTunerScreen(tuner, tunerInOffhand));
    }
}
