package dev.nezo.burmaldaholic.worldgen.logic;

import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure jigsaw assembler for the standalone casino (architecture doc casino-buildings.md §4.1, §9.1): places a
 * style's pieces exactly like vanilla's rigid {@code JigsawPlacement} does — a parent connector at {@code p}
 * pointing {@code d} receives the child connector whose {@code name} equals the parent's {@code target}, at
 * {@code p + d}, facing {@code -d} — and enumerates every combination of the multi-element pools. Used by
 * {@code AssemblyTest} (completeness, overlaps, footprint) and by {@link PackGen} for the preview manifest.
 *
 * <p>Rotations are clockwise quarter turns around the template origin ({@link Geometry}), the start piece
 * unrotated at the origin. Vanilla picks one random rotation for the whole assembly, which rotates every
 * result rigidly — so the checks here hold for every rotation.
 */
public final class Assembly {
	private Assembly() {}

	/** Content budget per standalone casino (§9.5). */
	public static final int MAX_FOOTPRINT = 47;
	public static final int MAX_NPCS = 6;
	public static final int MAX_CHESTS = 2;
	public static final int MAX_GAME_BLOCKS = 40;
	public static final int MAX_CELLS = 60_000;

	/** One placed piece: template origin + clockwise quarter turns, world box, jigsaw depth, pool. */
	public record Placed(Layout layout, String pool, Vec origin, int turns, Geometry.Box box, int depth) {}

	/** An assembled casino; {@code problems} empty = vanilla would build it exactly like this. */
	public record Result(String style, String name, List<Placed> pieces, List<String> problems) {
		public Result {
			pieces = List.copyOf(pieces);
			problems = List.copyOf(problems);
		}

		public Geometry.Box footprint() {
			Geometry.Box u = pieces.getFirst().box();
			for (Placed p : pieces) {
				Geometry.Box b = p.box();
				u = new Geometry.Box(Math.min(u.minX(), b.minX()), Math.min(u.minY(), b.minY()), Math.min(u.minZ(), b.minZ()),
					Math.max(u.maxX(), b.maxX()), Math.max(u.maxY(), b.maxY()), Math.max(u.maxZ(), b.maxZ()));
			}
			return u;
		}

		/** Pools present ({@code entrance}, {@code floor}, …). */
		public Set<String> pools() {
			Set<String> s = new java.util.TreeSet<>();
			pieces.forEach(p -> s.add(PackFiles.poolName(PackFiles.pieceName(p.layout().id()))));
			return s;
		}

		/** Game families present (from the game block ids in the templates). */
		public Set<Game.Family> families() {
			Set<Game.Family> f = EnumSet.noneOf(Game.Family.class);
			for (Placed p : pieces) {
				f.addAll(Assembly.families(p.layout()));
			}
			return f;
		}
	}

	/** Every assembly of every complete style in {@code layouts}. */
	public static List<Result> all(Collection<Layout> layouts) {
		List<Result> out = new ArrayList<>();
		List<PackFiles.Pool> pools = PackFiles.pools(layouts);
		for (String style : PackFiles.completeStyles(layouts)) {
			out.addAll(style(style, pools));
		}
		return out;
	}

	/** Every combination of one element per pool of {@code style} (2 floors × 2 rears = 4 for the contract set). */
	public static List<Result> style(String style, List<PackFiles.Pool> allPools) {
		String prefix = PackFiles.stylePoolPath(style, "");
		List<PackFiles.Pool> pools = allPools.stream().filter(p -> p.path().startsWith(prefix)).toList();
		List<Map<String, Layout>> choices = new ArrayList<>();
		choices.add(new LinkedHashMap<>());
		for (PackFiles.Pool pool : pools) {
			List<Map<String, Layout>> next = new ArrayList<>();
			for (Map<String, Layout> c : choices) {
				for (PackFiles.Element e : pool.elements()) {
					Map<String, Layout> m = new LinkedHashMap<>(c);
					m.put(pool.id(), e.layout());
					next.add(m);
				}
			}
			choices = next;
		}
		List<Result> out = new ArrayList<>();
		for (Map<String, Layout> choice : choices) {
			StringBuilder name = new StringBuilder();
			for (PackFiles.Pool pool : pools) {
				if (pool.elements().size() > 1) {
					name.append(name.isEmpty() ? "" : "+").append(PackFiles.pieceName(choice.get(pool.id()).id()));
				}
			}
			out.add(assemble(style, name.isEmpty() ? "default" : name.toString(), choice));
		}
		return out;
	}

