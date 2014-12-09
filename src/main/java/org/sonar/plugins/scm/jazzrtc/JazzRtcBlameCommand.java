/*
 * SonarQube :: Plugins :: SCM :: Jazz RTC
 * Copyright (C) 2014 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.scm.jazzrtc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.scm.BlameCommand;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.command.StreamConsumer;
import org.sonar.api.utils.command.StringStreamConsumer;

import java.io.File;
import java.util.List;

public class JazzRtcBlameCommand extends BlameCommand {

  private static final Logger LOG = LoggerFactory.getLogger(JazzRtcBlameCommand.class);
  private final CommandExecutor commandExecutor;
  private final JazzRtcConfiguration config;

  public JazzRtcBlameCommand(JazzRtcConfiguration configuration) {
    this(CommandExecutor.create(), configuration);
  }

  JazzRtcBlameCommand(CommandExecutor commandExecutor, JazzRtcConfiguration configuration) {
    this.commandExecutor = commandExecutor;
    this.config = configuration;
  }

  @Override
  public void blame(BlameInput input, BlameOutput output) {
    FileSystem fs = input.fileSystem();
    LOG.debug("Working directory: " + fs.baseDir().getAbsolutePath());
    for (InputFile inputFile : input.filesToBlame()) {
      blame(fs, inputFile, output);
    }

  }

  private void blame(FileSystem fs, InputFile inputFile, BlameOutput output) {
    String filename = inputFile.relativePath();
    Command cl = createCommandLine(fs.baseDir(), filename);
    JazzRtcBlameConsumer consumer = new JazzRtcBlameConsumer(filename);
    StringStreamConsumer stderr = new StringStreamConsumer();

    int exitCode = execute(cl, consumer, stderr);
    if (exitCode != 0) {
      throw new IllegalStateException("The jazz annotate command [" + cl.toString() + "] failed: " + stderr.getOutput());
    }
    List<BlameLine> lines = consumer.getLines();
    if (lines.size() == inputFile.lines() - 1) {
      // SONARPLUGINS-3097 JazzRTC do not report blame on last empty line
      lines.add(lines.get(lines.size() - 1));
    }
    output.blameResult(inputFile, lines);
  }

  public int execute(Command cl, StreamConsumer consumer, StreamConsumer stderr) {
    LOG.debug("Executing: " + cl);
    return commandExecutor.execute(cl, consumer, stderr, -1);
  }

  private Command createCommandLine(File workingDirectory, String filename) {
    Command cl = Command.create("scm");
    cl.setDirectory(workingDirectory);
    cl.addArgument("annotate");
    if (config.username() != null) {
      cl.addArgument("-u");
      cl.addArgument(config.username());
    }
    if (config.password() != null) {
      cl.addArgument("-P");
      cl.addMaskedArgument(config.password());
    }
    cl.addArgument(filename);
    return cl;
  }

}
