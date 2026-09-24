/**
 * Heavy bot work under `system.runJob` (BOTS.md §7.5): at most `bots.maxConcurrentJobs` generators per
 * world, FIFO queue, each yielding every 25 units; at the deadline the partial result is used.
 * Stale work (the table's decision token changed) is dropped — the poker `botSeq` pattern.
 */
import { system } from '@minecraft/server';
import type { BotWork } from '../logic/bots/policy';
import { createLogger } from '../log';

const log = createLogger('core.bots');
const YIELD_EVERY = 25;

interface Job {
  work: BotWork;
  deadlineTick: number;
  stillWanted: () => boolean;
  onDone: (result: unknown) => void;
}

export class BotJobs {
  private readonly queue: Job[] = [];
  private running = 0;

  constructor(private readonly maxConcurrent: () => number) {}

  submit(work: BotWork, deadlineTicks: number, stillWanted: () => boolean, onDone: (result: unknown) => void): void {
    this.queue.push({ work, deadlineTick: system.currentTick + Math.max(0, deadlineTicks), stillWanted, onDone });
    this.pump();
  }

  private pump(): void {
    while (this.running < Math.max(1, this.maxConcurrent()) && this.queue.length) {
      const job = this.queue.shift()!;
      this.running++;
      system.runJob(this.run(job));
    }
  }

  private *run(job: Job): Generator<void, void, void> {
    try {
      while (!job.work.done() && system.currentTick < job.deadlineTick) {
        if (!job.stillWanted()) return;
        job.work.step(YIELD_EVERY);
        yield;
      }
      if (job.stillWanted()) job.onDone(job.work.result());
    } catch (e) {
      log.error('bot job failed', e);
      if (job.stillWanted()) job.onDone(undefined);
    } finally {
      this.running--;
      this.pump();
    }
  }
}