	/**
	 * Assembles from the {@code entrance} pool's choice. {@code choice} maps a pool id to the element drawn
	 * from it (one per pool, like a vanilla run that drew that element).
	 */
	public static Result assemble(String style, String name, Map<String, Layout> choice) {
		List<String> problems = new ArrayList<>();
		List<Placed> placed = new ArrayList<>();
		Layout start = choice.get(PackFiles.stylePoolId(style, "entrance"));
		if (start == null) {
			return new Result(style, name, List.of(), List.of("no entrance piece"));
		}
		placed.add(new Placed(start, PackFiles.stylePoolId(style, "entrance"), Vec.ZERO, 0, Geometry.templateBox(Vec.ZERO, start.size(), 0), 0));
		ArrayDeque<Placed> queue = new ArrayDeque<>(placed);
		while (!queue.isEmpty()) {
			Placed parent = queue.poll();
			for (Layout.Jigsaw j : sortedJigsaws(parent.layout())) {
				if (j.pool().equals("minecraft:empty")) {
					continue;
				}
				String where = parent.layout().id() + " connector " + j.name() + " at " + j.pos();
				Layout child = choice.get(j.pool());
				if (child == null) {
					problems.add(where + ": pool " + j.pool() + " has no element");
					continue;
				}
				Optional<Facing> pf = horizontal(j.orientation());
				if (pf.isEmpty()) {
					problems.add(where + ": orientation " + j.orientation() + " is not horizontal");
					continue;
				}
				Facing out = pf.get().rotate(parent.turns());
				Vec target = Geometry.toWorld(parent.origin(), j.pos(), parent.turns()).add(new Vec(out.dx(), 0, out.dz()));
				List<Layout.Jigsaw> matches = sortedJigsaws(child).stream().filter(c -> c.name().equals(j.target())).toList();
				if (matches.size() != 1) {
					problems.add(where + ": " + matches.size() + " connectors named " + j.target() + " in " + child.id() + " (need exactly 1)");
					continue;
				}
				Layout.Jigsaw c = matches.getFirst();
				Optional<Facing> cf = horizontal(c.orientation());
				if (cf.isEmpty()) {
					problems.add(child.id() + " connector " + c.name() + ": orientation " + c.orientation() + " is not horizontal");
					continue;
				}
				int turns = Math.floorMod(out.opposite().ordinal() - cf.get().ordinal(), 4);
				Vec origin = target.subtract(Geometry.rotate(c.pos(), turns));
				Geometry.Box box = Geometry.templateBox(origin, child.size(), turns);
				int depth = parent.depth() + 1;
				if (depth > PackFiles.SIZE) {
					problems.add(where + ": depth " + depth + " exceeds size " + PackFiles.SIZE);
					continue;
				}
				for (Placed other : placed) {
					if (other.box().intersects(box)) {
						problems.add(child.id() + " (via " + where + ") overlaps " + other.layout().id());
					}
				}
				Placed p = new Placed(child, j.pool(), origin, turns, box, depth);
				placed.add(p);
				queue.add(p);
			}
		}
		Result r = new Result(style, name, placed, problems);
		List<String> all = new ArrayList<>(problems);
		all.addAll(budget(r));
		return new Result(style, name, placed, all);
	}

