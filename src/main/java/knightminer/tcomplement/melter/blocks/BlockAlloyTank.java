package knightminer.tcomplement.melter.blocks;

import java.util.List;
import java.util.Random;

import javax.annotation.Nonnull;

import com.google.common.collect.Lists;

import knightminer.tcomplement.library.TCompRegistry;
import knightminer.tcomplement.library.Util;
import knightminer.tcomplement.melter.blocks.BlockMelter.MelterType;
import knightminer.tcomplement.melter.tileentity.TileAlloyTank;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import slimeknights.tconstruct.library.smeltery.IFaucetDepth;
import slimeknights.tconstruct.smeltery.tileentity.TileTank;

public class BlockAlloyTank extends Block implements ITileEntityProvider, IFaucetDepth {

	public static final PropertyBool POWERED = PropertyBool.create("powered");

	private BlockMelter melter;
	private Block tank;
	public BlockAlloyTank(BlockMelter melter, Block tank) {
		super(Material.ROCK);
		this.setCreativeTab(TCompRegistry.tabGeneral);
		this.setHardness(3F);
		this.setResistance(20F);
		this.setSoundType(SoundType.METAL);
		this.setDefaultState(this.blockState.getBaseState().withProperty(POWERED, false));

		this.melter = melter;
		this.tank = tank;
	}


	/* Tank logic */

	/**
	 * Checks if the passed block is a valid heater for the melter
	 * @param state  Block state to check
	 * @return  True if the block state is a valid heater
	 */
	public boolean isHeater(IBlockState state) {
		Block block = state.getBlock();
		return block == tank || (block == melter && state.getValue(BlockMelter.TYPE) == MelterType.HEATER);
	}

	/**
	 * Checks if the passed block is a valid side tank for this alloy tank
	 * @param state  Block state to check
	 * @return  True if the block state is a valid side tank
	 */
	public boolean isTank(IBlockState state) {
		Block block = state.getBlock();
		return block == tank || (block == melter && state.getValue(BlockMelter.TYPE) == MelterType.MELTER);
	}

	@Nonnull
	@Override
	public TileEntity createNewTileEntity(@Nonnull World worldIn, int meta) {
		return new TileAlloyTank();
	}

