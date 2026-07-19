package dev.nitjsefnie.cultivated;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cultivated implements ModInitializer {
	public static final String MOD_ID = "cultivated";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Cultivated initialized");
	}
}
