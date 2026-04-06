package com.lovable.ai_service.validation;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ComponentStructureRepairService {

    public String fixDuplicateComponentDeclaration(String content) {
        if (content == null || content.isBlank()) return content;

        // Detect duplicate function/component declarations by name.
        Pattern pattern = Pattern.compile(
                "(?m)^(?:export\\s+default\\s+)?function\\s+([A-Z][A-Za-z0-9_]*)\\s*\\(");
        Matcher matcher = pattern.matcher(content);

        Set<String> seen = new HashSet<>();
        List<int[]> duplicateRanges = new ArrayList<>();

        while (matcher.find()) {
            String name = matcher.group(1);
            if (!seen.add(name)) {
                int start = matcher.start();
                int end = findFunctionBlockEnd(content, matcher.end());
                if (end > start) {
                    duplicateRanges.add(new int[]{start, end});
                }
            }
        }

        if (duplicateRanges.isEmpty()) return content;

        duplicateRanges.sort((a, b) -> Integer.compare(b[0], a[0]));
        StringBuilder sb = new StringBuilder(content);
        for (int[] range : duplicateRanges) {
            sb.delete(range[0], range[1]);
        }

        return sb.toString().replaceAll("(?m)^[ \\t]*\\n", "").trim() + "\n";
    }

    private int findFunctionBlockEnd(String content, int scanStart) {
        int braceStart = content.indexOf('{', scanStart);
        if (braceStart < 0) return content.length();

        int depth = 0;
        boolean inString = false;
        char stringChar = 0;
        boolean escaped = false;

        for (int i = braceStart; i < content.length(); i++) {
            char c = content.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                if (c == stringChar) {
                    inString = false;
                }
                continue;
            }

            if (c == '"' || c == '\'' || c == '`') {
                inString = true;
                stringChar = c;
                continue;
            }

            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    int j = i + 1;
                    while (j < content.length() && Character.isWhitespace(content.charAt(j))) {
                        j++;
                    }
                    if (j < content.length() && content.charAt(j) == ';') j++;
                    return j;
                }
            }
        }

        return content.length();
    }
}