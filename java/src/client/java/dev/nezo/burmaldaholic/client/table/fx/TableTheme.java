package dev.nezo.burmaldaholic.client.table.fx;

/**
 * Location themes of the table screens (docs/design/visual/tables.md §2.1): the casino the table stands in. Resolved by
 * the server ({@code theme} 0 village, 1 bastion, 2 end in the state / update tag); purely visual.
 */
public enum TableTheme {
	VILLAGE("village", 0xFFEDE2C4, 0xFF0E2E1C, 0xFF1E5E3A),
	BASTION("bastion", 0xFFFFD640, 0xFF240608, 0xFF5A1418),
	END("end", 0xFFF4ECF8, 0xFF100822, 0xFF2A1A4C);

	public final String id;
	/** Layout line / runtime label colour on the felt. */
	public final int line;
	/** Deepest felt shade (label shadows). */
	public final int feltDeep;
	public final int felt;

	TableTheme(String id, int line, int feltDeep, int felt) {
		this.id = id;
		this.line = line;
		this.feltDeep = feltDeep;
		this.felt = felt;
	}

	public static TableTheme of(int index) {
		TableTheme[] v = values();
		return index >= 0 && index < v.length ? v[index] : VILLAGE;
	}
}
