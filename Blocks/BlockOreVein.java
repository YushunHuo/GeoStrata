package Reika.GeoStrata.Blocks;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import Reika.DragonAPI.ModList;
import Reika.DragonAPI.ASM.APIStripper.Strippable;
import Reika.DragonAPI.ASM.DependentMethodStripper.ModDependent;
import Reika.DragonAPI.Exception.InstallationException;
import Reika.DragonAPI.Exception.RegistrationException;
import Reika.DragonAPI.Exception.UserErrorException;
import Reika.DragonAPI.Instantiable.Data.WeightedRandom;
import Reika.DragonAPI.Instantiable.Data.Maps.ItemHashMap;
import Reika.DragonAPI.Instantiable.IO.CustomRecipeList;
import Reika.DragonAPI.Instantiable.IO.LuaBlock;
import Reika.DragonAPI.Instantiable.IO.LuaBlock.LuaBlockDatabase;
import Reika.DragonAPI.Libraries.ReikaPlayerAPI;
import Reika.DragonAPI.Libraries.IO.ReikaSoundHelper;
import Reika.DragonAPI.Libraries.Java.ReikaJavaLibrary;
import Reika.GeoStrata.GeoStrata;
import Reika.GeoStrata.Registry.GeoISBRH;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;

@Strippable(value = {"mcp.mobius.waila.api.IWailaDataProvider", "framesapi.IMoveCheck", "vazkii.botania.api.mana.ILaputaImmobile"})
public class BlockOreVein extends BlockContainer implements IWailaDataProvider {

	public BlockOreVein(Material mat) {
		super(mat);
		this.setCreativeTab(GeoStrata.tabGeo);
	}

	public static enum VeinType {
		STONE(Blocks.stone, 0, Blocks.iron_block),
		ICE(Blocks.packed_ice, 1, Blocks.diamond_block),
		NETHER(Blocks.netherrack, 0, Blocks.gold_block),
		END(Blocks.end_stone, 0, Blocks.obsidian);

		public final Block template;
		public final int templateMeta;
		public final Block containedBlockIcon;
		private final WeightedRandom<HarvestableOre> ores = new WeightedRandom();

		public static final VeinType[] list = values();

		private VeinType(Block b, int m, Block b2) {
			template = b;
			templateMeta = m;
			containedBlockIcon = b2;
		}
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileOreVein();
	}

	@Override
	public Item getItemDropped(int meta, Random rand, int fortune) {
		return null;
	}

	@Override
	public IIcon getIcon(IBlockAccess iba, int x, int y, int z, int s) {
		VeinType v = VeinType.list[iba.getBlockMetadata(x, y, z)];
		return v.template.getIcon(s, v.templateMeta);
	}

	@Override
	public void registerBlockIcons(IIconRegister ico) {
		blockIcon = ico.registerIcon("geostrata:orevein");
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer ep, int s, float a, float b, float c) {
		TileOreVein te = (TileOreVein)world.getTileEntity(x, y, z);
		if (te == null)
			return false;
		if (world.isRemote)
			return true;
		ItemStack get = te.tryHarvest();
		if (get != null) {
			ReikaPlayerAPI.addOrDropItem(get, ep);
			ReikaSoundHelper.playSoundAtBlock(world, x, y, z, "random.pop");
		}
		return true;
	}

	@Override
	public int getRenderType() {
		return GeoISBRH.orevein.getRenderID();
	}

	@Override
	public boolean canEntityDestroy(IBlockAccess world, int x, int y, int z, Entity e) {
		return false;
	}

	@Override
	@ModDependent(ModList.WAILA)
	public ItemStack getWailaStack(IWailaDataAccessor acc, IWailaConfigHandler config) {
		return null;
	}

	@Override
	@ModDependent(ModList.WAILA)
	public final List<String> getWailaHead(ItemStack is, List<String> tip, IWailaDataAccessor acc, IWailaConfigHandler config) {
		return tip;
	}

	@Override
	@ModDependent(ModList.WAILA)
	public final List<String> getWailaBody(ItemStack is, List<String> tip, IWailaDataAccessor acc, IWailaConfigHandler config) {
		TileEntity te = acc.getTileEntity();
		if (te instanceof TileOreVein) {

		}
		return tip;
	}

	@ModDependent(ModList.WAILA)
	public final List<String> getWailaTail(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor acc, IWailaConfigHandler config) {
		return currenttip;
	}

