package dev.system.voicesystem;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

/** Loaded by Simple Voice Chat (only if it is installed). Hands microphone audio to Voice System. */
public class VoiceSystemPlugin implements VoicechatPlugin {
	@Override
	public String getPluginId() {
		return VoiceSystemClient.MOD_ID;
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(ClientSoundEvent.class, this::onMicAudio);
	}

	private void onMicAudio(ClientSoundEvent event) {
		VoiceListener listener = VoiceSystemClient.listener();
		if (listener != null) {
			listener.offerVoiceChatAudio(event.getRawAudio());
		}
	}
}
