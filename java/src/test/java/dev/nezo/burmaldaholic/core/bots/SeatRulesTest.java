package dev.nezo.burmaldaholic.core.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.BotsMode;
import dev.nezo.burmaldaholic.core.bots.logic.OwnerControls;
import dev.nezo.burmaldaholic.core.bots.logic.SeatAdmission;
import dev.nezo.burmaldaholic.core.bots.logic.SeatAdmission.Verdict;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.SettingsChange;
import dev.nezo.burmaldaholic.core.bots.logic.SettingsChange.Role;
import dev.nezo.burmaldaholic.core.bots.logic.TableAccess;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Admission (§3.2), who may change what (§2.4, §6.2) and private tables (§2.5): S4, S6. */
class SeatRulesTest {
	private static final BotSettings MIXED = new BotSettings(SeatPolicy.MIXED, 4, BotDifficulty.NORMAL, true, true, BotSpeed.NORMAL);
	private static final OwnerControls OPEN = OwnerControls.unowned(6);

	@Test
	void admission() {
		assertEquals(Verdict.SIT, SeatAdmission.admit(false, false, true, SeatPolicy.MIXED, false, 1, 1, 4), "free seat");
		assertEquals(Verdict.CLAIMANT, SeatAdmission.admit(false, false, true, SeatPolicy.MIXED, false, 1, 0, 5), "S3: all seats taken, bots seated");
		assertEquals(Verdict.FULL, SeatAdmission.admit(false, false, true, SeatPolicy.MIXED, false, 6, 0, 0), "all human");
		assertEquals(Verdict.FULL, SeatAdmission.admit(false, false, true, SeatPolicy.MIXED, false, 4, 0, 0), "every bot seat already claimed");
		assertEquals(Verdict.PRIVATE, SeatAdmission.admit(false, false, false, SeatPolicy.MIXED, false, 1, 3, 2), "S6: not invited");
		assertEquals(Verdict.BOTS_ONLY, SeatAdmission.admit(false, false, true, SeatPolicy.BOTS_ONLY, false, 1, 2, 3));
		assertEquals(Verdict.SIT, SeatAdmission.admit(false, false, true, SeatPolicy.BOTS_ONLY, false, 0, 6, 0), "empty BOTS_ONLY table: a new session");
		assertEquals(Verdict.SIT, SeatAdmission.admit(true, false, false, SeatPolicy.BOTS_ONLY, false, 2, 0, 0), "already seated always sits");
		assertEquals(Verdict.CLAIMANT, SeatAdmission.admit(false, true, true, SeatPolicy.MIXED, false, 1, 0, 0), "a claim is idempotent");
	}

	@Test
	void s4BotsOnlyNeedsTheHostAlone() {
		SettingsChange.Outcome o = SettingsChange.validate(Role.HOST, true, OPEN, BotRole.MONEY, true, MIXED.withPolicy(SeatPolicy.BOTS_ONLY), false, 2, 6);
		assertEquals(SettingsChange.ERR_OTHERS_SEATED, o.error());
		o = SettingsChange.validate(Role.HOST, true, OPEN, BotRole.MONEY, true, MIXED.withPolicy(SeatPolicy.BOTS_ONLY).withCount(0), false, 1, 6);
		assertTrue(o.ok());
		assertEquals(1, o.settings().count(), "BOTS_ONLY count ≥ 1");
		assertFalse(o.asDefaults(), "a host edits the session only");
	}

	@Test
	void rolesAndOwnerLimits() {
		assertEquals(SettingsChange.ERR_HOST_LOCKED, SettingsChange.validate(Role.NONE, true, OPEN, BotRole.MONEY, true, MIXED, false, 2, 6).error());
		assertEquals(SettingsChange.ERR_NOT_HOST, SettingsChange.validate(Role.NONE, false, OPEN, BotRole.MONEY, true, MIXED, false, 2, 6).error());
		OwnerControls locked = new OwnerControls(BotsMode.ALLOWED, false, 5, true);
		assertEquals(SettingsChange.ERR_OWNER_LOCKED, SettingsChange.validate(Role.HOST, true, locked, BotRole.MONEY, true, MIXED, false, 1, 6).error());
		assertTrue(SettingsChange.validate(Role.KEEPER, false, locked, BotRole.MONEY, true, MIXED, true, 1, 6).asDefaults(), "keeper saves defaults");
		OwnerControls atmosphere = OwnerControls.ownedDefaults(6);
		assertEquals(SettingsChange.ERR_OWNER_OFF, SettingsChange.validate(Role.OWNER, false, atmosphere, BotRole.MONEY, true, MIXED, true, 1, 6).error(),
			"Atmosphere only behaves as Off at poker/chemmy");
		assertTrue(SettingsChange.validate(Role.OWNER, false, atmosphere, BotRole.ATMOSPHERE, true, MIXED, true, 1, 6).ok());
		assertTrue(SettingsChange.validate(Role.HOST, true, atmosphere, BotRole.MONEY, true, BotSettings.HUMANS_ONLY, false, 1, 6).ok(),
			"switching bots off is always allowed");
		OwnerControls max2 = new OwnerControls(BotsMode.ALLOWED, true, 2, true);
		assertEquals(2, SettingsChange.validate(Role.HOST, true, max2, BotRole.MONEY, true, MIXED.withCount(5), false, 1, 6).settings().count());
		assertEquals(SettingsChange.ERR_DISABLED, SettingsChange.validate(Role.OPERATOR, false, OPEN, BotRole.MONEY, false, MIXED, false, 1, 6).error());
		assertEquals(5, SettingsChange.validate(Role.OPERATOR, false, OPEN, BotRole.MONEY, true, MIXED.withCount(8), false, 1, 6).settings().count(),
			"count ≤ seats − 1");
	}

	@Test
	void accessRights() {
		OwnerControls forbid = OwnerControls.ownedDefaults(6);
		assertEquals(SettingsChange.ERR_PRIVATE_FORBIDDEN, SettingsChange.validateAccess(Role.HOST, true, forbid, true, true));
		assertNull(SettingsChange.validateAccess(Role.HOST, true, forbid, true, false), "switching off / inviting is fine");
		assertNull(SettingsChange.validateAccess(Role.OPERATOR, false, forbid, true, true), "ops bypass");
		assertEquals(SettingsChange.ERR_DISABLED, SettingsChange.validateAccess(Role.KEEPER, false, OPEN, false, true));
		assertEquals(SettingsChange.ERR_HOST_LOCKED, SettingsChange.validateAccess(Role.NONE, true, OPEN, true, true));
	}

	@Test
	void s6PrivateTable() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		UUID g = UUID.randomUUID();
		TableAccess acc = new TableAccess();
		acc.setPrivate(true, List.of(a, b));
		assertTrue(acc.mayJoin(a, false) && acc.mayJoin(b, false), "seated humans auto-invited");
		assertFalse(acc.mayJoin(c, false));
		assertTrue(acc.mayJoin(c, true), "ops bypass");
		assertTrue(acc.invite(g, true, 4));
		assertTrue(acc.invite(c, false, 4));
		assertFalse(acc.invite(UUID.randomUUID(), false, 4), "list full");
		acc.endSession();
		assertTrue(acc.mayJoin(g, false), "keeper/owner guests are saved");
		assertFalse(acc.mayJoin(c, false), "host invites expire with the session");
		acc.uninvite(g);
		assertFalse(acc.mayJoin(g, false));
	}
}
