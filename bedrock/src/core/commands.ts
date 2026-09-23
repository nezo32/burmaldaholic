/**
 * Custom slash commands via the STABLE CustomCommandRegistry (@minecraft/server >= 2.0).
 * Must be registered during system.beforeEvents.startup (use StartupContext.registerCommand).
 * Command callbacks run in restricted execution, so handlers are deferred with system.run.
 *
 * NOTE: `description` is a plain string shown in the command autocomplete; the engine does
 * not translate it, so keep it short English (the only allowed non-rawtext string).
 */
import {
  CommandPermissionLevel,
  type CustomCommandOrigin,
  type CustomCommandParameter,
  type CustomCommandRegistry,
  CustomCommandStatus,
  type Player,
  system,
} from '@minecraft/server';
import { isCasinoEnabled } from './casino';
import { nsId } from './logic/ids';
import { t } from './logic/rawtext';
import type { Logger } from './log';

export interface CommandSpec {
  /** Without namespace; registered as `/burmaldaholic:<name>`. Lowercase, [a-z0-9_]. */
  name: string;
  description: string;
  /** 'any' = every player; 'admin' = operators only. */
  permission?: 'any' | 'admin';
  mandatory?: CustomCommandParameter[];
  optional?: CustomCommandParameter[];
  /** Allow running while casino mode is OFF (admin toggles). Default false. */
  bypassCasinoGuard?: boolean;
  /** Runs next tick, with full privileges. `player` is undefined for non-player sources. */
  run(player: Player | undefined, args: unknown[], origin: CustomCommandOrigin): void;
}

export function registerCommand(reg: CustomCommandRegistry, spec: CommandSpec, log: Logger): void {
  reg.registerCommand(
    {
      name: nsId(spec.name),
      description: spec.description,
      permissionLevel: spec.permission === 'admin' ? CommandPermissionLevel.GameDirectors : CommandPermissionLevel.Any,
      cheatsRequired: false,
      mandatoryParameters: spec.mandatory,
      optionalParameters: spec.optional,
    },
    (origin, ...args) => {
      system.run(() => {
        const src = origin.sourceEntity;
        const player = src?.typeId === 'minecraft:player' ? (src as Player) : undefined;
        try {
          if (!spec.bypassCasinoGuard && !isCasinoEnabled()) {
            player?.sendMessage(t('msg.burmaldaholic.core.casino_disabled'));
            return;
          }
          spec.run(player, args, origin);
        } catch (e) {
          log.error(`command ${spec.name} failed`, e);
        }
      });
      return { status: CustomCommandStatus.Success };
    },
  );
}
