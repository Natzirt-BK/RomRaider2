/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/** Compiled, dependency-free evaluator for logger conversion expressions. */
public final class PortableExpression {
    private final String source;
    private final Node root;
    private final Set<String> variables;
    private final boolean raw;

    private PortableExpression(String source, Node root, Set<String> variables, boolean raw) {
        this.source = source;
        this.root = root;
        this.variables = Collections.unmodifiableSet(new LinkedHashSet<>(variables));
        this.raw = raw;
    }

    public static PortableExpression compile(String expression) {
        return compile(expression, Collections.singleton("x"), true);
    }

    /** Only declared dependencies may be referenced; bracket units are retained. */
    public static PortableExpression compile(String expression, Set<String> dependencies) {
        return compile(expression, dependencies, false);
    }

    private static PortableExpression compile(String expression, Set<String> dependencies, boolean raw) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Logger conversion expression is required");
        }
        if (expression.length() > 4096 || dependencies == null || dependencies.size() > 64) {
            throw new IllegalArgumentException("Logger expression exceeds its size/dependency limit");
        }
        Parser parser = new Parser(expression, dependencies, raw);
        Node root = parser.parseExpression();
        parser.skipSpaces();
        if (!parser.atEnd()) parser.fail("Unexpected token");
        return new PortableExpression(expression, root, parser.variables, raw);
    }

    public double evaluate(double x) {
        if (!raw) throw new IllegalStateException("Calculated expressions require named dependency values");
        return Double.isFinite(x) ? finite(root.evaluate(name -> x)) : Double.NaN;
    }

    public double evaluate(Map<String, Double> values) {
        if (values == null) throw new IllegalArgumentException("Dependency values are required");
        for (String name : variables) {
            Double value = values.get(name);
            if (value == null || !Double.isFinite(value)) return Double.NaN;
        }
        return finite(root.evaluate(name -> values.get(name)));
    }

    public Set<String> getVariables() { return variables; }

    public String getSource() {
        return source;
    }

    private interface Node {
        double evaluate(ToDoubleFunction<String> values);
    }

    private static final class Parser {
        private final String source;
        private final Set<String> dependencies;
        private final Set<String> variables = new LinkedHashSet<>();
        private final boolean raw;
        private int offset;
        private int depth, nodes;

        private Parser(String source, Set<String> dependencies, boolean raw) {
            this.source = source;
            this.dependencies = new LinkedHashSet<>(dependencies);
            this.raw = raw;
        }

        private Node parseExpression() {
            return parseEquality();
        }

        private Node parseEquality() {
            Node value = parseComparison();
            while (true) {
                if (consume("==")) value = binary("==", value, parseComparison());
                else if (consume("!=")) value = binary("!=", value, parseComparison());
                else return value;
            }
        }

        private Node parseComparison() {
            Node value = parseSum();
            while (true) {
                if (consume("<=")) value = binary("<=", value, parseSum());
                else if (consume(">=")) value = binary(">=", value, parseSum());
                else if (consume("<")) value = binary("<", value, parseSum());
                else if (consume(">")) value = binary(">", value, parseSum());
                else return value;
            }
        }

        private Node parseSum() {
            Node value = parseProduct();
            while (true) {
                if (consume("+")) value = binary("+", value, parseProduct());
                else if (consume("-")) value = binary("-", value, parseProduct());
                else return value;
            }
        }

        private Node parseProduct() {
            Node value = parseUnary();
            while (true) {
                if (consume("*")) value = binary("*", value, parseUnary());
                else if (consume("/")) value = binary("/", value, parseUnary());
                else if (consume("%")) value = binary("%", value, parseUnary());
                else return value;
            }
        }

        private Node parseUnary() {
            if (++depth > 64 || ++nodes > 256) fail("Logger expression is too complex");
            try { return unaryValue(); }
            finally { depth--; }
        }

        private Node unaryValue() {
            if (consume("+")) return parseUnary();
            if (consume("-")) {
                Node value = parseUnary();
                return x -> finite(-value.evaluate(x));
            }
            if (consume("!")) {
                Node value = parseUnary();
                return x -> {
                    double number = value.evaluate(x);
                    return Double.isFinite(number) ? (truth(number) ? 0.0 : 1.0) : Double.NaN;
                };
            }
            return parsePrimary();
        }

        private Node parsePrimary() {
            skipSpaces();
            if (consume("[")) {
                if (raw) fail("Unit-bound references require a calculated parameter");
                int end = source.indexOf(']', offset);
                if (end < 0) fail("Unclosed dependency reference");
                String reference = source.substring(offset, end);
                int colon = reference.indexOf(':');
                if (colon <= 0 || colon == reference.length() - 1) fail("Expected [parameter:units]");
                String id = reference.substring(0, colon).trim(), units = reference.substring(colon + 1).trim();
                if (units.isEmpty()) fail("Dependency units are required");
                offset = end + 1;
                return variable("[" + id + ":" + units + "]", id);
            }
            if (consume("(")) {
                Node value = parseExpression();
                require(")");
                return value;
            }
            if (offset < source.length()
                    && (Character.isDigit(source.charAt(offset))
                    || source.charAt(offset) == '.')) {
                return number();
            }
            String identifier = identifier();
            if (identifier.isEmpty()) fail("Expected a number, variable, or function");
            if (raw && "x".equalsIgnoreCase(identifier)) return variable("x", "x");
            if (!consume("(")) {
                return variable(identifier, identifier);
            }
            Node first = parseExpression();
            require(",");
            Node second = parseExpression();
            require(",");
            Node third = parseExpression();
            require(")");
            if ("if".equalsIgnoreCase(identifier)) {
                return x -> {
                    double condition = first.evaluate(x);
                    if (!Double.isFinite(condition)) return Double.NaN;
                    return truth(condition) ? second.evaluate(x) : third.evaluate(x);
                };
            }
            if ("BitWise".equalsIgnoreCase(identifier)) {
                return x -> bitWise(first.evaluate(x), second.evaluate(x),
                        third.evaluate(x));
            }
            fail("Unsupported logger function " + identifier);
            return null;
        }

        private Node variable(String reference, String id) {
            if (!dependencies.contains(id)) fail("Undeclared logger dependency " + id);
            variables.add(reference);
            return values -> values.applyAsDouble(reference);
        }

        private Node number() {
            skipSpaces();
            int start = offset;
            boolean exponent = false;
            while (offset < source.length()) {
                char current = source.charAt(offset);
                if (Character.isDigit(current) || current == '.') {
                    offset++;
                } else if ((current == 'e' || current == 'E') && !exponent) {
                    exponent = true;
                    offset++;
                    if (offset < source.length()
                            && (source.charAt(offset) == '+'
                            || source.charAt(offset) == '-')) offset++;
                } else {
                    break;
                }
            }
            try {
                double value = Double.parseDouble(source.substring(start, offset));
                return x -> value;
            } catch (NumberFormatException ex) {
                fail("Invalid number");
                return null;
            }
        }

        private String identifier() {
            skipSpaces();
            int start = offset;
            while (offset < source.length()) {
                char current = source.charAt(offset);
                if (!Character.isLetterOrDigit(current) && current != '_') break;
                offset++;
            }
            return source.substring(start, offset);
        }

        private boolean consume(String token) {
            skipSpaces();
            if (!source.regionMatches(offset, token, 0, token.length())) return false;
            offset += token.length();
            return true;
        }

        private void require(String token) {
            if (!consume(token)) fail("Expected '" + token + "'");
        }

        private void skipSpaces() {
            while (offset < source.length()
                    && Character.isWhitespace(source.charAt(offset))) offset++;
        }

        private boolean atEnd() {
            return offset == source.length();
        }

        private void fail(String message) {
            throw new IllegalArgumentException(message + " at position "
                    + offset + " in logger expression: " + source);
        }
    }

    private static Node binary(String operation, Node left, Node right) {
        return values -> {
            double a = left.evaluate(values), b = right.evaluate(values);
            if (!Double.isFinite(a) || !Double.isFinite(b)) return Double.NaN;
            switch (operation) {
                case "+": return finite(a + b);
                case "-": return finite(a - b);
                case "*": return finite(a * b);
                case "/": return finite(a / b);
                case "%": return finite(a % b);
                case "==": return bool(a == b);
                case "!=": return bool(a != b);
                case "<": return bool(a < b);
                case "<=": return bool(a <= b);
                case ">": return bool(a > b);
                case ">=": return bool(a >= b);
                default: throw new IllegalArgumentException("Unknown operation " + operation);
            }
        };
    }

    private static double bitWise(double maskValue, double variableValue,
            double operationValue) {
        if (!Double.isFinite(maskValue) || !Double.isFinite(variableValue) || !Double.isFinite(operationValue)) return Double.NaN;
        int mask = (int) maskValue;
        int variable = (int) variableValue;
        switch ((int) operationValue) {
            case 1: return variable & mask;
            case 2: return variable | mask;
            case 3: return variable ^ mask;
            case 4: return variable << mask;
            case 5: return variable >> mask;
            case 6: return variable >>> mask;
            case 7: return ~variable;
            default: return 0.0;
        }
    }

    private static boolean truth(double value) {
        return value != 0.0 && !Double.isNaN(value);
    }

    private static double bool(boolean value) {
        return value ? 1.0 : 0.0;
    }

    private static double finite(double value) { return Double.isFinite(value) ? value : Double.NaN; }
}
