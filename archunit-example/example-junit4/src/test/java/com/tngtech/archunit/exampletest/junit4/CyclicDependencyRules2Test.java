package com.tngtech.archunit.exampletest.junit4;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@Category(Example.class)
@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = "org.hibernate")
public class CyclicDependencyRules2Test {

    @ArchTest
    public static final ArchRule no_cycles_in_complex_scenario =
            slices().matching("org.hibernate.(**)").namingSlices("$2 of $1").should().beFreeOfCycles();
}