	@Override
	public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x, int y, int z) {
		return tag;
	}

	private static final LuaBlockDatabase oreData = new LuaBlockDatabase();

	public static void loadConfigs() {
		oreData.clear();
		GeoStrata.logger.log("Loading ore vein configs.");
		File f = new File(GeoStrata.config.getConfigFolder(), "GeoStrataOreVeinConfig.lua");
		if (f.exists()) {
			try {
				oreData.loadFromFile(f);
				LuaBlock root = oreData.getRootBlock();
				for (LuaBlock b : root.getChildren()) {
					try {
						String type = b.getString("type");
						GeoStrata.logger.log("Parsing block '"+type+"'");
						oreData.addBlock(type, b);
						parseOreEntry(type, b);
					}
					catch (Exception e) {
						GeoStrata.logger.logError("Could not parse config section "+b.getString("type")+": ");
						ReikaJavaLibrary.pConsole(b);
						ReikaJavaLibrary.pConsole("----------------------Cause------------------------");
						e.printStackTrace();
					}
				}
			}
			catch (Exception e) {
				if (e instanceof UserErrorException)
					throw new InstallationException(GeoStrata.instance, "Configs could not be loaded! Correct them and try again.", e);
				else
					throw new RegistrationException(GeoStrata.instance, "Configs could not be loaded! Correct them and try again.", e);
			}

			GeoStrata.logger.log("Configs loaded.");
		}
	}

	private static void parseOreEntry(String type, LuaBlock b) throws NumberFormatException, IllegalArgumentException, IllegalStateException {
		ArrayList<HarvestableOre> blocks = new ArrayList();

		VeinType vein = VeinType.valueOf(b.getString("veinType").toUpperCase(Locale.ENGLISH));

		LuaBlock set = b.getChild("items");
		if (set == null)
			throw new IllegalArgumentException("No items specified");

		for (LuaBlock lb : set.getChildren()) {
			String item = lb.getString("item");
			ItemStack find = CustomRecipeList.parseItemString(item, null, true);
			if (find == null) {
				GeoStrata.logger.logError("No such item '"+item+"', skipping");
				continue;
			}
			blocks.add(new HarvestableOre(find, lb.getDouble("weight"), lb.getDouble("interval")));
		}

		if (blocks.isEmpty())
			throw new IllegalArgumentException("No usable items found");

		for (HarvestableOre o : blocks) {
			vein.ores.addEntry(o, o.spawnWeight);
		}
	}

	private static class HarvestableOre {

		private final ItemStack item;
		private final double spawnWeight;
		private final int minInterval;

		private HarvestableOre(ItemStack is, double wt, double seconds) {
			item = is.copy();
			spawnWeight = wt;
			minInterval = (int)(seconds*20);
		}

	}

	public static class TileOreVein extends TileEntity {

		private final ItemHashMap<Long> lastHarvest = new ItemHashMap();

		@Override
		public boolean canUpdate() {
			return false;
		}

		public float getRichness() {
			return 0.8F;
		}

		@Override
		public void writeToNBT(NBTTagCompound NBT) {
			super.writeToNBT(NBT);

			NBTTagCompound tag = new NBTTagCompound();
			lastHarvest.writeToNBT(tag, null);
			NBT.setTag("harvests", tag);
		}

		@Override
		public void readFromNBT(NBTTagCompound NBT) {
			super.readFromNBT(NBT);

			NBTTagCompound tag = NBT.getCompoundTag("harvests");
			lastHarvest.readFromNBT(tag, null);
		}

		public ItemStack tryHarvest() {
			VeinType v = VeinType.list[this.getBlockMetadata()];
			if (v.ores.isEmpty())
				return null;
			HashSet<HarvestableOre> ores = new HashSet(v.ores.getValues());
			Iterator<HarvestableOre> it = ores.iterator();
			long time = worldObj.getTotalWorldTime();
			while (it.hasNext()) {
				HarvestableOre ore = it.next();
				Long last = lastHarvest.get(ore.item);
				if (last != null && time-last < ore.minInterval)
					it.remove();
			}
			if (ores.isEmpty())
				return null;
			HarvestableOre ret = v.ores.getRandomEntry();
			while (!ores.contains(ret))
				ret = v.ores.getRandomEntry();
			lastHarvest.put(ret.item, time);
			return ret.item.copy();
		}

		@Override
		public final Packet getDescriptionPacket() {
			NBTTagCompound NBT = new NBTTagCompound();
			this.writeToNBT(NBT);
			S35PacketUpdateTileEntity pack = new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, NBT);
			return pack;
		}

		@Override
		public final void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity p)  {
			this.readFromNBT(p.field_148860_e);
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}

	}
}
