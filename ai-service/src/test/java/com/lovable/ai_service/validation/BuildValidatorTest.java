package com.lovable.ai_service.validation;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.service.PromptFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ═══════════════════════════════════════════════════════════════
 *  BuildValidatorTest — unit tests for all deterministic fixes
 *
 *  PHILOSOPHY:
 *  Every real build error that has ever occurred in production
 *  should have a test case here using the EXACT broken content
 *  from the build log. This creates a regression suite that
 *  guarantees a fixed error can never silently come back.
 *
 *  HOW TO ADD A NEW TEST WHEN A NEW BUILD ERROR OCCURS:
 *  1. Copy the broken file content from the Docker build log
 *  2. Add a @Test method here with that content as the input
 *  3. Assert the output no longer contains the broken pattern
 *  4. Assert the output contains the correct pattern
 *  5. If the fix needs a new method in BuildValidator, add it
 *     and register it in repairFile() before writing the test
 *
 *  No mocking needed for Category A tests — they are pure Java.
 *  Category B tests (AI repair) require a Mockito mock.
 * ═══════════════════════════════════════════════════════════════
 */
@ExtendWith(MockitoExtension.class)
class BuildValidatorTest {

    @Mock ChatClient chatClient;
    @Mock PromptFactory promptFactory;

    private BuildValidator v;

    @BeforeEach
    void setUp() {
        v = new BuildValidator(chatClient, promptFactory);
    }

    // ─────────────────────────────────────────────────────────────
    //  A10 — fixMarkdownFences
    // ─────────────────────────────────────────────────────────────

    @Test
    void fixMarkdownFences_stripsJsonFence() {
        String result = v.fixMarkdownFences("```json\n{\"name\": \"app\"}\n```");
        assertThat(result).isEqualTo("{\"name\": \"app\"}");
        assertThat(result).doesNotContain("```");
    }

    @Test
    void fixMarkdownFences_stripsJsxFence() {
        String result = v.fixMarkdownFences("```jsx\nimport React from 'react';\nexport default function App() {}\n```");
        assertThat(result).startsWith("import React");
        assertThat(result).doesNotContain("```");
    }

    @Test
    void fixMarkdownFences_noFence_returnsUnchanged() {
        String input = "import React from 'react';";
        assertThat(v.fixMarkdownFences(input)).isEqualTo(input);
    }

    @Test
    void fixMarkdownFences_onlyOpenFence_stripsIt() {
        String input = "```jsx\nimport React from 'react';\nexport default function App() {}";
        String result = v.fixMarkdownFences(input);
        assertThat(result).doesNotContain("```jsx");
        assertThat(result).contains("import React");
    }

    // ─────────────────────────────────────────────────────────────
    //  A11 — fixSmartQuotes
    // ─────────────────────────────────────────────────────────────

    @Test
    void fixSmartQuotes_replacesLeftRightDoubleQuotes() {
        String result = v.fixSmartQuotes("import React from \u201Creact\u201D;");
        assertThat(result).isEqualTo("import React from \"react\";");
    }

    @Test
    void fixSmartQuotes_replacesLeftRightSingleQuotes() {
        String result = v.fixSmartQuotes("const x = \u2018hello\u2019;");
        assertThat(result).isEqualTo("const x = 'hello';");
    }

    @Test
    void fixSmartQuotes_noSmartQuotes_returnsUnchanged() {
        String input = "import React from 'react';";
        assertThat(v.fixSmartQuotes(input)).isEqualTo(input);
    }

    // ─────────────────────────────────────────────────────────────
    //  A9 — fixPackageJsonDoubleEscape
    // ─────────────────────────────────────────────────────────────

    @Test
    void fixPackageJsonDoubleEscape_unwrapsEscapedString() {
        String input = "\"{\\n  \\\"name\\\": \\\"app\\\"\\n}\"";
        String result = v.fixPackageJsonDoubleEscape(input);
        assertThat(result).contains("\"name\": \"app\"");
        assertThat(result).doesNotStartWith("\"");
        assertThat(result.trim()).startsWith("{");
    }

    @Test
    void fixPackageJsonDoubleEscape_normalJson_unchanged() {
        String input = "{\n  \"name\": \"app\"\n}";
        assertThat(v.fixPackageJsonDoubleEscape(input)).isEqualTo(input);
    }

    // ─────────────────────────────────────────────────────────────
    //  A4 — fixCssImportOrder
    // ─────────────────────────────────────────────────────────────

    @Test
    void fixCssImportOrder_movesStrayFontsImportAboveRoot() {
        String input = """
                @import "tailwindcss";

                :root {
                  --color: red;
                }

                @import url('https://fonts.googleapis.com/css2?family=Inter');
                """;
        String result = v.fixCssImportOrder(input);
        assertThat(result.indexOf("@import url")).isLessThan(result.indexOf(":root"));
    }

