package knightminer.knightsconstruct.plugin.exnihilo;

import com.google.common.eventbus.Subscribe;

import knightminer.knightsconstruct.common.CommonProxy;
import knightminer.knightsconstruct.common.KnightsPulse;
import knightminer.knightsconstruct.library.KnightsRegistry;
import knightminer.knightsconstruct.plugin.exnihilo.items.SledgeHammer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import slimeknights.mantle.pulsar.pulse.Pulse;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.materials.Material;
import slimeknights.tconstruct.library.tools.ToolCore;
import slimeknights.tconstruct.library.tools.ToolPart;

@Pulse(id = ExNihiloPlugin.pulseID, description = "Adds various plugins geared towards Ex Nihilo", modsRequired = "exnihiloadscensio")
public class ExNihiloPlugin extends KnightsPulse {
	public static final String pulseID = "ExNihiloPlugin";

	@SidedProxy(clientSide = "knightminer.knightsconstruct.plugin.exnihilo.ENPluginClientProxy", serverSide = "knightminer.knightsconstruct.common.CommonProxy")
	public static CommonProxy proxy;

	public static ToolPart sledgeHead;
	public static ToolCore sledgeHammer;

	@Subscribe
	public void preInit(FMLPreInitializationEvent event) {
		sledgeHead = registerItem(new ToolPart(Material.VALUE_Ingot * 2), "sledge_head");
		sledgeHead.setCreativeTab(KnightsRegistry.tabGeneral);
		sledgeHammer = registerTool(new SledgeHammer(), "sledge_hammer");

		proxy.preInit();
	}

	@Subscribe
	public void init(FMLInitializationEvent event) {
		TinkerRegistry.registerToolCrafting(sledgeHammer);

		proxy.init();
	}

	@Subscribe
	public void postInit(FMLPostInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(new ENPluginEvents());
	}
}
