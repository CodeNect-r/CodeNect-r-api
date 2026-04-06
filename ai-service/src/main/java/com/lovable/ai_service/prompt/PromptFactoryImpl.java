package com.lovable.ai_service.prompt;

import com.lovable.ai_service.dto.DesignMemory;
import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.dto.GenerationMode;
import com.lovable.ai_service.dto.PromptContext;
import com.lovable.ai_service.dto.UICriticReport;
import com.lovable.ai_service.dto.UIFixSuggestion;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class PromptFactoryImpl implements PromptFactory {

    private static final String CSS_ENGINE_RULES = """
══════════════════════════════════════════════════════════
🚨 CSS ENGINE RULES (HIGHEST PRIORITY)
══════════════════════════════════════════════════════════

- Tailwind v4: ALL styling MUST be in JSX
- CSS file must be MINIMAL only

STRICTLY FORBIDDEN:
- @apply
  border-border
  bg-background
  text-foreground
- duplicate @import
- complex CSS logic
- custom tokens

ALLOWED:
- @import "tailwindcss";
- minimal layout resets only

GOAL:
Zero CSS bugs. All styling handled via Tailwind classes.

══════════════════════════════════════════════════════════
""";

    private static final String CRITICAL_JSX_RULES = """
        ══════════════════════════════════════════════════════════
        🚨 CRITICAL JSX BUILD RULES — VIOLATIONS BREAK THE BUILD
        ══════════════════════════════════════════════════════════
        1. Close all template literals correctly.
        2. No unterminated strings.
        3. export default is mandatory for component files.
        4. Only one default export per file.
        5. No duplicate component/function names.
        6. All imports must resolve to real files or declared packages.
        7. Never use @apply in Tailwind v4 CSS files.
        ══════════════════════════════════════════════════════════
        """;
    private static final String PREMIUM_UI_RULES = """
══════════════════════════════════════════════════════════
🔥 LOVABLE-LEVEL UI DESIGN SYSTEM (STRICT)
══════════════════════════════════════════════════════════

🎯 GOAL:
Generate UI indistinguishable from Lovable / Bolt

══════════════════════════════════════════════════════════
🎨 VISUAL STYLE
══════════════════════════════════════════════════════════

- Dark theme FIRST
- Clean, modern SaaS UI
- Glassmorphism + subtle borders
- Soft shadows
- Strong hierarchy

══════════════════════════════════════════════════════════
📐 LAYOUT
══════════════════════════════════════════════════════════

- max-w-7xl mx-auto px-6 py-8
- grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6
- space-y-6

══════════════════════════════════════════════════════════
🧱 CARDS (VERY IMPORTANT)
══════════════════════════════════════════════════════════

- bg-gray-900/70 backdrop-blur-xl
- border border-gray-800
- rounded-2xl p-6
- shadow-sm hover:shadow-md
- hover:border-gray-700
- transition-all duration-200

══════════════════════════════════════════════════════════
🔘 BUTTONS
══════════════════════════════════════════════════════════

Primary:
- bg-white text-black
- hover:bg-gray-200
- rounded-xl px-4 py-2

Secondary:
- border border-gray-700
- hover:bg-gray-800
- rounded-xl px-4 py-2

══════════════════════════════════════════════════════════
🔤 TYPOGRAPHY
══════════════════════════════════════════════════════════

- text-2xl font-semibold text-white
- text-lg text-gray-300
- text-sm text-gray-400

══════════════════════════════════════════════════════════
⚡ ANIMATIONS (MANDATORY)
══════════════════════════════════════════════════════════

- Use framer-motion

Examples:
- fade-in
- slide-up
- stagger children

Example:

<motion.div
  initial={{ opacity: 0, y: 10 }}
  animate={{ opacity: 1, y: 0 }}
  transition={{ duration: 0.3 }}
>

══════════════════════════════════════════════════════════
✨ INTERACTIONS
══════════════════════════════════════════════════════════

- hover:scale-[1.02]
- hover:bg-gray-800
- transition-all duration-200

══════════════════════════════════════════════════════════
🧩 STRUCTURE
══════════════════════════════════════════════════════════

- Sidebar / Navbar
- Content grid
- Cards + sections

══════════════════════════════════════════════════════════
🚫 FORBIDDEN
══════════════════════════════════════════════════════════

- plain UI
- no spacing
- no animations
- random colors
- inline styles

══════════════════════════════════════════════════════════
""";
    private static final String TAILWIND_V4_RULES = """
        ══════════════════════════════════════════════════════════
        🎨 TAILWIND v4 RULES
        ══════════════════════════════════════════════════════════
        - CSS entry file MUST start with: @import "tailwindcss";
        - Never use @tailwind base/components/utilities in v4
        - Prefer zero @apply usage
        - vite.config.js must include tailwindcss() plugin
        - package.json devDeps must include tailwindcss and @tailwindcss/vite
        ══════════════════════════════════════════════════════════
        """;

    private static final String TAILWIND_V3_RULES = """
        ══════════════════════════════════════════════════════════
        🎨 TAILWIND v3 RULES
        ══════════════════════════════════════════════════════════
        - CSS entry file must include:
          @tailwind base;
          @tailwind components;
          @tailwind utilities;
        - Never use @import "tailwindcss" in v3
        - tailwind.config.js and postcss.config.js are required
        ══════════════════════════════════════════════════════════
        """;
    private static final String TAILWIND_CLASS_RULES= """
            TAILWIND CLASS RULES (STRICT):
            
            - ONLY use standard Tailwind classes
            - NEVER invent custom tokens like:
              border-border
              bg-background
              text-foreground
            
            - ALWAYS use:
              border-gray-800
              bg-gray-900
              text-gray-400
            
            - DO NOT assume custom theme exists
            """;
    private static final String COMPONENT_SYSTEM_RULES = """
══════════════════════════════════════════════════════════
🧩 COMPONENT SYSTEM (MANDATORY - LOVABLE LEVEL)
══════════════════════════════════════════════════════════

🎯 GOAL:
Reusable + premium + consistent UI across entire app

══════════════════════════════════════════════════════════
📁 STRUCTURE
══════════════════════════════════════════════════════════

- All reusable components MUST be in:
  /src/components/ui/

- NEVER duplicate UI inside pages

══════════════════════════════════════════════════════════
🧱 REQUIRED COMPONENTS
══════════════════════════════════════════════════════════

- components/ui/Button.jsx
- components/ui/Card.jsx
- components/ui/Input.jsx

══════════════════════════════════════════════════════════
📐 DESIGN RULES
══════════════════════════════════════════════════════════

- Components MUST follow global theme (colors, spacing)
- Components MUST feel premium (not basic)
- Use consistent padding, border, radius

══════════════════════════════════════════════════════════
✨ INTERACTIONS (MANDATORY)
══════════════════════════════════════════════════════════

- Add hover states
- Add transitions
- Add subtle scale effects

Example:
- hover:scale-[1.02]
- transition-all duration-200

══════════════════════════════════════════════════════════
⚡ ANIMATIONS (MANDATORY)
══════════════════════════════════════════════════════════

- Use framer-motion where appropriate
- Cards should fade/slide in
- Buttons can have hover animations

══════════════════════════════════════════════════════════
🔘 BUTTON RULES
══════════════════════════════════════════════════════════

- Must support variants:
  primary / secondary

- MUST use theme colors (NOT fixed gray)

- Example:
  - Use ONLY real Tailwind colors
            
  PRIMARY:
  - bg-blue-600 hover:bg-blue-500 text-white
            
  SECONDARY:
  - border border-gray-700 hover:bg-gray-800 text-white

══════════════════════════════════════════════════════════
🧱 CARD RULES
══════════════════════════════════════════════════════════

- rounded-2xl p-6
- border + subtle background
- hover:border change
- shadow-sm → hover:shadow-md

══════════════════════════════════════════════════════════
📝 INPUT RULES
══════════════════════════════════════════════════════════

- Clean border
- Proper padding
- Focus ring using theme color

══════════════════════════════════════════════════════════
🚫 FORBIDDEN
══════════════════════════════════════════════════════════

- hardcoded colors (like only gray)
- duplicate UI
- NO bg-primary, border-muted etc
- plain/basic components
- missing hover/interaction

══════════════════════════════════════════════════════════
""";

    private static final String THEME_ENGINE_RULES = """
══════════════════════════════════════════════════════════
🎨 DYNAMIC THEME ENGINE (CRITICAL)
══════════════════════════════════════════════════════════

The UI color theme MUST adapt based on the product domain.

DO NOT use same gray theme for all apps.

══════════════════════════════════════════════════════════
🎯 THEME MAPPING (MANDATORY)
══════════════════════════════════════════════════════════

- Healthcare / Hospital:
  blue, cyan, teal (clean, trustworthy)

- Finance / Banking:
  green, emerald (growth, trust)

- SaaS / Dashboard:
  gray + indigo (modern)

- Education:
  purple, indigo (creative)

- E-commerce:
  orange, pink (engaging)

- AI / Tech:
  indigo, violet, blue (futuristic)

══════════════════════════════════════════════════════════
📌 RULES
══════════════════════════════════════════════════════════

- Define a PRIMARY color
- Use it consistently across:
  - buttons
  - focus rings
  - highlights
  - icons

- Example:
  bg-blue-600 hover:bg-blue-500
  focus:ring-blue-500

- NEVER fallback to only gray UI

══════════════════════════════════════════════════════════
🚨 IMPORTANT
══════════════════════════════════════════════════════════

- Theme must be consistent across ALL files
- Components MUST use theme colors
- Do NOT mix random colors

══════════════════════════════════════════════════════════
""";
    private static final String ROUTER_RULES = """
        ══════════════════════════════════════════════════════════
        🔴 ROUTER RULES
        ══════════════════════════════════════════════════════════
        - BrowserRouter lives in src/main.jsx only
        - App.jsx uses Routes/Route only
        - Never wrap App.jsx in BrowserRouter/Router
        ══════════════════════════════════════════════════════════
        """;

    private static final String SAFETY_RULES = """
        ══════════════════════════════════════════════════════════
        🛡 RUNTIME SAFETY RULES
        ══════════════════════════════════════════════════════════
        - Safe destructuring with fallbacks
        - Guard arrays before map/filter
        - Use optional chaining for dynamic data
        - Add empty state guards when rendering dynamic lists
        - Never create/import ErrorBoundary
        ══════════════════════════════════════════════════════════
        """;

    @Override
    public String buildSystemPrompt(GenerationMode mode) {
        if (mode == GenerationMode.INITIAL) {
            return """
            You are a senior frontend engineer and premium UI designer.

            Generate production-ready frontend code.

            STRICT RULES:
            - Return ONLY valid JSON
            - No markdown
            - No explanations
            - No invented imports
            - Preserve framework correctness

            DEPENDENCY RULES (CRITICAL):
            - ALWAYS use correct package names (e.g., lucide-react, NOT licude-react)
            - NEVER generate invalid versions like ^latest
            - ALWAYS use valid semver (e.g., "^0.263.1")
            - ONLY include dependencies that are actually used in the code
            - NEVER hallucinate packages

            CODE QUALITY:
            - Output must compile without errors
            - No missing imports
            - No broken JSX
            - No invalid Tailwind classes

            DESIGN:
            - Use modern premium SaaS design quality
            - Follow DESIGN SYSTEM strictly
            - UI must be consistent and production-grade
            """;
        }

        return """
        You are a senior frontend engineer modifying an existing frontend project.

        STRICT RULES:
        - Return ONLY valid JSON
        - No markdown
        - No explanations
        - Modify only what is requested
        - Preserve framework/build correctness

        DEPENDENCY RULES:
        - Do NOT introduce incorrect packages
        - Do NOT use invalid versions like ^latest
        - Keep dependencies valid and minimal
        """;
    }
    @Override
    public String buildPlanningSystemPrompt() {
        return """
            You are a frontend project planner.
            Return ONLY valid JSON:
            { "framework": "...", "files": ["..."] }
            """;
    }

    @Override
    public String buildPlanningPrompt(String userPrompt, String framework) {
        return """
            USER REQUEST:
            %s

            FORCED FRAMEWORK:
            %s
            
            ALWAYS INCLUDE:
            - src/components/ui/Button.jsx
            - src/components/ui/Card.jsx
            - src/components/ui/Input.jsx
            
            Plan a production-ready frontend structure.
            Return 6-14 files.
            Include the correct CSS entry file.
            Ensure all planned imports can resolve.
            Return JSON only.
            """.formatted(userPrompt, framework);
    }
    private static final String PREMIUM_LIBRARIES_RULES = """
══════════════════════════════════════════════════════════
🚀 PREMIUM UI LIBRARIES (MANDATORY)
══════════════════════════════════════════════════════════

ALWAYS USE:

1. Animation:
- framer-motion

2. Icons:
- lucide-react ONLY

3. Utility:
- clsx (optional)

RULES:
- Import ONLY when used
- NEVER hallucinate packages
- ALWAYS use correct names

EXAMPLES:

import { motion } from "framer-motion";
import { ArrowRight } from "lucide-react";

══════════════════════════════════════════════════════════
""";

    @Override
    public String buildSingleFilePrompt(PromptContext ctx, String filePath) {

        String tailwindRules = isV4Framework(ctx.getFramework())
                ? TAILWIND_V4_RULES
                : TAILWIND_V3_RULES;

        String design = buildDesignBlock(ctx.getDesignMemory());
        String context = compressContextFromFiles(ctx.getExistingFiles());
        String intent = buildIntentBlock(ctx);
        String artifacts = buildArtifactsBlock(ctx);
        String fileSpecificRules = getFileSpecificRules(filePath, ctx.getFramework());

        boolean needsRouterRules =
                filePath.contains("App.jsx")
                        || filePath.contains("App.tsx")
                        || filePath.contains("main.jsx")
                        || filePath.contains("main.tsx");

        boolean needsSafetyRules =
                filePath.endsWith(".jsx")
                        || filePath.endsWith(".tsx")
                        || filePath.endsWith(".vue");

        return """
══════════════════════════════════════════════════════════
🎯 TASK
══════════════════════════════════════════════════════════
Generate a COMPLETE production-ready file.

STRICT RULE:
- Do NOT break build
- Do NOT invent imports
- Do NOT output partial code

══════════════════════════════════════════════════════════
🧾 USER REQUEST
══════════════════════════════════════════════════════════
%s

══════════════════════════════════════════════════════════
📄 TARGET FILE
══════════════════════════════════════════════════════════
%s

══════════════════════════════════════════════════════════
⚙️ FRAMEWORK
══════════════════════════════════════════════════════════
%s

══════════════════════════════════════════════════════════
🧠 INTENT
══════════════════════════════════════════════════════════
%s

══════════════════════════════════════════════════════════
📦 ARTIFACT PLAN
══════════════════════════════════════════════════════════
%s

══════════════════════════════════════════════════════════
📚 EXISTING CONTEXT (REFERENCE ONLY)
══════════════════════════════════════════════════════════
%s

══════════════════════════════════════════════════════════
🎨 DESIGN (FOLLOW STRICTLY)
══════════════════════════════════════════════════════════
%s

%s

══════════════════════════════════════════════════════════
🧩 COMPONENT RULES (HIGH PRIORITY)
══════════════════════════════════════════════════════════
%s

══════════════════════════════════════════════════════════
🎨 TAILWIND RULES + CSS ENGINE RULES (STRICT)
══════════════════════════════════════════════════════════
%s

%s

══════════════════════════════════════════════════════════
📜 FILE RULES (HIGHEST PRIORITY)
══════════════════════════════════════════════════════════
%s

══════════════════════════════════════════════════════════
🛡 BUILD SAFETY (CRITICAL)
══════════════════════════════════════════════════════════
%s

%s

%s

══════════════════════════════════════════════════════════
🚨 FINAL INSTRUCTIONS
══════════════════════════════════════════════════════════
1. Output MUST compile
2. Output MUST be complete
3. No missing imports
4. No invalid Tailwind classes
5. No placeholder code

══════════════════════════════════════════════════════════
📤 OUTPUT
══════════════════════════════════════════════════════════
{
  "path": "%s",
  "content": "FULL FILE CONTENT ONLY"
}
""".formatted(
                ctx.getUserPrompt(),
                filePath,
                ctx.getFramework(),
                intent,
                artifacts,
                context,
                design,
                COMPONENT_SYSTEM_RULES,
                PREMIUM_LIBRARIES_RULES,
                tailwindRules,
                CSS_ENGINE_RULES,
                TAILWIND_CLASS_RULES,
                fileSpecificRules,
                CRITICAL_JSX_RULES,
                needsRouterRules ? ROUTER_RULES : "",
                needsSafetyRules ? SAFETY_RULES : "",
                filePath
        );
    }
    @Override
    public String buildCssAuditPrompt(List<GeneratedFile> files, String framework, String userPrompt) {
        String cssPath = getCssEntryPath(framework);
        boolean isV4 = isV4Framework(framework);

        Set<String> classes = new LinkedHashSet<>();
        StringBuilder previews = new StringBuilder();

        for (GeneratedFile file : files) {
            String path = file.getPath();
            // Filter for relevant frontend files
            if (!(path.endsWith(".jsx") || path.endsWith(".tsx")
                    || path.endsWith(".vue") || path.endsWith(".html"))) {
                continue;
            }


            previews.append("FILE: ").append(path).append("\n");
            String[] lines = file.getContent().split("\n");
            for (int i = 0; i < Math.min(lines.length, 40); i++) {
                previews.append(lines[i]).append("\n");
            }
            previews.append("-----\n");
        }

        // Determine the base directive
        String directive = isV4
                ? "@import \"tailwindcss\";"
                : "@tailwind base;\n@tailwind components;\n@tailwind utilities;";

        // Logic for version-specific rule block
        String versionSpecificRules = isV4
                ? """
              - For Tailwind v4:
                - AVOID @apply unless absolutely necessary
                - Prefer utility classes directly in JSX
                - Keep CSS minimal
                - Use CSS variables for theme overrides if needed
              """
                : "- For Tailwind v3: Maintain standard configuration standards.";
        classes.removeIf(c ->
                c.contains("border-border") ||
                        c.contains("bg-background") ||
                        c.contains("text-foreground")
        );

        return """
    Generate the CSS entry file for this project.

    USER REQUEST:
    %s

    FRAMEWORK:
    %s

    FILE:
    %s

    REQUIRED FIRST LINES:
    %s

    FRAMEWORK SETUP:
    %s

    ══════════════════════════════════════════════════════════
    🚨 CRITICAL TAILWIND SAFETY (STRICT - MUST FOLLOW)
    ══════════════════════════════════════════════════════════

    - NEVER use: border-border, bg-background, text-foreground
    - NEVER write: @apply border-border; @apply bg-background; @apply text-foreground;
    - NEVER assume custom theme tokens exist
    - ONLY use VALID Tailwind classes (e.g., border-gray-200, bg-white, text-black)
    - If unsure, use safe defaults: bg-white/black, border-gray-200/800

    ══════════════════════════════════════════════════════════
    ⚡ VERSION SPECIFIC RULES (Tailwind %s)
    ══════════════════════════════════════════════════════════

    %s
    
    %s

    ══════════════════════════════════════════════════════════
    📦 DETECTED TAILWIND CLASSES
    ══════════════════════════════════════════════════════════

    %s

    ══════════════════════════════════════════════════════════
    📄 REFERENCE FILES
    ══════════════════════════════════════════════════════════

    %s

    ══════════════════════════════════════════════════════════
    OUTPUT (STRICT JSON ONLY)
    ══════════════════════════════════════════════════════════

    {
      "path": "%s",
      "content": "full css content"
    }
    """.formatted(
                userPrompt,
                framework,
                cssPath,
                directive,
                getTailwindSetup(framework),
                isV4 ? "v4" : "v3",      // Version label
                isV4 ? TAILWIND_V4_RULES : TAILWIND_V3_RULES, // Constant rules
                versionSpecificRules,    // Inline logic rules
                classes.isEmpty() ? "(none detected)" : String.join(", ", classes),
                previews.toString(),
                cssPath
        );
    }
    @Override
    public String buildSummarySystemPrompt() {
        return """
You are a product summarizer.

STRICT RULES:
- DO NOT include any code
- DO NOT include file content
- DO NOT include imports or JSX
- ONLY describe the app in plain English

OUTPUT STYLE:
- 4–6 bullet points
- Simple product description
- Mention:
  - what was built
  - main features
  - pages/components
  - framework used

BAD EXAMPLE (FORBIDDEN):
import React from 'react'

GOOD EXAMPLE:
- Built a modern dashboard application
- Includes pages for students, teachers, and classes
- Uses reusable UI components like cards and forms
- Styled with Tailwind CSS
- Built using React + Vite
""";
    }
    @Override
    public String buildSummaryPrompt(String userPrompt, String framework, List<GeneratedFile> files, GenerationMode mode) {

        long jsxCount = files.stream().filter(f -> f.getPath().endsWith(".jsx")).count();
        long pageCount = files.stream().filter(f -> f.getPath().contains("/pages/")).count();

        return """
USER REQUEST:
%s

FRAMEWORK:
%s

MODE:
%s

PROJECT INFO:
- Total files: %d
- UI Components: %d
- Pages: %d

INSTRUCTION:
Write a clean product-level summary.
Do NOT include code.
Do NOT include file content.
Do NOT mention file paths.

OUTPUT:
""".formatted(
                userPrompt,
                framework,
                mode,
                files.size(),
                jsxCount,
                pageCount
        );
    }
    @Override
    public String buildUiFixPrompt(PromptContext ctx, UICriticReport critique, String filePath) {
        String design = buildDesignBlock(ctx.getDesignMemory());
        String context = compressContextFromFiles(ctx.getExistingFiles());

        String suggestions = critique.getSuggestions() == null
                ? ""
                : critique.getSuggestions().stream()
                .filter(s -> filePath.equals(s.getFilePath()))
                .map(this::formatSuggestion)
                .collect(Collectors.joining("\n"));

        return """
            Improve this file's UI quality without breaking functionality.

            USER REQUEST:
            %s

            FILE:
            %s

            FRAMEWORK:
            %s

            EXISTING CONTEXT:
            %s

            DESIGN SYSTEM:
            %s

            UI CRITIQUE:
            - overall: %d
            - hierarchy: %d
            - spacing: %d
            - consistency: %d
            - premium: %d
            - responsiveness: %d

            TARGETED IMPROVEMENTS:
            %s

            RULES:
            - keep behavior intact
            - improve premium feel
            - preserve valid imports
            - preserve framework correctness
            - return JSON only

            OUTPUT:
            {
              "path": "%s",
              "content": "full improved file content"
            }
            """.formatted(
                ctx.getUserPrompt(),
                filePath,
                ctx.getFramework(),
                context,
                design,
                critique.getOverallScore(),
                critique.getHierarchyScore(),
                critique.getSpacingScore(),
                critique.getConsistencyScore(),
                critique.getPremiumScore(),
                critique.getResponsivenessScore(),
                suggestions,
                filePath
        );
    }

    @Override
    public String detectFramework(String userPrompt) {
        String p = userPrompt.toLowerCase();
        if (p.contains("next")) return "next";
        if (p.contains("vue")) return "vue-vite";
        if (p.contains("angular")) return "angular";
        if (p.contains("cra") || p.contains("create react app")) return "react-cra";
        return "react-vite";
    }

    @Override
    public List<String> sortFilesForGeneration(List<String> files) {
        return files.stream()
                .sorted((a, b) -> {
                    boolean aIsCss = a.endsWith(".css");
                    boolean bIsCss = b.endsWith(".css");
                    boolean aIsJson = a.equals("package.json");
                    boolean bIsJson = b.equals("package.json");
                    boolean aIsConfig = a.endsWith(".config.js") || a.endsWith(".config.ts")
                            || a.equals("angular.json") || a.equals("tsconfig.json");
                    boolean bIsConfig = b.endsWith(".config.js") || b.endsWith(".config.ts")
                            || b.equals("angular.json") || b.equals("tsconfig.json");

                    if (aIsJson && !bIsJson) return -1;
                    if (!aIsJson && bIsJson) return 1;
                    if (aIsConfig && !bIsConfig) return -1;
                    if (!aIsConfig && bIsConfig) return 1;
                    if (aIsCss && !bIsCss) return 1;
                    if (!aIsCss && bIsCss) return -1;
                    return 0;
                })
                .collect(Collectors.toList());
    }

    @Override
    public String getCssEntryPath(String framework) {
        return switch (framework) {
            case "next" -> "app/globals.css";
            case "vue-vite" -> "src/style.css";
            case "angular" -> "src/styles.css";
            default -> "src/index.css";
        };
    }

    private String buildIntentBlock(PromptContext ctx) {
        if (ctx.getIntent() == null) return "INTENT: (not provided)";
        return """
            INTENT:
            - Type: %s
            - Features: %s
            """.formatted(
                ctx.getIntent().getPrimaryIntent(),
                ctx.getIntent().getFeatures()
        );
    }

    private String buildArtifactsBlock(PromptContext ctx) {
        if (ctx.getArtifactPlan() == null || ctx.getArtifactPlan().getArtifacts() == null) {
            return "ARTIFACTS: (not provided)";
        }
        return "ARTIFACTS:\n- " + String.join("\n- ", ctx.getArtifactPlan().getArtifacts());
    }

    private String buildDesignBlock(DesignMemory d) {
        if (d == null) return "(no stored design memory)";
        return """
            - Style: %s
            - Colors: %s
            - Radius: %s
            - Shadow: %s
            - Typography: %s
            """.formatted(
                d.getThemeStyle(),
                d.getColorSystem(),
                d.getRadius(),
                d.getShadow(),
                d.getTypography()
        );
    }

    private String compressContextFromFiles(List<GeneratedFile> files) {
        if (files == null || files.isEmpty()) return "(no existing files)";

        StringBuilder sb = new StringBuilder();
        for (GeneratedFile file : files.stream().limit(8).toList()) {
            sb.append("FILE: ").append(file.getPath()).append("\n");
            String[] lines = file.getContent().split("\n");
            for (int i = 0; i < Math.min(lines.length, 35); i++) {
                sb.append(lines[i]).append("\n");
            }
            if (lines.length > 35) {
                sb.append("// ... truncated\n");
            }
            sb.append("-----\n");
        }
        return sb.toString();
    }

    private boolean isV4Framework(String framework) {
        return "react-vite".equals(framework) || "vue-vite".equals(framework);
    }

    private String getTailwindSetup(String framework) {
        return switch (framework) {
            case "next" -> "Tailwind v3 setup for Next.js";
            case "react-cra" -> "Tailwind v3 setup for React CRA";
            case "vue-vite" -> "Tailwind v4 setup for Vue + Vite";
            case "angular" -> "Tailwind v3 setup for Angular";
            default -> "Tailwind v4 setup for React + Vite";
        };
    }

    private String getFileSpecificRules(String filePath, String framework) {
        return switch (filePath) {
            case "package.json" -> "Must include only required dependencies/scripts for " + framework;
            case "vite.config.js" -> "Must include proper framework plugin and tailwindcss() plugin";
            case "tailwind.config.js" -> "Only generate for v3 frameworks";
            case "postcss.config.js" -> "Must export tailwindcss + autoprefixer config";
            case "index.html" -> "Must include root div and correct script entry";
            case "src/main.jsx" -> "Owns BrowserRouter and imports App + CSS";
            case "src/App.jsx" -> "Must implement a structured layout with sections, cards, and proper spacing. No plain UI.";
            default -> "Generate a complete production-quality file with valid imports and Tailwind usage";
        };
    }

    private String formatSuggestion(UIFixSuggestion suggestion) {
        return "- [" + suggestion.getCategory() + "] " + suggestion.getIssue() + " -> " + suggestion.getFix();
    }

    public Set<String> extractTailwindClasses(String content) {
        Set<String> classes = new LinkedHashSet<>();
        if (content == null || content.isBlank()) return classes;

        Pattern p = Pattern.compile("(?:className|class)=[\"']([^\"']+)[\"']");
        Matcher m = p.matcher(content);
        while (m.find()) {
            Arrays.stream(m.group(1).split("\\s+"))
                    .map(String::trim)
                    .filter(c -> !c.isEmpty() && looksLikeTailwind(c))
                    .forEach(classes::add);
        }

        Pattern tp = Pattern.compile("(?:className|class)=\\{`([^`]+)`\\}");
        Matcher tm = tp.matcher(content);
        while (tm.find()) {
            String staticPart = tm.group(1).replaceAll("\\$\\{[^}]+\\}", " ");
            Arrays.stream(staticPart.split("\\s+"))
                    .map(String::trim)
                    .filter(c -> !c.isEmpty() && looksLikeTailwind(c))
                    .forEach(classes::add);
        }

        return classes;
    }

    private boolean looksLikeTailwind(String cls) {
        if (!cls.contains("-") && !List.of(
                "flex", "grid", "block", "hidden", "relative",
                "absolute", "fixed", "sticky", "overflow", "truncate",
                "uppercase", "lowercase", "capitalize", "italic",
                "underline", "container"
        ).contains(cls)) {
            return false;
        }
        if (cls.startsWith("${") || cls.contains("(") || cls.contains(")")) return false;
        if (cls.contains("border-border") ||
                cls.contains("bg-background") ||
                cls.contains("text-foreground")) {
            return false;
        }
        return true;
    }
}