    @Test
    void fixCssImportOrder_correctOrder_unchanged() {
        String input = """
                @import "tailwindcss";
                @import url('https://fonts.googleapis.com/css2?family=Inter');

                :root { --color: red; }
                """;
        String result = v.fixCssImportOrder(input);
        assertThat(result.indexOf("@import \"tailwindcss\"")).isLessThan(result.indexOf(":root"));
        assertThat(result.indexOf("@import url")).isLessThan(result.indexOf(":root"));
    }

    @Test
    void fixCssImportOrder_reproduceActualBuildWarning() {
        // Exact structure from the build log warning:
        // @import url(...) appearing after :root block
        String input = """
                @import "tailwindcss";

                :root {
                  --font-display: 'Playfair Display', serif;
                  --font-body: 'Inter', sans-serif;
                }

                @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=Playfair+Display:wght@400;500;600;700&display=swap');

                body { font-family: var(--font-body); }
                """;
        String result = v.fixCssImportOrder(input);
        int urlIdx  = result.indexOf("@import url");
        int rootIdx = result.indexOf(":root");
        assertThat(urlIdx).isGreaterThanOrEqualTo(0);
        assertThat(urlIdx).isLessThan(rootIdx);
    }

    // ─────────────────────────────────────────────────────────────
    //  A5, A6 — fixTailwindV4ApplyCustom
    // ─────────────────────────────────────────────────────────────

    @Test
    void fixTailwindV4ApplyCustom_removesCustomClassFromApply() {
        String input = "@layer components {\n  .card-hover { @apply card hover:bg-white/10; }\n}";
        String result = v.fixTailwindV4ApplyCustom(input);
        assertThat(result).doesNotContain("@apply card");
        assertThat(result).contains("hover:bg-white/10");
    }

    @Test
    void fixTailwindV4ApplyCustom_entireApplyIsCustom_removesLine() {
        String input = "@layer components {\n  .foo { @apply my-card btn-primary; }\n}";
        String result = v.fixTailwindV4ApplyCustom(input);
        assertThat(result).doesNotContain("@apply");
    }

    @Test
    void fixTailwindV4ApplyCustom_validUtilitiesOnly_unchanged() {
        String input = ".btn { @apply px-6 py-3 rounded-lg bg-indigo-600 text-white; }";
        String result = v.fixTailwindV4ApplyCustom(input);
        assertThat(result).contains("@apply px-6 py-3 rounded-lg bg-indigo-600 text-white;");
    }

    @Test
    void fixTailwindV4ApplyCustom_exactContentFromFailingBuild() {
        // Reproduces the EXACT failing index.css from the HR Recruitment build error
        String input = """
                @import "tailwindcss";
                @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');

                :root {
                  --font-display: 'Inter', sans-serif;
                  --font-body: 'Inter', sans-serif;
                }

                @layer components {
                  .card {
                    @apply rounded-xl border border-white/10 bg-white/5 p-6 backdrop-blur-sm transition-all;
                  }

                  .card-hover {
                    @apply card hover:bg-white/10;
                  }

                  .btn-primary {
                    @apply px-6 py-3 rounded-lg font-semibold bg-indigo-600 hover:bg-indigo-500 transition-colors;
                  }

                  .btn-secondary {
                    @apply btn px-6 py-3 rounded-lg;
                  }

                  .status-badge {
                    @apply inline-flex items-center px-3 py-1 rounded-full text-xs font-medium;
                  }
                }
                """;

        String result = v.fixTailwindV4ApplyCustom(input);

        // .card-hover: "card" removed, "hover:bg-white/10" kept
        assertThat(result).doesNotContain("@apply card");
        assertThat(result).contains("hover:bg-white/10");

        // .btn-secondary: "btn" removed, rest kept
        assertThat(result).doesNotContain("@apply btn ");
        assertThat(result).contains("px-6 py-3 rounded-lg");

        // .card valid @apply untouched
        assertThat(result).contains("@apply rounded-xl border border-white/10 bg-white/5 p-6");

        // .btn-primary valid @apply untouched
        assertThat(result).contains("@apply px-6 py-3 rounded-lg font-semibold bg-indigo-600");

        // .status-badge valid @apply untouched
        assertThat(result).contains("@apply inline-flex items-center px-3 py-1 rounded-full");
    }

    // ─────────────────────────────────────────────────────────────
    //  A7 — fixWrongTailwindDirective
    // ─────────────────────────────────────────────────────────────

