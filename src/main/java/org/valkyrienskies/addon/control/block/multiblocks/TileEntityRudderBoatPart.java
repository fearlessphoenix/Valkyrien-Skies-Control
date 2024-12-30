package org.valkyrienskies.addon.control.block.multiblocks;

import net.minecraft.block.BlockLiquid;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.joml.AxisAngle4d;
import org.joml.Math;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.addon.control.MultiblockRegistry;
import org.valkyrienskies.addon.control.config.VSControlConfig;
import org.valkyrienskies.mod.common.network.VSNetwork;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import valkyrienwarfare.api.TransformType;

import java.util.List;
import java.util.Optional;

public class TileEntityRudderBoatPart extends
    TileEntityMultiblockPartForce<RudderBoatAxleMultiblockSchematic, TileEntityRudderBoatPart> {

	static final double PI_OVER_180 = Math.PI/180d;
    // Angle must be between -90 and 90
    private double rudderAngle;
    // For client rendering purposes only
    private double prevRudderAngle;
    private double nextRudderAngle;
    
    private Vector3d localVelocity;
    private Vector3d facingAdjusted;
    private Vector3d normal;
    private Vector3d projectedVelocity;
    
    private double waterMultiplier;

    public TileEntityRudderBoatPart() {
    	this(VSControlConfig.rudderForceMultiplier, VSControlConfig.rudderFluidMultiplier);
    }
    
    public TileEntityRudderBoatPart(double thrustMultiplier, double waterMultiplier) {
        super();
        this.setMaxThrust(thrustMultiplier);
        this.waterMultiplier = waterMultiplier;
        this.rudderAngle = 0;
        this.prevRudderAngle = 0;
        this.nextRudderAngle = 0;
        this.localVelocity = new Vector3d();
        this.facingAdjusted = new Vector3d();
        this.normal = new Vector3d();
        this.projectedVelocity = new Vector3d();
    }

    @Override
    public void update() {
        super.update();
        this.prevRudderAngle = rudderAngle;
        if (this.getWorld().isRemote) {
            // Do this to smooth out lag between the server sending packets.
            this.rudderAngle = rudderAngle + .5 * (nextRudderAngle - rudderAngle);
        }
    }

    public Vector3d getForcePositionInShipSpace() {
        Vector3d facingOffset = getForcePosRelativeToAxleInShipSpace();
        if (facingOffset != null) {
            return new Vector3d(facingOffset.x + pos.getX() + .5, facingOffset.y + pos.getY() + .5,
                facingOffset.z + pos.getZ() + .5);
        } else {
            return null;
        }
    }

    private Vector3d getForcePosRelativeToAxleInShipSpace() {
        if (getRudderAxleSchematic().isPresent()) {
            Vec3i directionFacing = getRudderAxleFacingDirection().get().getDirectionVec();
            Vec3i directionAxle = this.getRudderAxleAxisDirection().get().getDirectionVec();
            Vector3d facingOffset = new Vector3d(directionFacing.getX(), directionFacing.getY(), directionFacing.getZ());
            double axleLength = getRudderAxleLength().get();
            // Then estimate the torque output for both, and use the one that has a positive
            // dot product to torqueAttemptNormal.
            facingOffset.mul(axleLength / 2D);
            // Then rotate the offset vector
            AxisAngle4d rotation = new AxisAngle4d(Math.toRadians(rudderAngle), directionAxle.getX(), directionAxle.getY(), directionAxle.getZ());

            rotation.transform(facingOffset);
            return facingOffset;
        } else {
            return null;
        }
    }

    public Vector3d calculateForceFromVelocity(PhysicsObject physicsObject) {
        if (getRudderAxleSchematic().isPresent()) {
            Vector3d directionFacing = this.getForcePosRelativeToAxleInShipSpace();
            Vector3d forcePosRelativeToShipCenter = this.getForcePositionInShipSpace();
            forcePosRelativeToShipCenter.sub(physicsObject.getShipTransform().getCenterCoord());
            physicsObject.getShipTransformationManager().getCurrentPhysicsTransform()
                .transformDirection(forcePosRelativeToShipCenter, TransformType.SUBSPACE_TO_GLOBAL);

            Vector3d velocity = physicsObject.getPhysicsCalculations()
                .getVelocityAtPoint(forcePosRelativeToShipCenter);
            physicsObject.getShipTransformationManager().getCurrentPhysicsTransform()
                .transformDirection(velocity, TransformType.GLOBAL_TO_SUBSPACE);
            // Now we have the velocity in local, the position in local, and the position relative to the axle
            Vec3i directionAxle = this.getRudderAxleAxisDirection().get().getDirectionVec();
            Vector3d directionAxleVector = new Vector3d(directionAxle.getX(), directionAxle.getY(), directionAxle.getZ());

            Vector3d surfaceNormal = directionAxleVector.cross(directionFacing, new Vector3d());
            surfaceNormal.normalize();

            double dragMagnitude = surfaceNormal.dot(velocity) * 10000;
            return surfaceNormal.mul(-dragMagnitude);
        } else {
            return null;
        }
    }

    public Vector3d calculateForceFromAngleUnadjusted(PhysicsObject physicsObject) {
        if (getRudderAxleSchematic().isPresent()) {
        	if (!this.isMaster()) {
        		TileEntityRudderBoatPart master = this.getMaster();
        		if (master != null) {
        			return master.calculateForceFromAngleUnadjusted(physicsObject);
        		} else {
        			return null;
        		}
        	}
            Vec3i directionFacing = getRudderAxleFacingDirection().get().getDirectionVec();
            Vec3i directionAxle = this.getRudderAxleAxisDirection().get().getDirectionVec();
            
            Vector3d localPos = this.getForcePositionInShipSpace();
            localPos.sub(physicsObject.getShipTransform().getCenterCoord());
            physicsObject.getShipTransformationManager().getCurrentPhysicsTransform()
        		.transformDirection(localPos, TransformType.SUBSPACE_TO_GLOBAL);
            this.localVelocity = physicsObject.getPhysicsCalculations().getVelocityAtPoint(localPos, this.localVelocity);
            physicsObject.getShipTransformationManager().getCurrentPhysicsTransform()
            	.transformDirection(this.localVelocity, TransformType.GLOBAL_TO_SUBSPACE);
            this.facingAdjusted.set(directionFacing.getX(), directionFacing.getY(), directionFacing.getZ());
            this.facingAdjusted.rotateAxis(this.getRudderAngle()*PI_OVER_180, directionAxle.getX(), directionAxle.getY(), directionAxle.getZ());

            // Cross product of facingAdjusted and directionAxle.
            this.normal.set(this.facingAdjusted.y()*directionAxle.getZ() - this.facingAdjusted.z()*directionAxle.getY(),
            		this.facingAdjusted.z()*directionAxle.getX() - this.facingAdjusted.x()*directionAxle.getZ(),
            		this.facingAdjusted.x()*directionAxle.getY() - this.facingAdjusted.y()*directionAxle.getX());
            
            this.normal.mul(this.localVelocity.dot(this.normal), this.projectedVelocity);

            return this.projectedVelocity.mul(-1);
        } else {
            return null;
        }
    }
    
    public Vector3d calculateForceFromAngle(PhysicsObject physicsObject) {
    	if (getRudderAxleSchematic().isPresent()) {
    		Vector3d unadjusted = this.calculateForceFromAngleUnadjusted(physicsObject);
    		BlockPos thisPos = this.getPos();
    		Vector3d globalPos = new Vector3d(thisPos.getX(), thisPos.getY(), thisPos.getZ());
    		physicsObject.getShipTransformationManager().getCurrentPhysicsTransform().transformPosition(globalPos, TransformType.SUBSPACE_TO_GLOBAL);
    		BlockPos pos = new BlockPos(globalPos.x, globalPos.y, globalPos.z);
    		boolean isInFluid = this.world.getBlockState(pos).getBlock() instanceof BlockLiquid;
    		Vector3d force = isInFluid ? unadjusted.mul(this.getMaxThrust() * this.waterMultiplier) : unadjusted.mul(this.getMaxThrust() / 2);
    		return force;
    	} else {
    		return null;
    	}
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        rudderAngle = compound.getDouble("rudderAngle");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagCompound toReturn = super.writeToNBT(compound);
        toReturn.setDouble("rudderAngle", rudderAngle);
        return toReturn;
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net,
        net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        double currentRudderAngle = rudderAngle;
        super.onDataPacket(net, pkt);
        nextRudderAngle = pkt.getNbtCompound().getDouble("rudderAngle");
        this.rudderAngle = currentRudderAngle;
    }

    @Override
    public Vector3dc getForceOutputUnoriented(double secondsToApply, PhysicsObject physicsObject) {
        return null;
    }

    @Override
    public Vector3dc getForceOutputNormal(double secondsToApply, PhysicsObject object) {
        return null;
    }

    @Override
    public double getThrustMagnitude(PhysicsObject physicsObject) {
//        return 0;
        return this.getMaxThrust() / 2;
    }

    public Optional<EnumFacing> getRudderAxleAxisDirection() {
        Optional<RudderBoatAxleMultiblockSchematic> rudderAxleSchematicOptional = getRudderAxleSchematic();
        if (rudderAxleSchematicOptional.isPresent()) {
            return Optional.of(rudderAxleSchematicOptional.get().getAxleAxisDirection());
        } else {
            return Optional.empty();
        }
    }

    public Optional<EnumFacing> getRudderAxleFacingDirection() {
        Optional<RudderBoatAxleMultiblockSchematic> rudderAxleSchematicOptional = getRudderAxleSchematic();
        if (rudderAxleSchematicOptional.isPresent()) {
            return Optional.of(rudderAxleSchematicOptional.get().getAxleFacingDirection());
        } else {
            return Optional.empty();
        }
    }

    public Optional<Integer> getRudderAxleLength() {
        Optional<RudderBoatAxleMultiblockSchematic> rudderAxleSchematicOptional = getRudderAxleSchematic();
        if (rudderAxleSchematicOptional.isPresent()) {
            return Optional.of(rudderAxleSchematicOptional.get().getAxleLength());
        } else {
            return Optional.empty();
        }
    }

    private Optional<RudderBoatAxleMultiblockSchematic> getRudderAxleSchematic() {
        if (this.isPartOfAssembledMultiblock()) {
            return Optional.of(getMultiBlockSchematic());
        } else {
            return Optional.empty();
        }
    }

    public double getRudderAngle() {
        return this.rudderAngle;
    }

    public void setRudderAngle(double forcedValue) {
        this.rudderAngle = forcedValue;
        VSNetwork.sendTileToAllNearby(this);
    }

    public double getRenderRudderAngle(double partialTicks) {
        return this.prevRudderAngle + ((this.rudderAngle - this.prevRudderAngle) * partialTicks);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        if (this.isPartOfAssembledMultiblock() && this.isMaster() && getRudderAxleAxisDirection()
            .isPresent()) {
            BlockPos minPos = this.pos;
            EnumFacing axleAxis = getRudderAxleAxisDirection().get();
            EnumFacing axleFacing = getRudderAxleFacingDirection().get();
            Vec3i otherAxis = axleAxis.getDirectionVec().crossProduct(axleFacing.getDirectionVec());

            int nexAxisX = axleAxis.getDirectionVec().getX() + axleFacing.getDirectionVec().getX();
            int nexAxisY = axleAxis.getDirectionVec().getY() + axleFacing.getDirectionVec().getY();
            int nexAxisZ = axleAxis.getDirectionVec().getZ() + axleFacing.getDirectionVec().getZ();

            int axleLength = getRudderAxleLength().get();

            int offsetX = nexAxisX * axleLength;
            int offsetY = nexAxisY * axleLength;
            int offsetZ = nexAxisZ * axleLength;

            BlockPos maxPos = minPos.add(offsetX, offsetY, offsetZ);

            int otherAxisXExpanded = otherAxis.getX() * axleLength;
            int otherAxisYExpanded = otherAxis.getY() * axleLength;
            int otherAxisZExpanded = otherAxis.getZ() * axleLength;

            return new AxisAlignedBB(minPos, maxPos)
                .grow(otherAxisXExpanded, otherAxisYExpanded, otherAxisZExpanded)
                .grow(.5, .5, .5);
        } else {
            return super.getRenderBoundingBox();
        }
    }

    @Override
    public boolean attemptToAssembleMultiblock(World worldIn, BlockPos pos, EnumFacing facing) {
        List<IMultiblockSchematic> schematics = MultiblockRegistry.getSchematicsWithPrefix("multiblock_rudder_boat_axle");
        for (IMultiblockSchematic schematic : schematics) {
            RudderBoatAxleMultiblockSchematic rudderSchem = (RudderBoatAxleMultiblockSchematic) schematic;
            if (facing.getAxis() != rudderSchem.getAxleAxisDirection().getAxis()
                && rudderSchem.getAxleFacingDirection() == facing
                && schematic.attemptToCreateMultiblock(worldIn, pos)) {
                return true;
            }
        }
        return false;
    }
}
