package dev.nezo.burmaldaholic.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.bots.logic.SettingsRules;
import dev.nezo.burmaldaholic.bots.logic.SettingsRules.Actor;
import dev.nezo.burmaldaholic.bots.logic.SettingsRules.Checked;
import dev.nezo.burmaldaholic.bots.logic.SettingsRules.DifficultyMode;
import dev.nezo.burmaldaholic.bots.logic.SettingsRules.Table;
import dev.nezo.burmaldaholic.bots.logic.SettingsRules.View;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.BotsMode;
import dev.nezo.burmaldaholic.core.bots.logic.OwnerControls;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import org.junit.jupiter.api.Test;

/** BOTS.md §2.1, §2.4, §2.5, §6.2, §8.2 and test plan §12.4 (S4, S6 access rules). */
class SettingsRulesTest {
	private static final Actor HOST = new Actor(false, false, false, true, true);
	private static final Actor GUEST = new Actor(false, false, false, false, true);
	private static final Actor KEEPER = new Actor(false, false, true, true, true);
	private static final Actor OWNER = new Actor(false, true, false, false, false);
	private static final Actor OP = new Actor(true, false, false, false, false);
	private static final BotSettings MIXED4 = new BotSettings(SeatPolicy.MIXED, 4, BotDifficulty.MIXED, true, true, BotSpeed.NORMAL);

	private static Table poker(int humans) {
		return new Table(false, OwnerControls.unowned(6), BotRole.MONEY, 6, humans, -1, true, false, true, true);
	}

	@Test
	void hostEditsSessionOnly() {
		View v = SettingsRules.view(HOST, poker(1), false);
		assertTrue(v.mayEdit());
		assertFalse(v.mayEditDefaults());
		assertTrue(v.botsVisible());
		assertEquals(5, v.maxCount());
		assertEquals(DifficultyMode.LEVEL, v.difficultyMode());
		assertTrue(v.mayEditAccess());
		assertTrue(v.privateAllowed());
		assertFalse(v.mayEditLimits());
		// defaults mode is for keeper / owner / op only
		assertFalse(SettingsRules.view(HOST, poker(1), true).mayEdit());
		assertTrue(SettingsRules.view(KEEPER, poker(1), true).mayEdit());
	}

	@Test
	void otherSeatedHumansAreReadOnly() {
		View v = SettingsRules.view(GUEST, poker(2), false);
		assertFalse(v.mayEdit());
		assertEquals(SettingsRules.HOST_LOCKED, v.lockReason());
		assertFalse(v.mayEditAccess());
		assertEquals(SettingsRules.HOST_LOCKED, v.privateReason());
		Checked c = SettingsRules.validate(MIXED4.withCount(1), MIXED4, v);
		assertEquals(SettingsRules.HOST_LOCKED, c.error());
	}

	@Test
	void ownerLocksHosts() {
		Table t = new Table(true, new OwnerControls(BotsMode.ALLOWED, false, 5, false), BotRole.MONEY, 6, 1, -1, true, false, true, true);
		View host = SettingsRules.view(HOST, t, false);
		assertFalse(host.mayEdit());
		assertEquals(SettingsRules.OWNER_LOCKED, host.lockReason());
		assertEquals(SettingsRules.PRIVATE_FORBIDDEN, host.privateReason());
		View owner = SettingsRules.view(OWNER, t, false);
		assertTrue(owner.mayEdit());
		assertTrue(owner.mayEditLimits());
		assertTrue(owner.mayEditBotsMode());
		assertFalse(SettingsRules.view(KEEPER, t, false).mayEditBotsMode()); // keeper means nothing at an owned table
	}

	@Test
	void botsOnlyNeedsTheHostAlone() {
		// S4: host switches to BOTS_ONLY while B is seated → refused with others_seated
		View two = SettingsRules.view(HOST, poker(2), false);
		assertFalse(two.botsOnlyAllowed());
		Checked c = SettingsRules.validate(MIXED4.withPolicy(SeatPolicy.BOTS_ONLY), MIXED4, two);
		assertEquals(SettingsRules.OTHERS_SEATED, c.error());
		View alone = SettingsRules.view(HOST, poker(1), false);
		Checked ok = SettingsRules.validate(MIXED4.withPolicy(SeatPolicy.BOTS_ONLY).withCount(0), MIXED4, alone);
		assertTrue(ok.ok());
		assertEquals(1, ok.settings().count()); // BOTS_ONLY: at least one bot
		assertTrue(SettingsRules.view(KEEPER, poker(3), true).botsOnlyAllowed()); // defaults may be BOTS_ONLY
	}

