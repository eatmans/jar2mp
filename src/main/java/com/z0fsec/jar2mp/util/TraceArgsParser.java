package com.z0fsec.jar2mp.util;

import java.util.ArrayList;
import java.util.List;

public final class TraceArgsParser {

    private TraceArgsParser() {
    }

    public static List<String> parse(String value) {
        List<String> parsed = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) {
            return parsed;
        }

        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaping = false;

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (Character.isWhitespace(ch) && !inSingleQuote && !inDoubleQuote) {
                addTraceArg(parsed, current);
                continue;
            }
            current.append(ch);
        }

        if (escaping) {
            current.append('\\');
        }
        addTraceArg(parsed, current);
        return parsed;
    }

    private static void addTraceArg(List<String> parsed, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        parsed.add(current.toString());
        current.setLength(0);
    }
}
