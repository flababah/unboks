package unboks

import unboks.internal.RefCountsImpl

/*
	private static Constant<?> createConstant(Object cst) {
		if (cst instanceof Integer) {
			return new Constant.Integer((Integer) cst);
		} else if (cst instanceof Float) {
			return new Constant.Float((Float) cst);
		} else if (cst instanceof Long) {
			return new Constant.Long((Long) cst);
		} else if (cst instanceof Double) {
			return new Constant.Double((Double) cst);
		} else if (cst instanceof String) {
			return new Constant.String((String) cst);
		} else if (cst instanceof Type) {
			Type type = (Type) cst;
			switch (type.getSort()) {
			case Type.OBJECT:
				return new Constant.Object(Thing.reference(type.getDescriptor()));
			case Type.ARRAY:
			case Type.METHOD:
			default:
				throw new RuntimeException("Unknown constant type: " + cst.getClass());
			}
		} else if (cst instanceof Handle) {
			throw new RuntimeException("TODO");
		} else {
			throw new RuntimeException("Unknown constant type: " + cst.getClass());
		}
	}
 */

sealed class Constant<T>(val value: T) : Def {
	override var name: String
		get() = value.toString() // TODO Display strings with quotes "...", etc...
		set(value) { /* No-op */ }

	override val uses: RefCounts<Use> = RefCountsImpl() // TODO Cache same values -- per FlowGraph
}

class IntConst(value: Int) : Constant<Int>(value) {
	override val type = INT
}

