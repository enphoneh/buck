/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.cxx;

import static org.junit.Assert.assertThat;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.args.FileListableLinkerInputArg;
import com.facebook.buck.rules.args.SourcePathArg;
import com.facebook.buck.rules.args.StringArg;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.google.common.collect.ImmutableList;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CxxPrepareForLinkStepTest {
  @Test
  public void cxxLinkStepPassesLinkerOptionsViaArgFile() throws IOException, InterruptedException {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    Path argFilePath = projectFilesystem.getRootPath().resolve(
        "/tmp/cxxLinkStepPassesLinkerOptionsViaArgFile.txt");
    Path fileListPath = projectFilesystem.getRootPath().resolve(
        "/tmp/cxxLinkStepPassesLinkerOptionsViaFileList.txt");
    Path output = projectFilesystem.getRootPath().resolve("output");

    runTestForArgFilePathAndOutputPath(argFilePath, fileListPath, output);
  }

  @Test
  public void cxxLinkStepCreatesDirectoriesIfNeeded() throws IOException, InterruptedException {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    Path argFilePath = projectFilesystem.getRootPath().resolve(
        "/tmp/unexisting_parent_folder/argfile.txt");
    Path fileListPath = projectFilesystem.getRootPath().resolve(
        "/tmp/unexisting_parent_folder/filelist.txt");
    Path output = projectFilesystem.getRootPath().resolve("output");

    Files.deleteIfExists(argFilePath);
    Files.deleteIfExists(fileListPath);
    Files.deleteIfExists(argFilePath.getParent());
    Files.deleteIfExists(fileListPath.getParent());

    runTestForArgFilePathAndOutputPath(argFilePath, fileListPath, output);

    // cleanup after test
    Files.deleteIfExists(argFilePath);
    Files.deleteIfExists(argFilePath.getParent());
    Files.deleteIfExists(fileListPath);
    Files.deleteIfExists(fileListPath.getParent());
  }

  private void runTestForArgFilePathAndOutputPath(
      Path argFilePath,
      Path fileListPath,
      Path output) throws IOException, InterruptedException {
    ExecutionContext context = TestExecutionContext.newInstance();

    BuildRuleResolver buildRuleResolver = new BuildRuleResolver(
        TargetGraph.EMPTY,
        new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(
        buildRuleResolver);

    // Setup some dummy values for inputs to the CxxLinkStep
    ImmutableList<Arg> args = ImmutableList.of(
        new StringArg("-rpath"),
        new StringArg("hello"),
        new StringArg("a.o"),
        FileListableLinkerInputArg.withSourcePathArg(
            new SourcePathArg(pathResolver, new FakeSourcePath("libb.a"))),
        new StringArg("-lsysroot"),
        new StringArg("/Library/Application Support/blabla"),
        new StringArg("-F/System/Frameworks"),
        new StringArg("-L/System/libraries"),
        new StringArg("-lz"));

    // Create our CxxLinkStep to test.
    CxxPrepareForLinkStep step = new CxxPrepareForLinkStep(
        argFilePath,
        fileListPath,
        ImmutableList.<Arg>of(
            new StringArg("-filelist"),
            new StringArg(fileListPath.toString())),
        output,
        args,
        CxxPlatformUtils.DEFAULT_PLATFORM.getLd().resolve(buildRuleResolver));

    step.execute(context);

    assertThat(Files.exists(argFilePath), Matchers.equalTo(true));

    ImmutableList<String> expectedArgFileContents = ImmutableList.<String>builder()
        .add("-o", output.toString())
        .add("-rpath")
        .add("hello")
        .add("a.o")
        .add("-lsysroot")
        .add("\"/Library/Application Support/blabla\"")
        .add("-F/System/Frameworks")
        .add("-L/System/libraries")
        .add("-lz")
        .add("-filelist")
        .add(fileListPath.toString())
        .build();

    ImmutableList<String> expectedFileListContents = ImmutableList.of(
        Paths.get("libb.a").toAbsolutePath().toString());

    checkContentsOfFile(argFilePath, expectedArgFileContents);
    checkContentsOfFile(fileListPath, expectedFileListContents);

    Files.deleteIfExists(argFilePath);
    Files.deleteIfExists(fileListPath);
  }

  private void checkContentsOfFile(Path file, ImmutableList<String> contents) throws IOException {
    List<String> fileContents = Files.readAllLines(file, StandardCharsets.UTF_8);
    assertThat(fileContents, Matchers.<List<String>>equalTo(contents));
  }


}
