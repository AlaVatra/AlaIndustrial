package dev.alaindustrial.menu;

import dev.alaindustrial.network.ResultChoicePayload;

/**
 * The menu of a furnace with a result button (MOD-1018): the electric furnace's and the iron furnace's. The press
 * rides the vanilla container-button channel ({@code clickMenuButton} with {@link #BUTTON_CYCLE_RESULT}); the
 * options come back to the one viewer as a {@link ResultChoicePayload}, kept by a
 * {@link dev.alaindustrial.network.ResultChoiceSync} field of the menu.
 */
public interface ResultChoiceMenu {

	/** The {@code clickMenuButton} id of the result button: far above every id a machine menu uses. */
	int BUTTON_CYCLE_RESULT = 1_018_000;

	/** Client side: the latest options of this menu's furnace (none until the first packet lands). */
	ResultChoicePayload resultChoices();

	/** Client side: accept a packet; one addressed to another menu is dropped. */
	void acceptResultChoices(ResultChoicePayload payload);
}