    @Test
    void fixWrongTailwindDirective_replacesV3DirectivesWithV4Import() {
        String input = "@tailwind base;\n@tailwind components;\n@tailwind utilities;\n\n:root { --color: red; }";
        String result = v.fixWrongTailwindDirective(input);
        assertThat(result).contains("@import \"tailwindcss\"");
        assertThat(result).doesNotContain("@tailwind base");
        assertThat(result).doesNotContain("@tailwind components");
        assertThat(result).doesNotContain("@tailwind utilities");
    }

    @Test
    void fixWrongTailwindDirective_v4AlreadyCorrect_unchanged() {
        String input = "@import \"tailwindcss\";\n\n:root { --color: red; }";
        String result = v.fixWrongTailwindDirective(input);
        assertThat(result).contains("@import \"tailwindcss\"");
        assertThat(result).doesNotContain("@tailwind base");
    }

    // ─────────────────────────────────────────────────────────────
    //  A8 — ensureTailwindImport
    // ─────────────────────────────────────────────────────────────

    @Test
    void ensureTailwindImport_prependsWhenMissing() {
        String input = ":root { --font: 'Inter'; }";
        String result = v.ensureTailwindImport(input);
        assertThat(result).startsWith("@import \"tailwindcss\"");
        assertThat(result).contains(":root");
    }

    @Test
    void ensureTailwindImport_alreadyPresent_noDuplicate() {
        String input = "@import \"tailwindcss\";\n\n:root { --font: 'Inter'; }";
        String result = v.ensureTailwindImport(input);
        assertThat(result.indexOf("@import \"tailwindcss\""))
                .isEqualTo(result.lastIndexOf("@import \"tailwindcss\""));
    }

    @Test
    void ensureTailwindImport_preservesCharsetAtTop() {
        String input = "@charset \"UTF-8\";\n\n:root { --color: red; }";
        String result = v.ensureTailwindImport(input);
        assertThat(result.indexOf("@charset")).isLessThan(result.indexOf("@import"));
        assertThat(result).contains("@import \"tailwindcss\"");
    }

    // ─────────────────────────────────────────────────────────────
    //  A1, A2, A3 — fixMissingExport
    // ─────────────────────────────────────────────────────────────

    @Test
    void fixMissingExport_fixesDefaultFunctionWithoutExport() {
        // Exact pattern from Footer.jsx build error
        String input = "import React from 'react';\nimport { Link } from 'react-router-dom';\n\ndefault function Footer() {\n  return <div>Footer</div>;\n}\n";
        String result = v.fixMissingExport(input);
        assertThat(result).contains("export default function Footer()");
        assertThat(result).doesNotContain("\ndefault function Footer()");
    }

    @Test
    void fixMissingExport_fixesDefaultFunctionWithoutExport_sidebar() {
        // Exact pattern from Sidebar.jsx build error
        String input = """
                import React from 'react';
                import { Home, Users } from 'lucide-react';
                import { useNavigate, useLocation } from 'react-router-dom';

                default function Sidebar() {
                  const navigate = useNavigate();
                  return <div>Sidebar</div>;
                }
                """;
        String result = v.fixMissingExport(input);
        assertThat(result).contains("export default function Sidebar()");
        assertThat(result).doesNotContain("\ndefault function Sidebar()");
    }

    @Test
    void fixMissingExport_fixesTopLevelFunctionWithNoExport() {
        String input = "import React from 'react';\n\nfunction Dashboard() {\n  return <div>Dashboard</div>;\n}\n";
        String result = v.fixMissingExport(input);
        assertThat(result).contains("export default");
    }

    @Test
    void fixMissingExport_fixesArrowComponentWithNoExport() {
        String input = "import React from 'react';\n\nconst Header = () => {\n  return <div>Header</div>;\n};\n";
        String result = v.fixMissingExport(input);
        assertThat(result).contains("export default Header");
    }

