package org.develar.j2ktCommiter

import org.eclipse.jgit.api.CommitCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.IndexDiff
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.ansi
import org.fusesource.jansi.AnsiConsole
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.dom.attribute
import kotlin.dom.toElementList

private val vcsConfigRelativePath = ".idea${File.separatorChar}vcs.xml"
private val DOT_KT = ".kt"

fun main(args: Array<String>) {
  // call it here to ensure that app will not crash/slowdown unexpectedly
  AnsiConsole.systemInstall()

  val dryRun = args.getOrNull(0)?.toBoolean() ?: false
  var projectDir = args.getOrNull(1) ?: System.getProperty("user.dir")
  val vcsFile = if (projectDir.isNullOrEmpty()) {
    Paths.get(vcsConfigRelativePath)
  }
  else {
    projectDir = expandUserHome(projectDir!!)
    Paths.get(projectDir, vcsConfigRelativePath)
  }

  if (!Files.isRegularFile(vcsFile)) {
    System.err.println("$vcsConfigRelativePath not found, please ensure that you run script in the project dir")
    return
  }

  val directories = ArrayList<Path>()
  for (element in (XPathFactory.newInstance().newXPath().evaluate("/project/component[@name='VcsDirectoryMappings']/mapping", InputSource(Files.newInputStream(vcsFile)), XPathConstants.NODESET) as NodeList).toElementList()) {
    if (element.attribute("vcs") == "Git") {
      val directory = element.attribute("directory")
      if (!directory.isNullOrEmpty()) {
        directories.add(Paths.get(directory.replace("\$PROJECT_DIR$", projectDir)))
      }
    }
  }

  if (directories.isEmpty()) {
    System.out.println("warn: GIT VCS mappings not found")
    return
  }

  for (workingDir in directories) {
    if (!Files.isDirectory(workingDir)) {
      System.out.println("warn: $workingDir is not a directory")
      continue
    }

    processRepository(workingDir, dryRun)
  }
}

private fun processRepository(workingDir: Path, dryRun: Boolean) {
  System.out.println(ansi().a("Repository ").bold(workingDir.toString()))

  val git = Git(FileRepositoryBuilder().setWorkTree(workingDir.toFile()).setMustExist(true).readEnvironment().build())
  val javaToKotlinFileMap = renameKotlinToJava(dryRun, git, workingDir)
  if (javaToKotlinFileMap.isEmpty()) {
    System.out.print(ansi().eraseLine().green("\rSkip, no converted files").a(" ".repeat(LEFT_PADDING)).newline().newline())
    return
  }

  val commitMessage = StringBuilder()
  commitMessage.append("convert ")
  for ((javaFile, kotlinFile) in javaToKotlinFileMap) {
    commitMessage.append(kotlinFile.className)
    commitMessage.append(", ")
  }
  commitMessage.setLength(commitMessage.length - 2)

  commitMessage.append(" to kotlin")

  if (dryRun) {
    System.out.println(ansi().fg(Ansi.Color.GREEN).a("Commit message will be ").bold().a(commitMessage).reset())
  }
  else {
    val commitMessageAsString = commitMessage.toString()
    createCommitCommand(git, javaToKotlinFileMap, true).setMessage(commitMessageAsString).call()
    System.out.println(ansi().green("Commit, step 1 of 2 done"))

    val addCommand = git.add()
    val removeCommand = git.rm()
    for ((javaFile, kotlinFile) in javaToKotlinFileMap) {
      Files.move(javaFile.file, kotlinFile.file)
      addCommand.addFilepattern(kotlinFile.path)
      removeCommand.addFilepattern(javaFile.path)
    }

    addCommand.call()
    removeCommand.call()

    createCommitCommand(git, javaToKotlinFileMap, false).setMessage(commitMessageAsString).call()
    System.out.println(ansi().green("Commit, step 2 of 2 done"))
  }

  System.out.println()
}

private fun renameKotlinToJava(dryRun: Boolean, git: Git, workingDir: Path): List<Pair<FileInfo, FileInfo>> {
  val workingTreeIterator = FileTreeIterator(git.repository)
  val diff: IndexDiff
  try {
    diff = IndexDiff(git.repository, Constants.HEAD, workingTreeIterator)
    diff.diff(TextProgressMonitor(System.out), ProgressMonitor.UNKNOWN, ProgressMonitor.UNKNOWN, "Compute status")
  }
  finally {
    workingTreeIterator.reset()
  }

  var lineErased = false

  val addCommand = git.add()
  val removeCommand = git.rm()

  val javaToKotlinFileMap = ArrayList<Pair<FileInfo, FileInfo>>()
  for (path in diff.added.filter { it.endsWith(DOT_KT) }) {
    val pathWithoutExtension = path.substring(0, path.length - DOT_KT.length)
    val javaCounterpartPath = "$pathWithoutExtension.java"
    if (!(diff.removed.contains(javaCounterpartPath) || diff.missing.contains(javaCounterpartPath))) {
      continue
    }

    var message = ansi()
    if (!lineErased) {
      message.a('\r')
      lineErased = true
    }
    val className = getFileName(pathWithoutExtension)
    System.out.println(message.a("rename ").bold(className).a(" (").a(getParentPath(pathWithoutExtension)).a(")"))

    val kotlinFile = workingDir.resolve(path)
    val javaFile = workingDir.resolve(javaCounterpartPath)
    javaToKotlinFileMap.add(FileInfo(javaCounterpartPath, javaFile, className) to FileInfo(path, kotlinFile, className))

    if (!dryRun) {
      Files.move(kotlinFile, javaFile)
      addCommand.addFilepattern(javaCounterpartPath)
      removeCommand.addFilepattern(path)
    }
  }

  if (!dryRun && javaToKotlinFileMap.isNotEmpty()) {
    addCommand.call()
    removeCommand.call()
  }
  return javaToKotlinFileMap
}

private fun createCommitCommand(git: Git, javaToKotlinFileMap: List<Pair<FileInfo, FileInfo>>, onlyJavaFile: Boolean): CommitCommand {
  val commitCommand = git.commit()
  for ((javaFile, kotlinFile) in javaToKotlinFileMap) {
    commitCommand.setOnly(javaFile.path)
    if (!onlyJavaFile) {
      commitCommand.setOnly(kotlinFile.path)
    }
  }
  return commitCommand
}

// system-independent path relative to working directory
private data class FileInfo(val path: String, val file: Path, val className: String)

private fun expandUserHome(path: String) = if (path.startsWith("~/") || path.startsWith("~\\")) "${System.getProperty("user.home")}${path.substring(1)}" else path

fun getFileName(path: String): String {
  if (path.isEmpty()) {
    return ""
  }
  val c = path[path.length - 1]
  val end = if (c == '/' || c == '\\') path.length - 1 else path.length
  val start = Math.max(path.lastIndexOf('/', end - 1), path.lastIndexOf('\\', end - 1)) + 1
  return path.substring(start, end)
}

fun getParentPath(path: String): String {
  if (path.length == 0) {
    return ""
  }
  var end = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'))
  if (end == path.length - 1) {
    end = Math.max(path.lastIndexOf('/', end - 1), path.lastIndexOf('\\', end - 1))
  }
  return if (end == -1) "" else path.substring(0, end)
}