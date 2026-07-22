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
package org.toresoft.signverify.adapter.storage;

/**
 * Shared leaf-name composition for the {@code DocumentStoragePort} adapters. Both the filesystem
 * and S3 backends must produce byte-identical leaf names so the two are drop-in interchangeable
 * (the design's behavioural-parity requirement); keeping the rules here prevents the two adapters
 * from drifting apart.
 *
 * <pre>
 *   input leaf : input-{safeName}      (safeName = filename with unsafe chars → '_', or "file")
 *   result leaf: result.json
 * </pre>
 */
final class StorageKeys {

  /** Leaf name of a job's stored validation result. */
  static final String RESULT_NAME = "result.json";

  private static final String INPUT_PREFIX = "input-";
  private static final String DEFAULT_INPUT_NAME = "file";
  private static final String SAFE_NAME_REGEX = "[^a-zA-Z0-9._-]";

  private StorageKeys() {}

  /**
   * Leaf name of a job's stored input document: {@code input-<safeName>}, where {@code safeName} is
   * {@code filename} with every character outside {@code [a-zA-Z0-9._-]} replaced by {@code '_'},
   * or {@code "file"} when {@code filename} is {@code null}.
   */
  static String inputLeaf(String filename) {
    String name =
        (filename == null) ? DEFAULT_INPUT_NAME : filename.replaceAll(SAFE_NAME_REGEX, "_");
    return INPUT_PREFIX + name;
  }
}
