/*******************************************************************************
 * @author Reika Kalseki
 *
 * Copyright 2017
 *
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.ChromatiCraft.World.IWG;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.ChestGenHooks;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fluids.BlockFluidBase;

import Reika.ChromatiCraft.ChromatiCraft;
import Reika.ChromatiCraft.Auxiliary.Structure.Worldgen.DesertStructure;
import Reika.ChromatiCraft.Auxiliary.Structure.Worldgen.OceanStructure;
import Reika.ChromatiCraft.Base.ChromaStructureBase;
import Reika.ChromatiCraft.Block.Worldgen.BlockLootChest.TileEntityLootChest;
import Reika.ChromatiCraft.Block.Worldgen.BlockStructureShield.BlockType;
import Reika.ChromatiCraft.ModInterface.MystPages;
import Reika.ChromatiCraft.Registry.ChromaBlocks;
import Reika.ChromatiCraft.Registry.ChromaOptions;
import Reika.ChromatiCraft.Registry.ChromaStructures;
import Reika.ChromatiCraft.Registry.ChromaTiles;
import Reika.ChromatiCraft.Registry.CrystalElement;
import Reika.ChromatiCraft.Registry.ExtraChromaIDs;
import Reika.ChromatiCraft.TileEntity.Technical.TileEntityStructControl;
import Reika.DragonAPI.ModList;
import Reika.DragonAPI.Instantiable.Data.BlockStruct.BlockArray;
import Reika.DragonAPI.Instantiable.Data.BlockStruct.FilledBlockArray;
import Reika.DragonAPI.Instantiable.Data.Immutable.Coordinate;
import Reika.DragonAPI.Instantiable.Data.Immutable.WorldChunk;
import Reika.DragonAPI.Instantiable.Data.Immutable.WorldLocation;
import Reika.DragonAPI.Instantiable.Data.Maps.TileEntityCache;
import Reika.DragonAPI.Instantiable.IO.NBTFile;
import Reika.DragonAPI.Interfaces.RetroactiveGenerator;
import Reika.DragonAPI.Interfaces.Registry.TreeType;
import Reika.DragonAPI.Libraries.ReikaSpawnerHelper;
import Reika.DragonAPI.Libraries.Java.ReikaJavaLibrary;
import Reika.DragonAPI.Libraries.Java.ReikaRandomHelper;
import Reika.DragonAPI.Libraries.MathSci.ReikaMathLibrary;
import Reika.DragonAPI.Libraries.Registry.ReikaTreeHelper;
import Reika.DragonAPI.Libraries.World.ReikaBiomeHelper;
import Reika.DragonAPI.Libraries.World.ReikaBlockHelper;
import Reika.DragonAPI.Libraries.World.ReikaWorldHelper;
import Reika.DragonAPI.ModInteract.DeepInteract.PlanetDimensionHandler;
import Reika.DragonAPI.ModInteract.DeepInteract.ReikaMystcraftHelper;
import Reika.DragonAPI.ModInteract.ItemHandlers.ExtraUtilsHandler;
import Reika.DragonAPI.ModInteract.ItemHandlers.TwilightForestHandler;
import Reika.DragonAPI.ModRegistry.ModWoodList;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class DungeonGenerator implements RetroactiveGenerator {

	public static final DungeonGenerator instance = new DungeonGenerator();

	private final ForgeDirection[] dirs = ForgeDirection.values();

	private final ArrayList<ChromaStructures> structs = new ArrayList();

	private final ConcurrentHashMap<ChromaStructures, TileEntityCache<Coordinate>> generatedStructures;
	private final EnumMap<ChromaStructures, Stack<StructureGeneration>> currentGenerationQueue = new EnumMap(ChromaStructures.class);

	private String baseFilepath;

	private boolean needsSave;

	private DungeonGenerator() {
		structs.add(ChromaStructures.CAVERN);
		structs.add(ChromaStructures.BURROW);
		structs.add(ChromaStructures.OCEAN);
		structs.add(ChromaStructures.DESERT);
		structs.add(ChromaStructures.SNOWSTRUCT);

		generatedStructures = new ConcurrentHashMap();

		for (ChromaStructures s : structs) {
			generatedStructures.put(s, new TileEntityCache());
			currentGenerationQueue.put(s, new Stack());
		}

		MinecraftForge.EVENT_BUS.register(this);
	}

	public Collection<ChromaStructures> getStructureTypes() {
		return Collections.unmodifiableCollection(structs);
	}

	public void initLevelData(MinecraftServer instance) {
		baseFilepath = DimensionManager.getCurrentSaveRootDirectory()+"/ChromatiCraft_Data/StructureCache/";
		this.loadData();
	}

	private final String getFilepath(ChromaStructures s) {
		return baseFilepath+s.name().toLowerCase()+".dat";
	}

	private void loadData() {
		for (ChromaStructures s : structs) {
			generatedStructures.put(s, new TileEntityCache());
		}

		for (ChromaStructures s : structs) {
			File f = new File(this.getFilepath(s));
			StructureFile pf = new StructureFile(f);
			TileEntityCache<Coordinate> cache = generatedStructures.get(s);
			try {
				pf.load();
				for (WorldLocation loc : pf.entries) {
					cache.put(loc, new Coordinate(loc));
				}
			}
			catch (Throwable e) {
				ChromatiCraft.logger.logError("Could not load structure cache: "+f.getName()+"; try deleting the file '"+f.getAbsolutePath()+"'");
				e.printStackTrace();
			}
		}

		needsSave = false;
	}

	private void saveData() {
		for (ChromaStructures s : structs) {
			File f = new File(this.getFilepath(s));
			StructureFile pf = new StructureFile(f);
			for (WorldLocation p : generatedStructures.get(s).keySet()) {
				pf.entries.add(p);
			}
			try {
				pf.save();
			}
			catch (Exception e) {
				ChromatiCraft.logger.logError("Could not save structure cache: "+f.getName());
				e.printStackTrace();
			}
		}

		needsSave = false;
	}

	@SubscribeEvent
	public void saveData(WorldEvent.Save evt) {
		if (needsSave)
			this.saveData();
	}

	public void printCache(ICommandSender ics) {
		ReikaJavaLibrary.pConsole("["+FMLCommonHandler.instance().getEffectiveSide()+"] Cache Debug: "+generatedStructures);
	}

	public Collection<WorldLocation> getNearbyStructures(ChromaStructures s, World world, double x, double y, double z, double r) {
		return generatedStructures.get(s).getAllLocationsNear(new WorldLocation(world, (int)Math.round(x), (int)Math.round(y), (int)Math.round(z)), r);
	}

	public WorldLocation getNearestStructure(ChromaStructures s, World world, double x, double y, double z, double r) {
		return this.getNearestStructure(s, world, x, y, z, r, null);
	}

	public WorldLocation getNearestStructure(ChromaStructures s, World world, double x, double y, double z, double r, WorldLocation exclude) {
		Collection<WorldLocation> c = generatedStructures.get(s).getAllLocationsNear(new WorldLocation(world, (int)Math.round(x), (int)Math.round(y), (int)Math.round(z)), r);
		WorldLocation closest = null;
		double d = Double.POSITIVE_INFINITY;
		for (WorldLocation loc : c) {
			if (exclude != null && exclude.equals(loc))
				continue;
			double dist = loc.getDistanceTo(x, y, z);
			if ((closest == null || dist < d) && dist <= r) {
				d = dist;
				closest = loc;
			}
		}
		return closest;
	}

	public boolean isStructureWithin(ChromaStructures s, World world, int x, int y, int z, double r) {
		return this.getNearestStructure(s, world, x, y, z, r) != null;
	}

	public void generateStructure(ChromaStructures s, TileEntityStructControl te) {
		WorldLocation loc = new WorldLocation(te);
		if (!generatedStructures.get(s).containsKey(loc)) {
			generatedStructures.get(s).put(loc, new Coordinate(te));
			needsSave = true;
		}
	}

	public void deleteStructure(ChromaStructures s, TileEntityStructControl te) {
		WorldLocation loc = new WorldLocation(te);
		if (generatedStructures.get(s).containsKey(loc)) {
			generatedStructures.get(s).remove(loc);
			needsSave = true;
		}
	}

	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {
		if (this.canGenerateIn(world)) {
			for (ChromaStructures s : structs) {
				this.checkChunk(world, chunkX, chunkZ, random, s);
			}
		}
	}

	private boolean checkChunk(World world, int chunkX, int chunkZ, Random random, ChromaStructures s) {
		if (this.isGennableChunk(world, chunkX*16, chunkZ*16, random, s)) {
			//ReikaWorldHelper.forceGenAndPopulate(world, chunkX*16, chunkZ*16, s == Structures.OCEAN ? 2 : 1); causes extra structures
			currentGenerationQueue.get(s).push(new StructureGeneration(s, world, chunkX*16, chunkZ*16));
			boolean flag = false;
			if (this.tryGenerate(world, chunkX*16, chunkZ*16, random, s)) {
				flag = true;
				//ChromatiCraft.logger.log("Successful generation of "+s.name()+" at "+chunkX*16+", "+chunkZ*16);
			}
			StructureGeneration done = currentGenerationQueue.get(s).pop();
			if (done.isDelayed) {
				ChromatiCraft.logger.log("Delayed "+done+" succeeded");
			}
			for (StructureGeneration del : done.delayed) {
				//ChromatiCraft.logger.log("Re-running delayed generator "+del);
				if (this.checkChunk(world, del.location.chunk.chunkXPos >> 4, del.location.chunk.chunkZPos >> 4, random, del.type)) {
					//ChromatiCraft.logger.log("Delayed generator "+gen+" failed exclusion zone, would have maybe been a duplicate");
				}
			}
			return flag;
		}
		return false;
	}

	private boolean tryGenerate(World world, int cx, int cz, Random r, ChromaStructures s) {
		int x = cx + r.nextInt(16);
		int z = cz + r.nextInt(16);
		s.getStructure().resetToDefaults();
		switch(s) {
			case CAVERN: {
				int y = 10+r.nextInt(40);
				int tries = 0;
				while (tries < 10 && !this.isValidCavernLocation(world, x, y, z, ChromaStructures.CAVERN.getArray(world, x, y, z))) {
					y = 10+r.nextInt(40);
					x = cx + r.nextInt(16);
					z = cz + r.nextInt(16);
					tries++;
				}
				FilledBlockArray struct = ChromaStructures.CAVERN.getArray(world, x, y, z);
				if (this.isValidCavernLocation(world, x, y, z, struct)) {
					struct.place(2);
					//generate tunnel
					for (int i = 7; i < 18; i++) {
						int dx = x+i;
						Block b = world.getBlock(dx, y, z);
						Block b2 = world.getBlock(dx, y-1, z);
						if (b.isAir(world, dx, y, z) && b2.isAir(world, dx, y-1, z)) {
							break;
						}
						else {
							world.setBlock(dx, y, z, Blocks.air);
							world.setBlock(dx, y-1, z, Blocks.air);
							//ReikaJavaLibrary.pConsole("Digging tunnel @ depth "+i);
						}
					}
					//ChromatiCraft.logger.log("Successful generation of "+s.name()+" at "+x+","+y+","+z);
					world.setBlock(x, y, z, ChromaTiles.STRUCTCONTROL.getBlock(), ChromaTiles.STRUCTCONTROL.getBlockMetadata(), 3);
					TileEntityStructControl te = (TileEntityStructControl)world.getTileEntity(x, y, z);
					te.generate(s, CrystalElement.WHITE);
					this.generateStructure(s, te);
					this.populateChests(s, struct, r);
					return true;
				}
				return false;
			}
			case BURROW: {
				int y = world.getTopSolidOrLiquidBlock(x, z)-1;
				CrystalElement e = CrystalElement.randomElement();
				FilledBlockArray arr = ChromaStructures.BURROW.getArray(world, x, y, z, e);
				if (this.isValidBurrowLocation(world, x, y, z, arr)) {
					arr.place(2);
					this.convertDirtToGrass(arr);
					//world.setBlockMetadataWithNotify(x-7, y-5, z-2, 5, 3); //that chest that never points right
					//ChromatiCraft.logger.log("Successful generation of "+s.name()+" at "+x+","+y+","+z);
					world.setBlock(x-5, y-8, z-2, ChromaTiles.STRUCTCONTROL.getBlock(), ChromaTiles.STRUCTCONTROL.getBlockMetadata(), 3);
					TileEntityStructControl te = (TileEntityStructControl)world.getTileEntity(x-5, y-8, z-2);
					te.generate(s, e);
					this.generateStructure(s, te);
					this.populateChests(s, arr, r);
					return true;
				}
				return false;
			}
			case OCEAN: {
				int d = 3;
				int y = world.getTopSolidOrLiquidBlock(x, z)-d;
				Block b = world.getBlock(x, y+d, z);
				int tries = 0;
				while (b != Blocks.water && b != Blocks.flowing_water && tries < 10) {
					x = cx + r.nextInt(16);
					z = cz + r.nextInt(16);
					b = world.getBlock(x, y+d, z);
					tries++;
				}
				if (b == Blocks.water || b == Blocks.flowing_water) {
					//ReikaJavaLibrary.pConsole("Attempting gen @ "+x+", "+y+", "+z);
					//while (b == Blocks.water || b == Blocks.flowing_water && y > 0) {
					//	y--;
					//	b = world.getBlock(x, y, z);
					//}
					FilledBlockArray struct = ChromaStructures.OCEAN.getArray(world, x, y, z);
					if (y > 0 && this.isValidOceanLocation(world, x, y, z, struct)) {
						struct.place(2);
						world.setBlock(x, y, z, ChromaTiles.STRUCTCONTROL.getBlock(), ChromaTiles.STRUCTCONTROL.getBlockMetadata(), 3);
						TileEntityStructControl te = (TileEntityStructControl)world.getTileEntity(x, y, z);
						te.generate(s, CrystalElement.WHITE);
						this.generateStructure(s, te);
						this.populateChests(s, struct, r);
						this.programSpawners(s, struct);
						this.mossify(s, struct, r);
						this.generatePit(world, x, y, z);
						return true;
					}
				}
				return false;
			}
			case DESERT: {
				int y = world.getTopSolidOrLiquidBlock(x, z);
				if (world.getBlock(x, y-1, z) != Blocks.sand)
					return false;

				y -= 8;
				Block b = world.getBlock(x, y, z);
				BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
				if (this.isValidBiomeForDesertStruct(biome)) {

					x -= 7;
					y -= 3;
					z -= 7;

					FilledBlockArray struct = ChromaStructures.DESERT.getArray(world, x, y, z);
					DesertStructure.getTerrain(struct, x, y, z);
					if (this.isValidDesertLocation(world, x, y, z, struct)) {
						struct.place(2);

						world.setBlock(x+7, y+3, z+7, ChromaTiles.STRUCTCONTROL.getBlock(), ChromaTiles.STRUCTCONTROL.getBlockMetadata(), 3);
						TileEntityStructControl te = (TileEntityStructControl)world.getTileEntity(x+7, y+3, z+7);
						te.generate(s, CrystalElement.WHITE);
						this.generateStructure(s, te);
						this.populateChests(s, struct, r);
						this.programSpawners(s, struct);
						for (int k = 0; k < struct.getSize(); k++) {
							Coordinate c = struct.getNthBlock(k);
							if (c.yCoord > struct.getMaxY()-2) {
								Block b1 = c.offset(0, 1, 0).getBlock(world);
								Block b2 = c.offset(0, -1, 0).getBlock(world);
								if (b1 == Blocks.air && b2 == Blocks.sand && ReikaRandomHelper.doWithChance(20+30D*Math.abs(Math.abs(c.xCoord-struct.getMidX())+Math.abs(c.zCoord-struct.getMidZ()))/7D)) {
									c.setBlock(world, Blocks.air);
								}
							}
						}
						for (int k1 = struct.getMinX(); k1 <= struct.getMaxX(); k1++) {
							for (int k2 = struct.getMinZ(); k2 <= struct.getMaxZ(); k2++) {
								Coordinate c = new Coordinate(k1, world.getTopSolidOrLiquidBlock(k1, k2), k2);
								if (c.getBlock(world) == Blocks.air && c.offset(0, -1, 0).getBlock(world) == Blocks.sand && ReikaRandomHelper.doWithChance(2)) {
									if (c.offset(1, 0, 0).getBlock(world) == Blocks.air && c.offset(-1, 0, 0).getBlock(world) == Blocks.air) {
										if (c.offset(0, 0, 1).getBlock(world) == Blocks.air && c.offset(0, 0, -1).getBlock(world) == Blocks.air) {
											c.setBlock(world, Blocks.cactus);
											if (ReikaRandomHelper.doWithChance(40)) {
												c.offset(0, 1, 0).setBlock(world, Blocks.cactus);
												if (ReikaRandomHelper.doWithChance(40))
													c.offset(0, 2, 0).setBlock(world, Blocks.cactus);
											}
										}
									}
								}
							}
						}
						//too dry for moss//this.mossify(s, struct, r);
						return true;
					}
				}
				return false;
			}
			case SNOWSTRUCT: {
				int y = world.getTopSolidOrLiquidBlock(x, z)-1;
				FilledBlockArray arr = ChromaStructures.SNOWSTRUCT.getArray(world, x, y, z, r);
				if (this.isValidSnowStructLocation(world, x, y, z, arr)) {
					arr.offset(0, -6, 0);
					arr.place(2);
					this.mossify(s, arr, r);
					this.convertDirtToGrass(arr);
					this.programSpawners(s, arr);
					this.removeAdjacentTrees(arr);
					this.cleanupSnowEntrances(arr);
					this.addMissingSupport(arr, Blocks.stone, 0, 5);
					this.addSnowCover(arr, 4);
					world.setBlock(x+8, y-3, z+6, ChromaTiles.STRUCTCONTROL.getBlock(), ChromaTiles.STRUCTCONTROL.getBlockMetadata(), 3);
					TileEntityStructControl te = (TileEntityStructControl)world.getTileEntity(x+8, y-3, z+6);
					te.generate(s, CrystalElement.WHITE);
					this.generateStructure(s, te);
					this.populateChests(s, arr, r);
					return true;
				}
				return false;
			}
			default:
				return false;
		}
	}

	private boolean isValidBiomeForDesertStruct(BiomeGenBase biome) {
		return BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.SANDY) && !BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.MESA);
	}

	private boolean isValidDesertLocation(World world, int x, int y, int z, FilledBlockArray struct) {
		if (!this.isValidBiomeForDesertStruct(world.getBiomeGenForCoords(struct.getMinX(), struct.getMinZ())))
			return false;
		if (!this.isValidBiomeForDesertStruct(world.getBiomeGenForCoords(struct.getMaxX(), struct.getMinZ())))
			return false;
		if (!this.isValidBiomeForDesertStruct(world.getBiomeGenForCoords(struct.getMinX(), struct.getMaxZ())))
			return false;
		if (!this.isValidBiomeForDesertStruct(world.getBiomeGenForCoords(struct.getMaxX(), struct.getMaxZ())))
			return false;
		if (world.getBlock(struct.getMinX(), struct.getMinY()+3, struct.getMinZ()) == Blocks.air)
			return false;
		if (world.getBlock(struct.getMaxX(), struct.getMinY()+3, struct.getMinZ()) == Blocks.air)
			return false;
		if (world.getBlock(struct.getMinX(), struct.getMinY()+3, struct.getMaxZ()) == Blocks.air)
			return false;
		if (world.getBlock(struct.getMaxX(), struct.getMinY()+3, struct.getMaxZ()) == Blocks.air)
			return false;
		return true;
	}

	private static FilledBlockArray getPitSlice(World world, int x, int y, int z) {
		FilledBlockArray array = new FilledBlockArray(world);
		Block b = ChromaBlocks.STRUCTSHIELD.getBlockInstance();

		x -= 3;
		z -= 3;

		array.setBlock(x+1, y+0, z+1, b, 8);
		array.setBlock(x+1, y+0, z+2, b, 8);
		array.setBlock(x+1, y+0, z+3, b, 8);
		array.setBlock(x+1, y+0, z+4, b, 8);
		array.setBlock(x+1, y+0, z+5, b, 8);
		array.setBlock(x+2, y+0, z+1, b, 8);
		array.setBlock(x+2, y+0, z+5, b, 8);
		array.setBlock(x+3, y+0, z+1, b, 8);
		array.setBlock(x+3, y+0, z+5, b, 8);
		array.setBlock(x+5, y+0, z+1, b, 8);
		array.setBlock(x+5, y+0, z+2, b, 8);
		array.setBlock(x+5, y+0, z+3, b, 8);
		array.setBlock(x+5, y+0, z+4, b, 8);
		array.setBlock(x+5, y+0, z+5, b, 8);
		array.setBlock(x+4, y+0, z+1, b, 8);
		array.setBlock(x+4, y+0, z+5, b, 8);

		array.setBlock(x+2, y+0, z+2, Blocks.air);
		array.setBlock(x+2, y+0, z+3, Blocks.air);
		array.setBlock(x+2, y+0, z+4, Blocks.air);
		array.setBlock(x+3, y+0, z+2, Blocks.air);
		array.setBlock(x+3, y+0, z+3, Blocks.air);
		array.setBlock(x+3, y+0, z+4, Blocks.air);
		array.setBlock(x+4, y+0, z+2, Blocks.air);
		array.setBlock(x+4, y+0, z+3, Blocks.air);
		array.setBlock(x+4, y+0, z+4, Blocks.air);

		return array;
	}

	private static FilledBlockArray getEndcap1(World world, int x, int y, int z) {
		FilledBlockArray array = new FilledBlockArray(world);
		Block b = ChromaBlocks.STRUCTSHIELD.getBlockInstance();

		x -= 3;
		z -= 3;
		array.setBlock(x+30, y+8, z+2, Blocks.air);
		array.setBlock(x+30, y+8, z+3, Blocks.air);
		array.setBlock(x+30, y+8, z+4, Blocks.air);
		array.setBlock(x+30, y+9, z+2, Blocks.air);
		array.setBlock(x+30, y+9, z+3, Blocks.air);
		array.setBlock(x+30, y+9, z+4, Blocks.air);
		array.setBlock(x+30, y+10, z+2, Blocks.air);
		array.setBlock(x+30, y+10, z+3, Blocks.air);
		array.setBlock(x+30, y+10, z+4, Blocks.air);

		return array;
	}

	private static FilledBlockArray getEndcap2(World world, int x, int y, int z) {
		FilledBlockArray array = new FilledBlockArray(world);
		Block b = ChromaBlocks.STRUCTSHIELD.getBlockInstance();

		x -= 3;
		z -= 3;

		array.setBlock(x+2, y+8, z+30, Blocks.air);
		array.setBlock(x+2, y+9, z+30, Blocks.air);
		array.setBlock(x+2, y+10, z+30, Blocks.air);
		array.setBlock(x+3, y+8, z+30, Blocks.air);
		array.setBlock(x+3, y+9, z+30, Blocks.air);
		array.setBlock(x+3, y+10, z+30, Blocks.air);
		array.setBlock(x+4, y+8, z+30, Blocks.air);
		array.setBlock(x+4, y+9, z+30, Blocks.air);
		array.setBlock(x+4, y+10, z+30, Blocks.air);

		return array;
	}

	private void generatePit(World world, int x, int y, int z) {
		for (int i = 3; i < 32; i++) {
			FilledBlockArray arr = this.getPitSlice(world, x, y-i, z);
			boolean flag = true;
			for (int k = 0; k < arr.getSize(); k++) {
				Coordinate c = arr.getNthBlock(k);
				Block b = c.getBlock(world);
				if (b != Blocks.air) {
					flag = false;
				}
			}
			if (flag && i > 6) {
				break;
			}
			else {
				arr.place();
			}
		}

		BlockArray arr = OceanStructure.getPitCover(x, y, z);
		for (int k = 0; k < arr.getSize(); k++) {
			Coordinate c = arr.getNthBlock(k);
			Block b = c.getBlock(world);
			c.setBlock(world, ChromaBlocks.STRUCTSHIELD.getBlockInstance(), BlockType.CLOAK.metadata);
		}
	}

	private void mossify(ChromaStructures s, FilledBlockArray arr, Random r) {
		Block b2 = ChromaBlocks.STRUCTSHIELD.getBlockInstance();
		for (int k = 0; k < arr.getSize(); k++) {
			Coordinate c = arr.getNthBlock(k);
			Block b = c.getBlock(arr.world);
			if (b == b2) {
				int meta = c.getBlockMetadata(arr.world);
				if (meta == BlockType.STONE.metadata) {
					int dy = c.yCoord-arr.getMinY();
					int ct = Math.max(1, dy*2-2);
					if (r.nextInt(ct) == 0) {
						arr.world.setBlockMetadataWithNotify(c.xCoord, c.yCoord, c.zCoord, BlockType.MOSS.metadata, 2);
					}
				}
			}
		}
	}

	private void programSpawners(ChromaStructures s, FilledBlockArray arr) {
		switch(s) {
			case OCEAN:
				for (int k = 0; k < arr.getSize(); k++) {
					Coordinate c = arr.getNthBlock(k);
					Block b = c.getBlock(arr.world);
					if (b == Blocks.mob_spawner) {
						TileEntityMobSpawner te = (TileEntityMobSpawner)arr.world.getTileEntity(c.xCoord, c.yCoord, c.zCoord);
						te.func_145881_a().activatingRangeFromPlayer = 8;
						te.func_145881_a().maxNearbyEntities = 16;
						te.func_145881_a().maxSpawnDelay = 400;
						ReikaSpawnerHelper.setMobSpawnerMob(te, (String)EntityList.classToStringMapping.get(EntityCreeper.class));
					}
				}
				break;
			case DESERT:
				for (int k = 0; k < arr.getSize(); k++) {
					Coordinate c = arr.getNthBlock(k);
					Block b = c.getBlock(arr.world);
					if (b == Blocks.mob_spawner) {
						TileEntityMobSpawner te = (TileEntityMobSpawner)arr.world.getTileEntity(c.xCoord, c.yCoord, c.zCoord);
						Class mob = c.yCoord <= arr.getMinY()+4 ? EntityBlaze.class : Math.abs(c.xCoord-arr.getMinX()) == Math.abs(c.zCoord-arr.getMinZ()) ? EntitySpider.class : EntitySilverfish.class;
						ReikaSpawnerHelper.setMobSpawnerMob(te, (String)EntityList.classToStringMapping.get(mob));
						te.func_145881_a().activatingRangeFromPlayer = 4;
						te.func_145881_a().spawnDelay = 0;
						if (mob == EntityBlaze.class) {
							te.func_145881_a().maxSpawnDelay = 100;
							te.func_145881_a().minSpawnDelay = 40;
						}
					}
				}
				break;
			case SNOWSTRUCT:
				for (int k = 0; k < arr.getSize(); k++) {
					Coordinate c = arr.getNthBlock(k);
					Block b = c.getBlock(arr.world);
					if (b == Blocks.mob_spawner) {
						TileEntityMobSpawner te = (TileEntityMobSpawner)arr.world.getTileEntity(c.xCoord, c.yCoord, c.zCoord);
						ReikaSpawnerHelper.setMobSpawnerMob(te, (String)EntityList.classToStringMapping.get(EntityWolf.class));
						te.func_145881_a().activatingRangeFromPlayer = 30;
						te.func_145881_a().spawnDelay = 0;
						te.func_145881_a().maxNearbyEntities = 8;
					}
				}
				break;
			default:
				break;
		}
	}

	public static void populateChests(ChromaStructures struct, FilledBlockArray arr, Random r) {
		for (int k = 0; k < arr.getSize(); k++) {
			Coordinate c = arr.getNthBlock(k);
			Block b = c.getBlock(arr.world);
			if (b == ChromaStructureBase.getChestGen()) {
				TileEntityLootChest te = (TileEntityLootChest)c.getTileEntity(arr.world);
				if (te.isUntouchedWorldgen()) {
					int bonus = 0;
					if (struct == ChromaStructures.OCEAN && c.yCoord-arr.getMinY() == 4)
						bonus = 4;
					if (struct == ChromaStructures.DESERT && c.yCoord-arr.getMinY() < 4)
						bonus = 2;
					if (struct == ChromaStructures.SNOWSTRUCT)
						bonus = c.yCoord-arr.getMinY() < 4 ? 2 : 1;
					populateChest(te, struct, bonus, r);
				}
			}
		}
	}

	public static void populateChest(TileEntityLootChest te, ChromaStructures struct, int bonus, Random r) {
		String s = null;
		switch (struct) {
			case CAVERN:
				s = ChestGenHooks.DUNGEON_CHEST;
				break;
			case BURROW:
				s = ChestGenHooks.BONUS_CHEST;
				break;
			case OCEAN:
				s = ChestGenHooks.PYRAMID_JUNGLE_CHEST;
				break;
			case DESERT:
				s = ChestGenHooks.PYRAMID_DESERT_CHEST;
				break;
			case SNOWSTRUCT:
				s = ChestGenHooks.STRONGHOLD_CORRIDOR;
				break;
			default:
				break;
		}
		if (s == null)
			return;
		te.populateChest(s, struct, bonus, r);
	}

	private void convertDirtToGrass(FilledBlockArray arr) {
		for (int k = 0; k < arr.getSize(); k++) {
			Coordinate c = arr.getNthBlock(k);
			Block b = c.getBlock(arr.world);
			if (b == Blocks.dirt) {
				if (arr.world.getBlockLightValue(c.xCoord, c.yCoord+1, c.zCoord) > 8) {
					c.setBlock(arr.world, Blocks.grass);
				}
			}
		}
	}

	private void addMissingSupport(FilledBlockArray arr, Block b, int meta, int d) {
		for (int x = arr.getMinX(); x <= arr.getMaxX(); x++) {
			for (int z = arr.getMinZ(); z <= arr.getMaxZ(); z++) {
				int bottom = arr.getBottomBlockAtXZ(x, z);
				if (bottom != Integer.MAX_VALUE) {
					bottom--;
					for (int i = 1; i <= d; i++) {
						int y = bottom-i;
						if (ReikaWorldHelper.softBlocks(arr.world, x, y, z))
							arr.world.setBlock(x, y, z, b, meta, 2);
					}
				}
			}
		}
	}

	private void addSnowCover(FilledBlockArray arr, int r) {
		/*
		for (int k = 0; k < arr.getSize(); k++) {
			Coordinate c = arr.getNthBlock(k);
			if (c.isEmpty(arr.world) && Blocks.snow_layer.canPlaceBlockAt(arr.world, c.xCoord, c.yCoord, c.zCoord)) {
				if (arr.world.getPrecipitationHeight(c.xCoord, c.zCoord) <= c.yCoord)
					c.setBlock(arr.world, Blocks.snow_layer);
			}
		}
		 */
		for (int x = arr.getMinX()-r; x <= arr.getMaxX()+r; x++) {
			for (int z = arr.getMinZ()-r; z <= arr.getMaxZ()+r; z++) {
				int top = arr.world.getTopSolidOrLiquidBlock(x, z)-1;
				if (arr.world.getBlock(x, top, z) != Blocks.snow_layer && arr.world.getBlock(x, top+1, z) == Blocks.air) {
					arr.world.setBlock(x, top+1, z, Blocks.snow_layer);
				}
			}
		}
	}

	private void removeAdjacentTrees(FilledBlockArray arr) {
		for (int k = 0; k < arr.getSize(); k++) {
			Coordinate c = arr.getNthBlock(k);
			for (int i = 0; i < 6; i++) {
				Coordinate c2 = c.offset(ForgeDirection.VALID_DIRECTIONS[i], 1);
				Block b = c2.getBlock(arr.world);
				if (b != Blocks.air && b != ChromaBlocks.STRUCTSHIELD.getBlockInstance()) {
					int meta = c2.getBlockMetadata(arr.world);
					TreeType tree = ReikaTreeHelper.getTree(b, meta);
					if (tree == null)
						tree = ModWoodList.getModWood(b, meta);
					if (tree != null) {
						BlockArray barr = new BlockArray();
						barr.recursiveMultiAddWithBounds(arr.world, c2.xCoord, c2.yCoord, c2.zCoord, c2.xCoord-12, c2.yCoord-12, c2.zCoord-12, c2.xCoord+12, c2.yCoord+12, c2.zCoord+12, b, tree.getLeafID());
						for (Coordinate c3 : barr.keySet()) {
							c3.setBlock(arr.world, Blocks.air);
						}
					}
				}
			}
		}
	}

	private void cleanupSnowEntrances(FilledBlockArray arr) {
		for (int k = 0; k < arr.getSize(); k++) {
			Coordinate c = arr.getNthBlock(k);
			Block b = c.getBlock(arr.world);
			if (b == ChromaBlocks.STRUCTSHIELD.getBlockInstance() && c.getBlockMetadata(arr.world) == BlockType.MOSS.metadata) {
				Block b3 = c.offset(0, 1, 0).getBlock(arr.world);
				if (b3 != b) {
					for (int i = 1; i < 5; i++) {
						for (int dx = -i; dx <= i; dx++) {
							for (int dz = -i; dz <= i; dz++) {
								Coordinate c2 = c.offset(dx, i, dz);
								Block b2 = c2.getBlock(arr.world);
								if (b2 == Blocks.grass || b2.getMaterial() == Material.ground || b2.getMaterial() == Material.plants || b2 == Blocks.stone || b2 == Blocks.snow_layer) {
									c2.setBlock(arr.world, Blocks.air);
								}
							}
						}
					}
				}
			}
		}
	}

	private boolean isValidCavernLocation(World world, int x, int y, int z, FilledBlockArray arr) {
		boolean flag = false;
		for (int i = 6; i < 18; i++) {
			int dx = x+i;
			Block b = world.getBlock(dx, y, z);
			Block b2 = world.getBlock(dx, y-1, z);
			if (b.isAir(world, dx, y, z) && b2.isAir(world, dx, y-1, z)) {
				flag = true;
				break;
			}
		}
		if (flag) {
			for (int k = 0; k < arr.getSize(); k++) {
				Coordinate c = arr.getNthBlock(k);
				Coordinate c2 = c.offset(0, 1, 0);
				Block b = c.getBlock(world);
				Block b2 = c2.getBlock(world);
				if (b.isAir(world, c.xCoord, c.yCoord, c.zCoord) || ReikaBlockHelper.isLiquid(b))
					return false;
				if (b.isAir(world, c2.xCoord, c2.yCoord, c2.zCoord) || ReikaBlockHelper.isLiquid(b2))
					return false;
			}
			return true;
		}
		return false;
	}

	private boolean isValidBurrowLocation(World world, int x, int y, int z, FilledBlockArray arr) {

		if (world.getBlock(x, y, z) != Blocks.grass)
			return false;

		//Surface visibility
		for (int i = 1; i <= 8; i++) {
			for (int k = -3; k <= 1; k++) {
				Block b = world.getBlock(x, y+i, z+k);
				if (!ReikaWorldHelper.softBlocks(world, x, y+i, z+k)) {
					return false;
				}
			}
		}

		//No lakes
		int r = 1;
		for (int i = -r; i <= r; i++) {
			for (int j = -r; j <= r; j++) {
				for (int k = -r; k <= r; k++) {
					Block b = world.getBlock(x+i, y+j, z+k);
					if (b instanceof BlockLiquid || b instanceof BlockFluidBase) {
						return false;
					}
				}
			}
		}

		//No air exposure
		for (int k = 0; k < arr.getSize(); k++) {
			Coordinate c = arr.getNthBlock(k);
			int dx = c.xCoord;
			int dy = c.yCoord;
			int dz = c.zCoord;
			Block b = world.getBlock(dx, dy, dz);
			if (b == ChromaBlocks.CLIFFSTONE.getBlockInstance())
				return false;
			if (world.getTopSolidOrLiquidBlock(dx, dz) < y-2)
				return false;
			if (arr.hasBlockAt(dx, dy, dz, Blocks.stone) || arr.hasBlockAt(dx, dy, dz, ChromaBlocks.STRUCTSHIELD.getBlockInstance())) {
				if (b.isAir(world, dx, dy, dz) || ReikaWorldHelper.checkForAdjMaterial(world, dx, dy, dz, Material.air) != null) {
					return false;
				}
			}
		}
		return true;
	}

	private boolean isValidOceanLocation(World world, int x, int y, int z, FilledBlockArray struct) {
		//needs to be at least 8 blocks deep
		if (world.getBlock(x, y+8, z) != Blocks.water && world.getBlock(x, y+8, z) != Blocks.flowing_water) {
			ChromatiCraft.logger.debug("Ocean Temple generation @ "+x+", "+y+", "+z+" failed: Not deep enough");
			return false;
		}

		//at least one end open
		boolean flag1 = true;
		boolean flag2 = true;
		FilledBlockArray cap = (FilledBlockArray)this.getEndcap1(world, x, y, z).offset(1, 0, 0);
		for (int k = 0; k < cap.getSize(); k++) {
			Coordinate c = cap.getNthBlock(k);
			Block b = c.getBlock(world);
			if (b != Blocks.water && b != Blocks.flowing_water) {
				flag1 = false;
			}
		}
		cap = (FilledBlockArray)this.getEndcap2(world, x, y, z).offset(0, 0, 1);
		for (int k = 0; k < cap.getSize(); k++) {
			Coordinate c = cap.getNthBlock(k);
			Block b = c.getBlock(world);
			if (b != Blocks.water && b != Blocks.flowing_water) {
				flag2 = false;
			}
		}
		if (!flag1 && !flag2) {
			ChromatiCraft.logger.debug("Ocean Temple generation @ "+x+", "+y+", "+z+" failed: Blocked ends");
			return false;
		}

		//bury lower half, and ensure not near shore or intersecting another
		for (int k = 0; k < struct.getSize(); k++) {
			Coordinate c = struct.getNthBlock(k);
			Block b = c.getBlock(world);
			if (b == ChromaBlocks.STRUCTSHIELD.getBlockInstance()) {
				ChromatiCraft.logger.debug("Ocean Temple generation @ "+x+", "+y+", "+z+" failed: Intersects other structure");
				return false;
			}
			if (world.getTopSolidOrLiquidBlock(c.xCoord, c.zCoord) <= y) {
				ChromatiCraft.logger.debug("Ocean Temple generation @ "+x+", "+y+", "+z+" failed: Extends out of water");
				return false;
			}
			if (!ReikaBiomeHelper.isOcean(world.getBiomeGenForCoords(c.xCoord, c.zCoord))) {
				ChromatiCraft.logger.debug("Ocean Temple generation @ "+x+", "+y+", "+z+" failed: Bounds outside ocean");
				return false;
			}
		}

		//can generate pit to cave
		int consec = 0;
		for (int i = 3; i < y; i++) {
			FilledBlockArray slice = this.getPitSlice(world, x, y-i, z);
			boolean flag = true;
			for (int k = 0; k < slice.getSize(); k++) {
				Coordinate c = slice.getNthBlock(k);
				Block b = c.getBlock(world);
				if (b != Blocks.air) {
					flag = false;
				}
			}
			if (flag && i > 6) {
				consec++;
				if (consec >= 3)
					return true;
			}
			else {
				consec = 0;
			}
		}
		ChromatiCraft.logger.debug("Ocean Temple generation @ "+x+", "+y+", "+z+" failed: No cave.");
		return false;
	}

	private boolean isValidSnowStructLocation(World world, int x, int y, int z, FilledBlockArray arr) {
		int h1 = world.getTopSolidOrLiquidBlock(arr.getMinX(), arr.getMinZ());
		int h2 = world.getTopSolidOrLiquidBlock(arr.getMaxX(), arr.getMinZ());
		int h3 = world.getTopSolidOrLiquidBlock(arr.getMinX(), arr.getMaxZ());
		int h4 = world.getTopSolidOrLiquidBlock(arr.getMaxX(), arr.getMaxZ());
		int max = ReikaMathLibrary.multiMax(h1, h2, h3, h4);
		int min = ReikaMathLibrary.multiMin(h1, h2, h3, h4);
		if (Math.abs(max-min) > 2)
			return false;
		BiomeGenBase b1 = world.getBiomeGenForCoords(arr.getMinX(), arr.getMinZ());
		BiomeGenBase b2 = world.getBiomeGenForCoords(arr.getMaxX(), arr.getMinZ());
		BiomeGenBase b3 = world.getBiomeGenForCoords(arr.getMinX(), arr.getMaxZ());
		BiomeGenBase b4 = world.getBiomeGenForCoords(arr.getMaxX(), arr.getMaxZ());
		if (b1 != b2 || b1 != b3 || b1 != b4)
			return false;
		for (int d = 1; d <= 5; d++) { //starts at the top non-air
			Block id1 = world.getBlock(arr.getMinX(), h1-d, arr.getMinZ());
			Block id2 = world.getBlock(arr.getMaxX(), h2-d, arr.getMinZ());
			Block id3 = world.getBlock(arr.getMinX(), h3-d, arr.getMaxZ());
			Block id4 = world.getBlock(arr.getMaxX(), h4-d, arr.getMaxZ());
			if (id1 == Blocks.air || id2 == Blocks.air || id3 == Blocks.air || id4 == Blocks.air)
				return false;
			if (ReikaBlockHelper.isLiquid(id1) || ReikaBlockHelper.isLiquid(id2) || ReikaBlockHelper.isLiquid(id3) || ReikaBlockHelper.isLiquid(id4))
				return false;
		}
		return true;
	}

	private boolean isVoidWorld(World world, int x, int z) {
		return world.getBlock(x, 0, z) == Blocks.air || world.canBlockSeeTheSky(x, 1, z);
	}

	private boolean isGennableChunk(World world, int x, int z, Random r, ChromaStructures s) {
		if (this.isVoidWorld(world, x, z))
			return false;
		int mind = this.getMinSeparation(s);
		Stack<StructureGeneration> li = currentGenerationQueue.get(s);
		for (StructureGeneration sg : li) {
			if (sg.location.dimensionID == world.provider.dimensionId) {
				double dist = ReikaMathLibrary.py3d(sg.location.chunk.chunkXPos-x, 0, sg.location.chunk.chunkZPos-z);
				if (dist < mind) {
					StructureGeneration del = new StructureGeneration(s, world, x, z);
					del.isDelayed = true;
					sg.delayed.add(del);
					//ChromatiCraft.logger.log("Delayed a "+del+" because it was triggered by a too-close "+sg);
					return false;
				}
			}
		}
		if (this.isStructureWithin(s, world, x, 48, z, mind)) {
			return false;
		}
		BiomeGenBase b = world.getBiomeGenForCoords(x, z);
		if (!this.isValidBiome(s, b))
			return false;
		switch(s) {
			case OCEAN:
				return r.nextInt(/*32*/6) == 0;
			case CAVERN:
				return r.nextInt(/*48*/5) == 0;
			case BURROW:
				return r.nextInt(/*64*/8) == 0;
			case DESERT:
				return r.nextInt(/*120*/10) == 0;
			case SNOWSTRUCT:
				return r.nextInt(/*120*/3) == 0;
			default:
				return false;
		}
	}

	private boolean isValidBiome(ChromaStructures s, BiomeGenBase b) {
		switch(s) {
			case OCEAN:
				return ReikaBiomeHelper.isOcean(b);
			case CAVERN:
				return true;
			case BURROW:
				return b.topBlock == Blocks.grass && !this.isValidBiome(ChromaStructures.SNOWSTRUCT, b);
			case DESERT:
				return b.topBlock == Blocks.sand;
			case SNOWSTRUCT:
				return b.topBlock == Blocks.grass && b.getEnableSnow() && ReikaBiomeHelper.getBiomeDecorator(b).treesPerChunk < 1 && !b.biomeName.toLowerCase(Locale.ENGLISH).contains("forest");
			default:
				return false;
		}
	}

	public int getMinSeparation(ChromaStructures s) {
		switch(s) {
			case DESERT:
				return 512;
			case OCEAN:
				return 1024;
			case CAVERN:
				return 128;
			case BURROW:
				return 192;
			case SNOWSTRUCT:
				return 768;
			default:
				return 0;
		}
	}

	private boolean canGenerateIn(World world) {
		if (ModList.MYSTCRAFT.isLoaded() && ReikaMystcraftHelper.isMystAge(world)) {
			if (!MystPages.Pages.STRUCTURES.existsInWorld(world)) {
				return false;
			}
		}
		if (world.getWorldInfo().getTerrainType() == WorldType.FLAT) {
			return ChromaOptions.FLATGEN.getState() && ReikaWorldHelper.getSuperflatHeight(world) > 15;
		}
		if (world.provider.dimensionId == 0)
			return true;
		if (world.provider.dimensionId == ExtraChromaIDs.DIMID.getValue())
			return false;
		if (Math.abs(world.provider.dimensionId) == 1)
			return false;
		if (world.provider.dimensionId == ExtraUtilsHandler.getInstance().darkID)
			return false;
		if (world.provider.dimensionId == TwilightForestHandler.getInstance().dimensionID)
			return false;
		if (world.provider.getClass().getName().equals("WorldProviderMiner"))
			return false;
		if (PlanetDimensionHandler.isOtherWorld(world))
			return false;
		return !ChromatiCraft.config.isDimensionBlacklistedForStructures(world.provider.dimensionId);
	}

	@Override
	public boolean canGenerateAt(World world, int chunkX, int chunkZ) {
		return true;
	}

	@Override
	public String getIDString() {
		return "ChromatiCraft Prefab Structures";
	}

	private static class StructureFile extends NBTFile {

		private final HashSet<WorldLocation> entries = new HashSet();

		private StructureFile(File f) {
			super(f);
			//encryptData = true;
		}

		@Override
		protected void readHeader(NBTTagCompound header) {

		}

		@Override
		protected void readData(NBTTagList li) {
			for (Object o : li.tagList) {
				NBTTagCompound tag = (NBTTagCompound)o;
				WorldLocation loc = WorldLocation.readFromNBT(tag);
				if (loc != null)
					entries.add(loc);
			}
		}

		@Override
		protected void readExtraData(NBTTagCompound extra) {

		}

		@Override
		protected void writeHeader(NBTTagCompound header) {

		}

		@Override
		protected void writeData(NBTTagList li) {
			for (WorldLocation loc : entries) {
				NBTTagCompound tag = loc.writeToTag();
				li.appendTag(tag);
			}
		}

		@Override
		protected NBTTagCompound writeExtraData() {
			return null;
		}

	}

	private static class StructureGeneration {

		private final ChromaStructures type;
		/** In block coords */
		private final WorldChunk location;

		private final ArrayList<StructureGeneration> delayed = new ArrayList();
		private boolean isDelayed;

		private StructureGeneration(ChromaStructures s, World world, int chunkX, int chunkZ) {
			type = s;
			location = new WorldChunk(world, chunkX, chunkZ);
		}

		@Override
		public String toString() {
			return type.getDisplayName()+" @ "+location.toString();
		}

	}

}
