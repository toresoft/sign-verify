/**
 * sign-verify Copyright (C) 2026 toresoft
 *
 * <p>This file is part of the "sign-verify" project.
 *
 * <p>This library is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * library; if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301 USA
 */
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
          .resideOutsideOfPackages("..adapter.dss..", "..config..", "..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("eu.europa.esig..");

  @ArchTest
  static final ArchRule controllers_do_not_use_repositories =
      noClasses()
          .that()
          .resideInAPackage("..api..")
          .and()
          .doNotHaveSimpleName("VerificationController")
          .and()
          .doNotHaveSimpleName("AsyncVerificationController")
          .and()
          .doNotHaveSimpleName("AuditController")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..persistence..");
}
