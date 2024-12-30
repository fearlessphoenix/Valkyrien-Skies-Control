package org.valkyrienskies.addon.control.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.valkyrienskies.addon.control.ValkyrienSkiesControl;
import org.valkyrienskies.mod.common.command.config.ShortName;
import org.valkyrienskies.mod.common.config.VSConfigTemplate;

// NOTE: When updating names/comments remember to update them in the lang files.
@SuppressWarnings("WeakerAccess") // NOTE: Any forge config option MUST be "public"
@Config(modid = ValkyrienSkiesControl.MOD_ID)
public class VSControlConfig extends VSConfigTemplate {

    @Config.Name("Disable Wrench Modes")
    @Config.Comment("Makes wrench toggle a multiblock’s constructed state, removes modes.")
    public static boolean wrenchModeless = false;

    @Config.Name("Network Relay Connections Limit")
    @Config.Comment({
            "How many components or relays can be connected to a Network Relay block.",
            "Default is 8."
    })
    public static int networkRelayLimit = 8;

    @Config.Name("Relay Wire Max Length")
    @Config.Comment({
            "How long a single relay wire can extend.",
            "Default is 8 meters."
    })
    public static double relayWireLength = 8D;

    @Config.Name("Compacted Valkyrium Upwards Force")
    @Config.Comment({
            "How much upwards force each block applies.",
            "Default is 200000 Newtons."
    })
    public static double compactedValkyriumLift = 200000;

    @Config.Name("Gyroscope Stabilizer Max Torque")
    @Config.Comment("Max torque in newton-meters")
    public static double stabilizerMaxTorque = 15000000;

    @Config.Name("Rudder Force Multiplier")
    @Config.Comment("Another arbitrary number that feels about right")
    public static double rudderForceMultiplier = 3000;

    @Config.Name("Boat Rudder Fluid Force Multiplier")
    @Config.Comment("The additional force multiplier of a boat rudder when in a fluid.")
    public static double rudderFluidMultiplier = 100;

    @Config.Name("Giant Propeller Thrust Multiplier")
    @Config.Comment("Thrust multiplier of the giant propeller.")
    public static double propellerThrustMultiplier = 500;

    @Config.Name("Boat Propeller Fluid Thrust Multiplier")
    @Config.Comment("The additional thrust multiplier of a boat propeller when in a fluid.")
    public static double propellerFluidMultiplier = 2;

    @Config.Name("Gyroscope Dampener Max Torque")
    @Config.Comment("Max torque in newton-meters")
    public static double dampenerMaxTorque = 10000;

    @Config.Name("Valkyrium Compressor Max Height")
    @Config.Comment("Valkyrium Compressor efficiency linearly decreases until it reaches 0 at this height")
    public static double compressorMaxHeight = 500;

    @Config.Name("Engine Thrust Settings")
    @ShortName("engineThrust")
    @Config.Comment({
            "Set the thrust force of engine blocks.",
            "Engines blocks must be replaced after changes are made."
    })
    @Config.LangKey("vs_control.general.engine_thrust")
    public static final VSControlConfig.EngineThrust ENGINE_THRUST = new VSControlConfig.EngineThrust();

    public static class EngineThrust {

        @Config.RequiresMcRestart
        @Config.Name("Basic Engine Thrust")
        public double basicEngineThrust = 100000;

        @Config.RequiresMcRestart
        @Config.Name("Advanced Engine Thrust")
        public double advancedEngineThrust = 250000;

        @Config.RequiresMcRestart
        @Config.Name("Elite Engine Thrust")
        public double eliteEngineThrust = 500000;

        @Config.RequiresMcRestart
        @Config.Name("Ultimate Engine Thrust")
        public double ultimateEngineThrust = 1000000;

        @Config.RequiresMcRestart
        @Config.Name("Redstone Engine Thrust")
        public double redstoneEngineThrust = 62500;

        @Config.RequiresMcRestart
        @Config.Name("Valkyrium Compressor Thrust")
        public double compressorMaxThrust = 2000000;

    }

    /**
     * Synchronizes the data in this class and the data in the forge configuration
     */
    public static void sync() {
        ConfigManager.sync(ValkyrienSkiesControl.MOD_ID, Config.Type.INSTANCE);
        VSControlConfig.onSync();
    }

    @Mod.EventBusSubscriber(modid = ValkyrienSkiesControl.MOD_ID)
    @SuppressWarnings("unused")
    private static class EventHandler {

        @SubscribeEvent
        public static void onConfigChanged(final ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(ValkyrienSkiesControl.MOD_ID)) {
                sync();
            }
        }
    }
}
