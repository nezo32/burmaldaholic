/**
 * Loan Shark + Debt Collectors (GAME_DESIGN §5, UI.md §10).
 *
 * - service.ts    loan records, take/pay, warnings, default, late fees, garnishment, Asset Freeze,
 *                 HUD segment, core debt provider
 * - shark.ts      Loan Shark / Piglin Moneylender NPC, Loan screen, status/pay screen, respawn
 * - collectors.ts collector waves, squad state machine, negotiation, Audit, Repossession
 * - logic/        pure rules (unit-tested)
 */
import { type Player, system, world } from '@minecraft/server';
import { ModalFormData } from '@minecraft/server-ui';
import { type CasinoModule, type ModuleContext, type Raw, ModalLayout, chips, isOperator, join, lit, parseAmount, showForm, t, worldTick } from '../core';
import { type LoanApi, LOAN_SERVICE } from './api';
import { Collectors } from './collectors';
import { type LoanRecord, adminForceDefault, adminSetDebt } from './logic';
import { LoanService } from './service';
import { LoanShark } from './shark';

interface Runtime {
  svc: LoanService;
  shark: LoanShark;
  collectors: Collectors;
}

let runtime: Runtime | undefined;

type AdminOp = 'clear' | 'set' | 'default';

/** Operator debt edits (§5.8.5). Returns the new record. */
function adminApply(rt: Runtime, target: Player, op: AdminOp, amount: number): LoanRecord {
  const now = worldTick();
  const rec = rt.svc.record(target);
  const next = op === 'default' ? adminForceDefault(rec, now) : adminSetDebt(rec, op === 'clear' ? 0 : amount, now);
  rt.svc.adminReplace(target, next);
  rt.svc.tick(target);
  return rt.svc.record(target);
}

async function adminForm(rt: Runtime, op: Player): Promise<void> {
  if (!isOperator(op)) return op.sendMessage(t('gui.burmaldaholic.error.no_permission'));
  const players = world.getAllPlayers();
  const layout = new ModalLayout();
  const form = new ModalFormData().title(t('gui.burmaldaholic.menu.admin.clear_debt'));
  form.dropdown(t('gui.burmaldaholic.menu.admin.player'), players.map((p) => lit(p.name)));
  const iPlayer = layout.control();
  form.dropdown(t('gui.burmaldaholic.loan.admin.action'), [t('gui.burmaldaholic.menu.admin.clear_debt'), t('gui.burmaldaholic.loan.admin.set'), t('gui.burmaldaholic.loan.admin.default')]);
  const iOp = layout.control();
  form.textField(t('gui.burmaldaholic.common.amount'), lit('0'));
  const iAmount = layout.control();
  form.submitButton(t('gui.burmaldaholic.common.confirm'));
  const res = await showForm(op, form);
  if (!res || res.canceled) return;
  const target = players[Number(layout.value(res, iPlayer))];
  const which = (['clear', 'set', 'default'] as const)[Number(layout.value(res, iOp))] ?? 'clear';
  const amount = parseAmount(String(layout.value(res, iAmount) ?? '')) ?? 0;
  if (!target?.isValid) return op.sendMessage(t('gui.burmaldaholic.error.invalid_amount'));
  if (which === 'set' && !(amount > 0)) return op.sendMessage(t('gui.burmaldaholic.error.invalid_amount'));
  const rec = adminApply(rt, target, which, amount);
  op.sendMessage(t('gui.burmaldaholic.menu.admin.done', join(lit(target.name), lit(': '), chips(rec.owed))));
}

