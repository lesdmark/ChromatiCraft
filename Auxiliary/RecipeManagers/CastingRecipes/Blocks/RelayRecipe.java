/*******************************************************************************
 * @author Reika Kalseki
 * 
 * Copyright 2015
 * 
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.ChromatiCraft.Auxiliary.RecipeManagers.CastingRecipes.Blocks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import Reika.ChromatiCraft.Auxiliary.Interfaces.EnergyLinkingRecipe;
import Reika.ChromatiCraft.Auxiliary.RecipeManagers.CastingRecipe.MultiBlockCastingRecipe;
import Reika.ChromatiCraft.Auxiliary.RecipeManagers.RecipesCastingTable;
import Reika.ChromatiCraft.Registry.ChromaBlocks;
import Reika.ChromatiCraft.Registry.CrystalElement;
import Reika.DragonAPI.Instantiable.Data.Immutable.Coordinate;

public class RelayRecipe extends MultiBlockCastingRecipe implements EnergyLinkingRecipe {

	public RelayRecipe(CrystalElement e) {
		super(ChromaBlocks.RELAY.getStackOfMetadata(e.ordinal()), ChromaBlocks.PYLONSTRUCT.getStackOfMetadata(0));

		this.addAuxItem(Items.glowstone_dust, 0, -4);
		this.addAuxItem(Items.glowstone_dust, -2, -2);
		this.addAuxItem(Items.glowstone_dust, 2, -2);
		this.addAuxItem(this.getChargedShard(e), 0, -2);
		this.addAuxItem(ChromaBlocks.PYLONSTRUCT.getStackOfMetadata(0), 0, 2);

		Coordinate c = runeRing.getNthBlock(e.ordinal());
		this.addRune(e, c.xCoord, c.yCoord, c.zCoord);
	}

	@Override
	public boolean canRunRecipe(EntityPlayer ep) {
		return super.canRunRecipe(ep) && RecipesCastingTable.playerHasCrafted(ep, RecipeType.PYLON);
	}

	@Override
	public int getNumberProduced() {
		return 4;
	}

}