	/** Footprint, reach and content-budget problems of an assembly (§4.1, §9.5). */
	static List<String> budget(Result r) {
		List<String> out = new ArrayList<>();
		Geometry.Box f = r.footprint();
		if (f.xSpan() > MAX_FOOTPRINT || f.zSpan() > MAX_FOOTPRINT) {
			out.add("footprint " + f.xSpan() + "×" + f.zSpan() + " exceeds " + MAX_FOOTPRINT + "×" + MAX_FOOTPRINT);
		}
		Geometry.Box s = r.pieces().getFirst().box();
		int cx = s.centerX();
		int cz = s.centerZ();
		for (Placed p : r.pieces()) {
			Geometry.Box b = p.box();
			int reach = Math.max(Math.max(cx - b.minX(), b.maxX() - cx), Math.max(cz - b.minZ(), b.maxZ() - cz));
			if (reach > PackFiles.MAX_DISTANCE) {
				out.add(p.layout().id() + " reaches " + reach + " blocks from the start centre (max " + PackFiles.MAX_DISTANCE + ")");
			}
		}
		int npcs = 0;
		int chests = 0;
		int games = 0;
		int cells = 0;
		int anchors = 0;
		for (Placed p : r.pieces()) {
			for (Layout.PlacedMarker m : p.layout().markers()) {
				switch (m.marker()) {
					case Markers.Npc n -> npcs++;
					case Markers.Chest c -> chests++;
					case Markers.Anchor a -> anchors++;
				}
			}
			Vec size = p.layout().size();
			for (int x = 0; x < size.x(); x++) {
				for (int y = 0; y < size.y(); y++) {
					for (int z = 0; z < size.z(); z++) {
						BlockSpec b = p.layout().grid().get(x, y, z);
						if (b != null) {
							cells++;
							// one game per block entity: a multi-block table counts once, by its main cell (MT-DESIGN §6)
							if (Game.byBlockId(b.name()) != null && "main".equals(b.properties().getOrDefault("part", "main"))) {
								games++;
							}
						}
					}
				}
			}
		}
		if (anchors != 1) {
			out.add(anchors + " casino anchors (need exactly 1, in the entrance)");
		}
		if (npcs > MAX_NPCS) {
			out.add(npcs + " NPCs (max " + MAX_NPCS + ")");
		}
		if (chests > MAX_CHESTS) {
			out.add(chests + " chests (max " + MAX_CHESTS + ")");
		}
		if (games > MAX_GAME_BLOCKS) {
			out.add(games + " game blocks (max " + MAX_GAME_BLOCKS + ")");
		}
		if (cells > MAX_CELLS) {
			out.add(cells + " non-void cells (max " + MAX_CELLS + ")");
		}
		return out;
	}

	/** Game families whose blocks appear in a layout's grid. */
	public static Set<Game.Family> families(Layout l) {
		Set<Game.Family> f = EnumSet.noneOf(Game.Family.class);
		Vec size = l.size();
		for (int x = 0; x < size.x(); x++) {
			for (int y = 0; y < size.y(); y++) {
				for (int z = 0; z < size.z(); z++) {
					BlockSpec b = l.grid().get(x, y, z);
					Game g = b == null ? null : Game.byBlockId(b.name());
					if (g != null) {
						f.add(g.family());
					}
				}
			}
		}
		return f;
	}

	private static List<Layout.Jigsaw> sortedJigsaws(Layout l) {
		List<Layout.Jigsaw> js = new ArrayList<>(l.jigsaws());
		js.sort(Comparator.comparingInt((Layout.Jigsaw j) -> j.pos().y()).thenComparingInt(j -> j.pos().z()).thenComparingInt(j -> j.pos().x()));
		return js;
	}

	/** {@code north_up} → NORTH; empty for vertical connectors ({@code up_north}, {@code down_south}). */
	static Optional<Facing> horizontal(String orientation) {
		int i = orientation.indexOf('_');
		if (i < 0 || !orientation.substring(i + 1).equals("up")) {
			return Optional.empty();
		}
		try {
			Facing f = Facing.byId(orientation.substring(0, i));
			return Optional.of(f);
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