	@Test
	void countClampedToSeatsOwnerAndAtmosphereCaps() {
		View v = SettingsRules.view(HOST, poker(1), false);
		assertEquals(5, SettingsRules.validate(MIXED4.withCount(9), MIXED4, v).settings().count());
		Table owned = new Table(true, new OwnerControls(BotsMode.ALLOWED, true, 2, true), BotRole.MONEY, 6, 1, -1, true, false, true, true);
		assertEquals(2, SettingsRules.maxCount(owned));
		Table blackjack = new Table(false, OwnerControls.unowned(5), BotRole.ATMOSPHERE, 5, 1, 2, true, false, true, true);
		assertEquals(2, SettingsRules.maxCount(blackjack));
	}

	@Test
	void ownerModeHidesBots() {
		Table atmosphereOnlyPoker = new Table(true, OwnerControls.ownedDefaults(6), BotRole.MONEY, 6, 1, -1, true, false, true, true);
		View v = SettingsRules.view(OWNER, atmosphereOnlyPoker, false);
		assertFalse(v.botsVisible());
		assertEquals(SettingsRules.OWNER_OFF, v.botsReason());
		assertEquals(SettingsRules.OWNER_OFF, SettingsRules.validate(MIXED4, BotSettings.HUMANS_ONLY, v).error());
		assertTrue(SettingsRules.validate(BotSettings.HUMANS_ONLY, BotSettings.HUMANS_ONLY, v).ok());
		Table atmosphereOnlyBlackjack = new Table(true, OwnerControls.ownedDefaults(5), BotRole.ATMOSPHERE, 5, 1, 2, true, false, true, true);
		assertTrue(SettingsRules.view(OWNER, atmosphereOnlyBlackjack, false).botsVisible());
		Table serverOff = new Table(false, OwnerControls.unowned(6), BotRole.MONEY, 6, 1, -1, true, false, false, true);
		assertEquals(SettingsRules.DISABLED, SettingsRules.view(HOST, serverOff, false).botsReason());
	}

	@Test
	void hiddenDifficultyIsKept() {
		Table roulette = new Table(false, OwnerControls.unowned(8), BotRole.ATMOSPHERE, 8, 1, 3, false, false, true, true);
		View v = SettingsRules.view(HOST, roulette, false);
		assertEquals(DifficultyMode.HIDDEN, v.difficultyMode());
		BotSettings cur = MIXED4.withDifficulty(BotDifficulty.MIXED).withCount(1);
		assertEquals(BotDifficulty.MIXED, SettingsRules.validate(cur.withDifficulty(BotDifficulty.HARD), cur, v).settings().difficulty());
		Table chemmy = new Table(false, OwnerControls.unowned(8), BotRole.MONEY, 8, 1, -1, true, true, true, true);
		assertEquals(DifficultyMode.STYLE, SettingsRules.view(HOST, chemmy, false).difficultyMode());
	}

	@Test
	void savedInvitesAndLimits() {
		assertTrue(SettingsRules.invitesAreSaved(KEEPER));
		assertTrue(SettingsRules.invitesAreSaved(OWNER));
		assertTrue(SettingsRules.invitesAreSaved(OP));
		assertFalse(SettingsRules.invitesAreSaved(HOST));
		assertFalse(SettingsRules.invitesAreSaved(new Actor(true, false, false, true, true)));
		OwnerControls cur = OwnerControls.ownedDefaults(6);
		OwnerControls next = SettingsRules.clampLimits(new OwnerControls(BotsMode.ALLOWED, false, 99, true), cur, 6, true, true);
		assertEquals(new OwnerControls(BotsMode.ALLOWED, false, 5, true), next);
		assertEquals(BotsMode.ATMOSPHERE, SettingsRules.clampLimits(new OwnerControls(BotsMode.OFF, true, 1, true), cur, 6, true, false).botsMode());
		assertEquals(BotsMode.ALLOWED, SettingsRules.clampLimits(new OwnerControls(BotsMode.OFF, true, -3, true), cur, 6, false, true).botsMode());
		assertEquals(0, SettingsRules.clampLimits(new OwnerControls(BotsMode.OFF, true, -3, true), cur, 6, false, true).maxBots());
	}

	@Test
	void privateToggle() {
		// S6 is core's TableAccess (auto-invites the seated); here: who may flip it and when it is forbidden
		assertNull(SettingsRules.view(HOST, poker(2), false).privateReason());
		Table noPrivate = new Table(false, OwnerControls.unowned(6), BotRole.MONEY, 6, 1, -1, true, false, true, false);
		assertEquals(SettingsRules.PRIVATE_FORBIDDEN, SettingsRules.view(HOST, noPrivate, false).privateReason());
		assertEquals(SettingsRules.NOT_HOST, SettingsRules.view(new Actor(false, false, false, false, false), poker(1), false).privateReason());
	}
}
