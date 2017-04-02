package Reika.ChromatiCraft.TileEntity.Networking;

import net.minecraft.nbt.NBTTagCompound;

import org.apache.commons.lang3.tuple.ImmutableTriple;

import Reika.ChromatiCraft.Base.TileEntity.CrystalTransmitterBase;
import Reika.ChromatiCraft.Magic.Interfaces.CrystalReceiver;
import Reika.ChromatiCraft.Magic.Interfaces.CrystalRepeater;
import Reika.ChromatiCraft.Magic.Interfaces.CrystalSource;
import Reika.ChromatiCraft.Magic.Interfaces.CrystalTransmitter;
import Reika.ChromatiCraft.Magic.Interfaces.NaturalNetworkTile;
import Reika.ChromatiCraft.Registry.ChromaTiles;
import Reika.ChromatiCraft.Registry.CrystalElement;


public class TileEntitySkypeater extends CrystalTransmitterBase implements CrystalRepeater, NaturalNetworkTile {

	private NodeClass type;

	@Override
	public int receiveElement(CrystalElement e, int amt) {
		return 1;
	}

	@Override
	public int getReceiveRange() {
		return 64;
	}

	@Override
	public boolean canReceiveFrom(CrystalTransmitter r) {
		return r instanceof CrystalRepeater;
	}

	@Override
	public boolean needsLineOfSightFromTransmitter(CrystalTransmitter r) {
		return true;//!(r instanceof TileEntityAirRepeater);
	}

	@Override
	public boolean isConductingElement(CrystalElement e) {
		return true;
	}

	@Override
	public int maxThroughput() {
		return Integer.MAX_VALUE;
	}

	@Override
	public boolean canConduct() {
		return true;
	}

	@Override
	public ImmutableTriple<Double, Double, Double> getTargetRenderOffset(CrystalElement e) {
		return null;
	}

	@Override
	public int getSendRange() {
		return 64;
	}

	@Override
	public boolean needsLineOfSightToReceiver(CrystalReceiver r) {
		return true;//!(r instanceof TileEntityAirRepeater);
	}

	@Override
	public boolean canTransmitTo(CrystalReceiver r) {
		return r instanceof CrystalRepeater;
	}

	@Override
	public int getPathPriority() {
		return 800000;
	}

	@Override
	public int getSignalDegradation() {
		return 0;
	}

	@Override
	public int getThoughputBonus() {
		return 0;
	}

	@Override
	public int getThoughputInsurance() {
		return 0;
	}

	@Override
	public int getSignalDepth(CrystalElement e) {
		return 0;
	}

	@Override
	public void setSignalDepth(CrystalElement e, int d) {

	}

	@Override
	public boolean checkConnectivity() {
		return false;
	}

	@Override
	public void onTransfer(CrystalSource src, CrystalReceiver r, CrystalElement element, int amt) {

	}

	@Override
	public ChromaTiles getTile() {
		return ChromaTiles.SKYPEATER;
	}

	public void setNodeType(NodeClass c) {
		type = c;
		this.syncAllData(false);
	}

	public NodeClass getNodeType() {
		return type != null ? type : NodeClass.WATER;
	}

	@Override
	public void writeToNBT(NBTTagCompound NBT) {
		super.writeToNBT(NBT);

		NBT.setInteger("type", this.getNodeType().ordinal());
	}

	@Override
	public void readFromNBT(NBTTagCompound NBT) {
		super.readFromNBT(NBT);

		type = NodeClass.list[NBT.getInteger("type")];
	}

	public static enum NodeClass {
		WATER(),
		SHORE(),
		//PLATEAU(),
		;

		private static final NodeClass[] list = values();

		public boolean isAbove(NodeClass c) {
			return c.ordinal() > this.ordinal();
		}
	}

}
