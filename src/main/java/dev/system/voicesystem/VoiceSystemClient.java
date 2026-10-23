package dev.system.voicesystem;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class VoiceSystemClient implements ClientModInitializer {
	public static final String MOD_ID = "voicesystem";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static VoiceConfig config;
	private static VoiceListener listener;

	public static VoiceConfig config() {
		return config;
	}

	public static VoiceListener listener() {
		return listener;
	}

	@Override
	public void onInitializeClient() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		config = VoiceConfig.loadOrCreate(configDir.resolve("voicesystem.json"));

		Path modelDir = configDir.resolve(config.modelFolder);
		listener = new VoiceListener(config, modelDir);

		boolean voiceChatInstalled = FabricLoader.getInstance().isModLoaded("voicechat");
		if (config.useSimpleVoiceChat && voiceChatInstalled) {
			listener.startFromVoiceChat();
		} else {
			listener.startFromMic();
		}

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (listener != null) listener.stop();
		}));
	}
}
