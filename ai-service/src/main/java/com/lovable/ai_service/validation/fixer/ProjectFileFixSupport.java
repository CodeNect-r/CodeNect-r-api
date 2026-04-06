package com.lovable.ai_service.validation.fixer;

import com.lovable.ai_service.dto.GeneratedFile;
import com.lovable.ai_service.prompt.PromptFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectFileFixSupport {

    private final PromptFactory promptFactory;

    public GeneratedFile fixCssEntryFile(List<GeneratedFile> files, String framework) {
        boolean isV4 = framework.equals("react-vite") || framework.equals("vue-vite");
        String cssPath = promptFactory.getCssEntryPath(framework);

        String existing = files.stream()
                .filter(f -> f.getPath().equals(cssPath))
                .map(GeneratedFile::getContent)
                .findFirst().orElse("");

        String header;
        if (isV4) {
            header = "@import \"tailwindcss\";\n\n";
            existing = existing
                    .replaceAll("@tailwind base;?\\s*", "")
                    .replaceAll("@tailwind components;?\\s*", "")
                    .replaceAll("@tailwind utilities;?\\s*", "")
                    .replaceAll("@import [\"']tailwindcss[\"'];?\\s*", "");
        } else {
            header = "@tailwind base;\n@tailwind components;\n@tailwind utilities;\n\n";
            existing = existing
                    .replaceAll("@import [\"']tailwindcss[\"'];?\\s*", "")
                    .replaceAll("@tailwind base;?\\s*", "")
                    .replaceAll("@tailwind components;?\\s*", "")
                    .replaceAll("@tailwind utilities;?\\s*", "");
        }

        return GeneratedFile.builder()
                .path(cssPath)
                .content(header + existing.trim())
                .build();
    }

    public GeneratedFile fixViteConfig(String framework) {
        boolean isVue = framework.equals("vue-vite");
        String frameworkImport = isVue
                ? "import vue from '@vitejs/plugin-vue';\n"
                : "import react from '@vitejs/plugin-react';\n";
        String frameworkPlugin = isVue ? "vue()" : "react()";

        String content = """
                import { defineConfig } from 'vite';
                %simport tailwindcss from '@tailwindcss/vite';

                export default defineConfig({
                  plugins: [%s, tailwindcss()],
                });
                """.formatted(frameworkImport, frameworkPlugin);

        return GeneratedFile.builder()
                .path("vite.config.js")
                .content(content)
                .build();
    }

    public GeneratedFile fixTailwindConfig(String framework) {
        String content = switch (framework) {
            case "next" -> """
                    /** @type {import('tailwindcss').Config} */
                    module.exports = {
                      content: ['./app/**/*.{js,ts,jsx,tsx,mdx}',
                                './pages/**/*.{js,ts,jsx,tsx,mdx}',
                                './components/**/*.{js,ts,jsx,tsx,mdx}'],
                      theme: { extend: {} }, plugins: [],
                    };
                    """;
            case "react-cra" -> """
                    module.exports = {
                      content: ['./src/**/*.{js,jsx,ts,tsx}', './public/index.html'],
                      theme: { extend: {} }, plugins: [],
                    };
                    """;
            case "angular" -> """
                    module.exports = {
                      content: ['./src/**/*.{html,ts}'],
                      theme: { extend: {} }, plugins: [],
                    };
                    """;
            default -> null;
        };

        if (content == null) return null;

        return GeneratedFile.builder()
                .path("tailwind.config.js")
                .content(content)
                .build();
    }

    public GeneratedFile fixPostcssConfig(String framework) {
        boolean cjs = framework.equals("next") || framework.equals("react-cra") || framework.equals("angular");
        String content = cjs
                ? "module.exports = { plugins: { tailwindcss: {}, autoprefixer: {} } };\n"
                : "export default { plugins: { tailwindcss: {}, autoprefixer: {} } };\n";

        return GeneratedFile.builder()
                .path("postcss.config.js")
                .content(content)
                .build();
    }

    public GeneratedFile fixIndexHtml(String framework) {
        String src = framework.equals("vue-vite") ? "/src/main.js" : "/src/main.jsx";
        String content = """
                <!DOCTYPE html>
                <html lang="en">
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>App</title>
                    <link rel="preconnect" href="https://fonts.googleapis.com" />
                    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
                    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet" />
                  </head>
                  <body>
                    <div id="root"></div>
                    <script type="module" src="%s"></script>
                  </body>
                </html>
                """.formatted(src);

        return GeneratedFile.builder()
                .path("index.html")
                .content(content)
                .build();
    }

    public GeneratedFile fixMainJsx() {
        return GeneratedFile.builder()
                .path("src/main.jsx")
                .content("""
                        import React from 'react';
                        import ReactDOM from 'react-dom/client';
                        import { BrowserRouter } from 'react-router-dom';
                        import App from './App';
                        import './index.css';
                        ReactDOM.createRoot(document.getElementById('root')).render(
                          <React.StrictMode><BrowserRouter><App /></BrowserRouter></React.StrictMode>
                        );
                        """)
                .build();
    }

    public GeneratedFile fixNextLayout() {
        return GeneratedFile.builder()
                .path("app/layout.jsx")
                .content("""
                        import './globals.css';
                        export const metadata = { title: 'App', description: 'App' };
                        export default function RootLayout({ children }) {
                          return <html lang="en"><body>{children}</body></html>;
                        }
                        """)
                .build();
    }

    public GeneratedFile fixNextPage() {
        return GeneratedFile.builder()
                .path("app/page.jsx")
                .content("export default function Home() { return <main><h1>Welcome</h1></main>; }")
                .build();
    }
}