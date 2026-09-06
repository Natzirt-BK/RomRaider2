/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2016 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.romraider.util;

import org.nfunk.jep.JEP;

import java.util.Collections;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JEPUtil {
	@SuppressWarnings("serial")
	static class LRUCache<K, V> extends LinkedHashMap<K, V> {
		private int cacheSize;

		public LRUCache(int cacheSize) {
			super(32, 0.75f, true);
			this.cacheSize = cacheSize;
		}

		@Override
		protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
			return size() > cacheSize;
		}
	};

	private static final Map<ExpressionKey, JEP> parserCache = new LRUCache<ExpressionKey, JEP>(32);

	/** Parsed symbol bindings belong to the complete variable-name set, not just the expression. */
	private static final class ExpressionKey {
		private final String expression;
		private final List<String> names;
		ExpressionKey(String expression, Map<String, Double> values) {
			this.expression = Objects.requireNonNull(expression, "expression");
			this.names = new ArrayList<String>(values.keySet());
			Collections.sort(names);
		}
		@Override public boolean equals(Object other) {
			if (!(other instanceof ExpressionKey)) return false;
			ExpressionKey key = (ExpressionKey) other;
			return expression.equals(key.expression) && names.equals(key.names);
		}
		@Override public int hashCode() { return 31 * expression.hashCode() + names.hashCode(); }
	}

	public static synchronized double evaluate(String expression, double value) {
		return evaluate(expression, Collections.singletonMap("x", value));
	}

	public static synchronized double evaluate(String expression, Map<String, Double> valueMap) {
		Map<String, Double> values = new LinkedHashMap<String, Double>(valueMap);
		ExpressionKey key = new ExpressionKey(expression, values);
		JEP parser = parserCache.get(key);
		if (parser == null) {
			parser = new JEP();
			parser.addStandardFunctions();
			parser.addFunction("BitWise", new BitWise());
			parser.initSymTab(); // clear the contents of the symbol table
			for (String id : values.keySet()) {
				parser.addVariable(id, values.get(id) == null ? Double.NaN : values.get(id));
			}
			parser.parseExpression(expression);
			parserCache.put(key, parser);
		} else {

			for (String id : values.keySet()) {
				parser.setVarValue(id, values.get(id) == null ? Double.NaN : values.get(id));
			}

		}
		return parser.getValue();
	}
}
