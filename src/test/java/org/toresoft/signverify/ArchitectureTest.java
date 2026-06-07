package org.toresoft.signverify;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "org.toresoft.signverify",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule domain_does_not_depend_on_spring =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..");

  @ArchTest
  static final ArchRule domain_does_not_depend_on_dss =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("eu.europa.esig..");

  @ArchTest
  static final ArchRule only_adapter_dss_imports_dss =
      noClasses()
          .that()
          .resideOutsideOfPackages("..adapter.dss..", "..config..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("eu.europa.esig..");

  @ArchTest
  static final ArchRule controllers_do_not_use_repositories =
      noClasses()
          .that()
          .resideInAPackage("..api..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..persistence..");
}
