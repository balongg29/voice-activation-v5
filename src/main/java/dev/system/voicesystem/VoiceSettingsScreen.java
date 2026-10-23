package dev.system.voicesystem;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Settings screen reached from Mod Menu: pick which key saying "system" presses. */
public class VoiceSettingsScreen extends Screen {
	private final Screen parent;
	private Button keyButton;
	private boolean waitingForKey;

	public VoiceSettingsScreen(Screen parent) {
		super(Component.literal("Voice System"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int centerY = this.height / 2;

		keyButton = Button.builder(buttonLabel(), button -> {
				waitingForKey = true;
				button.setMessage(Component.literal("Press a key...  (Esc to cancel)"));
			})
			.bounds(centerX - 120, centerY - 20, 240, 20)
			.build();
		this.addRenderableWidget(keyButton);

		this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(centerX - 120, centerY + 10, 240, 20)
				.build());
	}

	private Component buttonLabel() {
		VoiceConfig.Trigger trigger = VoiceSystemClient.config().systemTrigger();
		String keyName = trigger.key;
		String shown;
		try {
			shown = InputConstants.getKey(keyName).getDisplayName().getString();
		} catch (Exception e) {
			shown = keyName;
		}
		return Component.literal("Saying \"" + trigger.word + "\" presses:  " + shown);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (waitingForKey) {
			waitingForKey = false;
			if (event.key() != InputConstants.KEY_ESCAPE) {
				InputConstants.Key picked = InputConstants.Type.KEYSYM.getOrCreate(event.key());
				VoiceSystemClient.config().systemTrigger().key = picked.getName();
				VoiceSystemClient.config().save();
			}
			keyButton.setMessage(buttonLabel());
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
