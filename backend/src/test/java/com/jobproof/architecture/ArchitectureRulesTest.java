package com.jobproof.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module boundaries R1–R5 (docs/03 §5.3). Violations that predate the rules are frozen in
 * {@code src/test/resources/archunit-store}; any new violation fails the build.
 */
@AnalyzeClasses(packages = "com.jobproof", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    private static final Pattern MODULE_PACKAGE = Pattern.compile("^com\\.jobproof\\.modules\\.([a-z0-9]+)(\\.([a-z0-9]+))?");

    /** R1: a controller layer never reaches into persistence directly. */
    @ArchTest
    static final ArchRule webDoesNotUseInfra = FreezingArchRule.freeze(noClasses()
            .that().resideInAPackage("com.jobproof.modules.*.web..")
            .should().dependOnClassesThat().resideInAPackage("com.jobproof.modules.*.infra..")
            .as("R1 web packages do not access infra packages"));

    /** R2: modules talk to each other through application services, ports and domain types only. */
    @ArchTest
    static final ArchRule modulesKeepInternalsPrivate = FreezingArchRule.freeze(classes()
            .that().resideInAPackage("com.jobproof.modules..")
            .should(new ArchCondition<JavaClass>("not access another module's infra or web package") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    String own = module(item.getPackageName());
                    for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                        String target = dependency.getTargetClass().getPackageName();
                        String targetModule = module(target);
                        if (targetModule != null && !targetModule.equals(own) && isInternal(target)) {
                            events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                        }
                    }
                }
            })
            .as("R2 modules do not access another module's infra or web package"));

    /** R3: the platform layer is reusable and never knows about a business module. */
    @ArchTest
    static final ArchRule platformDoesNotDependOnModules = FreezingArchRule.freeze(noClasses()
            .that().resideInAnyPackage("com.jobproof.infrastructure..", "com.jobproof.shared..")
            .should().dependOnClassesThat().resideInAPackage("com.jobproof.modules..")
            .as("R3 infrastructure and shared do not depend on modules"));

    /** R4: HTTP endpoints live in web packages and are named as controllers. */
    @ArchTest
    static final ArchRule controllersLiveInWeb = FreezingArchRule.freeze(classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..web..")
            .andShould().haveSimpleNameEndingWith("Controller")
            .as("R4 rest controllers reside in web packages and end with Controller"));

    /** R5: the domain model stays free of the web stack. */
    @ArchTest
    static final ArchRule domainIsWebFree = FreezingArchRule.freeze(noClasses()
            .that().resideInAPackage("com.jobproof.modules.*.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "org.springframework.http..", "jakarta.servlet..")
            .as("R5 domain packages do not depend on Spring Web"));

    private static String module(String packageName) {
        Matcher matcher = MODULE_PACKAGE.matcher(packageName);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static boolean isInternal(String packageName) {
        Matcher matcher = MODULE_PACKAGE.matcher(packageName);
        if (!matcher.find() || matcher.group(3) == null) return false;
        String layer = matcher.group(3);
        return layer.equals("infra") || layer.equals("web");
    }
}
