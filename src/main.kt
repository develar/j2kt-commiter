package org.develar.j2ktCommiter

import org.eclipse.jgit.api.CommitCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.File
import java.util.*
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.dom.attribute
import kotlin.dom.toElementList

private val vcsConfigRelativePath = ".idea${File.separatorChar}vcs.xml"
private val DOT_KT = ".kt"

fun main(args: Array<String>) {
  val dryRun = args.getOrNull(0)?.toBoolean() ?: false
  var projectDir = args.getOrNull(1) ?: System.getProperty("user.dir")
  val vcsFile = if (projectDir.isNullOrEmpty()) {
    File(vcsConfigRelativePath)
  }
  else {
    projectDir = expandUserHome(projectDir!!)
    File(projectDir, vcsConfigRelativePath)
  }

  if (!vcsFile.isFile) {
    System.err.println("$vcsConfigRelativePath not found, please ensure that you run script in the project dir")
    return
  }

  val directories = ArrayList<String>()
  for (element in (XPathFactory.newInstance().newXPath().evaluate("/project/component[@name='VcsDirectoryMappings']/mapping", InputSource(vcsFile.inputStream()), XPathConstants.NODESET) as NodeList).toElementList()) {
    if (element.attribute("vcs") == "Git") {
      val directory = element.attribute("directory")
      if (!directory.isNullOrEmpty()) {
        directories.add(directory.replace("\$PROJECT_DIR$", projectDir ?: "."))
      }
    }
  }

  if (directories.isEmpty()) {
    System.err.println("git vcs mappings not found")
    return
  }

  for (directoryPath in directories) {
    val workingDir = File(directoryPath)
    if (!workingDir.isDirectory) {
      System.out.println("warn: $directoryPath is not a directory")
      continue
    }

    processRepository(dryRun, workingDir)
  }
}

private fun processRepository(dryRun: Boolean, workingDir: File) {
  System.out.println("Repository ${ansiBold(workingDir.path)}")

  val git = Git(FileRepositoryBuilder().setWorkTree(workingDir).setMustExist(true).readEnvironment().build())
  val javaToKotlinFileMap = renameKotlinToJavaAndCommit(dryRun, git, workingDir)
  if (javaToKotlinFileMap.isEmpty()) {
    System.out.println("\u001B[32mSkip, no converted files\u001B[0m")
    return
  }

  val addCommand = git.add()
  val removeCommand = git.rm()
  for ((javaFile, kotlinFile) in javaToKotlinFileMap) {
    if (!dryRun) {
      javaFile.file.renameTo(kotlinFile.file)
      addCommand.addFilepattern(kotlinFile.path)
      removeCommand.addFilepattern(javaFile.path)
    }
  }

  if (!dryRun) {
    addCommand.call()
    removeCommand.call()

    createCommitCommand(git, javaToKotlinFileMap, false).setMessage("Convert to kotlin").call()
    System.out.println("\u001B[32mCommit, step 2 of 2 done\u001B[0m")
  }

  System.out.println()
}

private fun renameKotlinToJavaAndCommit(dryRun: Boolean, git: Git, workingDir: File): List<Pair<FileInfo, FileInfo>> {
  val status = git.status().call()
  val removed = status.removed
  val added = status.added

  val addCommand = git.add()
  val removeCommand = git.rm()

  val javaToKotlinFileMap = ArrayList<Pair<FileInfo, FileInfo>>()
  for (path in added.filter { it.endsWith(DOT_KT) }) {
    val javaCounterpartPath = "${path.substring(0, path.length() - DOT_KT.length())}.java"
    if (!removed.contains(javaCounterpartPath)) {
      continue
    }

    System.out.println("rename ${coloredPath(path)} to ${ansiBold(getFileName(javaCounterpartPath))}")

    val kotlinFile = File(workingDir, path)
    val javaFile = File(workingDir, javaCounterpartPath)
    javaToKotlinFileMap.add(FileInfo(javaCounterpartPath, javaFile) to FileInfo(path, kotlinFile))

    if (!dryRun) {
      kotlinFile.renameTo(javaFile)
      addCommand.addFilepattern(javaCounterpartPath)
      removeCommand.addFilepattern(path)
    }
  }

  if (!dryRun && javaToKotlinFileMap.isNotEmpty()) {
    addCommand.call()
    removeCommand.call()

    createCommitCommand(git, javaToKotlinFileMap, true).call()
    System.out.println("\u001B[32mCommit, step 1 of 2 done\u001B[0m")
  }
  return javaToKotlinFileMap
}

private fun createCommitCommand(git: Git, javaToKotlinFileMap: List<Pair<FileInfo, FileInfo>>, onlyJavaFile: Boolean): CommitCommand {
  val commitCommand = git.commit().setMessage("Convert to kotlin")
  for ((javaFile, kotlinFile) in javaToKotlinFileMap) {
    commitCommand.setOnly(javaFile.path)
    if (!onlyJavaFile) {
      commitCommand.setOnly(kotlinFile.path)
    }
  }
  return commitCommand
}

// system-independent path relative to working directory
private data class FileInfo(val path: String, val file: File)

fun ansiBold(text: String) = "\u001B[1m$text\u001B[0m"

private fun expandUserHome(path: String) = if (path.startsWith("~/") || path.startsWith("~\\")) {
  "${System.getProperty("user.home")}${path.substring(1)}"
} else {
  path
}

fun coloredPath(path: String) = getParentPath(path) + "/" + ansiBold(getFileName(path))

fun getFileName(path: String): String {
  if (path.isEmpty()) {
    return ""
  }
  val c = path.charAt(path.length() - 1)
  val end = if (c == '/' || c == '\\') path.length() - 1 else path.length()
  val start = Math.max(path.lastIndexOf('/', end - 1), path.lastIndexOf('\\', end - 1)) + 1
  return path.substring(start, end)
}

fun getParentPath(path: String): String {
  if (path.length() == 0) {
    return ""
  }
  var end = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'))
  if (end == path.length() - 1) {
    end = Math.max(path.lastIndexOf('/', end - 1), path.lastIndexOf('\\', end - 1))
  }
  return if (end == -1) "" else path.substring(0, end)
}