    @Test
    void fixMissingExport_alreadyHasExport_unchanged() {
        String input = "import React from 'react';\n\nexport default function Dashboard() {\n  return <div>Dashboard</div>;\n}\n";
        String result = v.fixMissingExport(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    void fixMissingExport_arrowWithExportDefault_unchanged() {
        String input = "import React from 'react';\n\nconst Header = () => <div>Header</div>;\n\nexport default Header;\n";
        String result = v.fixMissingExport(input);
        assertThat(result).isEqualTo(input);
    }

    // ─────────────────────────────────────────────────────────────
    //  repairFile() FULL PIPELINE — end-to-end
    // ─────────────────────────────────────────────────────────────

    @Test
    void repairFile_cssWithMultipleErrors_fixesAll() {
        // Simulates the exact failing index.css from the HR Recruitment build
        String broken = """
                @import "tailwindcss";

                :root {
                  --font-display: 'Inter', sans-serif;
                }

                @import url('https://fonts.googleapis.com/css2?family=Inter');

                @layer components {
                  .card { @apply rounded-xl border border-white/10 bg-white/5 p-6; }
                  .card-hover { @apply card hover:bg-white/10; }
                }
                """;

        var file   = GeneratedFile.builder().path("src/index.css").content(broken).build();
        var result = v.repairFile(file, "react-vite");
        String fixed = result.getContent();

        // @import url must come before :root
        assertThat(fixed.indexOf("@import url")).isLessThan(fixed.indexOf(":root"));
        // @apply card must be gone
        assertThat(fixed).doesNotContain("@apply card");
        // hover: utility should remain
        assertThat(fixed).contains("hover:bg-white/10");
    }

    @Test
    void repairFile_jsxWithDefaultFunctionNoExport_fixed() {
        String broken = "import React from 'react';\n\ndefault function App() {\n  return <div>App</div>;\n}\n";
        var file   = GeneratedFile.builder().path("src/App.jsx").content(broken).build();
        var result = v.repairFile(file, "react-vite");
        assertThat(result.getContent()).contains("export default function App()");
    }

    @Test
    void repairFile_jsxWithMarkdownFenceAndMissingExport_fixesBoth() {
        String broken = "```jsx\nimport React from 'react';\n\ndefault function Footer() {\n  return <footer>Footer</footer>;\n}\n```";
        var file   = GeneratedFile.builder().path("src/components/Footer.jsx").content(broken).build();
        var result = v.repairFile(file, "react-vite");
        assertThat(result.getContent()).doesNotContain("```");
        assertThat(result.getContent()).contains("export default function Footer()");
    }

    @Test
    void repairFile_cssV4MissingTailwindImport_prepends() {
        String broken = ":root { --font: 'Inter'; }\n\nbody { color: white; }";
        var file   = GeneratedFile.builder().path("src/index.css").content(broken).build();
        var result = v.repairFile(file, "react-vite");
        assertThat(result.getContent()).startsWith("@import \"tailwindcss\"");
    }

    @Test
    void repairFile_cssWithV3DirectivesInV4Project_replaced() {
        String broken = "@tailwind base;\n@tailwind components;\n@tailwind utilities;\n\n:root { --color: red; }";
        var file   = GeneratedFile.builder().path("src/index.css").content(broken).build();
        var result = v.repairFile(file, "react-vite");
        assertThat(result.getContent()).contains("@import \"tailwindcss\"");
        assertThat(result.getContent()).doesNotContain("@tailwind base");
    }

    @Test
    void repairFile_packageJsonDoubleEscaped_unwrapped() {
        String broken = "\"{\\n  \\\"name\\\": \\\"app\\\",\\n  \\\"version\\\": \\\"0.0.0\\\"\\n}\"";
        var file   = GeneratedFile.builder().path("package.json").content(broken).build();
        var result = v.repairFile(file, "react-vite");
        assertThat(result.getContent().trim()).startsWith("{");
        assertThat(result.getContent()).contains("\"name\": \"app\"");
    }

    @Test
    void repairFile_nullContent_returnsOriginal() {
        var file   = GeneratedFile.builder().path("src/App.jsx").content(null).build();
        var result = v.repairFile(file, "react-vite");
        assertThat(result).isSameAs(file);
    }

    @Test
    void repairAll_repairsEveryFileInList() {
        List<GeneratedFile> files = List.of(
                GeneratedFile.builder().path("src/App.jsx")
                        .content("import React from 'react';\n\ndefault function App() { return <div/>; }\n")
                        .build(),
                GeneratedFile.builder().path("src/components/Footer.jsx")
                        .content("import React from 'react';\n\ndefault function Footer() { return <footer/>; }\n")
                        .build()
        );
        List<GeneratedFile> results = v.repairAll(files, "react-vite");
        assertThat(results).allSatisfy(f ->
                assertThat(f.getContent()).contains("export default function"));
    }

    // ─────────────────────────────────────────────────────────────
    //  validate() PASS 2 — structural validation
    // ─────────────────────────────────────────────────────────────

    @Test
    void validate_missingPackageJson_returnsIssue() {
        var issues = v.validate(List.of(), "react-vite");
        assertThat(issues).contains("Missing package.json");
    }

    @Test
    void validate_invalidPackageJson_returnsParseError() {
        var file = GeneratedFile.builder().path("package.json").content("not-json!!!").build();
        var issues = v.validate(List.of(file), "react-vite");
        assertThat(issues).anyMatch(i -> i.contains("Invalid package.json"));
    }
}