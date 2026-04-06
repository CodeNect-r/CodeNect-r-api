//package com.lovable.ai_service.service;
//
//import com.lovable.ai_service.dto.DesignMemory;
//import com.lovable.ai_service.dto.GeneratedFile;
//import com.lovable.ai_service.dto.GenerationMode;
//import com.lovable.ai_service.dto.PromptContext;
//import org.springframework.stereotype.Component;
//
//import java.util.*;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//import java.util.stream.Collectors;
//
//@Component
//public class PromptFactory {
//
//    /*
//     * ═══════════════════════════════════════════════════════════════
//     *  ARCHITECTURE DECISION: TAILWIND CSS v4 BY DEFAULT
//     *
//     *  FIX #9 (nested router): App.jsx MUST NOT contain BrowserRouter/Router.
//     *  main.jsx owns the single BrowserRouter. App.jsx uses only Routes+Route.
//     *
//     *  FIX #10 (Tailwind v4 CSS): src/index.css MUST use @import "tailwindcss"
//     *  (v4 syntax). @tailwind base/components/utilities is v3 only and will
//     *  produce ZERO output in a v4 project, causing a blank unstyled page.
//     *
//     *  FIX #11 (Tailwind v4 @apply failures): In v4, many utilities that are
//     *  valid in JSX can still fail inside CSS @apply. To prevent build crashes,
//     *  this prompt strongly discourages @apply and prefers JSX utility usage.
//     *
//     *  BUG 8 FIX (double-escaping): sanitize() was applying Java string escapes
//     *  to the context before injecting it into the prompt. This caused the LLM
//     *  to receive \\n instead of actual newlines and \\\" instead of quotes —
//     *  making all code snippets in the context unreadable. Removed sanitize()
//     *  from all prompt-building methods. The context is injected raw; JSON
//     *  escaping is the LLM's output responsibility, not input responsibility.
//     *
//     *  BUG 9 FIX (36 rules losing LLM attention): The monolithic
//     *  JSX_SYNTAX_SAFETY_RULES block with S1–S36 caused attention dilution.
//     *  Rules are now split into focused groups injected contextually:
//     *    - CRITICAL_JSX_RULES: the 6 rules that most commonly cause build failures
//     *    - TAILWIND_RULES: Tailwind-specific rules grouped by framework version
//     *    - ROUTER_RULES: single-router enforcement
//     *    - SAFETY_RULES: prop safety, optional chaining, null guards
//     *  Each prompt injects only the groups relevant to what it's generating.
//     * ═══════════════════════════════════════════════════════════════
//     */
//
//    // ─────────────────────────────────────────────────────────────
//    //  BUG 9 FIX: Focused rule groups instead of one giant S1-S36 block.
//    //  LLMs follow ~6-8 rules reliably in a block. Beyond that, attention
//    //  drops off sharply. We inject only what's needed per prompt.
//    // ─────────────────────────────────────────────────────────────
//
//    /**
//     * The 6 rules that directly cause esbuild/vite parse failures.
//     * Injected into EVERY generation prompt.
//     */
//    private static final String CRITICAL_JSX_RULES = """
//        ══════════════════════════════════════════════════════════
//        🚨 CRITICAL JSX BUILD RULES — VIOLATIONS BREAK THE BUILD
//        ══════════════════════════════════════════════════════════
//
//        🔴 RULE 1 — TEMPLATE LITERAL CLOSING:
//          ✅ `$${value.toFixed(2)}`
//          ❌ `$${value.toFixed(2)`}
//
//        🔴 RULE 2 — NO UNTERMINATED STRINGS:
//          ✅ {count === 0 ? 'FREE' : 'Paid'}
//          ❌ {count === 0 ? 'FREE : 'Paid'}
//
//        🔴 RULE 3 — EXPORT DEFAULT IS MANDATORY on every component file:
//          ✅ export default function Footer() { ... }
//          ❌ function Footer() { ... }
//
//        🔴 RULE 4 — ONE DEFAULT EXPORT PER FILE. No duplicates.
//
//        🔴 RULE 5 — NO DUPLICATE FUNCTION/COMPONENT NAMES in the same file.
//
//        🔴 RULE 6 — ALL IMPORTS must resolve to real files or declared packages.
//          Never import a local file that is not in the planned file list.
//        🔴 RULE 7 — NO @apply IN CSS FILES (Tailwind v4)
//           Using @apply WILL BREAK BUILD.
//           Use className utilities in JSX instead.
//        ══════════════════════════════════════════════════════════
//        """;
//
//    /**
//     * Tailwind v4 rules (react-vite / vue-vite). Injected only for v4 frameworks.
//     */
//    private static final String TAILWIND_V4_RULES = """
//        ══════════════════════════════════════════════════════════
//        🎨 TAILWIND v4 RULES (react-vite / vue-vite)
//        ══════════════════════════════════════════════════════════
//
//        🔴 CSS ENTRY FILE — HARD REQUIREMENT:
//          FIRST LINE MUST BE EXACTLY: @import "tailwindcss";
//
//          🚨 FORBIDDEN (silently broken in v4 — causes blank page):
//          @tailwind base;
//          @tailwind components;
//          @tailwind utilities;
//
//        🔴 @apply POLICY — PREFER ZERO @apply:
//          ❌ NEVER: @apply font-sans; @apply text-lg; @apply font-bold;
//          ❌ NEVER: @apply tracking-wide; @apply leading-relaxed;
//          ❌ NEVER: @apply custom-class-name;
//          ✅ Put utility classes directly in JSX className instead.
//
//        🔴 CSS @import ORDER — ALL @import before any rules or :root:
//          ✅ @import "tailwindcss";
//             @import url('...');
//             :root { ... }
//          ❌ :root { ... }
//             @import "tailwindcss";
//
//        🔴 vite.config.js MUST include: plugins: [react(), tailwindcss()]
//        🔴 package.json devDeps MUST include: tailwindcss ^4.0.0, @tailwindcss/vite ^4.0.0
//
//        🚨 STRICT RULE — NEVER USE @apply IN TAILWIND v4
//
//        ❌ FORBIDDEN:
//        @apply anything;
//
//        ❌ FORBIDDEN:
//        @apply font-playfair;
//        @apply text-lg;
//        @apply font-bold;
//
//        ✅ ALWAYS:
//        Use classes directly in JSX:
//        <div className="font-serif text-lg font-bold">
//
//        🚨 ANY @apply usage WILL BREAK BUILD
//        ══════════════════════════════════════════════════════════
//        """;
//
//    /**
//     * Tailwind v3 rules (next / react-cra / angular). Injected only for v3 frameworks.
//     */
//    private static final String TAILWIND_V3_RULES = """
//        ══════════════════════════════════════════════════════════
//        🎨 TAILWIND v3 RULES (next / react-cra / angular)
//        ══════════════════════════════════════════════════════════
//
//        🔴 CSS ENTRY FILE — HARD REQUIREMENT (ALL THREE LINES):
//          @tailwind base;
//          @tailwind components;
//          @tailwind utilities;
//
//          🚨 FORBIDDEN in v3: @import "tailwindcss"; (v4 only)
//
//        🔴 REQUIRED CONFIG FILES: tailwind.config.js + postcss.config.js
//        🔴 package.json devDeps: tailwindcss ^3.4.0, postcss ^8.4.0, autoprefixer ^10.4.0
//        ══════════════════════════════════════════════════════════
//        """;
//
//    /**
//     * Single-router enforcement rules. Only injected when generating App.jsx or main.jsx.
//     */
//    private static final String ROUTER_RULES = """
//        ══════════════════════════════════════════════════════════
//        🔴 ROUTER ARCHITECTURE — SINGLE BROWSERROUTER
//        ══════════════════════════════════════════════════════════
//
//        BrowserRouter lives in src/main.jsx ONLY.
//
//        ✅ CORRECT src/main.jsx:
//          import { BrowserRouter } from 'react-router-dom';
//          ReactDOM.createRoot(...).render(
//            <React.StrictMode>
//              <BrowserRouter><App /></BrowserRouter>
//            </React.StrictMode>
//          );
//
//        ✅ CORRECT src/App.jsx (NO Router wrapper):
//          import { Routes, Route } from 'react-router-dom';
//          export default function App() {
//            return (
//              <div className="flex flex-col min-h-screen bg-gray-950 text-white">
//                <Routes>
//                  <Route path="/" element={<Home />} />
//                </Routes>
//              </div>
//            );
//          }
//
//        ❌ WRONG src/App.jsx:
//          import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
//          export default function App() {
//            return <Router><Routes>...</Routes></Router>; // CRASH: nested routers
//          }
//        ══════════════════════════════════════════════════════════
//        """;
//
//    /**
//     * Runtime safety rules. Only injected for component/page files.
//     */
//    private static final String SAFETY_RULES = """
//        ══════════════════════════════════════════════════════════
//        🛡 RUNTIME SAFETY RULES
//        ══════════════════════════════════════════════════════════
//
//        🔴 SAFE DESTRUCTURING — always provide fallback:
//          ✅ const { icon = null, title = 'Untitled' } = props || {};
//          ❌ const { icon } = e;  // crashes if e is undefined
//
//        🔴 SAFE ARRAY MAP — guard before mapping:
//          ✅ items?.filter(Boolean).map((item, i) => <Card key={i} {...item} />)
//          ❌ items.map(...)  // crashes if items is undefined
//
//        🔴 OPTIONAL CHAINING everywhere for dynamic data:
//          ✅ data?.user?.name
//          ❌ data.user.name
//
//        🔴 EMPTY STATE GUARD before rendering dynamic lists:
//          ✅ if (!data || data.length === 0) return <EmptyState />;
//
//        🔴 NO ErrorBoundary — do not create, import, or use it in any form.
//        ══════════════════════════════════════════════════════════
//        """;
//
//    /* =======================================================
//       🎨 TAILWIND SETUP BLOCKS — per framework
//    ======================================================= */
//
//    private static final String TAILWIND_SETUP_REACT_VITE = """
//        ══════════════════════════════════════════════════════════
//        🎨 TAILWIND CSS v4 — react-vite  (MANDATORY SETUP)
//        ══════════════════════════════════════════════════════════
//
//        ┌─ POINT 1: package.json devDependencies ──────────────────┐
//        │  "tailwindcss": "^4.0.0"                                 │
//        │  "@tailwindcss/vite": "^4.0.0"                           │
//        └──────────────────────────────────────────────────────────┘
//
//        ┌─ POINT 2: vite.config.js ────────────────────────────────┐
//        │  import { defineConfig } from 'vite';                    │
//        │  import react from '@vitejs/plugin-react';               │
//        │  import tailwindcss from '@tailwindcss/vite';            │
//        │  export default defineConfig({                           │
//        │    plugins: [react(), tailwindcss()],                    │
//        │  });                                                     │
//        └──────────────────────────────────────────────────────────┘
//
//        ┌─ POINT 3: src/index.css — FIRST LINE MUST BE: ───────────┐
//        │  @import "tailwindcss";                                  │
//        │  🚨 Do NOT use @tailwind base/components/utilities (v3)  │
//        │     Those are silently ignored in v4 → blank page.       │
//        │  🚨 Prefer NO @apply usage in v4 CSS entry files         │
//        └──────────────────────────────────────────────────────────┘
//
//        ✅ NO tailwind.config.js needed (v4 handles it automatically)
//        ✅ NO postcss.config.js needed (v4 uses Vite plugin directly)
//        ══════════════════════════════════════════════════════════
//        """;
//
//    private static final String TAILWIND_SETUP_NEXT = """
//        ══════════════════════════════════════════════════════════
//        🎨 TAILWIND CSS v3 — next.js  (MANDATORY SETUP)
//        ══════════════════════════════════════════════════════════
//        POINT 1: devDeps: tailwindcss ^3.4.0, postcss ^8.4.0, autoprefixer ^10.4.0
//        POINT 2: tailwind.config.js (CJS) with content: app/**,pages/**,components/**
//        POINT 3: postcss.config.js (CJS): module.exports = { plugins: { tailwindcss: {}, autoprefixer: {} } }
//        POINT 4: app/globals.css starts with @tailwind base; @tailwind components; @tailwind utilities;
//        ══════════════════════════════════════════════════════════
//        """;
//
//    private static final String TAILWIND_SETUP_REACT_CRA = """
//        ══════════════════════════════════════════════════════════
//        🎨 TAILWIND CSS v3 — React CRA  (MANDATORY SETUP)
//        ══════════════════════════════════════════════════════════
//        POINT 1: devDeps: tailwindcss ^3.4.0, autoprefixer ^10.4.0
//        POINT 2: tailwind.config.js (CJS) with content: src/**
//        POINT 3: src/index.css starts with @tailwind base; @tailwind components; @tailwind utilities;
//        ══════════════════════════════════════════════════════════
//        """;
//
//    private static final String TAILWIND_SETUP_VUE_VITE = """
//        ══════════════════════════════════════════════════════════
//        🎨 TAILWIND CSS v4 — vue-vite  (MANDATORY SETUP)
//        ══════════════════════════════════════════════════════════
//        POINT 1: devDeps: tailwindcss ^4.0.0, @tailwindcss/vite ^4.0.0
//        POINT 2: vite.config.js: plugins: [vue(), tailwindcss()]
//        POINT 3: src/style.css FIRST LINE: @import "tailwindcss";
//                 🚨 Do NOT use @tailwind directives (v3) — silently ignored in v4
//                 🚨 Prefer NO @apply usage
//        ══════════════════════════════════════════════════════════
//        """;
//
//    private static final String TAILWIND_SETUP_ANGULAR = """
//        ══════════════════════════════════════════════════════════
//        🎨 TAILWIND CSS v3 — Angular  (MANDATORY SETUP)
//        ══════════════════════════════════════════════════════════
//        POINT 1: devDeps: tailwindcss ^3.4.0, autoprefixer ^10.4.0, postcss ^8.4.0
//        POINT 2: tailwind.config.js (CJS) with content: src/**/*.{html,ts}
//        POINT 3: src/styles.css starts with @tailwind base; @tailwind components; @tailwind utilities;
//        ══════════════════════════════════════════════════════════
//        """;
//
//    /* =======================================================
//       📦 DEPENDENCY RULES
//    ======================================================= */
//
//    private static final String DEPENDENCY_RULES_REACT_VITE = """
//        📦 DEPENDENCIES — react-vite (Tailwind v4 always included)
//        ALWAYS in package.json:
//          react ^18.2.0, react-dom ^18.2.0, react-router-dom ^6.22.0,
//          lucide-react ^0.395.0, framer-motion ^11.2.0, clsx ^2.1.1,
//          axios ^1.7.2, date-fns ^3.6.0, zustand ^4.5.2, prop-types ^15.8.1
//          vite ^5.0.0 (dev), @vitejs/plugin-react ^4.2.0 (dev),
//          tailwindcss ^4.0.0 (dev), @tailwindcss/vite ^4.0.0 (dev)
//        NEVER import a package not listed here unless package.json is updated too.
//        """;
//
//    private static final String DEPENDENCY_RULES_NEXT = """
//        📦 DEPENDENCIES — next.js (Tailwind v3 always included)
//        ALWAYS in package.json:
//          next ^14.2.0, react ^18.2.0, react-dom ^18.2.0,
//          lucide-react ^0.395.0, framer-motion ^11.2.0, clsx ^2.1.1,
//          axios ^1.7.2, prop-types ^15.8.1,
//          tailwindcss ^3.4.0 (dev), postcss ^8.4.0 (dev), autoprefixer ^10.4.0 (dev)
//        """;
//
//    private static final String DEPENDENCY_RULES_VUE_VITE = """
//        📦 DEPENDENCIES — vue-vite (Tailwind v4 always included)
//        ALWAYS in package.json:
//          vue ^3.4.0, vue-router ^4.3.0, pinia ^2.1.7,
//          @vueuse/core ^10.9.0, axios ^1.7.2, lucide-vue-next ^0.395.0,
//          vite ^5.0.0 (dev), @vitejs/plugin-vue ^5.0.4 (dev),
//          tailwindcss ^4.0.0 (dev), @tailwindcss/vite ^4.0.0 (dev)
//        """;
//
//    private static final String DEPENDENCY_RULES_REACT_CRA = """
//        📦 DEPENDENCIES — react-cra (Tailwind v3 always included)
//        ALWAYS in package.json:
//          react ^18.2.0, react-dom ^18.2.0, react-scripts ^5.0.1,
//          react-router-dom ^6.22.0, lucide-react ^0.395.0,
//          axios ^1.7.2, clsx ^2.1.1, prop-types ^15.8.1,
//          tailwindcss ^3.4.0 (dev), autoprefixer ^10.4.0 (dev)
//        """;
//
//    private static final String DEPENDENCY_RULES_ANGULAR = """
//        📦 DEPENDENCIES — angular (Tailwind v3 always included)
//        ALWAYS in package.json:
//          @angular/core ^17, @angular/common ^17, @angular/router ^17,
//          @angular/forms ^17, @angular/platform-browser ^17,
//          @angular/platform-browser-dynamic ^17, @angular/compiler ^17,
//          rxjs ^7.8.0, tslib ^2.6.0, zone.js ^0.14.0,
//          @angular-devkit/build-angular ^17 (dev), @angular/cli ^17 (dev),
//          @angular/compiler-cli ^17 (dev), typescript ^5.2.0 (dev),
//          tailwindcss ^3.4.0 (dev), postcss ^8.4.0 (dev), autoprefixer ^10.4.0 (dev)
//        """;
//
//    /* =======================================================
//       🧠 ENTRY POINTS
//    ======================================================= */
//
//    public String buildSystemPrompt(GenerationMode mode) {
//        return mode == GenerationMode.INITIAL ? initialSystemPrompt() : regenerateSystemPrompt();
//    }
//    public String buildPrompt(PromptContext ctx) {
//
//        String tailwindRules = isV4Framework(ctx.getFramework())
//                ? TAILWIND_V4_RULES
//                : TAILWIND_V3_RULES;
//
//        String designBlock = buildDesignBlock(ctx);
//        String intentBlock = buildIntentBlock(ctx);
//
//        if (ctx.getMode() == GenerationMode.REGENERATE) {
//            return """
//        You are modifying an existing frontend project.
//
//        🚨 FRAMEWORK: %s
//
//        %s
//        %s
//
//        %s
//        %s
//        %s
//        %s
//
//        CONTEXT:
//        %s
//
//        TARGET FILES:
//        %s
//
//        USER REQUEST:
//        %s
//
//        RULES:
//        - Modify ONLY target files
//        - DO NOT change framework
//        - Keep design consistent
//        """.formatted(
//                    ctx.getFramework(),
//                    intentBlock,
//                    designBlock,
//                    getDependencyRules(ctx.getFramework()),
//                    getTailwindSetup(ctx.getFramework()),
//                    tailwindRules,
//                    CRITICAL_JSX_RULES,
//                    compressContextFromFiles(ctx.getExistingFiles()),
//                    ctx.getTargetFiles(),
//                    ctx.getUserPrompt()
//            );
//        }
//
//        return """
//    You are building a frontend project.
//
//    🚨 FRAMEWORK: %s
//
//    %s
//    %s
//
//    %s
//    %s
//    %s
//    %s
//
//    USER REQUEST:
//    %s
//
//    OUTPUT:
//    JSON array of files
//    """.formatted(
//                ctx.getFramework(),
//                intentBlock,
//                designBlock,
//                getDependencyRules(ctx.getFramework()),
//                getTailwindSetup(ctx.getFramework()),
//                tailwindRules,
//                CRITICAL_JSX_RULES,
//                ctx.getUserPrompt()
//        );
//    }
//
//    private String buildIntentBlock(PromptContext ctx) {
//        if (ctx.getIntent() == null) return "";
//
//        return """
//    🎯 INTENT:
//    - Type: %s
//    - Features: %s
//    """.formatted(
//                ctx.getIntent().getPrimaryIntent(),
//                ctx.getIntent().getFeatures()
//        );
//    }
//    private String buildDesignBlock(DesignMemory d) {
//        if (d == null) return "";
//
//        return """
//    DESIGN SYSTEM:
//    Style: %s
//    Colors: %s
//    Radius: %s
//    Shadow: %s
//    Typography: %s
//    """.formatted(
//                d.getThemeStyle(),
//                d.getColorSystem(),
//                d.getRadius(),
//                d.getShadow(),
//                d.getTypography()
//        );
//    }
//    /* =======================================================
//       🧠 FRAMEWORK DETECTION
//    ======================================================= */
//
//    public String detectFramework(String prompt) {
//        String p = prompt.toLowerCase();
//        if (p.contains("next")) return "next";
//        if (p.contains("vue")) return "vue-vite";
//        if (p.contains("angular")) return "angular";
//        if (p.contains("cra") || p.contains("create react app")) return "react-cra";
//        return "react-vite";
//    }
//
//    /* =======================================================
//       📐 PLANNING
//    ======================================================= */
//
//    public String buildPlanningSystemPrompt() {
//        return """
//        You are a frontend project planner.
//        Return ONLY valid JSON: { "framework": "...", "files": [...] }
//
//        🔴 MANDATORY FILE ORDER:
//        react-vite: ["package.json","vite.config.js","index.html","src/main.jsx","src/App.jsx",...pages,...components,"src/index.css"]
//        next: ["package.json","next.config.js","tailwind.config.js","postcss.config.js","app/layout.jsx",...pages,...components,"app/globals.css"]
//        react-cra: ["package.json","tailwind.config.js","public/index.html","src/index.jsx","src/App.jsx",...,"src/index.css"]
//        vue-vite: ["package.json","vite.config.js","index.html","src/main.js","src/App.vue",...,"src/style.css"]
//        angular: ["package.json","angular.json","tsconfig.json","tailwind.config.js","src/main.ts","src/index.html",...,"src/styles.css"]
//
//        CSS entry file MUST be LAST.
//        Total 8-14 files.
//        Every import path that will be used later must map to a real planned file.
//        OUTPUT: JSON only.
//        """;
//    }
//
//    public String buildPlanningPrompt(String userPrompt, String framework) {
//        return """
//        USER REQUEST: %s
//
//        🚨 FORCED FRAMEWORK: %s
//
//        %s
//        %s
//
//        🔴 FILE LIST PATTERN:
//        %s
//
//        Total 8-14 files.
//        CSS file MUST be last.
//        Tailwind config files MUST be included if required by framework.
//        Do not plan files that will never be imported.
//        Do not invent aliases unless config supports them.
//        """.formatted(
//                userPrompt,
//                framework,
//                getDependencyRules(framework),
//                getTailwindSetup(framework),
//                getRequiredFilesBlock(framework)
//        );
//    }
//
//    /* =======================================================
//       📄 SINGLE FILE GENERATION
//    ======================================================= */
//
//    public String buildSingleFileSystemPrompt(GenerationMode mode) {
//        if (mode == GenerationMode.INITIAL) {
//            return """
//            You are generating one file at a time for a frontend project.
//
//            OUTPUT FORMAT — return ONLY this JSON:
//            { "path": "exact/file/path", "content": "full file content as escaped string" }
//
//            🚨 JSON RULES:
//            - "content" MUST be a plain STRING — never nested object
//            - Escape double-quotes as \\", newlines as \\n
//            - No markdown fences, no explanation outside JSON
//
//            %s
//
//            🎨 DESIGN QUALITY:
//            Google Fonts, dark theme, hover states, transitions,
//            rounded-xl cards, proper buttons, sticky nav, mobile-first responsive.
//            """.formatted(CRITICAL_JSX_RULES);
//        }
//
//        return """
//        Modify EXACTLY ONE file.
//
//        RULES:
//        - Keep Tailwind CSS approach — do NOT switch to plain CSS
//        - Only update required logic
//        - If you add a new import, ensure it is in package.json
//        - Return JSON only: { "path": "...", "content": "..." }
//
//        %s
//        """.formatted(CRITICAL_JSX_RULES);
//    }
//    @Override
//    public String buildSingleFilePrompt(PromptContext ctx, String filePath) {
//
//        String design = buildDesignBlock(ctx.getDesignMemory());
//        String intent = ctx.getIntentAsString();
//        String artifacts = ctx.getArtifactsAsString();
//
//        String context = compressContextFromFiles(ctx.getExistingFiles());
//
//        return """
//    USER REQUEST:
//    %s
//
//    FILE TO GENERATE:
//    %s
//
//    FRAMEWORK:
//    %s
//
//    ─────────────────────────────────────
//    🧠 INTENT
//    %s
//
//    🧩 ARTIFACTS
//    %s
//
//    ─────────────────────────────────────
//    📂 EXISTING CODE CONTEXT
//    %s
//
//    ─────────────────────────────────────
//    🎨 DESIGN SYSTEM
//    %s
//
//    ─────────────────────────────────────
//    🚨 RULES
//    - No markdown
//    - Only valid imports
//    - Production-ready code
//    - Follow design system strictly
//    - Maintain consistency with existing files
//
//    %s
//
//    OUTPUT:
//    {
//      "path": "%s",
//      "content": "code"
//    }
//    """.formatted(
//                ctx.getUserPrompt(),
//                filePath,
//                ctx.getFramework(),
//                intent,
//                artifacts,
//                context,
//                design,
//                CRITICAL_JSX_RULES,
//                filePath
//        );
//    }
//    private String compressContextFromFiles(List<GeneratedFile> files) {
//
//        if (files == null || files.isEmpty()) return "";
//
//        StringBuilder sb = new StringBuilder();
//
//        for (GeneratedFile file : files.stream().limit(6).toList()) {
//
//            sb.append("FILE: ").append(file.getPath()).append("\n");
//
//            String[] lines = file.getContent().split("\n");
//
//            for (int i = 0; i < Math.min(lines.length, 40); i++) {
//                sb.append(lines[i]).append("\n");
//            }
//
//            sb.append("-----\n");
//        }
//
//        return sb.toString();
//    }    /* =======================================================
//       🔍 CSS AUDIT PROMPT
//    ======================================================= */
//
//    public String buildCssAuditPrompt(
//            List<GeneratedFile> generatedFiles,
//            String framework,
//            String userPrompt
//    ) {
//        String cssPath = getCssEntryPath(framework);
//        boolean isV4 = framework.equals("react-vite") || framework.equals("vue-vite");
//
//        Set<String> tailwindClasses = new LinkedHashSet<>();
//        StringBuilder jsxSnippets = new StringBuilder();
//
//        for (GeneratedFile file : generatedFiles) {
//            String path = file.getPath();
//            if (!path.endsWith(".jsx") && !path.endsWith(".tsx")
//                    && !path.endsWith(".vue") && !path.endsWith(".ts")
//                    && !path.endsWith(".html")) {
//                continue;
//            }
//
//            tailwindClasses.addAll(extractTailwindClasses(file.getContent()));
//
//            String[] lines = file.getContent().split("\n");
//            jsxSnippets.append("\n// ").append(path).append("\n");
//            for (int i = 0; i < Math.min(40, lines.length); i++) {
//                jsxSnippets.append(lines[i]).append("\n");
//            }
//        }
//
//        String directive = isV4
//                ? "@import \"tailwindcss\";"
//                : "@tailwind base;\n@tailwind components;\n@tailwind utilities;";
//
//        String warning = isV4
//                ? """
//          🚨 CRITICAL TAILWIND v4 RULES (STRICT — NO EXCEPTIONS):
//
//          1. FIRST LINE MUST BE:
//             @import "tailwindcss";
//
//          2. 🚨 @apply IS STRICTLY FORBIDDEN
//             ❌ DO NOT USE @apply under ANY condition
//             ❌ @apply font-playfair;
//             ❌ @apply text-lg;
//             ❌ @apply anything;
//
//             🚨 USING @apply WILL BREAK THE BUILD
//
//          3. 🚨 DO NOT CREATE CUSTOM CLASSES
//             ❌ font-playfair
//             ❌ text-custom
//             ❌ any non-standard utility
//
//          4. 🚨 DO NOT STYLE FONTS USING TAILWIND CLASSES
//             Use Google Fonts + inline style in JSX if needed
//
//          5. KEEP CSS MINIMAL
//             Only:
//             - Tailwind import
//             - :root variables
//             - optional base styles
//
//          6. MOVE ALL STYLING INTO JSX
//             Use className instead of CSS rules
//
//          🚨 ANY VIOLATION WILL BREAK THE BUILD
//          """
//                : """
//                  This directive loads Tailwind.
//                  Without it, ALL utility classes produce zero CSS.
//                  """;
//
//        return """
//        Generate the CSS entry file for this project.
//
//        FILE: %s
//        FRAMEWORK: %s
//
//        %s
//
//        ══════════════════════════════════════════════════════════
//        🔴 MANDATORY — THIS FILE MUST START WITH:
//        %s
//
//        %s
//        ══════════════════════════════════════════════════════════
//
//        TAILWIND CLASSES DETECTED IN JSX FILES:
//        %s
//
//        ADDITIONAL REQUIREMENTS after the Tailwind directive:
//        1. @import for Google Font (if not already in index.html)
//        2. CSS custom properties for design tokens
//        3. @layer base { } for base styles if needed
//        4. Prefer utility classes in JSX over @layer components
//        5. 🚨 NEVER USE @apply — IT WILL BREAK THE BUILD
//        ⚠️ ALL @import statements must come before any rules or :root blocks.
//
//        USER REQUEST CONTEXT: %s
//
//        JSX FILE PREVIEWS:
//        %s
//
//        OUTPUT FORMAT — return ONLY this JSON:
//        { "path": "%s", "content": "..." }
//        """.formatted(
//                cssPath,
//                framework,
//                getTailwindSetup(framework),
//                directive,
//                warning,
//                tailwindClasses.isEmpty()
//                        ? "(none found — use JSX previews below)"
//                        : String.join(", ", tailwindClasses.stream().limit(50).collect(Collectors.toList())),
//                userPrompt,
//                jsxSnippets.length() > 6000
//                        ? jsxSnippets.substring(0, 6000) + "\n...(truncated)"
//                        : jsxSnippets.toString(),
//                cssPath
//        );
//    }
//
//    /* =======================================================
//       📋 PER-FILE CONTRACTS
//    ======================================================= */
//
//    private String getFileSpecificRules(String filePath, String framework) {
//        return switch (filePath) {
//
//            case "package.json" -> """
//                🔴 FILE CONTRACT — package.json (framework: %s)
//
//                For react-vite MUST include:
//                {
//                  "name":"app",
//                  "version":"0.0.0",
//                  "private":true,
//                  "type":"module",
//                  "scripts":{"dev":"vite","build":"vite build","preview":"vite preview"},
//                  "dependencies":{
//                    "react":"^18.2.0",
//                    "react-dom":"^18.2.0",
//                    "react-router-dom":"^6.22.0",
//                    "lucide-react":"^0.395.0",
//                    "framer-motion":"^11.2.0",
//                    "clsx":"^2.1.1",
//                    "axios":"^1.7.2",
//                    "prop-types":"^15.8.1"
//                  },
//                  "devDependencies":{
//                    "vite":"^5.0.0",
//                    "@vitejs/plugin-react":"^4.2.0",
//                    "tailwindcss":"^4.0.0",
//                    "@tailwindcss/vite":"^4.0.0"
//                  }
//                }
//
//                If code imports any extra external package, package.json MUST include it too.
//                """.formatted(framework);
//
//            case "vite.config.js" -> """
//                🔴 FILE CONTRACT — vite.config.js (ALWAYS includes Tailwind):
//                import { defineConfig } from 'vite';
//                import react from '@vitejs/plugin-react';
//                import tailwindcss from '@tailwindcss/vite';
//
//                export default defineConfig({
//                  plugins: [react(), tailwindcss()]
//                });
//
//                ⚠️ tailwindcss() MUST be in plugins.
//                """;
//
//            case "tailwind.config.js" -> getTailwindConfigContract(framework);
//
//            case "postcss.config.js" ->
//                    "module.exports = { plugins: { tailwindcss: {}, autoprefixer: {} } };";
//
//            case "index.html" -> """
//                🔴 index.html at PROJECT ROOT.
//                Must include:
//                - <div id="root"></div>
//                - <script type="module" src="/src/main.jsx"></script>
//                - optional Google Font <link> in <head>
//                """;
//
//            case "src/main.jsx" -> """
//                🔴 src/main.jsx — THE ONLY FILE THAT SHOULD CONTAIN BrowserRouter:
//
//                import React from 'react';
//                import ReactDOM from 'react-dom/client';
//                import { BrowserRouter } from 'react-router-dom';
//                import App from './App';
//                import './index.css';
//
//                ReactDOM.createRoot(document.getElementById('root')).render(
//                  <React.StrictMode>
//                    <BrowserRouter>
//                      <App />
//                    </BrowserRouter>
//                  </React.StrictMode>
//                );
//
//                🚨 App.jsx MUST NOT contain any Router.
//                """;
//
//            case "src/App.jsx" -> """
//                🔴 src/App.jsx — MUST NOT CONTAIN BrowserRouter, Router, or HashRouter:
//
//                import React from 'react';
//                import { Routes, Route } from 'react-router-dom';
//
//                export default function App() {
//                  return (
//                    <div className="flex flex-col min-h-screen bg-gray-950 text-white">
//                      <main className="flex-grow">
//                        <Routes>
//                          <Route path="/" element={<Home />} />
//                        </Routes>
//                      </main>
//                    </div>
//                  );
//                }
//
//                🚨 DO NOT import BrowserRouter, Router, or HashRouter here.
//                """;
//
//            case "src/index.css", "app/globals.css", "src/style.css", "src/styles.css" ->
//                    getFrameworkCssContract(filePath, framework);
//
//            default -> """
//                🔴 FILE: %s (framework: %s)
//                Generate complete, production-quality file using Tailwind CSS.
//                Use Tailwind utility classes directly in JSX.
//                Every external import must be in package.json.
//                Every local import path must point to a real planned file.
//                Do NOT wrap in BrowserRouter — routing is handled by main.jsx/App.jsx.
//                Do NOT create ErrorBoundary — it is forbidden.
//                """.formatted(filePath, framework);
//        };
//    }
//
//    private String getFrameworkCssContract(String filePath, String framework) {
//        boolean isV4 = framework.equals("react-vite") || framework.equals("vue-vite");
//
//        if (isV4) {
//            return """
//                🔴 FILE CONTRACT — %s (Tailwind v4)
//                ─────────────────────────────────────────────────
//                FIRST LINE MUST BE EXACTLY:
//                @import "tailwindcss";
//
//                🚨 DO NOT USE v3 DIRECTIVES:
//                   @tailwind base;
//                   @tailwind components;
//                   @tailwind utilities;
//
//                🚨 PREFER NO @apply. Move styling into JSX className.
//
//                🚨 FORBIDDEN @apply:
//                   ❌ @apply font-sans; @apply text-lg; @apply font-bold;
//
//                CORRECT structure:
//                  @import "tailwindcss";
//                  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap');
//
//                  :root {
//                    --font-display: 'Inter', sans-serif;
//                  }
//
//                  @layer base {
//                    html { scroll-behavior: smooth; }
//                    body { margin: 0; font-family: var(--font-display); }
//                  }
//                """.formatted(filePath);
//        } else {
//            return """
//                🔴 FILE CONTRACT — %s (Tailwind v3)
//                ─────────────────────────────────────────────────
//                FIRST THREE LINES MUST BE EXACTLY:
//                @tailwind base;
//                @tailwind components;
//                @tailwind utilities;
//
//                Do NOT use @import "tailwindcss" (v4 syntax).
//                ALL @import statements must come before any rules or :root blocks.
//                """.formatted(filePath);
//        }
//    }
//
//    private String getTailwindConfigContract(String framework) {
//        return switch (framework) {
//            case "next" -> """
//                🔴 tailwind.config.js (next, CJS):
//                module.exports = {
//                  content: [
//                    './app/**/*.{js,ts,jsx,tsx,mdx}',
//                    './pages/**/*.{js,ts,jsx,tsx,mdx}',
//                    './components/**/*.{js,ts,jsx,tsx,mdx}'
//                  ],
//                  theme: { extend: {} },
//                  plugins: []
//                };
//                """;
//            case "react-cra" -> """
//                🔴 tailwind.config.js (react-cra, CJS):
//                module.exports = {
//                  content: ['./src/**/*.{js,jsx,ts,tsx}','./public/index.html'],
//                  theme: { extend: {} },
//                  plugins: []
//                };
//                """;
//            case "angular" -> """
//                🔴 tailwind.config.js (angular, CJS):
//                module.exports = {
//                  content: ['./src/**/*.{html,ts}'],
//                  theme: { extend: {} },
//                  plugins: []
//                };
//                """;
//            default -> "🔴 tailwind.config.js NOT needed for " + framework + " v4. DO NOT generate.";
//        };
//    }
//
//    /* =======================================================
//       📊 SUMMARY
//    ======================================================= */
//
//    public String buildSummarySystemPrompt() {
//        return "Write a short bullet-point summary. Mention: what was built, file count, pages. No markdown.";
//    }
//
//    public String buildSummaryPrompt(
//            String userPrompt,
//            String framework,
//            List<GeneratedFile> files,
//            GenerationMode mode
//    ) {
//        String fileList = files.stream()
//                .map(GeneratedFile::getPath)
//                .collect(Collectors.joining(", "));
//        return "REQUEST: %s\nFRAMEWORK: %s\nMODE: %s\nFILES: %s"
//                .formatted(userPrompt, framework, mode.name(), fileList);
//    }
//
//    /* =======================================================
//       🔧 PUBLIC UTILITIES
//    ======================================================= */
//
//    public List<String> sortFilesForGeneration(List<String> files) {
//        return files.stream()
//                .sorted((a, b) -> {
//                    boolean aIsCss = a.endsWith(".css");
//                    boolean bIsCss = b.endsWith(".css");
//                    boolean aIsJson = a.equals("package.json");
//                    boolean bIsJson = b.equals("package.json");
//                    boolean aIsConfig = a.endsWith(".config.js") || a.endsWith(".config.ts")
//                            || a.equals("angular.json") || a.equals("tsconfig.json");
//                    boolean bIsConfig = b.endsWith(".config.js") || b.endsWith(".config.ts")
//                            || b.equals("angular.json") || b.equals("tsconfig.json");
//
//                    if (aIsJson && !bIsJson) return -1;
//                    if (!aIsJson && bIsJson) return 1;
//                    if (aIsConfig && !bIsConfig) return -1;
//                    if (!aIsConfig && bIsConfig) return 1;
//                    if (aIsCss && !bIsCss) return 1;
//                    if (!aIsCss && bIsCss) return -1;
//                    return 0;
//                })
//                .collect(Collectors.toList());
//    }
//
//    public String getCssEntryPath(String framework) {
//        return switch (framework) {
//            case "next" -> "app/globals.css";
//            case "vue-vite" -> "src/style.css";
//            case "angular" -> "src/styles.css";
//            default -> "src/index.css";
//        };
//    }
//
//    public Set<String> extractTailwindClasses(String content) {
//        Set<String> classes = new LinkedHashSet<>();
//        if (content == null || content.isBlank()) return classes;
//
//        Pattern p = Pattern.compile("(?:className|class)=[\"']([^\"']+)[\"']");
//        Matcher m = p.matcher(content);
//        while (m.find()) {
//            Arrays.stream(m.group(1).split("\\s+"))
//                    .map(String::trim)
//                    .filter(c -> !c.isEmpty() && looksLikeTailwind(c))
//                    .forEach(classes::add);
//        }
//
//        Pattern tp = Pattern.compile("(?:className|class)=\\{`([^`]+)`\\}");
//        Matcher tm = tp.matcher(content);
//        while (tm.find()) {
//            String staticPart = tm.group(1).replaceAll("\\$\\{[^}]+\\}", " ");
//            Arrays.stream(staticPart.split("\\s+"))
//                    .map(String::trim)
//                    .filter(c -> !c.isEmpty() && looksLikeTailwind(c))
//                    .forEach(classes::add);
//        }
//
//        return classes;
//    }
//
//    private boolean looksLikeTailwind(String cls) {
//        if (!cls.contains("-") && !List.of(
//                "flex", "grid", "block", "hidden", "relative",
//                "absolute", "fixed", "sticky", "overflow", "truncate",
//                "uppercase", "lowercase", "capitalize", "italic",
//                "underline", "container"
//        ).contains(cls)) {
//            return false;
//        }
//        if (cls.startsWith("${") || cls.contains("(") || cls.contains(")")) return false;
//        return true;
//    }
//
//    /* =======================================================
//       🔧 PRIVATE HELPERS
//    ======================================================= */
//
//    private boolean isV4Framework(String framework) {
//        return "react-vite".equals(framework) || "vue-vite".equals(framework);
//    }
//
//    private String getTailwindSetup(String framework) {
//        return switch (framework) {
//            case "next" -> TAILWIND_SETUP_NEXT;
//            case "react-cra" -> TAILWIND_SETUP_REACT_CRA;
//            case "vue-vite" -> TAILWIND_SETUP_VUE_VITE;
//            case "angular" -> TAILWIND_SETUP_ANGULAR;
//            default -> TAILWIND_SETUP_REACT_VITE;
//        };
//    }
//
//    private String getDependencyRules(String framework) {
//        return switch (framework) {
//            case "next" -> DEPENDENCY_RULES_NEXT;
//            case "vue-vite" -> DEPENDENCY_RULES_VUE_VITE;
//            case "react-cra" -> DEPENDENCY_RULES_REACT_CRA;
//            case "angular" -> DEPENDENCY_RULES_ANGULAR;
//            default -> DEPENDENCY_RULES_REACT_VITE;
//        };
//    }
//
//    private String getRequiredFilesBlock(String framework) {
//        return switch (framework) {
//
//            case "react-vite" -> """
//                1.  package.json       ← react, react-dom, router, lucide, framer-motion;
//                                         devDeps: vite, @vitejs/plugin-react, tailwindcss ^4, @tailwindcss/vite ^4
//                2.  vite.config.js     ← plugins: [react(), tailwindcss()]
//                3.  index.html         ← ROOT level, Google Font <link>, <div id="root">
//                4.  src/main.jsx       ← ReactDOM.createRoot, BrowserRouter (ONLY HERE), imports ./index.css
//                5.  src/App.jsx        ← Routes+Route ONLY — NO BrowserRouter/Router wrapper
//                6+. src/pages/...
//                7+. src/components/...
//                LAST: src/index.css    ← FIRST LINE: @import "tailwindcss"; (v4 — NOT @tailwind directives)
//                """;
//
//            case "next" -> """
//                1.  package.json       ← devDeps: tailwindcss ^3, postcss, autoprefixer
//                2.  next.config.js
//                3.  tailwind.config.js ← CJS, content covers app/**,pages/**,components/**
//                4.  postcss.config.js  ← CJS: tailwindcss+autoprefixer
//                5.  app/layout.jsx     ← imports './globals.css'
//                6+. app/page files
//                7+. components/
//                LAST: app/globals.css  ← @tailwind base/components/utilities (ALL THREE)
//                """;
//
//            case "react-cra" -> """
//                1.  package.json       ← devDeps: tailwindcss ^3, autoprefixer
//                2.  tailwind.config.js ← CJS, content covers src/**
//                3.  public/index.html
//                4.  src/index.jsx      ← imports './index.css'
//                5.  src/App.jsx
//                6+. src/pages/...
//                7+. src/components/...
//                LAST: src/index.css    ← @tailwind base/components/utilities (ALL THREE)
//                """;
//
//            case "vue-vite" -> """
//                1.  package.json       ← devDeps: vite, @vitejs/plugin-vue, tailwindcss ^4, @tailwindcss/vite ^4
//                2.  vite.config.js     ← plugins: [vue(), tailwindcss()]
//                3.  index.html
//                4.  src/main.js        ← imports './style.css'
//                5.  src/App.vue
//                6+. src/views/...
//                7+. src/components/...
//                LAST: src/style.css    ← FIRST LINE: @import "tailwindcss"; (v4 — NOT @tailwind directives)
//                """;
//
//            case "angular" -> """
//                1.  package.json       ← devDeps: tailwindcss ^3, postcss, autoprefixer
//                2.  angular.json       ← "styles": ["src/styles.css"]
//                3.  tsconfig.json
//                4.  tailwind.config.js ← CJS, content: src/**/*.{html,ts}
//                5.  src/main.ts
//                6.  src/index.html
//                7+. src/app/...
//                LAST: src/styles.css   ← @tailwind base/components/utilities (ALL THREE)
//                """;
//
//            default -> "1. package.json";
//        };
//    }
//
//    /* =======================================================
//       🧠 SYSTEM PROMPTS
//    ======================================================= */
//
//    private String initialSystemPrompt() {
//        return """
//        You are a senior frontend engineer and award-winning UI designer.
//        All projects use Tailwind CSS.
//
//        🔴 RULE 1 — TAILWIND CSS IS MANDATORY.
//        🔴 RULE 2 — TAILWIND WIRING:
//          react-vite/vue-vite (v4): CSS entry uses @import "tailwindcss" (NOT @tailwind directives)
//          next/cra/angular (v3): CSS entry uses @tailwind base/components/utilities
//
//        🔴 RULE 3 — CSS FILE IS GENERATED LAST.
//        🔴 RULE 4 — IMPORT/PACKAGE CONTRACT: every external import in package.json.
//        🔴 RULE 5 — MANDATORY FILES per framework (see buildPrompt for details).
//
//        🔴 RULE 6 — ROUTER ARCHITECTURE (react-vite):
//        BrowserRouter lives in src/main.jsx ONLY.
//        src/App.jsx contains ONLY Routes and Route — NO Router wrapper.
//
//        🔴 RULE 7 — TAILWIND v4 BUILD STABILITY:
//        Prefer NO @apply usage in react-vite / vue-vite projects.
//        Use utility classes directly in JSX.
//
//        🔴 RULE 8 — NO ErrorBoundary. Do not create, import, or use it.
//
//        %s
//
//        DESIGN:
//        Google Fonts, dark theme bg-gray-950, indigo accent, rounded-xl cards,
//        sticky nav, responsive sm/md/lg breakpoints, transitions.
//
//        OUTPUT: JSON array only. No markdown. No explanation.
//        """.formatted(CRITICAL_JSX_RULES);
//    }
//
//    private String regenerateSystemPrompt() {
//        return """
//        You are modifying an existing frontend project using Tailwind CSS.
//
//        RULES:
//        - Do NOT change framework, build system, or Tailwind setup
//        - Modify only explicitly allowed files
//        - Use Tailwind utility classes — avoid plain CSS class systems
//        - Preserve existing Tailwind config files
//        - If you add a new import, add it to package.json
//        - App.jsx MUST NOT contain BrowserRouter/Router — main.jsx owns routing
//        - For Tailwind v4, prefer NO @apply usage
//        - Do NOT create or import ErrorBoundary — it is forbidden
//
//        %s
//
//        OUTPUT: JSON array only. No explanation.
//        """.formatted(CRITICAL_JSX_RULES);
//    }
//
//    // BUG 8 FIX: sanitize() method removed entirely. It was applying Java string
//    // escaping (\\ → \\\\, " → \", \n → \\n) to code context before injecting into
//    // the prompt. This made all code examples in the context unreadable to the LLM.
//    // The context is now always injected as-is.
//}