/** `/scriptevent burmaldaholic:loan debt <player> clear|set <n>|default` */
function scriptEvent(rt: Runtime, message: string, reply: (m: Raw) => void): void {
  const [cmd, name, op, n] = message.trim().split(/\s+/);
  if (cmd !== 'debt' || !name) return reply(t('gui.burmaldaholic.error.invalid_amount'));
  const target = world.getAllPlayers().find((p) => p.name.toLowerCase() === name.toLowerCase());
  if (!target) return reply(t('gui.burmaldaholic.error.invalid_amount'));
  const amount = parseAmount(n ?? '') ?? 0;
  if (op === 'set' && !(amount > 0)) return reply(t('gui.burmaldaholic.error.invalid_amount'));
  const which: AdminOp = op === 'set' ? 'set' : op === 'default' ? 'default' : 'clear';
  const rec = adminApply(rt, target, which, amount);
  reply(t('gui.burmaldaholic.menu.admin.done', join(lit(target.name), lit(': '), chips(rec.owed))));
}

function onWorldLoad(ctx: ModuleContext): void {
  const svc = new LoanService(ctx);
  const shark = new LoanShark(svc);
  const collectors = new Collectors(svc);
  const rt: Runtime = (runtime = { svc, shark, collectors });

  ctx.economy.setDebtProvider({ owed: (p) => svc.owed(p), inDefault: (p) => svc.inDefault(p) });
  ctx.economy.onChange((p, _balance, delta, reason) => {
    try {
      svc.onBalanceChange(p, delta, reason);
    } catch (err) {
      ctx.log.error('garnish', err);
    }
  });
  ctx.hud.addSegment({ id: 'loan.debt', order: 40, render: (p) => svc.hudSegment(p) });
  ctx.menu.add({ id: 'loan', order: 30, label: t('gui.burmaldaholic.menu.loan'), icon: 'textures/items/emerald', visible: () => svc.active(), open: (p) => shark.statusScreen(p) });
  ctx.admin.addAction({ id: 'loan.debt', label: t('gui.burmaldaholic.menu.admin.clear_debt'), run: (op) => adminForm(rt, op) });

  system.afterEvents.scriptEventReceive.subscribe(
    (e) => {
      if (e.id !== 'burmaldaholic:loan') return;
      const src = e.sourceEntity?.typeId === 'minecraft:player' ? (e.sourceEntity as Player) : undefined;
      if (src && !isOperator(src)) return src.sendMessage(t('gui.burmaldaholic.error.no_permission'));
      try {
        scriptEvent(rt, e.message, (m) => (src ? src.sendMessage(m) : ctx.log.info(JSON.stringify(m))));
      } catch (err) {
        ctx.log.error('scriptevent', err);
      }
    },
    { namespaces: ['burmaldaholic'] },
  );

  world.afterEvents.playerSpawn.subscribe(
    ctx.guard((e) => {
      if (e.initialSpawn) svc.onJoin(e.player);
    }),
  );
  world.afterEvents.playerLeave.subscribe((e) => svc.forget(e.playerId));

  shark.start();
  collectors.start();
  if (svc.active()) svc.touch();

  system.runInterval(() => {
    if (!svc.active()) return;
    try {
      svc.shiftDormancy();
    } catch (err) {
      ctx.log.error('dormancy', err);
    }
    for (const p of world.getAllPlayers()) {
      try {
        svc.tick(p);
        collectors.maybeSpawn(p);
      } catch (err) {
        ctx.log.error(`tick ${p.name}`, err);
      }
    }
  }, 20);

  const api: LoanApi = {
    owed: (p) => svc.owed(p),
    status: (p) => (svc.active() ? svc.record(p).status : 'none'),
    inDefault: (p) => svc.inDefault(p),
    isWagerFrozen: (p) => svc.frozen(p),
    isSquadMember: (e) => collectors.isSquadMember(e),
    pay: (p, amount) => svc.pay(p, amount),
    openStatus: (p) => shark.statusScreen(p),
    spawnShark: (dim, loc, v) => shark.spawn(dim, loc, v === 'piglin'),
  };
  ctx.services.provide(LOAN_SERVICE, api);
}

export const loanModule: CasinoModule = {
  id: 'loan',
  onStartup(ctx) {
    ctx.registerCommand({
      name: 'loan',
      description: 'Show your loan status and pay it off',
      run: (p) => {
        if (p && runtime) void runtime.shark.statusScreen(p);
      },
    });
  },
  onWorldLoad,
};
