package net.nayrus.noteblockmaster.block.composer;

import libs.felnull.fnnbs.NBS;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.nayrus.noteblockmaster.NoteBlockMaster;
import net.nayrus.noteblockmaster.network.data.SongData;
import net.nayrus.noteblockmaster.setup.Registry;
import net.nayrus.noteblockmaster.utils.Utils;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ComposerBlock extends Block implements EntityBlock {

    public ComposerBlock(ResourceLocation key) {
        super(Block.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, key))
                .sound(SoundType.WOOD)
                .strength(1.0F)
                .noOcclusion());
        this.registerDefaultState(this.getStateDefinition().any());
    }

    @Nullable
    public static NBS loadNBSFile(String name){
        Path filePath = NoteBlockMaster.SONG_DIR.resolve(name+ ".nbs");
        if (Files.exists(filePath)) {
            try {
                return NBS.load(Files.newInputStream(filePath));
            } catch (IOException e) {
                NoteBlockMaster.LOGGER.error(e.getLocalizedMessage());
            }
        } else {
            NoteBlockMaster.LOGGER.warn("File not found: {}", filePath.toAbsolutePath());
        }
        return null;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if(!player.isShiftKeyDown()){
            if(!level.isClientSide())
                attack(state, level, pos, player);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        if(player.isShiftKeyDown()) super.spawnDestroyParticles(level, player, pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if(!(level.getBlockEntity(pos) instanceof ComposerBlockEntity BE)) return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        if(!level.isClientSide()){
            if(!stack.is(Registry.COMPOSITION)) return InteractionResult.TRY_WITH_EMPTY_HAND;
            ItemStack item = BE.getItem();
            float rotation = Utils.getAngleToBlock(pos, player);
            if(item.isEmpty()){
                BE.setItem(stack);
                player.setItemInHand(hand, ItemStack.EMPTY);
                PacketDistributor.sendToPlayer((ServerPlayer) player, new ComposerBlockEntity.ClientItemUpdate(pos, Optional.of(stack), Optional.of(rotation)));
            }else{
                int slot = player.getInventory().getFreeSlot();
                player.setItemInHand(hand, ItemStack.EMPTY);
                player.getInventory().add(slot, item);
                BE.setItem(stack);
                PacketDistributor.sendToPlayer((ServerPlayer) player, new ComposerBlockEntity.ClientItemUpdate(pos, Optional.of(stack), Optional.of(rotation)));
            }
            return InteractionResult.SUCCESS;
        }
        if(stack.is(Registry.COMPOSITION)) return InteractionResult.SUCCESS;
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if(!level.isClientSide() && player instanceof ServerPlayer serverPlayer){
            //serverPlayer.openMenu(state.getMenuProvider(level, pos));
            NBS nbs = loadNBSFile("Bad Apple!");
            if(nbs!= null){
                NoteBlockMaster.LOGGER.debug(nbs.getSongLength()+"");
                NoteBlockMaster.LOGGER.debug(SongData.of(nbs)+"");
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if(!level.isClientSide() && level.getBlockEntity(pos) instanceof ComposerBlockEntity BE){
            ItemStack item = BE.getItem();
            if(!item.isEmpty()){
                popResourceFromFace(level, pos, Direction.UP, item);
                BE.clearItem();
                PacketDistributor.sendToPlayer((ServerPlayer) player, new ComposerBlockEntity.ClientItemUpdate(pos));
            }
        }
        super.attack(state, level, pos, player);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComposerBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(BlockStateProperties.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((containerId, playerInventory, player) -> new ComposerContainer(containerId, playerInventory),
                Component.translatable("menu.title.noteblockmaster.composer"));
    }
}
