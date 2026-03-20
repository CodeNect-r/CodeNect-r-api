package com.lovable.ai_service.service;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.dto.GenerationMode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class PromptFactory {

    /*
     * ═══════════════════════════════════════════════════════════════
     *  ARCHITECTURE DECISION: TAILWIND CSS v4 BY DEFAULT
     *
     *  All projects now use Tailwind CSS v4 unless the user explicitly
     *  requests plain CSS. This eliminates the entire class of "CSS
     *  completeness" bugs — with Tailwind, the AI writes utility
     *  classes directly in JSX and there are no custom class names
     *  that need to be separately defined in a CSS file.
     *
     *  MANDATORY TAILWIND FILES per framework (always generated):
     *
     *  react-vite:
     *    - tailwindcss + @tailwindcss/vite in package.json devDeps
     *    - vite.config.js: plugins: [react(), tailwindcss()]
     *    - src/index.css: first line = @import "tailwindcss";
     *
     *  next.js:
     *    - tailwindcss + postcss + autoprefixer in devDeps
     *    - tailwind.config.js at ROOT
     *    - postcss.config.js at ROOT
     *    - app/globals.css: starts with @tailwind base/components/utilities
     *
     *  react-cra:
     *    - tailwindcss + autoprefixer in devDeps
     *    - tailwind.config.js at ROOT
     *    - src/index.css: starts with @tailwind base/components/utilities
     *
     *  vue-vite:
     *    - tailwindcss + @tailwindcss/vite in package.json devDeps
     *    - vite.config.js: plugins: [vue(), tailwindcss()]
     *    - src/style.css: first line = @import "tailwindcss";
     *
     *  angular:
     *    - tailwindcss + postcss + autoprefixer in devDeps
     *    - tailwind.config.js at ROOT
     *    - src/styles.css: starts with @tailwind base/components/utilities
     *
     *  FIX #5 (sanitize bypass): buildCssAuditPrompt() takes raw
     *  List<GeneratedFile> — never sanitized — so className regex works.
     *
     *  FIX #6 (CSS order): CSS file is always last in required files list.
     *
     *  FIX #7 (regen CSS re-audit): regenerateSystemPrompt() now
     *  explicitly includes CSS file in impacted files.
     *
     *  FIX #8 (JSX syntax safety): Added comprehensive JSX/JS syntax
     *  rules to prevent unterminated strings, broken template literals,
     *  and other syntax errors that cause build failures.
     * ═══════════════════════════════════════════════════════════════
     */

    /* =======================================================
       🚨 JSX / JS SYNTAX SAFETY RULES — FIX #8
       Injected into EVERY file-generation prompt to prevent
       syntax errors that break the build (e.g. unterminated
       string literals, broken template literals, bad JSX).
    ======================================================= */

    private static final String JSX_SYNTAX_SAFETY_RULES = """
        ══════════════════════════════════════════════════════════
        🚨 JSX / JAVASCRIPT SYNTAX RULES — VIOLATIONS BREAK BUILD
        ══════════════════════════════════════════════════════════

        🔴 RULE S1 — TEMPLATE LITERAL CLOSING POSITION:
        The closing backtick MUST come AFTER the last } of the expression.

          ✅ CORRECT:   `$${value.toFixed(2)}`
          ❌ INCORRECT: `$${value.toFixed(2)`}
          ✅ CORRECT:   `Hello ${name}, you have ${count} items`
          ❌ INCORRECT: `Hello ${name}, you have ${count}`  ← if used inside JSX {} without outer backtick pair

        🔴 RULE S2 — TERNARY EXPRESSIONS WITH STRING LITERALS:
        In JSX, every string in a ternary must be properly quoted.

          ✅ CORRECT:   {isLoading ? 'Loading...' : 'Done'}
          ✅ CORRECT:   {count === 0 ? 'FREE' : `$${count.toFixed(2)}`}
          ❌ INCORRECT: {count === 0 ? 'FREE' : `$${count.toFixed(2)`}
          ❌ INCORRECT: {count === 0 ? 'FREE : 'Done'}   ← missing closing quote

        🔴 RULE S3 — JSX ATTRIBUTE STRINGS:
        Attribute values must always have matching open/close quotes.

          ✅ CORRECT:   placeholder="Enter email"
          ✅ CORRECT:   className="flex items-center gap-4"
          ❌ INCORRECT: placeholder="Enter email
          ❌ INCORRECT: className="flex items-center gap-4'  ← mismatched quotes

        🔴 RULE S4 — NO UNTERMINATED STRINGS ANYWHERE:
        Every string literal — single-quoted, double-quoted, or backtick —
        must have its closing delimiter before the line ends (or be a
        deliberate multi-line template literal).

          ✅ CORRECT:   const msg = 'Hello world';
          ❌ INCORRECT: const msg = 'Hello world;    ← missing closing quote

        🔴 RULE S5 — JSX EXPRESSION BRACES MUST BE BALANCED:
        Every { opened inside JSX must have a matching }.

          ✅ CORRECT:   <span>{item.price.toFixed(2)}</span>
          ❌ INCORRECT: <span>{item.price.toFixed(2)</span>   ← missing }

        🔴 RULE S6 — CONDITIONAL RENDERING PATTERNS:
        Use these safe patterns only:

          ✅ {condition && <Component />}
          ✅ {condition ? <A /> : <B />}
          ✅ {condition ? 'text A' : 'text B'}
          ❌ {condition && <Component />    ← missing closing }
          ❌ {condition ? <A /> : <B />    ← missing closing }

        🔴 RULE S7 — IMPORT STATEMENTS:
        Every import path must be a complete, valid string.

          ✅ import Button from '../components/Button';
          ❌ import Button from '../components/Button;   ← unterminated

        🔴 RULE S8 — EXPORT SYNTAX IS MANDATORY ON EVERY COMPONENT:
        Every React component file MUST use "export default function" or
        "export default ComponentName". NEVER write "default function" alone —
        that is a syntax error and will crash the build immediately.

          ✅ CORRECT:   export default function Footer() { ... }
          ✅ CORRECT:   const Footer = () => { ... }; export default Footer;
          ❌ INCORRECT: default function Footer() { ... }   ← missing "export"
          ❌ INCORRECT: function Footer() { ... }            ← missing "export default"

        This applies to EVERY file: pages, components, layouts, hooks — all of them.
        If a file has no export default, the app will fail to build or render a blank page.

        🔴 RULE S9 — CSS @import ORDER IS STRICT:
        In any CSS file, ALL @import statements must appear at the very top,
        before ANY rules, :root blocks, @layer, or selectors.
        Putting @import after any rule causes a build warning or error.

          ✅ CORRECT ORDER:
            @import "tailwindcss";
            @import url('https://fonts.googleapis.com/...');
            :root { --font-display: 'Inter', sans-serif; }
            @layer components { ... }

          ❌ INCORRECT ORDER (BREAKS BUILD):
            @import "tailwindcss";
            :root { ... }
            @import url('https://fonts.googleapis.com/...');   ← @import after :root = ERROR
            @layer components { ... }

          ❌ ALSO INCORRECT:
            :root { ... }
            @import "tailwindcss";   ← @import after any rule = ERROR

        🔴 RULE S10 — SELF-REVIEW BEFORE OUTPUT:
        Before outputting any file, mentally scan for:
          □ Every backtick has a matching backtick
          □ Every ' has a matching '
          □ Every " has a matching "
          □ Every { inside JSX has a matching }
          □ No line ends in the middle of a string literal
          □ Template literal expressions use ${ } not ${ (missing closing brace)
          □ Every component file has "export default" at the top or bottom
          □ No "default function" without "export" in front of it
          □ All CSS @import lines appear before any rules or :root blocks
        ══════════════════════════════════════════════════════════
        """;

    /* =======================================================
       🎨 TAILWIND SETUP BLOCKS — per framework
       These are injected into EVERY prompt to ensure the AI
       generates ALL required Tailwind wiring files upfront.
    ======================================================= */

    private static final String TAILWIND_SETUP_REACT_VITE = """
        ══════════════════════════════════════════════════════════
        🎨 TAILWIND CSS v4 — react-vite  (MANDATORY SETUP)
        ══════════════════════════════════════════════════════════

        ALL THREE WIRING POINTS MUST BE PRESENT — verify each:

        ┌─ POINT 1: package.json devDependencies ──────────────────┐
        │  "tailwindcss": "^4.0.0"                                 │
        │  "@tailwindcss/vite": "^4.0.0"                           │
        │  Both MUST be present. Missing either = build fails.     │
        └──────────────────────────────────────────────────────────┘

        ┌─ POINT 2: vite.config.js ────────────────────────────────┐
        │  import { defineConfig } from 'vite';                    │
        │  import react from '@vitejs/plugin-react';               │
        │  import tailwindcss from '@tailwindcss/vite';            │
        │  export default defineConfig({                           │
        │    plugins: [react(), tailwindcss()],                    │
        │  });                                                     │
        │  ⚠️ tailwindcss() MISSING = Tailwind produces ZERO CSS   │
        └──────────────────────────────────────────────────────────┘

        ┌─ POINT 3: src/index.css — FIRST LINE MUST BE: ───────────┐
        │  @import "tailwindcss";                                  │
        │  ⚠️ This line missing = Tailwind produces ZERO CSS        │
        │  Do NOT use @tailwind base/components/utilities (v3)     │
        └──────────────────────────────────────────────────────────┘

        ✅ NO tailwind.config.js needed (v4 handles it automatically)
        ✅ NO postcss.config.js needed (v4 uses Vite plugin directly)

        USAGE IN JSX: use Tailwind utility classes directly
          className="flex items-center gap-4 p-6 bg-gray-900 rounded-xl"
          className="text-2xl font-bold text-white hover:text-indigo-400"
        ══════════════════════════════════════════════════════════
        """;

    private static final String TAILWIND_SETUP_NEXT = """
        ══════════════════════════════════════════════════════════
        🎨 TAILWIND CSS v3 — next.js  (MANDATORY SETUP)
        ══════════════════════════════════════════════════════════

        ALL FOUR WIRING POINTS MUST BE PRESENT:

        ┌─ POINT 1: package.json devDependencies ──────────────────┐
        │  "tailwindcss": "^3.4.0"                                 │
        │  "postcss": "^8.4.0"                                     │
        │  "autoprefixer": "^10.4.0"                               │
        └──────────────────────────────────────────────────────────┘

        ┌─ POINT 2: tailwind.config.js at ROOT (CJS) ──────────────┐
        │  /** @type {import('tailwindcss').Config} */              │
        │  module.exports = {                                      │
        │    content: [                                            │
        │      './app/**/*.{js,ts,jsx,tsx,mdx}',                   │
        │      './pages/**/*.{js,ts,jsx,tsx,mdx}',                 │
        │      './components/**/*.{js,ts,jsx,tsx,mdx}',            │
        │    ],                                                    │
        │    theme: { extend: {} },                                │
        │    plugins: [],                                          │
        │  };                                                      │
        └──────────────────────────────────────────────────────────┘

        ┌─ POINT 3: postcss.config.js at ROOT (CJS) ───────────────┐
        │  module.exports = {                                      │
        │    plugins: { tailwindcss: {}, autoprefixer: {} },       │
        │  };                                                      │
        └──────────────────────────────────────────────────────────┘

        ┌─ POINT 4: app/globals.css — FIRST THREE LINES: ──────────┐
        │  @tailwind base;                                         │
        │  @tailwind components;                                   │
        │  @tailwind utilities;                                    │
        │  ⚠️ All three required. app/layout.jsx MUST import it.   │
        └──────────────────────────────────────────────────────────┘
        ══════════════════════════════════════════════════════════
        """;

    private static final String TAILWIND_SETUP_REACT_CRA = """
        ══════════════════════════════════════════════════════════
        🎨 TAILWIND CSS v3 — React CRA  (MANDATORY SETUP)
        ══════════════════════════════════════════════════════════

        ALL THREE WIRING POINTS MUST BE PRESENT:

        ┌─ POINT 1: package.json devDependencies ──────────────────┐
        │  "tailwindcss": "^3.4.0"                                 │
        │  "autoprefixer": "^10.4.0"                               │
        │  (CRA has PostCSS built in — no separate postcss needed) │
        └──────────────────────────────────────────────────────────┘

        ┌─ POINT 2: tailwind.config.js at ROOT (CJS) ──────────────┐
        │  module.exports = {                                      │
        │    content: ['./src/**/*.{js,jsx,ts,tsx}',               │
        │              './public/index.html'],                     │
        │    theme: { extend: {} },                                │
        │    plugins: [],                                          │
        │  };                                                      │
        └──────────────────────────────────────────────────────────┘

        ┌─ POINT 3: src/index.css — FIRST THREE LINES: ────────────┐
        │  @tailwind base;                                         │
        │  @tailwind components;                                   │
        │  @tailwind utilities;                                    │
        │  src/index.js MUST import './index.css'                  │
        └──────────────────────────────────────────────────────────┘
        ══════════════════════════════════════════════════════════
        """;

    private static final String TAILWIND_SETUP_VUE_VITE = """
        ══════════════════════════════════════════════════════════
        🎨 TAILWIND CSS v4 — vue-vite  (MANDATORY SETUP)
        ══════════════════════════════════════════════════════════

        ALL THREE WIRING POINTS MUST BE PRESENT:

        ┌─ POINT 1: package.json devDependencies ──────────────────┐
        │  "tailwindcss": "^4.0.0"                                 │
        │  "@tailwindcss/vite": "^4.0.0"                           │
        └──────────────────────────────────────────────────────────┘

        ┌─ POINT 2: vite.config.js ────────────────────────────────┐
        │  import vue from '@vitejs/plugin-vue';                   │
        │  import tailwindcss from '@tailwindcss/vite';            │
        │  export default defineConfig({                           │
        │    plugins: [vue(), tailwindcss()],                      │
        │  });                                                     │
        └──────────────────────────────────────────────────────────┘

        ┌─ POINT 3: src/style.css — FIRST LINE MUST BE: ───────────┐
        │  @import "tailwindcss";                                  │
        │  src/main.js MUST import './style.css'                   │
        └──────────────────────────────────────────────────────────┘
        ══════════════════════════════════════════════════════════
        """;

    private static final String TAILWIND_SETUP_ANGULAR = """
        ══════════════════════════════════════════════════════════
        🎨 TAILWIND CSS v3 — Angular  (MANDATORY SETUP)
        ══════════════════════════════════════════════════════════

        ALL THREE WIRING POINTS MUST BE PRESENT:

        ┌─ POINT 1: package.json devDependencies ──────────────────┐
        │  "tailwindcss": "^3.4.0"                                 │
        │  "autoprefixer": "^10.4.0"                               │
        │  "postcss": "^8.4.0"                                     │
        └──────────────────────────────────────────────────────────┘

        ┌─ POINT 2: tailwind.config.js at ROOT (CJS) ──────────────┐
        │  module.exports = {                                      │
        │    content: ['./src/**/*.{html,ts}'],                    │
        │    theme: { extend: {} },                                │
        │    plugins: [],                                          │
        │  };                                                      │
        └──────────────────────────────────────────────────────────┘

        ┌─ POINT 3: src/styles.css — FIRST THREE LINES: ───────────┐
        │  @tailwind base;                                         │
        │  @tailwind components;                                   │
        │  @tailwind utilities;                                    │
        │  angular.json "styles": ["src/styles.css"]              │
        └──────────────────────────────────────────────────────────┘
        ══════════════════════════════════════════════════════════
        """;

    /* =======================================================
       📦 DEPENDENCY RULES
    ======================================================= */

    private static final String DEPENDENCY_RULES_REACT_VITE = """
        📦 DEPENDENCIES — react-vite (Tailwind v4 always included)
        GOLDEN RULE: import it → it MUST be in package.json.

        ALWAYS in package.json:
          react ^18.2.0, react-dom ^18.2.0, react-router-dom ^6.22.0,
          lucide-react ^0.395.0, framer-motion ^11.2.0, clsx ^2.1.1,
          axios ^1.7.2, date-fns ^3.6.0, zustand ^4.5.2, prop-types ^15.8.1
          vite ^5.0.0 (dev), @vitejs/plugin-react ^4.2.0 (dev),
          tailwindcss ^4.0.0 (dev), @tailwindcss/vite ^4.0.0 (dev)

        EXTRAS (add before using): @tanstack/react-query, react-hook-form,
          zod, recharts, react-hot-toast, @mui/material, antd, react-icons, lodash
        """;

    private static final String DEPENDENCY_RULES_NEXT = """
        📦 DEPENDENCIES — next.js (Tailwind v3 always included)
        GOLDEN RULE: import it → it MUST be in package.json.

        ALWAYS in package.json:
          next ^14.2.0, react ^18.2.0, react-dom ^18.2.0,
          lucide-react ^0.395.0, framer-motion ^11.2.0, clsx ^2.1.1,
          axios ^1.7.2, prop-types ^15.8.1,
          tailwindcss ^3.4.0 (dev), postcss ^8.4.0 (dev), autoprefixer ^10.4.0 (dev)
        """;

    private static final String DEPENDENCY_RULES_VUE_VITE = """
        📦 DEPENDENCIES — vue-vite (Tailwind v4 always included)
        GOLDEN RULE: import it → it MUST be in package.json.

        ALWAYS in package.json:
          vue ^3.4.0, vue-router ^4.3.0, pinia ^2.1.7,
          @vueuse/core ^10.9.0, axios ^1.7.2, lucide-vue-next ^0.395.0,
          vite ^5.0.0 (dev), @vitejs/plugin-vue ^5.0.4 (dev),
          tailwindcss ^4.0.0 (dev), @tailwindcss/vite ^4.0.0 (dev)
        """;

    private static final String DEPENDENCY_RULES_REACT_CRA = """
        📦 DEPENDENCIES — react-cra (Tailwind v3 always included)
        GOLDEN RULE: import it → it MUST be in package.json.

        ALWAYS in package.json:
          react ^18.2.0, react-dom ^18.2.0, react-scripts ^5.0.1,
          react-router-dom ^6.22.0, lucide-react ^0.395.0,
          axios ^1.7.2, clsx ^2.1.1, prop-types ^15.8.1,
          tailwindcss ^3.4.0 (dev), autoprefixer ^10.4.0 (dev)
        """;

    private static final String DEPENDENCY_RULES_ANGULAR = """
        📦 DEPENDENCIES — angular (Tailwind v3 always included)
        GOLDEN RULE: import it → it MUST be in package.json.

        ALWAYS in package.json:
          @angular/core ^17, @angular/common ^17, @angular/router ^17,
          @angular/forms ^17, @angular/platform-browser ^17,
          @angular/platform-browser-dynamic ^17, @angular/compiler ^17,
          rxjs ^7.8.0, tslib ^2.6.0, zone.js ^0.14.0,
          @angular-devkit/build-angular ^17 (dev), @angular/cli ^17 (dev),
          @angular/compiler-cli ^17 (dev), typescript ^5.2.0 (dev),
          tailwindcss ^3.4.0 (dev), postcss ^8.4.0 (dev), autoprefixer ^10.4.0 (dev)
        """;

    /* =======================================================
       🧠 ENTRY POINTS
    ======================================================= */

    public String buildSystemPrompt(GenerationMode mode) {
        return mode == GenerationMode.INITIAL ? initialSystemPrompt() : regenerateSystemPrompt();
    }

    public String buildPrompt(
            String context, String userPrompt,
            Set<String> impactedFiles, GenerationMode mode, String framework
    ) {
        String safeContext = sanitize(context);

        if (mode == GenerationMode.REGENERATE) {
            return """
            You are modifying an existing frontend project.
            🚨 FRAMEWORK (LOCKED): %s
            %s
            %s
            %s
            BEGIN CONTEXT
            %s
            END CONTEXT
            ALLOWED FILES: %s
            USER REQUEST: %s
            RULES:
            - Modify ONLY allowed files
            - DO NOT change framework, build system, or Tailwind setup
            - Keep Tailwind utility classes consistent
            - If you add a new import, add it to package.json
            """.formatted(framework, getDependencyRules(framework),
                    getTailwindSetup(framework), JSX_SYNTAX_SAFETY_RULES,
                    safeContext, impactedFiles, userPrompt);
        }

        return """
        You are building a complete frontend project from scratch.
        🚨 FRAMEWORK: %s
        %s
        %s
        %s
        ══════════════════════════════════════════
        🔴 MANDATORY FILES — ALL MUST APPEAR IN OUTPUT:
        ══════════════════════════════════════════
        %s
        ══════════════════════════════════════════
        USER REQUEST: %s
        OUTPUT: JSON array → [{ "path": "...", "content": "..." }, ...]
        - Every mandatory file above must be present — including ALL Tailwind config files
        - 3–4 page components beyond mandatory files
        - Use Tailwind utility classes in all JSX/HTML
        - Every imported package must be in package.json
        """.formatted(framework, getDependencyRules(framework),
                getTailwindSetup(framework), JSX_SYNTAX_SAFETY_RULES,
                getRequiredFilesBlock(framework), userPrompt);
    }

    /* =======================================================
       🧠 FRAMEWORK DETECTION
    ======================================================= */

    public String detectFramework(String prompt) {
        String p = prompt.toLowerCase();
        if (p.contains("next"))                                  return "next";
        if (p.contains("vue"))                                   return "vue-vite";
        if (p.contains("angular"))                               return "angular";
        if (p.contains("cra") || p.contains("create react app")) return "react-cra";
        return "react-vite";
    }

    /* =======================================================
       📐 PLANNING
       FIX #6: CSS/config files are always listed LAST so they
       are generated after all JSX files exist.
    ======================================================= */

    public String buildPlanningSystemPrompt() {
        return """
        You are a frontend project planner.
        Return ONLY valid JSON: { "framework": "...", "files": [...] }

        ══════════════════════════════════════════
        🔴 MANDATORY FILE ORDER — CRITICAL:

        react-vite (Tailwind v4 — ALWAYS):
          ["package.json", "vite.config.js", "index.html",
           "src/main.jsx", "src/App.jsx",
           ...page files..., ...component files...,
           "src/index.css"]   ← CSS MUST BE LAST

        next.js (Tailwind v3 — ALWAYS):
          ["package.json", "next.config.js",
           "tailwind.config.js", "postcss.config.js",
           "app/layout.jsx", ...page files..., ...component files...,
           "app/globals.css"]   ← CSS MUST BE LAST

        react-cra (Tailwind v3 — ALWAYS):
          ["package.json", "tailwind.config.js",
           "public/index.html", "src/index.jsx", "src/App.jsx",
           ...page files..., ...component files...,
           "src/index.css"]   ← CSS MUST BE LAST

        vue-vite (Tailwind v4 — ALWAYS):
          ["package.json", "vite.config.js", "index.html",
           "src/main.js", "src/App.vue",
           ...page files..., ...component files...,
           "src/style.css"]   ← CSS MUST BE LAST

        angular (Tailwind v3 — ALWAYS):
          ["package.json", "angular.json", "tsconfig.json",
           "tailwind.config.js",
           "src/main.ts", "src/index.html",
           ...component files...,
           "src/styles.css"]   ← CSS MUST BE LAST

        🔴 WHY CSS IS LAST: The CSS file is generated after all JSX/TS
        files exist so it can be audited for actual Tailwind usage.
        ══════════════════════════════════════════

        RULES:
        - ONE framework, 8–14 files, 3–4 pages, 1+ shared component
        - Tailwind config files MUST always be in the list
        - CSS entry file MUST be the last item in the list
        - package.json MUST include Tailwind devDependencies

        OUTPUT: JSON only. No markdown.
        """;
    }

    public String buildPlanningPrompt(String userPrompt, String framework) {
        return """
        USER REQUEST: %s
        🚨 FORCED FRAMEWORK: %s
        %s
        %s
        🔴 FILE LIST MUST FOLLOW THIS PATTERN:
        %s
        Total 8–14 files. 3–4 pages.
        CSS file MUST be last. Tailwind config files MUST be included.
        Every imported package must appear in package.json.
        """.formatted(userPrompt, framework,
                getDependencyRules(framework),
                getTailwindSetup(framework),
                getRequiredFilesBlock(framework));
    }

    /* =======================================================
       📄 SINGLE FILE GENERATION
    ======================================================= */

    public String buildSingleFileSystemPrompt(GenerationMode mode) {
        if (mode == GenerationMode.INITIAL) {
            return """
            You are generating one file at a time for a frontend project.

            OUTPUT FORMAT — return ONLY this JSON:
            { "path": "exact/file/path", "content": "full file content as escaped string" }

            🚨 JSON RULES:
            - "content" MUST be a plain STRING — never nested object
            - Escape double-quotes as \\", newlines as \\n
            - No markdown fences, no explanation outside JSON

            ══════════════════════════════════════════════════════════
            🔴 RULE 1 — TAILWIND CSS IS ALWAYS USED:
            ══════════════════════════════════════════════════════════
            Every project uses Tailwind CSS. Use utility classes in all JSX/HTML.
            DO NOT write custom CSS class definitions for layout/styling.
            DO NOT use plain CSS class names like className="hero-section".

            All Tailwind wiring files MUST be generated (see user prompt for setup).
            For the CSS entry file: first line MUST be the correct Tailwind directive.

            GOOD examples:
              className="flex flex-col min-h-screen bg-gray-950 text-white"
              className="grid grid-cols-1 md:grid-cols-3 gap-6 p-8"
              className="rounded-xl border border-white/10 bg-white/5 p-6 hover:bg-white/10 transition-all"
              className="text-4xl font-bold text-white font-display"
              className="px-6 py-3 bg-indigo-600 hover:bg-indigo-500 rounded-lg font-semibold transition-colors"

            BAD examples (DO NOT DO):
              className="hero-section"  ← no custom class names
              className="stats-grid"    ← no custom class names
              className="feature-card"  ← no custom class names
            ══════════════════════════════════════════════════════════

            🔴 RULE 2 — IMPORT/PACKAGE CONTRACT:
            Every import must have a matching package.json entry.

            %s

            🎨 DESIGN QUALITY (all UI files):
            - Google Font via @import or <link> — NEVER system fonts
            - Strong dark/light theme with consistent color palette
            - Hover + focus + active states using Tailwind hover:/focus: variants
            - Smooth transitions: transition-all duration-200/300
            - Cards: rounded-xl border border-white/10 bg-white/5 backdrop-blur-sm
            - Buttons: px-6 py-3 rounded-lg font-semibold with hover: color shift
            - Sticky nav: sticky top-0 z-50 backdrop-blur-md bg-black/80 border-b border-white/10
            - Mobile-first: sm: md: lg: breakpoints throughout
            - Typography: font-display for headings, tracking-tight, proper weights
            - Premium quality — Vercel / Linear / Stripe level
            """.formatted(JSX_SYNTAX_SAFETY_RULES);
        }

        return """
        Modify EXACTLY ONE file.
        RULES:
        - Keep Tailwind CSS approach — do NOT switch to plain CSS
        - Only update required logic
        - If you add a new import, ensure it is in package.json
        - Return JSON only: { "path": "...", "content": "..." }

        %s
        """.formatted(JSX_SYNTAX_SAFETY_RULES);
    }

    public String buildSingleFilePrompt(
            String context, String userPrompt, String filePath,
            Set<String> impactedFiles, GenerationMode mode, String framework
    ) {
        String safeContext = sanitize(context);

        if (mode == GenerationMode.REGENERATE) {
            return """
            Modify this file ONLY.
            FRAMEWORK: %s  FILE: %s  REQUEST: %s
            %s
            %s
            CONTEXT: %s
            RULES:
            - Keep Tailwind CSS — do NOT introduce plain CSS class names
            - If you add a new import, it must be in package.json
            OUTPUT: { "path": "%s", "content": "..." }
            """.formatted(framework, filePath, userPrompt,
                    getTailwindSetup(framework), JSX_SYNTAX_SAFETY_RULES,
                    safeContext, filePath);
        }

        String fileSpecificRules = getFileSpecificRules(filePath, framework);

        return """
        Generate this file for the project.
        FRAMEWORK: %s
        FILE TO GENERATE: %s
        %s
        %s
        %s
        %s
        USER REQUEST CONTEXT: %s
        ALREADY GENERATED FILES (for import reference): %s
        OUTPUT: { "path": "%s", "content": "..." }
        """.formatted(framework, filePath,
                fileSpecificRules,
                getDependencyRules(framework),
                getTailwindSetup(framework),
                JSX_SYNTAX_SAFETY_RULES,
                userPrompt, safeContext, filePath);
    }

    /* =======================================================
       🔍 CSS AUDIT PROMPT — FIX #2
       Called by generation service AFTER all JSX files exist,
       BEFORE CSS file is generated. Takes raw (unsanitized)
       files so className regex works correctly — FIX #5.
    ======================================================= */

    public String buildCssAuditPrompt(
            List<GeneratedFile> generatedFiles,
            String framework,
            String userPrompt
    ) {
        String cssPath = getCssEntryPath(framework);
        boolean isV4   = framework.equals("react-vite") || framework.equals("vue-vite");

        // FIX #5: use raw file content (not sanitized) so className regex matches
        Set<String> tailwindClasses = new LinkedHashSet<>();
        StringBuilder jsxSnippets   = new StringBuilder();

        for (GeneratedFile file : generatedFiles) {
            String path = file.getPath();
            if (!path.endsWith(".jsx") && !path.endsWith(".tsx")
                    && !path.endsWith(".vue") && !path.endsWith(".ts")
                    && !path.endsWith(".html")) continue;

            tailwindClasses.addAll(extractTailwindClasses(file.getContent()));

            // Include first 40 lines for structural context
            String[] lines = file.getContent().split("\n");
            jsxSnippets.append("\n// ").append(path).append("\n");
            for (int i = 0; i < Math.min(40, lines.length); i++) {
                jsxSnippets.append(lines[i]).append("\n");
            }
        }

        String directive = isV4
                ? "@import \"tailwindcss\";"
                : "@tailwind base;\n@tailwind components;\n@tailwind utilities;";

        return """
        Generate the CSS entry file for this project.
        FILE: %s
        FRAMEWORK: %s
        %s

        ══════════════════════════════════════════════════════════
        🔴 MANDATORY — THIS FILE MUST START WITH:
        %s

        This directive is what loads Tailwind. Without it, ALL
        Tailwind utility classes produce zero CSS output.
        ══════════════════════════════════════════════════════════

        TAILWIND CLASSES DETECTED IN JSX FILES:
        %s

        ADDITIONAL REQUIREMENTS after the Tailwind directive:
        1. @import for Google Font (if not already in index.html)
        2. CSS custom properties for any custom design tokens:
           --font-display, --font-body (matching the Google Font)
        3. Any @layer components { } overrides for custom global styles
        4. Responsive base styles for html/body if needed

        ⚠️ CRITICAL — @import ORDER IN CSS:
        The @import "tailwindcss" directive (or Google Fonts @import) MUST
        come BEFORE any rules, :root blocks, or @layer declarations.
        Wrong order causes build warnings and may break styles.

        CORRECT order:
          @import "tailwindcss";
          @import url('https://fonts.googleapis.com/...');
          :root { ... }
          @layer components { ... }

        WRONG order (DO NOT DO):
          :root { ... }
          @import "tailwindcss";   ← @import after rules = WARNING/ERROR

        USER REQUEST CONTEXT: %s

        JSX FILE PREVIEWS:
        %s

        OUTPUT FORMAT — return ONLY this JSON:
        { "path": "%s", "content": "..." }
        """.formatted(
                cssPath, framework,
                getTailwindSetup(framework),
                directive,
                tailwindClasses.isEmpty()
                        ? "(no specific classes found — rely on JSX previews below)"
                        : String.join(", ", tailwindClasses.stream().limit(50).collect(Collectors.toList())),
                userPrompt,
                jsxSnippets.length() > 6000
                        ? jsxSnippets.substring(0, 6000) + "\n...(truncated)"
                        : jsxSnippets.toString(),
                cssPath
        );
    }

    /* =======================================================
       📋 PER-FILE CONTRACTS
    ======================================================= */

    private String getFileSpecificRules(String filePath, String framework) {
        return switch (filePath) {

            case "package.json" -> """
                🔴 FILE CONTRACT — package.json  (framework: %s)
                Generate COMPLETE package.json with ALL Tailwind deps.
                "type": "module" for vite-based projects.
                Content MUST be escaped JSON string — NOT nested object.

                For react-vite MUST include:
                {
                  "name": "app", "version": "0.0.0", "private": true, "type": "module",
                  "scripts": { "dev":"vite", "build":"vite build", "preview":"vite preview" },
                  "dependencies": {
                    "react":"^18.2.0", "react-dom":"^18.2.0",
                    "react-router-dom":"^6.22.0", "lucide-react":"^0.395.0",
                    "framer-motion":"^11.2.0", "clsx":"^2.1.1",
                    "axios":"^1.7.2", "prop-types":"^15.8.1"
                  },
                  "devDependencies": {
                    "vite":"^5.0.0", "@vitejs/plugin-react":"^4.2.0",
                    "tailwindcss":"^4.0.0", "@tailwindcss/vite":"^4.0.0"
                  }
                }
                """.formatted(framework);

            case "vite.config.js" -> """
                🔴 FILE CONTRACT — vite.config.js  (ALWAYS includes Tailwind)
                import { defineConfig } from 'vite';
                import react from '@vitejs/plugin-react';
                import tailwindcss from '@tailwindcss/vite';
                export default defineConfig({ plugins: [react(), tailwindcss()] });
                ⚠️ tailwindcss() MUST be in plugins — without it = zero CSS output.
                """;

            case "tailwind.config.js" -> getTailwindConfigContract(framework);

            case "postcss.config.js" ->
                    "🔴 postcss.config.js: module.exports = { plugins: { tailwindcss: {}, autoprefixer: {} } };";

            case "index.html" -> """
                🔴 index.html at PROJECT ROOT (not src/index.html).
                <div id="root"></div>
                <script type="module" src="/src/main.jsx"></script>
                Add Google Font <link> in <head>.
                """;

            case "src/main.jsx" -> """
                🔴 src/main.jsx:
                import React from 'react';
                import ReactDOM from 'react-dom/client';
                import { BrowserRouter } from 'react-router-dom';
                import App from './App';
                import './index.css';   ← MUST import Tailwind CSS entry
                ReactDOM.createRoot(document.getElementById('root')).render(
                  <React.StrictMode><BrowserRouter><App /></BrowserRouter></React.StrictMode>
                );
                """;

            case "src/index.css", "app/globals.css", "src/style.css", "src/styles.css" ->
                    getFrameworkCssContract(filePath, framework);

            default -> """
                🔴 FILE: %s (framework: %s)
                Generate complete, production-quality file using Tailwind CSS.
                Use Tailwind utility classes directly in JSX — no custom class names.
                Every import must be in package.json.
                Use lucide-react for all icons.
                """.formatted(filePath, framework);
        };
    }

    private String getFrameworkCssContract(String filePath, String framework) {
        boolean isV4 = framework.equals("react-vite") || framework.equals("vue-vite");

        if (isV4) {
            return """
                🔴 FILE CONTRACT — %s  (Tailwind v4)
                ─────────────────────────────────────────────────
                FIRST LINE MUST BE EXACTLY:
                @import "tailwindcss";

                ⚠️ This is the only required Tailwind directive for v4.
                   Without it, ALL utility classes produce ZERO CSS output.
                   Do NOT use @tailwind base/components/utilities (v3 syntax).

                ⚠️ @import ORDER IS MANDATORY:
                   ALL @import statements must come before any rules.
                   CORRECT:
                     @import "tailwindcss";
                     @import url('https://fonts.googleapis.com/...');
                     :root { ... }
                   WRONG:
                     :root { ... }
                     @import "tailwindcss";   ← causes build warning/error

                After @import "tailwindcss"; you may add:
                  - @import url('https://fonts.googleapis.com/...') for custom fonts
                  - @layer base { } for custom base styles
                  - @layer components { } for any reusable component styles
                  - CSS custom properties for font family references
                """.formatted(filePath);
        } else {
            return """
                🔴 FILE CONTRACT — %s  (Tailwind v3)
                ─────────────────────────────────────────────────
                FIRST THREE LINES MUST BE EXACTLY:
                @tailwind base;
                @tailwind components;
                @tailwind utilities;

                ⚠️ All three directives required. Without them = zero CSS output.
                   Do NOT use @import "tailwindcss" (v4 syntax).

                ⚠️ @import ORDER IS MANDATORY:
                   ALL @import statements must come before any rules.
                   CORRECT:
                     @tailwind base;
                     @tailwind components;
                     @tailwind utilities;
                     @import url('https://fonts.googleapis.com/...');
                     :root { ... }
                   WRONG:
                     :root { ... }
                     @import url('...');   ← causes build warning/error

                After the directives you may add:
                  - @import for Google Fonts
                  - @layer base { } for custom base styles
                  - @layer components { } for reusable patterns
                  - CSS custom properties
                """.formatted(filePath);
        }
    }

    private String getTailwindConfigContract(String framework) {
        return switch (framework) {
            case "next" -> """
                🔴 tailwind.config.js (next, CJS — MANDATORY):
                /** @type {import('tailwindcss').Config} */
                module.exports = {
                  content: ['./app/**/*.{js,ts,jsx,tsx,mdx}',
                            './pages/**/*.{js,ts,jsx,tsx,mdx}',
                            './components/**/*.{js,ts,jsx,tsx,mdx}'],
                  theme: { extend: {} }, plugins: [],
                };
                """;
            case "react-cra" -> """
                🔴 tailwind.config.js (react-cra, CJS — MANDATORY):
                module.exports = {
                  content: ['./src/**/*.{js,jsx,ts,tsx}','./public/index.html'],
                  theme: { extend: {} }, plugins: [],
                };
                """;
            case "angular" -> """
                🔴 tailwind.config.js (angular, CJS — MANDATORY):
                module.exports = {
                  content: ['./src/**/*.{html,ts}'],
                  theme: { extend: {} }, plugins: [],
                };
                """;
            case "vue-vite" -> """
                🔴 tailwind.config.js NOT needed for vue-vite v4.
                The @tailwindcss/vite plugin handles configuration automatically.
                DO NOT generate this file for vue-vite.
                """;
            default -> """
                🔴 tailwind.config.js NOT needed for react-vite v4.
                The @tailwindcss/vite plugin handles configuration automatically.
                DO NOT generate this file for react-vite.
                """;
        };
    }

    /* =======================================================
       📊 SUMMARY
    ======================================================= */

    public String buildSummarySystemPrompt() {
        return "Write a short bullet-point summary. Mention: what was built, file count, pages. No markdown.";
    }

    public String buildSummaryPrompt(
            String userPrompt, String framework,
            List<GeneratedFile> files, GenerationMode mode
    ) {
        String fileList = files.stream().map(GeneratedFile::getPath).collect(Collectors.joining(", "));
        return "REQUEST: %s\nFRAMEWORK: %s\nMODE: %s\nFILES: %s"
                .formatted(userPrompt, framework, mode.name(), fileList);
    }

    /* =======================================================
       🔧 PUBLIC UTILITIES — used by generation service + validator
    ======================================================= */

    /** FIX #1: Sort file list so CSS is always last, JSX/TSX first. */
    public List<String> sortFilesForGeneration(List<String> files) {
        return files.stream()
                .sorted((a, b) -> {
                    boolean aIsCss  = a.endsWith(".css");
                    boolean bIsCss  = b.endsWith(".css");
                    boolean aIsJson = a.equals("package.json");
                    boolean bIsJson = b.equals("package.json");
                    boolean aIsConfig = a.endsWith(".config.js") || a.endsWith(".config.ts")
                            || a.equals("angular.json") || a.equals("tsconfig.json");
                    boolean bIsConfig = b.endsWith(".config.js") || b.endsWith(".config.ts")
                            || b.equals("angular.json") || b.equals("tsconfig.json");

                    // package.json first
                    if (aIsJson && !bIsJson) return -1;
                    if (!aIsJson && bIsJson) return 1;
                    // config files second
                    if (aIsConfig && !bIsConfig) return -1;
                    if (!aIsConfig && bIsConfig) return 1;
                    // CSS files last
                    if (aIsCss && !bIsCss) return 1;
                    if (!aIsCss && bIsCss) return -1;
                    return 0;
                })
                .collect(Collectors.toList());
    }

    /** Returns the CSS entry file path for a given framework. */
    public String getCssEntryPath(String framework) {
        return switch (framework) {
            case "next"     -> "app/globals.css";
            case "vue-vite" -> "src/style.css";
            case "angular"  -> "src/styles.css";
            default         -> "src/index.css";
        };
    }

    /**
     * Extracts unique Tailwind utility class names from JSX/HTML content.
     * Used by buildCssAuditPrompt() and BuildValidator.
     * FIX #5: works on raw (unsanitized) content.
     */
    public Set<String> extractTailwindClasses(String content) {
        Set<String> classes = new LinkedHashSet<>();
        if (content == null || content.isBlank()) return classes;

        // Match className="..." static strings
        Pattern p = Pattern.compile("(?:className|class)=[\"']([^\"']+)[\"']");
        Matcher m = p.matcher(content);
        while (m.find()) {
            Arrays.stream(m.group(1).split("\\s+"))
                    .map(String::trim)
                    .filter(c -> !c.isEmpty() && looksLikeTailwind(c))
                    .forEach(classes::add);
        }

        // Match className={`...`} template literals
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

    /** Heuristic: does this class token look like a Tailwind utility class? */
    private boolean looksLikeTailwind(String cls) {
        if (!cls.contains("-") && !List.of("flex","grid","block","hidden","relative",
                "absolute","fixed","sticky","overflow","truncate","uppercase",
                "lowercase","capitalize","italic","underline","container").contains(cls)) {
            return false;
        }
        if (cls.startsWith("${") || cls.contains("(") || cls.contains(")")) return false;
        return true;
    }

    /* =======================================================
       🔧 PRIVATE HELPERS
    ======================================================= */

    private String getTailwindSetup(String framework) {
        return switch (framework) {
            case "next"      -> TAILWIND_SETUP_NEXT;
            case "react-cra" -> TAILWIND_SETUP_REACT_CRA;
            case "vue-vite"  -> TAILWIND_SETUP_VUE_VITE;
            case "angular"   -> TAILWIND_SETUP_ANGULAR;
            default          -> TAILWIND_SETUP_REACT_VITE;
        };
    }

    private String getDependencyRules(String framework) {
        return switch (framework) {
            case "next"      -> DEPENDENCY_RULES_NEXT;
            case "vue-vite"  -> DEPENDENCY_RULES_VUE_VITE;
            case "react-cra" -> DEPENDENCY_RULES_REACT_CRA;
            case "angular"   -> DEPENDENCY_RULES_ANGULAR;
            default          -> DEPENDENCY_RULES_REACT_VITE;
        };
    }

    /**
     * FIX #6: CSS entry file is always LAST.
     * Tailwind config files are always explicitly listed.
     */
    private String getRequiredFilesBlock(String framework) {
        return switch (framework) {

            case "react-vite" -> """
                1.  package.json       ← react, react-dom, router, lucide, framer-motion;
                                         devDeps: vite, @vitejs/plugin-react,
                                         tailwindcss ^4, @tailwindcss/vite ^4
                2.  vite.config.js     ← plugins: [react(), tailwindcss()] — BOTH REQUIRED
                3.  index.html         ← ROOT level, Google Font <link>, <div id="root">
                4.  src/main.jsx       ← ReactDOM.createRoot, BrowserRouter, imports ./index.css
                5.  src/App.jsx        ← Routes for all pages
                6+. src/pages/...      ← page components
                7+. src/components/... ← shared components
                LAST: src/index.css    ← FIRST LINE: @import "tailwindcss"; (MANDATORY)
                """;

            case "next" -> """
                1.  package.json       ← next, react, react-dom;
                                         devDeps: tailwindcss ^3, postcss, autoprefixer
                2.  next.config.js
                3.  tailwind.config.js ← CJS, content covers app/**,pages/**,components/**
                4.  postcss.config.js  ← CJS: module.exports with tailwindcss+autoprefixer
                5.  app/layout.jsx     ← imports './globals.css'
                6+. app/page files
                7+. components/
                LAST: app/globals.css  ← @tailwind base/components/utilities (ALL THREE)
                """;

            case "react-cra" -> """
                1.  package.json       ← react, react-dom, react-scripts;
                                         devDeps: tailwindcss ^3, autoprefixer
                2.  tailwind.config.js ← CJS, content covers src/**
                3.  public/index.html
                4.  src/index.jsx      ← imports './index.css'
                5.  src/App.jsx
                6+. src/pages/...
                7+. src/components/...
                LAST: src/index.css    ← @tailwind base/components/utilities (ALL THREE)
                """;

            case "vue-vite" -> """
                1.  package.json       ← vue, vue-router, pinia;
                                         devDeps: vite, @vitejs/plugin-vue,
                                         tailwindcss ^4, @tailwindcss/vite ^4
                2.  vite.config.js     ← plugins: [vue(), tailwindcss()] — BOTH REQUIRED
                3.  index.html         ← ROOT level
                4.  src/main.js        ← imports './style.css'
                5.  src/App.vue
                6+. src/views/...
                7+. src/components/...
                LAST: src/style.css    ← FIRST LINE: @import "tailwindcss"; (MANDATORY)
                """;

            case "angular" -> """
                1.  package.json       ← @angular/core +deps;
                                         devDeps: tailwindcss ^3, postcss, autoprefixer
                2.  angular.json       ← "styles": ["src/styles.css"]
                3.  tsconfig.json
                4.  tailwind.config.js ← CJS, content: src/**/*.{html,ts}
                5.  src/main.ts
                6.  src/index.html
                7+. src/app/...
                LAST: src/styles.css   ← @tailwind base/components/utilities (ALL THREE)
                """;

            default -> "1. package.json";
        };
    }

    /* =======================================================
       🧠 SYSTEM PROMPTS
    ======================================================= */

    private String initialSystemPrompt() {
        return """
        You are a senior frontend engineer and award-winning UI designer.
        All projects use Tailwind CSS.

        ══════════════════════════════════════════
        🔴 RULE 1 — TAILWIND CSS IS MANDATORY:
        Every project uses Tailwind CSS utility classes.
        Do NOT write custom CSS class names like className="hero-section".
        Use utility classes: className="flex flex-col bg-gray-950 text-white p-8"

        🔴 RULE 2 — TAILWIND WIRING (all 3 points required):
        react-vite/vue-vite (v4):
          ① tailwindcss+@tailwindcss/vite in devDeps
          ② vite.config.js: plugins:[react/vue(),tailwindcss()]
          ③ CSS entry: @import "tailwindcss"; as first line

        next/cra/angular (v3):
          ① tailwindcss+postcss+autoprefixer in devDeps
          ② tailwind.config.js with correct content paths
          ③ CSS entry: @tailwind base/components/utilities as first 3 lines
          (+ postcss.config.js for next/vue-vite v3)

        🔴 RULE 3 — CSS FILE IS GENERATED LAST:
        Generate ALL JSX/TS/Vue files before the CSS entry file.
        The CSS file only needs the Tailwind directive — no component classes.

        🔴 RULE 4 — IMPORT/PACKAGE CONTRACT:
        Every import must be in package.json.

        🔴 RULE 5 — MANDATORY FILES:
        react-vite : package.json, vite.config.js, index.html, src/index.css (LAST)
        next       : package.json, tailwind.config.js, postcss.config.js, app/globals.css (LAST)
        react-cra  : package.json, tailwind.config.js, src/index.css (LAST)
        vue-vite   : package.json, vite.config.js, index.html, src/style.css (LAST)
        angular    : package.json, tailwind.config.js, angular.json, src/styles.css (LAST)
        ══════════════════════════════════════════

        %s

        DESIGN STANDARDS (using Tailwind):
        - Google Fonts — add via <link> in index.html or @import in CSS
        - Dark theme by default: bg-gray-950, bg-gray-900, text-white
        - Consistent accent color: indigo-500/600 or any strong accent
        - Cards: rounded-xl border border-white/10 bg-white/5 p-6
        - Buttons: px-6 py-3 bg-indigo-600 hover:bg-indigo-500 rounded-lg
        - Nav: sticky top-0 backdrop-blur-md bg-black/80 border-b border-white/10
        - Transitions: transition-all duration-200 or transition-colors duration-300
        - Responsive: always use sm: md: lg: breakpoints

        OUTPUT: JSON array only. No markdown. No explanation.
        """.formatted(JSX_SYNTAX_SAFETY_RULES);
    }

    private String regenerateSystemPrompt() {
        return """
        You are modifying an existing frontend project using Tailwind CSS.

        RULES:
        - Do NOT change framework, build system, or Tailwind setup
        - Modify only explicitly allowed files
        - Use Tailwind utility classes — do NOT add plain CSS class names
        - Preserve existing Tailwind config files
        - If you add a new import, add it to package.json
        - FIX #7: If JSX changes introduce new Tailwind utility usage,
          the CSS entry file may need re-auditing — include it in output
          if its Tailwind directive needs to be verified/fixed

        %s

        OUTPUT: JSON array only. No explanation.
        """.formatted(JSX_SYNTAX_SAFETY_RULES);
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}