	@Override
	public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
		updatePower(state, world, pos);
	}

	@Override
	@Deprecated
	public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
		updatePower(state, world, pos);
		// check structure
		TileEntity te = world.getTileEntity(pos);
		if(te instanceof TileAlloyTank) {
			((TileAlloyTank)te).checkTanks();
		}
	}

	private static void updatePower(IBlockState state, World world, BlockPos pos) {
		boolean powered = world.isBlockPowered(pos);
		if (powered != state.getValue(POWERED)){
			world.setBlockState(pos, state.withProperty(POWERED, powered), 4);
		}
	}

	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		if(Util.onFluidTankActivated(world, pos, player, hand, facing)) {
			return true;
		}
		return FluidUtil.getFluidHandler(player.getHeldItem(hand)) != null;
	}


	/* Retain liquids */

	@Override
	public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
		TileEntity te = world.getTileEntity(pos);
		if(te instanceof TileAlloyTank) {
			TileAlloyTank alloyer = (TileAlloyTank) te;
			if(!stack.isEmpty() && stack.hasTagCompound()) {
				alloyer.readTankFromNBT(stack.getTagCompound());
			}
			alloyer.checkTanks();
		}
	}

	@Nonnull
	@Override
	@Deprecated
	public List<ItemStack> getDrops(IBlockAccess world, BlockPos pos, @Nonnull IBlockState state, int fortune) {
		// standard drop logic
		List<ItemStack> ret = Lists.newArrayList();
		Random rand = world instanceof World ? ((World) world).rand : RANDOM;
		Item item = this.getItemDropped(state, rand, fortune);
		ItemStack stack = ItemStack.EMPTY;
		if(item != Items.AIR) {
			stack = new ItemStack(item, 1, this.damageDropped(state));
			ret.add(stack);
		}

		// save liquid data on the stack
		TileEntity te = world.getTileEntity(pos);
		if(te instanceof TileTank && !stack.isEmpty()) {
			if(((TileTank) te).containsFluid()) {
				NBTTagCompound tag = new NBTTagCompound();
				((TileTank) te).writeTankToNBT(tag);
				stack.setTagCompound(tag);
			}
		}
		return ret;
	}

	// fix blockbreak logic order. Needed to have the tile entity when getting the drops
	@Override
	public boolean removedByPlayer(@Nonnull IBlockState state, World world, @Nonnull BlockPos pos, @Nonnull EntityPlayer player, boolean willHarvest) {
		// we pull up a few calls to this point in time because we still have the TE here
		// the execution otherwise is equivalent to vanilla order
		this.onBlockDestroyedByPlayer(world, pos, state);
		if(willHarvest) {
			this.harvestBlock(world, player, pos, state, world.getTileEntity(pos), player.getHeldItemMainhand());
		}

		world.setBlockToAir(pos);
		// return false to prevent the above called functions to be called again
		// side effect of this is that no xp will be dropped. but it shoudln't anyway
		return false;
	}


	/* Block state logic */

	@Override
	protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, POWERED);
	}

	@Override
	@Deprecated
	public IBlockState getStateFromMeta(int meta) {
		return this.getDefaultState().withProperty(POWERED, (meta & 8) > 0);
	}

	@Override
	public int getMetaFromState(IBlockState state) {
		return state.getValue(POWERED) ? 8 : 0;
	}

	/* Rendering stuff etc */

	@Override
	public int getLightValue(@Nonnull IBlockState state, IBlockAccess world, @Nonnull BlockPos pos) {
		TileEntity te = world.getTileEntity(pos);
		if(!(te instanceof TileTank)) {
			return 0;
		}
		TileTank tank = (TileTank) te;
		return tank.getBrightness();
	}

	@Override
	public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
		TileEntity te = world.getTileEntity(pos);
		if(te instanceof TileAlloyTank && ((TileAlloyTank)te).isActive()) {
			// offset position to center of block
			double x = pos.getX() + 0.5D;
			double y = pos.getY() + 0.5D;
			double z = pos.getZ() + 0.5D;
			// just beyond the face
			double front = 0.52D;

			// normally used for X and Z
			double side = rand.nextDouble() * 0.8D - 0.4D;
			// normally used for Y
			double top = rand.nextDouble() * 0.8D - 0.4D;

			// choose a random side (or the top) to offset
			switch(rand.nextInt(5)) {
				case 0:
					spawnFireParticle(world, x - front, y + top, z + side);
					break;
				case 1:
					spawnFireParticle(world, x + front, y + top, z + side);
					break;
				case 2:
					spawnFireParticle(world, x + side, y + top, z - front);
					break;
				case 3:
					spawnFireParticle(world, x + side, y + top, z + front);
					break;
				case 4:
					spawnFireParticle(world, x + side, y + front, z + top);
			}
		}
	}

	private static void spawnFireParticle(World world, double x, double y, double z) {
		world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, x, y, z, 0.0D, 0.0D, 0.0D);
		world.spawnParticle(EnumParticleTypes.FLAME, x, y, z, 0.0D, 0.0D, 0.0D);
	}

	@Nonnull
	@Override
	@SideOnly(Side.CLIENT)
	public BlockRenderLayer getBlockLayer() {
		return BlockRenderLayer.CUTOUT;
	}

	@Override
	@Deprecated
	public boolean isFullCube(IBlockState state) {
		return false;
	}

	@Override
	@Deprecated
	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}

	@Override
	@Deprecated
	public boolean hasComparatorInputOverride(IBlockState state) {
		return true;
	}

	@Override
	@Deprecated
	public int getComparatorInputOverride(IBlockState blockState, World world, BlockPos pos) {
		TileEntity te = world.getTileEntity(pos);
		if(!(te instanceof TileTank)) {
			return 0;
		}

		return ((TileTank) te).comparatorStrength();
	}

	@Override
	public float getFlowDepth(World world, BlockPos pos, IBlockState state) {
		return 1;
	}
}
