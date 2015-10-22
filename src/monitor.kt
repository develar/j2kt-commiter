package org.develar.j2ktCommiter

import org.eclipse.jgit.lib.BatchingProgressMonitor
import java.io.Flushable
import java.io.PrintWriter

const val LEFT_PADDING = 20

open class TextProgressMonitor(private val out: Appendable = PrintWriter(System.err)) : BatchingProgressMonitor() {
  override fun onUpdate(taskName: String, workCurr: Int) {
    val s = StringBuilder()
    format(s, taskName, workCurr)
    send(s)
  }

  override fun onEndTask(taskName: String, workCurr: Int) {
    val s = StringBuilder()
    format(s, taskName, workCurr)
    send(s)
  }

  private fun format(s: StringBuilder, taskName: String, workCurr: Int) {
    s.append('\r')
    s.append(taskName)
    s.append(": ")
    while (s.length < LEFT_PADDING) {
      s.append(' ')
    }
    s.append(workCurr)
  }

  override fun onUpdate(taskName: String, cmp: Int, totalWork: Int, pcnt: Int) {
    val s = StringBuilder()
    format(s, taskName, cmp, totalWork, pcnt)
    send(s)
  }

  override fun onEndTask(taskName: String, cmp: Int, totalWork: Int, pcnt: Int) {
    val s = StringBuilder()
    format(s, taskName, cmp, totalWork, pcnt)
    send(s)
  }

  private fun format(s: StringBuilder, taskName: String, cmp: Int, totalWork: Int, pcnt: Int) {
    s.append('\r')
    s.append(taskName)
    s.append(": ")
    while (s.length < LEFT_PADDING) {
      s.append(' ')
    }

    val endStr = totalWork.toString()
    var curStr = cmp.toString()
    while (curStr.length < endStr.length) {
      curStr = " $curStr"
    }
    if (pcnt < 100) {
      s.append(' ')
    }
    if (pcnt < 10) {
      s.append(' ')
    }
    s.append(pcnt)
    s.append("% (")
    s.append(curStr)
    s.append('/')
    s.append(endStr)
    s.append(')')
  }

  private fun send(s: StringBuilder) {
    out.append(s)
    if (out is Flushable) {
      out.flush()
    }
  }
}