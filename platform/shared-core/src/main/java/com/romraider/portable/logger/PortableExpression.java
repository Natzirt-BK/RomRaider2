/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

/** Compiled, dependency-free evaluator for logger conversion expressions. */
public final class PortableExpression {
    private final String source;
    private final Node root;

    private PortableExpression(String source, Node root) {
        this.source = source;
        this.root = root;
    }

    public static PortableExpression compile(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Logger conversion expression is required");
        }
        Parser parser = new Parser(expression);
        Node root = parser.parseExpression();
        parser.skipSpaces();
        if (!parser.atEnd()) parser.fail("Unexpected token");
        return new PortableExpression(expression, root);
    }

    public double evaluate(double x) {
        return root.evaluate(x);
    }

    public String getSource() {
        return source;
    }

    private interface Node {
        double evaluate(double x);
    }

    private static final class Parser {
        private final String source;
        private int offset;

        private Parser(String source) {
            this.source = source;
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
            if (consume("+")) return parseUnary();
            if (consume("-")) {
                Node value = parseUnary();
                return x -> -value.evaluate(x);
            }
            if (consume("!")) {
                Node value = parseUnary();
                return x -> truth(value.evaluate(x)) ? 0.0 : 1.0;
            }
            return parsePrimary();
        }

        private Node parsePrimary() {
            skipSpaces();
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
            if (identifier.isEmpty()) fail("Expected a number, x, or function");
            if ("x".equalsIgnoreCase(identifier)) return x -> x;
            if (!consume("(")) {
                fail("Unsupported logger variable " + identifier);
            }
            Node first = parseExpression();
            require(",");
            Node second = parseExpression();
            require(",");
            Node third = parseExpression();
            require(")");
            if ("if".equalsIgnoreCase(identifier)) {
                return x -> truth(first.evaluate(x))
                        ? second.evaluate(x) : third.evaluate(x);
            }
            if ("BitWise".equalsIgnoreCase(identifier)) {
                return x -> bitWise(first.evaluate(x), second.evaluate(x),
                        third.evaluate(x));
            }
            fail("Unsupported logger function " + identifier);
            return null;
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
        switch (operation) {
            case "+": return x -> left.evaluate(x) + right.evaluate(x);
            case "-": return x -> left.evaluate(x) - right.evaluate(x);
            case "*": return x -> left.evaluate(x) * right.evaluate(x);
            case "/": return x -> left.evaluate(x) / right.evaluate(x);
            case "%": return x -> left.evaluate(x) % right.evaluate(x);
            case "==": return x -> bool(left.evaluate(x) == right.evaluate(x));
            case "!=": return x -> bool(left.evaluate(x) != right.evaluate(x));
            case "<": return x -> bool(left.evaluate(x) < right.evaluate(x));
            case "<=": return x -> bool(left.evaluate(x) <= right.evaluate(x));
            case ">": return x -> bool(left.evaluate(x) > right.evaluate(x));
            case ">=": return x -> bool(left.evaluate(x) >= right.evaluate(x));
            default: throw new IllegalArgumentException("Unknown operation " + operation);
        }
    }

    private static double bitWise(double maskValue, double variableValue,
            double operationValue) {
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
}
