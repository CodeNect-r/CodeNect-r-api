package com.lovable.ai_service.regeneration;

import org.springframework.stereotype.Component;

@Component
public class FileRoutingService {

    public String resolvePath(String framework, String type, String componentName) {

        return switch (framework) {

            case "react", "react-vite" -> resolveReact(type, componentName);

            case "next-app" -> resolveNextApp(type, componentName);
            case "next-pages" -> resolveNextPages(type, componentName);

            case "vue" -> resolveVue(type, componentName);

            case "angular" -> resolveAngular(type, componentName);

            default -> "src/components/" + componentName + ".jsx";
        };
    }

    private String resolveReact(String type, String name) {
        return switch (type) {
            case "page", "screen", "view" -> "src/pages/" + name + ".jsx";
            case "modal", "dialog" -> "src/components/modals/" + name + ".jsx";
            case "layout" -> "src/layouts/" + name + ".jsx";
            case "hook" -> "src/hooks/" + name + ".js";
            case "util", "helper" -> "src/utils/" + name + ".js";
            default -> "src/components/" + name + ".jsx";
        };
    }

    private String resolveNextApp(String type, String name) {
        if (type.equals("page")) {
            return "app/" + toRoute(name) + "/page.jsx";
        }
        return "components/" + name + ".jsx";
    }

    private String resolveNextPages(String type, String name) {
        if (type.equals("page")) {
            return "pages/" + toRoute(name) + ".jsx";
        }
        return "components/" + name + ".jsx";
    }

    private String resolveVue(String type, String name) {
        if (type.equals("page")) {
            return "src/views/" + name + ".vue";
        }
        return "src/components/" + name + ".vue";
    }

    private String resolveAngular(String type, String name) {
        if (type.equals("page")) {
            return "src/app/" + toKebab(name) + "/" + toKebab(name) + ".component.ts";
        }
        return "src/app/components/" + toKebab(name) + ".component.ts";
    }

    private String toRoute(String name) {
        return name.replaceAll("Page$", "")
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .toLowerCase();
    }

    private String toKebab(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }
}
