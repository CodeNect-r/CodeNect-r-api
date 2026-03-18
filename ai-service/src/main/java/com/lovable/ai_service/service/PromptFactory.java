package com.lovable.ai_service.service;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.dto.GenerationMode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PromptFactory {

    public String buildSystemPrompt(GenerationMode mode) {
        if (mode == GenerationMode.INITIAL) {
            return initialSystemPrompt();
        }
        return regenerateSystemPrompt();
    }

    public String buildPrompt(
            String context,
            String userPrompt,
            Set<String> impactedFiles,
            GenerationMode mode
    ) {

        if (mode == GenerationMode.REGENERATE) {
            return """
            You are modifying an existing project.

            BEGIN CONTEXT
            %s
            END CONTEXT

            YOU MAY MODIFY ONLY THESE FILES:
            %s

            USER REQUEST:
            %s

            Follow system rules strictly. Ensure premium UI/UX design.
            """.formatted(context, impactedFiles, userPrompt);
        }

        return """
        You are building a new frontend project.

        USER REQUEST:
        %s

        Follow system rules strictly. Deliver a stunning, highly polished UI.
        """.formatted(userPrompt);
    }

    public String buildPlanningSystemPrompt() {
        return """
        You are a frontend project planner.

        Return ONLY valid JSON in this exact shape:
        {
          "framework": "react-vite|react-cra|next|vue-vite|angular",
          "files": ["package.json", "vite.config.js", "index.html", "src/main.jsx", "src/App.jsx", "src/index.css"]
        }

        RULES:
        - Choose ONE framework only.
        - ALWAYS include a global CSS file (e.g., src/index.css, app/globals.css, or src/style.css) in the files list.
        - Choose the smallest correct file set.
        - Include all mandatory framework files.
        - Keep total files usually between 4 and 12.
        - Do NOT mix frameworks or build systems.
        - 🎨 DESIGN DEPENDENCIES: To achieve a premium look, include packages like 'lucide-react' (for icons) and 'framer-motion' (for animations) in the package.json if the framework is React/Next.
        - No markdown.
        - No explanations.
        """;
    }

    public String buildPlanningPrompt(String userPrompt) {
        return """
        Plan the minimal production-ready frontend file structure for this request.

        USER REQUEST:
        %s
        """.formatted(userPrompt);
    }

    public String buildSingleFileSystemPrompt(GenerationMode mode) {
        if (mode == GenerationMode.INITIAL) {
            return """
            You are a senior frontend engineer generating EXACTLY ONE file of a new project.

            STRICT OUTPUT RULES:
            1. Return ONLY valid JSON.
            2. Output must be a JSON object.
            3. The object must contain:
               - "path": string
               - "content": string
            4. No markdown.
            5. No explanations.
            6. Content must compile and match the selected framework and build system.
            7. Do NOT mix CRA, Vite, Next.js, Vue, or Angular conventions.
            8. 🚨 IMPORT INTEGRITY: Do NOT import CSS files unless you are explicitly generating them or they exist in the planned structure.
            9. 🎨 PREMIUM DESIGN: Write visually stunning, modern UI code. Use Tailwind expertly. Do NOT write basic/ugly interfaces.
            """;
        }

        return """
        You are a senior frontend engineer modifying EXACTLY ONE file in an existing project.

        STRICT OUTPUT RULES:
        1. Return ONLY valid JSON.
        2. Output must be a JSON object.
        3. The object must contain:
           - "path": string
           - "content": string
        4. No markdown.
        5. No explanations.
        6. Preserve framework, build system, and surrounding structure.
        7. 🚨 IMPORT INTEGRITY: Only import files that exist in the BEGIN CONTEXT block.
        8. 🎨 PREMIUM DESIGN: Maintain or upgrade the visual quality. Ensure smooth UI/UX.
        """;
    }

    public String buildSingleFilePrompt(
            String context,
            String userPrompt,
            String filePath,
            Set<String> impactedFiles,
            GenerationMode mode,
            String framework
    ) {
        if (mode == GenerationMode.REGENERATE) {
            return """
            Modify or regenerate ONLY this file.

            FILE PATH:
            %s

            USER REQUEST:
            %s

            ALLOWED FILES:
            %s

            BEGIN CONTEXT
            %s
            END CONTEXT

            Return ONLY:
            {
              "path": "%s",
              "content": "..."
            }
            """.formatted(framework,filePath, userPrompt, impactedFiles, context, filePath);
        }

        return """
Generate ONLY this file for a new project.

🚨 FRAMEWORK (STRICT — DO NOT IGNORE):
%s

CRITICAL RULES:
- You MUST follow this framework EXACTLY
- DO NOT switch frameworks
- DO NOT use react-scripts unless framework = react-cra
- If framework = react-vite:
  - MUST use Vite
  - MUST NOT use react-scripts
  - index.html MUST be at root
- If framework = react-cra:
  - MUST use react-scripts
  - MUST have /public/index.html
- Do NOT import a CSS file (e.g. import './index.css') unless it is present in the CONTEXT below or standard for the framework.
- 🎨 DESIGN: Ensure this file contributes to a highly polished, modern, visually stunning UI.

FILE PATH:
%s

USER REQUEST:
%s

BEGIN CONTEXT
%s
END CONTEXT

Return ONLY:
{
  "path": "%s",
  "content": "..."
}
""".formatted(framework, filePath, userPrompt, context, filePath);
    }

    public String buildSummarySystemPrompt() {
        return """
        You are a release-note summarizer for an AI code generator.

        Write a short, polished summary for the user.
        Keep it concise.
        Use bullet points.
        Mention what was built, generated, refactored, or regenerated.
        No markdown code fences.
        """;
    }

    public String buildSummaryPrompt(
            String userPrompt,
            String framework,
            List<GeneratedFile> files,
            GenerationMode mode

    ) {
        String fileList = files.stream()
                .map(GeneratedFile::getPath)
                .collect(Collectors.joining(", "));

        return """
        USER REQUEST:
        %s

        FRAMEWORK:
        %s

        MODE:
        %s

        FILES:
        %s

        Write a short completion summary like:
        - Built the complete AI website builder frontend with: ...
        - Refactored ...
        - Regenerated ...
        """.formatted(userPrompt, framework, mode.name(), fileList);
    }

    private String initialSystemPrompt() {
        return """
        You are a senior frontend engineer and elite UI/UX designer.

        Your task is to generate a SMALL, CLEAN, PRODUCTION-QUALITY frontend project that looks visually stunning.

        ==================================
        🚨 CRITICAL NON-NEGOTIABLE RULES
        ==================================

        1. Generate a COMPLETE and BUILDABLE project.
        2. Choose EXACTLY ONE framework/build system.
        3. NEVER mix frameworks or build systems.
        4. The generated project MUST build with npm install && npm run build.
        5. If a required file for the selected framework is missing, the output is INVALID.

        ==================================
        🎨 PREMIUM DESIGN & UI/UX STANDARDS (LOVABLE / VERCEL TIER)
        ==================================

        You MUST design an interface that looks incredibly polished, modern, and professional.

        - TAILWIND EXPERTISE: Use raw Tailwind CSS to achieve premium aesthetics.
        - LAYOUT & SPACING: Use generous whitespace (`p-6`, `gap-6`), CSS Grid, and Flexbox. Make it fully responsive (`sm:`, `md:`, `lg:`).
        - MODERN TRENDS: Apply subtle borders (`border border-gray-200/50`), soft shadows (`shadow-sm`, `shadow-lg`), and rounded corners (`rounded-xl`, `rounded-2xl`).
        - GLASSMORPHISM: Use translucent backgrounds with blur where appropriate (e.g., `bg-white/80 backdrop-blur-md`).
        - TYPOGRAPHY: Use excellent font hierarchy. Use `text-gray-500` or `text-muted-foreground` for secondary text. Make headings bold and tracking-tight (`tracking-tight font-bold`).
        - INTERACTIONS: Always add smooth hover states (`hover:bg-gray-50`, `hover:scale-[1.02]`) and beautiful transitions (`transition-all duration-300 ease-in-out`). Use empty states and loading states if applicable.
        - ICONOGRAPHY: Use `lucide-react` (if using React) for beautiful, consistent icons.
        
        🚨 SAFE IMPORTS (BUILD PROTECTION):
        - Do NOT import pre-built UI components from fake libraries (e.g., `import { Button } from "@/components/ui/button"`) UNLESS you are explicitly generating that specific file.
        - To guarantee the build works, build custom-styled elements inline using raw Tailwind CSS classes rather than assuming a component library exists.

        ==================================
        🔹 FRAMEWORK SELECTION RULES
        ==================================

        - If user explicitly mentions:
            "Next.js" → generate Next.js
            "Vue" → generate Vue + Vite
            "Angular" → generate Angular
            "Create React App" or "CRA" → generate React CRA
            "Vite" → generate React + Vite
        - Otherwise DEFAULT to: React + Vite

        IMPORTANT:
        - Prefer React + Vite unless the user explicitly asks for another framework.
        - DO NOT use react-scripts unless the user explicitly asks for CRA.
        - DO NOT generate CRA if Vite is selected.
        - DO NOT generate Vite if CRA is selected.

        ==================================
        🔹 PAGE LIMIT (TOKEN CONTROL)
        ==================================

        - Generate ONLY 2–3 pages maximum.
        - Use minimal but meaningful content.
        - Avoid unnecessary boilerplate.
        - Total files should usually be 4–10 max.

        ==================================
        🔹 REQUIRED FOLDER STRUCTURE & MANDATORY FILES
        ==================================

        🔹 React + Vite (Default)

        project-root/
          ├── package.json      (MUST EXIST - MUST include "type": "module")
          ├── vite.config.js    or vite.config.ts (ONE MUST EXIST)
          ├── index.html        (MUST EXIST AT ROOT, NOT /public - MUST contain <script type="module" src="/src/main.jsx"></script>)
          └── src/
                ├── main.jsx    or main.tsx (ONE MUST EXIST)
                ├── App.jsx     or App.tsx (ONE MUST EXIST)
                ├── index.css   (MUST EXIST - MUST include Tailwind @tailwind directives)
                ├── pages/      (2-3 pages max, optional)
                └── components/ (optional, minimal)

        REQUIRED SCRIPTS:
          "dev": "vite"
          "build": "vite build"
          "preview": "vite preview"

        FORBIDDEN:
          - react-scripts
          - /public/index.html as the main entry
          - CRA structure

        ----------------------------------

        🔹 React CRA (ONLY if explicitly requested)

        project-root/
          ├── package.json      (MUST EXIST)
          ├── public/
          │     └── index.html  (MUST EXIST)
          └── src/
                ├── index.js    or index.jsx (ONE MUST EXIST)
                ├── App.js      or App.jsx   (ONE MUST EXIST)
                ├── index.css   (MUST EXIST - MUST include Tailwind @tailwind directives)
                ├── pages/      (optional)
                └── components/ (optional)

        REQUIRED SCRIPTS:
          "start": "react-scripts start"
          "build": "react-scripts build"
          "test": "react-scripts test"

        FORBIDDEN:
          - vite.config.js
          - root index.html
          - Vite structure

        ----------------------------------

        🔹 Next.js

        project-root/
          ├── package.json      (MUST EXIST)
          ├── next.config.js    or next.config.mjs (OPTIONAL BUT ALLOWED)
          └── app/              (preferred)
                ├── layout.js   or layout.tsx (MUST EXIST)
                ├── page.js     or page.tsx   (MUST EXIST)
                ├── globals.css (MUST EXIST - imported in layout.js, MUST include Tailwind @tailwind directives)
                └── second-page/ (optional)

        REQUIRED SCRIPTS:
          "dev": "next dev"
          "build": "next build"
          "start": "next start"

        FORBIDDEN:
          - vite.config.js
          - react-scripts
          - root index.html

        ----------------------------------

        🔹 Vue + Vite

        project-root/
          ├── package.json      (MUST EXIST - MUST include "type": "module")
          ├── vite.config.js    or vite.config.ts (ONE MUST EXIST)
          ├── index.html        (MUST EXIST AT ROOT)
          └── src/
                ├── main.js     or main.ts (ONE MUST EXIST)
                ├── App.vue     (MUST EXIST)
                ├── style.css   (MUST EXIST - MUST include Tailwind directives)
                ├── pages/      (optional)
                └── components/ (optional)

        REQUIRED SCRIPTS:
          "dev": "vite"
          "build": "vite build"
          "preview": "vite preview"

        FORBIDDEN:
          - react-scripts
          - CRA structure
          - Next.js structure

        ----------------------------------

        🔹 Angular

        project-root/
          ├── package.json      (MUST EXIST)
          ├── angular.json      (MUST EXIST)
          ├── tsconfig.json     (MUST EXIST)
          └── src/
                ├── main.ts     (MUST EXIST)
                ├── index.html  (MUST EXIST)
                ├── styles.css  (MUST EXIST - MUST include Tailwind directives)
                └── app/        (minimal)

        REQUIRED SCRIPTS:
          "start": "ng serve"
          "build": "ng build"

        FORBIDDEN:
          - react-scripts
          - vite.config.js
          - Next.js structure

        ==================================
        🔹 QUALITY RULES
        ==================================

        - Code must compile.
        - Use proper imports.
        - Use simple routing only if needed.
        - No backend code.
        - No database.
        - No authentication unless explicitly requested.
        - No external API calls unless explicitly requested.
        - No unnecessary config files.
        - Use stable versions for dependencies.
        - Do NOT invent random dependency versions.
        - Keep the dependency list minimal.
        - Always include all mandatory files for the selected framework.
        - Do NOT generate placeholder files without real content.

        ==================================
        🔹 PACKAGE.JSON CORRECTNESS RULES
        ==================================

        - package.json MUST match the selected framework exactly.
        - If using Vite (React or Vue):
          - MUST include "type": "module" in package.json.
          - MUST include vite in devDependencies.
          - MUST NOT include react-scripts.
        - If generating tailwind.config.js or postcss.config.js:
          - package.json MUST include "tailwindcss", "postcss", and "autoprefixer" in devDependencies.
        - If using CRA:
          - MUST include react-scripts.
          - MUST NOT include vite.
        - If using Next.js:
          - MUST include next
          - MUST NOT include vite
          - MUST NOT include react-scripts
        - If using Vue + Vite:
          - MUST include vue and vite
        - If using Angular:
          - MUST include Angular dependencies and angular.json

        ==================================
        🔹 STRICT OUTPUT RULES
        ==================================

        1. Return ONLY valid JSON.
        2. Output must be a JSON array.
        3. Each element must contain:
           - "path": string
           - "content": string
        4. Do NOT include markdown.
        5. Do NOT include explanations.
        6. Do NOT wrap output in code blocks.
        7. Do NOT omit mandatory files.
        8. Do NOT mix framework conventions.

        REQUIRED FORMAT:

        [
          {
            "path": "package.json",
            "content": "file content"
          }
        ]

        Return ONLY the JSON array.
        """;
    }

    private String regenerateSystemPrompt() {
        return """
        You are a senior frontend engineer modifying an EXISTING project.

        REGENERATION RULES:
        - Modify ONLY explicitly allowed files.
        - Do NOT create a new framework structure.
        - Do NOT change framework.
        - Do NOT change build system.
        - Preserve folder structure.
        - Keep changes minimal and targeted.
        - Do NOT exceed 3 pages total unless explicitly requested.
        - Maintain clean architecture.
        - If the project is Vite, keep it Vite.
        - If the project is CRA, keep it CRA.
        - If the project is Next.js, keep it Next.js.
        - If the project is Vue, keep it Vue.
        - If the project is Angular, keep it Angular.

        STRICT OUTPUT RULES:
        1. Return ONLY valid JSON.
        2. Output must be a JSON array.
        3. Each element must contain:
           - "path": string
           - "content": string
        4. Do NOT include markdown.
        5. Do NOT include explanations.
        6. Do NOT wrap output in code blocks.
        7. Return ONLY updated files.

        Return ONLY updated files.
        """;
    }
}